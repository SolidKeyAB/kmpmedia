package com.solidkey.painpoints.control

import com.solidkey.painpoints.ai.OGFillSpec
import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.style.OGStyleSpec
import com.solidkey.painpoints.style.roughenVertices
import com.solidkey.painpoints.style.waveVertices
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * The **skin** of an [OGControl] — a serializable, model-/designer-authorable description of a
 * one-call button: its rounded-rect geometry, its hand-drawn ink [outline], an optional gradient
 * [fill] and a seasonal [garnish]. The *live* colours (background / content) and the click handler
 * stay runtime arguments on the composable; everything here is the look that can live in a `.control`
 * JSON pack (round-tripped by [OGControls]) and so can be authored by a model exactly like a `.look`
 * or a `.style`.
 *
 * All sizes are in the natural unit the composable converts: [cornerRadius]/[height] in `dp`,
 * [fontSize] in `sp`. Defaults reproduce WordStorm's action button (46dp tall, 11dp corners, a
 * sketched ink edge) so `OGControlSpec()` is a sensible hand-drawn button out of the box.
 */
@Serializable
data class OGControlSpec(
    /** Corner radius in `dp`. */
    val cornerRadius: Float = 11f,
    /** Control height in `dp` (width comes from the modifier the caller passes). */
    val height: Float = 46f,
    /** Label size in `sp`. */
    val fontSize: Float = 13f,
    /** Pressed-state feedback: `scale` | `dim` | `brutalist` | `none` (case-insensitive). */
    val pressEffect: String = "scale",
    /** Opacity of the whole fill when `enabled == false` (the label dims to half separately). */
    val disabledAlpha: Float = 0.4f,
    /** The hand-drawn ink edge; `null` for a clean (un-sketched) button. */
    val outline: OGControlOutline? = OGControlOutline(),
    /** Optional styling pipeline for the vector label (roughen / boil / wave …); `null` = the
     *  composable's default (a subtle hand-drawn roughen). An empty-ops spec = crisp letters. */
    val labelStyle: OGStyleSpec? = null,
    /** Optional gradient fill. When present it wins over the flat `backgroundColor` argument. */
    val fill: OGFillSpec? = null,
    /** Optional seasonal garnish dripping on the control (e.g. Halloween slime, winter snow). */
    val garnish: OGGarnishSpec? = null,
)

/**
 * The hand-drawn ink edge of an [OGControl]: a rounded-rect inset from the button, roughened into a
 * sketch line and stroked just inside the fill. [amplitude] is a fraction of the control height;
 * [inset]/[width] are in `dp`; [inkAlpha] is how strongly the content colour inks the edge.
 */
@Serializable
data class OGControlOutline(
    val width: Float = 2.2f,
    val inset: Float = 3.5f,
    val amplitude: Float = 0.03f,
    val detail: Int = 7,
    val inkAlpha: Float = 0.45f,
)

/**
 * A **seasonal garnish** clinging to one [edge] of an [OGControl]: a coloured band whose inner edge
 * waves and sprouts a few drips, all oozing over time via the library's own [waveVertices]. It is the
 * lib primitive behind WordStorm's Halloween "slime" (and winter snow, spring petals, …): one spec,
 * fully data-defined, no hand-rolled Canvas per app.
 *
 * All depths are fractions of the control height. Colours are SVG colour strings (`#RRGGBB` /
 * `#AARRGGBB` / `rgb()` / names), parsed the same way the rest of the library parses fills.
 */
@Serializable
data class OGGarnishSpec(
    /** Which edge the goo clings to: `top` (default) or `bottom`. */
    val edge: String = "top",
    /** The main goo colour. */
    val color: String = "#7CB342",
    /** A deeper rim traced along the drippy inner edge; `null` to skip. */
    val deepColor: String? = "#558B2F",
    /** A glossy highlight streak near the clinging edge; `null` to skip. */
    val shineColor: String? = "#AED581",
    /** Band depth as a fraction of the control height (drips extend past it). */
    val coverage: Float = 0.5f,
    /** How many Gaussian drip bumps sag off the band. */
    val drips: Int = 2,
    /** Full wave cycles around the band outline. */
    val waves: Int = 3,
    /** Live wobble amplitude, a fraction of the control height. */
    val amplitude: Float = 0.055f,
    /** Wobble speed in cycles per second. */
    val speed: Float = 0.5f,
    /** How far the per-seed jitter shifts each drip (0 = drips evenly spaced, 1 = full jitter). */
    val spread: Float = 1f,
)

// ──────────────────────────────────────────────────────────────────────────────────────────────────
//  Pure geometry — Compose-free, deterministic, unit-testable. The composable only draws these.
// ──────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * The hand-drawn ink outline of a control: a rounded-rect inset from the (`width` × `height`) box,
 * then [roughenVertices]'d into a sketch edge. Pure + deterministic from [seed]; call once and cache
 * it (e.g. in `drawWithCache`). All arguments are in **pixels** except [detail] (a count).
 */
