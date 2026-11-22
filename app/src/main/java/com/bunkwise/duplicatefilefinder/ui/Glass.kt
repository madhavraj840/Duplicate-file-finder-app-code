package com.bunkwise.duplicatefilefinder.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

/**
 * Frosted glass: [glassSource] records the page into a GPU layer, [glassEffect] draws it blurred.
 * Replaces Haze, whose source draws the page *only* through that layer - a software canvas
 * (Clarity's session capture) throws on it, so recordings showed nothing but the nav bar. Here a
 * software canvas gets the page drawn plainly and the glass its flat tint.
 */
class Glass(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlass(): Glass {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Glass(layer) }
}

private val DrawScope.gpu get() = drawContext.canvas.nativeCanvas.isHardwareAccelerated

fun Modifier.glassSource(glass: Glass): Modifier = this
    .onGloballyPositioned { glass.origin = it.positionInWindow() }
    .drawWithContent {
        if (!gpu) return@drawWithContent drawContent()
        glass.layer.record { this@drawWithContent.drawContent() }
        drawLayer(glass.layer)
    }

/** Clip before this. Below Android 12 (no blur) and on a software canvas, just [fallback]. */
@Composable
fun Modifier.glassEffect(glass: Glass, background: Color, blur: Dp, fallback: Color, tint: Color = Color.Transparent): Modifier {
    val blurred = rememberGraphicsLayer()
    var at by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { at = it.positionInWindow() }
        .drawBehind {
            if (Build.VERSION.SDK_INT < 31 || !gpu) return@drawBehind drawRect(fallback)
            drawRect(background)
            val r = blur.toPx()
            blurred.renderEffect = BlurEffect(r, r, TileMode.Clamp)
            blurred.clip = true
            // The recording points at the page's layer, so the glass follows it without re-recording.
            blurred.record(IntSize(size.width.toInt(), size.height.toInt())) {
                translate(glass.origin.x - at.x, glass.origin.y - at.y) { drawLayer(glass.layer) }
            }
            drawLayer(blurred)
            drawRect(tint)
        }
}
