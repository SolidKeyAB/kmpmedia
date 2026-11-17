package com.solidkey.painpoints.depth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OGDepthHazeTest {

    private val haze = OGDepthHaze(nearZ = 300f, farZ = 1400f)

    @Test
    fun noHazeBeforeNearZ() {
        assertEquals(0f, haze.hazeAlphaAt(0f))
        assertEquals(0f, haze.hazeAlphaAt(299f))
        assertEquals(0f, haze.hazeAlphaAt(300f))
    }

    @Test
    fun fullHazeAtFarZ() {
        assertEquals(1f, haze.hazeAlphaAt(1400f), 0.001f)
        assertEquals(1f, haze.hazeAlphaAt(2000f), 0.001f)
    }

    @Test
    fun hazeIsLinearBetweenNearAndFar() {
        val mid = haze.hazeAlphaAt(850f)   // midpoint of 300..1400
        assertEquals(0.5f, mid, 0.01f)
    }

    @Test
    fun hazeIsMonotonicallyIncreasing() {
        val zs = listOf(0f, 100f, 300f, 500f, 900f, 1400f, 1600f)
        val alphas = zs.map { haze.hazeAlphaAt(it) }
        for (i in 1 until alphas.size) {
            assertTrue(alphas[i] >= alphas[i - 1], "alpha not increasing at z=${zs[i]}")
        }
    }

    @Test
    fun hazeWithZeroRange_clampsSafely() {
        val flat = OGDepthHaze(nearZ = 500f, farZ = 500f)
        // farZ == nearZ → (z - nearZ) / 0 — should not crash, coerces to 0 or 1
        val alpha = flat.hazeAlphaAt(500f)
        assertTrue(alpha in 0f..1f)
    }

    @Test
    fun screenYForHeightIsCorrectAtCameraLevel() {
        val cam = OGPerspectiveCamera(focalLength = 300f, cameraHeight = 120f)
        // A point at world Y = cameraHeight (same as eye level) projects to horizonY
        val screenY = cam.screenYForHeight(cam.cameraHeight, 500f, 400f)
        assertEquals(400f, screenY, 0.001f)
    }

    @Test
    fun screenYForHeightAboveCameraIsAboveHorizon() {
        val cam = OGPerspectiveCamera(focalLength = 300f, cameraHeight = 120f)
        val y = cam.screenYForHeight(300f, 500f, 400f) // 300 > 120 → above horizon
        assertTrue(y < 400f, "ceiling ($y) should be above horizon (400)")
    }

    @Test
    fun screenYForHeightBelowCameraIsBelowHorizon() {
        val cam = OGPerspectiveCamera(focalLength = 300f, cameraHeight = 120f)
        val y = cam.screenYForHeight(0f, 500f, 400f) // ground → matches screenYAt
        assertEquals(cam.screenYAt(500f, 400f), y, 0.001f)
    }
}
