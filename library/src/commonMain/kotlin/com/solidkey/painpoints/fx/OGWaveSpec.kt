package com.solidkey.painpoints.fx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * **Water surface / waves** — overlapping scrolling wave bands, described as plain data. Part of the
 * `com.solidkey.painpoints.fx` family. Decode + presets on [OGWave]; draw with [OGWaveView].
 *
 * **How it stays 60fps (the perf gate).** No per-pixel work and no shaders: each layer is one closed
 * path (a sampled crest line down to the bottom edge) filled with a single vertical gradient, plus an
 * optional foam stroke along the crest. The crest profile is a sum of **Gerstner (trochoidal) waves**
 * — the closed-form gravity-wave solution, giving the sharp-crest/broad-trough water silhouette — with
 * fractal [OGFxNoise] chop on top, sampled at a handful of points per band — pure `kotlin.math`,
 * deterministic, frame-identical on Android & iOS.
 *
 * **Coordinates.** Everything is normalized `0..1` over the view; [amplitude] is a fraction of the
 * view height; [speed] is a fraction of the view per second.
 */
@kotlinx.serialization.Serializable
data class OGWaveSpec(
    val name: String? = null,
    /** Deep water colour — visible in the troughs and where waves are opaque. Any SVG colour string. */
    val deepColor: String = "#1565C0",
    /** Shallow/crest colour — the lighter tone at wave tops and foam edges. Any SVG colour string. */
    val shallowColor: String = "#4FC3F7",
    /** Number of overlapping wave bands. 2 = calm; 4 = choppy ocean. Clamped 1..8. */
    val layers: Int = 3,
    /** Wave amplitude as a fraction of the view height (how tall the waves are). */
    val amplitude: Float = 0.08f,
    /** Base wave frequency — how many wave crests fit across the view. */
    val frequency: Float = 2.5f,
    /** How fast the waves scroll horizontally (fraction of the view per second, + = right). */
    val speed: Float = 0.25f,
    /** Organic waviness from fractal noise added on top of the base sine (fraction of amplitude). */
    val turbulence: Float = 0.45f,
    /** Whether to draw a white foam strip at wave crests. */
    val foam: Boolean = true,
    /** Foam strip thickness as a fraction of the wave amplitude. */
    val foamThickness: Float = 0.15f,
    /** Overall opacity ceiling for the wave fills (0..1). */
    val opacity: Float = 0.85f,
    /** Y position of the wave surface, 0..1 of the view (0 = top, 1 = bottom; 0.5 = mid-screen). */
    val surfaceY: Float = 0.5f,
    /** Fractal-noise octaves for the wave turbulence. Clamped 1..6. */
    val octaves: Int = 3,
    /**
     * Wave **peak sharpness** `0..1` — the Gerstner steepness. `0` = rounded (still more watery than a
     * sine); `~0.65` = classic sharp-crest/broad-trough ocean; near `1` = very peaked chop (clamped to
     * `0.95` so crests never self-intersect). [turbulence] adds a little extra steepness on top.
     */
    val steepness: Float = 0.65f,
    /** Seed for deterministic wave layout. */
    val seed: Int = 0,
) {
    /** Clamp every field into its sane range (lenient model/designer input never breaks a frame). */
    fun sanitized(): OGWaveSpec = copy(
        layers = layers.coerceIn(1, 8),
        amplitude = amplitude.coerceIn(0.005f, 0.5f),
        frequency = frequency.coerceAtLeast(0.1f),
        speed = speed,
        turbulence = turbulence.coerceIn(0f, 2f),
        foamThickness = foamThickness.coerceIn(0f, 1f),
        opacity = opacity.coerceIn(0f, 1f),
        surfaceY = surfaceY.coerceIn(0f, 1f),
        octaves = octaves.coerceIn(1, 6),
        steepness = steepness.coerceIn(0f, 0.95f),
    )
}

/** One resolved wave band at a given time — the crest y-profile as a list of normalized x,y points
 *  (enough samples for a smooth curve), the [fillAlpha], and whether to draw [foam]. */
data class OGWaveBand(val crests: List<OGWavePoint>, val fillAlpha: Float, val foam: Boolean)

/** A normalized `0..1` point on a wave crest. */
data class OGWavePoint(val x: Float, val y: Float)

/** Horizontal samples per band — enough for a smooth crest at any phone width. */
private const val WAVE_SAMPLES = 32

/** Gerstner wave trains summed per band — enough for sharp crests without visible repetition. */
private const val WAVE_TRAINS = 4

/** Scaled gravitational constant for the deep-water dispersion relation `ω = √(g·k)`. */
private const val WAVE_G = 9.8f

/** Relative train amplitudes: one dominant swell + progressively smaller cross-chop (sums to 1). */
private val WAVE_WEIGHTS = floatArrayOf(1f, 0.55f, 0.32f, 0.20f)
private val WAVE_WEIGHT_SUM = WAVE_WEIGHTS.sum()

