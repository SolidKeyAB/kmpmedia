package com.solidkey.painpoints.fx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OGVortexTest {

    @Test
    fun presets_exist_and_are_case_insensitive() {
        for (n in listOf("tornado", "cyclone", "dust_devil", "fire_vortex", "whirlpool", "portal", "maelstrom", "nebula", "eye_of_storm")) {
            assertNotNull(OGVortex.preset(n), "preset $n")
        }
        assertNotNull(OGVortex.preset("TORNADO"))
        assertEquals(9, OGVortex.presets.size)
    }

    @Test
    fun codec_round_trips() {
        val back = OGVortex.decodeSpec(OGVortex.encode(OGVortex.TORNADO))
        assertEquals(OGVortex.TORNADO, back)
    }

    @Test
    fun decode_tolerates_prose_and_partial_json() {
        val s = OGVortex.decodeSpec("here you go: {\"name\":\"x\",\"rings\":5,\"arms\":2} done")
        assertEquals("x", s.name)
        assertEquals(5, s.rings)
        assertEquals(2, s.arms)
    }

    @Test
    fun decode_bad_input_is_null_not_crash() {
        assertEquals(null, OGVortex.decodeSpecOrNull("not json at all"))
    }

    @Test
    fun sanitized_clamps_out_of_range() {
        val s = OGVortexSpec(
            eyeRadiusFraction = 99f, rings = 100, arms = -5,
            armSweepDeg = 5f, angularVelocity = 0f, opacity = 3f,
        ).sanitized()
        assertTrue(s.eyeRadiusFraction in 0.04f..0.40f)
        assertTrue(s.rings in 1..8)
        assertTrue(s.arms in 0..6)
        assertTrue(s.armSweepDeg >= 10f)
        assertTrue(s.angularVelocity >= 0.05f)
        assertTrue(s.opacity in 0f..1f)
    }

    @Test
    fun vortex_state_deterministic() {
        val spec = OGVortex.TORNADO.sanitized()
        assertEquals(vortexState(spec, 1.0f), vortexState(spec, 1.0f))
    }

    @Test
    fun vortex_state_has_correct_ring_count() {
        for (rings in 1..5) {
            val spec = OGVortexSpec(rings = rings).sanitized()
            assertEquals(rings, vortexState(spec, 1.0f).ringOmegas.size)
        }
    }

    @Test
    fun vortex_state_rankine_inner_fastest() {
        val spec = OGVortexSpec(rings = 3, angularVelocity = 1.0f).sanitized()
        val state = vortexState(spec, 1.0f)
        assertTrue(state.ringOmegas[0] > state.ringOmegas[2], "inner ring must rotate faster than outer")
    }

    @Test
    fun vortex_state_advances_over_time() {
        val spec = OGVortex.TORNADO.sanitized()
        val t0 = vortexState(spec, 0f)
        val t1 = vortexState(spec, 2f)
        assertTrue(t0.ringOmegas[0] != t1.ringOmegas[0], "angles should advance over time")
    }

    @Test
    fun single_ring_uses_full_velocity() {
        val spec = OGVortexSpec(rings = 1, angularVelocity = 2.0f).sanitized()
        val state = vortexState(spec, 1.0f)
        val expected = 1.0f * 2.0f * (2.0 * kotlin.math.PI).toFloat()
        assertTrue(kotlin.math.abs(state.ringOmegas[0] - expected) < 0.001f)
    }

    @Test
    fun new_fields_default_to_backward_compat_values() {
        assertEquals(0, OGVortex.TORNADO.particles)
        assertEquals(0f, OGVortex.TORNADO.streakTailDeg)
        assertEquals(1, OGVortex.TORNADO.depthLayers)
    }

    @Test
    fun sanitized_clamps_new_fields() {
        val s = OGVortexSpec(particles = 9999, streakTailDeg = 999f, depthLayers = 99).sanitized()
        assertTrue(s.particles in 0..250)
        assertTrue(s.streakTailDeg in 0f..40f)
        assertTrue(s.depthLayers in 1..4)
        val low = OGVortexSpec(particles = -1, streakTailDeg = -5f, depthLayers = 0).sanitized()
        assertEquals(0, low.particles)
        assertEquals(0f, low.streakTailDeg)
        assertEquals(1, low.depthLayers)
    }

    @Test
    fun codec_round_trips_new_fields() {
        val back = OGVortex.decodeSpec(OGVortex.encode(OGVortex.EYE_OF_STORM))
        assertEquals(OGVortex.EYE_OF_STORM, back)
    }

    @Test
    fun eye_of_storm_uses_all_new_features() {
        val p = OGVortex.EYE_OF_STORM
        assertTrue(p.particles > 0, "should have particles")
        assertTrue(p.streakTailDeg > 0f, "should have streak tails")
        assertTrue(p.depthLayers > 1, "should have depth layers")
    }
}
