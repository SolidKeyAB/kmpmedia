package com.solidkey.painpoints.mask

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * **Soft (feathered) & gradient clip masks** for KMPMedia media surfaces.
 *
 * A plain `Modifier.clip(shape)` gives a *hard* edge — a crisp cut-out. These modifiers give a
 * *soft* one: the content's alpha is multiplied by a mask (`BlendMode.DstIn`) so it can **fade** at
 * the shape boundary (a feathered vignette) or along an arbitrary [Brush] gradient. The recipe is a
 * single offscreen compositing layer plus one or two DstIn draws — **no blur, no RenderEffect, no
 * Android API-level floor** — so it holds 60fps and is identical on Android and iOS.
 *
 * These are the "soft & gradient masks" item of **Bet 2 — Living shapes** on the roadmap; the
 * multi-region half is [com.solidkey.painpoints.shape.OGMultiRegionShape].
 *
 * ### Scope
 * They work on **Compose-drawn content** — a photo or an animated GIF in [OGImageView], or any
 * composable you apply them to. They do **not** apply to `OGAVPlayer` video: that renders through a
 * native surface (Android `TextureView` / iOS `AVPlayerLayer`) which sits outside the Compose
 * compositing layer, so a DstIn mask over it is unreliable (the same reason blur over a video surface
 * doesn't repaint). Clip video with a hard shape or an [OGMultiRegionShape] instead.
 */

/**
 * Clip to [shape] with a **feathered edge**: the content is fully opaque through the interior and
 * fades to transparent over the last [feather] near the shape's boundary. `feather = 0.dp` is a plain
 * hard clip. The falloff is radial about the shape's bounds, so it reads best on roughly centered /
 * round shapes (circle, oval, diamond, rounded-rect, a compact lasso); a long thin shape feathers
 * more at its ends than its long sides.
 */
fun Modifier.ogSoftClip(shape: Shape, feather: Dp): Modifier =
    ogSoftMask(shape = shape, feather = feather, brush = null)

/**
 * Multiply the content's alpha by [brush] — an **arbitrary gradient mask**. Pass a
 * `Brush.linearGradient` / `verticalGradient` / `radialGradient` running from an opaque color to
 * `Color.Transparent` and the media fades out along it: an edge fade, a spotlight, a vignette. Only
 * the alpha of the brush matters (via `BlendMode.DstIn`); the hue is irrelevant.
 */
fun Modifier.ogGradientMask(brush: Brush): Modifier =
    ogSoftMask(shape = null, feather = 0.dp, brush = brush)

/**
 * The shared implementation: an offscreen layer, then DstIn the [shape] (optionally feathered by
 * [feather]) and/or the [brush]. Public callers use [ogSoftClip] / [ogGradientMask]; [OGImageView]
 * uses this directly to combine a shape feather with a gradient mask in one pass.
 */
internal fun Modifier.ogSoftMask(shape: Shape?, feather: Dp, brush: Brush?): Modifier {
    if (shape == null && brush == null) return this
    return this
        // DstIn only makes sense against an isolated layer, otherwise it would punch through
        // everything drawn beneath this node too.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val path = shape?.let { shapePath(it, size, layoutDirection, this) }
            val featherBrush = path?.let { featherBrush(it.getBounds(), feather.toPx()) }
            onDrawWithContent {
                drawContent()
                // 1) Shape clip, hard or feathered. Outside the path the mask is 0 → clipped away;
                //    inside, a hard fill (feather 0) or a radial fade (feather > 0).
                if (path != null) {
                    if (featherBrush != null) {
                        drawPath(path = path, brush = featherBrush, blendMode = BlendMode.DstIn)
                    } else {
                        drawPath(path = path, color = Color.Black, blendMode = BlendMode.DstIn)
                    }
                }
                // 2) Extra gradient mask, multiplied on top (both masks intersect).
                if (brush != null) {
                    drawRect(brush = brush, blendMode = BlendMode.DstIn)
                }
            }
        }
}

// ---------------------------------------------------------------------------------------------
// Pure geometry — no Path / Brush, so it is directly unit-testable off-device.
// ---------------------------------------------------------------------------------------------

/**
 * The inner opaque fraction of the feather's radial gradient: fully opaque out to this fraction of
 * the radius, then ramping to transparent at the edge. `feather >= radius` ⇒ 0 (fades from the
 * center); `feather <= 0` ⇒ ~1 (no fade). Clamped just below 1 so a stop pair is always valid.
 */
internal fun featherInnerFraction(maxRadiusPx: Float, featherPx: Float): Float {
    if (maxRadiusPx <= 0f) return 0f
    return ((maxRadiusPx - featherPx) / maxRadiusPx).coerceIn(0f, 0.999f)
}

// ---------------------------------------------------------------------------------------------
// Platform-backed geometry (Path / Brush).
// ---------------------------------------------------------------------------------------------

/** A radial opaque→transparent brush that feathers the outer [featherPx] of [bounds]; null if 0. */
private fun featherBrush(bounds: Rect, featherPx: Float): Brush? {
    if (featherPx <= 0f) return null
    val maxR = max(bounds.width, bounds.height) / 2f
    if (maxR <= 0f) return null
    val innerFrac = featherInnerFraction(maxR, featherPx)
    return Brush.radialGradient(
        colorStops = arrayOf(
            0f to Color.Black,
            innerFrac to Color.Black,
            1f to Color.Transparent,
        ),
        center = Offset(bounds.center.x, bounds.center.y),
        radius = maxR,
    )
}

/** A shape's outline → a fresh, mutable [Path] for masking. */
private fun shapePath(shape: Shape, size: Size, ld: LayoutDirection, density: Density): Path =
    when (val outline = shape.createOutline(size, ld, density)) {
        is Outline.Generic -> Path().apply { addPath(outline.path) }
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
    }
