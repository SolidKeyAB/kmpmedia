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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draw and animate an [OGVortexSpec]'s top-down aerial vortex, filling this composable's bounds.
 * Set [playing] to `false` to freeze the animation.
 *
 * 60 fps + cross-platform: arc-dash dust rings with Rankine differential rotation (inner fastest),
 * spiral-arm arcs, and a dark eye disc — pure Canvas 2D draws, no per-pixel work. For the one-shot
 * tornado-over-board effect see WordStorm's `TornadoView`; this is the reusable ambient primitive.
 */
@Composable
fun OGVortexView(
    spec: OGVortexSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val s = remember(spec) { spec.sanitized() }
    val color = remember(s) { s.color.toSpecColor() ?: Color(0xFFCBB7F4) }
    val eyeColor = remember(s) { s.eyeColor.toSpecColor() ?: Color(0xFF0E0520) }

    var elapsed by remember(s) { mutableStateOf(0f) }
    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }

    Canvas(modifier.clipToBounds()) {
        drawOGVortexFrame(s, elapsed / 1000f, color, eyeColor)
    }
}

/**
 * Draw one frame of [spec] at [timeSec] into the current [DrawScope]. Shared by the live
 * [OGVortexView] and any off-screen export baker — one code path so export matches the preview.
 */
internal fun DrawScope.drawOGVortexFrame(
    spec: OGVortexSpec,
    timeSec: Float,
    color: Color,
    eyeColor: Color,
) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return
    val cx = w * 0.5f; val cy = h * 0.5f
    val maxR = min(w, h) * 0.46f

    val state = vortexState(spec, timeSec)

    // Background haze
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.42f to color.copy(alpha = 0.18f * spec.opacity),
                0.78f to color.copy(alpha = 0.10f * spec.opacity),
                1.0f to Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = maxR,
        ),
        radius = maxR,
        center = Offset(cx, cy),
    )

    // Eye disc with lilac glow at the eye-wall edge
    val eyeR = maxR * spec.eyeRadiusFraction
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to eyeColor.copy(alpha = 0.90f * spec.opacity),
                0.55f to eyeColor.copy(alpha = 0.65f * spec.opacity),
                0.82f to color.copy(alpha = 0.30f * spec.opacity),
                1.0f to Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = eyeR * 1.9f,
        ),
        radius = eyeR * 1.9f,
        center = Offset(cx, cy),
    )

    // Depth layers: pulsing concentric rings inside the eye for a funnel look
    if (spec.depthLayers > 1) {
        val extra = spec.depthLayers - 1
        for (d in 1..extra) {
            val layerFrac = d.toFloat() / (extra + 1)
            val ringR = eyeR * 1.8f * (1f - layerFrac * 0.65f)
            val pulse = 0.60f + 0.40f * sin(timeSec * (1.1f + d * 0.9f))
            val ringAlpha = 0.28f * (1f - layerFrac * 0.5f) * pulse * spec.opacity
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.60f to Color.Transparent,
                        0.78f to color.copy(alpha = ringAlpha),
                        0.90f to color.copy(alpha = ringAlpha * 0.5f),
                        1.0f to Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = ringR,
                ),
                radius = ringR,
                center = Offset(cx, cy),
            )
        }
    }

    // Spiral arms
    if (spec.arms > 0) {
        val armInR = maxR * 0.19f
        val armOutR = maxR * 0.44f
        val armStep = TAU / spec.arms
        repeat(spec.arms) { k ->
            val startDeg = toDeg(state.armOmega + k * armStep)
            // Inner core arc
            drawArc(
                color = color.copy(alpha = 0.65f * spec.opacity),
                startAngle = startDeg,
                sweepAngle = spec.armSweepDeg,
                useCenter = false,
                topLeft = Offset(cx - armInR, cy - armInR),
                size = Size(armInR * 2f, armInR * 2f),
                style = Stroke(width = maxR * 0.040f, cap = StrokeCap.Round),
            )
            // Outer halo extension — arm broadens as it spirals outward
            val haloStartDeg = startDeg + spec.armSweepDeg * 0.42f
            drawArc(
                color = color.copy(alpha = 0.30f * spec.opacity),
                startAngle = haloStartDeg,
                sweepAngle = spec.armSweepDeg * 0.75f,
                useCenter = false,
                topLeft = Offset(cx - armOutR, cy - armOutR),
                size = Size(armOutR * 2f, armOutR * 2f),
                style = Stroke(width = maxR * 0.075f, cap = StrokeCap.Round),
            )
        }
    }

    // Concentric dust rings with Rankine differential rotation
    val n = spec.rings
    for (i in 0 until n) {
        val frac = if (n == 1) 0.5f else i.toFloat() / (n - 1)
        val ringR = maxR * lerp(INNER_RING_FRAC, OUTER_RING_FRAC, frac)
        val ringW = maxR * lerp(INNER_RING_WIDTH, OUTER_RING_WIDTH, frac)
        val alpha = lerp(INNER_RING_ALPHA, OUTER_RING_ALPHA, frac) * spec.opacity
        val streaks = (10 - i).coerceAtLeast(6)
        drawDustRing(cx, cy, ringR, state.ringOmegas[i], ringW, alpha, color, streaks, spec.streakTailDeg)
    }

    // Particles orbiting at Rankine speed
    if (spec.particles > 0) {
        drawVortexParticles(cx, cy, maxR, spec, timeSec, color)
    }
}

