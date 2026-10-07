package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGPointSpec
import com.solidkey.painpoints.ai.clamp01
import com.solidkey.painpoints.shape.OGPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The **breathing-slash ribbon** — the signature anime-action effect, described as plain,
 * serializable data: a glowing, tapered arc that draws on along a [path] and stays alive with a
 * flowing edge and a breathing width. One primitive, three signature forms chosen by DATA, not code:
 * a **water** sweep, a **flame** lick, a **thunder** bolt (see [OGSlashes] presets). It is the
 * motion sibling of [com.solidkey.painpoints.particle.OGParticleSpec] (particles) and
 * [com.solidkey.painpoints.shape.OGParametricSpec] (shapes): a designer or a language model authors a
 * `.slash` pack and the lib brings it to life, no code and no rebuild. Decode/encode + ready-made
 * presets live on [OGSlashes]; drop it on screen with [OGSlashView].
 *
 * **Coordinates.** [path] vertices are normalized `0..1` over the view box (head → tail); [width]
 * and [edgeAmp] are fractions of the box's shorter side; angles/phases are derived, times are ms.
 *
 * **Perf.** The expensive part — the smooth centreline through [path] — is sampled **once** per spec
 * (parse-once) and only the cheap per-frame work (width profile, a sine/noise edge, the draw-on
 * reveal) runs each frame. There is no RNG object churn (a tiny integer hash), a hard [samples] cap,
 * and all the math is pure, so from the same [seed] it renders frame-identically on Android and iOS.
 * This is the real-app/60fps perf gate the rest of the library clears.
 */
@kotlinx.serialization.Serializable
data class OGSlashSpec(
    val name: String? = null,
    /** The blade trajectory in normalized `0..1`, head → tail. Needs ≥ 2 points; a smooth curve is fit through them. */
    val path: List<OGPointSpec> = DEFAULT_PATH,
    /** Max ribbon half-thickness as a fraction of the box's shorter side. */
    val width: Float = 0.11f,
    /** Tip sharpness: higher tapers the two ends to a finer point (`~0.1..4`). */
    val taper: Float = 0.9f,
    /** Where along the length the ribbon is widest, `0..1` (`0.4` = just past the head). */
    val peak: Float = 0.42f,
    /** Centreline roundness, `0..1` (`0` = straight segments through the path, `1` = full curve). */
    val smoothing: Float = 1f,
    /** Centreline resolution — points sampled along the arc. The perf ceiling; clamped `4..512`. */
    val samples: Int = 72,
    /** Flowing gradient colour at the head — any SVG colour string (`#RGB`/`#RRGGBB`/`#AARRGGBB`/`rgb()`/name). */
    val colorStart: String = "#4FC3F7",
    /** Flowing gradient colour at the tail. */
    val colorEnd: String = "#01579B",
    /** Additive bloom strength, `0` (flat) .. `1` (strong neon glow). */
    val glow: Float = 0.7f,
    /** Glow colour; `null` uses [colorStart]. */
    val glowColor: String? = null,
    /** Alive edge: `smooth` · `wave` (water ripple) · `rough` (flame lick) · `bolt` (thunder jag). */
    val edge: String = "wave",
    /** Edge displacement amplitude, as a fraction of the box's shorter side. */
    val edgeAmp: Float = 0.02f,
    /** Edge flow / flicker speed in cycles per second. */
    val edgeSpeed: Float = 1.2f,
    /** Edge richness: wave count (`wave`) or jag/lick detail (`rough`/`bolt`). Clamped `1..16`. */
    val edgeDetail: Int = 4,
    /** Width pulsation ("breathing") amplitude, `0..1`. */
    val breatheAmp: Float = 0.14f,
    /** Breathing speed in cycles per second. */
    val breatheSpeed: Float = 0.9f,
    /** Head→tail draw-on time in ms (`0` = appear instantly, fully drawn). */
    val revealMs: Float = 420f,
    /** How long the fully-drawn slash holds before fading, in ms. */
    val holdMs: Float = 900f,
    /** Fade-out time in ms. */
    val fadeMs: Float = 360f,
    /** Re-trigger the whole draw-on → hold → fade cycle forever. */
    val loop: Boolean = true,
    /** Seed for the deterministic edge noise (`rough`/`bolt`) — change it for a different-but-repeatable edge. */
    val seed: Int = 0,
) {
    /** Resolve the alive-edge mode once. */
    val edgeMode: OGSlashEdge
        get() = when (edge.lowercase()) {
            "wave", "water", "flow" -> OGSlashEdge.WAVE
            "rough", "turbulence", "flame", "fire" -> OGSlashEdge.ROUGH
            "bolt", "thunder", "lightning", "jag" -> OGSlashEdge.BOLT
            else -> OGSlashEdge.SMOOTH
        }

    /** Total one-shot duration (reveal + hold + fade) in ms; the loop period. */
    val cycleMs: Float get() = revealMs.coerceAtLeast(0f) + holdMs.coerceAtLeast(0f) + fadeMs.coerceAtLeast(0f)

    companion object {
        /** A sweeping diagonal arc — the default blade trajectory. */
        val DEFAULT_PATH: List<OGPointSpec> = listOf(
            OGPointSpec(0.10f, 0.34f),
            OGPointSpec(0.34f, 0.20f),
            OGPointSpec(0.60f, 0.34f),
            OGPointSpec(0.88f, 0.66f),
        )
    }
}

