package com.solidkey.painpoints.audio.playing

/**
 * Pure round-robin voice allocator shared by both platform [OGAudioSprite] actuals. Each call to
 * [next] returns the index of the voice to (re)use for the next trigger, cycling `0..voiceCount-1`.
 * When all voices are busy this is the standard "steal the oldest voice" policy for sound effects.
 *
 * Kept as a tiny, platform-free class so the allocation behaviour is unit-tested once in
 * `commonTest` rather than duplicated (and drifting) across Android and iOS.
 */
internal class OGVoiceRotor(private val voiceCount: Int) {
    init { require(voiceCount >= 1) { "voiceCount must be >= 1 (was $voiceCount)" } }

    private var cursor = 0

    /** Index of the next voice to use, advancing the cursor. */
    fun next(): Int {
        val index = cursor
        cursor = (cursor + 1) % voiceCount
        return index
    }
}
