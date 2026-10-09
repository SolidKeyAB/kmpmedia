package com.solidkey.painpoints.motion

import kotlin.test.Test
import kotlin.test.assertTrue

class OGStaggerTest {

    private fun close(a: Float, b: Float, eps: Float = 1e-4f) = kotlin.math.abs(a - b) <= eps

    @Test
    fun singleElement_isJustTheEasedTimeline() {
        assertTrue(close(0f, OGStagger.progressFor(0, 1, 0f)))
        assertTrue(close(1f, OGStagger.progressFor(0, 1, 1f)))
    }

    @Test
    fun zeroStagger_everyElementMovesTogether() {
        val n = 5
        for (t in listOf(0f, 0.3f, 0.6f, 1f)) {
            val a = OGStagger.progressFor(0, n, t, stagger = 0f, easing = OGEasing.LINEAR)
            for (i in 1 until n) {
                val b = OGStagger.progressFor(i, n, t, stagger = 0f, easing = OGEasing.LINEAR)
                assertTrue(close(a, b), "element $i should equal element 0 at t=$t")
            }
            assertTrue(close(t, a), "linear + no stagger == t")
        }
    }

    @Test
    fun endpoints_allZeroAtStart_allOneAtEnd() {
        val n = 6
        for (i in 0 until n) {
            assertTrue(close(0f, OGStagger.progressFor(i, n, 0f, stagger = 0.6f, easing = OGEasing.LINEAR)), "t=0 element $i")
            assertTrue(close(1f, OGStagger.progressFor(i, n, 1f, stagger = 0.6f, easing = OGEasing.LINEAR)), "t=1 element $i")
        }
    }

    @Test
    fun firstElementLeads_reverseFlipsIt() {
        val n = 6
        val t = 0.3f
        val first = OGStagger.progressFor(0, n, t, stagger = 0.6f, easing = OGEasing.LINEAR)
        val last = OGStagger.progressFor(n - 1, n, t, stagger = 0.6f, easing = OGEasing.LINEAR)
        assertTrue(first > last, "first index should lead with reverse=false")

        val firstR = OGStagger.progressFor(0, n, t, stagger = 0.6f, easing = OGEasing.LINEAR, reverse = true)
        val lastR = OGStagger.progressFor(n - 1, n, t, stagger = 0.6f, easing = OGEasing.LINEAR, reverse = true)
        assertTrue(lastR > firstR, "last index should lead with reverse=true")
    }

    @Test
    fun reverse_isIndexMirror() {
        val n = 7
        val t = 0.4f
        for (i in 0 until n) {
            val rev = OGStagger.progressFor(i, n, t, stagger = 0.5f, easing = OGEasing.LINEAR, reverse = true)
            val mirror = OGStagger.progressFor(n - 1 - i, n, t, stagger = 0.5f, easing = OGEasing.LINEAR, reverse = false)
            assertTrue(close(rev, mirror), "reverse of $i should match forward of ${n - 1 - i}")
        }
    }

    @Test
    fun allEasings_mapEndpointsToZeroAndOne() {
        for (e in OGEasing.entries) {
            assertTrue(close(0f, e.ease(0f)), "$e ease(0)")
            assertTrue(close(1f, e.ease(1f)), "$e ease(1)")
        }
    }

    @Test
    fun easingNames_parseLeniently() {
        assertTrue(ogEasingOf("easeOut") == OGEasing.EASE_OUT)
        assertTrue(ogEasingOf("ease_in_out") == OGEasing.EASE_IN_OUT)
        assertTrue(ogEasingOf("OVERSHOOT") == OGEasing.OVERSHOOT)
        assertTrue(ogEasingOf("nonsense") == OGEasing.EASE_IN_OUT, "unknown falls back")
    }

    @Test
    fun overshoot_exceedsOneMidCurve() {
        // The whole point of OVERSHOOT: it springs past the target before settling.
        assertTrue(OGEasing.OVERSHOOT.ease(0.75f) > 1f, "overshoot should exceed 1 mid-curve")
    }

    @Test
    fun easeInOut_isContinuousAtMidpoint() {
        assertTrue(close(0.5f, OGEasing.EASE_IN_OUT.ease(0.5f)), "value at 0.5")
        val lo = OGEasing.EASE_IN_OUT.ease(0.499f)
        val hi = OGEasing.EASE_IN_OUT.ease(0.501f)
        assertTrue(kotlin.math.abs(hi - lo) < 0.02f, "no jump across the branch at 0.5")
    }

    @Test
    fun startTimes_areEvenlySpacedAndOrdered() {
        // Element i begins moving at t = (i/(n-1)) * stagger — evenly spaced, in index order.
        val n = 5
        val f = 0.8f
        for (i in 0 until n) {
            val start = (i / (n - 1f)) * f
            if (start > 0f) {
                assertTrue(close(0f, OGStagger.progressFor(i, n, start - 1e-3f, f, OGEasing.LINEAR)), "element $i not moving before its start $start")
            }
            val justAfter = (start + 1e-3f).coerceAtMost(1f)
            assertTrue(OGStagger.progressFor(i, n, justAfter, f, OGEasing.LINEAR) > 0f, "element $i has started just after $start")
        }
    }

    @Test
    fun progress_isMonotonicInTime() {
        val n = 4
        for (i in 0 until n) {
            var prev = -1f
            var t = 0f
            while (t <= 1f) {
                val p = OGStagger.progressFor(i, n, t, stagger = 0.6f, easing = OGEasing.EASE_OUT)
                assertTrue(p >= prev - 1e-5f, "element $i should not go backwards at t=$t")
                prev = p
                t += 0.05f
            }
        }
    }

    @Test
    fun outOfRangeInputs_areSafe() {
        val n = 5
        // t beyond range clamps; index out of bounds is coerced into the valid order range.
        assertTrue(close(1f, OGStagger.progressFor(0, n, 5f, 0.6f, OGEasing.LINEAR)), "t>1 clamps to end")
        assertTrue(close(0f, OGStagger.progressFor(0, n, -5f, 0.6f, OGEasing.LINEAR)), "t<0 clamps to start")
        val oob = OGStagger.progressFor(99, n, 0.5f, 0.6f, OGEasing.LINEAR)
        assertTrue(oob in 0f..1f, "out-of-range index stays in 0..1")
    }

    @Test
    fun spec_progressMatchesObject() {
        val spec = OGStaggerSpec(stagger = 0.7f, easing = "easeOut", reverse = true)
        val viaSpec = spec.progressFor(2, 5, 0.4f)
        val viaObj = OGStagger.progressFor(2, 5, 0.4f, 0.7f, OGEasing.EASE_OUT, reverse = true)
        assertTrue(close(viaSpec, viaObj), "spec should delegate to the object")
    }
}
