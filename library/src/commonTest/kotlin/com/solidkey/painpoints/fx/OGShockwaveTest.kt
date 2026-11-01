package com.solidkey.painpoints.fx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OGShockwaveTest {

    @Test
    fun presets_exist_and_are_case_insensitive() {
        for (n in listOf("airburst", "ground", "splash", "electric")) assertNotNull(OGShockwave.preset(n), "preset $n")
        assertNotNull(OGShockwave.preset("AIRBURST"))
        assertNotNull(OGShockwave.preset("Ground"))
        assertEquals(4, OGShockwave.presets.size)
    }

    @Test
    fun codec_round_trips() {
        val back = OGShockwave.decodeSpec(OGShockwave.encode(OGShockwave.GROUND))
        assertEquals(OGShockwave.GROUND, back)
    }

    @Test
    fun decode_bad_input_is_null_not_crash() {
        assertEquals(null, OGShockwave.decodeSpecOrNull("no json here at all"))
    }

    @Test
    fun sanitized_clamps_squash() {
        assertEquals(0.1f, OGShockwaveSpec(squash = -3f).sanitized().squash)
        assertEquals(0.1f, OGShockwaveSpec(squash = 0.05f).sanitized().squash)
        assertEquals(1f, OGShockwaveSpec(squash = 2.5f).sanitized().squash)
    }
}
