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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.solidkey.painpoints.ai.toSpecColor
import com.solidkey.painpoints.shape.OGPoint

/**
 * Draw and run a [spec]'s **breathing-slash ribbon**, filling this composable's bounds. The slash
 * draws on head → tail, then stays alive with a flowing edge and a breathing width; with
 * [OGSlashSpec.loop] the whole reveal → hold → fade cycle repeats. Set [playing] to `false` to freeze
 * the current frame.
 *
 * It holds 60fps: the smooth centreline is sampled **once** (`remember(spec)`), and each frame only
 * rebuilds the tapered outline (a cheap sine/noise pass over the samples) and fills it with a GPU
 * gradient. The glow is faked with a couple of additive strokes under the fill (deliberately **not**
 * `RenderEffect` blur, which is flaky over surfaces) — so it is correct on Android and iOS alike.
 * Good as an overlay: put it in a `Box` above your content; everywhere the ribbon isn't drawn is fully
 * transparent. Pair it with an [com.solidkey.painpoints.particle.OGParticleView] (embers / droplets)
 * for the full elemental look.
 *
 * @param spec the slash; changing it re-samples the centreline and restarts the draw-on.
 * @param playing drive the frame clock (`false` freezes the current frame).
 */
@Composable
fun OGSlashView(
    spec: OGSlashSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val centerline = remember(spec) { slashCenterline(spec) }
    val head = remember(spec) { if (spec.colorStart.toSpecColor() != null) spec.colorStart.toSpecColor()!! else Color.White }
    val tail = remember(spec) { spec.colorEnd.toSpecColor() ?: head }
    val glowColor = remember(spec) { spec.glowColor?.toSpecColor() ?: head }

    var elapsed by remember(spec) { mutableStateOf(0f) }
    LaunchedEffect(spec, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }

    Canvas(modifier.clipToBounds()) { // keep wide glow + forked branches inside the view's bounds
        if (centerline.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val short = minOf(w, h)

        val frame = slashFrameAt(spec, elapsed)
        if (frame.reveal <= 0f || frame.alpha <= 0f) return@Canvas

        // Centreline in PIXELS so normals (hence thickness) are correct on any aspect ratio.
        val clPx = centerline.map { OGPoint(it.x * w, it.y * h) }
        // Thickness fractions are of the shorter side → widthScale = short.
        val outline = slashRibbonOutline(clPx, spec, frame.reveal, elapsed.toLong(), widthScale = short)
        if (outline.size < 3) return@Canvas

        val ribbon = Path()
        ribbon.moveTo(outline[0].x, outline[0].y)
        for (i in 1 until outline.size) ribbon.lineTo(outline[i].x, outline[i].y)
        ribbon.close()

        // Flowing gradient from head to the drawn-on tip — interpolated in OKLab (perceptually even,
        // no muddy middle) by sampling into stops the GPU then blends per-segment.
        val lastIdx = clPx.size - 1
        val tipIdx = (frame.reveal * lastIdx).toInt().coerceIn(0, lastIdx)
        val headPx = Offset(clPx[0].x, clPx[0].y)
        val tailPx = Offset(clPx[tipIdx].x, clPx[tipIdx].y)
        val brush = Brush.linearGradient(OGFxColor.oklabStops(head, tail), start = headPx, end = tailPx)

        // Layered additive bloom: several strokes of the tapered OUTLINE, wide+faint → tight+bright, so
        // the halo hugs the silhouette (tapers with the blade) and sums toward a hot core under Plus
        // blend — a real neon bloom, still zero-dep, 60fps, and frame-identical on Android & iOS (no
        // RenderEffect / platform blur, which is API-floored on Android and not cross-platform-identical).
        val glow = spec.glow.coerceIn(0f, 1f)
        if (glow > 0f) {
            val baseW = spec.width * short
            val passes = 4
            for (p in 0 until passes) {
                val t = p / (passes - 1f) // 0 = outermost & faintest, 1 = innermost & brightest
                drawPath(
                    ribbon, glowColor, alpha = lerp(0.05f, 0.20f, t) * glow * frame.alpha,
                    style = Stroke(width = baseW * lerp(2.8f, 0.55f, t), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    blendMode = BlendMode.Plus,
                )
            }
        }

        // Crisp gradient-filled ribbon on top.
        drawPath(ribbon, brush, alpha = frame.alpha)

        // A white-hot core along the drawn-on spine — the "neon" centre, scaled by glow.
        if (glow > 0f) {
            val core = Path()
            core.moveTo(headPx.x, headPx.y)
            for (i in 1..tipIdx) core.lineTo(clPx[i].x, clPx[i].y)
            drawPath(
                core, Color.White, alpha = 0.5f * glow * frame.alpha,
                style = Stroke(width = spec.width * short * 0.35f, cap = StrokeCap.Round),
                blendMode = BlendMode.Plus,
            )
        }

        // Forked lightning branches (bolt edge only): thin glowing sub-channels off the main bolt.
        if (spec.edgeMode == OGSlashEdge.BOLT) {
            val bw = spec.width * short
            for (br in slashBranches(clPx, spec, frame.reveal, elapsed.toLong(), short)) {
                if (br.size < 2) continue
                val p = Path()
                p.moveTo(br[0].x, br[0].y)
                for (i in 1 until br.size) p.lineTo(br[i].x, br[i].y)
                if (glow > 0f) {
                    drawPath(p, glowColor, alpha = 0.3f * glow * frame.alpha,
                        style = Stroke(width = bw * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
                }
                drawPath(p, Color.White, alpha = (0.4f + 0.5f * glow) * frame.alpha,
                    style = Stroke(width = bw * 0.28f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
            }
        }
    }
}

/** Linear interpolate — tiny helper for the bloom falloff ramp. */
private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
