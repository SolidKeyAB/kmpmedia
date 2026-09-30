package com.solidkey.painpoints.mask

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the pure math behind the feathered clip — the inner opaque fraction of the radial falloff
 * that [ogSoftClip] builds. The DstIn draw itself is platform-backed and validated by the demo;
 * pinning this fraction pins how much of the shape stays solid before it ramps to transparent.
 */
class OGSoftMaskTest {

    @Test
    fun noFeatherKeepsAlmostAllSolid() {
        // feather 0 → fully opaque; clamped just below 1 so the gradient stop pair is always valid.
        assertEquals(0.999f, featherInnerFraction(maxRadiusPx = 100f, featherPx = 0f))
    }

    @Test
    fun featherRampsTheOuterBand() {
        // A 20px feather on a 100px radius keeps the inner 80% opaque, ramps over the outer 20%.
        assertEquals(0.8f, featherInnerFraction(maxRadiusPx = 100f, featherPx = 20f), 0.0001f)
    }

    @Test
    fun featherAtOrBeyondRadiusFadesFromCenter() {
        assertEquals(0f, featherInnerFraction(maxRadiusPx = 100f, featherPx = 100f), 0.0001f)
        // Over-large feather is clamped, not negative.
        assertEquals(0f, featherInnerFraction(maxRadiusPx = 100f, featherPx = 150f), 0.0001f)
    }

    @Test
    fun degenerateRadiusIsSafe() {
        assertEquals(0f, featherInnerFraction(maxRadiusPx = 0f, featherPx = 20f))
    }

    @Test
    fun fractionStaysInRange() {
        for (f in listOf(-10f, 0f, 5f, 50f, 99f, 100f, 500f)) {
            val frac = featherInnerFraction(100f, f)
            assertTrue(frac in 0f..0.999f, "fraction $frac out of range for feather $f")
        }
    }
}
