package com.solidkey.painpoints.depth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OGCorridorTest {

    private val cam = OGPerspectiveCamera(focalLength = 300f, cameraHeight = 120f, horizonFraction = 0.5f)
    private val spec = OGCorridorSpec(camera = cam, halfWidth = 240f, height = 320f)

    @Test
    fun resolveColorsDoesNotCrash() {
        OGCorridorSpec().resolveColors()
        OGCorridors.Temple.resolveColors()
        OGCorridors.Cave.resolveColors()
        OGCorridors.SciFi.resolveColors()
    }

    @Test
    fun presetsHaveCorrectNames() {
        assertEquals("Temple", OGCorridors.Temple.name)
        assertEquals("Cave",   OGCorridors.Cave.name)
        assertEquals("SciFi",  OGCorridors.SciFi.name)
    }

    @Test
    fun ceilingIsAboveHorizonForTallCorridor() {
        // height=320 > cameraHeight=120 → ceiling should appear above horizonY
        val horizonY = 400f
        val ceilingY = cam.screenYForHeight(spec.height, 500f, horizonY)
        assertTrue(ceilingY < horizonY, "ceiling ($ceilingY) should be above horizon ($horizonY)")
    }

    @Test
    fun groundIsBelowHorizon() {
        val horizonY = 400f
        val groundY = cam.screenYAt(500f, horizonY)
        assertTrue(groundY > horizonY, "ground ($groundY) should be below horizon ($horizonY)")
    }

    @Test
    fun wallEdgesSymmetricAroundPathCentre() {
        // Straight path (amplitude=0): left and right edges are symmetric around vpX
        val flat  = OGPathDynamics(bendAmplitude = 0f)
        val scale = cam.perspectiveScale(500f)
        val cx    = pathCenterX(500f, flat) * scale  // = 0 for flat path
        val left  = cx - spec.halfWidth * scale
        val right = cx + spec.halfWidth * scale
        assertEquals(-right, left, 0.001f)
    }

    @Test
    fun archIntervalDefaultIsPositive() {
        assertTrue(OGCorridorSpec().archInterval > 0f)
    }

    @Test
    fun corridorHeightExceedsCameraHeight() {
        // Corridor must be taller than camera or ceiling would be below camera
        val s = OGCorridorSpec()
        assertTrue(s.height > s.camera.cameraHeight,
            "corridorHeight (${s.height}) must exceed cameraHeight (${s.camera.cameraHeight})")
    }

    @Test
    fun hazePresetValuesAreSane() {
        val h = OGCorridorSpec().haze
        assertTrue(h.nearZ < h.farZ, "nearZ must be < farZ")
        assertTrue(h.farZ > 0f)
    }
}
