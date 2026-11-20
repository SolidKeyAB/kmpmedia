package com.solidkey.painpoints.depth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OGAtmosphereTest {

    @Test
    fun resolveColorsDoesNotCrash() {
        OGAtmosphereSpec().resolveColors()
        OGAtmospheres.Dawn.resolveColors()
        OGAtmospheres.Night.resolveColors()
        OGAtmospheres.Alien.resolveColors()
        OGAtmospheres.Overcast.resolveColors()
    }

    @Test
    fun atmosphereRngInUnitRange() {
        for (i in -20..20) {
            for (ch in 0..3) {
                val r = atmosphereRng(i, 42L, ch)
                assertTrue(r in 0f..1f, "atmosphereRng($i,42,$ch)=$r outside [0,1]")
            }
        }
    }

    @Test
    fun atmosphereRngIsDeterministic() {
        assertEquals(atmosphereRng(5, 99L, 2), atmosphereRng(5, 99L, 2))
    }

    @Test
    fun atmosphereRngVariesAcrossIndices() {
        val values = (0..15).map { atmosphereRng(it, 42L, 0) }.toSet()
        assertTrue(values.size >= 10, "Expected variation across indices")
    }

    @Test
    fun atmosphereRngVariesAcrossChannels() {
        val values = (0..3).map { ch -> atmosphereRng(0, 42L, ch) }.toSet()
        assertTrue(values.size >= 2, "Expected variation across channels")
    }

    @Test
    fun presetsHaveExpectedNames() {
        assertEquals("Dawn",     OGAtmospheres.Dawn.name)
        assertEquals("Night",    OGAtmospheres.Night.name)
        assertEquals("Alien",    OGAtmospheres.Alien.name)
        assertEquals("Overcast", OGAtmospheres.Overcast.name)
    }

    @Test
    fun nightPresetHasSunDisabled() {
        assertTrue(!OGAtmospheres.Night.sunEnabled)
    }
}
