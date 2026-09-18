package com.bunkwise.duplicatefilefinder.presentation.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunkwise.duplicatefilefinder.core.domain.model.DuplicateGroup
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.core.domain.repository.ScanResultRepository
import com.bunkwise.duplicatefilefinder.core.domain.repository.SettingsRepository
import com.bunkwise.duplicatefilefinder.core.domain.repository.StorageStatsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The ONE selection store observed by Cleanup, its stat grid, the bottom bar, the
 * Preview overlay and the Review/Delete overlays (DESIGN §5 — they can never
 * disagree). Selection is per-FILE (so the Preview overlay can mark individual
 * copies) but the group checkbox / "Select All" still marks every non-keeper at
 * once. The keeper is never marked automatically and the UI blocks marking the
 * last surviving copy, which surfaces the "keep at least one" invariant
 * (ARCHITECTURE §7.4) as prevention rather than an error.
 */
class SharedSelectionViewModel(
    private val scanRepo: ScanResultRepository,
    private val settings: SettingsRepository,
    private val storage: StorageStatsProvider
) : ViewModel() {

    enum class SortMode { SIZE, NAME, DUPLICATES }

    data class GroupUi(
        val group: DuplicateGroup,
        /** Files in this group currently marked for deletion. */
        val markedCount: Int
    ) {
        val selected: Boolean get() = markedCount > 0
    }

    data class CleanupState(
        val hasResult: Boolean = false,
        val groups: List<GroupUi> = emptyList(),
        val totalDuplicateFiles: Int = 0,
        val duplicateBytes: Long = 0L,
        val percentOfTotal: Int = 0,
        val groupCount: Int = 0,
        val markedBytes: Long = 0L,
        val markedFiles: Int = 0,
        val category: FileCategory = FileCategory.ALL,
        val sort: SortMode = SortMode.SIZE,
        val ascending: Boolean = false,
        val query: String = "",
        val archiveMode: Boolean = false,
        val isPremium: Boolean = false
    )

    /** File ids marked for deletion (across all groups). */
    private val selectedFileIds = MutableStateFlow<Set<Long>>(emptySet())
    private val category = MutableStateFlow(FileCategory.ALL)
    private val sort = MutableStateFlow(SortMode.SIZE)
    private val ascending = MutableStateFlow(false)
    private val query = MutableStateFlow("")
    private val deviceTotal = MutableStateFlow(0L)

    /** Group whose Preview overlay is currently open (set before navigating). */
    var previewGroupId: Long = -1L

    init {
        viewModelScope.launch { deviceTotal.value = storage.current().totalBytes }
        // Reset the selection whenever a NEW scan result is loaded, so marks from a
        // previous scan can never carry over onto a different scan's files.
        viewModelScope.launch {
            var lastStamp: Long? = null
            scanRepo.observe().collect { result ->
                val stamp = result?.finishedAtMs
                if (stamp != lastStamp) {
                    lastStamp = stamp
                    selectedFileIds.value = emptySet()
                }
            }
        }
    }

    val state: StateFlow<CleanupState> = run {
        val filters = combine(category, sort, ascending, query) { c, s, a, q -> Filters(c, s, a, q) }
        combine(scanRepo.observe(), selectedFileIds, filters, settings.settings, deviceTotal) {
                result, selected, f, set, total ->
            if (result == null) return@combine CleanupState(hasResult = false)
            val allGroups = result.groups

            var filtered = if (f.category == FileCategory.ALL) allGroups
            else allGroups.filter { it.category == f.category }
            if (f.query.isNotBlank()) {
                val q = f.query.trim().lowercase()
                filtered = filtered.filter { g -> g.files.any { it.displayName.lowercase().contains(q) } }
            }
            filtered = when (f.sort) {
                SortMode.SIZE -> filtered.sortedBy { it.wastedBytes }
                SortMode.NAME -> filtered.sortedBy { it.files.firstOrNull()?.displayName?.lowercase() ?: "" }
                SortMode.DUPLICATES -> filtered.sortedBy { it.duplicateCount }
            }
            if (!f.ascending) filtered = filtered.reversed()

            val markedBytes = allGroups.flatMap { it.files }.filter { it.id in selected }.sumOf { it.sizeBytes }
            val markedFiles = selected.size
            val dupBytes = allGroups.sumOf { it.wastedBytes }
            val percent = if (total > 0) ((dupBytes * 100.0) / total).toInt().coerceIn(0, 100) else 0

            CleanupState(
                hasResult = true,
                groups = filtered.map { g -> GroupUi(g, g.files.count { it.id in selected }) },
                totalDuplicateFiles = allGroups.sumOf { it.duplicateCount },
                duplicateBytes = dupBytes,
                percentOfTotal = percent,
                groupCount = allGroups.size,
                markedBytes = markedBytes,
                markedFiles = markedFiles,
                category = f.category,
                sort = f.sort,
                ascending = f.ascending,
                query = f.query,
                archiveMode = set.archiveMode,
                isPremium = set.isPremium
            )
        }
            // Filtering + sorting (O(n log n)) + object mapping runs on every checkbox
            // tap; keep it off the main thread so large result sets never drop frames.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CleanupState())
    }

    private data class Filters(
        val category: FileCategory, val sort: SortMode, val ascending: Boolean, val query: String
    )

    // ---- selection ----

    /** Group checkbox / "Select All": mark every non-keeper, or clear if any marked. */
    fun toggleGroup(groupId: Long) {
        val group = groupById(groupId) ?: return
        val nonKeepers = group.files.filter { it.id != group.keeperId }.map { it.id }.toSet()
        val current = selectedFileIds.value
        val anyMarked = nonKeepers.any { it in current }
        selectedFileIds.value = if (anyMarked) current - nonKeepers else current + nonKeepers
    }

    /**
     * Toggle a single file (Preview overlay). Never marks the keeper's last twin:
     * at least one file per group must stay unmarked. Returns false when the toggle
     * was blocked so the UI can show the "keep at least one" hint.
     */
    fun toggleFile(groupId: Long, fileId: Long): Boolean {
        val group = groupById(groupId) ?: return false
        val current = selectedFileIds.value
        if (fileId in current) {
            selectedFileIds.value = current - fileId
            return true
        }
        // Marking this one: block if it would leave zero survivors in the group.
        val groupIds = group.files.map { it.id }.toSet()
        val survivingAfter = groupIds.count { it != fileId && it !in current }
        if (survivingAfter < 1) return false
        selectedFileIds.value = current + fileId
        return true
    }

    fun isFileSelected(fileId: Long): Boolean = fileId in selectedFileIds.value

    /** Smart Cleanup: mark every group's non-keepers for deletion (premium). */
    fun selectAllGroups() {
        val result = scanRepo.current() ?: return
        selectedFileIds.value = result.groups
            .flatMap { g -> g.files.filter { it.id != g.keeperId } }
            .map { it.id }.toSet()
    }

    fun setCategory(cat: FileCategory) { category.value = cat }
    fun setSort(mode: SortMode) { sort.value = mode }
    fun toggleDirection() { ascending.value = !ascending.value }
    fun setSearchQuery(q: String) { query.value = q }
    fun clearSelection() { selectedFileIds.value = emptySet() }

    fun groupById(id: Long): DuplicateGroup? = scanRepo.current()?.groups?.firstOrNull { it.id == id }

    /** Files marked for deletion. */
    fun selectedFiles(): List<FileItem> {
        val result = scanRepo.current() ?: return emptyList()
        val sel = selectedFileIds.value
        return result.groups.flatMap { it.files }.filter { it.id in sel }
    }

    /** Files that will remain: everything not marked for deletion. */
    fun keptFiles(): List<FileItem> {
        val result = scanRepo.current() ?: return emptyList()
        val sel = selectedFileIds.value
        return result.groups.flatMap { it.files }.filterNot { it.id in sel }
    }
}
