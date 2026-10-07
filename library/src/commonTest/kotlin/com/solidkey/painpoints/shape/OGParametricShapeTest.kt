package com.solidkey.painpoints.shape

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Covers the pure parametric generators in [OGParametricShape.kt], the spec routing and the codec. */
class OGParametricShapeTest {

    private fun dist(p: OGPoint): Float {
        val dx = p.x - 0.5f; val dy = p.y - 0.5f
        return sqrt(dx * dx + dy * dy)
    }

    private fun List<OGPoint>.allFinite() = all { it.x.isFinite() && it.y.isFinite() }
    private fun List<OGPoint>.allInBox() = all { it.x in -1e-3f..1.001f && it.y in -1e-3f..1.001f }

    // ── regular polygon ─────────────────────────────────────────────────────────────────────────

    @Test
    fun polygon_hasSidesVerticesOnTheUnitRadius_pointingUp() {
        val hex = OGParametric.regularPolygon(6)
        assertEquals(6, hex.size)
        assertTrue(hex.allInBox() && hex.allFinite())
        hex.forEach { assertTrue(abs(dist(it) - 0.5f) < 1e-3f) } // all on radius 0.5
        assertTrue(hex[0].y < 0.5f)                               // first vertex points up
    }

    @Test
    fun polygon_floorsAtThree() {
        assertEquals(3, OGParametric.regularPolygon(1).size)
    }

    // ── star ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun star_alternatesOuterAndInnerRadii() {
        val star = OGParametric.star(points = 5, innerRatio = 0.4f)
        assertEquals(10, star.size)
        assertTrue(star.allInBox())
        // Even indices are tips (radius 0.5), odd are valleys (radius 0.2).
        star.forEachIndexed { i, p ->
            val expected = if (i % 2 == 0) 0.5f else 0.2f
            assertTrue(abs(dist(p) - expected) < 1e-3f, "vertex $i radius ${dist(p)} != $expected")
        }
    }

    // ── gear ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun gear_hasFourVerticesPerTooth_betweenInnerAndOuter() {
        val teeth = 8
        val gear = OGParametric.gear(teeth, depth = 0.4f)
        assertEquals(teeth * 4, gear.size)
        assertTrue(gear.allInBox())
        gear.forEach { assertTrue(dist(it) <= 0.5f + 1e-3f && dist(it) >= 0.3f - 1e-3f) }
    }

    // ── flower / superellipse / blob ──────────────────────────────────────────────────────────

    @Test
    fun flower_staysInsideTheUnitDisk() {
        val f = OGParametric.flower(petals = 6, depth = 0.5f, samples = 72)
        assertEquals(72, f.size)
        assertTrue(f.allInBox() && f.allFinite())
        f.forEach { assertTrue(dist(it) <= 0.5f + 1e-3f) }
    }

    @Test
    fun superellipse_exponentTwoIsACircle_largeExponentReachesCorners() {
        val circle = OGParametric.superellipse(exponent = 2f, samples = 64)
        circle.forEach { assertTrue(abs(dist(it) - 0.5f) < 2e-3f) }   // n=2 is exactly a circle
        val squareish = OGParametric.superellipse(exponent = 24f, samples = 64)
        assertTrue(squareish.allInBox())
        assertTrue(squareish.any { dist(it) > 0.6f })                // corners push past the circle
    }

    @Test
    fun blob_isDeterministicBySeed_andStaysInBox() {
        val a = OGParametric.blob(lobes = 6, irregularity = 0.5f, seed = 1, samples = 60)
        val b = OGParametric.blob(lobes = 6, irregularity = 0.5f, seed = 1, samples = 60)
        val c = OGParametric.blob(lobes = 6, irregularity = 0.5f, seed = 2, samples = 60)
        assertEquals(a, b)                 // same seed → identical
        assertNotEquals(a, c)              // different seed → different blob
        assertTrue(a.allInBox() && a.allFinite())
        a.forEach { assertTrue(dist(it) <= 0.5f + 1e-3f) }
    }

    // ── spec + codec ────────────────────────────────────────────────────────────────────────────

    @Test
    fun spec_routesKindToGenerator_andUnknownIsEmpty() {
        assertEquals(OGParametric.star(7, 0.45f), OGParametricSpec("star", count = 7, innerRatio = 0.45f).toPoints())
        assertEquals(OGParametric.gear(10, 0.3f), OGParametricSpec("gear", count = 10, depth = 0.3f).toPoints())
        assertTrue(OGParametricSpec("totally-unknown").toPoints().isEmpty())
    }

    @Test
    fun spec_toShape_wrapsPointsWithSmoothing() {
        val shape = OGParametricSpec("polygon", count = 5).toShape(smoothing = 0.5f)
        assertEquals(OGParametric.regularPolygon(5), shape.points)
        assertEquals(0.5f, shape.smoothing)
    }

    @Test
    fun codec_roundTrips_andToleratesFencesAndGarbage() {
        val spec = OGParametricSpec("blob", count = 6, irregularity = 0.5f, seed = 3)
        assertEquals(spec, OGParametrics.decodeSpec(OGParametrics.encode(spec)))
        // tolerant of markdown fences
        val fenced = "```json\n{\"kind\":\"star\",\"count\":6}\n```"
        assertEquals(OGParametric.star(6), OGParametrics.decodeOrNull(fenced)?.points)
        assertEquals(null, OGParametrics.decodeOrNull("not json at all"))
    }
}
