package com.solidkey.painpoints.look

import androidx.compose.ui.graphics.BlendMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-maths + codec tests for the Live-looks layer. These deliberately exercise only the colour-matrix
 * maths ([buildColorMatrixArray] & friends → `FloatArray`), the serializable enum mapping, and the JSON
 * codec — never `ColorFilter` / `ColorMatrix` objects, which need a platform graphics backend absent in
 * a plain unit test. The maths is pure and deterministic, so JVM and iOS agree.
 */
class OGLookTest {

    private val eps = 1e-4f

    // row i (0..3), col j (0..4) of a 4×5 colour matrix
    private fun FloatArray.at(i: Int, j: Int) = this[i * 5 + j]

    // ── identity / neutral ────────────────────────────────────────────────────────────────────

    @Test
    fun defaultSpec_isIdentity() {
        assertTrue(OGLookSpec().isIdentity)
        assertTrue(OGLookSpec.Identity.isIdentity)
        assertFalse(OGLookSpec(saturation = 0f).isIdentity)
        assertFalse(OGLookSpec(brightness = 0.1f).isIdentity)
    }

    @Test
    fun identitySpec_yieldsIdentityMatrix() {
        val m = buildColorMatrixArray(OGLookSpec())
        for (i in 0 until 4) for (j in 0 until 5) {
            val expected = if (i == j) 1f else 0f
            assertEquals(expected, m.at(i, j), eps, "cell ($i,$j)")
        }
    }

    // ── individual knobs ──────────────────────────────────────────────────────────────────────

    @Test
    fun saturationZero_collapsesToLuminanceRows() {
        val m = buildColorMatrixArray(OGLookSpec(saturation = 0f))
        // Every output channel becomes the same luminance combination → the three RGB rows are equal.
        for (i in 0 until 3) {
            assertEquals(0.213f, m.at(i, 0), eps)
            assertEquals(0.715f, m.at(i, 1), eps)
            assertEquals(0.072f, m.at(i, 2), eps)
            assertEquals(0f, m.at(i, 4), eps) // no translate
        }
        assertEquals(1f, m.at(3, 3), eps) // alpha untouched
    }

    @Test
    fun brightness_isAdditiveOffsetScaledTo255() {
        val m = buildColorMatrixArray(OGLookSpec(brightness = 0.5f))
        for (i in 0 until 3) {
            assertEquals(1f, m.at(i, i), eps)        // diagonal untouched
            assertEquals(127.5f, m.at(i, 4), eps)    // +0.5 → +127.5 in 0..255 space
        }
    }

    @Test
    fun contrast_scalesAroundMidGrey() {
        val c = 0.5f
        val m = buildColorMatrixArray(OGLookSpec(contrast = c))
        val t = 127.5f * (1f - c)
        for (i in 0 until 3) {
            assertEquals(c, m.at(i, i), eps)
            assertEquals(t, m.at(i, 4), eps)
        }
    }

    @Test
    fun temperature_warmsRedCoolsBlue() {
        val m = buildColorMatrixArray(OGLookSpec(temperature = 1f))
        assertTrue(m.at(0, 0) > 1f, "red boosted")   // 1 + 0.2
        assertTrue(m.at(2, 2) < 1f, "blue dropped")  // 1 - 0.2
        assertEquals(1f, m.at(1, 1), eps)            // green untouched by pure temperature
    }

    @Test
    fun hueZero_isIdentity_and_hue360_roundsBack() {
        val h0 = buildColorMatrixArray(OGLookSpec(hue = 0f))
        val h360 = buildColorMatrixArray(OGLookSpec(hue = 360f))
        // hue 360 lands back on hue 0 (cos/sin periodic).
        for (idx in 0 until 20) assertEquals(h0[idx], h360[idx], 1e-3f, "cell $idx")
        // hue 0 is exactly identity on the RGB diagonal.
        assertEquals(1f, h0.at(0, 0), eps)
        assertEquals(1f, h0.at(1, 1), eps)
        assertEquals(1f, h0.at(2, 2), eps)
    }

    @Test
    fun hue_preservesLuminanceOfGrey() {
        // Rotating hue must leave a neutral grey unchanged (luminance-preserving): each output row's
        // weights sum to 1, so grey (r=g=b=v) maps to v.
        val m = buildColorMatrixArray(OGLookSpec(hue = 90f))
        for (i in 0 until 3) {
            val rowSum = m.at(i, 0) + m.at(i, 1) + m.at(i, 2)
            assertEquals(1f, rowSum, 1e-3f, "row $i weights sum to 1")
        }
    }

    // ── concat ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun concat_withIdentity_isNoOp() {
        val sat = saturationMatrix(1.5f)
        val id = buildColorMatrixArray(OGLookSpec())
        val a = concat(sat, id)
        val b = concat(id, sat)
        for (idx in 0 until 20) {
            assertEquals(sat[idx], a[idx], eps, "concat(sat, id) cell $idx")
            assertEquals(sat[idx], b[idx], eps, "concat(id, sat) cell $idx")
        }
    }

    @Test
    fun multipleKnobs_composeWithoutNaN() {
        val m = buildColorMatrixArray(
            OGLookSpec(brightness = 0.1f, contrast = 1.2f, saturation = 1.3f, temperature = 0.2f, tint = -0.1f, hue = 30f),
        )
        assertEquals(20, m.size)
        m.forEach { assertTrue(it.isFinite(), "finite cell") }
        assertEquals(1f, m.at(3, 3), eps) // alpha row stays identity-ish on the diagonal
    }

    // ── blend-mode mapping ────────────────────────────────────────────────────────────────────

    @Test
    fun blendMode_mapsToCompose() {
        assertEquals(BlendMode.SrcOver, OGBlendMode.NORMAL.toBlendMode())
        assertEquals(BlendMode.Multiply, OGBlendMode.MULTIPLY.toBlendMode())
        assertEquals(BlendMode.Screen, OGBlendMode.SCREEN.toBlendMode())
        assertEquals(BlendMode.Plus, OGBlendMode.PLUS.toBlendMode())
    }

    // ── codec ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun encodeDecode_roundTrips() {
        val spec = OGLookSpec(name = "sunset", temperature = 0.3f, saturation = 1.2f, contrast = 1.1f)
        val round = OGLooks.decodeSpec(OGLooks.encode(spec))
        assertEquals(spec, round)
    }

    @Test
    fun decodeSpec_toleratesFencesAndProse() {
        val spec = OGLooks.decodeSpec(
            """
            Sure, here's your look:
            ```json
            {"name":"warm","temperature":0.4,"saturation":1.1}
            ```
            """.trimIndent(),
        )
        assertEquals("warm", spec.name)
        assertEquals(0.4f, spec.temperature, eps)
        assertEquals(1.1f, spec.saturation, eps)
        assertEquals(1f, spec.contrast, eps) // omitted field → neutral default
    }

    @Test
    fun decodeOrNull_returnsNullOnGarbage() {
        assertNull(OGLooks.decodeOrNull("not json at all"))
    }

    @Test
    fun presets_areKnownAndNonIdentity() {
        assertEquals(setOf("warm", "cool", "noir", "faded", "vivid"), OGLooks.presets.keys)
        OGLooks.presets.forEach { (name, spec) ->
            assertFalse(spec.isIdentity, "$name should change something")
            // every preset survives an encode/decode round-trip
            assertEquals(spec, OGLooks.decodeSpec(OGLooks.encode(spec)))
        }
        assertNotEquals(OGLooks.preset("noir"), OGLooks.preset("vivid"))
        assertNull(OGLooks.preset("nope"))
    }
}
