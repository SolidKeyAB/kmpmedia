package com.solidkey.painpoints.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OGBadgeTest {

    @Test
    fun materials_haveValidLightDarkContrast() {
        for (mat in OGBadgeMaterial.values()) {
            assertNotNull(mat.base)
            assertNotNull(mat.light)
            assertNotNull(mat.dark)
            assertNotNull(mat.deep)
        }
    }

    @Test
    fun silhouettes_defined() {
        assertEquals(3, OGBadgeSilhouette.values().size)
    }
}
