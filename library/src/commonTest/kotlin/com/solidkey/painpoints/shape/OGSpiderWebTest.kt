package com.solidkey.painpoints.shape

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OGSpiderWebTest {

    @Test
    fun deterministic_sameSeedSameWeb() {
        val a = OGSpiderWeb.generate(seed = 42)
        val b = OGSpiderWeb.generate(seed = 42)
        assertEquals(a, b, "same seed must reproduce the same web exactly")
    }

    @Test
    fun differentSeeds_differByDefault() {
        assertTrue(OGSpiderWeb.generate(1) != OGSpiderWeb.generate(2), "different seeds should differ")
    }

    @Test
    fun notEmpty() {
        assertTrue(OGSpiderWeb.generate(seed = 7, spokes = 8, rings = 5).isNotEmpty())
    }

    @Test
    fun fitsUnitCircle() {
        val segs = OGSpiderWeb.generate(seed = 99, spokes = 8, rings = 5)
        for (s in segs) for (p in listOf(s.a, s.b)) {
            val r = sqrt(p.x * p.x + p.y * p.y)
            assertTrue(r <= 1.05f, "point radius $r should stay within the unit circle")
        }
    }

    @Test
    fun moreRings_moreSegments() {
        val few = OGSpiderWeb.generate(seed = 7, spokes = 8, rings = 3).size
        val many = OGSpiderWeb.generate(seed = 7, spokes = 8, rings = 8).size
        assertTrue(many > few, "more rings should produce more segments ($many vs $few)")
    }
}

class OGLeafVeinsTest {

    @Test
    fun deterministic_sameSeedSameLeaf() {
        val a = OGLeafVeins.generate(seed = 42)
        val b = OGLeafVeins.generate(seed = 42)
        assertEquals(a, b, "same seed must reproduce the same leaf exactly")
    }

    @Test
    fun differentSeeds_differ() {
        assertTrue(OGLeafVeins.generate(1) != OGLeafVeins.generate(2), "different seeds should differ")
    }

    @Test
    fun notEmpty() {
        assertTrue(OGLeafVeins.generate(seed = 5, veins = 4).isNotEmpty())
    }

    @Test
    fun deeperDepth_moreSegments() {
        val d0 = OGLeafVeins.generate(seed = 7, veins = 4, depth = 0).size
        val d2 = OGLeafVeins.generate(seed = 7, veins = 4, depth = 2).size
        assertTrue(d2 > d0, "depth 2 should produce more segments than depth 0 ($d2 vs $d0)")
    }
}
