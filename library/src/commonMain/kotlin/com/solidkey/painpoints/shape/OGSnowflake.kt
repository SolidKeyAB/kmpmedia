package com.solidkey.painpoints.shape

import kotlin.math.cos
import kotlin.math.sin

/** One straight twig of a snowflake crystal, in a unit space centred at `(0,0)` (roughly `[-1,1]`). */
data class OGSnowflakeSegment(val a: OGPoint, val b: OGPoint)

/**
 * A **procedural snowflake generator** — a deterministic `seed` → a unique, n-fold **symmetric ice
 * crystal** as plain line segments, no two alike. It is the vector-geometry sibling of the lib's other
 * generative shape makers ([OGParametricShape] / [OGPolygonShape]): pure maths, zero dependency, no
 * Compose — the caller turns the segments into a `Path` (strokes read as a crystal; snowflakes are line
 * figures, not filled blobs).
 *
 * How it's built: one **arm** is grown along +Y — a central spine plus a few **side-twigs** at seeded
 * positions / angles / lengths. Each twig is a tiny **recursive fractal** (a seeded dendrite): at
 * [depth] > 0 every twig sprouts its own smaller side-twigs, and those sprout again, exactly the way a
 * real stellar-dendrite crystal grows. Only the right half of the arm is grown and then **mirrored**
 * left↔right so the arm is bilaterally symmetric; that whole arm is copied by rotating it around the
 * centre [arms] times (6 = a real snowflake's hexagonal symmetry), so every arm matches while the
 * crystal stays unique per seed. [depth] 0 = the old plain-prong look; 2 (default) reads as a crystal.
 */
object OGSnowflake {

    /**
     * Generate a crystal for [seed]. [arms] = radial symmetry (6 is natural); [branches] = primary
     * side-twigs per arm; [depth] = fractal recursion — how many times a twig sprouts smaller twigs
     * (0 = straight prongs like before, 2 = a convincing dendrite, capped at 4). The result is in a unit
     * circle centred at `(0,0)`; scale + translate it to place it.
     */
    fun generate(
        seed: Int,
        arms: Int = 6,
        branches: Int = 3,
        depth: Int = 2,
    ): List<OGSnowflakeSegment> {
        val a = arms.coerceIn(3, 12)
        val b = branches.coerceIn(0, 6)
        val d = depth.coerceIn(0, 4)
        var rng = (seed * -1640531527) xor 0x9E3779B1.toInt()
        fun next(): Float { // xorshift → [0,1)
            rng = rng xor (rng shl 13); rng = rng xor (rng ushr 17); rng = rng xor (rng shl 5)
            return ((rng ushr 8) and 0xFFFFFF) / 16_777_216f
        }

        // Grow only the RIGHT half of one arm (twigs leaning +x off the +Y spine), recursing per twig,
        // then mirror it to the left — guarantees the arm is bilaterally symmetric about its spine.
        val tip = 0.55f + next() * 0.45f            // spine length (0.55..1.0)
        val right = ArrayList<OGSnowflakeSegment>()
        for (i in 0 until b) {
            val t = 0.25f + (i + next()) / b.coerceAtLeast(1) * 0.65f // position along the spine (0.25..0.9)
            val baseY = t * tip
            val len = (1f - t) * tip * (0.35f + next() * 0.4f)        // shorter twigs toward the tip
            val lean = (28f + next() * 34f) * DEG                     // 28°..62° off the spine
            growTwig(right, 0f, baseY, lean, len, d) { next() }
        }

        val arm = ArrayList<OGSnowflakeSegment>(1 + right.size * 2)
        arm += OGSnowflakeSegment(OGPoint(0f, 0f), OGPoint(0f, tip))  // spine (on the mirror axis)
        arm += right
        for (s in right) arm += OGSnowflakeSegment(OGPoint(-s.a.x, s.a.y), OGPoint(-s.b.x, s.b.y))

        // Rotate the finished arm into all [a] directions.
        val out = ArrayList<OGSnowflakeSegment>(arm.size * a)
        for (k in 0 until a) {
            val ang = k * (TWO_PI / a)
            val c = cos(ang); val s = sin(ang)
            for (seg in arm) {
                out += OGSnowflakeSegment(rotate(seg.a, c, s), rotate(seg.b, c, s))
            }
        }
        return out
    }

    /**
     * Emit one twig from ([bx],[by]) pointing [ang] off the +Y axis with length [len], then — while
     * [depth] > 0 — recursively sprout smaller twigs along it to either side, the fractal that turns a
     * bare prong into a dendrite. [next] is the shared seeded RNG so the whole crystal stays deterministic.
     */
    private fun growTwig(
        out: MutableList<OGSnowflakeSegment>,
        bx: Float, by: Float, ang: Float, len: Float, depth: Int, next: () -> Float,
    ) {
        val tx = bx + sin(ang) * len
        val ty = by + cos(ang) * len
        out += OGSnowflakeSegment(OGPoint(bx, by), OGPoint(tx, ty))
        if (depth <= 0) return
        val kids = if (depth >= 2) 2 else 1
        for (i in 0 until kids) {
            val t = 0.35f + (i + next() * 0.9f) / kids * 0.5f   // where along this twig (0.35..0.85)
            val px = bx + (tx - bx) * t
            val py = by + (ty - by) * t
            val childLen = len * (0.4f + next() * 0.25f)        // children are ~40–65% of the parent
            val sub = (24f + next() * 30f) * DEG                // sprout angle off the parent twig
            growTwig(out, px, py, ang + sub, childLen, depth - 1, next)  // outward child
            growTwig(out, px, py, ang - sub, childLen, depth - 1, next)  // inward child
        }
    }

    private fun rotate(p: OGPoint, c: Float, s: Float): OGPoint =
        OGPoint(p.x * c - p.y * s, p.x * s + p.y * c)

    private const val DEG = 0.017453292f
    private const val TWO_PI = 6.2831855f
}
