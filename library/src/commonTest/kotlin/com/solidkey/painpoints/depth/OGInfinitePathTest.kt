package com.solidkey.painpoints.depth

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the pure math behind [OGInfinitePathView] — projection math, path centre, and tree RNG.
 * Composable rendering is not testable in commonTest; the math functions are the entire logic layer.
 */
class OGInfinitePathTest {

    private val cam = OGPerspectiveCamera(focalLength = 300f, cameraHeight = 120f)
    private val flat = OGPathDynamics(bendAmplitude = 0f)   // straight road
    private val bent = OGPathDynamics(bendAmplitude = 160f, bendFrequency = 0.0018f)

    // ── OGPerspectiveCamera ───────────────────────────────────────────────────

    @Test
    fun perspectiveScaleIsOneAtOrigin() {
        // At Z = 0.5 (the coercion floor) scale ≈ 1
        assertEquals(1f, cam.perspectiveScale(0f), 0.01f)
    }

    @Test
    fun perspectiveScaleDecreasesWithDepth() {
        val near = cam.perspectiveScale(100f)
        val far  = cam.perspectiveScale(500f)
        assertTrue(near > far, "near ($near) should be larger than far ($far)")
    }

    @Test
    fun groundZInvertsScreenY() {
        val horizonY = 400f
        val screenY  = 480f                         // below horizon
        val z = cam.groundZAt(screenY, horizonY)    // world Z
        val back = cam.screenYAt(z, horizonY)       // round-trip
        assertEquals(screenY, back, 0.1f)
    }

    @Test
    fun groundZIsMaxAboveHorizon() {
        val horizonY = 400f
        assertEquals(Float.MAX_VALUE, cam.groundZAt(horizonY,       horizonY))
        assertEquals(Float.MAX_VALUE, cam.groundZAt(horizonY - 10f, horizonY))
    }

    @Test
    fun projectXIsVpXWhenWorldXIsZero() {
        val vpX = 300f
        assertEquals(vpX, cam.projectX(0f, 100f, vpX), 0.001f)
    }

    @Test
    fun projectXScalesWithPerspective() {
        val vpX   = 300f
        val near  = cam.projectX(100f, 100f,  vpX)
        val far   = cam.projectX(100f, 1000f, vpX)
        // Near object appears farther from VP than far object
        assertTrue(near - vpX > far - vpX)
    }

    // ── pathCenterX / pathHeadingX ────────────────────────────────────────────

    @Test
    fun flatRoadCenterIsAlwaysZero() {
        for (z in listOf(0f, 100f, 500f, 1200f)) {
            assertEquals(0f, pathCenterX(z, flat), 0.001f)
            assertEquals(0f, pathHeadingX(z, flat), 0.001f)
        }
    }

    @Test
    fun bentRoadStaysWithinAmplitude() {
        // The two harmonics (0.6 + 0.4) sum to at most 1.0 × amplitude
        for (z in 0..2000 step 50) {
            val cx = pathCenterX(z.toFloat(), bent)
            assertTrue(abs(cx) <= bent.bendAmplitude + 0.001f,
                "pathCenterX($z) = $cx exceeds amplitude ${bent.bendAmplitude}")
        }
    }

    @Test
    fun headingIsNumericalDerivativeOfCenter() {
        val dz = 0.1f
        val z  = 500f
        val numeric  = (pathCenterX(z + dz, bent) - pathCenterX(z - dz, bent)) / (2 * dz)
        val analytic = pathHeadingX(z, bent)
        assertEquals(numeric, analytic, 0.5f)   // 0.5 world-unit tolerance
    }

    @Test
    fun differentSeedsGiveDifferentPaths() {
        val d1 = OGPathDynamics(bendAmplitude = 160f, seed = 1L)
        val d2 = OGPathDynamics(bendAmplitude = 160f, seed = 999L)
        val different = (0..10).any { z ->
            abs(pathCenterX(z * 100f, d1) - pathCenterX(z * 100f, d2)) > 1f
        }
        assertTrue(different)
    }

    // ── treeRng ───────────────────────────────────────────────────────────────

    @Test
    fun treeRngInUnitRange() {
        for (i in -100..100) {
            val r = treeRng(i, 42L)
            assertTrue(r in 0f..1f, "treeRng($i) = $r outside [0,1]")
        }
    }

    @Test
    fun treeRngIsDeterministic() {
        assertEquals(treeRng(17, 42L), treeRng(17, 42L))
    }

    @Test
    fun treeRngVariesAcrossIndices() {
        val values = (0..20).map { treeRng(it, 42L) }.toSet()
        assertTrue(values.size >= 15, "Expected variation across indices, got $values")
    }
}
