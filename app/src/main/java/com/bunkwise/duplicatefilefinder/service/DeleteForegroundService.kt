package com.bunkwise.duplicatefilefinder.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs a deletion/archive batch in the foreground (ARCHITECTURE §7.4,
 * NOTIFICATION.TXT §3). No Stop action — batches are per-file atomic (DESIGN
 * §3.7). Reads the confirmed file list from [AppContainer.pendingDeletionFiles].
 */
class DeleteForegroundService : Service() {

    companion object {
        const val ACTION_START = "action_start_delete"

        fun start(context: Context) {
            val intent = Intent(context, DeleteForegroundService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) startDelete()
        return START_NOT_STICKY
    }

    private fun startDelete() {
        val container = appContainer
        if (container.deleteController.isRunning) return
        val files = container.pendingDeletionFiles
        val archive = container.pendingArchive
        if (files.isEmpty()) { stopSelf(); return }

        container.deleteController.onStart(files.size)
        val started = System.currentTimeMillis()
        startAsForeground(container.notifications.buildDeleteProgress(DeleteProgress(total = files.size)))

        job = scope.launch {
            val treeUri = if (archive) container.settingsRepository.current().archiveTreeUri else null
            val result = container.deletionEngine.delete(files, archive, treeUri) { deleted, total, freed, current ->
                val dp = DeleteProgress(deleted, total, freed, current, System.currentTimeMillis() - started)
                container.deleteController.onProgress(dp)
                maybeUpdateNotification(dp)
            }
            when (result) {
                is Result.Success -> {
                    val outcome = result.data
                    // Remove exactly the files that were deleted — a skip anywhere in the
                    // batch must not shift which ids get dropped from the results.
                    if (outcome.deletedIds.isNotEmpty()) container.scanResultRepository.removeFiles(outcome.deletedIds)
                    container.notifications.notifyDeleteComplete(outcome, archive)
                    container.deleteController.onCompleted(outcome, archive)
                }
                is Result.Error -> container.deleteController.onFailed()
            }
            container.pendingDeletionFiles = emptyList()
            finish()
        }
    }

    private fun maybeUpdateNotification(p: DeleteProgress) {
        val now = System.currentTimeMillis()
        if (now - lastNotify < 500) return
        lastNotify = now
        runCatching {
            ServiceCompat.startForeground(
                this, NotificationOrchestrator.ID_DELETE,
                appContainer.notifications.buildDeleteProgress(p), foregroundType()
            )
        }
    }

    private fun startAsForeground(notification: android.app.Notification) {
        ServiceCompat.startForeground(this, NotificationOrchestrator.ID_DELETE, notification, foregroundType())
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        appContainer.notifications.cancelDeleteProgress()
        stopSelf()
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }
}
