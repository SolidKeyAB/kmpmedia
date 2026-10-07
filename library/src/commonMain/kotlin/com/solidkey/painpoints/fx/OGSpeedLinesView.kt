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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draw and run an [spec]'s **speed / impact lines**, filling this composable's bounds — tapered streaks
 * converging on a focus (radial) or sweeping across (linear), gently breathing. Set [playing] to
 * `false` to freeze.
 *
 * 60fps + cross-platform: a pure, deterministic set of filled wedges on the GPU, no per-frame
 * allocation beyond the reused `Path`. Overlay it in a `Box` above your subject (leave the `innerRadius`
 * clear zone over the subject); it is transparent elsewhere.
 */
@Composable
fun OGSpeedLinesView(
    spec: OGSpeedLinesSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val color = remember(spec) { spec.color.toSpecColor() ?: Color.Black }
    val lines = remember(spec) { speedLineParams(spec) }

    var elapsed by remember(spec) { mutableStateOf(0f) }
    LaunchedEffect(spec, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }

    Canvas(modifier.clipToBounds()) {
        val w = size.width; val h = size.height
        val short = minOf(w, h)
        val halfDiag = sqrt(w * w + h * h) * 0.5f
        val phase = TWO_PI * spec.speed * elapsed / 1000f
        val path = Path()

        if (spec.isRadial) {
            val fx = spec.cx * w; val fy = spec.cy * h
            lines.forEachIndexed { i, ln ->
                val breathe = 1f - spec.pulse * (0.5f + 0.5f * sin(phase + i * 0.3f))
                val dx = cos(ln.angleRad); val dy = sin(ln.angleRad)
                val px = -dy; val py = dx // perpendicular
                val rIn = ln.innerFrac * breathe * halfDiag
                val rOut = ln.outerFrac * halfDiag
                val hw = ln.halfWidthFrac * short
                val oX = fx + dx * rOut; val oY = fy + dy * rOut
                val iX = fx + dx * rIn; val iY = fy + dy * rIn
                path.reset()
                path.moveTo(oX + px * hw, oY + py * hw)
                path.lineTo(oX - px * hw, oY - py * hw)
                path.lineTo(iX, iY) // taper to a point at the inner end
                path.close()
                drawPath(path, color, alpha = spec.alpha)
            }
        } else {
            // Linear: parallel streaks across the view along angleDeg, at jittered lateral offsets.
            val ang = spec.angleDeg * DEG
            val dx = cos(ang); val dy = sin(ang)
            val px = -dy; val py = dx
            val cxp = w * 0.5f; val cyp = h * 0.5f
            val span = sqrt(w * w + h * h)
            lines.forEachIndexed { i, ln ->
                val lateral = ln.offset * span
                val breathe = 1f - spec.pulse * (0.5f + 0.5f * sin(phase + i * 0.3f))
                val half = span * 0.5f * ln.outerFrac * breathe
                val hw = ln.halfWidthFrac * short
                val baseX = cxp + px * lateral; val baseY = cyp + py * lateral
                val aX = baseX - dx * half; val aY = baseY - dy * half
                val bX = baseX + dx * half; val bY = baseY + dy * half
                path.reset()
                path.moveTo(aX + px * hw, aY + py * hw)
                path.lineTo(aX - px * hw, aY - py * hw)
                path.lineTo(bX, bY) // taper to a point at the leading end
                path.close()
                drawPath(path, color, alpha = spec.alpha)
            }
        }
    }
}

private const val TWO_PI = 6.2831855f
private const val DEG = 0.017453292f