/** The alive-edge style, resolved once from [OGSlashSpec.edge]. */
enum class OGSlashEdge { SMOOTH, WAVE, ROUGH, BOLT }

/**
 * One frame of a running slash: the [reveal] fraction `0..1` (how much of the ribbon is drawn on)
 * and the overall [alpha] `0..1` (the fade). Derived from the spec's reveal/hold/fade timings; pure
 * so the lifecycle is unit-testable without a frame clock.
 */
data class OGSlashFrame(val reveal: Float, val alpha: Float)

/**
 * Resolve the draw-on [OGSlashFrame] at [elapsedMs] for [spec]: ramp [OGSlashFrame.reveal] 0→1 over
 * `revealMs`, hold, then fade [OGSlashFrame.alpha] 1→0 over `fadeMs`. With `loop` the elapsed time
 * wraps the cycle; without it, it clamps to the final (faded-out) frame. Pure + deterministic.
 */
fun slashFrameAt(spec: OGSlashSpec, elapsedMs: Float): OGSlashFrame {
    val cycle = spec.cycleMs
    if (cycle <= 0f) return OGSlashFrame(1f, 1f)
    val t = if (spec.loop) {
        val m = elapsedMs % cycle
        if (m < 0f) m + cycle else m
    } else {
        elapsedMs.coerceIn(0f, cycle)
    }
    val reveal = if (spec.revealMs > 0f) (t / spec.revealMs).coerceIn(0f, 1f) else 1f
    val fadeStart = spec.revealMs + spec.holdMs
    val alpha = when {
        spec.fadeMs <= 0f -> 1f
        t <= fadeStart -> 1f
        else -> (1f - (t - fadeStart) / spec.fadeMs).coerceIn(0f, 1f)
    }
    return OGSlashFrame(reveal, alpha)
}

/**
 * Sample the smooth **centreline** through [spec]'s path — an open, interpolating **centripetal
 * Catmull-Rom** curve (the open sibling of [com.solidkey.painpoints.shape.catmullRomClosedCubics])
 * that passes through every path vertex. Returns `spec.samples` points in normalized `0..1`. This is
 * the parse-once part: call it when the spec changes, then feed the result to [slashRibbonOutline]
 * every frame. Pure + affine-covariant; a path of < 2 points yields an empty list.
 */
