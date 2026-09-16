package com.solidkey.painpoints.audio.playing

data class OGAudioPlaybackConfig(
    val autoStart: Boolean = false,       // ✅ Auto-start after loading
    val autoRepeat: Boolean = false,      // 🔁 Loop playback
    val startDelay: Long = 0L             // ⏱ Delay before playback (in milliseconds)
)
