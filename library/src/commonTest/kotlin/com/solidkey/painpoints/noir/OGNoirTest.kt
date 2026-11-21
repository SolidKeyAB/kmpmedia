package com.solidkey.painpoints.noir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OGNoirTest {

    // ── Grain ────────────────────────────────────────────────────────────────

    @Test
    fun grain_presets_exist() {
        for (n in listOf("kodak", "silver", "heavy")) assertNotNull(OGNoir.grainPresets[n], n)
        assertEquals(3, OGNoir.grainPresets.size)
    }

    @Test
    fun grain_codec_round_trips() {
        val back = OGNoir.decodeGrainSpec(OGNoir.encodeGrain(OGNoir.KODAK))
        assertEquals(OGNoir.KODAK, back)
    }

    @Test
    fun grain_decode_tolerates_prose() {
        val s = OGNoir.decodeGrainSpec("""here: {"intensity":0.3,"speed":12}""")
        assertEquals(0.3f, s.intensity)
        assertEquals(12f, s.speed)
    }

    @Test
    fun grain_decode_bad_input_null() {
        assertEquals(null, OGNoir.decodeGrainSpecOrNull("not json"))
    }

    @Test
    fun grain_sanitized_clamps() {
        val s = OGGrainSpec(intensity = 5f, speed = 999f, scale = 99f).sanitized()
        assertTrue(s.intensity in 0f..1f)
        assertTrue(s.speed in 1f..120f)
        assertTrue(s.scale in 0.3f..5f)
    }

    // ── Blind ────────────────────────────────────────────────────────────────

    @Test
    fun blind_presets_exist() {
        for (n in listOf("window", "harsh", "wide")) assertNotNull(OGNoir.blindPresets[n], n)
        assertEquals(3, OGNoir.blindPresets.size)
    }

    @Test
    fun blind_codec_round_trips() {
        val back = OGNoir.decodeBlindSpec(OGNoir.encodeBlinds(OGNoir.WINDOW))
        assertEquals(OGNoir.WINDOW, back)
    }

    @Test
    fun blind_decode_tolerates_prose() {
        val s = OGNoir.decodeBlindSpec("""here: {"slats":5,"angleDeg":-45.0}""")
        assertEquals(5, s.slats)
        assertEquals(-45f, s.angleDeg)
    }

    @Test
    fun blind_decode_bad_input_null() {
        assertEquals(null, OGNoir.decodeBlindSpecOrNull("not json"))
    }

    @Test
    fun blind_sanitized_clamps() {
        val s = OGBlindSpec(opacity = 2f, slats = 100, gapRatio = 5f, oscillationDeg = 999f).sanitized()
        assertTrue(s.opacity in 0f..1f)
        assertTrue(s.slats in 1..20)
        assertTrue(s.gapRatio in 0f..0.9f)
        assertTrue(s.oscillationDeg in 0f..45f)
    }

    // ── Spotlight ─────────────────────────────────────────────────────────────

    @Test
    fun spotlight_presets_exist() {
        for (n in listOf("interrogation", "stage", "alley")) assertNotNull(OGNoir.spotlightPresets[n], n)
        assertEquals(3, OGNoir.spotlightPresets.size)
    }

    @Test
    fun spotlight_codec_round_trips() {
        val back = OGNoir.decodeSpotlightSpec(OGNoir.encodeSpotlight(OGNoir.INTERROGATION))
        assertEquals(OGNoir.INTERROGATION, back)
    }

    @Test
    fun spotlight_decode_tolerates_prose() {
        val s = OGNoir.decodeSpotlightSpec("""result: {"radius":0.4,"scrimOpacity":0.9}""")
        assertEquals(0.4f, s.radius)
        assertEquals(0.9f, s.scrimOpacity)
    }

    @Test
    fun spotlight_decode_bad_input_null() {
        assertEquals(null, OGNoir.decodeSpotlightSpecOrNull("not json"))
    }

    @Test
    fun spotlight_sanitized_clamps() {
        val s = OGSpotlightSpec(scrimOpacity = 5f, radius = 99f, softness = 5f, swingAmp = 5f).sanitized()
        assertTrue(s.scrimOpacity in 0f..1f)
        assertTrue(s.radius in 0.05f..1f)
        assertTrue(s.softness in 0f..1f)
        assertTrue(s.swingAmp in 0f..0.4f)
    }

    @Test
    fun spotlight_sanitized_clamps_center() {
        val s = OGSpotlightSpec(centerX = -1f, centerY = 2f).sanitized()
        assertTrue(s.centerX in 0f..1f)
        assertTrue(s.centerY in 0f..1f)
    }
}
