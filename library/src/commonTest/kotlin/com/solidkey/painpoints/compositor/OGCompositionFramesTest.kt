package com.solidkey.painpoints.compositor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure coverage of the frame-sequence *plan* ([frameTimesMs]) — no rendering, so it runs on JVM + iOS.
 * The actual per-frame bitmap rendering reuses [renderFrame] (already exercised by the GIF/MP4 exporters)
 * and is verified on-device in the demo's filmstrip.
 */
class OGCompositionFramesTest {

    private fun comp(durationMs: Long, fps: Int) =
        OGComposition(width = 8, height = 8, durationMs = durationMs, layers = emptyList(), fps = fps)

    @Test
    fun frameTimesCountMatchesFrameCount() {
        val c = comp(1000, 24)
        assertEquals(c.frameCount, c.frameTimesMs().size)
        assertEquals(24, c.frameCount)
    }

    @Test
    fun framesStartAtZeroAndIncreaseMonotonically() {
        val times = comp(2000, 30).frameTimesMs()
        assertEquals(0L, times.first())
        for (i in 1 until times.size) {
            assertTrue(times[i] > times[i - 1], "frame $i time ${times[i]} not after ${times[i - 1]}")
        }
    }

    @Test
    fun zeroDurationStillYieldsOneFrame() {
        val times = comp(0, 24).frameTimesMs()
        assertEquals(1, times.size)
        assertEquals(0L, times.first())
    }

    @Test
    fun frameSpacingFollowsFps() {
        val times = comp(1000, 10).frameTimesMs() // 10 frames, 100ms apart
        assertEquals(10, times.size)
        assertEquals(100L, times[1])
        assertEquals(900L, times.last())
    }
}
