package com.solidkey.painpoints.compositor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * Draw [composition] at [timeMs] into the current [DrawScope], in the composition's own pixel space
 * (top-left origin, `width` × `height`). Used both by the offscreen [renderFrame] exporter and by the
 * on-screen `OGCompositionView` preview (which wraps this in a fit-scale transform). Pure Compose
 * drawing — no platform types — so a frame looks the same on Android and iOS.
 */
fun DrawScope.drawComposition(composition: OGComposition, timeMs: Long) {
    if (composition.background != Color.Transparent) {
        drawRect(
            color = composition.background,
            topLeft = Offset.Zero,
            size = Size(composition.width.toFloat(), composition.height.toFloat()),
        )
    }
    for (layer in composition.resolveAt(timeMs)) {
        drawResolvedLayer(layer)
    }
}

/** Draw one flattened layer: apply its transform, clip, then paint the content with its opacity. */
internal fun DrawScope.drawResolvedLayer(layer: OGResolvedLayer) {
    if (layer.opacity <= 0f || layer.width <= 0f || layer.height <= 0f) return
    val lsize = Size(layer.width, layer.height)
    val pivot = Offset(layer.width / 2f, layer.height / 2f)

    withTransform({
        translate(layer.x, layer.y)
        if (layer.rotationDeg != 0f) rotate(layer.rotationDeg, pivot)
        if (layer.scale != 1f) scale(layer.scale, layer.scale, pivot)
    }) {
        val paint: DrawScope.() -> Unit = {
            when (val c = layer.content) {
                is OGLayerContent.Solid ->
                    drawRect(color = c.color, topLeft = Offset.Zero, size = lsize, alpha = layer.opacity)
                is OGLayerContent.Image ->
                    drawImageIntoBox(c.bitmap, lsize, layer, layer.opacity)
            }
        }
        val clip = layer.clip
        if (clip != null) {
            val outline = clip.createOutline(lsize, layoutDirection, this)
            clipPath(outlineToPath(outline)) { paint() }
        } else {
            paint()
        }
    }
}

/** Draw [bitmap] into a `size` box honouring [OGResolvedLayer.contentScale], trimmed to the box. */
private fun DrawScope.drawImageIntoBox(
    bitmap: ImageBitmap,
    size: Size,
    layer: OGResolvedLayer,
    alpha: Float,
) {
    val iw = bitmap.width.toFloat()
    val ih = bitmap.height.toFloat()
    if (iw <= 0f || ih <= 0f) return
    val factor = layer.contentScale.computeScaleFactor(Size(iw, ih), size)
    val dw = iw * factor.scaleX
    val dh = ih * factor.scaleY
    val dx = (size.width - dw) / 2f
    val dy = (size.height - dh) / 2f
    // Clip to the layer box so an over-covering (Crop) image is trimmed to the frame.
    clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
        drawImage(
            image = bitmap,
            dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
            dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
            alpha = alpha,
        )
    }
}

/**
 * Render a single frame of the composition at absolute [timeMs] to an offscreen [ImageBitmap] the size
 * of the composition. Allocates one bitmap per call, so it is meant for **export / snapshotting**, not
 * a per-frame render loop — use `OGCompositionView` for a live 60fps preview (it draws straight to the
 * screen with no intermediate bitmap).
 */
fun OGComposition.renderFrame(timeMs: Long): ImageBitmap {
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    val bitmap = ImageBitmap(w, h)
    val canvas = Canvas(bitmap)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(w.toFloat(), h.toFloat()),
    ) {
        drawComposition(this@renderFrame, timeMs)
    }
    return bitmap
}

/** Any [Outline] → a fresh [Path] we can hand to `clipPath`. */
private fun outlineToPath(outline: Outline): Path = when (outline) {
    is Outline.Generic -> outline.path
    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
}
