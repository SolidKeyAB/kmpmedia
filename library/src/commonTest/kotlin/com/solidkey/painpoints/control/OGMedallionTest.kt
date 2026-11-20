package com.solidkey.painpoints.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OGMedallionTest {

    @Test
    fun medallionShapes_allValuesExist() {
        val shapes = OGMedallionShape.values()
        assertEquals(6, shapes.size)
        assertTrue(shapes.contains(OGMedallionShape.PLAY_TRIANGLE))
        assertTrue(shapes.contains(OGMedallionShape.SCALLOP_STAR))
        assertTrue(shapes.contains(OGMedallionShape.RETURN_COIN))
        assertTrue(shapes.contains(OGMedallionShape.OVERLAPPING_TILES))
        assertTrue(shapes.contains(OGMedallionShape.CHEVRON_PLAQUE))
        assertTrue(shapes.contains(OGMedallionShape.MAGNIFYING_GLASS))
    }
}
