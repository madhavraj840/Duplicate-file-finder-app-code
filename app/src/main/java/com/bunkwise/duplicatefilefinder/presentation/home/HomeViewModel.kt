package com.bunkwise.duplicatefilefinder.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunkwise.duplicatefilefinder.core.data.QuotaRepositoryImpl
import com.bunkwise.duplicatefilefinder.core.domain.model.BinEntry
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.QuotaInfo
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanMode
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanResult
import com.bunkwise.duplicatefilefinder.core.domain.model.StorageInfo
import com.bunkwise.duplicatefilefinder.core.domain.repository.FileEnumerator
import com.bunkwise.duplicatefilefinder.core.domain.repository.QuotaRepository
import com.bunkwise.duplicatefilefinder.core.domain.repository.RecycleBinRepository
import com.bunkwise.duplicatefilefinder.core.domain.repository.ScanResultRepository
import com.bunkwise.duplicatefilefinder.core.domain.repository.StorageStatsProvider
import com.bunkwise.duplicatefilefinder.core.domain.usecase.CheckScanQuotaUseCase
import com.bunkwise.duplicatefilefinder.core.domain.usecase.QuotaDecision
import com.bunkwise.duplicatefilefinder.presentation.common.Permissions
import com.bunkwise.duplicatefilefinder.service.DeleteController
import com.bunkwise.duplicatefilefinder.service.DeleteStatus
import com.bunkwise.duplicatefilefinder.service.ScanController
import com.bunkwise.duplicatefilefinder.service.ScanStatus
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
    scanResults: ScanResultRepository,
    bin: RecycleBinRepository,
    private val scanController: ScanController,
    deleteController: DeleteController
) : ViewModel() {

    data class HomeState(
        val hasPermission: Boolean = false,
        val storage: StorageInfo? = null,
        val quota: QuotaInfo? = null,
        val sizeByCategory: Map<FileCategory, Long> = emptyMap(),
        val selectedMode: ScanMode = ScanMode.EXACT,
        /** Concrete types only (never ALL); all five selected means "everything". */
        val selectedTypes: Set<FileCategory> = SCAN_TYPES.toSet(),
        val lastResult: ScanResult? = null,
        val bin: List<BinEntry> = emptyList(),
        /** Percent of a scan running in the background, or null when idle. */
        val runningPercent: Int? = null
    )

    sealed interface HomeEvent {
        data class NavigateToScan(
            val mode: ScanMode, val categories: Set<FileCategory>, val budget: Int
        ) : HomeEvent
        data object RequestStorageAccess : HomeEvent
        data class ShowQuotaDialog(
            val estimate: Int, val remaining: Int, val mode: ScanMode, val categories: Set<FileCategory>
        ) : HomeEvent
        data object ScanFailed : HomeEvent
    }

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * A scan waiting for the notification-permission answer. Kept here, not in the
     * fragment, so rotating the phone while the system dialog is open doesn't drop it.
     */
    var pendingScan: HomeEvent.NavigateToScan? = null

    init {
        viewModelScope.launch { scanResults.observe().collect { r -> _state.update { it.copy(lastResult = r) } } }
        viewModelScope.launch { bin.observe().collect { b -> _state.update { it.copy(bin = b) } } }
        viewModelScope.launch {
            scanController.progress.collect { p ->
                if (scanController.isRunning) _state.update { it.copy(runningPercent = p.percent) }
            }
        }
        viewModelScope.launch {
            scanController.status.collect { status ->
                when (status) {
                    is ScanStatus.Running -> _state.update { it.copy(runningPercent = it.runningPercent ?: 0) }
                    is ScanStatus.Failed -> {
                        // Don't reset() here: if the Scan screen is open it must still see
                        // Failed, or it spins forever. It consumes the status itself.
                        _state.update { it.copy(runningPercent = null) }
                        _events.send(HomeEvent.ScanFailed)
                    }
                    else -> _state.update { it.copy(runningPercent = null) }
                }
            }
        }
        // Deleting files changes the type sizes; the delete service already
        // invalidated the enumerator's cache, so just re-read it.
        viewModelScope.launch {
            deleteController.status.collect { status ->
                if (status is DeleteStatus.Completed && _state.value.hasPermission) refreshSizes()
            }
        }
    }

    fun refresh() {
        val granted = hasStoragePermission()
        _state.update { it.copy(hasPermission = granted) }
        viewModelScope.launch {
            _state.update { it.copy(storage = storage.current(), quota = quota.current()) }
            // Cheap on every visit: the enumerator only re-walks MediaStore when the
            // media library actually changed since the last computation.
            if (granted) refreshSizes()
        }
    }

    private suspend fun refreshSizes() {
        // On failure keep the previous values; flashing back to 0 B is worse than
        // briefly showing slightly outdated sizes.
        runCatching { enumerator.sizeByCategory() }.getOrNull()?.let { sizes ->
            _state.update { it.copy(sizeByCategory = sizes) }
        }
    }

    fun onModeSelected(mode: ScanMode) = _state.update { it.copy(selectedMode = mode) }

    fun onTypeToggled(type: FileCategory, checked: Boolean) = _state.update { s ->
        s.copy(selectedTypes = if (checked) s.selectedTypes + type else s.selectedTypes - type)
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
        if (s.selectedTypes.isEmpty()) return
        val categories = if (s.selectedTypes.size == SCAN_TYPES.size) setOf(FileCategory.ALL) else s.selectedTypes
        viewModelScope.launch {
            when (val decision = checkQuota(categories)) {
                is QuotaDecision.Allowed -> {
                    val q = quota.current()
                    val budget = if (q.isPremium) Int.MAX_VALUE else q.remaining.coerceAtLeast(1)
                    _events.send(HomeEvent.NavigateToScan(s.selectedMode, categories, budget))
                }
                is QuotaDecision.Exceeds -> _events.send(
                    HomeEvent.ShowQuotaDialog(decision.estimate, decision.remaining, s.selectedMode, categories)
                )
            }
        }
    }

    /** From the quota dialog: scan only as many files as are left today. */
    fun scanWithinLimit(mode: ScanMode, categories: Set<FileCategory>, remaining: Int) {
        if (remaining <= 0) return
        viewModelScope.launch { _events.send(HomeEvent.NavigateToScan(mode, categories, remaining)) }
    }

    /** Rewarded ad watched: add the bonus, then re-run the quota check. */
    fun onAdReward() {
        viewModelScope.launch {
            quota.addBonus(QuotaRepositoryImpl.AD_BONUS)
            _state.update { it.copy(quota = quota.current()) }
            onStartScan()
        }
    }

    fun hasStoragePermission(): Boolean = Permissions.hasStorageAccess(appContext)

    companion object {
        /** The file types offered on the Scan tab, in display order. */
        val SCAN_TYPES = listOf(
            FileCategory.IMAGES, FileCategory.VIDEOS, FileCategory.AUDIO,
            FileCategory.DOCUMENTS, FileCategory.OTHER
        )
    }
}
