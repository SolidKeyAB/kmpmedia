package com.solidkey.painpoints.motion

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OGClothStripTest {

    private fun seg(s: OGClothStrip, i: Int): Float {
        val dx = s.x(i + 1) - s.x(i)
        val dy = s.y(i + 1) - s.y(i)
        return sqrt(dx * dx + dy * dy)
    }

    @Test
    fun reset_seedsHangingWithZeroVelocity() {
        val spec = OGClothSpec(nodes = 8, length = 0.35f, windAmplitude = 0f)
        val s = OGClothStrip(spec)
        s.reset(0.3f, 0.2f)
        assertEquals(0.3f, s.x(0), "head x at anchor")
        assertEquals(0.2f, s.y(0), "head y at anchor")
        val rest = spec.length / (spec.nodes - 1)
        // Hangs straight down, evenly spaced.
        for (i in 0 until s.count) {
            assertTrue(abs(s.x(i) - 0.3f) < 1e-6f, "node $i directly below anchor")
            assertTrue(abs(s.y(i) - (0.2f + i * rest)) < 1e-5f, "node $i at rest spacing")
        }
    }

    @Test
    fun settlesToRestLength_stillAir() {
        val spec = OGClothSpec(nodes = 12, length = 0.5f, windAmplitude = 0f, gravity = 1f, damping = 1.5f)
        val s = OGClothStrip(spec)
        s.reset(0.5f, 0.1f)
        repeat(240) { s.step(1f / 60f, 0.5f, 0.1f) } // ~4s to settle
        val rest = spec.length / (spec.nodes - 1)
        for (i in 0 until s.count - 1) {
            val e = seg(s, i)
            assertTrue(abs(e - rest) < rest * 0.1f, "segment $i length $e ~ rest $rest (inextensible)")
        }
    }

    @Test
    fun fixedStep_isDeterministic() {
        val spec = OGClothSpec(nodes = 14, windAmplitude = 0.8f, windFrequency = 0.9f, windSeed = 5)
        val a = OGClothStrip(spec); a.reset(0.5f, 0.1f)
        val b = OGClothStrip(spec); b.reset(0.5f, 0.1f)
        repeat(200) {
            val ax = 0.5f + 0.02f * it
            a.step(1f / 60f, ax % 1f, 0.1f)
            b.step(1f / 60f, ax % 1f, 0.1f)
        }
        for (i in 0 until a.count) {
            assertEquals(a.x(i), b.x(i), "node $i x identical across runs")
            assertEquals(a.y(i), b.y(i), "node $i y identical across runs")
        }
    }

    @Test
    fun stalledFrame_isClampedNotExploded() {
        val spec = OGClothSpec(nodes = 12, windAmplitude = 1f)
        val s = OGClothStrip(spec)
        s.reset(0.5f, 0.1f)
        s.step(10f, 0.5f, 0.1f) // a 10-second "stall" must not blow up
        for (i in 0 until s.count) {
            assertTrue(s.x(i).isFinite() && s.y(i).isFinite(), "node $i stays finite after a stall")
            assertTrue(abs(s.x(i)) < 10f && abs(s.y(i)) < 10f, "node $i stays bounded after a stall")
        }
    }

    @Test
    fun pinTail_bothEndsHeld() {
        val spec = OGClothSpec(nodes = 12, length = 0.6f, pinTail = true, windAmplitude = 0.5f)
        val s = OGClothStrip(spec)
        s.reset(0.2f, 0.3f)
        repeat(120) { s.step(1f / 60f, 0.2f, 0.3f, tailX = 0.8f, tailY = 0.35f) }
        assertTrue(abs(s.x(0) - 0.2f) < 1e-4f && abs(s.y(0) - 0.3f) < 1e-4f, "head pinned")
        assertTrue(abs(s.x(s.count - 1) - 0.8f) < 1e-4f && abs(s.y(s.count - 1) - 0.35f) < 1e-4f, "tail pinned")
    }

    @Test
    fun prevailingWind_deflectsTheFreeEnd() {
        fun settleTailX(wind: Float): Float {
            val s = OGClothStrip(OGClothSpec(nodes = 12, length = 0.5f, gravity = 0.8f, damping = 1f, wind = wind, windAmplitude = 0f))
            s.reset(0.5f, 0.1f)
            repeat(300) { s.step(1f / 60f, 0.5f, 0.1f) }
            return s.x(s.count - 1)
        }
        assertTrue(settleTailX(1.2f) > 0.55f, "wind blowing right deflects the free end right")
        assertTrue(settleTailX(-1.2f) < 0.45f, "wind blowing left deflects the free end left")
    }

    @Test
    fun codec_roundTripsAndIsLenient() {
        for (spec in OGCloths.presets.values) {
            val back = OGCloths.decodeSpec(OGCloths.encode(spec))
            assertEquals(spec, back, "preset ${spec.name} round-trips")
        }
        // Tolerates fences / prose, and fails soft on garbage.
        val fenced = "```json\n{\"name\":\"x\",\"nodes\":9,\"pinTail\":true}\n```"
        assertEquals(9, OGCloths.decodeSpec(fenced).nodes)
        assertTrue(OGCloths.decodeSpec(fenced).pinTail)
        assertNull(OGCloths.decodeSpecOrNull("not json at all"), "garbage decodes to null")
        assertEquals("scarf", OGCloths.preset("SCARF")?.name, "preset lookup is case-insensitive")
        assertNull(OGCloths.preset("nope"))
    }

    @Test
    fun sampleAt_matchesStepByStep() {
        val spec = OGClothSpec(nodes = 10, windAmplitude = 0.6f, windSeed = 2)
        val anchor: (Float) -> com.solidkey.painpoints.shape.OGPoint =
            { t -> com.solidkey.painpoints.shape.OGPoint(0.5f + 0.05f * t, 0.1f) }
        val sampled = OGCloths.sampleAt(spec, timeSec = 1f, anchorAt = anchor)
        // Reproduce the same fixed-step march by hand.
        val manual = OGClothStrip(spec)
        val h = 1f / 120f
        manual.reset(anchor(0f).x, anchor(0f).y)
        var t = 0f
        while (t < 1f) { val a = anchor(t + h); manual.step(h, a.x, a.y); t += h }
        for (i in 0 until manual.count) {
            assertTrue(abs(sampled.x(i) - manual.x(i)) < 1e-5f, "node $i x reproducible")
            assertTrue(abs(sampled.y(i) - manual.y(i)) < 1e-5f, "node $i y reproducible")
        }
    }
}
