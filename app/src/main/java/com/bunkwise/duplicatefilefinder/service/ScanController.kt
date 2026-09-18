package com.bunkwise.duplicatefilefinder.service

import com.bunkwise.duplicatefilefinder.core.domain.error.AppError
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanMode
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanProgress
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ScanStatus {
    data object Idle : ScanStatus
    data class Running(val mode: ScanMode) : ScanStatus
    data class Completed(val result: ScanResult) : ScanStatus
    data class Failed(val error: AppError.Scan) : ScanStatus
    data object Stopped : ScanStatus
}

/**
 * Singleton bridge between the ScanForegroundService (single writer) and the UI
 * (observers). The service owns all writes; ViewModels only read. This keeps the
 * scan alive across screen/config changes without the ViewModel holding it.
 */
class ScanController {
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    private val _status = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val status: StateFlow<ScanStatus> = _status.asStateFlow()

    @Volatile
    var isRunning = false
        private set

    fun onStart(mode: ScanMode) {
        isRunning = true
        _progress.value = ScanProgress()
        _status.value = ScanStatus.Running(mode)
    }

    fun onProgress(p: ScanProgress) { _progress.value = p }

    fun onCompleted(result: ScanResult) {
        isRunning = false
        _status.value = ScanStatus.Completed(result)
    }

    fun onFailed(error: AppError.Scan) {
        isRunning = false
        _status.value = ScanStatus.Failed(error)
    }

    fun onStopped() {
        isRunning = false
        _status.value = ScanStatus.Stopped
    }

    /** Called by the UI once a terminal status has been handled. */
    fun reset() {
        _status.value = ScanStatus.Idle
        _progress.value = ScanProgress()
    }
}
