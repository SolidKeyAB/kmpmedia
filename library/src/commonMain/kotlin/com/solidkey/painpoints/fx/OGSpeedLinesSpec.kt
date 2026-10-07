package com.solidkey.painpoints.fx

/**
 * **Impact / speed lines** — the manga "focus framing": tapered streaks converging on a point (radial)
 * or sweeping across (linear), described as plain data. Dirt-cheap and iconic; pairs with a parametric
 * `star` flash or an [OGSlashSpec] hit. Part of the `com.solidkey.painpoints.fx` action-FX family.
 * Decode + presets on [OGSpeedLines]; draw with [OGSpeedLinesView].
 *
 * **Coordinates.** [cx]/[cy] (focus) are `0..1`; radii are fractions of the view's half-diagonal (so
 * lines reach the corners); [thickness] is a fraction of the shorter side. **Perf.** The lines are a
 * pure, deterministic set of tapered wedges (integer hash, no RNG) filled on the GPU — frame-identical
 * on Android & iOS.
 */
@kotlinx.serialization.Serializable
data class OGSpeedLinesSpec(
    val name: String? = null,
    /** `radial` (converge on the focus) or `linear` (parallel streaks along [angleDeg]). */
    val mode: String = "radial",
    /** Focus point, normalized `0..1` (radial mode). */
    val cx: Float = 0.5f,
    val cy: Float = 0.5f,
    /** Number of lines. Clamped `1..512`. */
    val count: Int = 48,
    /** Line colour — any SVG colour string. */
    val color: String = "#000000",
    /** Clear zone: lines start this far from the focus (fraction of the half-diagonal). */
    val innerRadius: Float = 0.3f,
    /** Lines reach this far (fraction of the half-diagonal). */
    val outerRadius: Float = 1.05f,
    /** Base line thickness at the outer edge, fraction of the shorter side. */
    val thickness: Float = 0.012f,
    /** Per-line start-radius spread, `0..1`. */
    val lengthJitter: Float = 0.5f,
    /** Per-line thickness spread, `0..1`. */
    val thicknessJitter: Float = 0.6f,
    /** Direction in degrees for `linear` mode (0 = →, 90 = ↓). */
    val angleDeg: Float = 0f,
    /** Breathing speed of the inner edge in cycles/second (`0` = static). */
    val speed: Float = 1.4f,
    /** How far the inner edge breathes, fraction of [innerRadius]. */
    val pulse: Float = 0.25f,
    /** Opacity `0..1`. */
    val alpha: Float = 0.9f,
    /** Seed for the deterministic jitter. */
    val seed: Int = 0,
) {
    val isRadial: Boolean get() = !mode.equals("linear", ignoreCase = true)
}

/** One resolved speed line: its [angleRad] (radial) or lateral [offset] (linear), radii and half-width
 *  — all as fractions, ready to scale into pixels. */
data class OGSpeedLine(val angleRad: Float, val offset: Float, val innerFrac: Float, val outerFrac: Float, val halfWidthFrac: Float)

/**
 * Resolve [OGSpeedLinesSpec.count] lines with their per-line jittered start radius and thickness —
 * pure + deterministic from [OGSpeedLinesSpec.seed]. For `radial`, [OGSpeedLine.angleRad] spreads the
 * lines around the focus; for `linear`, [OGSpeedLine.offset] (`-0.5..0.5`) places them across the
 * sweep. The view scales these into pixels (and applies the breathing [OGSpeedLinesSpec.speed]).
 */
fun speedLineParams(spec: OGSpeedLinesSpec): List<OGSpeedLine> {
    val n = spec.count.coerceIn(1, 512)
    val inner = spec.innerRadius.coerceAtLeast(0f)
    val outer = spec.outerRadius.coerceAtLeast(inner + 1e-3f)
    val baseHalf = spec.thickness.coerceAtLeast(0f) * 0.5f
    return List(n) { i ->
        val t = i / n.toFloat()
        val jLen = 1f + spec.lengthJitter * OGFxMath.hashSigned(i, spec.seed)
        val jW = (1f + spec.thicknessJitter * OGFxMath.hashSigned(i * 3 + 1, spec.seed)).coerceAtLeast(0.15f)
        OGSpeedLine(
            angleRad = t * TWO_PI_SL,
            offset = t - 0.5f,
            innerFrac = (inner * jLen).coerceIn(0f, outer - 1e-3f),
            outerFrac = outer,
            halfWidthFrac = baseHalf * jW,
        )
    }
}

private const val TWO_PI_SL = 6.2831855f
