package com.bunkwise.duplicatefilefinder

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Lite mode for phones that can't keep up (ported from the Night Mode app). Low-end phones
 * (Android's low-RAM flag, or under 3 GB) start in it; any other phone is watched while the app is
 * open and switched over if animations run below 60% of the refresh rate (and under 55 fps) for two
 * 2-second stretches in a row. The switch lasts until the next process start ([init]).
 *
 * Lite: no live blur, the Pro badge plays once instead of looping, no infinite animations.
 */
object Performance {
    var lite by mutableStateOf(false)
        private set

    private const val WINDOW_NS = 2_000_000_000L
    /** A gap this long means the animation stopped, not that a frame was late. */
    private const val IDLE_GAP_NS = 250_000_000L

    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private val frames by lazy { Handler(HandlerThread("frame-watch").apply { start() }.looper) }
    private val main by lazy { Handler(Looper.getMainLooper()) }

    internal fun isLowEnd(lowRam: Boolean, totalMem: Long): Boolean = lowRam || totalMem < (3L shl 30)

    internal fun isSlow(fps: Float, refresh: Float): Boolean = fps < minOf(55f, refresh * 0.6f)

    fun init(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val memory = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        lite = isLowEnd(am.isLowRamDevice, memory.totalMem)
    }

    /** Watch [window]'s frames while it's in front (onResume); [unwatch] on pause. */
    fun watch(window: Window) {
        if (lite || listener != null) return
        val refresh = window.decorView.display?.refreshRate?.takeIf { it > 1f } ?: 60f
        var spanStart = 0L
        var last = 0L
        var count = 0
        var slowSpans = 0
        val l = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            // INTENDED_VSYNC_TIMESTAMP / FIRST_DRAW_FRAME are API 26+; below that, total
            // duration accumulation is not worth it - those phones are low-end via init() anyway.
            if (Build.VERSION.SDK_INT < 26) return@OnFrameMetricsAvailableListener
            val vsync = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L || last == 0L || vsync - last > IDLE_GAP_NS) {
                spanStart = vsync
                count = 0
            } else {
                count++
            }
            last = vsync
            if (vsync - spanStart >= WINDOW_NS) {
                val fps = count * 1e9f / (vsync - spanStart)
                slowSpans = if (isSlow(fps, refresh)) slowSpans + 1 else 0
                spanStart = vsync
                count = 0
                if (slowSpans >= 2) main.post { goLite(window) }
            }
        }
        runCatching { window.addOnFrameMetricsAvailableListener(l, frames) }.onSuccess { listener = l }
    }

    fun unwatch(window: Window) {
        listener?.let { runCatching { window.removeOnFrameMetricsAvailableListener(it) } }
        listener = null
    }

    private fun goLite(window: Window) {
        if (lite) return
        lite = true
        unwatch(window)
    }
}
