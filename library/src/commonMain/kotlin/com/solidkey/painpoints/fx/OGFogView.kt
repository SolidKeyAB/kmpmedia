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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.min

/**
 * Draw and run an [spec]'s **fog / smoke / mist / steam**, filling this composable's bounds with a soft
 * drifting haze. Set [playing] to `false` to freeze it.
 *
 * 60fps + cross-platform: a handful of big, soft radial-gradient puffs that drift and wander (see
 * [OGFogSpec]) — GPU gradient fills, the fractal noise sampled once per puff, no per-pixel work. Overlay
 * it in a `Box` above (or below) your subject; it is transparent between the puffs. For a thicker bank,
 * raise [OGFogSpec.density] or stack two layers with different seeds/speeds.
 */
@Composable
fun OGFogView(
    spec: OGFogSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val s = remember(spec) { spec.sanitized() }
    val color = remember(s) { s.color.toSpecColor() ?: Color.White }

    var elapsed by remember(s) { mutableStateOf(0f) }
    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }

    Canvas(modifier.clipToBounds()) {
        drawOGFogFrame(s, elapsed / 1000f, color)
    }
}

/**
 * Draw one frame of a [spec]'s fog at [timeSec] with its resolved [color], into the current [DrawScope].
 * The per-frame body shared by the live [OGFogView] and any off-screen baker — one code path, so an
 * export matches the preview. Each puff is drawn as a soft radial gradient, plus the edge-wrapped copies
 * it spills onto, so the drift tiles with no seam. Clip to the bounds first.
 */
internal fun DrawScope.drawOGFogFrame(spec: OGFogSpec, timeSec: Float, color: Color) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return
    val short = min(w, h)
    val transparent = color.copy(alpha = 0f)
    // softness 1 → fully feathered (gradient from the centre); 0 → a solid core out to ~0.9r then a fade.
    val solidTo = (1f - spec.softness).coerceIn(0f, 0.9f).coerceAtLeast(1e-3f)

    fogPuffs(spec, timeSec).forEach { puff ->
        val px = puff.cx * w; val py = puff.cy * h
        val r = (puff.radiusFrac * short).coerceAtLeast(1f)
        val core = color.copy(alpha = puff.alpha)
        val brush = Brush.radialGradient(
            colorStops = arrayOf(0f to core, solidTo to core, 1f to transparent),
            center = Offset(px, py),
            radius = r,
        )
        // Toroidal wrap: draw this puff and only the edge copies that actually spill into view.
        for (dx in WRAP) for (dy in WRAP) {
            val ox = dx * w; val oy = dy * h
            if (px + ox + r < 0f || px + ox - r > w || py + oy + r < 0f || py + oy - r > h) continue
            translate(ox, oy) { drawCircle(brush = brush, radius = r, center = Offset(px, py)) }
        }
    }
}

private val WRAP = intArrayOf(-1, 0, 1)
