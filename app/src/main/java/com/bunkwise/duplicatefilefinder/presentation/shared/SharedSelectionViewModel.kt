package com.bunkwise.duplicatefilefinder.presentation.shared

import com.bunkwise.duplicatefilefinder.ads.ProFeature
import com.bunkwise.duplicatefilefinder.ads.Unlocks
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunkwise.duplicatefilefinder.core.domain.model.DuplicateGroup
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.core.domain.model.KeepRule
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanResult
import com.bunkwise.duplicatefilefinder.core.domain.repository.ScanResultRepository
import com.bunkwise.duplicatefilefinder.core.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.bunkwise.duplicatefilefinder.core.engine.KeeperRanker
import java.io.File

/**
 * The ONE selection store observed by Duplicates, the group detail screen and
 * Review (DESIGN §5 — they can never disagree). Selection is per file; the group
 * checkbox marks every non-keeper at once. [deletable] is the single place that
 * enforces "at least one copy of every group stays".
 */
class SharedSelectionViewModel(
    private val scanRepo: ScanResultRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    /** Each sort has its natural direction: biggest, A-Z, most copies. */
    enum class SortMode { SIZE, NAME, COPIES }

    data class GroupUi(
        val group: DuplicateGroup,
        /** Files in this group currently marked for deletion. */
        val markedCount: Int
    )

    data class CleanupState(
        val result: ScanResult? = null,
        /** Groups after filter, search and sort. */
        val groups: List<GroupUi> = emptyList(),
        /** Number of groups per file type in the whole result (for the filter menu). */
        val typeCounts: Map<FileCategory, Int> = emptyMap(),
        val markedBytes: Long = 0L,
        val markedFiles: Int = 0,
        val category: FileCategory = FileCategory.ALL,
        val sort: SortMode = SortMode.SIZE,
        val query: String = "",
        val archiveMode: Boolean = false,
        val archiveTreeUri: String? = null,
        val isPremium: Boolean = false,
        val keepRule: KeepRule = KeepRule.BEST,
    )

    /** File ids marked for deletion (across all groups). */
    private val selectedFileIds = MutableStateFlow<Set<Long>>(emptySet())
    private val category = MutableStateFlow(FileCategory.ALL)
    private val sort = MutableStateFlow(SortMode.SIZE)
    private val query = MutableStateFlow("")

    /** Group whose detail screen is open (set before navigating). */
    var previewGroupId: Long = -1L

    init {
        // A NEW scan result starts with an empty selection, so marks can never carry
        // over to a different scan. When the same result is trimmed (files deleted),
        // only drop the ids that no longer exist.
        viewModelScope.launch {
            var lastStamp: Long? = null
            scanRepo.observe().collect { result ->
                val stamp = result?.finishedAtMs
                if (stamp != lastStamp) {
                    lastStamp = stamp
                    selectedFileIds.value = emptySet()
                    category.value = FileCategory.ALL
                } else if (result != null) {
                    val live = result.groups.flatMapTo(HashSet()) { g -> g.files.map { it.id } }
                    selectedFileIds.value = selectedFileIds.value.intersect(live)
                }
            }
        }
    }

    val state: StateFlow<CleanupState> = run {
        val filters = combine(category, sort, query) { c, s, q -> Filters(c, s, q) }
        combine(scanRepo.observe(), selectedFileIds, filters, settings.settings, Unlocks.version) { result, selected, f, set, _ ->
            if (result == null) return@combine CleanupState(isPremium = set.isPremium)
            val all = result.groups
            val visible = filterAndSort(all, f)
            val marked = deletable(all, selected)
            CleanupState(
                result = result,
                groups = visible.map { g -> GroupUi(g, g.files.count { it.id in selected }) },
                typeCounts = all.groupingBy { it.category }.eachCount(),
                markedBytes = marked.sumOf { it.sizeBytes },
                markedFiles = marked.size,
                category = f.category,
                sort = f.sort,
                query = f.query,
                // Archiving is premium; a lapsed premium falls back to the recycle bin.
                archiveMode = Unlocks.unlocked(set.isPremium, ProFeature.ARCHIVE) && set.archiveMode,
                archiveTreeUri = set.archiveTreeUri,
                isPremium = set.isPremium,
                keepRule = set.keepRule,
            )
        }
            // Filtering + sorting + mapping runs on every checkbox tap; keep it off the
            // main thread so large results never drop frames.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CleanupState())
    }

    private data class Filters(val category: FileCategory, val sort: SortMode, val query: String)

    private fun filterAndSort(groups: List<DuplicateGroup>, f: Filters): List<DuplicateGroup> {
        var out = if (f.category == FileCategory.ALL) groups else groups.filter { it.category == f.category }
        val q = f.query.trim().lowercase()
        if (q.isNotEmpty()) out = out.filter { g -> g.files.any { it.displayName.lowercase().contains(q) } }
        return when (f.sort) {
            SortMode.SIZE -> out.sortedByDescending { it.wastedBytes }
            SortMode.NAME -> out.sortedBy { keeperOf(it)?.displayName?.lowercase() ?: "" }
            SortMode.COPIES -> out.sortedByDescending { it.fileCount }
        }
    }

    // ---- selection ----

    /**
     * Group checkbox: if anything in the group is marked, clear the whole group;
     * otherwise mark every copy except the keeper (and make sure the keeper itself
     * is unmarked, so the group can never end up fully marked).
     */
    fun toggleGroup(groupId: Long) {
        val group = groupById(groupId) ?: return
        val all = group.files.map { it.id }.toSet()
        val current = selectedFileIds.value
        selectedFileIds.value = if (all.any { it in current }) current - all
        else current - group.keeperId + (all - group.keeperId)
    }

    /**
     * Toggle a single file (group detail). Refuses to mark the last unmarked copy of
     * a group; returns false then, so the UI can explain why.
     */
    fun toggleFile(groupId: Long, fileId: Long): Boolean {
        val group = groupById(groupId) ?: return false
        val current = selectedFileIds.value
        if (fileId in current) {
            selectedFileIds.value = current - fileId
            return true
        }
        val survivingAfter = group.files.count { it.id != fileId && it.id !in current }
        if (survivingAfter < 1) return false
        selectedFileIds.value = current + fileId
        return true
    }

    fun isFileSelected(fileId: Long): Boolean = fileId in selectedFileIds.value

    /** Premium: mark every non-keeper in the groups currently shown (respects filter/search). */
    fun selectAllVisible() {
        val ids = state.value.groups.flatMap { ui -> ui.group.files.filter { it.id != ui.group.keeperId } }.map { it.id }
        selectedFileIds.value = selectedFileIds.value + ids
    }

    fun clearSelection() { selectedFileIds.value = emptySet() }

    /** New keep rule: remembered for later scans and applied to this result now. Marks start over. */
    fun setKeepRule(rule: KeepRule) {
        viewModelScope.launch {
            settings.setKeepRule(rule)
            selectedFileIds.value = emptySet()
            scanRepo.current()?.let { scanRepo.save(it) }
        }
    }
    fun setCategory(cat: FileCategory) { category.value = cat }
    fun setSort(mode: SortMode) { sort.value = mode }
    fun setSearchQuery(q: String) { query.value = q }

    fun groupById(id: Long): DuplicateGroup? = scanRepo.current()?.groups?.firstOrNull { it.id == id }

    /** Files that will be deleted. Always leaves at least one copy per group. */
    fun selectedFiles(): List<FileItem> {
        val result = scanRepo.current() ?: return emptyList()
        val doomed = deletable(result.groups, selectedFileIds.value)
        val doomedIds = doomed.mapTo(HashSet()) { it.id }
        // Results can be stale: if every unmarked copy of a group was already deleted
        // outside the app, spare the best marked copy so one real file always survives.
        // ponytail: File.exists() on the main thread, only for unmarked copies of marked groups.
        val spared = HashSet<Long>()
        for (g in result.groups) {
            val marked = g.files.filter { it.id in doomedIds }
            if (marked.isEmpty()) continue
            val survivorExists = g.files.any { it.id !in doomedIds && File(it.path).exists() }
            if (!survivorExists) spared += KeeperRanker.keeperId(marked)
        }
        return if (spared.isEmpty()) doomed else doomed.filterNot { it.id in spared }
    }

    /** The surviving copies of every group that has something marked for deletion. */
    fun keptFiles(): List<FileItem> {
        val result = scanRepo.current() ?: return emptyList()
        val doomed = selectedFiles().mapTo(HashSet()) { it.id }
        return result.groups.filter { g -> g.files.any { it.id in doomed } }
            .flatMap { g -> g.files.filterNot { it.id in doomed } }
    }

    /**
     * The single enforcement point of the keep-one-copy invariant: whatever the
     * selection says, a group that is fully marked keeps its keeper. Every count,
     * the review screen and the delete service all read through here.
     */
    private fun deletable(groups: List<DuplicateGroup>, selected: Set<Long>): List<FileItem> =
        groups.flatMap { g ->
            val marked = g.files.filter { it.id in selected }
            if (marked.size < g.files.size) marked else marked.filter { it.id != g.keeperId }
        }

    companion object {
        fun keeperOf(g: DuplicateGroup): FileItem? = g.files.firstOrNull { it.id == g.keeperId }
    }
}
