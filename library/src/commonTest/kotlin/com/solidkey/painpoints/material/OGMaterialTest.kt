package com.solidkey.painpoints.material

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OGMaterialTest {

    // ── OGSurfaceSpec ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun surface_finish_defaults_are_ordered_by_intensity() {
        val (matteI, _)  = OGFinish.MATTE.defaults()
        val (satinI, _)  = OGFinish.SATIN.defaults()
        val (glossI, _)  = OGFinish.GLOSS.defaults()
        val (mirrorI, _) = OGFinish.MIRROR.defaults()
        assertTrue(matteI < satinI, "matte < satin intensity")
        assertTrue(satinI < glossI, "satin < gloss intensity")
        assertTrue(glossI < mirrorI, "gloss < mirror intensity")
    }

    @Test
    fun surface_finish_sizes_are_ordered_largest_to_smallest() {
        val (_, matteS)  = OGFinish.MATTE.defaults()
        val (_, satinS)  = OGFinish.SATIN.defaults()
        val (_, glossS)  = OGFinish.GLOSS.defaults()
        val (_, mirrorS) = OGFinish.MIRROR.defaults()
        assertTrue(mirrorS < glossS, "mirror < gloss size")
        assertTrue(glossS < satinS, "gloss < satin size")
    }

    @Test
    fun surface_sanitized_expands_negative_sentinels_from_finish() {
        val spec = OGSurfaceSpec(finish = OGFinish.GLOSS).sanitized()
        val (defaultI, defaultS) = OGFinish.GLOSS.defaults()
        assertEquals(defaultI, spec.specularIntensity, 0.001f)
        assertEquals(defaultS, spec.specularSize, 0.001f)
    }

    @Test
    fun surface_sanitized_respects_explicit_values() {
        val spec = OGSurfaceSpec(finish = OGFinish.GLOSS, specularIntensity = 0.2f, specularSize = 0.8f).sanitized()
        assertEquals(0.2f, spec.specularIntensity, 0.001f)
        assertEquals(0.8f, spec.specularSize, 0.001f)
    }

    @Test
    fun surface_sanitized_clamps_out_of_range() {
        val spec = OGSurfaceSpec(specularIntensity = 5f, specularSize = -1f).sanitized()
        assertTrue(spec.specularIntensity in 0f..1f)
        assertTrue(spec.specularSize >= 0.01f)
    }

    @Test
    fun surface_matte_is_identity() {
        assertTrue(OGSurfaceSpec.None.isIdentity)
    }

    @Test
    fun surface_codec_round_trips_all_presets() {
        for (p in OGMaterial.surfacePresets.values) {
            assertEquals(p, OGMaterial.decodeSurface(OGMaterial.encode(p)))
        }
    }

    @Test
    fun surface_presets_exist() {
        for (name in listOf("ceramic", "lacquer", "chrome", "rubber")) {
            assertNotNull(OGMaterial.surfacePresets[name], "preset $name")
        }
    }

    @Test
    fun surface_decode_bad_input_is_null() {
        assertNull(OGMaterial.decodeSurfaceOrNull("not json"))
    }

    // ── OGIridescenceSpec ────────────────────────────────────────────────────────────────────────────

    @Test
    fun iridescence_sanitized_clamps_range_and_intensity() {
        val s = OGIridescenceSpec(hueRange = 500f, intensity = 2f, saturation = -1f, lightness = 2f).sanitized()
        assertTrue(s.hueRange <= 360f)
        assertTrue(s.intensity in 0f..1f)
        assertTrue(s.saturation in 0f..1f)
        assertTrue(s.lightness in 0.1f..0.9f)
    }

    @Test
    fun iridescence_sanitized_normalises_base_hue() {
        val s = OGIridescenceSpec(baseHue = -90f).sanitized()
        assertTrue(s.baseHue in 0f..360f)
        val s2 = OGIridescenceSpec(baseHue = 400f).sanitized()
        assertTrue(s2.baseHue in 0f..360f)
    }

    @Test
    fun iridescence_zero_intensity_is_identity() {
        assertTrue(OGIridescenceSpec(intensity = 0f).isIdentity)
        assertFalse(OGIridescenceSpec(intensity = 0.1f).isIdentity)
    }

    @Test
    fun iridescence_codec_round_trips_all_presets() {
        for (p in OGMaterial.iridescencePresets.values) {
            assertEquals(p, OGMaterial.decodeIridescence(OGMaterial.encode(p)))
        }
    }

    @Test
    fun iridescence_presets_exist() {
        for (name in listOf("soap", "beetle", "holographic", "pearl")) {
            assertNotNull(OGMaterial.iridescencePresets[name], "preset $name")
        }
    }

    @Test
    fun iridescence_decode_bad_input_is_null() {
        assertNull(OGMaterial.decodeIridescenceOrNull("not json"))
    }

    // ── OGTranslucencySpec ───────────────────────────────────────────────────────────────────────────

    @Test
    fun translucency_sanitized_clamps_all_knobs() {
        val s = OGTranslucencySpec(depth = 5f, scatterIntensity = 2f, rimStrength = -1f).sanitized()
        assertTrue(s.depth in 0f..1f)
        assertTrue(s.scatterIntensity in 0f..1f)
        assertTrue(s.rimStrength in 0f..1f)
    }

    @Test
    fun translucency_zero_values_is_identity() {
        assertTrue(OGTranslucencySpec(scatterIntensity = 0f, rimStrength = 0f).isIdentity)
        assertFalse(OGTranslucencySpec(scatterIntensity = 0.1f).isIdentity)
    }

    @Test
    fun translucency_codec_round_trips_all_presets() {
        for (p in OGMaterial.translucencyPresets.values) {
            assertEquals(p, OGMaterial.decodeTranslucency(OGMaterial.encode(p)))
        }
    }

    @Test
    fun translucency_presets_exist() {
        for (name in listOf("jade", "wax", "skin", "frosted")) {
            assertNotNull(OGMaterial.translucencyPresets[name], "preset $name")
        }
    }

    @Test
    fun translucency_decode_bad_input_is_null() {
        assertNull(OGMaterial.decodeTranslucencyOrNull("not json"))
    }

    // ── OGMetalSpec ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun metal_sanitized_clamps_reflectivity_and_anisotropy() {
        val s = OGMetalSpec(reflectivity = 5f, anisotropy = -1f).sanitized()
        assertTrue(s.reflectivity in 0f..1f)
        assertTrue(s.anisotropy in 0f..1f)
    }

    @Test
    fun metal_zero_reflectivity_is_identity() {
        assertTrue(OGMetalSpec.None.isIdentity)
        assertFalse(OGMetalSpec(reflectivity = 0.1f).isIdentity)
    }

    @Test
    fun metal_codec_round_trips_all_presets() {
        for (p in OGMaterial.metalPresets.values) {
            assertEquals(p, OGMaterial.decodeMetal(OGMaterial.encode(p)))
        }
    }

    @Test
    fun metal_presets_exist() {
        for (name in listOf("silver", "gold", "copper", "brushed_steel", "rose_gold")) {
            assertNotNull(OGMaterial.metalPresets[name], "preset $name")
        }
    }

    @Test
    fun metal_decode_bad_input_is_null() {
        assertNull(OGMaterial.decodeMetalOrNull("not json"))
    }

    // ── colorFromHsl ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun hsl_red_at_hue_0() {
        val c = colorFromHsl(0f, 1f, 0.5f)
        assertTrue(c.red > 0.9f, "red channel high at hue=0")
        assertTrue(c.green < 0.1f, "green channel low at hue=0")
        assertTrue(c.blue < 0.1f, "blue channel low at hue=0")
    }

    @Test
    fun hsl_green_at_hue_120() {
        val c = colorFromHsl(120f, 1f, 0.5f)
        assertTrue(c.green > 0.9f, "green channel high at hue=120")
        assertTrue(c.red < 0.1f, "red channel low at hue=120")
    }

    @Test
    fun hsl_blue_at_hue_240() {
        val c = colorFromHsl(240f, 1f, 0.5f)
        assertTrue(c.blue > 0.9f, "blue channel high at hue=240")
        assertTrue(c.red < 0.1f, "red channel low at hue=240")
    }

    @Test
    fun hsl_alpha_is_passed_through() {
        val c = colorFromHsl(0f, 1f, 0.5f, alpha = 0.4f)
        assertEquals(0.4f, c.alpha, 0.01f)
    }

    @Test
    fun hsl_white_at_full_lightness() {
        val c = colorFromHsl(0f, 1f, 1f)
        assertTrue(c.red > 0.99f && c.green > 0.99f && c.blue > 0.99f)
    }

    @Test
    fun hsl_black_at_zero_lightness() {
        val c = colorFromHsl(0f, 1f, 0f)
        assertTrue(c.red < 0.01f && c.green < 0.01f && c.blue < 0.01f)
    }

    // ── direction helpers ────────────────────────────────────────────────────────────────────────────

    @Test
    fun direction_right_at_0_degrees() {
        val d = directionOf(0f)
        assertEquals(1f, d.x, 0.001f)
        assertEquals(0f, d.y, 0.001f)
    }

    @Test
    fun direction_down_at_90_degrees() {
        val d = directionOf(90f)
        assertEquals(0f, d.x, 0.001f)
        assertEquals(1f, d.y, 0.001f)
    }
}
