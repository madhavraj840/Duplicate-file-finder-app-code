package com.bunkwise.duplicatefilefinder.core.common

import java.util.Locale
import kotlin.math.abs

/** Pure formatting utilities (ARCHITECTURE §core:common). No Android deps. */
object Formatters {

    /** 1_912_602_624 -> "1.78 GB". Uses binary units to match storage displays. */
    fun bytes(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = size.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return if (unit == 0) {
            "${size} B"
        } else {
            val rounded = String.format(Locale.US, if (value < 10) "%.2f" else "%.1f", value)
            "$rounded ${units[unit]}"
        }
    }

    /** 83_000 ms -> "01:23"; over an hour -> "1:02:03". */
    fun duration(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    /** 12456 -> "12,456" using the given locale's grouping separators. */
    fun count(n: Int, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%,d", n)

    fun percent(value: Int): String = "$value%"

    /** True if two longs are within [tolerance] of each other. */
    fun within(a: Long, b: Long, tolerance: Long): Boolean = abs(a - b) <= tolerance
}
