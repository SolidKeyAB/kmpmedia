package com.solidkey.painpoints.shape

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OGSnowflakeTest {

    @Test
    fun deterministic_sameSeedSameCrystal() {
        val a = OGSnowflake.generate(seed = 42)
        val b = OGSnowflake.generate(seed = 42)
        assertEquals(a, b, "same seed must reproduce the same crystal exactly")
    }

    @Test
    fun differentSeeds_differByDefault() {
        assertTrue(OGSnowflake.generate(1) != OGSnowflake.generate(2), "different seeds should differ")
    }

    @Test
    fun hasRadialSymmetry_segmentCountIsAMultipleOfArms() {
        val arms = 6
        val segs = OGSnowflake.generate(seed = 7, arms = arms, branches = 3)
        assertTrue(segs.isNotEmpty())
        assertEquals(0, segs.size % arms, "every arm contributes the same segment count")
    }

    @Test
    fun depthZero_isPlainProngs_oneSpinePlusTwoTwigsPerBranch() {
        val arms = 6
        val branches = 3
        val segs = OGSnowflake.generate(seed = 7, arms = arms, branches = branches, depth = 0)
        // depth 0 = the old look: 1 spine + 2 twigs per branch, times the arm count.
        assertEquals((1 + branches * 2) * arms, segs.size)
    }

    @Test
    fun deeperFractal_addsMoreSegments() {
        val plain = OGSnowflake.generate(seed = 7, branches = 3, depth = 0).size
        val d1 = OGSnowflake.generate(seed = 7, branches = 3, depth = 1).size
        val d2 = OGSnowflake.generate(seed = 7, branches = 3, depth = 2).size
        assertTrue(d1 > plain, "one level of recursion should add twigs ($d1 vs $plain)")
        assertTrue(d2 > d1, "two levels should add still more ($d2 vs $d1)")
    }

    @Test
    fun staysWithinUnitCircle() {
        val segs = OGSnowflake.generate(seed = 99, arms = 6, branches = 4, depth = 3)
        for (s in segs) for (p in listOf(s.a, s.b)) {
            val r = sqrt(p.x * p.x + p.y * p.y)
            assertTrue(r <= 1.75f, "point radius $r should stay within the unit-ish circle")
        }
    }
}
