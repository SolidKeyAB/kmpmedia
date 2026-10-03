package com.solidkey.painpoints.cutout

import com.solidkey.painpoints.shape.OGPolygonShape
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OGMaskContourTest {

    /** Build a mask of [w]x[h] with foreground where [fg] returns true. */
    private fun mask(w: Int, h: Int, fg: (x: Int, y: Int) -> Boolean): OGSegmentationMask {
        val arr = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) arr[y * w + x] = if (fg(x, y)) 1f else 0f
        return OGSegmentationMask(w, h, arr)
    }

    private fun bbox(poly: OGPolygonShape): FloatArray {
        var minX = 1f; var minY = 1f; var maxX = 0f; var maxY = 0f
        for (p in poly.points) {
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    @Test
    fun emptyMaskReturnsNull() {
        assertNull(OGMaskContour.maskToPolygon(mask(20, 20) { _, _ -> false }))
    }

    @Test
    fun allPointsAreNormalized() {
        val poly = OGMaskContour.maskToPolygon(mask(20, 20) { x, y -> x in 5..14 && y in 5..14 })
        assertNotNull(poly)
        assertTrue(poly.points.all { it.x in 0f..1f && it.y in 0f..1f }, "a point escaped 0..1")
    }

    @Test
    fun centeredSquareTracesToItsBoundingBox() {
        val poly = OGMaskContour.maskToPolygon(mask(20, 20) { x, y -> x in 5..14 && y in 5..14 })
        assertNotNull(poly)
        // A clean axis-aligned square should collapse to ~4 corners.
        assertTrue(poly.points.size in 3..8, "expected ~4 corners, got ${poly.points.size}")
        val (minX, minY, maxX, maxY) = bbox(poly)
        // normalized by (w-1)=19: 5/19≈0.263, 14/19≈0.737
        assertTrue(minX in 0.20f..0.33f, "minX=$minX"); assertTrue(maxX in 0.67f..0.80f, "maxX=$maxX")
        assertTrue(minY in 0.20f..0.33f, "minY=$minY"); assertTrue(maxY in 0.67f..0.80f, "maxY=$maxY")
    }

    @Test
    fun picksTheLargestBlobAndIgnoresSpeckle() {
        // small 3x3 blob top-left, big 10x10 blob bottom-right — must trace the big one.
        val poly = OGMaskContour.maskToPolygon(
            mask(40, 40) { x, y ->
                (x in 1..3 && y in 1..3) || (x in 25..34 && y in 25..34)
            },
        )
        assertNotNull(poly)
        val (minX, minY, _, _) = bbox(poly)
        // big blob starts at 25/39≈0.64, so the outline must be in the bottom-right, not near 0.
        assertTrue(minX > 0.5f, "traced the wrong (small) blob, minX=$minX")
        assertTrue(minY > 0.5f, "traced the wrong (small) blob, minY=$minY")
    }

    @Test
    fun vertexCountIsBounded() {
        // a ragged circle-ish blob; ensure the cap is honoured
        val cx = 30f; val cy = 30f; val r = 24f
        val poly = OGMaskContour.maskToPolygon(
            mask(60, 60) { x, y ->
                val dx = x - cx; val dy = y - cy
                dx * dx + dy * dy <= r * r
            },
            maxVertices = 24,
        )
        assertNotNull(poly)
        assertTrue(poly.points.size <= 24, "exceeded vertex cap: ${poly.points.size}")
        assertTrue(poly.points.size >= 3)
    }

    @Test
    fun tinyMaskReturnsNull() {
        assertNull(OGMaskContour.maskToPolygon(mask(2, 2) { _, _ -> true }))
    }
}
