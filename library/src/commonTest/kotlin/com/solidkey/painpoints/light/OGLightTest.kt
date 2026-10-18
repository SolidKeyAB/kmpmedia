package com.solidkey.painpoints.light

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OGLightTest {

    // ── Presets + codec ─────────────────────────────────────────────────────────────────────────

    @Test
    fun light_presets_exist_and_are_case_insensitive() {
        for (n in listOf("studio", "noon", "rim", "dramatic", "warm", "sculpt")) assertNotNull(OGLight.lightPreset(n), "light $n")
        assertNotNull(OGLight.lightPreset("STUDIO"))
        assertEquals(6, OGLight.lights.size)
    }

    @Test
    fun shadow_presets_exist_and_are_case_insensitive() {
        for (n in listOf("soft", "drop", "long", "contact", "floating")) assertNotNull(OGLight.shadowPreset(n), "shadow $n")
        assertNotNull(OGLight.shadowPreset("DROP"))
        assertEquals(5, OGLight.shadows.size)
    }

    @Test
    fun light_codec_round_trips() {
        assertEquals(OGLight.RIM, OGLight.decodeLight(OGLight.encode(OGLight.RIM)))
    }

    @Test
    fun shadow_codec_round_trips() {
        assertEquals(OGLight.DROP, OGLight.decodeShadow(OGLight.encode(OGLight.DROP)))
    }

    @Test
    fun light_decode_tolerates_prose_and_partial_json() {
        val s = OGLight.decodeLight("here you go: {\"name\":\"key\",\"angleDeg\":-100,\"intensity\":0.7} cheers")
        assertEquals("key", s.name)
        assertEquals(-100f, s.angleDeg)
        assertEquals(0.7f, s.intensity)
    }

    @Test
    fun shadow_decode_tolerates_prose_and_partial_json() {
        val s = OGLight.decodeShadow("sure: {\"name\":\"d\",\"distance\":0.09,\"contact\":0.4} done")
        assertEquals("d", s.name)
        assertEquals(0.09f, s.distance)
        assertEquals(0.4f, s.contact)
    }

    @Test
    fun decode_bad_input_is_null_not_crash() {
        assertEquals(null, OGLight.decodeLightOrNull("no json here"))
        assertEquals(null, OGLight.decodeShadowOrNull("still none"))
    }

    // ── Light maths ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun light_sanitized_clamps_out_of_range() {
        val s = OGLightSpec(intensity = 9f, ambient = -2f, rim = 9f, occlusion = -1f).sanitized()
        assertEquals(1f, s.intensity)
        assertEquals(0f, s.ambient)
        assertEquals(1f, s.rim)
        assertEquals(0f, s.occlusion)
    }

    @Test
    fun light_direction_points_at_the_light() {
        val up = OGLightSpec(angleDeg = -90f).direction()   // straight above
        assertTrue(abs(up.x) < 1e-3f, "x≈0 got ${up.x}")
        assertTrue(up.y < -0.99f, "y points up got ${up.y}")
        val right = OGLightSpec(angleDeg = 0f).direction()
        assertTrue(right.x > 0.99f && abs(right.y) < 1e-3f)
    }

    @Test
    fun light_alphas_ambient_lifts_only_the_shadow() {
        val dramatic = OGLightSpec(intensity = 0.6f, ambient = 0f)
        assertEquals(0.6f, dramatic.litAlpha(), 1e-4f)
        assertEquals(0.6f, dramatic.shadeAlpha(), 1e-4f)      // no fill ⇒ shadow as strong as the key
        val flat = OGLightSpec(intensity = 0.6f, ambient = 1f)
        assertEquals(0.6f, flat.litAlpha(), 1e-4f)            // highlight unaffected by ambient
        assertEquals(0f, flat.shadeAlpha(), 1e-4f)            // full fill ⇒ no core shadow
    }

    @Test
    fun light_zero_intensity_is_identity_but_rim_or_occlusion_is_not() {
        assertTrue(OGLightSpec(intensity = 0f).isIdentity)
        assertTrue(OGLightSpec.None.isIdentity)
        assertEquals(0f, OGLightSpec(intensity = 0f).litAlpha())
        assertFalse(OGLightSpec(intensity = 0f, rim = 0.5f).isIdentity)       // rim still draws
        assertFalse(OGLightSpec(intensity = 0f, occlusion = 0.5f).isIdentity) // AO still draws
    }

    // ── Sheen ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun sheen_presets_and_codec() {
        for (n in listOf("glint", "shimmer")) assertNotNull(OGLight.sheenPreset(n), "sheen $n")
        assertNotNull(OGLight.sheenPreset("GLINT"))
        assertEquals(2, OGLight.sheens.size)
        assertEquals(OGLight.GLINT, OGLight.decodeSheen(OGLight.encode(OGLight.GLINT)))
        assertNull(OGLight.decodeSheenOrNull("no json"))
    }

    @Test
    fun sheen_progress_sweeps_then_rests_deterministically() {
        val s = OGSheenSpec(periodMs = 1000f, dutyCycle = 0.4f, seed = 0)
        // seed 0 ⇒ phase starts at 0: t=0 is the start of a sweep, t≈200ms is mid-sweep, t≈700ms is resting.
        assertEquals(0f, s.progressAt(0f)!!, 1e-3f)
        assertTrue(s.progressAt(200f)!! in 0f..1f)
        assertNull(s.progressAt(700f))                           // past the duty window ⇒ resting (no draw)
        assertEquals(s.progressAt(200f)!!, s.progressAt(1200f)!!, 1e-3f) // one period later ⇒ same sweep (pure)
    }

    @Test
    fun sheen_sanitized_clamps() {
        val s = OGSheenSpec(intensity = 9f, width = 9f, periodMs = -5f, dutyCycle = 9f).sanitized()
        assertEquals(1f, s.intensity)
        assertTrue(s.width in 0.02f..1f)
        assertTrue(s.periodMs >= 1f)
        assertTrue(s.dutyCycle in 0.05f..1f)
    }

    // ── Spot ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun spot_presets_and_codec() {
        for (n in listOf("pool", "vignette", "storm")) assertNotNull(OGLight.spotPreset(n), "spot $n")
        assertNotNull(OGLight.spotPreset("POOL"))
        assertEquals(3, OGLight.spots.size)
        assertEquals(OGLight.POOL, OGLight.decodeSpot(OGLight.encode(OGLight.POOL)))
        assertNull(OGLight.decodeSpotOrNull("nope"))
    }

    @Test
    fun spot_sanitized_and_identity() {
        val s = OGSpotSpec(radius = -1f, softness = 9f, dim = 9f, lightIntensity = -2f).sanitized()
        assertTrue(s.radius > 0f)
        assertTrue(s.softness in 0f..1f)
        assertEquals(1f, s.dim)
        assertEquals(0f, s.lightIntensity)
        assertTrue(OGSpotSpec(dim = 0f, lightIntensity = 0f).isIdentity)
        assertFalse(OGLight.POOL.isIdentity)
    }

    // ── Shadow maths ────────────────────────────────────────────────────────────────────────────

    @Test
    fun shadow_sanitized_clamps_out_of_range() {
        val s = OGShadowSpec(distance = -1f, blur = -1f, spread = 9f, opacity = 9f, passes = 99, contact = 9f).sanitized()
        assertTrue(s.distance >= 0f)
        assertTrue(s.blur >= 0f)
        assertEquals(1f, s.spread)
        assertEquals(1f, s.opacity)
        assertTrue(s.passes in 1..8)
        assertEquals(1f, s.contact)
    }

    @Test
    fun shadow_drop_passes_are_well_formed_and_deterministic() {
        val spec = OGLight.SOFT
        val a = spec.dropPasses()
        val b = spec.dropPasses()
        assertEquals(a, b)                                    // pure ⇒ identical on every platform
        assertEquals(spec.passes, a.size)
        assertEquals(0f, a.first().first, 1e-4f)              // inner pass: no growth
        assertTrue(a.last().first > 0f, "outer pass grows when blur > 0")
        for ((growth, alpha) in a) {
            assertTrue(growth >= 0f, "growth $growth")
            assertTrue(alpha in 0f..1f, "alpha $alpha")
        }
        assertTrue(a.first().second >= a.last().second, "inner is at least as bright as outer")
    }

    @Test
    fun shadow_zero_opacity_draws_nothing() {
        assertTrue(OGShadowSpec(opacity = 0f).dropPasses().isEmpty())
        assertTrue(OGShadowSpec.None.isIdentity)
    }
}
