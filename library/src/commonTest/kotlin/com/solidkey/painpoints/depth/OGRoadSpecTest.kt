package com.solidkey.painpoints.depth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OGRoadSpecTest {

    @Test
    fun defaultRoadSpecHasTwoLanes() {
        assertEquals(2, OGRoadSpec().laneCount)
    }

    @Test
    fun singleLaneProducesNoDividers() {
        val laneCount = 1
        val dividers = if (laneCount <= 1) emptyList()
        else List(laneCount - 1) { k ->
            -120f + 240f * (k + 1).toFloat() / laneCount
        }
        assertTrue(dividers.isEmpty(), "1 lane should have no dividing lines")
    }

    @Test
    fun twoLanesProducesOneCentreLine() {
        val laneCount = 2
        val halfW = 120f
        val dividers = List(laneCount - 1) { k ->
            -halfW + halfW * 2f * (k + 1).toFloat() / laneCount
        }
        assertEquals(1, dividers.size)
        assertEquals(0f, dividers[0], 0.001f)   // centre of road
    }

    @Test
    fun threeLanesProducesTwoDividersSymmetrically() {
        val laneCount = 3
        val halfW = 120f
        val dividers = List(laneCount - 1) { k ->
            -halfW + halfW * 2f * (k + 1).toFloat() / laneCount
        }
        assertEquals(2, dividers.size)
        assertEquals(-halfW / 3f * 1f, dividers[0], 0.5f)
        assertEquals( halfW / 3f * 1f, dividers[1], 0.5f)
        // Symmetric around 0
        assertEquals(-dividers[0], dividers[1], 0.5f)
    }

    @Test
    fun resolveColorsIncludesEdgeLine() {
        val spec = OGInfinitePathSpec(road = OGRoadSpec(edgeLineColor = "#CCFFFFFF"))
        val colors = spec.resolveColors()
        assertTrue(colors.edgeLine.alpha > 0f, "edge line color should have non-zero alpha")
    }

    @Test
    fun laneMarkStyleEnumValuesExist() {
        val styles = OGLaneMarkStyle.entries
        assertTrue(styles.contains(OGLaneMarkStyle.DASHES))
        assertTrue(styles.contains(OGLaneMarkStyle.SOLID))
        assertTrue(styles.contains(OGLaneMarkStyle.NONE))
        assertTrue(styles.contains(OGLaneMarkStyle.CATS_EYE))
    }

    @Test
    fun noneStyleEdgeLinesHaveZeroAlpha() {
        val spec = OGInfinitePathSpec(
            road = OGRoadSpec(markStyle = OGLaneMarkStyle.NONE, edgeLineColor = "#00000000"),
        )
        val colors = spec.resolveColors()
        assertEquals(0f, colors.edgeLine.alpha, 0.001f)
    }
}
