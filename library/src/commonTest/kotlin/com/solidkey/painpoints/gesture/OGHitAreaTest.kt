package com.solidkey.painpoints.gesture

import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.shape.OGPolygonShape
import com.solidkey.painpoints.shape.OGShapeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OGHitAreaTest {

    @Test
    fun rectAcceptsAnyPointInTheBoxAndRejectsOutside() {
        val a = OGHitArea.RECT
        assertTrue(a.containsNormalized(0f, 0f))
        assertTrue(a.containsNormalized(0.5f, 0.5f))
        assertTrue(a.containsNormalized(1f, 1f))
        assertFalse(a.containsNormalized(-0.01f, 0.5f))
        assertFalse(a.containsNormalized(0.5f, 1.01f))
    }

    @Test
    fun circleAcceptsCentreAndRejectsCorners() {
        val a = OGHitArea.CIRCLE
        assertTrue(a.containsNormalized(0.5f, 0.5f))      // centre
        assertTrue(a.containsNormalized(0.5f, 0.02f))     // top edge, just inside
        assertTrue(a.containsNormalized(0.98f, 0.5f))     // right edge, just inside
        assertFalse(a.containsNormalized(0f, 0f))         // top-left corner — outside the inscribed circle
        assertFalse(a.containsNormalized(1f, 1f))         // bottom-right corner
        assertFalse(a.containsNormalized(0.1f, 0.1f))     // deep in the corner (dist² = 0.32 > 0.25)
    }

    @Test
    fun triangleUpRejectsTopCornersAcceptsBase() {
        val a = OGHitArea.TRIANGLE_UP // apex (0.5,0), base along y=1
        assertTrue(a.containsNormalized(0.5f, 0.9f))      // low centre, well inside
        assertTrue(a.containsNormalized(0.1f, 0.98f))     // near bottom-left base
        assertFalse(a.containsNormalized(0.05f, 0.1f))    // top-left corner is empty for an up-triangle
        assertFalse(a.containsNormalized(0.95f, 0.1f))    // top-right corner empty
    }

    @Test
    fun triangleDownRejectsBottomCornersAcceptsTop() {
        val a = OGHitArea.TRIANGLE_DOWN // base along y=0, apex (0.5,1)
        assertTrue(a.containsNormalized(0.5f, 0.1f))      // high centre, inside
        assertTrue(a.containsNormalized(0.1f, 0.02f))     // near top-left base
        assertFalse(a.containsNormalized(0.05f, 0.9f))    // bottom-left corner empty
        assertFalse(a.containsNormalized(0.95f, 0.9f))    // bottom-right corner empty
    }

    @Test
    fun diamondAcceptsCentreRejectsCorners() {
        val a = OGHitArea.DIAMOND // the four edge midpoints
        assertTrue(a.containsNormalized(0.5f, 0.5f))      // centre
        assertTrue(a.containsNormalized(0.5f, 0.1f))      // near top vertex
        assertFalse(a.containsNormalized(0.05f, 0.05f))   // all four corners empty
        assertFalse(a.containsNormalized(0.95f, 0.95f))
    }

    @Test
    fun ofMapsEachShapeTypeToTheMatchingArea() {
        // CIRCLE type rejects a corner; SQUARE/RECTANGLE accept everything in the box.
        assertFalse(OGHitArea.of(OGShapeType.CIRCLE).containsNormalized(0f, 0f))
        assertTrue(OGHitArea.of(OGShapeType.SQUARE).containsNormalized(0f, 0f))
        assertTrue(OGHitArea.of(OGShapeType.RECTANGLE).containsNormalized(0.99f, 0.01f))
        assertFalse(OGHitArea.of(OGShapeType.TRIANGLE_UP).containsNormalized(0.02f, 0.02f))
        assertFalse(OGHitArea.of(OGShapeType.DIAMOND).containsNormalized(0.02f, 0.02f))
    }

    @Test
    fun polygonFromLassoReusesItsVertices() {
        // A small square lasso occupying the top-left quadrant.
        val lasso = OGPolygonShape.of(0.1f to 0.1f, 0.4f to 0.1f, 0.4f to 0.4f, 0.1f to 0.4f)
        val a = OGHitArea.polygon(lasso)
        assertTrue(a.containsNormalized(0.25f, 0.25f))    // inside the lasso square
        assertFalse(a.containsNormalized(0.7f, 0.7f))     // outside it (but inside the box)
    }

    @Test
    fun pointInPolygonNeedsAtLeastThreeVertices() {
        assertFalse(pointInPolygon(listOf(OGPoint(0f, 0f), OGPoint(1f, 1f)), 0.5f, 0.5f))
        assertFalse(pointInPolygon(emptyList(), 0.5f, 0.5f))
    }

    @Test
    fun pointInPolygonHandlesAConvexQuad() {
        val square = listOf(OGPoint(0f, 0f), OGPoint(1f, 0f), OGPoint(1f, 1f), OGPoint(0f, 1f))
        assertTrue(pointInPolygon(square, 0.5f, 0.5f))
        assertFalse(pointInPolygon(square, 1.5f, 0.5f))
    }

    @Test
    fun equalByValueSoAnInlineHitAreaIsAStablePointerInputKey() {
        // Two areas built from the SAME data must be equal (and share a hashCode) — otherwise a fresh
        // `OGHitArea.polygon(shape)` on every recomposition would look like a new pointerInput key and
        // restart (cancel) an in-flight gesture. A different outline must NOT be equal.
        val shape = OGPolygonShape.of(0.1f to 0.1f, 0.4f to 0.1f, 0.4f to 0.4f, 0.1f to 0.4f)
        assertEquals(OGHitArea.polygon(shape), OGHitArea.polygon(shape))
        assertEquals(OGHitArea.polygon(shape).hashCode(), OGHitArea.polygon(shape).hashCode())
        assertEquals(OGHitArea.RECT, OGHitArea.RECT)
        assertNotEquals(OGHitArea.RECT, OGHitArea.CIRCLE)
        assertNotEquals(
            OGHitArea.polygon(shape),
            OGHitArea.polygon(OGPolygonShape.of(0.5f to 0.5f, 0.9f to 0.5f, 0.9f to 0.9f, 0.5f to 0.9f)),
        )
    }
}
