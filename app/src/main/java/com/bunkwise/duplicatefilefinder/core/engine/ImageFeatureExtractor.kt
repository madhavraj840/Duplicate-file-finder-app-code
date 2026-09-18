package com.bunkwise.duplicatefilefinder.core.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bunkwise.duplicatefilefinder.core.engine.algo.DHasher
import java.io.File
import kotlin.math.sqrt

/** Two independent perceptual hashes for one image plus its luminance spread. */
data class ImageHashes(
    val dHash: Long,
    val aHash: Long
)

/**
 * Decodes an image ONCE to a tiny bitmap and derives its perceptual features
 * (ARCHITECTURE §5.4 decode-once rule). Uses RGB_565 + inSampleSize so peak
 * memory per decode is a few KB. Returns null for unreadable/undecodable files.
 *
 * Two hashes are computed from the SAME grayscale grid:
 *   - dHash (gradient/structure) via [DHasher]
 *   - aHash (average-brightness) here
 * Requiring BOTH to agree is the "two independent signals" false-positive guard
 * (ARCHITECTURE §5.4/§5.6) that stops unrelated images from being grouped.
 * Near-flat images (solid colours, gradients, blank thumbnails) have almost no
 * structure and produce colliding hashes, so they are rejected up front.
 */
class ImageFeatureExtractor {

    /** Computes the 64-bit dHash for the image at [path], or null on failure. */
    fun dHash(path: String): Long? = hashes(path)?.dHash

    /**
     * Computes both perceptual hashes, or null when the image can't be decoded or
     * is too low-contrast to fingerprint reliably (see [MIN_STD_DEV]).
     */
    fun hashes(path: String): ImageHashes? {
        val small = decodeSmall(path) ?: return null
        try {
            val scaled = Bitmap.createScaledBitmap(small, DHasher.WIDTH, DHasher.HEIGHT, true)
            val gray = IntArray(DHasher.WIDTH * DHasher.HEIGHT)
            val pixels = IntArray(DHasher.WIDTH * DHasher.HEIGHT)
            scaled.getPixels(pixels, 0, DHasher.WIDTH, 0, 0, DHasher.WIDTH, DHasher.HEIGHT)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                // Rec. 601 luma.
                gray[i] = (r * 299 + g * 587 + b * 114) / 1000
            }
            if (scaled != small) scaled.recycle()

            // Reject near-flat images: their fingerprints collide with everything.
            if (standardDeviation(gray) < MIN_STD_DEV) return null
            return ImageHashes(dHash = DHasher.dHash(gray), aHash = aHash(gray))
        } catch (t: Throwable) {
            return null
        } finally {
            small.recycle()
        }
    }

    /** 64-bit average hash over the 8x8 block of the [DHasher.WIDTH]x8 grid. */
    private fun aHash(gray: IntArray): Long {
        var sum = 0
        for (row in 0 until DHasher.HEIGHT) {
            val base = row * DHasher.WIDTH
            for (col in 0 until 8) sum += gray[base + col]
        }
        val mean = sum / 64
        var hash = 0L
        var bit = 0
        for (row in 0 until DHasher.HEIGHT) {
            val base = row * DHasher.WIDTH
            for (col in 0 until 8) {
                if (gray[base + col] >= mean) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    /** Standard deviation of the 8x8 luminance block (0-255 scale). */
    private fun standardDeviation(gray: IntArray): Double {
        var sum = 0
        for (row in 0 until DHasher.HEIGHT) {
            val base = row * DHasher.WIDTH
            for (col in 0 until 8) sum += gray[base + col]
        }
        val mean = sum / 64.0
        var acc = 0.0
        for (row in 0 until DHasher.HEIGHT) {
            val base = row * DHasher.WIDTH
            for (col in 0 until 8) {
                val d = gray[base + col] - mean
                acc += d * d
            }
        }
        return sqrt(acc / 64.0)
    }

    private fun decodeSmall(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            // 1) bounds only.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            // 2) decode downscaled to ~TARGET px on the long edge.
            val target = 32
            var sample = 1
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            while (longEdge / (sample * 2) >= target) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(path, opts)
        } catch (t: Throwable) {
            null
        }
    }

    private companion object {
        /** Below this luminance spread an image is too flat to fingerprint. */
        const val MIN_STD_DEV = 8.0
    }
}
