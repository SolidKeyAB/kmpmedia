package com.solidkey.painpoints.video.playing

import co.touchlab.kermit.Logger

/**
 * A timed trigger fired off a video's playback position — the "cue points / timers" primitive
 * every serious player has (HTML5 `TextTrack` cues, Video.js/JW/Brightcove cue points). Hand a list
 * of these to [OGAVPlayer] via `cues =` and it fires them as playback crosses their times, so
 * chapter markers, captions, shoppable tags, quiz prompts, "skip intro" buttons, and analytics
 * beacons are plain callbacks — no manual position math.
 *
 * Two forms:
 *  - **point cue** ([untilMs] `== null`): [onEnter] fires once each time playback crosses [atMs]
 *    going forward (including a seek that jumps over it).
 *  - **range cue** ([untilMs] `!= null`): [onEnter] fires when playback ENTERS the half-open range
 *    `[atMs, untilMs)`, [onExit] when it LEAVES — the free "show X only between t1 and t2" gating.
 *
 * Times are milliseconds, matching [OGPlaybackStatus]. Callbacks run on the main thread.
 */
data class OGCue(
    val atMs: Long,
    val untilMs: Long? = null,
    val id: String = if (untilMs == null) "at:$atMs" else "range:$atMs-$untilMs",
    val onEnter: () -> Unit = {},
    val onExit: () -> Unit = {},
) {
    init {
        require(atMs >= 0L) { "OGCue atMs ($atMs) must be >= 0" }
        require(untilMs == null || untilMs > atMs) {
            "OGCue range untilMs ($untilMs) must be > atMs ($atMs)"
        }
    }

    companion object {
        /** Point cue: [onFire] runs once each time playback crosses [atMs] going forward. */
        fun at(atMs: Long, onFire: () -> Unit): OGCue = OGCue(atMs = atMs, onEnter = onFire)

        /** Range cue: [onEnter]/[onExit] run as playback enters/leaves the range `[fromMs, toMs)`. */
        fun range(
            fromMs: Long,
            toMs: Long,
            onEnter: () -> Unit = {},
            onExit: () -> Unit = {},
        ): OGCue = OGCue(atMs = fromMs, untilMs = toMs, onEnter = onEnter, onExit = onExit)
    }
}

/** True when a point cue at [atMs] is crossed going forward from [prevMs] to [curMs]. */
internal fun crossedForward(prevMs: Long, curMs: Long, atMs: Long): Boolean =
    prevMs in 0L until atMs && curMs >= atMs

/** True when [posMs] lies inside the half-open range `[fromMs, toMs)`. */
internal fun inRange(posMs: Long, fromMs: Long, toMs: Long): Boolean =
    posMs in fromMs until toMs

/**
 * Stateful dispatcher behind [OGAVPlayer]'s `cues`. It is pure Kotlin driven purely by the playback
 * position (which [OGAVPlayer] already samples ~5 Hz), so it needs NO platform code — the same
 * instance works identically on Android and iOS, and can be driven by hand off
 * [OGAVPlayerController.status] for full control.
 *
 * Feed it the latest position via [onTick]; it compares against the previous tick to fire point-cue
 * crossings and range-cue enter/exit transitions exactly once each. A callback that throws is logged
 * and swallowed so one bad cue can't stall position tracking.
 */
class OGCueEngine(cues: List<OGCue> = emptyList()) {
    private var cues: List<OGCue> = cues
    private var lastMs: Long = -1L
    private val insideIds = HashSet<String>()

    /**
     * Replace the cue list. Range-"inside" state for cues that are no longer present is dropped, so
     * removing a range cue mid-play does NOT fire a spurious [OGCue.onExit].
     */
    fun setCues(newCues: List<OGCue>) {
        cues = newCues
        val validIds = newCues.mapTo(HashSet()) { it.id }
        insideIds.retainAll(validIds)
    }

    /** Forget all crossing/inside state (e.g. when the player rebinds a fresh source). */
    fun reset() {
        lastMs = -1L
        insideIds.clear()
    }

    /** Feed the latest playback position; fires any cues crossed since the previous call. */
    fun onTick(positionMs: Long) {
        val prev = lastMs
        for (cue in cues) {
            val until = cue.untilMs
            if (until == null) {
                if (crossedForward(prev, positionMs, cue.atMs)) safe(cue.onEnter)
            } else {
                val nowInside = inRange(positionMs, cue.atMs, until)
                val wasInside = cue.id in insideIds
                if (nowInside && !wasInside) {
                    insideIds.add(cue.id)
                    safe(cue.onEnter)
                } else if (!nowInside && wasInside) {
                    insideIds.remove(cue.id)
                    safe(cue.onExit)
                }
            }
        }
        lastMs = positionMs
    }

    private inline fun safe(cb: () -> Unit) {
        try {
            cb()
        } catch (t: Throwable) {
            Logger.w("OGCue callback threw and was ignored: ${t.message}")
        }
    }
}
