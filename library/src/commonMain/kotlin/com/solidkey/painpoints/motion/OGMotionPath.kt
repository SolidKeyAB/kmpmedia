package com.solidkey.painpoints.motion

import com.solidkey.painpoints.shape.OGCubicSegment
import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.shape.catmullRomClosedCubics
import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * An **arc-length-parameterized** path you can send an element gliding along, or reveal a stroke of.
 * Build it once from a handful of vertices (in **normalized** `0..1` space, same convention as
 * [OGPoint] everywhere else in the library) and then read [pointAt] / [tangentDegAt] / [trimmed] by a
 * `progress` in `0..1` — where `0` is the start and `1` the end, *measured by distance travelled*, not
 * by vertex index. So an evenly-advancing `progress` moves at a constant speed even when the vertices
 * are unevenly spaced.
 *
 * This is the pure-maths core of the motion-design pack: it holds the points plus their cumulative
 * lengths and does only O(log n) work per lookup, so animating many elements along paths stays on the
 * 60fps budget (the perf gate the rest of the library clears). It is platform-independent and directly
 * unit-testable (no Compose).
 *
 * Build an open polyline with [of], a smooth open curve through the points with [smoothOpen], or a
 * closed loop (optionally rounded) with [loop]. For a smooth curve, sampling happens once here, not
 * per frame. Non-finite input coordinates are treated as `0` (render-safe, like `scalePolygonPoints`).
 */
