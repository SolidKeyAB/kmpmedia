package com.solidkey.painpoints.video.playing

/**
 * Live, reactive snapshot of a video's playback timeline. It is delivered continuously (~5 Hz)
 * while a video is attached to an [OGAVPlayerController] (read [OGAVPlayerController.status]) or an
 * `onProgress` callback is supplied to [OGAVPlayer], on BOTH Android (ExoPlayer) and iOS (AVPlayer),
 * so callers can build scrubbers, progress bars, time-synced hotspots, chapter markers, etc. — all
 * in plain Compose — without touching the platform players.
 *
 * All times are milliseconds. Fields are safe defaults until the media reports real values, so a
 * [progress]-driven UI shows an empty/zero state rather than garbage before the video is ready.
 *
 * Note: [bufferedMs] is reported on Android; on iOS it is currently left at 0 (the position/duration/
 * isPlaying fields — everything a scrubber needs — are populated on both platforms).
 */
data class OGPlaybackStatus(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,   // 0 until the media reports a real duration
    val bufferedMs: Long = 0L,
) {
    /** Fraction played in `0f..1f`; `0f` until [durationMs] is known. */
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * How often each platform samples the underlying player's position and pushes a fresh
 * [OGPlaybackStatus]. 200 ms (5 Hz) is smooth enough for a scrubber/progress bar while staying
 * negligible for battery — and the sampling loop only runs while a controller or `onProgress`
 * consumer is actually attached (it is skipped entirely otherwise).
 */
internal const val OG_POSITION_POLL_MS = 200L
