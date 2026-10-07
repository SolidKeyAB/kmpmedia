package com.solidkey.painpoints.fx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the pure falloff math behind [Modifier.ogBloom] ([bloomPasses]). The GraphicsLayer/BlurEffect
 * compositing itself is platform (RenderEffect / Skia) and verified on-device, not here.
 */
class OGBloomTest {

    @Test
    fun zeroRadius_producesNoPasses() {
        assertTrue(bloomPasses(0f, 0.8f).isEmpty())
        assertTrue(bloomPasses(-5f, 0.8f).isEmpty())
    }

    @Test
    fun zeroIntensity_producesNoPasses() {
        assertTrue(bloomPasses(12f, 0f).isEmpty())
        assertTrue(bloomPasses(12f, -0.3f).isEmpty())
    }

    @Test
    fun normal_producesTwoPasses_wideSoftThenTightBright() {
        val passes = bloomPasses(20f, 0.8f)
        assertEquals(2, passes.size)
        val (wide, tight) = passes
        // Wide halo reaches farther than the core.
        assertTrue(wide.first > tight.first)
        // Core is brighter than the halo.
        assertTrue(tight.second > wide.second)
        // Concrete falloff: wide = full radius at 0.6·intensity, core = 0.45·radius at full intensity.
        assertEquals(20f, wide.first, 1e-4f)
        assertEquals(0.8f * 0.6f, wide.second, 1e-4f)
        assertEquals(20f * 0.45f, tight.first, 1e-4f)
        assertEquals(0.8f, tight.second, 1e-4f)
    }

    @Test
    fun intensity_isClampedToUnit() {
        val passes = bloomPasses(10f, 5f)
        assertEquals(2, passes.size)
        // Every alpha stays within [0, 1] even with an over-driven intensity.
        assertTrue(passes.all { it.second in 0f..1f })
        assertEquals(1f, passes[1].second, 1e-4f)
    }
}