class OGMotionPath private constructor(
    /** The (possibly resampled) vertices this path walks through, in normalized `0..1` space. */
    val points: List<OGPoint>,
    // Cumulative arc length at each vertex; cum[0] == 0, cum.last() == total length. Parallel to points.
    private val cum: FloatArray,
) {
    /** Total length of the path in normalized units (0 for an empty or single-point path). */
    val length: Float get() = if (cum.isEmpty()) 0f else cum[cum.size - 1]

    /**
     * The point at [t] (`0..1`) along the path **by distance** (constant-speed). `0` = start,
     * `1` = end; out-of-range (or non-finite) is clamped. An empty path returns `(0,0)`; a single
     * point returns it.
     */
    fun pointAt(t: Float): OGPoint {
        if (points.isEmpty()) return OGPoint(0f, 0f)
        if (points.size == 1 || length <= 0f) return points.first()
        val target = (if (t.isFinite()) t else 0f).coerceIn(0f, 1f) * length
        val hi = upperIndex(target)
        val lo = hi - 1
        val segLen = cum[hi] - cum[lo]
        val local = if (segLen > 0f) (target - cum[lo]) / segLen else 0f
        val a = points[lo]
        val b = points[hi]
        return OGPoint(a.x + (b.x - a.x) * local, a.y + (b.y - a.y) * local)
    }

    /**
     * The travel **direction** at [t] (`0..1`) in degrees (clockwise from +x, y down — the screen
     * convention), i.e. the tangent of the segment the point sits on. Use it to turn an element to
     * face the way it is moving (`orient = true` on [Modifier.ogMotionPath]). `0` for a degenerate path.
     *
     * Pass [scaleX] / [scaleY] (the box's pixel width / height) to get the **on-screen** angle in a
     * non-square box; left at `1f` the angle is measured in normalized space. [Modifier.ogMotionPath]
     * supplies the container size so its orientation is exact. Zero-length segments (duplicate points)
     * are skipped so a repeated vertex can't snap the angle to 0.
     */
    fun tangentDegAt(t: Float, scaleX: Float = 1f, scaleY: Float = 1f): Float {
        if (points.size < 2 || length <= 0f) return 0f
        val target = (if (t.isFinite()) t else 0f).coerceIn(0f, 1f) * length
        var hi = upperIndex(target)
        // Skip forward over zero-length segments; if we ran into a trailing run of duplicates, back up.
        while (hi < points.size - 1 && cum[hi] == cum[hi - 1]) hi++
        if (cum[hi] == cum[hi - 1]) {
            var lo = hi
            while (lo > 1 && cum[lo] == cum[lo - 1]) lo--
            hi = lo
        }
        val a = points[hi - 1]
        val b = points[hi]
        return atan2((b.y - a.y) * scaleY, (b.x - a.x) * scaleX) * RAD2DEG
    }

    /**
     * The sub-path between arc-length fractions [start] and [end] (`0..1`), with both ends
     * interpolated exactly onto the path, as a polyline. This is what powers **draw-on**: animate
     * `end` from `0` to `1` and the stroke reveals itself; animate `start` too for a travelling dash.
     * Returns an empty list when `end <= start` or the path is degenerate. Scans only the vertices in
     * the revealed window, not the whole path.
     */
    fun trimmed(start: Float, end: Float): List<OGPoint> {
        if (points.size < 2 || length <= 0f) return emptyList()
        val s = (if (start.isFinite()) start else 0f).coerceIn(0f, 1f)
        val e = (if (end.isFinite()) end else 0f).coerceIn(0f, 1f)
        if (e <= s) return emptyList()
        val ds = s * length
        val de = e * length
        val out = ArrayList<OGPoint>()
        out.add(pointAt(s))
        var i = upperIndex(ds)
        while (i < points.size && cum[i] < de) {
            if (cum[i] > ds) out.add(points[i])
            i++
        }
        out.add(pointAt(e))
        return out
    }

    // Smallest index hi (>= 1) whose cumulative length reaches target. Binary search over cum.
    private fun upperIndex(target: Float): Int {
        var lo = 1
        var hi = cum.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cum[mid] < target) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        private const val RAD2DEG = 57.29578f

        /** An open polyline straight through [points] (in order). The simplest path. */
        fun of(points: List<OGPoint>): OGMotionPath {
            if (points.isEmpty()) return OGMotionPath(emptyList(), FloatArray(0))
            // Sanitize only if needed, so the all-finite common case stays allocation-free.
            val safe = if (points.all { it.x.isFinite() && it.y.isFinite() }) points
            else points.map { OGPoint(if (it.x.isFinite()) it.x else 0f, if (it.y.isFinite()) it.y else 0f) }
            val cum = FloatArray(safe.size)
            var acc = 0f
            for (i in 1 until safe.size) {
                val dx = safe[i].x - safe[i - 1].x
                val dy = safe[i].y - safe[i - 1].y
                acc += sqrt(dx * dx + dy * dy)
                cum[i] = acc
            }
            return OGMotionPath(safe, cum)
        }

        /** Convenience builder from raw `(x, y)` pairs in normalized `0..1` space. */
        fun of(vararg points: Pair<Float, Float>): OGMotionPath =
            of(points.map { OGPoint(it.first, it.second) })

        /**
         * A **smooth open** curve passing through [points] — a **centripetal** Catmull-Rom spline (the
         * same cusp-free spacing the library uses to smooth a lasso, see [catmullRomClosedCubics]),
         * sampled into [samplesPerSegment] per span. Endpoints are clamped, so the curve starts and
         * ends exactly on the first and last vertex and holds frame-identically on both platforms.
         * [smoothing] `0..1` scales the roundness (`0` falls back to the straight polyline). Fewer
         * than 3 points falls back to [of].
         */
        fun smoothOpen(points: List<OGPoint>, smoothing: Float = 1f, samplesPerSegment: Int = 16): OGMotionPath {
            if (points.size < 3) return of(points)
            val s = smoothing.coerceIn(0f, 1f)
            if (s <= 0f) return of(points)
            val spp = samplesPerSegment.coerceAtLeast(1)
            val segs = openCentripetalCubics(points, s)
            val out = ArrayList<OGPoint>(segs.size * spp + 1)
            out.add(points[0])
            for (i in segs.indices) {
                val p0 = points[i]
                val seg = segs[i]
                for (k in 1..spp) out.add(cubicPoint(p0, seg.control1, seg.control2, seg.end, k / spp.toFloat()))
            }
            return of(out)
        }

        /**
         * A **closed loop** through [points] (last links back to first) — e.g. orbit an element
         * forever. [smoothing] `0` keeps straight edges (a polygon loop); above `0` rounds it with the
         * library's centripetal Catmull-Rom, sampled into [samplesPerSegment] per edge (needs ≥3
         * points to round). Two points make a there-and-back loop; fewer than 2 falls back to [of].
         */
        fun loop(points: List<OGPoint>, smoothing: Float = 0f, samplesPerSegment: Int = 16): OGMotionPath {
            if (points.size < 2) return of(points)
            if (smoothing <= 0f || points.size < 3) return of(points + points[0])
            val segs = catmullRomClosedCubics(points, smoothing)
            val spp = samplesPerSegment.coerceAtLeast(1)
            val out = ArrayList<OGPoint>(segs.size * spp + 1)
            for (i in segs.indices) {
                val p0 = points[i]
                val seg = segs[i]
                for (k in 0 until spp) out.add(cubicPoint(p0, seg.control1, seg.control2, seg.end, k / spp.toFloat()))
            }
            out.add(points[0])
            return of(out)
        }

        // Open centripetal Catmull-Rom: one cubic Bézier span per edge (points[i] -> points[i+1]),
        // with clamped phantom endpoints. Mirrors catmullRomClosedCubics but open; s scales the handles.
        private fun openCentripetalCubics(points: List<OGPoint>, smoothing: Float): List<OGCubicSegment> {
            val n = points.size
            fun knot(a: OGPoint, b: OGPoint): Float {
                val dx = b.x - a.x
                val dy = b.y - a.y
                return maxOf(sqrt(sqrt(dx * dx + dy * dy)), 1e-4f)
            }
            return List(n - 1) { i ->
                val p0 = points[if (i - 1 < 0) 0 else i - 1]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[if (i + 2 > n - 1) n - 1 else i + 2]
                val d01 = knot(p0, p1)
                val d12 = knot(p1, p2)
                val d23 = knot(p2, p3)
                val m1x = (p1.x - p0.x) / d01 - (p2.x - p0.x) / (d01 + d12) + (p2.x - p1.x) / d12
                val m1y = (p1.y - p0.y) / d01 - (p2.y - p0.y) / (d01 + d12) + (p2.y - p1.y) / d12
                val m2x = (p2.x - p1.x) / d12 - (p3.x - p1.x) / (d12 + d23) + (p3.x - p2.x) / d23
                val m2y = (p2.y - p1.y) / d12 - (p3.y - p1.y) / (d12 + d23) + (p3.y - p2.y) / d23
                val h = smoothing * d12 / 3f
                OGCubicSegment(
                    control1 = OGPoint(p1.x + h * m1x, p1.y + h * m1y),
                    control2 = OGPoint(p2.x - h * m2x, p2.y - h * m2y),
                    end = p2,
                )
            }
        }

        // One point on a cubic Bézier at parameter u (0..1).
        private fun cubicPoint(p0: OGPoint, c1: OGPoint, c2: OGPoint, p3: OGPoint, u: Float): OGPoint {
            val mu = 1f - u
            val a = mu * mu * mu
            val b = 3f * mu * mu * u
            val c = 3f * mu * u * u
            val d = u * u * u
            return OGPoint(
                a * p0.x + b * c1.x + c * c2.x + d * p3.x,
                a * p0.y + b * c1.y + c * c2.y + d * p3.y,
            )
        }
    }
}

