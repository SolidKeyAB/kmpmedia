package com.solidkey.painpoints.fx

import kotlin.math.floor

/**
 * **Fog / smoke / mist / steam** — a soft, drifting volumetric haze, described as plain data. Part of
 * the `com.solidkey.painpoints.fx` family. Decode + presets on [OGFog]; draw with [OGFogView].
 *
 * **How it stays 60fps (the perf gate).** A full-screen per-pixel noise field is far too costly on the
 * CPU, and the library floors runtime shaders out, so fog is built the cheap 2-D way: a small set of
 * big, soft **radial-gradient "puffs"** whose centres **drift** (linear) and **wander** (fractal
 * [OGFxNoise] noise) over time, overlapping into a cloudy mass. That's a handful of GPU gradient fills
 * per frame — the noise is sampled only once per puff, not per pixel — and it's deterministic, so it is
 * frame-identical on Android & iOS. The puffs wrap toroidally, so the haze drifts forever with no seam.
 *
 * **Coordinates.** Puff centres are normalized `0..1`; [scale] is a fraction of the shorter side;
 * [driftX]/[driftY] are fractions of the view per second.
 */
@kotlinx.serialization.Serializable
data class OGFogSpec(
    val name: String? = null,
    /** Haze colour — any SVG colour string. White reads as fog/steam, grey as smoke. */
    val color: String = "#FFFFFF",
    /** Number of overlapping puffs. More = thicker (and costlier). Clamped `1..120`. */
    val density: Int = 18,
    /** Overall opacity ceiling `0..1` (per-puff peak alpha rides under this). */
    val opacity: Float = 0.45f,
    /** Puff radius as a fraction of the shorter side (bigger = softer, more enveloping). */
    val scale: Float = 0.55f,
    /** Horizontal drift speed, fraction of the view per second (+ = right). */
    val driftX: Float = 0.03f,
    /** Vertical drift speed, fraction of the view per second (+ = down; use negative to rise like smoke). */
    val driftY: Float = 0f,
    /** How far each puff organically wanders off its drift path (fraction of the view), via fractal noise. */
    val turbulence: Float = 0.08f,
    /** How fast that wander evolves (cycles/second-ish). `0` = pure linear drift. */
    val wanderSpeed: Float = 0.12f,
    /** Fractal-noise octaves for the wander. Clamped `1..8`. */
    val octaves: Int = 3,
    /** Edge softness `0..1`: `1` = fully feathered puff, `0` = a harder solid core with a thin fade. */
    val softness: Float = 1f,
    /** Seed for the deterministic puff layout + wander. */
    val seed: Int = 0,
) {
    /** Clamp every field into its sane range (lenient model/designer input never breaks a frame). */
    fun sanitized(): OGFogSpec = copy(
        density = density.coerceIn(1, 120),
        opacity = opacity.coerceIn(0f, 1f),
        scale = scale.coerceIn(0.03f, 3f),
        turbulence = turbulence.coerceIn(0f, 1f),
        wanderSpeed = wanderSpeed.coerceAtLeast(0f),
        octaves = octaves.coerceIn(1, 8),
        softness = softness.coerceIn(0f, 1f),
    )
}

/** One resolved puff: wrapped centre `0..1`, [radiusFrac] (fraction of the shorter side) and peak [alpha]. */
data class OGFogPuff(val cx: Float, val cy: Float, val radiusFrac: Float, val alpha: Float)

/**
 * Resolve [OGFogSpec.density] puff centres at [timeSec] — pure + deterministic from [OGFogSpec.seed], so
 * the same `(spec, time)` always yields the same puffs (and both platforms match). Each puff starts at a
 * hashed base position, **drifts** linearly by [OGFogSpec.driftX]/[driftY]·t, and **wanders** by a
 * fractal-noise offset; centres wrap into `[0,1)` so the field tiles seamlessly as it moves. The view
 * scales these into pixels and draws the soft gradient (and its edge-wrapped copies).
 */
fun fogPuffs(spec: OGFogSpec, timeSec: Float): List<OGFogPuff> {
    val n = spec.density.coerceIn(1, 120)
    val oct = spec.octaves.coerceIn(1, 8)
    return List(n) { k ->
        val bx = OGFxMath.hash01(k, spec.seed)
        val by = OGFxMath.hash01(k * 7 + 3, spec.seed)
        val wanderX = spec.turbulence * OGFxNoise.fbm(timeSec * spec.wanderSpeed + k * 1.7f, 0f, oct, spec.seed)
        val wanderY = spec.turbulence * OGFxNoise.fbm(0f, timeSec * spec.wanderSpeed + k * 1.7f, oct, spec.seed + 131)
        val cx = frac(bx + spec.driftX * timeSec + wanderX)
        val cy = frac(by + spec.driftY * timeSec + wanderY)
        val radiusFrac = spec.scale * (0.6f + 0.5f * OGFxMath.hash01(k * 3 + 5, spec.seed))
        val alpha = (spec.opacity * (0.55f + 0.45f * OGFxMath.hash01(k * 9 + 2, spec.seed))).coerceIn(0f, 1f)
        OGFogPuff(cx, cy, radiusFrac, alpha)
    }
}

/** Fractional part, wrapping negatives too (so an ever-growing drift stays in `[0,1)`). */
private fun frac(v: Float): Float = v - floor(v)
