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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.pow

/**
 * Draw and run an [spec]'s **fire** — upward-licking flame tongues filling this composable's bounds from
 * [OGFlameSpec.baseY] up. Set [playing] to `false` to freeze it; pass a live [windOverride] to bend the
 * whole fire with a scene's breeze (overrides [OGFlameSpec.wind] when non-null).
 *
 * 60fps + cross-platform: each tongue is one GPU-filled tapered path with a hot→cool vertical gradient,
 * its centreline waving and flickering via a few fractal-noise samples (see [OGFlameSpec]) — no per-pixel
 * work, no shader. Overlay it in a `Box`; it is transparent between and above the tongues. For a candle,
 * use the one-tongue [OGFlame.CANDLE] preset in a narrow box.
 */
@Composable
fun OGFlameView(
    spec: OGFlameSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    windOverride: Float? = null,
) {
    val s = remember(spec, windOverride) {
        (if (windOverride != null) spec.copy(wind = windOverride) else spec).sanitized()
    }
    val core = remember(s) { s.coreColor.toSpecColor() ?: Color(0xFFFFD24D) }
    val tip = remember(s) { s.tipColor.toSpecColor() ?: Color(0xFFFF4A1C) }

    var elapsed by remember(s) { mutableStateOf(0f) }
    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }

    Canvas(modifier.clipToBounds()) {
        drawOGFlameFrame(s, elapsed / 1000f, core, tip)
    }
}

/**
 * Draw one frame of a [spec]'s fire at [timeSec] with its resolved [core] (base) and [tip] colours, into
 * the current [DrawScope]. The per-frame body shared by the live [OGFlameView] and any off-screen baker —
 * one code path, so an export matches the preview. Each tongue is a tapered path around its waving spine,
 * filled with a vertical base→tip gradient. Clip to the bounds first.
 */
internal fun DrawScope.drawOGFlameFrame(spec: OGFlameSpec, timeSec: Float, core: Color, tip: Color) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return

    flameTongues(spec, timeSec).forEach { tongue ->
        val spine = tongue.spine
        if (spine.size < 2) return@forEach
        val baseHalf = (tongue.halfWidth * w).coerceAtLeast(0.5f)
        val n = spine.size - 1

        // Tapered outline: up the left edge (base → tip), then down the right edge (tip → base).
        val path = Path()
        val base = spine[0]
        path.moveTo(base.x * w - baseHalf, base.y * h)
        for (i in 1..n) {
            val hw = baseHalf * edgeTaper(i.toFloat() / n)
            path.lineTo(spine[i].x * w - hw, spine[i].y * h)
        }
        for (i in n downTo 0) {
            val hw = baseHalf * edgeTaper(i.toFloat() / n)
            path.lineTo(spine[i].x * w + hw, spine[i].y * h)
        }
        path.close()

        val cx = base.x * w
        val brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to core.copy(alpha = core.alpha * tongue.alpha),
                1f to tip.copy(alpha = tip.alpha * tongue.alpha),
            ),
            start = Offset(cx, base.y * h),          // base (hot core)
            end = Offset(cx, spine[n].y * h),        // tip (cooler)
        )
        drawPath(path, brush = brush)
    }
}

/** Half-width factor along a tongue: full at the base, tapering smoothly to a point at the tip. */
private fun edgeTaper(s: Float): Float = (1f - s).coerceIn(0f, 1f).pow(0.65f)
