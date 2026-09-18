package com.bunkwise.duplicatefilefinder.core.designsystem

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.bunkwise.duplicatefilefinder.R
import kotlin.random.Random

/**
 * One-shot celebratory confetti burst (DESIGN §1.5, matches the hand-rolled
 * Canvas views in this package). Call [start] when a completion screen appears;
 * particles rain down once, then the view stops and hides itself. Purely
 * decorative: never clickable, lets touches pass through, and pauses when
 * detached to avoid a stray animation loop / leak.
 */
class ConfettiView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var angle: Float, var angularVel: Float,
        val halfW: Float, val halfH: Float,
        val color: Int,
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val density = resources.displayMetrics.density
    private val gravity = 260f * density        // px/s^2, gentle downward accel
    private val palette = intArrayOf(
        R.color.primary, R.color.success, R.color.premium_gold,
        R.color.cat_images, R.color.cat_videos, R.color.cat_audio, R.color.cat_documents,
    ).map { ContextCompat.getColor(context, it) }

    private val particles = ArrayList<Particle>(PARTICLE_COUNT)
    private var pendingStart = false
    private var running = false
    private var lastFrameNanos = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()

            if (pendingStart) {
                if (width == 0 || height == 0) { postOnAnimation(this); return } // wait for layout
                spawn()
                pendingStart = false
                lastFrameNanos = now
            }

            val dt = if (lastFrameNanos == 0L) 0f
                else ((now - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.05f)
            lastFrameNanos = now

            var alive = false
            for (p in particles) {
                p.vy += gravity * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.angle += p.angularVel * dt
                if (p.y - p.halfH <= height) alive = true
            }
            invalidate()

            if (alive) {
                postOnAnimation(this)
            } else {
                running = false
                particles.clear()
                isVisible = false
            }
        }
    }

    /** Fire the burst. Safe to call before the view is laid out (spawns on first frame). */
    fun start() {
        isVisible = true
        particles.clear()
        pendingStart = true
        running = true
        lastFrameNanos = 0L
        removeCallbacks(ticker)
        postOnAnimation(ticker)
    }

    private fun spawn() {
        particles.clear()
        for (i in 0 until PARTICLE_COUNT) {
            particles.add(
                Particle(
                    x = Random.nextFloat() * width,
                    // Stagger above the top edge so they stream in rather than pop at once.
                    y = -Random.nextFloat() * height * 0.4f,
                    vx = (Random.nextFloat() - 0.5f) * 120f * density,
                    vy = (120f + Random.nextFloat() * 160f) * density,
                    angle = Random.nextFloat() * 360f,
                    angularVel = (Random.nextFloat() - 0.5f) * 720f,
                    halfW = (2.5f + Random.nextFloat() * 2f) * density,
                    halfH = (4f + Random.nextFloat() * 3f) * density,
                    color = palette[Random.nextInt(palette.size)],
                )
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        for (p in particles) {
            paint.color = p.color
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.angle)
            canvas.drawRect(-p.halfW, -p.halfH, p.halfW, p.halfH, paint)
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val PARTICLE_COUNT = 90
    }
}
