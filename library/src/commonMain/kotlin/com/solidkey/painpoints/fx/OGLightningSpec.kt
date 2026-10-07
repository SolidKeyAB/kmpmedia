package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.clamp01
import com.solidkey.painpoints.shape.OGPoint
import kotlin.math.sqrt

/**
 * A **forked lightning bolt** between two points, described as plain, serializable data: a jagged main
 * channel (recursive **midpoint displacement**, the classic fractal-lightning construction) that
 * spawns tapering sub-branches, strikes bright, then flickers and decays. The point-to-point sibling
 * of [OGSlashSpec] (which is a ribbon) in the `com.solidkey.painpoints.fx` action-FX family. Decode +
 * presets live on [OGLightnings]; draw it with [OGLightningView].
 *
 * **Coordinates.** Endpoints are normalized `0..1` over the view box; [width]/[jaggedness] are
 * fractions of the shorter side. **Perf.** The bolt geometry is pure, deterministic (integer hash, no
 * RNG) midpoint displacement generated per strike, drawn as a few additive strokes — so it is
 * zero-allocation on the steady path and frame-identical on Android & iOS.
 *
 * See `docs/ACADEMIC_FOUNDATIONS.md` — Fournier/Fussell/Carpenter (midpoint displacement, 1982).
 */
@kotlinx.serialization.Serializable
data class OGLightningSpec(
    val name: String? = null,
    /** Strike origin, normalized `0..1`. */
    val x1: Float = 0.5f,
    val y1: Float = 0.05f,
    /** Strike target, normalized `0..1`. */
    val x2: Float = 0.5f,
    val y2: Float = 0.95f,
    /** Core stroke width as a fraction of the shorter side. */
    val width: Float = 0.014f,
    /** Max perpendicular jag as a fraction of the shorter side (halved each subdivision). */
    val jaggedness: Float = 0.16f,
    /** Midpoint-displacement depth: the channel has `2^detail` segments. Clamped `1..9`. */
    val detail: Int = 6,
    /** Number of forked sub-branches. Clamped `0..12`. */
    val branches: Int = 4,
    /** Branch length as a fraction of the main channel length. */
    val branchLength: Float = 0.38f,
    /** Bolt colour — any SVG colour string. */
    val color: String = "#B3E5FC",
    /** White-hot core colour. */
    val coreColor: String = "#FFFFFF",
    /** Additive bloom strength `0..1`. */
    val glow: Float = 1f,
    /** Glow colour; `null` uses [color]. */
    val glowColor: String? = null,
    /** Bright-strike duration in ms. */
    val strikeMs: Float = 90f,
    /** Fade-out duration in ms after the strike. */
    val decayMs: Float = 230f,
    /** Dark gap before the next strike in ms (a new, different bolt is drawn each strike). */
    val gapMs: Float = 420f,
    /** Re-strike forever. */
    val loop: Boolean = true,
    /** Seed for the deterministic jag/branches. */
    val seed: Int = 0,
) {
    /** One strike cycle (strike + decay + gap) in ms. */
    val cycleMs: Float get() = strikeMs.coerceAtLeast(0f) + decayMs.coerceAtLeast(0f) + gapMs.coerceAtLeast(0f)
}

/**
 * Resolve the strike **alpha** (`0..1`) and the **strike index** at [elapsedMs]: full-bright during
 * `strikeMs`, fading over `decayMs`, dark during `gapMs`; with `loop` each cycle is a *new* bolt (the
 * returned [OGStrike.index] changes so the geometry can be reseeded). Pure + deterministic.
 */
fun lightningStrikeAt(spec: OGLightningSpec, elapsedMs: Float): OGStrike {
    val cycle = spec.cycleMs
    if (cycle <= 0f) return OGStrike(1f, 0)
    val idx = if (spec.loop) (elapsedMs / cycle).toInt() else 0
    val t = if (spec.loop) elapsedMs - idx * cycle else elapsedMs.coerceIn(0f, cycle)
    val alpha = when {
        t <= spec.strikeMs -> 1f
        t <= spec.strikeMs + spec.decayMs && spec.decayMs > 0f -> 1f - (t - spec.strikeMs) / spec.decayMs
        t <= spec.strikeMs + spec.decayMs -> 0f
        else -> 0f
    }
    return OGStrike(alpha.coerceIn(0f, 1f), idx)
}