fun slashCenterline(spec: OGSlashSpec): List<OGPoint> {
    val pts = spec.path.map { OGPoint(clamp01(it.x), clamp01(it.y)) }
    val n = pts.size
    if (n < 2) return emptyList()
    val total = spec.samples.coerceIn(4, 512)
    val s = spec.smoothing.coerceIn(0f, 1f)

    // Phantom endpoints (reflected) so the open curve has neighbours at both ends.
    val ext = ArrayList<OGPoint>(n + 2)
    ext.add(OGPoint(2f * pts[0].x - pts[1].x, 2f * pts[0].y - pts[1].y))
    ext.addAll(pts)
    ext.add(OGPoint(2f * pts[n - 1].x - pts[n - 2].x, 2f * pts[n - 1].y - pts[n - 2].y))

    fun knot(a: OGPoint, b: OGPoint): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        return maxOf(sqrt(sqrt(dx * dx + dy * dy)), 1e-4f)
    }

    val perSeg = maxOf(2, total / (n - 1))
    val out = ArrayList<OGPoint>(perSeg * (n - 1) + 1)
    out.add(pts[0])
    for (i in 0 until n - 1) {
        val p0 = ext[i]; val p1 = ext[i + 1]; val p2 = ext[i + 2]; val p3 = ext[i + 3]
        val d01 = knot(p0, p1); val d12 = knot(p1, p2); val d23 = knot(p2, p3)
        val m1x = (p1.x - p0.x) / d01 - (p2.x - p0.x) / (d01 + d12) + (p2.x - p1.x) / d12
        val m1y = (p1.y - p0.y) / d01 - (p2.y - p0.y) / (d01 + d12) + (p2.y - p1.y) / d12
        val m2x = (p2.x - p1.x) / d12 - (p3.x - p1.x) / (d12 + d23) + (p3.x - p2.x) / d23
        val m2y = (p2.y - p1.y) / d12 - (p3.y - p1.y) / (d12 + d23) + (p3.y - p2.y) / d23
        val h = s * d12 / 3f
        val c1 = OGPoint(p1.x + h * m1x, p1.y + h * m1y)
        val c2 = OGPoint(p2.x - h * m2x, p2.y - h * m2y)
        for (k in 1..perSeg) {
            val t = k / perSeg.toFloat()
            out.add(cubicAt(p1, c1, c2, p2, t))
        }
    }
    return out
}

/**
 * Build the closed ribbon **outline** for one frame: offset the [centerline] (from [slashCenterline])
 * by a tapered half-width on each side, apply the breathing pulse + the alive edge ([OGSlashEdge]),
 * and truncate to the drawn-on [reveal] fraction (`0..1`) with a tapering leading tip. [timeMs] drives
 * the flow/flicker. Returns a closed polygon in the centreline's own units (left edge forward, right
 * edge back), ready to drop into a `Path` and fill.
 *
 * Pass the [centerline] already in the units you will draw in (e.g. pixels) together with
 * [widthScale] — the pixels-per-width-unit that turns [OGSlashSpec.width]/[OGSlashSpec.edgeAmp]
 * (fractions of the box's shorter side) into those units. The normals are computed in the centreline's
 * space, so handing in a pixel-space centreline gives a ribbon of correct, uniform thickness on any
 * aspect ratio. Pure + deterministic from [OGSlashSpec.seed]; an empty or too-short centreline (or
 * `reveal <= 0`) yields an empty outline.
 */
