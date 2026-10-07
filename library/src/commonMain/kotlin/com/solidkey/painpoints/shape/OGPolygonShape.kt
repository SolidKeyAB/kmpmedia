package com.solidkey.painpoints.shape

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.sqrt

/**
 * A single vertex in **normalized** coordinates: `(0,0)` = top-left of the target box,
 * `(1,1)` = bottom-right. Values outside `0..1` are clamped when the polygon is rasterized,
 * so a segmentation model / detector or a finger-drawn outline can hand over raw coordinates
 * without worrying about the exact box size.
 */
data class OGPoint(val x: Float, val y: Float)

/**
 * A free-form clip shape built from an ordered list of [points] joined by straight **line
 * segments** — a polygon "lasso". This is the AI-friendly primitive: the outline is just data
 * (a `List<OGPoint>` in `0..1` space), so the vertices an on-image drawing OR a segmentation
 * model produces drop straight in with no external image editor. The polygon is always closed
 * (the last point links back to the first).
 *
 * By default the vertices are joined by straight **line segments**. Set [smoothing] above `0f` to
 * round the outline with a closed **centripetal Catmull-Rom** spline (drawn as cubic Béziers) that
 * still passes through every vertex — so a vision model's faceted 8–40-point silhouette comes out
 * smooth, and the centripetal spacing keeps it free of the overshoot loops uniform smoothing makes
 * on unevenly spaced points. `0f` = straight (unchanged), `1f` = full roundness; the wire format
 * stays "just points".
 *
 * Clipping is the same `Modifier.clip(shape)` GPU mask the built-in [OGShapeType]s use, so for a
 * still image it is drawn once = no runtime cost. Pass it to
 * [com.solidkey.painpoints.image.OGImageView]'s `clipShape` (or to any `Modifier.clip`) to keep
 * only the region inside the outline — e.g. lasso a head out of a photo, no external tool.
 *
 * Fewer than 3 points is degenerate (no enclosed area) and yields an empty outline (nothing
 * shown) rather than throwing.
 */
class OGPolygonShape(val points: List<OGPoint>, val smoothing: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val abs = scalePolygonPoints(points, size.width, size.height)
        val path = Path().apply {
            if (abs.size >= 3) {
                moveTo(abs[0].x, abs[0].y)
                if (smoothing <= 0f) {
                    for (i in 1 until abs.size) lineTo(abs[i].x, abs[i].y)
                } else {
                    for (seg in catmullRomClosedCubics(abs, smoothing)) {
                        cubicTo(seg.control1.x, seg.control1.y, seg.control2.x, seg.control2.y, seg.end.x, seg.end.y)
                    }
                }
                close()
            }
        }
        return Outline.Generic(path)
    }

    // Value equality (by points + smoothing) so a re-created lasso is a cache/skip hit (e.g. OGMorphShape endpoints).
    override fun equals(other: Any?): Boolean =
        other is OGPolygonShape && other.points == points && other.smoothing == smoothing

    override fun hashCode(): Int = 31 * points.hashCode() + smoothing.hashCode()

    companion object {
        /** Convenience builder from raw `(x, y)` pairs in normalized `0..1` space. */
        fun of(vararg points: Pair<Float, Float>): OGPolygonShape =
            OGPolygonShape(points.map { OGPoint(it.first, it.second) })
    }
}

/**
 * Maps normalized [points] (`0..1`) onto a [width]×[height] box, clamping every coordinate into
 * range — and treating a non-finite `NaN`/`∞` coordinate as `0` — so out-of-bounds or malformed
 * input (e.g. a shaky model reply) can't escape the box or poison the path. Returns an empty list
 * for a degenerate polygon (fewer than 3 points). Pure + platform-independent so it can be
 * unit-tested directly; [OGPolygonShape.createOutline] is a thin wrapper over it.
 */
fun scalePolygonPoints(points: List<OGPoint>, width: Float, height: Float): List<OGPoint> {
    if (points.size < 3) return emptyList()
    return points.map { p -> OGPoint(finiteUnit(p.x) * width, finiteUnit(p.y) * height) }
}

/** Clamp to `0..1`, mapping a non-finite value (`NaN`/`∞`) to `0` — the render-safe form of a coordinate. */
private fun finiteUnit(v: Float): Float = if (v.isFinite()) v.coerceIn(0f, 1f) else 0f

/** One cubic Bézier span of a smoothed polygon edge: two control points and the end vertex. */
data class OGCubicSegment(val control1: OGPoint, val control2: OGPoint, val end: OGPoint)

/**
 * Closed, interpolating **centripetal Catmull-Rom** spline through [points] (needs ≥3), returned as
 * one cubic Bézier span per edge — span `i` runs from `points[i]` to `points[(i + 1) % n]`.
 * [smoothing] `0..1` scales the control handles: `0` = straight edges, `1` = full roundness. The
 * curve passes through every input vertex (only the path between them is rounded).
 *
 * Knots are spaced by the **square root of each edge length** (centripetal, α = 0.5) rather than
 * uniformly, so an unevenly spaced outline — exactly what a vision model emits, with a long edge
 * next to a short one — can't form the cusps or self-intersecting loops uniform Catmull-Rom
 * produces there. On an evenly spaced polygon it is identical to the classic uniform `1/6` spline.
 * Zero-length edges (duplicate points) are tolerated (the knot spacing is floored off zero). Pure
 * and affine-covariant, so it gives the same shape on normalized or absolute points and is directly
 * unit-testable.
 */
fun catmullRomClosedCubics(points: List<OGPoint>, smoothing: Float): List<OGCubicSegment> {
    val n = points.size
    if (n < 3) return emptyList()
    val s = smoothing.coerceIn(0f, 1f)
    // Centripetal knot spacing: √(edge length), floored so a zero-length (duplicate-point) edge can't divide by zero.
    fun knot(a: OGPoint, b: OGPoint): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        return maxOf(sqrt(sqrt(dx * dx + dy * dy)), 1e-4f)
    }
    return List(n) { i ->
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]
        val d01 = knot(p0, p1)
        val d12 = knot(p1, p2)
        val d23 = knot(p2, p3)
        // Non-uniform Catmull-Rom tangents at p1 / p2 (w.r.t. the centripetal knot parameter).
        val m1x = (p1.x - p0.x) / d01 - (p2.x - p0.x) / (d01 + d12) + (p2.x - p1.x) / d12
        val m1y = (p1.y - p0.y) / d01 - (p2.y - p0.y) / (d01 + d12) + (p2.y - p1.y) / d12
        val m2x = (p2.x - p1.x) / d12 - (p3.x - p1.x) / (d12 + d23) + (p3.x - p2.x) / d23
        val m2y = (p2.y - p1.y) / d12 - (p3.y - p1.y) / (d12 + d23) + (p3.y - p2.y) / d23
        // Hermite(p1, p2, m1, m2) over the span's parameter length d12 → Bézier handles; s scales them (0 = straight).
        val h = s * d12 / 3f
        OGCubicSegment(
            control1 = OGPoint(p1.x + h * m1x, p1.y + h * m1y),
            control2 = OGPoint(p2.x - h * m2x, p2.y - h * m2y),
            end = p2,
        )
    }
}
