package com.bunkwise.duplicatefilefinder.core.designsystem

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Lightweight donut chart (DESIGN §3.4). Single onDraw, no allocation in draw.
 * Replaces a chart library to protect APK size (KISS).
 */
class DonutChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Slice(val value: Float, val color: Int)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val oval = RectF()

    private var slices: List<Slice> = emptyList()
    private var total: Float = 0f
    var holeColor: Int = android.graphics.Color.WHITE
        set(value) { field = value; holePaint.color = value; invalidate() }

    fun setSlices(slices: List<Slice>) {
        this.slices = slices.filter { it.value > 0f }
        this.total = this.slices.sumOf { it.value.toDouble() }.toFloat()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (total <= 0f) return
        val size = min(width, height).toFloat()
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        oval.set(left, top, left + size, top + size)

        var start = -90f
        val gap = 2f
        for (s in slices) {
            val sweep = 360f * (s.value / total) - gap
            paint.color = s.color
            canvas.drawArc(oval, start, sweep.coerceAtLeast(0f), true, paint)
            start += 360f * (s.value / total)
        }
        // Punch the hole.
        holePaint.color = holeColor
        canvas.drawCircle(oval.centerX(), oval.centerY(), size * 0.29f, holePaint)
    }
}
