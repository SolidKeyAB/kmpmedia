package com.solidkey.painpoints.compositor

/**
 * Easing curve applied to the segment that **ends** at a keyframe — i.e. the interpolation from the
 * previous keyframe up to this one uses this keyframe's [OGKeyframe.easing]. All curves are pure math
 * over a normalized `t in 0..1`, so they cost nothing and are identical on Android and iOS.
 */
enum class OGEasing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

/** Maps a linear `t in 0..1` through [easing]. Pure + platform-independent (directly unit-testable). */
fun ogEase(easing: OGEasing, t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return when (easing) {
        OGEasing.LINEAR -> x
        OGEasing.EASE_IN -> x * x
        OGEasing.EASE_OUT -> x * (2f - x)
        OGEasing.EASE_IN_OUT -> if (x < 0.5f) 2f * x * x else 1f - (-2f * x + 2f) * (-2f * x + 2f) / 2f
    }
}

/**
 * A single stop on an [OGKeyframedFloat] track: the property reaches [value] at [timeMs] (absolute
 * composition time, milliseconds), and the ramp *into* this stop from the previous one uses [easing].
 */
data class OGKeyframe(
    val timeMs: Long,
    val value: Float,
    val easing: OGEasing = OGEasing.LINEAR,
)

/**
 * An animatable `Float` property: a [default] value plus zero or more [keyframes]. With no keyframes
 * it is a constant [default]. With keyframes it is piecewise-interpolated by [valueAt]:
 * clamped-flat before the first / after the last stop, and eased between adjacent stops.
 *
 * The whole thing is pure data + pure math (no Compose, no platform types), so a track is evaluated
 * **once per frame** with a couple of multiplies — the parse-once / lerp-per-frame budget that keeps
 * a composition preview at 60fps.
 */
data class OGKeyframedFloat(
    val default: Float,
    val keyframes: List<OGKeyframe> = emptyList(),
) {
    // Sorted once (by value equality this is stable), so evaluation never re-sorts per frame.
    private val sorted: List<OGKeyframe> by lazy { keyframes.sortedBy { it.timeMs } }

    /** The property's value at absolute [timeMs]. Flat before the first / after the last keyframe. */
    fun valueAt(timeMs: Long): Float {
        val ks = sorted
        if (ks.isEmpty()) return default
        if (ks.size == 1) return ks[0].value
        if (timeMs <= ks.first().timeMs) return ks.first().value
        if (timeMs >= ks.last().timeMs) return ks.last().value
        // Find the segment [a, b] with a.timeMs <= timeMs < b.timeMs.
        var a = ks[0]
        for (i in 1 until ks.size) {
            val b = ks[i]
            if (timeMs < b.timeMs) {
                val span = (b.timeMs - a.timeMs).toFloat()
                val t = if (span <= 0f) 1f else (timeMs - a.timeMs) / span
                return a.value + (b.value - a.value) * ogEase(b.easing, t)
            }
            a = b
        }
        return ks.last().value
    }

    companion object {
        /** A constant track (no animation). */
        fun const(value: Float): OGKeyframedFloat = OGKeyframedFloat(value)

        /**
         * Build a track from `time -> value` pairs (all [OGEasing.LINEAR]). The [default] is the first
         * pair's value, which only matters if the list is somehow empty.
         */
        fun of(vararg stops: Pair<Long, Float>): OGKeyframedFloat = OGKeyframedFloat(
            default = stops.firstOrNull()?.second ?: 0f,
            keyframes = stops.map { OGKeyframe(it.first, it.second) },
        )
    }
}
