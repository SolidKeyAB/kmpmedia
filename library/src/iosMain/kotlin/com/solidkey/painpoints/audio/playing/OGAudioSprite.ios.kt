package com.solidkey.painpoints.audio.playing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.solidkey.painpoints.source.OGSource
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.currentItem
import platform.AVFoundation.error
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setForwardPlaybackEndTime
import platform.AVFoundation.setVolume
import platform.AVFoundation.status
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

/**
 * iOS [OGAudioSprite] — a pool of `AVPlayer` voices sharing the same file URL. Each trigger takes
 * the next voice round-robin, seeks it to the clip's `startMs`, and sets
 * `AVPlayerItem.forwardPlaybackEndTime` to `endMs` so playback stops exactly at the window end.
 * Mirrors the Android actual's behaviour (voice pool, round-robin, per-clip windows).
 */
@OptIn(ExperimentalForeignApi::class)
actual class OGAudioSprite {

    private var voices: List<AVPlayer> = emptyList()
    private var voiceClip: Array<String?> = emptyArray()
    private var rotor = OGVoiceRotor(1)

    private var clips: Map<String, OGAudioClip> = emptyMap()
    private var url: NSURL? = null
    private var volume: Float = 1f
    private var onError: ((String) -> Unit)? = null

    actual companion object {
        @Composable
        // remember so the sprite survives recomposition — callers (e.g. a game screen that
        // recomposes every frame) get ONE stable pool instead of leaking a new one each pass.
        actual fun create(): OGAudioSprite = remember { OGAudioSprite() }
    }

    actual fun load(
        source: OGSource,
        clips: List<OGAudioClip>,
        config: OGAudioSpriteConfig,
        onError: ((String) -> Unit)?,
    ) {
        release()
        this.onError = onError
        this.clips = clips.associateBy { it.id }
        this.volume = config.volume.coerceIn(0f, 1f)

        val resolved = resolveUrl(source)
        if (resolved == null) {
            onError?.invoke("Invalid or missing audio source: $source")
            return
        }
        url = resolved

        val voiceCount = config.voices.coerceAtLeast(1)
        rotor = OGVoiceRotor(voiceCount)
        voiceClip = arrayOfNulls(voiceCount)
        // Each voice needs its OWN item (an AVPlayerItem can't be shared across players); they all
        // point at the same file, so iOS caches the underlying asset.
        voices = List(voiceCount) {
            AVPlayer(playerItem = AVPlayerItem(uRL = resolved)).apply { setVolume(volume) }
        }
    }

    actual fun play(clipId: String) {
        val clip = clips[clipId] ?: run {
            onError?.invoke("Unknown clip id: $clipId")
            return
        }
        if (voices.isEmpty() || url == null) {
            onError?.invoke("No audio loaded")
            return
        }

        val index = rotor.next()
        val player = voices[index]
        voiceClip[index] = clipId

        val item = player.currentItem
        if (item == null) {
            // A prior error may have cleared the item — rebuild it so the voice keeps working.
            url?.let { player.replaceCurrentItemWithPlayerItem(AVPlayerItem(uRL = it)) }
        }
        if (player.currentItem?.status == AVPlayerItemStatusFailed) {
            onError?.invoke(player.error?.localizedDescription ?: "Audio playback failed")
            return
        }

        // Stop at the window end. For a clip that runs to the file end we can't set "infinity"
        // portably, so we push the end far past any real audio length — the file ends first. This
        // also RESETS any finite end left on this voice by a previous (bounded) clip.
        val endTime =
            if (clip.playsToEnd) CMTimeMake(value = Long.MAX_VALUE, timescale = 1)
            else CMTimeMake(value = clip.endMs, timescale = 1000)
        player.currentItem?.setForwardPlaybackEndTime(endTime)
        // Frame-accurate seek to the window start (zero tolerance), then fire.
        player.seekToTime(
            time = CMTimeMake(value = clip.startMs, timescale = 1000),
            toleranceBefore = CMTimeMake(value = 0, timescale = 1),
            toleranceAfter = CMTimeMake(value = 0, timescale = 1),
        )
        player.setVolume(volume)
        player.play()
    }

    actual fun stop(clipId: String) {
        voices.forEachIndexed { index, player ->
            if (voiceClip[index] == clipId) {
                player.pause()
                voiceClip[index] = null
            }
        }
    }

    actual fun stopAll() {
        voices.forEachIndexed { index, player ->
            player.pause()
            voiceClip[index] = null
        }
    }

    actual fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        voices.forEach { it.setVolume(this.volume) }
    }

    actual fun release() {
        voices.forEach { it.pause() }
        voices = emptyList()
        voiceClip = emptyArray()
        clips = emptyMap()
        url = null
        onError = null
    }

    actual val clipIds: Set<String> get() = clips.keys

    private fun resolveUrl(source: OGSource): NSURL? = when (source) {
        is OGSource.Url -> NSURL.URLWithString(source.url)
        is OGSource.FilePath -> NSURL.fileURLWithPath(source.path)
        is OGSource.Resource -> {
            // Mirror OGAudioPlayer.ios: honor an explicit extension, else default to mp3.
            val ext = source.extension ?: "mp3"
            NSBundle.mainBundle.URLForResource(source.resource, ext)
        }
    }
}
