package com.bunkwise.duplicatefilefinder.core.designsystem

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.bunkwise.duplicatefilefinder.R

/**
 * The connected step selector rail (DESIGN §3.2): [count] node circles joined by
 * a line, with one selected. Labels live in the layout below, aligned to the same
 * evenly-spaced columns. Tapping a node (or anywhere in its column) selects it via
 * [onStepClick]; the label row forwards taps to the same callback.
 */
class StepConnectorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var count: Int = 4
        set(value) { field = value; invalidate() }

    var selected: Int = 0
        set(value) { field = value.coerceIn(0, count - 1); invalidate() }

    /** Invoked with the tapped node index when the user selects a step. */
    var onStepClick: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val primary = ContextCompat.getColor(context, R.color.primary)
    private val track = ContextCompat.getColor(context, R.color.track)
    private val white = ContextCompat.getColor(context, R.color.card_surface)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f * density }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * density
    }

    private val outer = 11f * density
    private val inner = 5f * density

    private var pendingIndex = -1

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (count < 2) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pendingIndex = nearestIndex(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (pendingIndex >= 0 && pendingIndex == nearestIndex(event.x)) {
                    val index = pendingIndex
                    pendingIndex = -1
                    performClick()
                    onStepClick?.invoke(index)
                    return true
                }
                pendingIndex = -1
            }
            MotionEvent.ACTION_CANCEL -> pendingIndex = -1
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Nearest node column for a horizontal touch position. */
    private fun nearestIndex(x: Float): Int {
        val margin = outer + 4f * density
        val usable = width - margin * 2
        if (usable <= 0f) return 0
        val step = usable / (count - 1)
        return Math.round((x - margin) / step).coerceIn(0, count - 1)
    }

    override fun onDraw(canvas: Canvas) {
        if (count < 2) return
        val cy = height / 2f
        val margin = outer + 4f * density
        val usable = width - margin * 2
        fun cx(i: Int) = margin + usable * i / (count - 1)

        // Connecting line: primary up to the selected node, track after.
        linePaint.color = track
        canvas.drawLine(cx(0), cy, cx(count - 1), cy, linePaint)
        linePaint.color = primary
        if (selected > 0) canvas.drawLine(cx(0), cy, cx(selected), cy, linePaint)

        for (i in 0 until count) {
            val x = cx(i)
            if (i == selected) {
                fillPaint.color = primary
                canvas.drawCircle(x, cy, outer, fillPaint)
                fillPaint.color = white
                canvas.drawCircle(x, cy, inner, fillPaint)
            } else {
                fillPaint.color = white
                canvas.drawCircle(x, cy, outer, fillPaint)
                strokePaint.color = track
                canvas.drawCircle(x, cy, outer, strokePaint)
            }
        }
    }
}
