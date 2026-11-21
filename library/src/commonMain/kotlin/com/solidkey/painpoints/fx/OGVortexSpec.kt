package com.solidkey.painpoints.fx

import kotlinx.serialization.Serializable
import kotlin.math.PI

/** Rotation angles resolved from [OGVortexSpec] at a given time. Pure and deterministic. */
data class OGVortexState(
    /** Rotation angle (radians) per ring, innermost first. Inner rings rotate fastest (Rankine profile). */
    val ringOmegas: List<Float>,
    /** Base rotation angle for the arm set (radians). */
    val armOmega: Float,
)

/**
 * **Top-down aerial vortex** — a spinning eye with concentric dust bands and optional spiral arms.
 * Codec + presets on [OGVortex]; draw with [OGVortexView].
 *
 * Rendered as arc-dash dust rings with Rankine differential rotation (inner rings fastest), broad
 * spiral-arm arcs, and a dark eye disc — pure Canvas 2D, no shaders, 60 fps, frame-identical on
 * Android and iOS. Resolve per-frame geometry with [vortexState].
 */
@Serializable
data class OGVortexSpec(
    val name: String? = null,
    /** Eye radius as a fraction of the vortex's outer radius. `0.14` = 14 %. Clamped `0.04..0.40`. */
    val eyeRadiusFraction: Float = 0.14f,
    /** Concentric spinning dust bands. Clamped `1..8`. */
    val rings: Int = 3,
    /** Spiral arms radiating outward (`0` = none). Clamped `0..6`. */
    val arms: Int = 3,
    /** Arc span of each spiral arm in degrees. Clamped `10..180`. */
    val armSweepDeg: Float = 90f,
    /** Inner-ring rotations per second; outer rings are slower by the Rankine velocity profile. */
    val angularVelocity: Float = 1.2f,
    /** Main cloud, arm and ring colour — any SVG hex string. */
    val color: String = "#CBB7F4",
    /** Dark eye-disc colour. */
    val eyeColor: String = "#0E0520",
    /** Overall opacity multiplier `0..1`. */
    val opacity: Float = 0.85f,
    /** Debris particles distributed across the vortex field, orbiting at Rankine speed (0 = none). Clamped `0..250`. */
    val particles: Int = 0,
    /** Trailing fade arc behind each dust ring streak for a motion feel, degrees (0 = none). Clamped `0..40`. */
    val streakTailDeg: Float = 0f,
    /** Depth gradient rings layered inside the eye for a funnel look (1 = flat disc, 2–4 = progressively deeper). Clamped `1..4`. */
    val depthLayers: Int = 1,
) {
    fun sanitized(): OGVortexSpec = copy(
        eyeRadiusFraction = eyeRadiusFraction.coerceIn(0.04f, 0.40f),
        rings = rings.coerceIn(1, 8),
        arms = arms.coerceIn(0, 6),
        armSweepDeg = armSweepDeg.coerceIn(10f, 180f),
        angularVelocity = angularVelocity.coerceAtLeast(0.05f),
        opacity = opacity.coerceIn(0f, 1f),
        particles = particles.coerceIn(0, 250),
        streakTailDeg = streakTailDeg.coerceIn(0f, 40f),
        depthLayers = depthLayers.coerceIn(1, 4),
    )
}

private val TAU = (2.0 * PI).toFloat()

/**
 * Resolve rotation angles for all rings and the arm set at [timeSec].
 *
 * Inner rings rotate fastest (Rankine differential): ring 0 = full [OGVortexSpec.angularVelocity],
 * outermost = 40 % of that; proportional for rings in between.
 */
fun vortexState(spec: OGVortexSpec, timeSec: Float): OGVortexState {
    val n = spec.rings.coerceIn(1, 8)
    val base = timeSec * spec.angularVelocity * TAU
    val ringOmegas = List(n) { i ->
        val rankineScale = if (n == 1) 1f else 1f - 0.6f * (i.toFloat() / (n - 1))
        base * rankineScale
    }
    return OGVortexState(ringOmegas = ringOmegas, armOmega = base)
}
