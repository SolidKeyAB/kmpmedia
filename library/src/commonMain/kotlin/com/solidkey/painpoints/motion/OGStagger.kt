package com.solidkey.painpoints.motion

import kotlinx.serialization.Serializable

/**
 * A small set of easing curves for motion-design timing. Each maps an input `0..1` to an output,
 * normally `0..1` (except [OVERSHOOT], which overshoots past `1` then settles — a "pop").
 */
enum class OGEasing {
    /** Constant speed. */
    LINEAR,

    /** Starts slow, accelerates (quadratic). */
    EASE_IN,

    /** Starts fast, decelerates (quadratic) — the classic UI "arrive gently". */
    EASE_OUT,

    /** Slow at both ends, fast in the middle (smoothstep-like). */
    EASE_IN_OUT,

    /** Eases out but overshoots past the target and springs back — a lively pop (can exceed 1). */
    OVERSHOOT,
}

/** Apply this easing to [t] (clamped to `0..1` on input). */
fun OGEasing.ease(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return when (this) {
        OGEasing.LINEAR -> x
        OGEasing.EASE_IN -> x * x
        OGEasing.EASE_OUT -> 1f - (1f - x) * (1f - x)
        OGEasing.EASE_IN_OUT -> if (x < 0.5f) 2f * x * x else 1f - 2f * (1f - x) * (1f - x)
        OGEasing.OVERSHOOT -> {
            val c1 = 1.70158f
            val c3 = c1 + 1f
            val p = x - 1f
            1f + c3 * p * p * p + c1 * p * p
        }
    }
}

/** Parse an easing name leniently (case / separators ignored); unknown falls back to [OGEasing.EASE_IN_OUT]. */
fun ogEasingOf(name: String): OGEasing =
    when (name.lowercase().replace("_", "").replace("-", "").replace(" ", "")) {
        "linear" -> OGEasing.LINEAR
        "easein", "in" -> OGEasing.EASE_IN
        "easeout", "out" -> OGEasing.EASE_OUT
        "easeinout", "inout" -> OGEasing.EASE_IN_OUT
        "overshoot", "back", "pop" -> OGEasing.OVERSHOOT
        else -> OGEasing.EASE_IN_OUT
    }

/**
 * **Stagger** turns one global `progress` into a *per-element* progress so a group animates with
 * offset timing instead of in lockstep — the move that makes a list "cascade" in, letters pop one
 * after another, or a row of icons ripple. It is pure maths: give it the element's [index], the group
 * [count], and the shared timeline position [t] (`0..1`), and it returns that element's own `0..1`
 * progress, which you feed to a draw-on, a motion path, a scale, an alpha — anything.
 *
 * [stagger] (`0..1`) controls how spread out the starts are: `0` = everyone moves together (no
 * stagger), approaching `1` = fully sequential (each element waits for the previous). The first index
 * leads; [reverse] makes the last index lead instead. [easing] shapes each element's local curve.
 *
 * There is no state and no allocation, so staggering hundreds of elements per frame is free.
 */
object OGStagger {
    /**
     * The `0..1` progress of element [index] of [count], at shared timeline position [t].
     * See the class doc for [stagger] / [easing] / [reverse].
     */
    fun progressFor(
        index: Int,
        count: Int,
        t: Float,
        stagger: Float = 0.5f,
        easing: OGEasing = OGEasing.EASE_IN_OUT,
        reverse: Boolean = false,
    ): Float {
        val tt = (if (t.isFinite()) t else 0f).coerceIn(0f, 1f)
        if (count <= 1) return easing.ease(tt)
        val f = stagger.coerceIn(0f, 0.999f) // spread of start offsets; cap below 1 so the window stays > 0
        val window = 1f - f
        val order = if (reverse) (count - 1 - index) else index
        val start = (order.coerceIn(0, count - 1) / (count - 1f)) * f
        val local = ((tt - start) / window).coerceIn(0f, 1f)
        return easing.ease(local)
    }
}

/**
 * The **data** form of a stagger (JSON-friendly, model-authorable), mirroring the library's other
 * specs. Decode/encode and reach for a [OGMotions.staggerPresets] via [OGMotions].
 *
 * @param stagger spread of start offsets `0..1` (`0` = together, near `1` = sequential).
 * @param easing an [OGEasing] name ("linear", "easeIn", "easeOut", "easeInOut", "overshoot").
 * @param reverse let the last element lead instead of the first.
 */
@Serializable
data class OGStaggerSpec(
    val name: String? = null,
    val stagger: Float = 0.5f,
    val easing: String = "easeInOut",
    val reverse: Boolean = false,
) {
    /** This spec's [OGStagger.progressFor] for element [index] of [count] at timeline position [t]. */
    fun progressFor(index: Int, count: Int, t: Float): Float =
        OGStagger.progressFor(index, count, t, stagger, ogEasingOf(easing), reverse)
}
