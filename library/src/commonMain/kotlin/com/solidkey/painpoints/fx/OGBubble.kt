package com.solidkey.painpoints.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.max
import kotlin.math.sin

/**
 * Draw one glossy soap/water bubble: a translucent radial-gradient body (lit from the top-left),
 * an optional iridescent thin-film shimmer, a bright rim, and two specular sparkles.
 * No shader, no per-pixel work — pure [DrawScope] circles and ovals.
 *
 * [cx]/[cy] are the centre in pixels (not normalised). [r] is the radius in pixels. [tint] sets
 * the bubble colour; [alpha] fades everything (use 0..1 for pop and fade animations).
 *
 * @param scaleX Horizontal deformation factor (1 = no deformation). Pair with
 *   `scaleY = 1/scaleX` for volume-preserving squash-and-stretch.
 * @param scaleY Vertical deformation factor (1 = no deformation).
 * @param ripplePhase Animated iridescent shimmer phase in radians. Advance each frame
 *   (e.g. `phase += dt * 2f`) for living Newton-ring colour cycling.
 * @param rippleAmp Shimmer intensity in [0,1] — 0 disables thin-film layers entirely;
 *   1 is full Newton-ring shimmer with cyan, magenta, and gold crescents. Values outside
 *   [0,1] are clamped internally.
 */
fun DrawScope.drawOGBubbleFrame(
    cx: Float, cy: Float, r: Float,
    tint: Color, alpha: Float,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    ripplePhase: Float = 0f,
    rippleAmp: Float = 0f,
) {
    if (r <= 0f) return
    val body: DrawScope.() -> Unit = {
        val c = Offset(cx, cy)
        // Body: mostly see-through, brighter where the light hits top-left, deepening to tint at the rim.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.45f * alpha),
                    tint.copy(alpha = 0.30f * alpha),
                    tint.copy(alpha = 0.12f * alpha),
                ),
                center = Offset(cx - r * 0.30f, cy - r * 0.34f),
                radius = r * 1.45f,
            ),
            radius = r,
            center = c,
        )
        // Iridescent thin-film interference layers (Newton rings) — before rim and specular.
        if (rippleAmp > 0f) {
            // Clamp to [0,1] — Color.copy(alpha) requires it, and rippleAmp is caller-supplied.
            val iriAlpha = (rippleAmp * alpha).coerceIn(0f, 1f)
            val oval = Size(r * 2f, r * 2f)
            val tl   = Offset(cx - r, cy - r)
            val ph   = sin(ripplePhase)
            // Three Newton-ring interference crescents; alpha decreases outward: cyan > magenta > gold.
            // Cyan crescent
            drawOval(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.70f to Color.Transparent,
                        0.85f to Color(0xFF70E0FF).copy(alpha = 0.40f * iriAlpha),
                        0.96f to Color.Transparent,
                    ),
                    center = Offset(cx - r * 0.12f + ph * rippleAmp * 0.08f * r, cy - r * 0.10f),
                    radius = r * 0.98f,
                ),
                topLeft = tl, size = oval,
            )
            // Magenta crescent
            drawOval(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.72f to Color.Transparent,
                        0.86f to Color(0xFFFF70D0).copy(alpha = 0.333f * iriAlpha),
                        0.98f to Color.Transparent,
                    ),
                    center = Offset(cx + r * 0.14f - ph * rippleAmp * 0.08f * r, cy + r * 0.12f),
                    radius = r * 0.96f,
                ),
                topLeft = tl, size = oval,
            )
            // Gold sheen
            drawOval(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.78f to Color.Transparent,
                        0.90f to Color(0xFFFFE57F).copy(alpha = 0.302f * iriAlpha),
                        0.99f to Color.Transparent,
                    ),
                    center = Offset(cx - r * 0.08f, cy + r * 0.15f),
                    radius = r * 0.95f,
                ),
                topLeft = tl, size = oval,
            )
        }
        // Rim light — the thin bright edge that reads as "bubble".
        drawCircle(color = Color.White.copy(alpha = 0.42f * alpha), radius = r, center = c,
            style = Stroke(width = max(1f, r * 0.05f)))
        // Specular highlights near the top-left.
        drawCircle(color = Color.White.copy(alpha = 0.78f * alpha), radius = r * 0.17f,
            center = Offset(cx - r * 0.33f, cy - r * 0.37f))
        drawCircle(color = Color.White.copy(alpha = 0.40f * alpha), radius = r * 0.08f,
            center = Offset(cx + r * 0.12f, cy - r * 0.44f))
    }

    if (scaleX != 1f || scaleY != 1f) {
        withTransform({ scale(scaleX, scaleY, Offset(cx, cy)) }) { body() }
    } else {
        body()
    }
}
