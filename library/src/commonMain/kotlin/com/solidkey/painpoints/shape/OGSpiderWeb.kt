package com.solidkey.painpoints.shape

import kotlin.math.cos
import kotlin.math.sin

/**
 * A seeded procedural spider web — a deterministic `seed` → a unique radial web as plain line
 * segments. [spokes] radiate from the centre; [rings] concentric rings connect adjacent spokes at
 * increasing radii. Each seed varies spoke angles and ring spacing so two webs placed side-by-side
 * read as naturally distinct.
 *
 * Result segments are in a unit circle centred at `(0,0)` — scale + translate to place them.
 * Render as stroked paths; webs are line figures, not filled shapes.
 */
object OGSpiderWeb {

    /**
     * Generate a web for [seed]. [spokes] = radial line count (8 is natural, 4–16); [rings] =
     * concentric ring count (5 is natural, 2–12). The result fits a unit circle centred at `(0,0)`.
     */
    fun generate(seed: Int, spokes: Int = 8, rings: Int = 5): List<OGSnowflakeSegment> {
        val s = spokes.coerceIn(4, 16)
        val r = rings.coerceIn(2, 12)
        var rng = (seed * -1640531527) xor 0x9E3779B1.toInt()
        fun next(): Float {
            rng = rng xor (rng shl 13); rng = rng xor (rng ushr 17); rng = rng xor (rng shl 5)
            return ((rng ushr 8) and 0xFFFFFF) / 16_777_216f
        }

        // Spoke angles — base = even spacing, jittered slightly per seed
        val angles = FloatArray(s) { k ->
            k * (TWO_PI / s) + (next() - 0.5f) * (0.4f * TWO_PI / s)
        }

        // Ring radii — irregular inward-to-outward spacing
        var cumulative = 0.08f + next() * 0.04f
        val radii = FloatArray(r)
        for (k in 0 until r) {
            val step = (0.78f / r) * (0.65f + next() * 0.7f)
            cumulative = (cumulative + step).coerceAtMost(0.98f)
            radii[k] = cumulative
        }

        val out = ArrayList<OGSnowflakeSegment>(s * (r + 2) + s)
        val hubR = 0.04f + next() * 0.02f

        // Spokes: hub edge → outermost ring
        for (k in 0 until s) {
            val a = angles[k]
            out += OGSnowflakeSegment(
                OGPoint(cos(a) * hubR, sin(a) * hubR),
                OGPoint(cos(a) * radii[r - 1], sin(a) * radii[r - 1]),
            )
        }

        // Hub ring (small polygon around centre)
        for (k in 0 until s) {
            val a0 = angles[k]; val a1 = angles[(k + 1) % s]
            out += OGSnowflakeSegment(
                OGPoint(cos(a0) * hubR, sin(a0) * hubR),
                OGPoint(cos(a1) * hubR, sin(a1) * hubR),
            )
        }

        // Concentric rings: two segments per inter-spoke arc (with a slight inward sag mid-span)
        for (ri in 0 until r) {
            val rad = radii[ri]
            for (k in 0 until s) {
                val a0 = angles[k]; val a1 = angles[(k + 1) % s]
                val midA = (a0 + a1) * 0.5f
                val sagR = rad * (0.92f + next() * 0.09f)   // slight inward bow
                val mx = cos(midA) * sagR; val my = sin(midA) * sagR
                out += OGSnowflakeSegment(OGPoint(cos(a0) * rad, sin(a0) * rad), OGPoint(mx, my))
                out += OGSnowflakeSegment(OGPoint(mx, my), OGPoint(cos(a1) * rad, sin(a1) * rad))
            }
        }

        return out
    }

    private const val TWO_PI = 6.2831855f
}

/**
 * A seeded procedural leaf vein skeleton — a deterministic `seed` → a unique branching leaf as plain
 * line segments. A central **midrib** runs top-to-bottom; [veins] alternating side veins branch off it
 * at seeded angles, each optionally sprouting [depth] levels of smaller sub-veins.
 *
 * Result segments fit a `1×2` bounding box centred at `(0,0)` (tip at `y = -1`, base at `y = +1`).
 * Scale and translate to fit your canvas.
 */
object OGLeafVeins {

    /**
     * Generate a leaf for [seed]. [veins] = side-vein pairs per side (1–8; default 4); [depth] =
     * sub-vein recursion (0–3; default 2). Result fits a `1×2` bounding box centred at `(0,0)`.
     */
    fun generate(seed: Int, veins: Int = 4, depth: Int = 2): List<OGSnowflakeSegment> {
        val v = veins.coerceIn(1, 8)
        val d = depth.coerceIn(0, 3)
        var rng = (seed * 1013904223) xor 0x6C62272E.toInt()
        fun next(): Float {
            rng = rng xor (rng shl 17); rng = rng xor (rng ushr 13); rng = rng xor (rng shl 5)
            return ((rng ushr 8) and 0xFFFFFF) / 16_777_216f
        }

        val out = ArrayList<OGSnowflakeSegment>()
        // Midrib from (0,-1) to (0,+1)
        out += OGSnowflakeSegment(OGPoint(0f, -1f), OGPoint(0f, 1f))

        // Side veins: v pairs, each at a seeded y position along the midrib
        // Top quarter and bottom quarter avoided — veins sit in the middle 60% of the midrib
        for (i in 0 until v) {
            val t = -0.6f + (i + 0.5f + (next() - 0.5f) * 0.4f) / v * 1.2f  // y along midrib [-0.6..0.6]
            val baseLen = (0.3f + next() * 0.3f) * (1f - t * t)                // shorter near tip/base
            val angle = (38f + next() * 28f) * DEG                             // 38°..66° from midrib

            // Right vein
            growVein(out, 0f, t, angle, baseLen, d, true) { next() }
            // Left vein (mirror X)
            growVein(out, 0f, t, angle, baseLen, d, false) { next() }
        }

        return out
    }

    private fun growVein(
        out: MutableList<OGSnowflakeSegment>,
        bx: Float, by: Float, angle: Float, len: Float, depth: Int, right: Boolean,
        next: () -> Float,
    ) {
        val sign = if (right) 1f else -1f
        val tx = bx + sign * cos(angle) * len
        val ty = by - sin(angle) * len           // tip goes upward (y decreases)
        out += OGSnowflakeSegment(OGPoint(bx, by), OGPoint(tx, ty))
        if (depth <= 0) return
        val subLen = len * (0.4f + next() * 0.2f)
        val subAngle = angle + (8f + next() * 15f) * DEG  // child fans out a bit more
        growVein(out, tx, ty, subAngle, subLen, depth - 1, right, next)
        if (depth >= 2) {
            val subAngle2 = angle - (5f + next() * 10f) * DEG
            growVein(out, tx, ty, subAngle2, subLen * 0.7f, depth - 2, right, next)
        }
    }

    private const val DEG = 0.017453292f
}
