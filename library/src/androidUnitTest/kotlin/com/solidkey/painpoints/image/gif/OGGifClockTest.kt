package com.solidkey.painpoints.image.gif

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [OGGifClock] — the platform-free frame-timing engine that drives animated GIF
 * playback. Uses virtual time so the assertions are exact and instant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OGGifClockTest {

    @Test
    fun playsThroughEveryFrameOnceThenStops() = runTest {
        val clock = OGGifClock(frameCount = 3, frameDurationsMs = listOf(40, 60, 100))
        val job = launch { clock.run(loop = false, speed = 1f) }

        advanceUntilIdle()

        assertTrue(job.isCompleted, "non-looping run should finish")
        assertEquals(2, clock.currentIndex, "ends on the last frame")
        assertEquals(200L, testScheduler.currentTime, "wall-clock == sum of frame durations (40+60+100)")
    }

    @Test
    fun advancesTheFrameIndexAsTimePasses() = runTest {
        val clock = OGGifClock(frameCount = 3, frameDurationsMs = listOf(100, 100, 100))
        val job = launch { clock.run(loop = false, speed = 1f) }

        runCurrent()
        assertEquals(0, clock.currentIndex, "starts on the first frame")

        advanceTimeBy(120); runCurrent()
        assertEquals(1, clock.currentIndex, "advances after the first frame's duration")

        advanceTimeBy(100); runCurrent()
        assertEquals(2, clock.currentIndex, "advances again")

        job.cancel()
    }

    @Test
    fun speedGreaterThanOnePlaysFaster() = runTest {
        val clock = OGGifClock(frameCount = 2, frameDurationsMs = listOf(100, 100))
        val job = launch { clock.run(loop = false, speed = 2f) }

        advanceUntilIdle()

        assertEquals(100L, testScheduler.currentTime, "2x speed halves the total duration (200/2)")
        job.cancel()
    }

    @Test
    fun nonPositiveSpeedIsTreatedAsNormalSpeed() = runTest {
        val clock = OGGifClock(frameCount = 2, frameDurationsMs = listOf(100, 100))
        val job = launch { clock.run(loop = false, speed = 0f) }

        advanceUntilIdle()

        assertEquals(200L, testScheduler.currentTime, "speed <= 0 falls back to 1x")
        job.cancel()
    }

    @Test
    fun zeroDurationFramesFallBackToTheDefault() = runTest {
        val clock = OGGifClock(frameCount = 2, frameDurationsMs = listOf(0, 0))
        val job = launch { clock.run(loop = false, speed = 1f) }

        advanceUntilIdle()

        assertEquals(
            2L * OGGifClock.DEFAULT_FRAME_MS,
            testScheduler.currentTime,
            "0ms frames use DEFAULT_FRAME_MS"
        )
        job.cancel()
    }

    @Test
    fun singleFrameReturnsImmediatelyWithoutLooping() = runTest {
        val clock = OGGifClock(frameCount = 1, frameDurationsMs = listOf(100))
        val job = launch { clock.run(loop = true, speed = 1f) }

        advanceUntilIdle()

        assertTrue(job.isCompleted, "a single-frame image must not spin forever")
        assertEquals(0, clock.currentIndex)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun loopingKeepsCyclingBackToTheFirstFrame() = runTest {
        val clock = OGGifClock(frameCount = 2, frameDurationsMs = listOf(100, 100))
        val job = launch { clock.run(loop = true, speed = 1f) }

        // Two full frames = one loop; a third step should wrap back to frame 0.
        advanceTimeBy(120); runCurrent()
        assertEquals(1, clock.currentIndex)
        advanceTimeBy(100); runCurrent()
        assertEquals(0, clock.currentIndex, "wraps back to the first frame")

        assertTrue(job.isActive, "looping run keeps running")
        job.cancel()
    }
}