/** One frame of a running bolt: its [alpha] and the [index] of the current strike (for reseeding). */
data class OGStrike(val alpha: Float, val index: Int)

/**
 * Build a jagged channel from [a] to [b] by **recursive midpoint displacement** to [detail] levels
 * (→ `2^detail` segments), each midpoint pushed along the segment normal by up to [jaggedness] (in the
 * endpoints' own units), the amplitude halving each level. Deterministic from [seed]. Hand in
 * pixel-space endpoints + a pixel [jaggedness] (as [OGLightningView] does) so the jag is isotropic.
 */
fun lightningChannel(a: OGPoint, b: OGPoint, jaggedness: Float, detail: Int, seed: Int): List<OGPoint> {
    var pts = mutableListOf(a, b)
    var amp = jaggedness
    val levels = detail.coerceIn(1, 9)
    var counter = 0
    repeat(levels) { level ->
        val next = ArrayList<OGPoint>(pts.size * 2)
        for (i in 0 until pts.size - 1) {
            val p = pts[i]; val q = pts[i + 1]
            next.add(p)
            val mx = (p.x + q.x) * 0.5f; val my = (p.y + q.y) * 0.5f
            val dx = q.x - p.x; val dy = q.y - p.y
            val len = sqrt(dx * dx + dy * dy)
            if (len > 1e-5f) {
                val nx = -dy / len; val ny = dx / len
                val disp = OGFxMath.hashSigned(counter++, seed + level * 101) * amp
                next.add(OGPoint(mx + nx * disp, my + ny * disp))
            } else {
                next.add(OGPoint(mx, my))
            }
        }
        next.add(pts.last())
        pts = next
        amp *= 0.5f
    }
    return pts
}

/**
 * Build the forked sub-branches of a bolt: [OGLightningSpec.branches] shorter channels rooted at
 * points along the [main] channel, each heading off at an angle and jagging like the main. In the
 * same units as [main]; pass [widthScale] = shorter side (px). Deterministic from [OGLightningSpec.seed]
 * + [strikeIndex].
 */
fun lightningBranches(main: List<OGPoint>, spec: OGLightningSpec, strikeIndex: Int, widthScale: Float): List<List<OGPoint>> {
    val n = main.size
    val nB = spec.branches.coerceIn(0, 12)
    if (n < 3 || nB == 0) return emptyList()
    val seed = spec.seed + strikeIndex * 7919
    // Main length (for branch length scaling).
    var mainLen = 0f
    for (i in 1 until n) { val dx = main[i].x - main[i - 1].x; val dy = main[i].y - main[i - 1].y; mainLen += sqrt(dx * dx + dy * dy) }
    val out = ArrayList<List<OGPoint>>(nB)
    for (bi in 0 until nB) {
        val at = (0.15f + 0.7f * OGFxMath.hash01(bi, seed)) // along the main, 0.15..0.85
        val idx = (at * (n - 1)).toInt().coerceIn(1, n - 2)
        val root = main[idx]
        // Heading: the local main direction rotated by a random ± angle.
        val p = main[idx - 1]; val q = main[idx + 1]
        var dx = q.x - p.x; var dy = q.y - p.y
        val dl = sqrt(dx * dx + dy * dy)
        if (dl < 1e-5f) continue
        dx /= dl; dy /= dl
        val ang = OGFxMath.hashSigned(bi * 3 + 1, seed) * 0.9f // up to ~±50°
        val ca = kotlin.math.cos(ang); val sa = kotlin.math.sin(ang)
        val hx = dx * ca - dy * sa; val hy = dx * sa + dy * ca
        val len = mainLen * spec.branchLength * (0.6f + 0.6f * OGFxMath.hash01(bi * 5 + 2, seed))
        val tip = OGPoint(root.x + hx * len, root.y + hy * len)
        out.add(lightningChannel(root, tip, widthScale * spec.jaggedness * 0.5f, (spec.detail - 2).coerceIn(1, 7), seed + bi * 131))
    }
    return out
}

/** Sanitize endpoints into finite `0..1` — guards a sloppy decoded spec. */
internal fun OGLightningSpec.endpointA() = OGPoint(clamp01(x1), clamp01(y1))
internal fun OGLightningSpec.endpointB() = OGPoint(clamp01(x2), clamp01(y2))
