package com.bunkwise.duplicatefilefinder.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanSchedule
import com.bunkwise.duplicatefilefinder.core.domain.repository.AppSettings
import java.util.concurrent.TimeUnit

/**
 * Registers/cancels the periodic [AutoScanWorker] to match the current settings.
 * The job runs when the user is premium AND either a Schedule Scan cadence is set
 * or Auto Cleanup is on (in which case it defaults to weekly). Idempotent: an
 * in-memory guard avoids re-enqueuing on every settings emission.
 */
class AutoScanScheduler(private val context: Context) {

    // null = unknown (fresh process), -1 = known cancelled, >0 = scheduled interval hours.
    @Volatile
    private var scheduledHours: Long? = null

    fun sync(settings: AppSettings) {
        val enabled = settings.isPremium &&
            (settings.scanSchedule != ScanSchedule.OFF || settings.autoCleanup)

        if (!enabled) {
            if (scheduledHours == CANCELLED) return
            WorkManager.getInstance(context).cancelUniqueWork(AutoScanWorker.WORK_NAME)
            scheduledHours = CANCELLED
            return
        }

        val hours = settings.scanSchedule.intervalHours ?: DEFAULT_HOURS
        if (scheduledHours == hours) return

        val request = PeriodicWorkRequestBuilder<AutoScanWorker>(hours, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AutoScanWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
        scheduledHours = hours
    }

    private companion object {
        const val CANCELLED = -1L
        const val DEFAULT_HOURS = 24L * 7 // Auto Cleanup with no explicit schedule → weekly
    }
}
