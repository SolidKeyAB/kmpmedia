package com.solidkey.painpoints.video.playing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the pure, platform-independent parts of the interactive-video foundation:
 * [OGPlaybackStatus.progress] and [OGAVPlayerController]'s seek clamping ([seekTo]/[seekBy]) +
 * live [status] plumbing. The platform actuals only *feed* these (poll → updateStatus) and *drain*
 * the seek (seekHandler → player.seekTo), so pinning the math + clamping here pins the cross-platform
 * behaviour without a running player.
 */
class OGPlaybackStatusTest {

    // ---- OGPlaybackStatus.progress ----

    @Test
    fun progressIsZeroWhenDurationUnknown() {
        assertEquals(0f, OGPlaybackStatus().progress, 0.0001f)
        assertEquals(0f, OGPlaybackStatus(positionMs = 5_000, durationMs = 0).progress, 0.0001f)
    }

    @Test
    fun progressIsPositionOverDuration() {
        assertEquals(0.5f, OGPlaybackStatus(positionMs = 5_000, durationMs = 10_000).progress, 0.0001f)
        assertEquals(0.25f, OGPlaybackStatus(positionMs = 2_500, durationMs = 10_000).progress, 0.0001f)
    }

    @Test
    fun progressClampsToOne() {
        // A position past the reported duration (can happen momentarily at loop wrap) never exceeds 1.
        assertEquals(1f, OGPlaybackStatus(positionMs = 12_000, durationMs = 10_000).progress, 0.0001f)
    }

    @Test
    fun defaultsAreZeroState() {
        val s = OGPlaybackStatus()
        assertFalse(s.isPlaying)
        assertEquals(0L, s.positionMs)
        assertEquals(0L, s.durationMs)
        assertEquals(0L, s.bufferedMs)
    }

    // ---- controller.status ----

    @Test
    fun controllerStatusStartsAtZeroAndReflectsUpdates() {
        val c = OGAVPlayerController()
        assertEquals(OGPlaybackStatus(), c.status.value)
        c.updateStatus(OGPlaybackStatus(isPlaying = true, positionMs = 3_000, durationMs = 9_000))
        assertTrue(c.status.value.isPlaying)
        assertEquals(3_000L, c.status.value.positionMs)
        assertEquals(9_000L, c.status.value.durationMs)
    }

    // ---- seekTo / seekBy clamping ----

    private fun controllerWithCapture(): Pair<OGAVPlayerController, () -> Long?> {
        val c = OGAVPlayerController()
        var captured: Long? = null
        c.seekHandler = { captured = it }
        return c to { captured }
    }

    @Test
    fun seekToClampsToDuration() {
        val (c, captured) = controllerWithCapture()
        c.updateStatus(OGPlaybackStatus(positionMs = 3_000, durationMs = 10_000))
        c.seekTo(4_000)
        assertEquals(4_000L, captured())
        c.seekTo(20_000)          // past the end -> clamped to duration
        assertEquals(10_000L, captured())
        c.seekTo(-5)              // negative -> clamped to 0
        assertEquals(0L, captured())
    }

    @Test
    fun seekToDoesNotClampUpwardsWhenDurationUnknown() {
        val (c, captured) = controllerWithCapture()
        // durationMs stays 0 (unknown) -> only the lower bound is enforced.
        c.seekTo(20_000)
        assertEquals(20_000L, captured())
        c.seekTo(-1)
        assertEquals(0L, captured())
    }

    @Test
    fun seekByIsRelativeToCurrentPositionAndClamped() {
        val (c, captured) = controllerWithCapture()
        c.updateStatus(OGPlaybackStatus(positionMs = 3_000, durationMs = 10_000))
        c.seekBy(2_000)           // 3000 + 2000
        assertEquals(5_000L, captured())
        c.seekBy(-10_000)         // 3000 - 10000 -> clamped to 0
        assertEquals(0L, captured())
        c.seekBy(60_000)          // 3000 + 60000 -> clamped to duration
        assertEquals(10_000L, captured())
    }

    @Test
    fun seekIsNoOpUntilAttached() {
        val c = OGAVPlayerController()
        assertNull(c.seekHandler)
        // No handler bound yet: must not throw.
        c.seekTo(5_000)
        c.seekBy(-1_000)
    }
}