/**
 * The **data** form of an [OGMotionPath]: a list of [points] as flat `x,y` pairs in normalized `0..1`
 * space (so it stays JSON-friendly and model-authorable, the same wire-format philosophy as the rest
 * of the library's specs). [toPath] builds the live path, choosing an open polyline, a smooth open
 * curve, or a closed loop from the flags. Decode/encode via [OGMotions].
 *
 * @param points flat `[x0, y0, x1, y1, ...]` in `0..1`; a trailing odd value is ignored.
 * @param closed link the last point back to the first (a loop).
 * @param smooth round the path (a centripetal Catmull-Rom spline) instead of straight segments.
 * @param smoothing roundness `0..1` applied when [smooth] is set (open or closed).
 * @param samplesPerSegment how finely a smoothed span is sampled (higher = smoother, costs memory once).
 */
@Serializable
data class OGMotionPathSpec(
    val name: String? = null,
    val points: List<Float> = emptyList(),
    val closed: Boolean = false,
    val smooth: Boolean = false,
    val smoothing: Float = 1f,
    val samplesPerSegment: Int = 16,
) {
    /** The vertices as [OGPoint]s (flat pairs unpacked; a dangling final value is dropped). */
    fun toPoints(): List<OGPoint> =
        points.chunked(2).filter { it.size == 2 }.map { OGPoint(it[0], it[1]) }

    /** Build the live [OGMotionPath] described by this spec. */
    fun toPath(): OGMotionPath {
        val pts = toPoints()
        return when {
            closed -> OGMotionPath.loop(pts, if (smooth) smoothing else 0f, samplesPerSegment)
            smooth -> OGMotionPath.smoothOpen(pts, smoothing, samplesPerSegment)
            else -> OGMotionPath.of(pts)
        }
    }
}
