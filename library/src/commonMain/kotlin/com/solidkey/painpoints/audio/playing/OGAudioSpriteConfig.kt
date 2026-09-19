package com.solidkey.painpoints.audio.playing

/**
 * Tuning for an [OGAudioSprite].
 *
 * @param voices how many clips may sound *at the same time*. Each voice is an independent player
 *   instance; triggers are handed out round-robin, so the (voices+1)-th overlapping trigger reuses
 *   (and cuts off) the oldest voice. `4` comfortably covers UI feedback and most game SFX. Coerced
 *   to at least `1`.
 * @param volume master volume for every voice, `0f`..`1f`. Coerced into range.
 */
data class OGAudioSpriteConfig(
    val voices: Int = 4,
    val volume: Float = 1.0f,
)
