package com.solidkey.painpoints.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max

/**
 * Draw one glossy soap/water bubble: a translucent radial-gradient body (lit from the top-left),
 * a bright rim, and two specular sparkles. No shader, no per-pixel work — pure [DrawScope] circles.
 *
 * [cx]/[cy] are the centre in pixels (not normalised). [r] is the radius in pixels. [tint] sets
 * the bubble colour; [alpha] fades everything (use 0..1 for pop and fade animations).
 */
fun DrawScope.drawOGBubbleFrame(cx: Float, cy: Float, r: Float, tint: Color, alpha: Float) {
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
    // Rim light — the thin bright edge that reads as "bubble".
    drawCircle(color = Color.White.copy(alpha = 0.42f * alpha), radius = r, center = c,
        style = Stroke(width = max(1f, r * 0.05f)))
    // Specular highlights near the top-left.
    drawCircle(color = Color.White.copy(alpha = 0.78f * alpha), radius = r * 0.17f,
        center = Offset(cx - r * 0.33f, cy - r * 0.37f))
    drawCircle(color = Color.White.copy(alpha = 0.40f * alpha), radius = r * 0.08f,
        center = Offset(cx + r * 0.12f, cy - r * 0.44f))
}
