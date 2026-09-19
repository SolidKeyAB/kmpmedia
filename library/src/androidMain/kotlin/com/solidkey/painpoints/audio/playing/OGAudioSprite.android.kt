package com.solidkey.painpoints.audio.playing

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.solidkey.painpoints.source.OGSource

/**
 * Android [OGAudioSprite] — a pool of ExoPlayers, one per voice. Each trigger takes the next voice
 * round-robin and plays the clip's `[startMs, endMs]` window via media3's
 * [MediaItem.ClippingConfiguration], which stops exactly at the window end. Reuses the same
 * media3/ExoPlayer stack the video player already depends on. Short SFX deliberately do NOT grab
 * audio focus (a per-trigger focus request/abandon would add latency and duck the user's music).
 */
@OptIn(UnstableApi::class)
actual class OGAudioSprite(private val context: Context) {

    private var voices: List<ExoPlayer> = emptyList()
    /** Which clip id each voice is currently playing (parallel to [voices]); null = idle/free. */
    private var voiceClip: Array<String?> = emptyArray()
    private var rotor = OGVoiceRotor(1)

    private var clips: Map<String, OGAudioClip> = emptyMap()
    private var mediaUri: Uri? = null
    private var volume: Float = 1f
    private var onError: ((String) -> Unit)? = null

    actual companion object {
        @Composable
        actual fun create(): OGAudioSprite {
            val context = LocalContext.current
            // remember so the sprite survives recomposition — callers (e.g. a game screen that
            // recomposes every frame) get ONE stable pool instead of leaking a new one each pass.
            return remember { OGAudioSprite(context) }
        }
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

        val uri = resolveUri(source)
        if (uri == null) {
            onError?.invoke("Invalid or missing audio source: $source")
            return
        }
        mediaUri = uri

        val voiceCount = config.voices.coerceAtLeast(1)
        rotor = OGVoiceRotor(voiceCount)
        voiceClip = arrayOfNulls(voiceCount)
        voices = List(voiceCount) { index ->
            ExoPlayer.Builder(context).build().apply {
                volume = this@OGAudioSprite.volume
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        onError?.invoke("ExoPlayer error: ${error.message}")
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        // Mark the voice free once it drains, so stop(clipId) doesn't chase a
                        // finished clip.
                        if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
                            voiceClip[index] = null
                        }
                    }
                })
            }
        }
    }

    actual fun play(clipId: String) {
        val clip = clips[clipId] ?: run {
            onError?.invoke("Unknown clip id: $clipId")
            return
        }
        val uri = mediaUri ?: run {
            onError?.invoke("No audio loaded")
            return
        }
        if (voices.isEmpty()) return

        val index = rotor.next()
        val player = voices[index]
        voiceClip[index] = clipId

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clip.startMs)
            .apply { if (!clip.playsToEnd) setEndPositionMs(clip.endMs) }
            .build()
        val item = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(clipping)
            .build()

        player.run {
            stop()
            setMediaItem(item)
            volume = this@OGAudioSprite.volume
            prepare()
            playWhenReady = true
        }
    }

    actual fun stop(clipId: String) {
        voices.forEachIndexed { index, player ->
            if (voiceClip[index] == clipId) {
                player.stop()
                voiceClip[index] = null
            }
        }
    }

    actual fun stopAll() {
        voices.forEachIndexed { index, player ->
            player.stop()
            voiceClip[index] = null
        }
    }

    actual fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        voices.forEach { it.volume = this.volume }
    }

    actual fun release() {
        voices.forEach { it.release() }
        voices = emptyList()
        voiceClip = emptyArray()
        clips = emptyMap()
        mediaUri = null
        onError = null
    }

    actual val clipIds: Set<String> get() = clips.keys

    private fun resolveUri(source: OGSource): Uri? = when (source) {
        is OGSource.Url -> Uri.parse(source.url)
        is OGSource.FilePath -> Uri.parse(source.path)
        is OGSource.Resource -> {
            val resId = context.resources.getIdentifier(source.resource, "raw", context.packageName)
            if (resId == 0) null else Uri.parse("android.resource://${context.packageName}/$resId")
        }
    }
}
