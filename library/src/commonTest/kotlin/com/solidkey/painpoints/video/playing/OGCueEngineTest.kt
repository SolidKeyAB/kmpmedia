package com.solidkey.painpoints.video.playing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the cue/timer dispatch behind [OGAVPlayer]'s `cues`: the pure crossing/range helpers
 * ([crossedForward], [inRange]) and the stateful [OGCueEngine] (fire-once point crossings, range
 * enter/exit exactly once, seek-over, live cue-list swaps, and callback-exception isolation). The
 * platform actuals only feed positions into [OGCueEngine.onTick], so pinning it here pins the
 * cross-platform behaviour without a running player.
 */
class OGCueEngineTest {

    // ---- pure helpers ----

    @Test
    fun crossedForwardOnlyOnForwardCrossing() {
        assertTrue(crossedForward(prevMs = 2_900, curMs = 3_100, atMs = 3_000))
        assertTrue(crossedForward(prevMs = 2_999, curMs = 3_000, atMs = 3_000)) // inclusive at boundary
        assertFalse(crossedForward(prevMs = 3_000, curMs = 3_200, atMs = 3_000)) // already past
        assertFalse(crossedForward(prevMs = 1_000, curMs = 2_000, atMs = 3_000)) // not reached
        assertFalse(crossedForward(prevMs = -1, curMs = 3_500, atMs = 3_000))    // no valid previous (first tick)
    }

    @Test
    fun inRangeIsHalfOpen() {
        assertTrue(inRange(posMs = 3_000, fromMs = 3_000, toMs = 5_000))  // start inclusive
        assertTrue(inRange(posMs = 4_999, fromMs = 3_000, toMs = 5_000))
        assertFalse(inRange(posMs = 5_000, fromMs = 3_000, toMs = 5_000)) // end exclusive
        assertFalse(inRange(posMs = 2_999, fromMs = 3_000, toMs = 5_000))
    }

    // ---- point cues ----

    @Test
    fun pointCueFiresOnceWhenCrossed() {
        var fired = 0
        val engine = OGCueEngine(listOf(OGCue.at(3_000) { fired++ }))
        engine.onTick(1_000); assertEquals(0, fired)
        engine.onTick(2_900); assertEquals(0, fired)
        engine.onTick(3_100); assertEquals(1, fired)  // crossed
        engine.onTick(4_000); assertEquals(1, fired)  // not again
        engine.onTick(5_000); assertEquals(1, fired)
    }

    @Test
    fun pointCueRefiresAfterSeekingBackThenForward() {
        var fired = 0
        val engine = OGCueEngine(listOf(OGCue.at(3_000) { fired++ }))
        engine.onTick(2_900); engine.onTick(3_100); assertEquals(1, fired)
        engine.onTick(1_000)  // seek back before it
        engine.onTick(3_100)  // cross forward again
        assertEquals(2, fired)
    }

    @Test
    fun pointCueFiresWhenSeekJumpsOverIt() {
        var fired = 0
        val engine = OGCueEngine(listOf(OGCue.at(3_000) { fired++ }))
        engine.onTick(1_000)
        engine.onTick(8_000)  // seek jumped from 1s to 8s, over the 3s cue
        assertEquals(1, fired)
    }

    @Test
    fun pointCueNeverFiresIfNotReached() {
        var fired = 0
        val engine = OGCueEngine(listOf(OGCue.at(9_000) { fired++ }))
        engine.onTick(1_000); engine.onTick(2_000); engine.onTick(3_000)
        assertEquals(0, fired)
    }

    // ---- range cues ----

    @Test
    fun rangeCueEntersAndExitsOnce() {
        var enters = 0
        var exits = 0
        val engine = OGCueEngine(listOf(OGCue.range(3_000, 5_000, onEnter = { enters++ }, onExit = { exits++ })))
        engine.onTick(2_000); assertEquals(0, enters); assertEquals(0, exits)
        engine.onTick(3_500); assertEquals(1, enters); assertEquals(0, exits) // entered
        engine.onTick(4_500); assertEquals(1, enters); assertEquals(0, exits) // still inside, no re-enter
        engine.onTick(6_000); assertEquals(1, enters); assertEquals(1, exits) // exited
        engine.onTick(7_000); assertEquals(1, enters); assertEquals(1, exits) // stays out
    }

    @Test
    fun rangeCueStartingAtZeroEntersOnFirstTick() {
        var enters = 0
        val engine = OGCueEngine(listOf(OGCue.range(0, 2_000, onEnter = { enters++ })))
        engine.onTick(0); assertEquals(1, enters) // range from 0 fires immediately (no previous needed)
    }

    // ---- setCues ----

    @Test
    fun removingRangeCueWhileInsideDoesNotFireSpuriousExit() {
        var exits = 0
        val cue = OGCue.range(3_000, 9_000, onExit = { exits++ })
        val engine = OGCueEngine(listOf(cue))
        engine.onTick(4_000) // now inside
        engine.setCues(emptyList()) // remove it while inside
        engine.onTick(10_000)       // would have been an exit if still tracked
        assertEquals(0, exits)
    }

    @Test
    fun multipleCuesAreIndependent() {
        var a = 0
        var b = 0
        val engine = OGCueEngine(
            listOf(
                OGCue.at(2_000) { a++ },
                OGCue.range(4_000, 6_000, onEnter = { b++ }),
            )
        )
        engine.onTick(1_000)
        engine.onTick(2_500); assertEquals(1, a); assertEquals(0, b)
        engine.onTick(4_500); assertEquals(1, a); assertEquals(1, b)
    }

    // ---- robustness ----

    @Test
    fun aThrowingCueDoesNotStopOtherCues() {
        var good = 0
        val engine = OGCueEngine(
            listOf(
                OGCue.at(3_000) { throw IllegalStateException("boom") },
                OGCue.at(3_000) { good++ },
            )
        )
        engine.onTick(2_000)
        engine.onTick(3_100) // first cue throws (swallowed), second must still fire
        assertEquals(1, good)
    }

    // ---- construction validation ----

    @Test
    fun rangeCueRequiresPositiveWidth() {
        assertFailsWith<IllegalArgumentException> { OGCue.range(5_000, 5_000) }
        assertFailsWith<IllegalArgumentException> { OGCue.range(5_000, 4_000) }
    }
}
