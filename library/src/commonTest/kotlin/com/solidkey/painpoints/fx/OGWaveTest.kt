package com.solidkey.painpoints.fx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OGWaveTest {

    @Test
    fun presets_exist_and_are_case_insensitive() {
        for (n in listOf("calm", "ocean", "flood", "lava")) assertNotNull(OGWave.preset(n), "preset $n")
        assertNotNull(OGWave.preset("OCEAN"))
        assertEquals(4, OGWave.presets.size)
    }

    @Test
    fun codec_round_trips() {
        val back = OGWave.decodeSpec(OGWave.encode(OGWave.OCEAN))
        assertEquals(OGWave.OCEAN, back)
    }

    @Test
    fun decode_tolerates_prose_and_partial_json() {
        val s = OGWave.decodeSpec("sure, here: {\"name\":\"x\",\"deepColor\":\"#102030\",\"layers\":5} — enjoy")
        assertEquals("x", s.name)
        assertEquals("#102030", s.deepColor)
        assertEquals(5, s.layers)
    }

    @Test
    fun decode_bad_input_is_null_not_crash() {
        assertEquals(null, OGWave.decodeSpecOrNull("no json here at all"))
    }

    @Test
    fun bands_are_deterministic_well_formed_and_move() {
        val spec = OGWave.OCEAN.sanitized()
        val a = waveBands(spec, 1.5f)
        val b = waveBands(spec, 1.5f)
        assertEquals(a, b) // same (spec, time) → identical bands (cross-platform guarantee)
        assertEquals(spec.layers, a.size)
        for (band in a) {
            assertTrue(band.crests.size >= 2, "crest samples: ${band.crests.size}")
            assertTrue(band.fillAlpha in 0f..1f, "fillAlpha: ${band.fillAlpha}")
            assertEquals(0f, band.crests.first().x)
            assertEquals(1f, band.crests.last().x)
        }
        val later = waveBands(spec, 3.5f)
        assertTrue(
            a.indices.any { i -> a[i].crests.indices.any { j -> a[i].crests[j].y != later[i].crests[j].y } },
            "waves should move over time",
        )
    }
}
