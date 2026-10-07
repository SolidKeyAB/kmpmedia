package com.solidkey.painpoints.style

import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.shape.catmullRomClosedCubics
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The **geometry** half of the procedural `style` family — pure, deterministic vertex-list
 * transforms that extend the pipeline beyond [OGBoil.kt]'s boil / quantize / pixelate. Each one
 * takes a closed outline in the points' own units (e.g. a normalized `0..1` [com.solidkey.painpoints.shape.OGPolygonShape])
 * and returns a new outline, so they compose with the rest of the pipeline index-after-index and
 * drop into any `clipShape`. All are allocation-light (one output list), hold 60fps, and give the
 * same result on Android and iOS frame-for-frame (no RNG, no platform calls) — the same perf gate
 * the rest of the family clears.
 *
 * They are wired into [OGStyle] as the `subdivide`, `smooth`, `roughen` and `wave` ops; see
 * [OGStyleSpec] for the JSON contract.
 */

/**
 * Densify a closed outline by inserting [detail] evenly spaced points along **each** edge (straight,
 * linear interpolation) — the shape is unchanged, it just gains vertices. Put it *before* a
 * displacement op (`boil` / `wave` / `roughen`) so a low-vertex outline (say a 4-point diamond) has
 * enough points to move richly instead of only shifting at its corners. Order preserved; pure.
 * `detail < 1` (or fewer than 2 points) returns the input untouched.
 */
fun subdivideOutline(points: List<OGPoint>, detail: Int): List<OGPoint> {
    val n = points.size
    if (detail < 1 || n < 2) return points
    val out = ArrayList<OGPoint>(n * (detail + 1))
    for (i in 0 until n) {
        val a = points[i]
        val b = points[(i + 1) % n]
        out.add(a)
        for (k in 1..detail) {
            val t = k / (detail + 1f)
            out.add(OGPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
        }
    }
    return out
}

/**
 * **Round** a closed outline by resampling it through the closed **centripetal Catmull-Rom** spline
 * (shared with [com.solidkey.painpoints.shape.OGPolygonShape]'s `smoothing`), emitting [detail]
 * samples per edge. [strength] `0..1` scales the roundness: `0` traces the original straight edges,
 * `1` is full roundness. The curve passes through every original vertex; the result has `n * detail`
 * vertices. Static (no time); pure. Needs ≥ 3 points (returns the input otherwise).
 */
fun smoothOutline(points: List<OGPoint>, strength: Float, detail: Int): List<OGPoint> {
    val n = points.size
    if (n < 3) return points
    val d = detail.coerceAtLeast(1)
    val segments = catmullRomClosedCubics(points, strength.coerceIn(0f, 1f))
    val out = ArrayList<OGPoint>(n * d)
    for (i in 0 until n) {
        val p0 = points[i]
        val s = segments[i] // control1, control2, end (= points[i+1])
        for (k in 0 until d) {
            val t = k / d.toFloat() // [0,1): start on each original vertex, don't duplicate the join
            out.add(cubicAt(p0, s.control1, s.control2, s.end, t))
        }
    }
    return out
}

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

/**
 * **Roughen** a closed outline into a hand-drawn / sketch edge: subdivide each edge into [detail]
 * pieces, then push every vertex by a **static** (time-independent) pseudo-random offset up to
 * [amplitude] (in the points' units) on each axis. Deterministic from [seed] — a different seed is a
 * different rough edge, the same seed is identical on Android, iOS, preview and export. Unlike
 * [OGBoil] this does *not* animate: it is a fixed rough edge (pair it with `boil` if you want it to
 * also wobble). Pure. `amplitude <= 0` (or fewer than 2 points) returns the input.
 */
fun roughenVertices(points: List<OGPoint>, amplitude: Float, detail: Int, seed: Int): List<OGPoint> {
    if (amplitude <= 0f || points.size < 2) return points
    val dense = subdivideOutline(points, detail.coerceAtLeast(1))
    return dense.mapIndexed { i, p ->
        OGPoint(p.x + hashNoise(i, 0, seed) * amplitude, p.y + hashNoise(i, 1, seed) * amplitude)
    }
}

/**
 * A travelling **wave / ripple**: displace every vertex along its local outline normal by a sine
 * whose phase advances [waves] full cycles around the perimeter and [speed] cycles per second,
 * sampled at [timeMs]. Smooth and directional, unlike [OGBoil]'s random jitter — a flowing ripple /
 * underwater / flag edge. Pure + deterministic. Pair with [subdivideOutline] for a fine ripple on a
 * low-vertex outline. `amplitude <= 0` (or fewer than 3 points) returns the input.
 */
fun waveVertices(
    points: List<OGPoint>,
    amplitude: Float,
    waves: Int,
    speed: Float,
    timeMs: Long,
): List<OGPoint> {
    val n = points.size
    if (amplitude <= 0f || n < 3) return points
    val cyclesAround = waves.coerceAtLeast(1)
    val phase = (timeMs / 1000.0 * speed).toFloat() * TWO_PI
    return List(n) { i ->
        val prev = points[(i - 1 + n) % n]
        val next = points[(i + 1) % n]
        // Outline normal ≈ perpendicular of the tangent (next - prev).
        val tx = next.x - prev.x
        val ty = next.y - prev.y
        val len = sqrt(tx * tx + ty * ty)
        val p = points[i]
        if (len < 1e-6f) {
            p
        } else {
            val nx = ty / len
            val ny = -tx / len
            val offset = amplitude * sin(TWO_PI * cyclesAround * (i / n.toFloat()) + phase)
            OGPoint(p.x + nx * offset, p.y + ny * offset)
        }
    }
}

private const val TWO_PI = (2.0 * PI).toFloat()

/** Static pseudo-random value in ~`[-1, 1]` from an integer hash (no RNG, no time) — matches [OGBoil]'s hash. */
private fun hashNoise(i: Int, axis: Int, seed: Int): Float {
    var h = i * 374761393 + axis * 1013904223 + seed * 1274126177
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 32768f - 1f
}
