package com.solidkey.painpoints.lens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OGLensTest {

    // ── Vignette ──────────────────────────────────────────────────────────────

    @Test
    fun vignette_presets_exist() {
        for (n in listOf("subtle_vig", "moody_vig", "lomo_vig"))
            assertNotNull(OGLens.vignettePresets[n], n)
        assertEquals(3, OGLens.vignettePresets.size)
    }

    @Test
    fun vignette_codec_round_trips() {
        assertEquals(OGLens.MOODY_VIG, OGLens.decodeVignetteSpec(OGLens.encodeVignette(OGLens.MOODY_VIG)))
    }

    @Test
    fun vignette_decode_tolerates_prose() {
        val s = OGLens.decodeVignetteSpec("""here: {"intensity":0.8,"radius":0.4}""")
        assertEquals(0.8f, s.intensity)
        assertEquals(0.4f, s.radius)
    }

    @Test
    fun vignette_decode_bad_input_null() {
        assertEquals(null, OGLens.decodeVignetteSpecOrNull("not json"))
    }

    @Test
    fun vignette_sanitized_clamps() {
        val s = OGVignetteSpec(intensity = 5f, radius = 10f, softness = -1f).sanitized()
        assertTrue(s.intensity in 0f..1f)
        assertTrue(s.radius in 0f..1.5f)
        assertTrue(s.softness in 0f..1f)
    }

    // ── Chroma ────────────────────────────────────────────────────────────────

    @Test
    fun chroma_presets_exist() {
        for (n in listOf("chroma_retro", "chroma_glitch"))
            assertNotNull(OGLens.chromaPresets[n], n)
        assertEquals(2, OGLens.chromaPresets.size)
    }

    @Test
    fun chroma_codec_round_trips() {
        assertEquals(OGLens.CHROMA_RETRO, OGLens.decodeChromaSpec(OGLens.encodeChroma(OGLens.CHROMA_RETRO)))
    }

    @Test
    fun chroma_decode_tolerates_prose() {
        val s = OGLens.decodeChromaSpec("""result: {"shift":6.0,"pulse":0.5}""")
        assertEquals(6f, s.shift)
        assertEquals(0.5f, s.pulse)
    }

    @Test
    fun chroma_decode_bad_input_null() {
        assertEquals(null, OGLens.decodeChromaSpecOrNull("not json"))
    }

    @Test
    fun chroma_sanitized_clamps() {
        val s = OGChromaSpec(shift = 999f, pulse = 5f, pulseSpeed = 0f).sanitized()
        assertTrue(s.shift in 0f..20f)
        assertTrue(s.pulse in 0f..1f)
        assertTrue(s.pulseSpeed >= 0.1f)
    }

    // ── Light leak ────────────────────────────────────────────────────────────

    @Test
    fun light_leak_presets_exist() {
        for (n in listOf("leak_warm", "leak_cool"))
            assertNotNull(OGLens.lightLeakPresets[n], n)
        assertEquals(2, OGLens.lightLeakPresets.size)
    }

    @Test
    fun light_leak_codec_round_trips() {
        assertEquals(OGLens.LEAK_WARM, OGLens.decodeLightLeakSpec(OGLens.encodeLightLeak(OGLens.LEAK_WARM)))
    }

    @Test
    fun light_leak_decode_tolerates_prose() {
        val s = OGLens.decodeLightLeakSpec("""here: {"opacity":0.4,"cornerX":1.0}""")
        assertEquals(0.4f, s.opacity)
        assertEquals(1f, s.cornerX)
    }

    @Test
    fun light_leak_decode_bad_input_null() {
        assertEquals(null, OGLens.decodeLightLeakSpecOrNull("not json"))
    }

    @Test
    fun light_leak_sanitized_clamps() {
        val s = OGLightLeakSpec(opacity = 2f, cornerX = 5f, cornerY = -1f, spread = 0f).sanitized()
        assertTrue(s.opacity in 0f..1f)
        assertTrue(s.cornerX in 0f..1f)
        assertTrue(s.cornerY in 0f..1f)
        assertTrue(s.spread >= 0.1f)
    }

    // ── Anamorphic ────────────────────────────────────────────────────────────

    @Test
    fun anamorphic_presets_exist() {
        for (n in listOf("anamorphic_blue", "anamorphic_amber"))
            assertNotNull(OGLens.anamorphicPresets[n], n)
        assertEquals(2, OGLens.anamorphicPresets.size)
    }

    @Test
    fun anamorphic_codec_round_trips() {
        assertEquals(OGLens.ANAMORPHIC_BLUE, OGLens.decodeAnamorphicSpec(OGLens.encodeAnamorphic(OGLens.ANAMORPHIC_BLUE)))
    }

    @Test
    fun anamorphic_decode_tolerates_prose() {
        val s = OGLens.decodeAnamorphicSpec("""result: {"width":4.0,"centerY":0.3}""")
        assertEquals(4f, s.width)
        assertEquals(0.3f, s.centerY)
    }

    @Test
    fun anamorphic_decode_bad_input_null() {
        assertEquals(null, OGLens.decodeAnamorphicSpecOrNull("not json"))
    }

    @Test
    fun anamorphic_sanitized_clamps() {
        val s = OGAnamorphicSpec(opacity = 2f, width = 999f, centerY = -1f, spread = 0f).sanitized()
        assertTrue(s.opacity in 0f..1f)
        assertTrue(s.width in 0.5f..10f)
        assertTrue(s.centerY in 0f..1f)
        assertTrue(s.spread >= 0.1f)
    }

    // ── Lens flare ────────────────────────────────────────────────────────────

    @Test
    fun flare_presets_exist() {
        for (n in listOf("flare_cinema", "flare_sun", "flare_subtle"))
            assertNotNull(OGLens.flarePresets[n], n)
        assertEquals(3, OGLens.flarePresets.size)
    }

    @Test
    fun flare_codec_round_trips() {
        assertEquals(OGLens.FLARE_CINEMA, OGLens.decodeLensFlareSpec(OGLens.encodeLensFlare(OGLens.FLARE_CINEMA)))
    }

    @Test
    fun flare_decode_tolerates_prose() {
        val s = OGLens.decodeLensFlareSpec("""here: {"streakCount":8,"bokehCount":3}""")
        assertEquals(8, s.streakCount)
        assertEquals(3, s.bokehCount)
    }

    @Test
    fun flare_decode_bad_input_null() {
        assertEquals(null, OGLens.decodeLensFlareSpecOrNull("not json"))
    }

    @Test
    fun flare_sanitized_clamps() {
        val s = OGLensFlareSpec(opacity = 2f, scale = 99f, streakCount = 99, bokehCount = 99).sanitized()
        assertTrue(s.opacity in 0f..1f)
        assertTrue(s.scale in 0.1f..3f)
        assertTrue(s.streakCount in 2..12)
        assertTrue(s.bokehCount in 0..10)
    }
}
