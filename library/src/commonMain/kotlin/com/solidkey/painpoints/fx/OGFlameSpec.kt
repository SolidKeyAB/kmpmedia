package com.solidkey.painpoints.fx

/**
 * **Fire** — a body of living, upward-licking flame, described as plain data. Part of the
 * `com.solidkey.painpoints.fx` family and the fire sibling of [OGFogSpec] (smoke) and the particle
 * `EMBERS` preset (sparks). Decode + presets on [OGFlame]; draw with [OGFlameView].
 *
 * **How it stays 60fps (the perf gate).** No fluid sim, no per-pixel shader (the library floors runtime
 * shaders out). A fire is a handful of **tongues** — tapered flame shapes rooted along a baseline — whose
 * centrelines **wave** with fractal [OGFxNoise] noise and whose heights **flicker** over time. Each
 * tongue is one GPU-filled path with a vertical hot→cool gradient; the noise is sampled a few times per
 * tongue, never per pixel. It is pure + deterministic from [seed], so it is frame-identical on Android &
 * iOS and bakes into an export byte-for-byte, exactly like the rest of the FX layer.
 *
 * **Coordinates.** Everything is normalized `0..1` of the view: [baseY] is where the flames are rooted
 * (`1` = the bottom edge), [height]/[width]/[sway] are fractions of the view. `y` grows downward (screen
 * convention), so a tongue rises from `baseY` up toward smaller `y`.
 */
@kotlinx.serialization.Serializable
data class OGFlameSpec(
    val name: String? = null,
    /** The hot colour at the **base** of each tongue (where fire is brightest) — any SVG colour string. */
    val coreColor: String = "#FFD24D",
    /** The cooler colour at the **tip** of each tongue (where fire fades to smoke) — any SVG colour string. */
    val tipColor: String = "#FF4A1C",
    /** Number of flame tongues across the base. More = a wider, fuller fire. Clamped `1..60`. */
    val tongues: Int = 9,
    /** Average tongue height as a fraction of the view (`0..1`). */
    val height: Float = 0.62f,
    /** Per-tongue random variation in height (`0..1`): `0` = all equal, higher = raggeder crown. */
    val heightJitter: Float = 0.45f,
    /** Base width of a single tongue as a fraction of the view. Tongues overlap into one mass. */
    val width: Float = 0.16f,
    /** How far a tongue's tip swings sideways (fraction of the view) as it licks. */
    val sway: Float = 0.06f,
    /**
     * A **prevailing wind** that bends the whole flame sideways (fraction of the view at the tip; `+` =
     * right, `-` = left). The lean grows with height so the base stays put and the tip streams downwind —
     * drive it live from the same breeze that pushes your snow/leaves so the fire reacts to the weather.
     */
    val wind: Float = 0f,
    /** How fast that sideways licking evolves (cycles/second-ish). */
    val swaySpeed: Float = 1.1f,
    /** Height flicker amount (`0..1`): the fast up-down pulsing of the flame crown. */
    val flicker: Float = 0.3f,
    /** How fast the height flicker pulses (cycles/second-ish). */
    val flickerSpeed: Float = 2.4f,
    /** Fractal-noise octaves for the waving edges. Clamped `1..8`. */
    val octaves: Int = 3,
    /** Overall opacity ceiling `0..1` (per-tongue peak alpha rides under this). */
    val opacity: Float = 0.92f,
    /** Where the fire is rooted, `0..1` of the view height (`1` = the bottom edge). */
    val baseY: Float = 1f,
    /** Seed for the deterministic tongue layout, wave and flicker. */
    val seed: Int = 0,
) {
    /** Clamp every field into its sane range (lenient model/designer input never breaks a frame). */
    fun sanitized(): OGFlameSpec = copy(
        tongues = tongues.coerceIn(1, 60),
        height = height.coerceIn(0.02f, 1.5f),
        heightJitter = heightJitter.coerceIn(0f, 1f),
        width = width.coerceIn(0.01f, 1f),
        sway = sway.coerceIn(0f, 0.5f),
        wind = wind.coerceIn(-1f, 1f),
        swaySpeed = swaySpeed.coerceAtLeast(0f),
        flicker = flicker.coerceIn(0f, 1f),
        flickerSpeed = flickerSpeed.coerceAtLeast(0f),
        octaves = octaves.coerceIn(1, 8),
        opacity = opacity.coerceIn(0f, 1f),
        baseY = baseY.coerceIn(0f, 1f),
    )
}

