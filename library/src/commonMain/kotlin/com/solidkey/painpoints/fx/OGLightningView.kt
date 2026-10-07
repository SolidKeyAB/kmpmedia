package com.solidkey.painpoints.fx

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.solidkey.painpoints.ai.toSpecColor
import com.solidkey.painpoints.shape.OGPoint

/**
 * Draw and run an [spec]'s **forked lightning bolt**, filling this composable's bounds: it strikes
 * bright, flickers and decays, then re-strikes as a fresh (different) bolt. Set [playing] to `false`
 * to freeze.
 *
 * 60fps + cross-platform: the channel is a pure, deterministic midpoint-displacement polyline drawn
 * as a few **additive** (`BlendMode.Plus`) strokes — a wide faint halo, the coloured channel, and a
 * white-hot core — never a `RenderEffect` blur. Put it in a `Box` over your content; it is transparent
 * everywhere it isn't drawn.
 */
@Composable
fun OGLightningView(
    spec: OGLightningSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val color = remember(spec) { spec.color.toSpecColor() ?: Color.White }
    val coreColor = remember(spec) { spec.coreColor.toSpecColor() ?: Color.White }
    val glowColor = remember(spec) { spec.glowColor?.toSpecColor() ?: color }

    var elapsed by remember(spec) { mutableStateOf(0f) }
    LaunchedEffect(spec, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }

    Canvas(modifier.clipToBounds()) {
        val w = size.width; val h = size.height
        val short = minOf(w, h)
        val strike = lightningStrikeAt(spec, elapsed)
        if (strike.alpha <= 0f) return@Canvas

        val a = OGPoint(spec.endpointA().x * w, spec.endpointA().y * h)
        val b = OGPoint(spec.endpointB().x * w, spec.endpointB().y * h)
        val main = lightningChannel(a, b, short * spec.jaggedness, spec.detail, spec.seed + strike.index * 7919)
        val baseW = short * spec.width

        drawChannel(polylinePath(main), baseW, spec.glow, strike.alpha, color, coreColor, glowColor)
        for (br in lightningBranches(main, spec, strike.index, short)) {
            if (br.size >= 2) drawChannel(polylinePath(br), baseW * 0.5f, spec.glow, strike.alpha * 0.9f, color, coreColor, glowColor)
        }
    }
}

private fun polylinePath(pts: List<OGPoint>): Path {
    val p = Path()
    if (pts.isEmpty()) return p
    p.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y)
    return p
}

/** Stroke a bolt channel as a layered additive bloom + a white-hot core. */
private fun DrawScope.drawChannel(
    path: Path,
    baseW: Float,
    glow: Float,
    alpha: Float,
    color: Color,
    coreColor: Color,
    glowColor: Color,
) {
    if (glow > 0f) {
        drawPath(path, glowColor, alpha = 0.12f * glow * alpha,
            style = Stroke(width = baseW * 3f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
        drawPath(path, glowColor, alpha = 0.2f * glow * alpha,
            style = Stroke(width = baseW * 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
    }
    drawPath(path, color, alpha = 0.9f * alpha,
        style = Stroke(width = baseW, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
    drawPath(path, coreColor, alpha = alpha,
        style = Stroke(width = baseW * 0.4f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
}
