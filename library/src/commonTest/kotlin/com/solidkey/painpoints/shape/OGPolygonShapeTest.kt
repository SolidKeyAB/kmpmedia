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
}
