package com.bunkwise.duplicatefilefinder.engage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bunkwise.duplicatefilefinder.appContainer

/** The once-a-day reminder check; [Engagement.runDaily] decides, NotificationOrchestrator posts. */
class EngagementWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.appContainer
        val last = c.scanResultRepository.current()
        Engagement.runDaily(
            applicationContext,
            lastScanAt = last?.finishedAtMs ?: 0L,
            leftoverBytes = last?.wastedBytes ?: 0L,
            busy = c.scanController.isRunning || c.deleteController.isRunning,
        ) { kind, value -> c.notifications.notifyReminder(kind, value) }
        return Result.success()
    }
}
