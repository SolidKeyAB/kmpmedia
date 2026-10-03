package com.solidkey.painpoints.gesture

import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.shape.OGPolygonShape
import com.solidkey.painpoints.shape.OGShapeType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The region of a box that counts as "inside the shape" for a gesture — so
 * [Modifier.ogInteractive][ogInteractive] can **ignore touches that land in a clip's transparent
 * corners**. When you clip media to a triangle, circle or lasso, the composable is still a
 * rectangle; without a hit area, a drag started in an empty corner still grabs it. An [OGHitArea]
 * restores the intuition that you grab the *shape*, not its bounding box.
 *
 * It's described entirely in **normalized** `0..1` coordinates (`(0,0)` = top-left of the box,
 * `(1,1)` = bottom-right), so it's independent of the actual pixel size and — crucially — pure data
 * + pure math (even-odd ray cast / analytic tests), which means it's unit-testable with no Compose
 * runtime, exactly like the shape and depth maths.
 *
 * The built-in areas mirror the [OGShapeType] vocabulary ([CIRCLE], [TRIANGLE_UP], [TRIANGLE_DOWN],
 * [DIAMOND], [RECT]); [polygon] matches an [OGPolygonShape] lasso. Pass [of] an [OGShapeType] to get
 * the matching area. Circle/diamond are exact for a square box (the common case for clipped media);
 * in a non-square box they follow the same normalized outline the clip uses.
 */
class OGHitArea private constructor(
    private val kind: Kind,
    private val polygon: List<OGPoint>,
) {
    private enum class Kind { RECT, CIRCLE, POLYGON }

    /**
     * True if the normalized point ([nx], [ny]) — each in `0..1`, measured from the box's top-left —
     * lies inside this area. Points outside the box are always `false`.
     */
    fun containsNormalized(nx: Float, ny: Float): Boolean {
        if (nx < 0f || nx > 1f || ny < 0f || ny > 1f) return false
        return when (kind) {
            Kind.RECT -> true
            Kind.CIRCLE -> {
                val dx = nx - 0.5f
                val dy = ny - 0.5f
                dx * dx + dy * dy <= 0.25f
            }
            Kind.POLYGON -> pointInPolygon(polygon, nx, ny)
        }
    }

    /**
     * A closed, ordered outline of this area in normalized `0..1` space — for **drawing** it (e.g. the
     * hard-offset shadow of a [Modifier.ogButton][ogButton] `Brutalist` press). [CIRCLE] is sampled into
     * [samples] points; [RECT] is the four corners; triangles / diamond / a [polygon] lasso return their
     * exact vertices. Pure data + math, so it's unit-testable with no Compose runtime, like the rest of
     * this class.
     */
    fun outlineNormalized(samples: Int = 48): List<OGPoint> = when (kind) {
        Kind.RECT -> listOf(OGPoint(0f, 0f), OGPoint(1f, 0f), OGPoint(1f, 1f), OGPoint(0f, 1f))
        Kind.CIRCLE -> {
            val n = samples.coerceAtLeast(3)
            (0 until n).map { i ->
                val a = i.toFloat() / n * 2f * PI.toFloat()
                OGPoint(0.5f + 0.5f * cos(a), 0.5f + 0.5f * sin(a))
            }
        }
        Kind.POLYGON -> polygon
    }

    companion object {
        /** The whole box — every touch counts (the default; same as passing no hit area). */
        val RECT: OGHitArea = OGHitArea(Kind.RECT, emptyList())

        /** The inscribed circle (centre `(0.5, 0.5)`, radius `0.5`) — exact for a square box. */
        val CIRCLE: OGHitArea = OGHitArea(Kind.CIRCLE, emptyList())

        /** Upward triangle: apex at top-centre, base along the bottom (matches [OGShapeType.TRIANGLE_UP]). */
        val TRIANGLE_UP: OGHitArea = polygon(
            listOf(OGPoint(0.5f, 0f), OGPoint(1f, 1f), OGPoint(0f, 1f)),
        )

        /** Downward triangle: base along the top, apex at bottom-centre (matches [OGShapeType.TRIANGLE_DOWN]). */
        val TRIANGLE_DOWN: OGHitArea = polygon(
            listOf(OGPoint(0f, 0f), OGPoint(1f, 0f), OGPoint(0.5f, 1f)),
        )

        /** Diamond: the four edge midpoints (matches [OGShapeType.DIAMOND]). */
        val DIAMOND: OGHitArea = polygon(
            listOf(OGPoint(0.5f, 0f), OGPoint(1f, 0.5f), OGPoint(0.5f, 1f), OGPoint(0f, 0.5f)),
        )

        /** A free-form area from an ordered list of normalized `0..1` vertices (a lasso outline). */
        fun polygon(points: List<OGPoint>): OGHitArea = OGHitArea(Kind.POLYGON, points)

        /** The area matching an [OGPolygonShape] lasso (reuses its exact vertices). */
        fun polygon(shape: OGPolygonShape): OGHitArea = OGHitArea(Kind.POLYGON, shape.points)

        /**
         * The hit area matching a built-in [OGShapeType]. Rounded corners ([OGShapeType.SQUARE] /
         * [OGShapeType.RECTANGLE]) are treated as the full [RECT] — the rounding is a small visual
         * nicety, not worth corner-arc hit maths.
         */
        fun of(type: OGShapeType): OGHitArea = when (type) {
            OGShapeType.CIRCLE -> CIRCLE
            OGShapeType.TRIANGLE_UP -> TRIANGLE_UP
            OGShapeType.TRIANGLE_DOWN -> TRIANGLE_DOWN
            OGShapeType.DIAMOND -> DIAMOND
            OGShapeType.SQUARE, OGShapeType.RECTANGLE -> RECT
        }
    }
}

/**
 * Even-odd (ray-cast) point-in-polygon test on a closed polygon of [poly] vertices, for the point
 * ([x], [y]). Works in whatever coordinate space the inputs share (this library uses normalized
 * `0..1`). Fewer than 3 vertices encloses no area → always `false`. Pure + platform-independent so
 * it's directly unit-testable.
 */
internal fun pointInPolygon(poly: List<OGPoint>, x: Float, y: Float): Boolean {
    if (poly.size < 3) return false
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val xi = poly[i].x
        val yi = poly[i].y
        val xj = poly[j].x
        val yj = poly[j].y
        // Does the horizontal ray at height y cross edge (i, j)? The side test guarantees yj != yi.
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
            inside = !inside
        }
        j = i
    }
    return inside
}
