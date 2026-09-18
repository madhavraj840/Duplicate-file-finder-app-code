package com.bunkwise.duplicatefilefinder.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.domain.engine.ScanRequest
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanMode

/**
 * Premium background scan (Settings › Schedule Scan / Auto Cleanup). Runs an EXACT
 * duplicate scan over the user's configured scan scope, persists the result so it
 * shows in-app, and posts a notification. Per the product decision this is
 * scan-and-notify only — it never deletes files automatically.
 */
class AutoScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val settings = container.settingsRepository.current()

        // Scheduled scans are premium-only and need storage access to enumerate.
        if (!settings.isPremium) return Result.success()
        if (!hasStoragePermission()) return Result.success()

        val request = ScanRequest(ScanMode.EXACT, setOf(FileCategory.ALL), fileBudget = Int.MAX_VALUE)
        return when (val outcome = container.duplicateEngine.scan(request) { /* no progress UI */ }) {
            is com.bunkwise.duplicatefilefinder.core.common.Result.Success -> {
                container.scanResultRepository.save(outcome.data)
                container.notifications.notifyAutoScanComplete(outcome.data, settings.autoCleanup)
                Result.success()
            }
            is com.bunkwise.duplicatefilefinder.core.common.Result.Error -> Result.retry()
        }
    }

    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    companion object {
        const val WORK_NAME = "auto_scan"
    }
}