/** One point on a flame tongue's centreline, normalized `0..1` of the view (`y` grows downward). */
data class OGFlamePoint(val x: Float, val y: Float)

/**
 * One resolved tongue at a given time: its [spine] centreline from base (`spine.first()`, at `baseY`) up
 * to the flickering tip (`spine.last()`), the [halfWidth] of its base (fraction of the view, taper to 0
 * at the tip) and its peak [alpha]. The view builds a tapered path around the spine and fills it with the
 * hot→cool gradient.
 */
data class OGFlameTongue(val spine: List<OGFlamePoint>, val halfWidth: Float, val alpha: Float)

/** How many points sample each tongue's centreline (base → tip). More = smoother waves, marginally costlier. */
private const val SPINE_SAMPLES = 9

/**
 * Resolve [OGFlameSpec.tongues] flame tongues at [timeSec] — pure + deterministic from [OGFlameSpec.seed],
 * so the same `(spec, time)` always yields the same fire (and both platforms match). Each tongue is rooted
 * at a spread-out base x, given a jittered height that **flickers** via fractal noise, and a centreline
 * that **waves** more toward the tip (rooted at the base) via fractal noise. The view scales these into
 * pixels, tapers a path around each spine and fills it with the core→tip gradient.
 */
fun flameTongues(spec: OGFlameSpec, timeSec: Float): List<OGFlameTongue> {
    val n = spec.tongues.coerceIn(1, 60)
    val oct = spec.octaves.coerceIn(1, 8)
    return List(n) { k ->
        // Spread roots across the base, with a touch of hashed jitter so they don't look combed.
        val rootX = (k + 0.5f) / n + OGFxMath.hashSigned(k * 5 + 1, spec.seed) * (0.5f / n)
        val hJit = 1f + spec.heightJitter * OGFxMath.hashSigned(k * 3 + 7, spec.seed)
        // Fast height pulsing; fbm is ~[-1,1] so flicker scales the crown up and down.
        val flick = 1f + spec.flicker * OGFxNoise.fbm(timeSec * spec.flickerSpeed + k * 2.3f, 0f, oct, spec.seed + 61)
        val h = (spec.height * hJit * flick).coerceAtLeast(0.01f)
        val halfWidth = spec.width * 0.5f * (0.7f + 0.6f * OGFxMath.hash01(k * 9 + 2, spec.seed))
        val alpha = (spec.opacity * (0.8f + 0.2f * OGFxMath.hash01(k * 11 + 4, spec.seed))).coerceIn(0f, 1f)

        val spine = List(SPINE_SAMPLES + 1) { j ->
            val s = j.toFloat() / SPINE_SAMPLES                     // 0 at base → 1 at tip
            val y = spec.baseY - h * s                             // rise upward (smaller y)
            // Sway grows toward the tip (rooted at the base) and wanders along the length over time.
            val wave = spec.sway * s * OGFxNoise.fbm(
                s * 2.5f + timeSec * spec.swaySpeed + k * 1.7f,
                timeSec * spec.swaySpeed * 0.6f,
                oct, spec.seed,
            )
            // Prevailing wind bends the tongue downwind, quadratic in height so the base stays rooted.
            val lean = spec.wind * s * s
            OGFlamePoint(rootX + wave + lean, y)
        }
        OGFlameTongue(spine, halfWidth, alpha)
    }
}
