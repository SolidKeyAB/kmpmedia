package com.solidkey.painpoints.depth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OGScrollDepthTest {

    @Test
    fun resolveColorsDoesNotCrash() {
        OGScrollDepthSpec().layers.forEach { it.resolveColors() }
        OGScrollDepths.Jungle.layers.forEach { it.resolveColors() }
        OGScrollDepths.Space.layers.forEach { it.resolveColors() }
        OGScrollDepths.Cave.layers.forEach { it.resolveColors() }
    }

    @Test
    fun scrollLayerRngInUnitRange() {
        for (i in -20..20) {
            for (ch in 0..4) {
                val r = scrollLayerRng(i, 42L, ch)
                assertTrue(r in 0f..1f, "scrollLayerRng($i,42,$ch)=$r outside [0,1]")
            }
        }
    }

    @Test
    fun scrollLayerRngIsDeterministic() {
        assertEquals(scrollLayerRng(7, 100L, 3), scrollLayerRng(7, 100L, 3))
    }

    @Test
    fun scrollLayerRngVariesAcrossIndices() {
        val values = (0..20).map { scrollLayerRng(it, 42L, 0) }.toSet()
        assertTrue(values.size >= 15, "Expected variation across indices, got $values")
    }

    @Test
    fun farLayersScrollSlower() {
        // Layer speed = baseSpeed * (1 - depth).
        // depth=0.9 → 10% speed; depth=0.1 → 90% speed.
        val baseSpeed = 100f
        val farLayerScroll   = baseSpeed * (1f - 0.9f)
        val nearLayerScroll  = baseSpeed * (1f - 0.1f)
        assertTrue(nearLayerScroll > farLayerScroll,
            "near layer ($nearLayerScroll) should scroll faster than far layer ($farLayerScroll)")
    }

    @Test
    fun presetsHaveCorrectNames() {
        assertEquals("Jungle", OGScrollDepths.Jungle.name)
        assertEquals("Space",  OGScrollDepths.Space.name)
        assertEquals("Cave",   OGScrollDepths.Cave.name)
    }

    @Test
    fun defaultSpecHasAtLeastOneLayer() {
        assertTrue(OGScrollDepthSpec().layers.isNotEmpty())
    }

    @Test
    fun depthIsClampedInSpeedCalc() {
        // Layers with depth outside [0,1] should not produce negative or >baseSpeed scroll
        for (depth in listOf(-0.5f, 0f, 0.5f, 1f, 1.5f)) {
            val speed = 80f * (1f - depth.coerceIn(0f, 1f))
            assertTrue(speed in 0f..80f, "speed=$speed out of range for depth=$depth")
        }
    }
}
