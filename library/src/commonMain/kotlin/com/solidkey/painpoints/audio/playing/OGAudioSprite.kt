package com.solidkey.painpoints.audio.playing

import androidx.compose.runtime.Composable
import com.solidkey.painpoints.source.OGSource

/**
 * Plays *slices* of a single audio file on demand — an **audio sprite** / sound atlas.
 *
 * Load one file plus a set of [OGAudioClip]s (each a `[startMs, endMs]` window), then fire any clip
 * by id in response to an event: `sprite.play("hit")`. Ideal for game SFX and UI feedback where many
 * short sounds live in one asset. A small pool of voices (see [OGAudioSpriteConfig.voices]) lets
 * clips *overlap* — e.g. a "collect" chime can ring while a "hit" thud is still playing — instead of
 * cutting each other off the way a single [OGAudioPlayer] would.
 *
 * Under the hood this reuses the platform media stack already in the library: Android plays each
 * window via media3/ExoPlayer clipping; iOS via `AVPlayer` seek + `forwardPlaybackEndTime`.
 *
 * Typical use:
 * ```
 * val sprite = OGAudioSprite.create()
 * LaunchedEffect(Unit) {
 *     sprite.load(
 *         source = OGSource.Resource("sfx"),
 *         clips = listOf(
 *             OGAudioClip("collect", 0, 400),
 *             OGAudioClip("hit", 400, 950),
 *         ),
 *     )
 * }
 * // ...on a game event:
 * sprite.play("hit")
 * ```
 * Remember to [release] the sprite when the screen leaves composition (e.g. in a `DisposableEffect`).
 */
expect class OGAudioSprite {
    companion object {
        /** Factory — obtains the platform context it needs from composition. */
        @Composable
        fun create(): OGAudioSprite
    }

    /**
     * Load the backing audio [source] and the [clips] that can be triggered from it. Replaces any
     * previously loaded content. [config] sizes the voice pool and sets master volume.
     * [onError] surfaces load/playback failures (bad source, decode error, unknown clip).
     */
    fun load(
        source: OGSource,
        clips: List<OGAudioClip>,
        config: OGAudioSpriteConfig = OGAudioSpriteConfig(),
        onError: ((String) -> Unit)? = null,
    )

    /**
     * Trigger the clip registered under [clipId] on the next free voice (overlapping earlier clips
     * up to the voice count). No-op with an [onError] callback if the id is unknown or nothing is
     * loaded.
     */
    fun play(clipId: String)

    /** Silence any voices currently playing [clipId] (leaves other clips sounding). */
    fun stop(clipId: String)

    /** Silence every voice immediately. */
    fun stopAll()

    /** Set master volume for all voices, `0f`..`1f` (coerced into range). */
    fun setVolume(volume: Float)

    /** Release all voices and platform resources. The sprite is unusable until [load] is called again. */
    fun release()

    /** The ids of every clip currently loaded (see [load]). */
    val clipIds: Set<String>
}
