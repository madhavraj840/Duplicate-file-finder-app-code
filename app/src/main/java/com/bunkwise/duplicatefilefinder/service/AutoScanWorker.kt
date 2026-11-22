package com.bunkwise.duplicatefilefinder.service

import com.bunkwise.duplicatefilefinder.ads.ProFeature
import com.bunkwise.duplicatefilefinder.ads.Unlocks
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Result as AppResult
import com.bunkwise.duplicatefilefinder.core.domain.engine.ScanRequest
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanMode
import com.bunkwise.duplicatefilefinder.presentation.common.Permissions

/**
 * Premium background scan (Settings › Schedule scan). Runs an EXACT duplicate scan
 * over the user's configured scan scope, persists the result so it shows in-app, and
 * posts a notification. Scan-and-notify only — it never deletes files.
 */
class AutoScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val settings = container.settingsRepository.current()

        if (!Unlocks.unlocked(settings.isPremium, ProFeature.SCHEDULE)) return Result.success()
        // Same check as the app itself: media access is enough to scan.
        if (!Permissions.hasStorageAccess(applicationContext)) return Result.success()
        // Never compete with the user: a manual scan or a cleanup in progress owns the
        // result the user is looking at. Skip this run; the next period will pick it up.
        if (container.scanController.isRunning || container.deleteController.isRunning) return Result.success()

        val request = ScanRequest(ScanMode.EXACT, setOf(FileCategory.ALL), fileBudget = Int.MAX_VALUE)
        val controller = container.scanController
        // Claim the scan slot like a manual scan does, so a manual scan or a cleanup
        // can't start mid-run and then have its result overwritten by this one.
        controller.onStart(ScanMode.EXACT)
        var finished = false
        // ponytail: no setForeground(), so WorkManager's ~10 min cap can cut very large
        // libraries short; promote to a foreground worker if users report that.
        try {
            return when (val outcome = container.duplicateEngine.scan(request) { controller.onProgress(it) }) {
                is AppResult.Success -> {
                    container.scanResultRepository.save(outcome.data)
                    container.fileEnumerator.invalidateSizes()
                    container.notifications.notifyAutoScanComplete(outcome.data)
                    controller.onCompleted(outcome.data)
                    finished = true
                    Result.success()
                }
                // A failed background scan isn't worth retrying with backoff: that repeats
                // the heaviest work the app does. The next scheduled run tries again.
                is AppResult.Error -> {
                    controller.onStopped()
                    finished = true
                    Result.success()
                }
            }
        } finally {
            // Cancelled by WorkManager (constraints lost, time cap): release the slot.
            if (!finished) controller.onStopped()
        }
    }

    companion object {
        const val WORK_NAME = "auto_scan"
    }
}
