package com.solidkey.painpoints.fx

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OGFlameTest {

    @Test
    fun presets_exist_and_are_case_insensitive() {
        for (n in listOf("candle", "campfire", "torch", "inferno")) assertNotNull(OGFlame.preset(n), "preset $n")
        assertNotNull(OGFlame.preset("CANDLE"))
        assertEquals(4, OGFlame.presets.size)
    }

    @Test
    fun codec_round_trips() {
        val back = OGFlame.decodeSpec(OGFlame.encode(OGFlame.CAMPFIRE))
        assertEquals(OGFlame.CAMPFIRE, back)
    }

    @Test
    fun decode_tolerates_prose_and_partial_json() {
        val s = OGFlame.decodeSpec("here you go: {\"name\":\"x\",\"coreColor\":\"#102030\",\"tongues\":5} ok?")
        assertEquals("x", s.name)
        assertEquals("#102030", s.coreColor)
        assertEquals(5, s.tongues)
    }

    @Test
    fun decode_bad_input_is_null_not_crash() {
        assertEquals(null, OGFlame.decodeSpecOrNull("no json here at all"))
    }

    @Test
    fun sanitized_clamps_out_of_range() {
        val s = OGFlameSpec(tongues = 9999, height = 9f, width = -1f, sway = 9f, wind = 9f, octaves = 99, opacity = 5f, baseY = 9f).sanitized()
        assertTrue(s.tongues in 1..60)
        assertTrue(s.height <= 1.5f)
        assertTrue(s.width >= 0.01f)
        assertTrue(s.sway in 0f..0.5f)
        assertTrue(s.wind in -1f..1f)
        assertTrue(s.octaves in 1..8)
        assertEquals(1f, s.opacity)
        assertEquals(1f, s.baseY)
    }

    @Test
    fun tongues_are_deterministic_and_well_formed() {
        val spec = OGFlame.CAMPFIRE.sanitized()
        val a = flameTongues(spec, 1.5f)
        val b = flameTongues(spec, 1.5f)
        assertEquals(a, b) // same (spec, time) → identical fire (cross-platform guarantee)
        assertEquals(spec.tongues, a.size)
        for (t in a) {
            assertTrue(t.spine.size >= 2, "each tongue needs a spine")
            assertTrue(t.alpha in 0f..1f, "alpha: ${t.alpha}")
            assertTrue(t.halfWidth > 0f, "halfWidth: ${t.halfWidth}")
            // Base sits at baseY, tip is above it (smaller y, screen convention).
            assertTrue(t.spine.last().y < t.spine.first().y, "tongue should rise upward")
        }
    }

    @Test
    fun flame_flickers_over_time() {
        val spec = OGFlame.CAMPFIRE.sanitized()
        val t0 = flameTongues(spec, 0f)
        val t1 = flameTongues(spec, 1f)
        assertTrue(
            t0.indices.any { t0[it].spine.last().y != t1[it].spine.last().y },
            "flame tips should move/flicker over time",
        )
    }

    @Test
    fun wind_bends_the_tips_downwind() {
        val calm = flameTongues(OGFlame.CANDLE.copy(wind = 0f, sway = 0f).sanitized(), 0.5f)
        val windy = flameTongues(OGFlame.CANDLE.copy(wind = 0.3f, sway = 0f).sanitized(), 0.5f)
        // Base roots unchanged, tips pushed to the right (+x) by the wind lean.
        assertTrue(abs(calm[0].spine.first().x - windy[0].spine.first().x) < 1e-4f, "base stays rooted")
        assertTrue(windy[0].spine.last().x > calm[0].spine.last().x, "tip should lean downwind")
    }
}
