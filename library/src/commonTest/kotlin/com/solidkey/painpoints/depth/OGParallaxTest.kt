package com.solidkey.painpoints.depth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OGParallaxTest {

    private val maxPx = 100f

    @Test
    fun focalPlaneDoesNotMove() {
        assertEquals(0f, parallaxShift(depth = 0.5f, focalDepth = 0.5f, viewpoint = 1f, maxShiftPx = maxPx), 1e-4f)
        assertEquals(0f, parallaxShift(depth = 0.5f, focalDepth = 0.5f, viewpoint = -1f, maxShiftPx = maxPx), 1e-4f)
    }

    @Test
    fun nearerLayersMoveWithViewpointFartherAgainst() {
        // focal at 0.5: depth 0.9 (nearer) moves same sign as viewpoint; depth 0.1 (farther) opposite.
        val near = parallaxShift(0.9f, 0.5f, viewpoint = 1f, maxShiftPx = maxPx)
        val far = parallaxShift(0.1f, 0.5f, viewpoint = 1f, maxShiftPx = maxPx)
        assertTrue(near > 0f, "near=$near")
        assertTrue(far < 0f, "far=$far")
    }

    @Test
    fun magnitudeScalesWithDepthDistanceAndViewpoint() {
        // default focal 0: shift = depth * viewpoint * maxPx
        assertEquals(50f, parallaxShift(0.5f, 0f, 1f, maxPx), 1e-3f)
        assertEquals(25f, parallaxShift(0.5f, 0f, 0.5f, maxPx), 1e-3f)
        assertEquals(100f, parallaxShift(1f, 0f, 1f, maxPx), 1e-3f)
    }

    @Test
    fun viewpointIsClamped() {
        assertEquals(parallaxShift(1f, 0f, 1f, maxPx), parallaxShift(1f, 0f, 5f, maxPx), 1e-4f)
        assertEquals(parallaxShift(1f, 0f, -1f, maxPx), parallaxShift(1f, 0f, -9f, maxPx), 1e-4f)
    }

    @Test
    fun depthIsClamped() {
        // out-of-range depth clamps to 1, so behaves like depth==1
        assertEquals(parallaxShift(1f, 0f, 1f, maxPx), parallaxShift(3f, 0f, 1f, maxPx), 1e-4f)
    }
}
