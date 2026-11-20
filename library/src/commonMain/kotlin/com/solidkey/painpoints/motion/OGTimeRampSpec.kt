package com.solidkey.painpoints.motion

import kotlinx.serialization.Serializable

/**
 * **Time-ramp / bullet-time** — a data description of a speed-ramping event: normal speed ramps down
 * to a near-freeze, holds, then returns to normal. Apps multiply their frame `dt` by
 * [OGTimeRamp.speedAt] (or drive a live [OGTimeRampValue]) to slow every animated system — particles,
 * scroll speed, physics — simultaneously without changing the systems themselves.
 *
 * The ramp shape uses [smoothStep] cubic easing on both the ramp-down and ramp-up phases for a
 * cinematic feel (no abrupt snap). The sequence is a one-shot event triggered by [OGTimeRampValue.trigger];
 * it auto-completes after [rampDownSec] + [holdSec] + [rampUpSec] seconds and resets to `timeScale = 1`.
 *
 * Decode + presets on [OGTimeRamp]; live usage via [OGTimeRampValue].
 *
 * @param minSpeed Slowest playback scale reached at the bottom of the ramp (`0` = full freeze,
 *   `0.05` = Matrix-style near-freeze, `1` = no effect). Clamped `0..1`.
 * @param rampDownSec Seconds to ease from `1.0` → [minSpeed].
 * @param holdSec Seconds spent at [minSpeed].
 * @param rampUpSec Seconds to ease from [minSpeed] → `1.0`.
 */
@Serializable
data class OGTimeRampSpec(
    val name: String? = null,
    val minSpeed: Float = 0.05f,
    val rampDownSec: Float = 0.25f,
    val holdSec: Float = 2.0f,
    val rampUpSec: Float = 0.5f,
)
