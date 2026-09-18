package com.bunkwise.duplicatefilefinder.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bunkwise.duplicatefilefinder.MainActivity
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanProgress
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanResult
import com.bunkwise.duplicatefilefinder.core.domain.repository.DeletionOutcome

/**
 * The ONLY writer of notifications (NOTIFICATION.TXT §6). Fixed ids so updates
 * replace rather than stack. Progress channels are LOW importance (silent).
 */
class NotificationOrchestrator(private val context: Context) {

    companion object {
        const val CH_SCAN = "scan_progress"
        const val CH_DELETE = "delete_progress"
        const val CH_RESULTS = "results"

        const val ID_SCAN = 1001
        const val ID_SCAN_RESULT = 1002
        const val ID_DELETE = 2001
        const val ID_DELETE_RESULT = 2002

        /** Intent extra read by MainActivity to deep-link a notification tap. */
        const val EXTRA_NAV = "extra_nav_target"
        const val NAV_RESULTS = "results"
    }

    private val manager get() = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SCAN, context.getString(R.string.channel_scan), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DELETE, context.getString(R.string.channel_delete), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULTS, context.getString(R.string.channel_results), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    // ---- scan ----

    fun buildScanProgress(p: ScanProgress): android.app.Notification {
        val indeterminate = p.filesScanned == 0
        val stop = PendingIntent.getService(
            context, ID_SCAN,
            Intent(context, ScanForegroundService::class.java).apply { action = ScanForegroundService.ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CH_SCAN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_scanning, p.percent))
            .setContentText(
                context.getString(
                    R.string.notif_scan_text,
                    Formatters.count(p.filesScanned), Formatters.count(p.duplicatesFound)
                )
            )
            .setProgress(100, p.percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .addAction(0, context.getString(R.string.stop_scan), stop)
            .build()
    }

    fun notifyScanComplete(result: ScanResult) {
        val text = if (result.groupCount == 0) {
            context.getString(R.string.notif_scan_done_empty)
        } else {
            context.getString(
                R.string.notif_scan_done_text,
                Formatters.count(result.totalDuplicateFiles), Formatters.count(result.groupCount)
            )
        }
        val title = if (result.groupCount == 0) {
            context.getString(R.string.notif_scan_done_clean)
        } else {
            context.getString(R.string.notif_scan_done_title, Formatters.bytes(result.wastedBytes))
        }
        // Tapping jumps straight to Results (not Home) so the user sees what was found.
        val nav = if (result.groupCount == 0) null else NAV_RESULTS
        safeNotify(ID_SCAN_RESULT, resultBuilder(title, text, nav).build())
    }

    /**
     * Result of a background scheduled scan. Silent when nothing was found. When
     * [cleanupPrompt] (Auto Cleanup on) the copy nudges the user toward cleanup;
     * otherwise it is a passive summary. Never deletes — tapping opens the app.
     */
    fun notifyAutoScanComplete(result: ScanResult, cleanupPrompt: Boolean) {
        if (result.groupCount == 0) return
        val title = if (cleanupPrompt) {
            context.getString(R.string.notif_auto_clean_title, Formatters.count(result.totalDuplicateFiles))
        } else {
            context.getString(R.string.notif_scan_done_title, Formatters.bytes(result.wastedBytes))
        }
        val text = if (cleanupPrompt) {
            context.getString(
                R.string.notif_auto_clean_text,
                Formatters.count(result.totalDuplicateFiles), Formatters.bytes(result.wastedBytes)
            )
        } else {
            context.getString(
                R.string.notif_scan_done_text,
                Formatters.count(result.totalDuplicateFiles), Formatters.count(result.groupCount)
            )
        }
        safeNotify(ID_SCAN_RESULT, resultBuilder(title, text, NAV_RESULTS).build())
    }

    // ---- delete ----

    fun buildDeleteProgress(p: DeleteProgress): android.app.Notification {
        val percent = if (p.total == 0) 0 else (p.deleted * 100 / p.total)
        return NotificationCompat.Builder(context, CH_DELETE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_deleting, percent))
            .setContentText(
                context.getString(
                    R.string.notif_delete_text,
                    Formatters.count(p.deleted), Formatters.count(p.total), Formatters.bytes(p.freedBytes)
                )
            )
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .build()
    }

    fun notifyDeleteComplete(outcome: DeletionOutcome, archived: Boolean) {
        val title = context.getString(R.string.notif_delete_done_title, Formatters.bytes(outcome.freedBytes))
        val text = if (archived) {
            context.getString(R.string.notif_delete_done_archived, Formatters.count(outcome.deleted))
        } else {
            context.getString(R.string.notif_delete_done_binned, Formatters.count(outcome.deleted))
        }
        safeNotify(ID_DELETE_RESULT, resultBuilder(title, text).build())
    }

    fun cancelScanProgress() = manager.cancel(ID_SCAN)
    fun cancelDeleteProgress() = manager.cancel(ID_DELETE)

    // ---- helpers ----

    private fun resultBuilder(title: String, text: String, navTarget: String? = null) =
        NotificationCompat.Builder(context, CH_RESULTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent(navTarget))

    private fun contentIntent(navTarget: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        navTarget?.let { intent.putExtra(EXTRA_NAV, it) }
        // A distinct request code per target keeps the extras from being coalesced
        // across the progress and result PendingIntents (FLAG_UPDATE_CURRENT).
        val requestCode = navTarget?.hashCode() ?: 0
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun safeNotify(id: Int, notification: android.app.Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — in-app UI is sufficient.
        }
    }
}
