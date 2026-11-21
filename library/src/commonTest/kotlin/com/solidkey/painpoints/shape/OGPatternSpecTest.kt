package com.solidkey.painpoints.shape

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OGPatternSpecTest {

    @Test
    fun roundTrip_allTypes() {
        OGPatternType.entries.forEach { type ->
            val spec = OGPatternSpec(type = type, seed = 42, color = "#AABBCCDD",
                strokeFraction = 0.02f, complexity = 2, count = 6)
            val encoded = OGPatterns.encode(spec)
            val decoded = OGPatterns.decodeSpec(encoded)
            assertEquals(spec, decoded, "round-trip failed for $type")
        }
    }

    @Test
    fun decodeSpecOrNull_invalidJson_returnsNull() {
        val result = OGPatterns.decodeSpecOrNull("not json at all")
        assertEquals(null, result)
    }

    @Test
    fun presetsAreNonNull() {
        assertNotNull(OGPatterns.SnowCrystal)
        assertNotNull(OGPatterns.OrbWeb)
        assertNotNull(OGPatterns.GoldenHive)
        assertNotNull(OGPatterns.TropicalLeaf)
    }

    @Test
    fun presetsHaveDifferentSeeds_snowflakesLookDifferent() {
        val a = OGSnowflake.generate(OGPatterns.SnowCrystal.seed)
        val b = OGSnowflake.generate(OGPatterns.SuperCrystal.seed)
        assertTrue(a != b, "different preset seeds should produce different crystals")
    }
}
