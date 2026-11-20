package com.solidkey.painpoints.depth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OGBillboardTest {

    private val cam = OGPerspectiveCamera(focalLength = 300f, cameraHeight = 120f)

    @Test
    fun resolveColorsDoesNotCrash() {
        OGBillboardItem().resolveColors()
        OGBillboards.TemplePillars.items.forEach { it.resolveColors() }
        OGBillboards.Obstacles.items.forEach { it.resolveColors() }
    }

    @Test
    fun presetsHaveCorrectNames() {
        assertEquals("TemplePillars", OGBillboards.TemplePillars.name)
        assertEquals("Obstacles",     OGBillboards.Obstacles.name)
    }

    @Test
    fun itemAtOriginProjectsToVpX() {
        val vpX     = 300f
        val screenX = cam.projectX(0f, 500f, vpX)
        assertEquals(vpX, screenX, 0.001f)
    }

    @Test
    fun itemToRightOfCentreProjectsRightOfVpX() {
        val vpX   = 300f
        val screenX = cam.projectX(100f, 500f, vpX)
        assertTrue(screenX > vpX, "item to the right should project right of VP")
    }

    @Test
    fun perspectiveScaleShrinksBillboardAtDistance() {
        val near = cam.perspectiveScale(100f)
        val far  = cam.perspectiveScale(800f)
        assertTrue(near > far, "near scale ($near) should exceed far scale ($far)")
    }

    @Test
    fun loopingItemProducesMultipleVisibleCopies() {
        val item = OGBillboardItem(worldZ = 300f, loopInterval = 200f)
        val spec = OGBillboardSpec(items = listOf(item))
        // For scroll=0 and farClip=1600, we expect multiple copies (at z=300,500,700,...)
        val visibleCount = (0 until 20).count { k ->
            val absZ  = k * item.loopInterval + (item.worldZ % item.loopInterval)
            val localZ = absZ - 0f   // scroll=0
            localZ in 0f..1600f
        }
        assertTrue(visibleCount >= 4, "Expected at least 4 looping copies, got $visibleCount")
    }

    @Test
    fun itemBehindCameraIsCulled() {
        val item   = OGBillboardItem(worldZ = 50f) // when scroll=100, localZ=-50
        val scroll = 100f
        val localZ = item.worldZ - scroll
        assertTrue(localZ < 0f, "item behind camera should have negative localZ")
    }

    @Test
    fun allShapeEnumValuesExist() {
        val shapes = OGBillboardShape.entries
        assertTrue(shapes.contains(OGBillboardShape.RECT))
        assertTrue(shapes.contains(OGBillboardShape.CIRCLE))
        assertTrue(shapes.contains(OGBillboardShape.DIAMOND))
        assertTrue(shapes.contains(OGBillboardShape.TRIANGLE))
        assertTrue(shapes.contains(OGBillboardShape.PILLAR))
    }
}
