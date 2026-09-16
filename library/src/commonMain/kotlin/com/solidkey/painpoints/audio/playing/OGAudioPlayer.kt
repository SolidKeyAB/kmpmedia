package com.solidkey.painpoints.audio.playing

import androidx.compose.runtime.Composable
import com.solidkey.painpoints.source.OGSource


expect class OGAudioPlayer {
    companion object {
        @Composable
        fun create(): OGAudioPlayer // ✅ Factory Method
    }

    fun load(
        source: OGSource,
        config: OGAudioPlaybackConfig = OGAudioPlaybackConfig(),
        onError: ((String) -> Unit)? = null // ✅ Surface load/playback failures to the caller
    )
    fun play()
    fun pause()
    fun stop()
    fun isPlaying(): Boolean
    fun reset()
}
