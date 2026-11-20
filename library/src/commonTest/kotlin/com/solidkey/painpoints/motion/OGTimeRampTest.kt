package com.solidkey.painpoints.motion

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OGTimeRampTest {

    private fun close(a: Float, b: Float, eps: Float = 1e-3f) = abs(a - b) <= eps

    @Test
    fun speedAt_before_trigger_is_one() {
        val spec = OGTimeRamp.BULLET_TIME
        assertEquals(1f, OGTimeRamp.speedAt(spec, -1f))
        assertEquals(1f, OGTimeRamp.speedAt(spec, -0.001f))
    }

    @Test
    fun speedAt_at_zero_is_one() {
        // At the very start of the ramp, speed is still 1
        assertTrue(close(1f, OGTimeRamp.speedAt(OGTimeRamp.BULLET_TIME, 0f)), "t=0 should be 1")
    }

    @Test
    fun speedAt_bottom_of_ramp_is_minSpeed() {
        val spec = OGTimeRamp.BULLET_TIME
        val atBottom = OGTimeRamp.speedAt(spec, spec.rampDownSec)
        assertTrue(close(spec.minSpeed, atBottom, eps = 0.01f), "at rampDownSec speed should be minSpeed, got $atBottom")
    }

    @Test
    fun speedAt_hold_phase_stays_at_minSpeed() {
        val spec = OGTimeRamp.BULLET_TIME
        val midHold = spec.rampDownSec + spec.holdSec / 2f
        assertTrue(close(spec.minSpeed, OGTimeRamp.speedAt(spec, midHold)), "hold phase should stay at minSpeed")
    }

    @Test
    fun speedAt_after_sequence_is_one() {
        val spec = OGTimeRamp.BULLET_TIME
        val total = OGTimeRamp.totalDurationSec(spec)
        assertEquals(1f, OGTimeRamp.speedAt(spec, total))
        assertEquals(1f, OGTimeRamp.speedAt(spec, total + 10f))
    }

    @Test
    fun speedAt_rampDown_is_monotonically_decreasing() {
        val spec = OGTimeRamp.BULLET_TIME
        var prev = 1f
        var t = 0.01f
        while (t <= spec.rampDownSec) {
            val v = OGTimeRamp.speedAt(spec, t)
            assertTrue(v <= prev + 1e-4f, "rampDown should be monotone at t=$t: $v > $prev")
            prev = v; t += 0.01f
        }
    }

    @Test
    fun speedAt_rampUp_is_monotonically_increasing() {
        val spec = OGTimeRamp.BULLET_TIME
        val start = spec.rampDownSec + spec.holdSec
        var prev = spec.minSpeed
        var t = start + 0.01f
        while (t <= OGTimeRamp.totalDurationSec(spec)) {
            val v = OGTimeRamp.speedAt(spec, t)
            assertTrue(v >= prev - 1e-4f, "rampUp should be monotone at t=$t: $v < $prev")
            prev = v; t += 0.01f
        }
    }

    @Test
    fun totalDurationSec_sum() {
        val spec = OGTimeRamp.BULLET_TIME
        assertTrue(close(spec.rampDownSec + spec.holdSec + spec.rampUpSec, OGTimeRamp.totalDurationSec(spec)))
    }

    @Test
    fun freeze_preset_reaches_zero() {
        val spec = OGTimeRamp.FREEZE
        assertEquals(0f, spec.minSpeed)
        assertTrue(close(0f, OGTimeRamp.speedAt(spec, spec.rampDownSec)))
    }

    @Test
    fun presets_exist_and_case_insensitive() {
        for (n in listOf("bullet_time", "quick_mo", "freeze", "focus")) assertNotNull(OGTimeRamp.preset(n), "preset $n")
        assertNotNull(OGTimeRamp.preset("BULLET_TIME"))
        assertNotNull(OGTimeRamp.preset("Freeze"))
        assertEquals(4, OGTimeRamp.presets.size)
    }

    @Test
    fun codec_round_trips() {
        val back = OGTimeRamp.decodeSpec(OGTimeRamp.encode(OGTimeRamp.BULLET_TIME))
        assertEquals(OGTimeRamp.BULLET_TIME, back)
    }

    @Test
    fun decode_bad_input_is_null_not_crash() {
        assertEquals(null, OGTimeRamp.decodeSpecOrNull("no json at all"))
    }

    // ── OGTimeRampValue ───────────────────────────────────────────────────────

    @Test
    fun value_starts_inactive_at_one() {
        val v = OGTimeRampValue(OGTimeRamp.BULLET_TIME)
        assertFalse(v.isActive)
        assertEquals(1f, v.timeScale)
    }

    @Test
    fun value_trigger_makes_active_and_ramps_down() {
        val v = OGTimeRampValue(OGTimeRamp.BULLET_TIME)
        v.trigger()
        assertTrue(v.isActive)
        // Advance through ramp-down
        repeat(30) { v.update(0.01f) }  // 0.3s > rampDownSec(0.25)
        assertTrue(v.timeScale < 0.3f, "should be near minSpeed after rampDown, got ${v.timeScale}")
    }

    @Test
    fun value_auto_resets_after_full_sequence() {
        val spec = OGTimeRamp.QUICK_MO  // shorter sequence for test speed
        val v = OGTimeRampValue(spec)
        v.trigger()
        val total = OGTimeRamp.totalDurationSec(spec)
        // Run past the full sequence
        var elapsed = 0f
        while (elapsed <= total + 0.1f) { v.update(0.05f); elapsed += 0.05f }
        assertFalse(v.isActive, "should be inactive after sequence completes")
        assertEquals(1f, v.timeScale)
    }

    @Test
    fun value_reset_cancels_mid_sequence() {
        val v = OGTimeRampValue(OGTimeRamp.BULLET_TIME)
        v.trigger()
        repeat(5) { v.update(0.05f) }  // mid ramp-down
        v.reset()
        assertFalse(v.isActive)
        assertEquals(1f, v.timeScale)
    }
}