private fun DrawScope.drawVortexParticles(
    cx: Float, cy: Float, maxR: Float,
    spec: OGVortexSpec, timeSec: Float, color: Color,
) {
    val n = spec.particles
    if (n <= 0 || maxR <= 0f) return
    // Start just outside the eye-wall glow (1.5× eyeR) so particles live in the vortex field
    val innerR = maxR * spec.eyeRadiusFraction * 1.5f
    val outerR = maxR * 0.95f
    val span = (outerR - innerR).coerceAtLeast(1f)
    val baseOmega = timeSec * spec.angularVelocity * TAU

    for (i in 0 until n) {
        val r01 = particleRandom(i, 0)              // radial position 0..1
        val phase = particleRandom(i, 1) * TAU      // initial angle
        val sizeSeed = particleRandom(i, 2)
        val alphaSeed = particleRandom(i, 3)

        val r = innerR + r01 * span
        // Rankine angular velocity: inner = full speed, outer = 40%
        val rankineScale = 1f - 0.6f * r01
        val angle = phase + baseOmega * rankineScale

        val px = cx + r * cos(angle)
        val py = cy + r * sin(angle)

        // Deeper (closer to eye) → smaller and brighter; outer → bigger and dimmer
        val particleRadius = 1.5f + sizeSeed * 3.5f
        val baseAlpha = 0.80f - r01 * 0.42f
        val twinkle = 0.70f + 0.30f * sin(alphaSeed * TAU + timeSec * (1.5f + r01 * 2.5f))
        val alpha = (baseAlpha * twinkle * spec.opacity).coerceIn(0f, 1f)

        drawCircle(
            color = color.copy(alpha = alpha),
            radius = particleRadius,
            center = Offset(px, py),
        )
    }
}

private fun DrawScope.drawDustRing(
    cx: Float, cy: Float,
    radius: Float, omega: Float,
    ringWidth: Float, baseAlpha: Float,
    color: Color, streaks: Int, tailDeg: Float,
) {
    if (radius <= 0f || streaks <= 0) return
    val tl = Offset(cx - radius, cy - radius)
    val sz = Size(radius * 2f, radius * 2f)
    val stepRad = TAU / streaks
    for (s in 0 until streaks) {
        val angle = omega + s * stepRad
        val sweep = 18f + (s % 4) * 10f    // 18°/28°/38°/48° variation so it doesn't look tiled
        val a = (baseAlpha * (0.60f + 0.40f * sin(angle * 2.5f))).coerceIn(0f, 1f)

        // Trailing fade arc for motion feel
        if (tailDeg > 0f) {
            drawArc(
                color = color.copy(alpha = a * 0.28f),
                startAngle = toDeg(angle) - tailDeg,
                sweepAngle = tailDeg,
                useCenter = false,
                topLeft = tl,
                size = sz,
                style = Stroke(width = ringWidth * 0.55f, cap = StrokeCap.Round),
            )
        }

        drawArc(
            color = color.copy(alpha = a),
            startAngle = toDeg(angle),
            sweepAngle = sweep,
            useCenter = false,
            topLeft = tl,
            size = sz,
            style = Stroke(width = ringWidth, cap = StrokeCap.Round),
        )
    }
}

/** Deterministic per-particle pseudo-random in [0, 1). LCG mixing so adjacent indices differ. */
private fun particleRandom(particleIndex: Int, salt: Int): Float {
    val h = particleIndex * 1664525 + 1013904223 + salt * 22695477
    return ((h ushr 1).toFloat()) / 2147483647f
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
private val TAU = (2.0 * PI).toFloat()
private fun toDeg(rad: Float): Float = (rad * 180.0 / PI).toFloat()

private const val INNER_RING_FRAC = 0.21f
private const val OUTER_RING_FRAC = 0.55f
private const val INNER_RING_WIDTH = 0.050f
private const val OUTER_RING_WIDTH = 0.090f
private const val INNER_RING_ALPHA = 0.62f
private const val OUTER_RING_ALPHA = 0.24f
