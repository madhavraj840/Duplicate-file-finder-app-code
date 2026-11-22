package com.bunkwise.duplicatefilefinder.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Result
import com.bunkwise.duplicatefilefinder.core.domain.error.AppError
import com.bunkwise.duplicatefilefinder.core.domain.engine.ScanRequest
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanMode
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanPhase
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs a scan in the foreground so it survives app minimize with a live progress
 * notification (ARCHITECTURE §6, NOTIFICATION.TXT §2). START_NOT_STICKY: a killed
 * scan does not auto-resume (§12) — results were in-memory.
 */
class ScanForegroundService : Service() {

    companion object {
        const val ACTION_START = "action_start_scan"
        const val ACTION_STOP = "action_stop_scan"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_CATEGORIES = "extra_categories"
        const val EXTRA_BUDGET = "extra_budget"

        /**
         * Starts a scan. The controller flips to Running synchronously, before the UI
         * navigates, so the Scan screen can never act on a stale Completed status left
         * over from an earlier scan. Returns false if a scan is already running.
         */
        fun start(context: Context, mode: ScanMode, categories: Set<FileCategory>, budget: Int): Boolean {
            val controller = context.appContainer.scanController
            if (controller.isRunning) return false
            controller.onStart(mode)
            val intent = Intent(context, ScanForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_CATEGORIES, categories.map { it.name }.toTypedArray())
                putExtra(EXTRA_BUDGET, budget)
            }
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }.onFailure { controller.onFailed(AppError.Scan.UNKNOWN) }.isSuccess
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ScanForegroundService::class.java).apply { action = ACTION_STOP })
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    // Written from the engine's parallel IO workers via the progress callback.
    @Volatile private var lastNotify = 0L
    @Volatile private var lastScanned = 0
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopScan(); return START_NOT_STICKY }
            ACTION_START -> startScan(intent)
        }
        return START_NOT_STICKY
    }

    private fun startScan(intent: Intent) {
        val container = appContainer
        // Every startForegroundService() must be answered with startForeground().
        startAsForeground(container.notifications.buildScanProgress(ScanProgress()))
        if (job?.isActive == true) return // idempotent

        val mode = runCatching { ScanMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "") }
            .getOrDefault(ScanMode.EXACT)
        val categories = (intent.getStringArrayExtra(EXTRA_CATEGORIES) ?: emptyArray())
            .mapNotNull { runCatching { FileCategory.valueOf(it) }.getOrNull() }.toSet()
            .ifEmpty { setOf(FileCategory.ALL) }
        val budget = intent.getIntExtra(EXTRA_BUDGET, Int.MAX_VALUE)

        acquireWakeLock()

        job = scope.launch {
            val result = container.duplicateEngine.scan(ScanRequest(mode, categories, budget)) { p ->
                lastScanned = p.filesScanned
                container.scanController.onProgress(p)
                maybeUpdateNotification(p)
            }
            when (result) {
                is Result.Success -> {
                    container.scanResultRepository.save(result.data)
                    container.fileEnumerator.invalidateSizes()
                    if (lastScanned > 0) container.quotaRepository.addUsage(lastScanned)
                    container.notifications.notifyScanComplete(result.data)
                    container.scanController.onCompleted(result.data)
                }
                is Result.Error -> container.scanController.onFailed(result.error)
            }
            finish()
        }
    }

    private fun stopScan() {
        job?.cancel()
        appContainer.scanController.onStopped()
        finish()
    }

    private fun maybeUpdateNotification(p: ScanProgress) {
        val now = System.currentTimeMillis()
        // Always let the terminal (100% / DONE) update through — otherwise the 500 ms
        // throttle can swallow it and the bar appears stuck at the last grouping tick.
        val terminal = p.phase == ScanPhase.DONE || p.percent >= 100
        if (!terminal && now - lastNotify < 500) return
        lastNotify = now
        runCatching {
            ServiceCompat.startForeground(
                this, NotificationOrchestrator.ID_SCAN,
                appContainer.notifications.buildScanProgress(p), foregroundType()
            )
        }
    }

    private fun startAsForeground(notification: android.app.Notification) {
        ServiceCompat.startForeground(this, NotificationOrchestrator.ID_SCAN, notification, foregroundType())
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0

    /**
     * A partial wakelock keeps the CPU alive while the screen is off. Without it,
     * aggressive OEM power management (Samsung/Xiaomi) throttles or pauses the scan
     * once the device dozes, even though the foreground notification stays up. The
     * 30-minute timeout is a safety net so a wedged scan can never pin the CPU.
     */
    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dff:scan").apply {
                setReferenceCounted(false)
                acquire(30 * 60 * 1000L)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    private fun finish() {
        releaseWakeLock()
        // REMOVE (not DETACH) so the progress notification is torn down atomically as
        // we leave the foreground. DETACH + cancel raced on some devices and left the
        // bar frozen at 100%, making the app look stuck. The "scan complete" summary
        // lives under a different id (ID_SCAN_RESULT), so it is unaffected by this.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        appContainer.notifications.cancelScanProgress()
        stopSelf()
    }

    /**
     * Android 15+: dataSync services get ~6h per 24h. Not stopping within seconds of
     * this callback crashes the app, so end the scan the same way Stop does.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopScan()
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.coroutineContext[Job]?.cancel()
        // Killed before reaching a terminal state (OEM task killer, "stop app"): release
        // the controller, otherwise every later scan is refused and the UI spins forever.
        val controller = appContainer.scanController
        if (controller.isRunning) controller.onStopped()
        super.onDestroy()
    }
}
