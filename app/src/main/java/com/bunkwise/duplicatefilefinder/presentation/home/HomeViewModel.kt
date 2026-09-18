package com.bunkwise.duplicatefilefinder.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.QuotaInfo
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanMode
import com.bunkwise.duplicatefilefinder.core.domain.model.StorageInfo
import com.bunkwise.duplicatefilefinder.core.domain.repository.FileEnumerator
import com.bunkwise.duplicatefilefinder.core.domain.repository.QuotaRepository
import com.bunkwise.duplicatefilefinder.core.domain.repository.StorageStatsProvider
import com.bunkwise.duplicatefilefinder.core.domain.usecase.CheckScanQuotaUseCase
import com.bunkwise.duplicatefilefinder.core.domain.usecase.QuotaDecision
import com.bunkwise.duplicatefilefinder.presentation.common.Permissions
import com.bunkwise.duplicatefilefinder.service.DeleteController
import com.bunkwise.duplicatefilefinder.service.DeleteStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val appContext: Context,
    private val storage: StorageStatsProvider,
    private val quota: QuotaRepository,
    private val enumerator: FileEnumerator,
    private val checkQuota: CheckScanQuotaUseCase,
    deleteController: DeleteController
) : ViewModel() {

    data class HomeState(
        val hasPermission: Boolean = false,
        val storage: StorageInfo? = null,
        val quota: QuotaInfo? = null,
        val sizeByCategory: Map<FileCategory, Long> = emptyMap(),
        val selectedMode: ScanMode = ScanMode.EXACT,
        val selectedCategories: Set<FileCategory> = setOf(FileCategory.ALL)
    )

    sealed interface HomeEvent {
        data class NavigateToScan(
            val mode: ScanMode, val categories: Set<FileCategory>, val budget: Int
        ) : HomeEvent
        data object RequestStorageAccess : HomeEvent
        data class ShowQuotaSheet(
            val estimate: Int, val remaining: Int, val mode: ScanMode, val categories: Set<FileCategory>
        ) : HomeEvent
    }

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** When the category sizes were last computed; 0 = never / invalidated. */
    private var sizesFetchedAt = 0L

    init {
        // A completed deletion batch is the one event that materially changes the
        // category sizes, so it invalidates the cache; the next refresh() recomputes.
        viewModelScope.launch {
            deleteController.status.collect { status ->
                if (status is DeleteStatus.Completed) {
                    sizesFetchedAt = 0L
                    if (_state.value.hasPermission) refreshSizes()
                }
            }
        }
    }

    fun refresh() {
        val granted = hasStoragePermission()
        _state.update { it.copy(hasPermission = granted) }
        viewModelScope.launch {
            _state.update { it.copy(storage = storage.current(), quota = quota.current()) }
            // sizeByCategory sweeps all of MediaStore — far too heavy for every
            // onResume, so serve the cached values until they expire or a deletion
            // invalidates them.
            val stale = System.currentTimeMillis() - sizesFetchedAt > SIZES_TTL_MS
            if (granted && (stale || _state.value.sizeByCategory.isEmpty())) refreshSizes()
        }
    }

    private suspend fun refreshSizes() {
        // On failure keep the previous values — flashing back to 0 B is worse
        // than briefly showing slightly outdated sizes.
        runCatching { enumerator.sizeByCategory() }.getOrNull()?.let { sizes ->
            sizesFetchedAt = System.currentTimeMillis()
            _state.update { it.copy(sizeByCategory = sizes) }
        }
    }

    fun onModeSelected(index: Int) {
        val mode = ScanMode.entries.getOrNull(index) ?: ScanMode.EXACT
        _state.update { it.copy(selectedMode = mode) }
    }

    fun onCategoryToggled(category: FileCategory) {
        _state.update { s ->
            val current = s.selectedCategories
            val next = when {
                category == FileCategory.ALL -> setOf(FileCategory.ALL)
                current.contains(category) -> (current - category).ifEmpty { setOf(FileCategory.ALL) }
                else -> (current - FileCategory.ALL + category)
            }
            s.copy(selectedCategories = next)
        }
    }

    fun onGrantAccess() {
        viewModelScope.launch { _events.send(HomeEvent.RequestStorageAccess) }
    }

    fun onStartScan() {
        if (!hasStoragePermission()) {
            viewModelScope.launch { _events.send(HomeEvent.RequestStorageAccess) }
            return
        }
        val s = _state.value
        viewModelScope.launch {
            when (val decision = checkQuota(s.selectedCategories)) {
                is QuotaDecision.Allowed -> {
                    val q = quota.current()
                    val budget = if (q.isPremium) Int.MAX_VALUE else q.remaining.coerceAtLeast(1)
                    _events.send(HomeEvent.NavigateToScan(s.selectedMode, s.selectedCategories, budget))
                }
                is QuotaDecision.Exceeds -> _events.send(
                    HomeEvent.ShowQuotaSheet(decision.estimate, decision.remaining, s.selectedMode, s.selectedCategories)
                )
            }
        }
    }

    /** Called from the quota sheet: scan only within the remaining free limit. */
    fun scanWithinLimit(mode: ScanMode, categories: Set<FileCategory>, remaining: Int) {
        viewModelScope.launch {
            _events.send(HomeEvent.NavigateToScan(mode, categories, remaining.coerceAtLeast(1)))
        }
    }

    /**
     * Rewarded-ad reward earned: top up the daily allowance by [AD_BONUS_FILES]
     * (written synchronously, OTHER §7) then re-run the pre-scan quota check —
     * if the boost is enough the scan runs in full, otherwise the sheet reappears.
     */
    fun onAdReward() {
        viewModelScope.launch {
            quota.addBonus(AD_BONUS_FILES)
            _state.update { it.copy(quota = quota.current()) }
            onStartScan()
        }
    }

    private companion object {
        // Matches QuotaRepositoryImpl.AD_BONUS; kept here to avoid a data-layer import.
        const val AD_BONUS_FILES = 500
        /** Category sizes drift slowly; a minute of staleness is invisible on Home. */
        const val SIZES_TTL_MS = 60_000L
    }

    fun hasStoragePermission(): Boolean = Permissions.hasStorageAccess(appContext)
}
