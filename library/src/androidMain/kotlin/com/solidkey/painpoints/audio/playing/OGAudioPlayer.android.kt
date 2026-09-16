package com.solidkey.painpoints.audio.playing

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.solidkey.painpoints.source.OGSource

actual class OGAudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var lastLoadedSource: OGSource? = null
    private var isPrepared: Boolean = false  // ✅ Track if audio is ready
    private var pendingPlayRequest: Boolean = false  // 🚩 If user clicks Play before prepared

    actual companion object {
        @Composable
        actual fun create(): OGAudioPlayer {
            val ctx = LocalContext.current
            return OGAudioPlayer(ctx)
        }
    }

    actual fun load(source: OGSource, config: OGAudioPlaybackConfig, onError: ((String) -> Unit)?) {
        stop()  // ✅ Ensure previous playback is stopped
        reset()  // ✅ Fully reset the player before loading a new source

        lastLoadedSource = source
        isPrepared = false
        pendingPlayRequest = false

        mediaPlayer = MediaPlayer().apply {
            setVolume(1.0f, 1.0f)
            isLooping = config.autoRepeat  // 🔁 Loop if needed

            setOnPreparedListener {
                isPrepared = true

                // 🚀 Handle queued Play request
                if (pendingPlayRequest || config.autoStart) {
                    if (config.startDelay > 0) {
                        Handler(Looper.getMainLooper()).postDelayed({ play() }, config.startDelay)
                    } else {
                        play()
                    }
                }
            }

            setOnErrorListener { _, what, extra ->
                reset()  // ✅ Reset the player on error
                onError?.invoke("MediaPlayer error: what=$what, extra=$extra")
                false
            }

            try {
                when (source) {
                    is OGSource.FilePath -> setDataSource(source.path)
                    is OGSource.Url -> setDataSource(source.url)
                    is OGSource.Resource -> {
                        val resId = context.resources.getIdentifier(source.resource, "raw", context.packageName)
                        setDataSource(context, Uri.parse("android.resource://${context.packageName}/$resId"))
                    }
                }
                prepareAsync()  // ✅ Async preparation
            } catch (e: Exception) {
                onError?.invoke("Failed to load audio: ${e.message}")
            }
        }
    }

    actual fun play() {
        if (isPrepared) {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                    it.start()
                }
            } ?: println("❌ No audio loaded to play.")
        } else {
            pendingPlayRequest = true  // 🚩 Queue the Play request
        }
    }

    actual fun pause() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
    }

    actual fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
                it.seekTo(0)  // ✅ Rewind
                isPrepared = false
            }
        }
    }

    actual fun reset() {
        mediaPlayer?.reset()  // 🚫 Reset player state
        mediaPlayer?.release()  // ♻️ Release resources
        mediaPlayer = null
        lastLoadedSource = null
        isPrepared = false
        pendingPlayRequest = false
    }

    actual fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
}
