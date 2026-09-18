package com.bunkwise.duplicatefilefinder.core.designsystem

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.bunkwise.duplicatefilefinder.R
import kotlin.math.abs
import kotlin.math.sin

/**
 * Three dots with a staggered translateY bounce, 900 ms loop (DESIGN §1.5).
 * Driven by a single postInvalidateOnAnimation loop; paused when detached to
 * save battery and avoid leaks.
 */
class BouncingDotsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
        style = Paint.Style.FILL
    }
    private val density = resources.displayMetrics.density
    private val radius = 4f * density
    private val gap = 7f * density
    private var startTime = 0L
    private var running = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTime = System.currentTimeMillis()
        running = true
        postOnAnimation(ticker)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (radius * 6 + gap * 2 + paddingLeft + paddingRight).toInt()
        val h = (radius * 4 + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(
            resolveSize(w, widthMeasureSpec),
            resolveSize(h, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val t = (System.currentTimeMillis() - startTime) % 900L / 900f
        val cy = height / 2f
        val totalWidth = radius * 6 + gap * 2
        var cx = (width - totalWidth) / 2f + radius
        for (i in 0 until 3) {
            val phase = t - i * 0.18f
            val bounce = abs(sin(phase * Math.PI)).toFloat()
            val alpha = 0.4f + 0.6f * bounce
            paint.alpha = (alpha * 255).toInt().coerceIn(60, 255)
            canvas.drawCircle(cx, cy - bounce * 5f * density, radius, paint)
            cx += radius * 2 + gap
        }
    }
}
