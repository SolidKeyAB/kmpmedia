package com.solidkey.painpoints.fx

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent

/**
 * Modifier FX for the `com.solidkey.painpoints.fx` action family. Both capture the composable's drawn
 * content into a `GraphicsLayer` **once per frame** and re-draw that layer — so they work on *any*
 * content (a shape, a lasso-cut photo, an SVG, text) with no per-content code, hold 60fps (one capture
 * + a few cheap composites), and are identical on Android & iOS (no `RenderEffect` blur, no API floor).
 */

/**
 * **After-image / motion smear** — draw a trail of fading copies of this composable behind it, offset
 * by [offsets] (in px, index `0` = nearest/brightest), for the god-speed dash look. Supply the trail
 * from your animation's recent positions. [maxAlpha] is the nearest ghost's opacity.
 */
fun Modifier.ogAfterImage(offsets: List<Offset>, maxAlpha: Float = 0.5f): Modifier = composed {
    val layer = rememberGraphicsLayer()
    drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        val n = offsets.size
        // Farthest (faintest) first so the nearer ghosts layer on top.
        for (i in offsets.indices.reversed()) {
            layer.alpha = (maxAlpha * (1f - i / (n + 1f))).coerceIn(0f, 1f)
            translate(offsets[i].x, offsets[i].y) { drawLayer(layer) }
        }
        layer.alpha = 1f
        drawContent() // the crisp current frame on top
    }
}

/**
 * **After-image** convenience: a straight trail of [count] ghosts each offset by [step] px from the
 * last (a static directional smear), fading from [maxAlpha].
 */
fun Modifier.ogAfterImage(count: Int, step: Offset, maxAlpha: Float = 0.5f): Modifier =
    ogAfterImage(List(count.coerceAtLeast(0)) { Offset(step.x * (it + 1), step.y * (it + 1)) }, maxAlpha)

/**
 * **Glow / bloom halo** around this composable, blur-free and cross-platform: a few additive
 * (`BlendMode.Plus`), outward-scaled copies of the content are drawn behind it. Pass [color] to tint
 * the halo (e.g. a neon colour); `null` blooms the content's own colours. [radius] is how far the
 * outer copy expands (`1.0` = none), [intensity] the halo opacity, [passes] the softness.
 *
 * This is the zero-dependency bloom. A true gaussian bloom needs a platform shader (API-floored and
 * not frame-identical); that remains an opt-in, not this.
 */
fun Modifier.ogGlow(
    color: Color? = null,
    radius: Float = 1.3f,
    intensity: Float = 0.6f,
    passes: Int = 3,
): Modifier = composed {
    val layer = rememberGraphicsLayer()
    drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        val p = passes.coerceIn(1, 6)
        val tint = color?.let { ColorFilter.tint(it) }
        for (k in 0 until p) {
            val t = if (p == 1) 0f else k / (p - 1f)
            layer.blendMode = BlendMode.Plus
            layer.colorFilter = tint
            layer.alpha = (intensity * (1f - 0.6f * t)).coerceIn(0f, 1f)
            val s = 1f + (radius - 1f) * (0.45f + 0.55f * t)
            layer.scaleX = s; layer.scaleY = s
            drawLayer(layer)
        }
        // Reset and draw the crisp content on top.
        layer.blendMode = BlendMode.SrcOver
        layer.colorFilter = null
        layer.alpha = 1f
        layer.scaleX = 1f; layer.scaleY = 1f
        drawContent()
    }
}
