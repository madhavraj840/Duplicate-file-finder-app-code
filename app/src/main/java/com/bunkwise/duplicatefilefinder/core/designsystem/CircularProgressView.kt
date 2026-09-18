package com.bunkwise.duplicatefilefinder.core.designsystem

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.bunkwise.duplicatefilefinder.R
import kotlin.math.min

/**
 * Percent ring with a centred label (DESIGN §1.4). Used on Home, Scan and Delete.
 * Animates between values (no teleporting) per DESIGN §1.5. Draws only in onDraw
 * with no per-frame allocation.
 */
class CircularProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.track)
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.primary)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val oval = RectF()
    private var animator: ValueAnimator? = null

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 100f)
            invalidate()
        }

    var showText: Boolean = true
    var strokeWidthDp: Float = 12f
        set(value) { field = value; invalidate() }

    /** Opt-in sweep gradient (primary -> accent) for the big Scan ring. */
    var useGradient: Boolean = false
        set(value) { field = value; gradient = null; invalidate() }
    private var gradient: SweepGradient? = null
    private val gradientMatrix = Matrix()

    /** Spinner mode for phases with no meaningful percent yet (enumeration). */
    private var indeterminate = false
    private var sweepStart = 0f
    private var sweepAnimator: ValueAnimator? = null

    fun setProgressColor(colorInt: Int) {
        progressPaint.color = colorInt
        textPaint.color = colorInt
        useGradient = false
        invalidate()
    }

    fun setTextColorInt(colorInt: Int) {
        textPaint.color = colorInt
        invalidate()
    }

    fun animateTo(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, target.coerceIn(0f, 100f)).apply {
            duration = 450
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float }
            start()
        }
    }

    fun setIndeterminate(on: Boolean) {
        if (indeterminate == on) return
        indeterminate = on
        sweepAnimator?.cancel()
        sweepAnimator = null
        if (on) {
            animator?.cancel()
            sweepAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 1100
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { sweepStart = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = strokeWidthDp * resources.displayMetrics.density
        trackPaint.strokeWidth = stroke
        progressPaint.strokeWidth = stroke

        val size = min(width, height).toFloat()
        val pad = stroke / 2f + 2f
        val left = (width - size) / 2f + pad
        val top = (height - size) / 2f + pad
        oval.set(left, top, left + size - pad * 2, top + size - pad * 2)

        if (useGradient && gradient == null) {
            val accent = ContextCompat.getColor(context, R.color.onboarding_accent)
            gradient = SweepGradient(
                oval.centerX(), oval.centerY(),
                intArrayOf(progressPaint.color, accent, progressPaint.color),
                floatArrayOf(0f, 0.5f, 1f)
            )
        }
        // Rotate the gradient so its seam sits at the arc start (12 o'clock).
        progressPaint.shader = if (useGradient) gradient?.apply {
            gradientMatrix.setRotate(if (indeterminate) sweepStart else -90f, oval.centerX(), oval.centerY())
            setLocalMatrix(gradientMatrix)
        } else null

        canvas.drawArc(oval, 0f, 360f, false, trackPaint)
        if (indeterminate) {
            canvas.drawArc(oval, sweepStart, 100f, false, progressPaint)
        } else {
            canvas.drawArc(oval, -90f, 360f * (progress / 100f), false, progressPaint)
        }

        if (showText && !indeterminate) {
            textPaint.textSize = size * 0.24f
            val label = "${progress.toInt()}%"
            val cy = oval.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, oval.centerX(), cy, textPaint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradient = null
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        sweepAnimator?.cancel()
        sweepAnimator = null
        super.onDetachedFromWindow()
    }
}
