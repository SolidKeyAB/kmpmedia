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
        drawOGLightningFrame(spec, elapsed, color, coreColor, glowColor)
    }
}

/**
 * Draw one frame of a [spec]'s forked bolt into the current [DrawScope] at [elapsedMs], given its
 * resolved channel / core / glow colours. The per-frame body shared by the live [OGLightningView] and
 * the off-screen stage exporter (`com.solidkey.painpoints.stage`) — one code path, so an exported GIF
 * matches the preview. Pure Compose drawing; clip to the target bounds before calling.
 */
internal fun DrawScope.drawOGLightningFrame(
    spec: OGLightningSpec,
    elapsedMs: Float,
    color: Color,
    coreColor: Color,
    glowColor: Color,
) {
    val w = size.width; val h = size.height
    val short = minOf(w, h)
    val strike = lightningStrikeAt(spec, elapsedMs)
    if (strike.alpha <= 0f) return

    val a = OGPoint(spec.endpointA().x * w, spec.endpointA().y * h)
    val b = OGPoint(spec.endpointB().x * w, spec.endpointB().y * h)
    val main = lightningChannel(a, b, short * spec.jaggedness, spec.detail, spec.seed + strike.index * 7919)
    val baseW = short * spec.width

    drawChannel(main, baseW, spec.glow, strike.alpha, color, coreColor, glowColor, spec.taper)
    for (br in lightningBranches(main, spec, strike.index, short)) {
        if (br.size >= 2) drawChannel(br, baseW * 0.5f, spec.glow, strike.alpha * 0.9f, color, coreColor, glowColor, spec.taper)
    }
    
    // Draw cinematic flash impact
    if (spec.flashRadius > 0f && strike.alpha > 0.1f) {
        val flashR = spec.flashRadius * short * strike.alpha
        drawCircle(glowColor.copy(alpha = 0.4f * strike.alpha), radius = flashR * 2f, center = androidx.compose.ui.geometry.Offset(b.x, b.y), blendMode = BlendMode.Plus)
        drawCircle(coreColor.copy(alpha = 0.9f * strike.alpha), radius = flashR * 0.6f, center = androidx.compose.ui.geometry.Offset(b.x, b.y), blendMode = BlendMode.Plus)
    }
}

/** Stroke a bolt channel as a layered additive bloom + a white-hot core. If tapered, draws segments. */
private fun DrawScope.drawChannel(
    pts: List<OGPoint>,
    baseW: Float,
    glow: Float,
    alpha: Float,
    color: Color,
    coreColor: Color,
    glowColor: Color,
    taper: Boolean
) {
    if (pts.size < 2) return
    if (!taper) {
        val p = Path()
        p.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y)
        if (glow > 0f) {
            drawPath(p, glowColor, alpha = 0.12f * glow * alpha,
                style = Stroke(width = baseW * 3f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
            drawPath(p, glowColor, alpha = 0.2f * glow * alpha,
                style = Stroke(width = baseW * 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
        }
        drawPath(p, color, alpha = 0.9f * alpha,
            style = Stroke(width = baseW, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
        drawPath(p, coreColor, alpha = alpha,
            style = Stroke(width = baseW * 0.4f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
    } else {
        val total = pts.size - 1
        for (i in 0 until total) {
            val tFrac = 1f - (i.toFloat() / total)
            val w = baseW * (0.1f + 0.9f * tFrac)
            val p1 = androidx.compose.ui.geometry.Offset(pts[i].x, pts[i].y)
            val p2 = androidx.compose.ui.geometry.Offset(pts[i+1].x, pts[i+1].y)
            if (glow > 0f) {
                drawLine(glowColor, p1, p2, strokeWidth = w * 3f, cap = StrokeCap.Round, alpha = 0.12f * glow * alpha, blendMode = BlendMode.Plus)
                drawLine(glowColor, p1, p2, strokeWidth = w * 1.7f, cap = StrokeCap.Round, alpha = 0.2f * glow * alpha, blendMode = BlendMode.Plus)
            }
            drawLine(color, p1, p2, strokeWidth = w, cap = StrokeCap.Round, alpha = 0.9f * alpha, blendMode = BlendMode.Plus)
            drawLine(coreColor, p1, p2, strokeWidth = w * 0.4f, cap = StrokeCap.Round, alpha = alpha, blendMode = BlendMode.Plus)
        }
    }
}
