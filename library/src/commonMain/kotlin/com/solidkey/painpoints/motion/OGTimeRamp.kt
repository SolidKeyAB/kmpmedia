package com.solidkey.painpoints.motion

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec + math + live stepper** for [OGTimeRampSpec] — the bullet-time / slow-motion timing
 * primitive.
 *
 * **Stateless math**: [speedAt] returns the playback speed scale at any `secSinceTriggered` without
 * holding state — useful for exportable/scrubable animations.
 *
 * **Live usage**: hold an [OGTimeRampValue] in `remember`, call [OGTimeRampValue.trigger] on the
 * slow-motion moment, and multiply all frame `dt`s by [OGTimeRampValue.timeScale]:
 * ```kotlin
 * val ramp = remember { OGTimeRampValue(OGTimeRamp.BULLET_TIME) }
 * // In game loop:
 * ramp.update(rawDt)
 * val dt = rawDt * ramp.timeScale
 * // On bullet hit / punch / dodge:
 * ramp.trigger()
 * ```
 */
object OGTimeRamp {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGTimeRampSpec =
        json.decodeFromString(OGTimeRampSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGTimeRampSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGTimeRampSpec): String = json.encodeToString(OGTimeRampSpec.serializer(), spec)

    fun preset(name: String): OGTimeRampSpec? = presets[name.lowercase()]

    /** Total seconds for one full ramp-down → hold → ramp-up sequence. */
    fun totalDurationSec(spec: OGTimeRampSpec): Float =
        spec.rampDownSec.coerceAtLeast(0f) + spec.holdSec.coerceAtLeast(0f) + spec.rampUpSec.coerceAtLeast(0f)

    /**
     * Playback speed scale at [secSinceTriggered] seconds after a trigger, using smoothStep cubic
     * easing on both transition legs. Returns `1f` before the trigger (`< 0`) and after the sequence
     * completes (`>= totalDurationSec`).
     */
    fun speedAt(spec: OGTimeRampSpec, secSinceTriggered: Float): Float {
        if (secSinceTriggered < 0f) return 1f
        val min = spec.minSpeed.coerceIn(0f, 1f)
        val down = spec.rampDownSec.coerceAtLeast(0f)
        val hold = spec.holdSec.coerceAtLeast(0f)
        val up = spec.rampUpSec.coerceAtLeast(0f)
        return when {
            down > 0f && secSinceTriggered < down ->
                smoothStep(1f, min, secSinceTriggered / down)
            secSinceTriggered < down + hold -> min
            up > 0f && secSinceTriggered < down + hold + up ->
                smoothStep(min, 1f, (secSinceTriggered - down - hold) / up)
            else -> 1f
        }
    }

    /** Classic Matrix bullet-time — near-freeze with a dramatic hold. */
    val BULLET_TIME = OGTimeRampSpec(
        name = "bullet_time", minSpeed = 0.05f, rampDownSec = 0.25f, holdSec = 2.0f, rampUpSec = 0.5f,
    )

    /** Quick cinematic mo — brief dip, good for dodge / parry moments. */
    val QUICK_MO = OGTimeRampSpec(
        name = "quick_mo", minSpeed = 0.15f, rampDownSec = 0.1f, holdSec = 0.8f, rampUpSec = 0.3f,
    )

    /** Freeze — full stop for a dramatic pause (finishing-blow freeze-frame). */
    val FREEZE = OGTimeRampSpec(
        name = "freeze", minSpeed = 0f, rampDownSec = 0.15f, holdSec = 1.0f, rampUpSec = 0.4f,
    )

    /** Subtle focus — mild slow-down (25%) for a tension moment, barely perceptible. */
    val FOCUS = OGTimeRampSpec(
        name = "focus", minSpeed = 0.25f, rampDownSec = 0.4f, holdSec = 1.5f, rampUpSec = 0.6f,
    )

    /** All built-in presets, keyed by lowercase name. */
    val presets: Map<String, OGTimeRampSpec> = listOf(BULLET_TIME, QUICK_MO, FREEZE, FOCUS).associateBy { it.name!! }

    private fun smoothStep(from: Float, to: Float, t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        val s = c * c * (3f - 2f * c)
        return from + (to - from) * s
    }
}

/**
 * A live, one-shot bullet-time state machine. Hold one in `remember` and call [trigger] to start the
 * ramp; call [update] with the raw (wall-clock) `dt` each frame; read [timeScale] to get the current
 * speed multiplier to apply to game/animation delta-time.
 *
 * The sequence completes automatically after [OGTimeRamp.totalDurationSec] and resets to `timeScale = 1`.
 * Calling [trigger] while already active restarts the ramp from the beginning.
 */
class OGTimeRampValue(val spec: OGTimeRampSpec = OGTimeRamp.BULLET_TIME) {
    private var elapsed: Float = -1f

    /** Current playback speed scale `0..1`. Multiply your game/animation `dt` by this. */
    var timeScale: Float = 1f
        private set

    /** `true` while a triggered ramp sequence is running. */
    val isActive: Boolean get() = elapsed >= 0f

    /** Start (or restart) the ramp-down → hold → ramp-up sequence. */
    fun trigger() {
        elapsed = 0f
    }

    /**
     * Advance the state machine by [dt] **wall-clock** seconds (the raw, unscaled frame time).
     * Updates [timeScale]; auto-resets when the sequence completes.
     */
    fun update(dt: Float) {
        if (elapsed < 0f) {
            timeScale = 1f
            return
        }
        elapsed += dt.coerceAtLeast(0f)
        timeScale = OGTimeRamp.speedAt(spec, elapsed)
        if (elapsed >= OGTimeRamp.totalDurationSec(spec)) {
            elapsed = -1f
            timeScale = 1f
        }
    }

    /** Snap back to normal speed immediately, cancelling any in-progress ramp. */
    fun reset() {
        elapsed = -1f
        timeScale = 1f
    }
}