fun slashRibbonOutline(
    centerline: List<OGPoint>,
    spec: OGSlashSpec,
    reveal: Float,
    timeMs: Long,
    widthScale: Float = 1f,
): List<OGPoint> {
    val m = centerline.size
    if (m < 2) return emptyList()
    val r = reveal.coerceIn(0f, 1f)
    if (r <= 0f) return emptyList()

    val lastIdx = m - 1
    val frontier = (r * lastIdx) // fractional index of the growing tip
    val k = frontier.toInt().coerceIn(0, lastIdx) // last fully-included sample
    if (k < 1) return emptyList()

    val maxHalf = spec.width.coerceAtLeast(0f) * widthScale
    val amp = spec.edgeAmp * widthScale
    val peak = spec.peak.coerceIn(0.001f, 0.999f)
    val exp = spec.taper.coerceIn(0.1f, 4f).toDouble()
    val breathe = 1f + spec.breatheAmp.coerceIn(0f, 1f) * sin(TWO_PI * spec.breatheSpeed * timeMs / 1000f)
    val mode = spec.edgeMode
    val waves = spec.edgeDetail.coerceIn(1, 16)
    val tSec = timeMs / 1000f
    val phase = TWO_PI * spec.edgeSpeed * tSec
    val flowT = tSec * spec.edgeSpeed // continuous time coord for the flowing simplex field
    val tipWindow = 0.07f // leading tip tapers to a point over this fraction of the length

    val left = ArrayList<OGPoint>(k + 1)
    val right = ArrayList<OGPoint>(k + 1)
    for (i in 0..k) {
        val c = centerline[i]
        // Local tangent from neighbours → unit normal.
        val a = centerline[(i - 1).coerceAtLeast(0)]
        val b = centerline[(i + 1).coerceAtMost(lastIdx)]
        var tx = b.x - a.x; var ty = b.y - a.y
        val tl = sqrt(tx * tx + ty * ty)
        if (tl < 1e-6f) { tx = 1f; ty = 0f } else { tx /= tl; ty /= tl }
        val nx = ty; val ny = -tx

        val u = i / lastIdx.toFloat()
        // Tapered width profile: a triangular peak raised to `exp` → fine points at both ends.
        val tt = (if (u <= peak) u / peak else (1f - u) / (1f - peak)).coerceIn(0f, 1f)
        var half = maxHalf * tt.toDouble().pow(exp).toFloat() * breathe
        // Leading tip: taper to zero right at the draw-on frontier so the growth looks pointed.
        if (r < 1f) {
            val fade = ((frontier - i) / (tipWindow * m)).coerceIn(0f, 1f)
            half *= fade
        }

        var cx = c.x; var cy = c.y
        var offL = 0f; var offR = 0f
        when (mode) {
            OGSlashEdge.SMOOTH -> {}
            OGSlashEdge.WAVE -> {
                // A travelling sine (clean water ripple) with a little flowing simplex wobble on top.
                val w = amp * sin(TWO_PI * waves * u - phase) +
                    amp * 0.35f * OGFxNoise.fbm(u * waves * 0.5f, flowT * 0.7f, 2, spec.seed)
                offL = w; offR = w // symmetric bulge
            }
            OGSlashEdge.ROUGH -> {
                // Independent flowing fbm per side — an organic, licking flame edge.
                offL = amp * OGFxNoise.fbm(u * waves, flowT, 3, spec.seed)
                offR = amp * OGFxNoise.fbm(u * waves + 31.7f, flowT + 11.3f, 3, spec.seed)
            }
            OGSlashEdge.BOLT -> {
                // Jag the centreline (not the edges), endpoints pinned — a forked thunder bolt.
                if (i in 1 until k) {
                    val j = amp * 3f * OGFxNoise.fbm(u * waves, flowT, 2, spec.seed)
                    cx += nx * j; cy += ny * j
                }
            }
        }
        left.add(OGPoint(cx + nx * (half + offL), cy + ny * (half + offL)))
        right.add(OGPoint(cx - nx * (half + offR), cy - ny * (half + offR)))
    }

    val outline = ArrayList<OGPoint>(left.size + right.size)
    outline.addAll(left)
    for (i in right.indices.reversed()) outline.add(right[i])
    return outline
}

/**
 * Generate the **forked branches** of a `bolt`-edge slash — the secondary lightning channels that
 * split off the main bolt (fractal lightning: each branch roots on the main channel and jags away with
 * a tapering tip, after the recursive midpoint-displacement / dielectric-breakdown model). Returns a
 * list of polylines in the [centerline]'s units (hand in a pixel-space centreline + [widthScale] =
 * shorter side, as [slashRibbonOutline] does). Only `bolt` slashes branch; everything else returns
 * empty. Pure + deterministic from [OGSlashSpec.seed]; `reveal <= 0` (or too short) returns empty.
 *
 * See `docs/SLASH.md` (References) — fractal lightning via midpoint displacement + branching.
 */
