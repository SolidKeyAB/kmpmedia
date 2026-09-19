package com.solidkey.painpoints.audio.playing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the platform-free core of the audio-sprite feature: the [OGAudioClip] window model (its
 * validation + derived duration) and the [OGVoiceRotor] round-robin allocator that both the Android
 * and iOS actuals share to pick which voice fires next.
 */
class OGAudioSpriteTest {

    // --- OGAudioClip: window math -------------------------------------------------------------

    @Test
    fun boundedClipReportsItsDuration() {
        val clip = OGAudioClip("hit", startMs = 400, endMs = 950)
        assertFalse(clip.playsToEnd)
        assertEquals(550, clip.durationMs)
    }

    @Test
    fun clipWithoutEndPlaysToFileEnd() {
        val clip = OGAudioClip("powerup", startMs = 950)
        assertTrue(clip.playsToEnd)
        assertEquals(OGAudioClip.END, clip.endMs)
        assertEquals(OGAudioClip.END, clip.durationMs)
    }

    // --- OGAudioClip: validation --------------------------------------------------------------

    @Test
    fun blankIdIsRejected() {
        assertFailsWith<IllegalArgumentException> { OGAudioClip("  ", startMs = 0, endMs = 100) }
    }

    @Test
    fun negativeStartIsRejected() {
        assertFailsWith<IllegalArgumentException> { OGAudioClip("x", startMs = -1, endMs = 100) }
    }

    @Test
    fun endNotAfterStartIsRejected() {
        assertFailsWith<IllegalArgumentException> { OGAudioClip("x", startMs = 500, endMs = 500) }
        assertFailsWith<IllegalArgumentException> { OGAudioClip("x", startMs = 500, endMs = 400) }
    }

    @Test
    fun endEqualToSentinelPlaysToEndRegardlessOfStart() {
        // END (-1) must never trip the "end > start" rule.
        val clip = OGAudioClip("x", startMs = 5_000, endMs = OGAudioClip.END)
        assertTrue(clip.playsToEnd)
    }

    // --- OGVoiceRotor: round-robin ------------------------------------------------------------

    @Test
    fun rotorCyclesThroughEveryVoiceInOrder() {
        val rotor = OGVoiceRotor(3)
        assertEquals(listOf(0, 1, 2, 0, 1, 2, 0), List(7) { rotor.next() })
    }

    @Test
    fun singleVoiceRotorAlwaysReturnsZero() {
        val rotor = OGVoiceRotor(1)
        assertEquals(listOf(0, 0, 0), List(3) { rotor.next() })
    }

    @Test
    fun rotorRejectsNonPositiveVoiceCount() {
        assertFailsWith<IllegalArgumentException> { OGVoiceRotor(0) }
    }
}
