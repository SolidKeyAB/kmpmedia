package com.solidkey.painpoints.motion

import kotlin.test.Test
import kotlin.test.assertTrue

/** Follow-through chain, squash & stretch, and idle sway. */
class OGDynamicsCoreTest {

    private fun close(a: Float, b: Float, eps: Float = 1e-3f) = kotlin.math.abs(a - b) <= eps

    @Test
    fun followChain_lagsThenConvergesToStaticLeader() {
        val chain = OGFollowChain(count = 4, spec = OGSpringSpec(stiffness = 200f, dampingRatio = 0.7f))
        chain.reset(0f, 0f)
        // One step after the leader jumps: links trail (not snapped to the leader), later links lag more.
        chain.update(10f, 0f, 1f / 60f)
        assertTrue(chain.x(0) > 0f && chain.x(0) < 10f, "link 0 lags the leader")
        assertTrue(chain.x(3) <= chain.x(0) + 1e-4f, "later links lag at least as much as earlier ones")
        // Held on the leader long enough, the whole chain converges.
        repeat(600) { chain.update(10f, 0f, 1f / 60f) }
        for (i in 0 until 4) assertTrue(close(10f, chain.x(i), eps = 1e-1f), "link $i converges to the leader")
    }

    @Test
    fun followChain_zeroCount_isSafe() {
        val chain = OGFollowChain(count = 0)
        chain.update(5f, 5f, 1f / 60f) // must not throw
    }

    @Test
    fun squash_isVolumePreserving() {
        for (speed in listOf(-0.8f, -0.2f, 0f, 0.3f, 0.9f)) {
            val s = OGSquash.fromSpeed(speed, intensity = 0.4f)
            assertTrue(close(1f, s.scaleX * s.scaleY, eps = 1e-3f), "volume preserved at speed $speed")
        }
    }

    @Test
    fun squash_zeroSpeedIsIdentity_positiveStretchesChosenAxis() {
        val idle = OGSquash.fromSpeed(0f)
        assertTrue(close(1f, idle.scaleX) && close(1f, idle.scaleY), "no speed = identity")
        val fast = OGSquash.fromSpeed(1f, intensity = 0.3f, vertical = true)
        assertTrue(fast.scaleY > 1f && fast.scaleX < 1f, "positive speed stretches Y, squashes X")
    }

    @Test
    fun sway_isDeterministicAndBounded() {
        val sway = OGSway(amplitude = 2f, frequency = 0.5f, octaves = 3, seed = 7)
        assertTrue(sway.value(1.3f) == sway.value(1.3f), "deterministic")
        var t = 0f
        while (t <= 10f) {
            val o = sway.offset(t)
            assertTrue(o.x.isFinite() && o.y.isFinite(), "finite at t=$t")
            assertTrue(kotlin.math.abs(sway.value(t)) <= 2f + 1e-3f, "within amplitude at t=$t")
            t += 0.25f
        }
    }
}
