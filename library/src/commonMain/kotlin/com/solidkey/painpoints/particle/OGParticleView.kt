package com.solidkey.painpoints.particle

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.solidkey.painpoints.shape.OGParametric

/**
 * Draw and run a [spec]'s particle system, filling this composable's bounds. The simulation advances
 * on the frame clock (one [OGParticleSystem.update] per frame, capped at the spec's `maxParticles`),
 * and each particle is drawn as its [OGParticleSpec.shape]. Set [playing] to `false` to freeze it.
 *
 * It holds 60fps: the sim is a tight struct-of-arrays loop with no per-frame allocation, and circles
 * (the default) draw with a direct `drawCircle`. Good as an overlay (confetti on a win, sparks on a
 * tap) — put it in a `Box` above your content; the areas with no particles are fully transparent.
 *
 * @param spec the emitter; changing it rebuilds the system from scratch.
 * @param playing drive the frame clock (`false` freezes the current frame).
 */
@Composable
fun OGParticleView(
    spec: OGParticleSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val system = remember(spec) { spec.toSystem() }
    val outline = remember(system.shape) { unitOutline(system.shape) }

    var tick by remember(system) { mutableStateOf(0L) }
    LaunchedEffect(system, playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dtMs = (now - last) / 1_000_000f
                last = now
                system.update(dtMs.coerceIn(0f, 64f)) // clamp stalls so a dropped frame can't teleport everything
                tick = now
            }
        }
    }

    Canvas(modifier) {
        tick // read so the draw re-runs each frame
        val w = size.width
        val h = size.height
        val short = minOf(w, h)
        val path = Path()
        for (i in 0 until system.count) {
            val cx = system.x[i] * w
            val cy = system.y[i] * h
            val r = system.size[i] * short
            if (r <= 0f) continue
            val color = Color(system.argb[i])
            if (system.shape == OGParticleShape.CIRCLE || outline == null) {
                drawCircle(color, radius = r, center = Offset(cx, cy))
            } else {
                drawPolyParticle(path, outline, cx, cy, r, system.rotationDeg[i], color)
            }
        }
    }
}

/** Build the closed path for one polygonal particle (unit outline in `[-1,1]`) and fill it. */
private fun DrawScope.drawPolyParticle(
    path: Path,
    outline: List<Offset>,
    cx: Float,
    cy: Float,
    r: Float,
    rotationDeg: Float,
    color: Color,
) {
    path.reset()
    path.moveTo(cx + outline[0].x * r, cy + outline[0].y * r)
    for (k in 1 until outline.size) path.lineTo(cx + outline[k].x * r, cy + outline[k].y * r)
    path.close()
    rotate(rotationDeg, pivot = Offset(cx, cy)) {
        drawPath(path, color)
    }
}

/** Unit outline (centred, spanning `[-1,1]`) for a non-circle shape; `null` means draw a circle. */
private fun unitOutline(shape: OGParticleShape): List<Offset>? = when (shape) {
    OGParticleShape.CIRCLE -> null
    OGParticleShape.SQUARE -> listOf(Offset(-1f, -1f), Offset(1f, -1f), Offset(1f, 1f), Offset(-1f, 1f))
    OGParticleShape.TRIANGLE -> OGParametric.regularPolygon(3).map { Offset((it.x - 0.5f) * 2f, (it.y - 0.5f) * 2f) }
    OGParticleShape.STAR -> OGParametric.star(5, innerRatio = 0.45f).map { Offset((it.x - 0.5f) * 2f, (it.y - 0.5f) * 2f) }
    // A lens / almond — cherry-blossom petal or leaf (pointed at both ends, bulging sides).
    OGParticleShape.PETAL -> listOf(
        Offset(0f, -1f), Offset(0.4f, -0.4f), Offset(0.5f, 0f), Offset(0.4f, 0.4f),
        Offset(0f, 1f), Offset(-0.4f, 0.4f), Offset(-0.5f, 0f), Offset(-0.4f, -0.4f),
    )
    // A teardrop — pointed at the top, round at the bottom (a water droplet / ember).
    OGParticleShape.TEARDROP -> listOf(
        Offset(0f, -1f), Offset(0.5f, -0.1f), Offset(0.65f, 0.4f), Offset(0.38f, 0.82f),
        Offset(0f, 1f), Offset(-0.38f, 0.82f), Offset(-0.65f, 0.4f), Offset(-0.5f, -0.1f),
    )
}