/**
 * Resolve [OGWaveSpec.layers] wave bands at [timeSec] — pure + deterministic from [OGWaveSpec.seed],
 * so the same `(spec, time)` always yields the same bands (and both platforms match).
 *
 * Each band's crest profile is a sum of **Gerstner (trochoidal) waves** — the closed-form solution to
 * the irrotational free-surface gravity-wave equation. Each train `i` displaces a surface point both
 * vertically *and horizontally*:
 * ```
 * x(u,t) = u − Σ (Qᵢ/kᵢ)·sin(kᵢu − ωᵢt + φᵢ)      y(u,t) = Σ Aᵢ·cos(kᵢu − ωᵢt + φᵢ)
 * ```
 * with the deep-water dispersion relation `ωᵢ = √(g·kᵢ)` so long waves travel faster than short ones.
 * The horizontal displacement bunches samples at crests, producing the characteristic **sharp-crest /
 * broad-trough** water silhouette a plain sine can never make. We sample at fixed screen `x` and invert
 * the parametrization with one fixed-point step, so crest endpoints stay pinned at `x = 0` and `x = 1`.
 * Total steepness `ΣQᵢ` is driven by [OGWaveSpec.steepness] (plus a little [OGWaveSpec.turbulence])
 * and capped at `0.85` so crests never self-intersect. Fractal [OGFxNoise] chop still rides on top.
 *
 * Back layers sit lower, shorter and more transparent; the view scales the normalized crest points
 * into pixels.
 */
fun waveBands(spec: OGWaveSpec, timeSec: Float): List<OGWaveBand> {
    val n = spec.layers.coerceIn(1, 8)
    val oct = spec.octaves.coerceIn(1, 6)
    val twoPi = 2f * PI.toFloat()
    // Total crest steepness ΣQᵢ: the spec's dial plus a touch of extra bite from turbulence,
    // capped below 1 so the trochoid never self-intersects (loops over itself).
    val totalSteepness = (spec.steepness + 0.25f * spec.turbulence).coerceIn(0f, 0.85f)

    return List(n) { k ->
        val freqMult = 1f + k * 0.18f
        val speedMult = 1f - k * 0.12f
        val ampMult = 1f - k * 0.15f   // back layers slightly shorter

        // Depth offset: back layers sit lower (deeper water behind the front)
        val depthOffset = k * spec.amplitude * 0.6f

        // ── Build this band's Gerstner wave trains (deterministic from the seed) ──────────────────
        val amp = FloatArray(WAVE_TRAINS)      // Aᵢ (normalized view-height units)
        val wavek = FloatArray(WAVE_TRAINS)    // kᵢ = |k⃗ᵢ| (rad per normalized x unit)
        val dir = FloatArray(WAVE_TRAINS)      // k⃗ᵢₓ/|k⃗ᵢ| — x-projection of the train's direction
        val omega = FloatArray(WAVE_TRAINS)    // ωᵢ = √(g·kᵢ) · speed
        val phase = FloatArray(WAVE_TRAINS)    // φᵢ — hashed random phase
        var qSum = 0f                          // Σ Aᵢ·kᵢ before normalization
        for (i in 0 until WAVE_TRAINS) {
            // Spatial frequency scatter 0.8..1.3 of the base, so trains beat against each other.
            val fJit = 0.8f + 0.5f * OGFxMath.hash01(i * 13 + k * 7 + 3, spec.seed)
            wavek[i] = spec.frequency * freqMult * fJit * twoPi
            // Direction within ±35° of horizontal: only the x-projection matters for a profile cut.
            val angDeg = 35f * OGFxMath.hashSigned(i * 17 + k * 5 + 1, spec.seed)
            dir[i] = cos(angDeg * PI.toFloat() / 180f)
            amp[i] = spec.amplitude * ampMult * WAVE_WEIGHTS[i] / WAVE_WEIGHT_SUM
            // Deep-water dispersion ω=√(g·k); spec.speed scales propagation (0 freezes the surface).
            omega[i] = sqrt(WAVE_G * wavek[i]) * spec.speed * speedMult
            phase[i] = OGFxMath.hash01(i * 29 + k * 7 + 3, spec.seed) * twoPi
            qSum += amp[i] * wavek[i] * dir[i]
        }
        // Normalize per-train steepness Qᵢ so the band's total hits exactly totalSteepness.
        val qScale = if (qSum > 1e-6f) totalSteepness / qSum else 0f

        val crests = List(WAVE_SAMPLES + 1) { j ->
            val nx = j.toFloat() / WAVE_SAMPLES     // normalized screen x 0..1
            // Invert x(u)=nx with one fixed-point step: u ≈ nx + Σ (Qᵢ/kᵢ)·sin(θᵢ(nx)).
            var du = 0f
            for (i in 0 until WAVE_TRAINS) {
                val theta = wavek[i] * dir[i] * nx - omega[i] * timeSec + phase[i]
                du += amp[i] * qScale * dir[i] * sin(theta)
            }
            val u = nx + du
            // Height at the recovered parameter u: crests bunch, troughs stretch → trochoidal profile.
            var gy = 0f
            for (i in 0 until WAVE_TRAINS) {
                gy += amp[i] * cos(wavek[i] * dir[i] * u - omega[i] * timeSec + phase[i])
            }
            // Organic fbm chop on top (foam-scale detail); Gerstner is the base.
            val noiseY = spec.turbulence * spec.amplitude * ampMult *
                OGFxNoise.fbm(
                    nx * spec.frequency * freqMult + timeSec * spec.speed * speedMult * 0.7f,
                    timeSec * 0.3f + k * 1.9f, oct, spec.seed + k * 31,
                )
            // gy > 0 is a crest → smaller screen y (upward).
            OGWavePoint(nx, spec.surfaceY + depthOffset - gy + noiseY)
        }
        // Front layers are more opaque; back layers more transparent
        val fillAlpha = (spec.opacity * (1f - k * 0.22f)).coerceIn(0f, 1f)
        OGWaveBand(crests, fillAlpha, spec.foam)
    }
}
