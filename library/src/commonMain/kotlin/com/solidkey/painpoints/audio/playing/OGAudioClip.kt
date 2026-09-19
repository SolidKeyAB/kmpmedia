package com.solidkey.painpoints.audio.playing

/**
 * A named, time-bounded region ("clip") inside a single audio file — the primitive behind
 * [OGAudioSprite]. Pack many short sounds into one asset (an *audio sprite* / sound atlas) and
 * address each one by [id], the same way a texture atlas packs many images into one bitmap.
 *
 * Example — one `sfx.mp3` holding three game sounds laid out back to back:
 * ```
 * OGAudioClip("collect", startMs = 0,    endMs = 400)
 * OGAudioClip("hit",     startMs = 400,  endMs = 950)
 * OGAudioClip("powerup", startMs = 950)                 // plays to the end of the file
 * ```
 *
 * @param id       caller's key used to trigger the clip, e.g. `sprite.play("hit")`.
 * @param startMs  offset into the file where the clip begins (milliseconds, `>= 0`).
 * @param endMs    offset where the clip ends (milliseconds). Use [END] (the default) to play from
 *                 [startMs] to the end of the file. Otherwise must be strictly greater than [startMs].
 */
data class OGAudioClip(
    val id: String,
    val startMs: Long,
    val endMs: Long = END,
) {
    init {
        require(id.isNotBlank()) { "OGAudioClip id must not be blank" }
        require(startMs >= 0L) { "OGAudioClip startMs must be >= 0 (was $startMs)" }
        require(endMs == END || endMs > startMs) {
            "OGAudioClip endMs must be > startMs or END (start=$startMs, end=$endMs)"
        }
    }

    /** True when the clip runs to the end of the file (no explicit end was given). */
    val playsToEnd: Boolean get() = endMs == END

    /** Clip length in ms, or [END] (`-1`) when it plays to the end of the file (length unknown here). */
    val durationMs: Long get() = if (playsToEnd) END else endMs - startMs

    companion object {
        /** Sentinel [endMs] meaning "play from [startMs] to the end of the file". */
        const val END: Long = -1L
    }
}
