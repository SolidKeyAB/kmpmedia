package com.solidkey.painpoints.puppet

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers the puppet codec ([OGPuppet]) and the pure, deterministic pose math ([OGPuppetRig]). */
class OGPuppetTest {

    private val sample = OGPuppetSpec(
        base = "chipmunk_body",
        aspect = 0.7f,
        grounded = true,
        parts = listOf(
            OGPuppetPart("chipmunk_arm_r", pivotX = 0.25f, pivotY = 0.48f, swingDeg = 4f, speed = 0.7f, phase = 2.6f),
            OGPuppetPart("chipmunk_head", pivotX = 0.56f, pivotY = 0.42f, swingDeg = 2f, head = true),
            OGPuppetPart("chipmunk_eye_l", pivotX = 0.73f, pivotY = 0.22f, gaze = 0.028f),
        ),
    )

    @Test
    fun codec_roundTrips() {
        val json = OGPuppet.encode(sample)
        val back = OGPuppet.decode(json)
        assertEquals(sample, back)
    }

    @Test
    fun decode_toleratesProseAndFences() {
        val wrapped = "sure! here it is:\n```json\n" + OGPuppet.encode(sample) + "\n```\n"
        assertEquals(sample, OGPuppet.decode(wrapped))
    }

    @Test
    fun decodeOrNull_isNullOnGarbage() {
        assertNull(OGPuppet.decodeOrNull("not json at all"))
    }

    @Test
    fun headPart_isTheOneFlaggedHead() {
        assertEquals("chipmunk_head", sample.headPart?.res)
        assertNull(sample.copy(parts = sample.parts.filter { !it.head }).headPart)
    }

    @Test
    fun effectiveLook_usesHostValueWhenGiven_elseIdleGlance() {
        assertEquals(0.5f, OGPuppetRig.effectiveLook(0.5f, 1.23f))
        assertEquals(1f, OGPuppetRig.effectiveLook(2f, 0f)) // clamped
        // null → a bounded idle glance within ±0.6
        assertTrue(abs(OGPuppetRig.effectiveLook(null, 3.3f)) <= 0.6001f)
    }

    @Test
    fun groundedFigure_neverFloats_andBendsTowardLook() {
        assertEquals(0f, OGPuppetRig.figureFloat(sample, 2f)) // grounded: no vertical hover
        // Bending more toward a hard-right look than at centre.
        val atCentre = OGPuppetRig.figureRotation(sample, 0f, 0f)
        val atRight = OGPuppetRig.figureRotation(sample, 0f, 1f)
        assertTrue(atRight > atCentre)
    }

    @Test
    fun flyingFigure_floatsAndStaysBounded() {
        val flyer = sample.copy(grounded = false, hoverAmp = 0.05f)
        var maxAbs = 0f
        var i = 0
        while (i < 400) { maxAbs = maxOf(maxAbs, abs(OGPuppetRig.figureFloat(flyer, i * 0.05f))); i++ }
        assertTrue(maxAbs > 0f && maxAbs <= 0.05001f)
    }

    @Test
    fun headTurns_butEyesOnlyGaze() {
        val head = sample.parts.first { it.head }
        val eye = sample.parts.first { it.gaze > 0f }
        // Head mirror-flips with the look sign; a plain part stays width 1.
        assertTrue(OGPuppetRig.partScaleX(head, 1f) > 0f)
        assertTrue(OGPuppetRig.partScaleX(head, -1f) < 0f)
        assertEquals(1f, OGPuppetRig.partScaleX(eye, -1f))
        // The eye slides toward the look; a hard-right look pushes it right by +gaze.
        assertEquals(eye.gaze, OGPuppetRig.partOffsetX(eye, 1f))
        assertEquals(0f, OGPuppetRig.partOffsetX(eye, 0f))
    }

    @Test
    fun partAngle_headAddsLookTurn_overDampedIdle() {
        val head = sample.parts.first { it.head }
        // At t=0 the idle term for the head is swingDeg*0.25*sin(phase=0)=0, so the angle is purely the look-turn.
        val a = OGPuppetRig.partAngle(sample, head, 0f, 1f)
        assertEquals(sample.lookTurnDeg, a, 1e-4f)
    }
}
