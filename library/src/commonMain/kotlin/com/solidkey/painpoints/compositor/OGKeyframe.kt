package com.solidkey.painpoints.compositor

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull

/**
 * Easing curve applied to the segment that **ends** at a keyframe — i.e. the interpolation from the
 * previous keyframe up to this one uses this keyframe's [OGKeyframe.easing]. All curves are pure math
 * over a normalized `t in 0..1`, so they cost nothing and are identical on Android and iOS.
 */
@Serializable
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
@Serializable
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
 *
 * **Decode tolerance (model-authorability).** A track serializes as the object `{ "default":…,
 * "keyframes":[…] }`, but [decode][OGKeyframedFloatSerializer] *also* accepts a **bare JSON number** as
 * a constant track — so a language model that writes `"opacity": 0.8` (instead of the verbose object)
 * still produces a valid track. This mirrors the rest of the library's lenient, model-friendly codecs;
 * encoding always emits the explicit object form, so the wire output is unchanged and round-trips.
 */
@Serializable(with = OGKeyframedFloatSerializer::class)
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

/** The structural form of an [OGKeyframedFloat]; [default] is optional so a model may give only
 *  [keyframes]. Used by [OGKeyframedFloatSerializer] for the object branch (and for encoding). */
@Serializable
private data class OGKeyframedFloatSurrogate(
    val default: Float = 0f,
    val keyframes: List<OGKeyframe> = emptyList(),
)

/**
 * Serializer for [OGKeyframedFloat] that **encodes the object form** but **decodes either** a bare JSON
 * number (→ a constant track) **or** the `{ "default", "keyframes" }` object. Lets a model author a
 * constant track as just `0.8`, matching the library's lenient model-friendly codecs, with no change to
 * the emitted wire format (so existing data and round-trips are unaffected).
 */
object OGKeyframedFloatSerializer : KSerializer<OGKeyframedFloat> {
    private val surrogate = OGKeyframedFloatSurrogate.serializer()
    override val descriptor: SerialDescriptor = surrogate.descriptor

    override fun serialize(encoder: Encoder, value: OGKeyframedFloat) {
        surrogate.serialize(encoder, OGKeyframedFloatSurrogate(value.default, value.keyframes))
    }

    override fun deserialize(decoder: Decoder): OGKeyframedFloat {
        val json = decoder as? JsonDecoder
            ?: return surrogate.deserialize(decoder).let { OGKeyframedFloat(it.default, it.keyframes) }
        val element = json.decodeJsonElement()
        if (element is JsonPrimitive) {
            val n = element.floatOrNull ?: element.content.toFloatOrNull()
            if (n != null) return OGKeyframedFloat.const(n)
        }
        val s = json.json.decodeFromJsonElement(surrogate, element)
        return OGKeyframedFloat(s.default, s.keyframes)
    }
}
