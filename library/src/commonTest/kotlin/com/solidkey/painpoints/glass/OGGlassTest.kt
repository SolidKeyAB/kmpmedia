package com.solidkey.painpoints.glass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OGGlassTest {

    @Test
    fun presets_exist_and_are_case_insensitive() {
        for (n in listOf("magnifier", "reader", "minifier", "amber", "rose")) assertNotNull(OGGlass.preset(n), "preset $n")
        assertNotNull(OGGlass.preset("MAGNIFIER"))
        assertEquals(5, OGGlass.presets.size)
    }

    @Test
    fun magnifier_magnifies_minifier_shrinks() {
        assertTrue(OGGlass.MAGNIFIER.zoom > 1f, "magnifier should zoom in")
        assertTrue(OGGlass.MINIFIER.zoom < 1f, "minifier should zoom out")
    }

    @Test
    fun codec_round_trips() {
        val back = OGGlass.decodeSpec(OGGlass.encode(OGGlass.AMBER))
        assertEquals(OGGlass.AMBER, back)
    }

    @Test
    fun decode_tolerates_prose_and_partial_json() {
        val s = OGGlass.decodeSpec("lens please: {\"name\":\"x\",\"zoom\":3.0,\"tint\":\"#102030\"} thanks")
        assertEquals("x", s.name)
        assertEquals(3f, s.zoom)
        assertEquals("#102030", s.tint)
    }

    @Test
    fun decode_bad_input_is_null_not_crash() {
        assertEquals(null, OGGlass.decodeSpecOrNull("no json here at all"))
    }

    @Test
    fun sanitized_clamps_out_of_range() {
        val s = OGGlassSpec(zoom = 99f, radius = 9f, tintStrength = 5f, rimWidth = 9f, rimAlpha = 5f, highlight = 9f).sanitized()
        assertTrue(s.zoom <= 6f)
        assertTrue(s.radius in 0.03f..1f)
        assertEquals(1f, s.tintStrength)
        assertTrue(s.rimWidth in 0f..0.5f)
        assertEquals(1f, s.rimAlpha)
        assertEquals(1f, s.highlight)
    }
}
