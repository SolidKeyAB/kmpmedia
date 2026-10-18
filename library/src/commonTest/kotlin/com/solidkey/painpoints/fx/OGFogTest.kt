package com.solidkey.painpoints.fx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OGFogTest {

    @Test
    fun presets_exist_and_are_case_insensitive() {
        for (n in listOf("fog", "smoke", "mist", "steam")) assertNotNull(OGFog.preset(n), "preset $n")
        assertNotNull(OGFog.preset("FOG"))
        assertEquals(4, OGFog.presets.size)
    }

    @Test
    fun codec_round_trips() {
        val back = OGFog.decodeSpec(OGFog.encode(OGFog.SMOKE))
        assertEquals(OGFog.SMOKE, back)
    }

    @Test
    fun decode_tolerates_prose_and_partial_json() {
        val s = OGFog.decodeSpec("sure, here: {\"name\":\"x\",\"color\":\"#102030\",\"density\":7} — enjoy")
        assertEquals("x", s.name)
        assertEquals("#102030", s.color)
        assertEquals(7, s.density)
    }

    @Test
    fun decode_bad_input_is_null_not_crash() {
        assertEquals(null, OGFog.decodeSpecOrNull("no json here at all"))
    }

    @Test
    fun sanitized_clamps_out_of_range() {
        val s = OGFogSpec(density = 9999, opacity = 5f, scale = -1f, turbulence = 9f, octaves = 99, softness = 2f).sanitized()
        assertTrue(s.density in 1..120)
        assertEquals(1f, s.opacity)
        assertTrue(s.scale >= 0.03f)
        assertTrue(s.turbulence in 0f..1f)
        assertTrue(s.octaves in 1..8)
        assertEquals(1f, s.softness)
    }

    @Test
    fun puffs_are_deterministic_and_in_bounds() {
        val spec = OGFog.SMOKE.sanitized()
        val a = fogPuffs(spec, 1.5f)
        val b = fogPuffs(spec, 1.5f)
        assertEquals(a, b) // same (spec, time) → identical puffs (cross-platform guarantee)
        assertEquals(spec.density, a.size)
        for (p in a) {
            assertTrue(p.cx >= 0f && p.cx < 1f, "cx wrapped: ${p.cx}")
            assertTrue(p.cy >= 0f && p.cy < 1f, "cy wrapped: ${p.cy}")
            assertTrue(p.alpha in 0f..1f, "alpha: ${p.alpha}")
            assertTrue(p.radiusFrac > 0f, "radius: ${p.radiusFrac}")
        }
    }

    @Test
    fun puffs_drift_over_time() {
        val spec = OGFog.FOG.sanitized()
        val t0 = fogPuffs(spec, 0f)
        val t1 = fogPuffs(spec, 2f)
        assertTrue(t0.indices.any { t0[it].cx != t1[it].cx || t0[it].cy != t1[it].cy }, "fog should move over time")
    }
}
