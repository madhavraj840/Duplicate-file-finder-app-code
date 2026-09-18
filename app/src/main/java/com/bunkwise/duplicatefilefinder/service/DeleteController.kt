package com.bunkwise.duplicatefilefinder.service

import com.bunkwise.duplicatefilefinder.core.domain.repository.DeletionOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeleteProgress(
    val deleted: Int = 0,
    val total: Int = 0,
    val freedBytes: Long = 0L,
    val currentName: String = "",
    val elapsedMs: Long = 0L
)

sealed interface DeleteStatus {
    data object Idle : DeleteStatus
    data object Running : DeleteStatus
    data class Completed(val outcome: DeletionOutcome, val archived: Boolean) : DeleteStatus
    data object Failed : DeleteStatus
}

/** Singleton bridge for the DeleteForegroundService, mirroring [ScanController]. */
class DeleteController {
    private val _progress = MutableStateFlow(DeleteProgress())
    val progress: StateFlow<DeleteProgress> = _progress.asStateFlow()

    private val _status = MutableStateFlow<DeleteStatus>(DeleteStatus.Idle)
    val status: StateFlow<DeleteStatus> = _status.asStateFlow()

    @Volatile
    var isRunning = false
        private set

    fun onStart(total: Int) {
        isRunning = true
        _progress.value = DeleteProgress(total = total)
        _status.value = DeleteStatus.Running
    }

    fun onProgress(p: DeleteProgress) { _progress.value = p }

    fun onCompleted(outcome: DeletionOutcome, archived: Boolean) {
        isRunning = false
        _status.value = DeleteStatus.Completed(outcome, archived)
    }

    fun onFailed() {
        isRunning = false
        _status.value = DeleteStatus.Failed
    }

    fun reset() {
        _status.value = DeleteStatus.Idle
        _progress.value = DeleteProgress()
    }
}