fun slashBranches(
    centerline: List<OGPoint>,
    spec: OGSlashSpec,
    reveal: Float,
    timeMs: Long,
    widthScale: Float = 1f,
): List<List<OGPoint>> {
    if (spec.edgeMode != OGSlashEdge.BOLT) return emptyList()
    val m = centerline.size
    if (m < 3) return emptyList()
    val r = reveal.coerceIn(0f, 1f)
    if (r <= 0f) return emptyList()

    val lastIdx = m - 1
    val tipIdx = (r * lastIdx).toInt().coerceIn(2, lastIdx)
    val flowT = timeMs / 1000f * spec.edgeSpeed
    val nBranches = (spec.edgeDetail / 2).coerceIn(2, 5)
    val out = ArrayList<List<OGPoint>>(nBranches)

    for (bi in 0 until nBranches) {
        val h = hash01(bi, spec.seed)
        val anchorIdx = (( 0.2f + 0.7f * h) * tipIdx).toInt().coerceIn(1, tipIdx - 1)
        val c = centerline[anchorIdx]
        val a = centerline[anchorIdx - 1]
        val b = centerline[(anchorIdx + 1).coerceAtMost(lastIdx)]
        var tx = b.x - a.x; var ty = b.y - a.y
        val tl = sqrt(tx * tx + ty * ty)
        if (tl < 1e-6f) { tx = 1f; ty = 0f } else { tx /= tl; ty /= tl }
        val nx = ty; val ny = -tx
        val side = if (hash01(bi * 7 + 1, spec.seed) > 0.5f) 1f else -1f
        // Branch heading: mostly outward along the normal, tilted forward along the bolt.
        var dx = side * nx * 0.85f + tx * 0.45f
        var dy = side * ny * 0.85f + ty * 0.45f
        val dl = sqrt(dx * dx + dy * dy)
        if (dl < 1e-6f) continue
        dx /= dl; dy /= dl
        val len = widthScale * (0.55f + 0.8f * hash01(bi * 13 + 3, spec.seed))
        val segs = 6
        val branch = ArrayList<OGPoint>(segs + 1)
        branch.add(c)
        var px = c.x; var py = c.y
        val step = len / segs
        for (k in 1..segs) {
            val t = k / segs.toFloat()
            px += dx * step; py += dy * step
            // Jag perpendicular to the branch via flowing fbm, tapering to the tip.
            val perp = OGFxNoise.fbm(t * 3f + bi * 5f, flowT, 2, spec.seed) * widthScale * 0.14f * (1f - t)
            branch.add(OGPoint(px + dy * perp, py - dx * perp))
        }
        out.add(branch)
    }
    return out
}

// ── pure helpers ───────────────────────────────────────────────────────────────────────────────

private const val TWO_PI = (2.0 * PI).toFloat()

/** Evaluate the cubic Bézier (p0, c1, c2, p3) at [t] in `0..1`. */
private fun cubicAt(p0: OGPoint, c1: OGPoint, c2: OGPoint, p3: OGPoint, t: Float): OGPoint {
    val u = 1f - t
    val a = u * u * u
    val b = 3f * u * u * t
    val c = 3f * u * t * t
    val e = t * t * t
    return OGPoint(
        a * p0.x + b * c1.x + c * c2.x + e * p3.x,
        a * p0.y + b * c1.y + c * c2.y + e * p3.y,
    )
}

/** Deterministic integer hash → `[0, 1)` — seeds branch anchors/angles/lengths without an RNG. */
private fun hash01(a: Int, b: Int): Float {
    var h = a * 374761393 + b * 1274126177 + 0x9E3779B1.toInt()
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return ((h ushr 8) and 0xFFFFFF) / 16_777_216f
}
