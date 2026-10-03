package com.solidkey.painpoints.style

import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.shape.OGPolygonShape
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OGBoilTest {

    private val square = listOf(OGPoint(0f, 0f), OGPoint(1f, 0f), OGPoint(1f, 1f), OGPoint(0f, 1f))

    @Test
    fun deterministic_sameTimeSameResult() {
        val b = OGBoil(amplitude = 0.05f, boilFps = 8f)
        assertEquals(b.displace(square, 300L), b.displace(square, 300L), "boil must be pure for a given time")
    }

    @Test
    fun amplitude_bounds_eachVertexWithinAmplitude() {
        val amp = 0.05f
        val out = OGBoil(amplitude = amp, boilFps = 8f).displace(square, 137L)
        out.forEachIndexed { i, p ->
            assertTrue(abs(p.x - square[i].x) <= amp + 1e-4f, "x displacement must stay within amplitude")
            assertTrue(abs(p.y - square[i].y) <= amp + 1e-4f, "y displacement must stay within amplitude")
        }
    }

    @Test
    fun zeroAmplitude_returnsInputUnchanged() {
        assertEquals(square, OGBoil(amplitude = 0f).displace(square, 500L))
    }

    @Test
    fun boils_differentBoilFramesDiffer() {
        // 8fps => the jitter re-rolls every 125ms; frame 0 (0ms) and frame 1 (200ms) must differ.
        val b = OGBoil(amplitude = 0.05f, boilFps = 8f, smooth = false)
        assertNotEquals(b.displace(square, 0L), b.displace(square, 200L))
    }

    @Test
    fun preservesPointCount() {
        assertEquals(square.size, OGBoil().displace(square, 10L).size)
    }

    @Test
    fun boiledPolygon_wrapsDisplacedPoints() {
        val b = OGBoil(amplitude = 0.03f, boilFps = 6f)
        val t = 250L
        assertEquals(b.displace(square, t), OGPolygonShape(square).boiled(b, t).points)
    }

    @Test
    fun pixelateFill_fullBoxFillsEveryCell() {
        // The unit square covers the whole grid, so every cell center is inside.
        assertEquals(16, pixelateFill(square, 4).size)
        assertEquals(1, pixelateFill(square, 1).size)
    }

    @Test
    fun pixelateFill_cellCentersOnGrid() {
        val cells = pixelateFill(square, 2) // centers at 0.25 / 0.75 on each axis
        assertEquals(4, cells.size)
        assertTrue(cells.all { it.x == 0.25f || it.x == 0.75f })
        assertTrue(cells.all { it.y == 0.25f || it.y == 0.75f })
    }

    @Test
    fun pixelateFill_degenerateInputsEmpty() {
        assertTrue(pixelateFill(square, 0).isEmpty())
        assertTrue(pixelateFill(listOf(OGPoint(0f, 0f), OGPoint(1f, 1f)), 4).isEmpty()) // < 3 points
    }

    @Test
    fun pixelateFill_smallTriangleFillsFewerThanFullGrid() {
        val tri = listOf(OGPoint(0f, 0f), OGPoint(0.3f, 0f), OGPoint(0f, 0.3f))
        val cells = pixelateFill(tri, 10)
        assertTrue(cells.isNotEmpty())
        assertTrue(cells.size < 100, "a small triangle must not fill the whole 10x10 grid")
    }
}
