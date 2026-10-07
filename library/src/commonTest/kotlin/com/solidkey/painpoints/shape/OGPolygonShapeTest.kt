package com.solidkey.painpoints.shape

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the pure derivation behind [OGPolygonShape] — [scalePolygonPoints], which maps the
 * normalized (0..1) lasso vertices onto a concrete box and clamps out-of-range input.
 * [OGPolygonShape.createOutline] is a thin Path wrapper over exactly this, so pinning the math
 * here pins the clip behaviour (correct scaling, clamping, and graceful degenerate handling).
 */
class OGPolygonShapeTest {

    private val triangle = listOf(OGPoint(0.5f, 0f), OGPoint(1f, 1f), OGPoint(0f, 1f))

    @Test
    fun scalesNormalizedPointsToBox() {
        val scaled = scalePolygonPoints(triangle, width = 200f, height = 100f)
        assertEquals(3, scaled.size)
        assertEquals(OGPoint(100f, 0f), scaled[0])   // 0.5*200, 0*100
        assertEquals(OGPoint(200f, 100f), scaled[1]) // 1*200, 1*100
        assertEquals(OGPoint(0f, 100f), scaled[2])   // 0*200, 1*100
    }

    @Test
    fun clampsOutOfRangeCoordinatesIntoBox() {
        val pts = listOf(OGPoint(-0.5f, 2f), OGPoint(1.5f, -1f), OGPoint(0.5f, 0.5f))
        val scaled = scalePolygonPoints(pts, width = 100f, height = 100f)
        // -0.5 -> 0, 2 -> 1 (i.e. 100); 1.5 -> 1 (100), -1 -> 0; midpoint stays put.
        assertEquals(OGPoint(0f, 100f), scaled[0])
        assertEquals(OGPoint(100f, 0f), scaled[1])
        assertEquals(OGPoint(50f, 50f), scaled[2])
        // Nothing escapes the box.
        assertTrue(scaled.all { it.x in 0f..100f && it.y in 0f..100f })
    }

    @Test
    fun degeneratePolygonYieldsEmpty() {
        assertTrue(scalePolygonPoints(emptyList(), 100f, 100f).isEmpty())
        assertTrue(scalePolygonPoints(listOf(OGPoint(0f, 0f)), 100f, 100f).isEmpty())
        assertTrue(scalePolygonPoints(listOf(OGPoint(0f, 0f), OGPoint(1f, 1f)), 100f, 100f).isEmpty())
    }

    @Test
    fun ofBuilderMatchesPointList() {
        val a = OGPolygonShape.of(0.5f to 0f, 1f to 1f, 0f to 1f)
        assertEquals(triangle, a.points)
    }

    @Test
    fun preservesVertexOrder() {
        // Order matters for line-segment shapes (winding) — scaling must not reorder.
        val scaled = scalePolygonPoints(triangle, 10f, 10f)
        assertEquals(5f, scaled[0].x, 0.0001f)  // first stays first (apex)
        assertEquals(0f, scaled[2].x, 0.0001f)  // last stays last (bottom-left)
    }

    // ---- robustness: non-finite coordinates --------------------------------------------------

    @Test
    fun nonFiniteCoordinatesCollapseToZero() {
        val pts = listOf(
            OGPoint(Float.NaN, 0.5f),
            OGPoint(0.5f, Float.POSITIVE_INFINITY),
            OGPoint(Float.NEGATIVE_INFINITY, 0.5f),
        )
        val scaled = scalePolygonPoints(pts, 100f, 100f)
        // NaN / ±∞ -> 0; a finite 0.5 -> 50. Nothing is NaN or escapes the box.
        assertEquals(OGPoint(0f, 50f), scaled[0])
        assertEquals(OGPoint(50f, 0f), scaled[1])
        assertEquals(OGPoint(0f, 50f), scaled[2])
        assertTrue(scaled.all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..100f && it.y in 0f..100f })
    }

    // ---- smoothing: closed Catmull-Rom cubic spans -------------------------------------------

    private val square = listOf(OGPoint(0f, 0f), OGPoint(1f, 0f), OGPoint(1f, 1f), OGPoint(0f, 1f))

    @Test
    fun catmullRomGivesOneCubicSpanPerEdgeEndingOnEachNextVertex() {
        val segs = catmullRomClosedCubics(square, smoothing = 1f)
        assertEquals(4, segs.size)
        // The curve is interpolating: span i ends exactly on the next vertex (passes through all of them).
        for (i in square.indices) assertEquals(square[(i + 1) % square.size], segs[i].end)
    }

    @Test
    fun zeroSmoothingCollapsesControlsToTheSegmentEndpoints() {
        // smoothing = 0 => a cubic whose controls sit on its endpoints == a straight edge (unchanged look).
        val segs = catmullRomClosedCubics(square, smoothing = 0f)
        for (i in square.indices) {
            assertEquals(square[i], segs[i].control1)
            assertEquals(square[(i + 1) % square.size], segs[i].control2)
        }
    }

    @Test
    fun catmullRomControlsBulgeSymmetricallyOnAUnitSquare() {
        val s0 = catmullRomClosedCubics(square, smoothing = 1f)[0] // edge (0,0)->(1,0)
        // Uniform Catmull-Rom (k = 1/6) lifts both controls off the top edge by 1/6, symmetrically.
        assertEquals(1f / 6f, s0.control1.x, 1e-4f)
        assertEquals(-1f / 6f, s0.control1.y, 1e-4f)
        assertEquals(5f / 6f, s0.control2.x, 1e-4f)
        assertEquals(-1f / 6f, s0.control2.y, 1e-4f)
    }

    @Test
    fun fewerThanThreePointsHasNoSmoothSpans() {
        assertTrue(catmullRomClosedCubics(listOf(OGPoint(0f, 0f), OGPoint(1f, 1f)), 1f).isEmpty())
    }

    @Test
    fun smoothingParticipatesInValueEquality() {
        val straight = OGPolygonShape(square)
        val smooth = OGPolygonShape(square, smoothing = 0.8f)
        assertTrue(straight != smooth)
        assertEquals(OGPolygonShape(square, 0.8f), smooth)
        assertEquals(OGPolygonShape(square, 0.8f).hashCode(), smooth.hashCode())
    }

    @Test
    fun centripetalSmoothingToleratesDuplicateAndUnevenVertices() {
        // A zero-length edge (duplicate point) + wildly uneven spacing is exactly what breaks uniform
        // Catmull-Rom (divide-by-zero / overshoot). Centripetal must stay finite and interpolating.
        val messy = listOf(
            OGPoint(0f, 0f),
            OGPoint(0f, 0f),    // duplicate -> zero-length edge
            OGPoint(1f, 0.02f), // a tiny edge followed by a long one
            OGPoint(0.5f, 1f),
        )
        val segs = catmullRomClosedCubics(messy, smoothing = 1f)
        assertEquals(messy.size, segs.size)
        for (i in messy.indices) {
            assertEquals(messy[(i + 1) % messy.size], segs[i].end) // still passes through every vertex
            assertTrue(segs[i].control1.x.isFinite() && segs[i].control1.y.isFinite())
            assertTrue(segs[i].control2.x.isFinite() && segs[i].control2.y.isFinite())
        }
    }
}