fun ogControlOutline(
    width: Float,
    height: Float,
    cornerRadius: Float,
    inset: Float,
    amplitude: Float,
    detail: Int,
    seed: Int,
): List<OGPoint> = roughenVertices(roundedRectOutline(width, height, cornerRadius, inset), amplitude, detail, seed)

/** A rounded-rect outline inset from `(0,0)-(width,height)`, corners sampled clockwise. Pure. */
internal fun roundedRectOutline(
    width: Float,
    height: Float,
    cornerRadius: Float,
    inset: Float,
    cornerSteps: Int = 4,
): List<OGPoint> {
    val l = inset
    val t = inset
    val r = width - inset
    val b = height - inset
    val maxR = (minOf(r - l, b - t) / 2f).coerceAtLeast(0f)
    val rad = cornerRadius.coerceIn(0f, maxR)
    val steps = cornerSteps.coerceAtLeast(1)
    val pts = ArrayList<OGPoint>((steps + 1) * 4)
    fun arc(cx: Float, cy: Float, fromDeg: Float, toDeg: Float) {
        for (s in 0..steps) {
            val a = (fromDeg + (toDeg - fromDeg) * s / steps) * (PI.toFloat() / 180f)
            pts.add(OGPoint(cx + rad * cos(a), cy + rad * sin(a)))
        }
    }
    arc(l + rad, t + rad, 180f, 270f) // top-left
    arc(r - rad, t + rad, 270f, 360f) // top-right
    arc(r - rad, b - rad, 0f, 90f)    // bottom-right
    arc(l + rad, b - rad, 90f, 180f)  // bottom-left
    return pts
}

/**
 * The closed goo ring for an [OGGarnishSpec] over a (`width` × `height`) control at [timeMs]: a
 * straight clinging edge (kept just off the border so the wobble can't expose a gap) joined to a
 * drippy inner edge, then displaced by [waveVertices] for the live ooze. Pure + deterministic from
 * [seed]; so each control (seeded 1..6) oozes a little differently. Pixels in, pixels out.
 */
fun ogGarnishRing(
    spec: OGGarnishSpec,
    width: Float,
    height: Float,
    seed: Int,
    timeMs: Long,
    samples: Int = 26,
): List<OGPoint> {
    val n = samples.coerceAtLeast(2)
    val coverage = spec.coverage.coerceIn(0f, 1f)
    val flat = height * 0.4f * coverage
    val drips = spec.drips.coerceIn(0, 8)
    val centers = FloatArray(drips)
    val widths = FloatArray(drips)
    val depths = FloatArray(drips)
    for (j in 0 until drips) {
        val base = 0.2f + 0.6f * ((j + 0.5f) / drips)
        centers[j] = (base + gHashPM(seed, j) * 0.12f * spec.spread).coerceIn(0.08f, 0.92f)
        widths[j] = 0.055f + gHash01(seed, j + 11) * 0.03f
        depths[j] = height * coverage * (0.5f + 0.25f * gHash01(seed, j + 23))
    }
    fun baseY(fx: Float): Float {
        var y = flat
        for (j in 0 until drips) y += depths[j] * gBump(fx, centers[j], widths[j])
        return y
    }
    val over = height * 0.065f
    val ring = ArrayList<OGPoint>(n + 3)
    ring.add(OGPoint(0f, -over))
    ring.add(OGPoint(width, -over))
    for (i in n downTo 0) {
        val fx = i / n.toFloat()
        ring.add(OGPoint(fx * width, baseY(fx)))
    }
    val waved = waveVertices(ring, amplitude = height * spec.amplitude, waves = spec.waves, speed = spec.speed, timeMs = timeMs)
    return if (spec.edge.trim().lowercase() == "bottom") waved.map { OGPoint(it.x, height - it.y) } else waved
}

/** `true` when this garnish clings to the bottom edge rather than the top. */
internal fun OGGarnishSpec.isBottom(): Boolean = edge.trim().lowercase() == "bottom"

private fun gHashRaw(seed: Int, j: Int): Int {
    var x = (seed * 73856093) xor (j * 19349663) xor 0x6A09E667
    x = x xor (x ushr 15)
    x *= 0x2c1b3c6d
    x = x xor (x ushr 13)
    return x
}

private fun gHash01(seed: Int, j: Int): Float = ((gHashRaw(seed, j) ushr 8) and 0xFFFF) / 65536f

private fun gHashPM(seed: Int, j: Int): Float = gHash01(seed, j) * 2f - 1f

/** A soft Gaussian bell ~1 at [center], fading to 0 — shapes a single drip. */
private fun gBump(fx: Float, center: Float, width: Float): Float {
    val z = (fx - center) / width
    return exp(-z * z)
}
