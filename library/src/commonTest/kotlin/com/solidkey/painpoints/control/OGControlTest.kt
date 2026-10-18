package com.solidkey.painpoints.control

import com.solidkey.painpoints.ai.OGFillSpec
import com.solidkey.painpoints.ai.OGStopSpec
import com.solidkey.painpoints.gesture.OGPressEffect
import com.solidkey.painpoints.style.OGStyleOp
import com.solidkey.painpoints.style.OGStyleSpec
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OGControlTest {

    // ── codec round-trip ────────────────────────────────────────────────────────────────────

    @Test
    fun codec_roundTripsDefaults() {
        val spec = OGControlSpec()
        assertEquals(spec, OGControls.decodeSpec(OGControls.encode(spec)))
    }

    @Test
    fun codec_roundTripsRichSpec() {
        val spec = OGControlSpec(
            cornerRadius = 14f,
            height = 52f,
            fontSize = 15f,
            pressEffect = "brutalist",
            disabledAlpha = 0.3f,
            outline = OGControlOutline(width = 3f, inset = 4f, amplitude = 0.05f, detail = 5, inkAlpha = 0.6f),
            labelStyle = OGStyleSpec(ops = listOf(OGStyleOp(op = "roughen", amplitude = 0.03f, detail = 4))),
            fill = OGFillSpec(type = "linear", stops = listOf(OGStopSpec(0f, "#7CB342"), OGStopSpec(1f, "#558B2F"))),
            garnish = OGGarnishSpec(edge = "bottom", color = "#FFFFFF", drips = 3),
        )
        assertEquals(spec, OGControls.decodeSpec(OGControls.encode(spec)))
    }

    @Test
    fun codec_roundTripsOutlineNull() {
        val spec = OGControlSpec(outline = null, labelStyle = OGStyleSpec())
        val back = OGControls.decodeSpec(OGControls.encode(spec))
        assertNull(back.outline)
        assertEquals(spec, back)
    }

    @Test
    fun codec_isLenient_fencesAndUnknownKeys() {
        val raw = """
            Sure! here is your control:
            ```json
            { "cornerRadius": 20, "mysteryKnob": 7, "pressEffect": "dim" }
            ```
        """.trimIndent()
        val spec = OGControls.decodeSpec(raw)
        assertEquals(20f, spec.cornerRadius)
        assertEquals("dim", spec.pressEffect)
    }

    @Test
    fun presets_andGarnishes_resolve() {
        assertNull(OGControls.preset("nope"))
        assertEquals(null, OGControls.presets["sketch"]!!.garnish)
        assertNotEquals(null, OGControls.preset("slime")!!.garnish)
        assertNull(OGControls.presets["plain"]!!.outline)
        assertEquals("top", OGControls.garnish("snow")!!.edge)
    }

    // ── press-effect mapping ─────────────────────────────────────────────────────────────────

    @Test
    fun pressEffect_mapsByName() {
        assertTrue(ogControlPressEffect("scale") is OGPressEffect.Scale)
        assertTrue(ogControlPressEffect("DIM") is OGPressEffect.Dim)
        assertTrue(ogControlPressEffect(" brutalist ") is OGPressEffect.Brutalist)
        assertTrue(ogControlPressEffect("none") is OGPressEffect.None)
        assertTrue(ogControlPressEffect("bogus") is OGPressEffect.Scale) // falls back
    }

    // ── rounded-rect outline geometry ────────────────────────────────────────────────────────

    @Test
    fun roundedRect_staysInsideInsetBox() {
        val w = 120f; val h = 46f; val inset = 3.5f
        val pts = roundedRectOutline(w, h, cornerRadius = 11f, inset = inset)
        assertEquals(20, pts.size) // (cornerSteps 4 + 1) * 4 corners
        pts.forEach { p ->
            assertTrue(p.x >= inset - 1e-3f && p.x <= w - inset + 1e-3f, "x ${p.x} out of box")
            assertTrue(p.y >= inset - 1e-3f && p.y <= h - inset + 1e-3f, "y ${p.y} out of box")
        }
    }

    @Test
    fun roundedRect_clampsRadiusToHalfShortSide() {
        // A huge radius on a short control must clamp (no NaN, stays in box).
        val pts = roundedRectOutline(200f, 40f, cornerRadius = 999f, inset = 0f)
        pts.forEach { p ->
            assertTrue(p.x in -1e-3f..200.001f)
            assertTrue(p.y in -1e-3f..40.001f)
        }
    }

    @Test
    fun outline_isDeterministicPerSeed() {
        fun make(seed: Int) = ogControlOutline(120f, 46f, cornerRadius = 11f, inset = 3.5f, amplitude = 1.4f, detail = 7, seed = seed)
        val a1 = make(1); val a1b = make(1); val a2 = make(2)
        assertEquals(a1, a1b)             // same seed → identical
        assertNotEquals(a1, a2)           // different seed → different edge
        assertTrue(a1.size > 20)          // roughen subdivided the base outline
    }

    // ── garnish ring geometry ────────────────────────────────────────────────────────────────

    @Test
    fun garnishRing_hasExpectedShapeAndIsDeterministic() {
        val spec = OGGarnishSpec()
        val r1 = ogGarnishRing(spec, 120f, 46f, seed = 3, timeMs = 0L, samples = 26)
        val r1b = ogGarnishRing(spec, 120f, 46f, seed = 3, timeMs = 0L, samples = 26)
        assertEquals(29, r1.size)   // 2 straight-edge corners + (samples + 1) drippy points
        assertEquals(r1, r1b)       // deterministic at a fixed time + seed
        r1.forEach { p -> assertTrue(p.x.isFinite() && p.y.isFinite()) }
    }

    @Test
    fun garnishRing_bottomIsTopMirrored() {
        val h = 46f
        val top = ogGarnishRing(OGGarnishSpec(edge = "top"), 120f, h, seed = 2, timeMs = 0L)
        val bottom = ogGarnishRing(OGGarnishSpec(edge = "bottom"), 120f, h, seed = 2, timeMs = 0L)
        assertEquals(top.size, bottom.size)
        for (i in top.indices) {
            assertEquals(top[i].x, bottom[i].x, 1e-3f)
            assertTrue(abs((h - top[i].y) - bottom[i].y) < 1e-3f, "bottom should mirror top across height")
        }
    }
}
