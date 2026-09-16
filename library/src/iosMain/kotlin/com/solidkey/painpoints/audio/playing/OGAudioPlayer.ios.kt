package com.solidkey.painpoints.audio.playing

import androidx.compose.runtime.Composable
import com.solidkey.painpoints.source.OGSource
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

actual class OGAudioPlayer {
    private var player: AVPlayer? = null
    private var lastLoadedSource: OGSource? = null
    private var isPrepared: Boolean = false
    private var pendingPlayRequest: Boolean = false  // 🚩 Handle early Play requests

    actual companion object {
        @Composable
        actual fun create(): OGAudioPlayer = OGAudioPlayer()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun load(source: OGSource, config: OGAudioPlaybackConfig, onError: ((String) -> Unit)?) {
        stop()   // ✅ Ensure previous playback is stopped
        reset()  // ✅ Fully reset the player before loading new audio

        lastLoadedSource = source
        isPrepared = false
        pendingPlayRequest = false
        var didReportError = false  // ✅ Guard against duplicate error callbacks

        val url: NSURL? = when (source) {
            is OGSource.FilePath -> NSURL.fileURLWithPath(source.path)
            is OGSource.Url -> NSURL.URLWithString(source.url)
            is OGSource.Resource -> NSBundle.mainBundle.URLForResource(source.resource, "mp3")
        }

        if (url == null) {
            onError?.invoke("Invalid or missing audio source")
            return
        }

        player = AVPlayer(url).apply {
            addPeriodicTimeObserverForInterval(
                interval = CMTimeMake(1, 1),
                queue = null
            ) { _ ->
                if (currentItem?.status == AVPlayerItemStatusFailed && !didReportError) {
                    didReportError = true
                    onError?.invoke(currentItem?.error?.localizedDescription ?: "Audio playback failed")
                } else if (!isPrepared && currentItem?.status == AVPlayerItemStatusReadyToPlay) {
                    isPrepared = true

                    if (pendingPlayRequest || config.autoStart) {
                        if (config.startDelay > 0) {
                            dispatch_after(
                                dispatch_time(DISPATCH_TIME_NOW, config.startDelay * NSEC_PER_MSEC.toLong()),
                                dispatch_get_main_queue()
                            ) {
                                play()
                            }
                        } else {
                            play()
                        }
                    }
                }
            }
        }
    }

    actual fun play() {
        if (isPrepared) {
            player?.let {
                if (!isPlaying()) {
                    it.play()
                }
            } ?: println("❌ No audio loaded to play.")
        } else {
            pendingPlayRequest = true
        }
    }

    actual fun pause() {
        player?.takeIf { isPlaying() }?.pause()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun stop() {
        player?.pause()
        player?.seekToTime(CMTimeMake(value = 0, timescale = 1)) // ✅ Rewind to the beginning
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun reset() {
        player?.pause()
        player?.seekToTime(CMTimeMake(value = 0, timescale = 1)) // 🔄 Rewind before resetting
        player = null
        lastLoadedSource = null
        isPrepared = false
        pendingPlayRequest = false
    }

    actual fun isPlaying(): Boolean {
        return (player?.rate ?: 0.0f) > 0.0f // ✅ Check if audio is currently playing
    }
}
