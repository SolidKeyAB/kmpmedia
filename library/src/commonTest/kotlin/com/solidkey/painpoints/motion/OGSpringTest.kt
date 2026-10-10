package com.solidkey.painpoints.motion

import kotlin.test.Test
import kotlin.test.assertTrue

class OGSpringTest {

    private fun close(a: Float, b: Float, eps: Float = 1e-3f) = kotlin.math.abs(a - b) <= eps

    @Test
    fun valueAt_startAndSettle() {
        val spec = OGSpringSpec(stiffness = 200f, dampingRatio = 0.6f)
        assertTrue(close(0f, OGSpring.valueAt(spec, 0f, from = 0f, to = 1f)), "t=0 is from")
        // Given enough time it settles on the target regardless of damping.
        assertTrue(close(1f, OGSpring.valueAt(spec, 5f, from = 0f, to = 1f), eps = 1e-2f), "settles to target")
    }

    @Test
    fun underdamped_overshootsPastTarget() {
        val spec = OGSpringSpec(stiffness = 200f, dampingRatio = 0.25f)
        var maxV = Float.NEGATIVE_INFINITY
        var t = 0f
        while (t <= 2f) { maxV = maxOf(maxV, OGSpring.valueAt(spec, t, 0f, 1f)); t += 0.01f }
        assertTrue(maxV > 1.01f, "a bouncy spring must overshoot past the target, got peak $maxV")
    }

    @Test
    fun criticallyAndOverdamped_neverOvershoot() {
        for (z in listOf(1f, 1.8f)) {
            val spec = OGSpringSpec(stiffness = 200f, dampingRatio = z)
            var maxV = Float.NEGATIVE_INFINITY
            var t = 0f
            while (t <= 4f) { maxV = maxOf(maxV, OGSpring.valueAt(spec, t, 0f, 1f)); t += 0.01f }
            assertTrue(maxV <= 1f + 1e-3f, "damping $z must not overshoot, got peak $maxV")
        }
    }

    @Test
    fun velocityAt_startsAtInitialVelocity_endsAtRest() {
        val spec = OGSpringSpec(stiffness = 150f, dampingRatio = 0.5f)
        assertTrue(close(3f, OGSpring.velocityAt(spec, 0f, 0f, 1f, velocity = 3f)), "t=0 keeps initial velocity")
        assertTrue(close(0f, OGSpring.velocityAt(spec, 6f, 0f, 1f), eps = 1e-2f), "velocity decays to 0 at rest")
    }

    @Test
    fun liveStepper_convergesToTarget() {
        val spec = OGSpringSpec(stiffness = 200f, dampingRatio = 0.7f)
        val v = OGSpringValue(0f)
        repeat(400) { v.update(target = 1f, dt = 1f / 60f, spec = spec) }
        assertTrue(close(1f, v.value, eps = 1e-2f), "stepper settles on target, got ${v.value}")
        assertTrue(close(0f, v.velocity, eps = 1e-2f), "velocity settles to 0")
    }

    @Test
    fun stepper_matchesClosedForm_atSampledTimes() {
        // Advancing the stepper in small steps should track the closed-form response.
        val spec = OGSpringSpec(stiffness = 180f, dampingRatio = 0.4f)
        val v = OGSpringValue(0f)
        val dt = 1f / 240f
        var t = 0f
        repeat(120) {
            v.update(1f, dt, spec)
            t += dt
            val closed = OGSpring.valueAt(spec, t, 0f, 1f)
            assertTrue(kotlin.math.abs(v.value - closed) < 2e-2f, "stepper vs closed-form drift at t=$t: ${v.value} vs $closed")
        }
    }

    @Test
    fun deterministic() {
        val spec = OGSpringSpec(stiffness = 333f, dampingRatio = 0.55f)
        assertTrue(OGSpring.valueAt(spec, 0.37f, 0f, 1f) == OGSpring.valueAt(spec, 0.37f, 0f, 1f))
    }

    @Test
    fun snapTo_resetsVelocity() {
        val v = OGSpringValue(5f, 9f)
        v.snapTo(2f)
        assertTrue(close(2f, v.value) && close(0f, v.velocity))
    }
}
