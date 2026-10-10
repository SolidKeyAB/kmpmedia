package com.solidkey.painpoints.motion

import com.solidkey.painpoints.fx.OGFxNoise
import com.solidkey.painpoints.shape.OGPoint

/**
 * **Idle sway / breathe** — organic, non-repeating low-frequency motion so a character (or an idle UI
 * element) never sits like a statue between animations. It samples the library's existing fractal
 * noise ([OGFxNoise], simplex + fBm) against time, so the drift is smooth, seedable and deterministic
 * (frame-identical on Android & iOS), not a looping sine. Add the returned offset to a position, or
 * use [value] for a single wandering channel (a slow rotation, a breathing scale).
 *
 * @param amplitude peak offset.
 * @param frequency how fast it wanders (cycles of noise per second of `t`).
 * @param octaves fractal detail (more = busier); 2–4 is natural.
 * @param seed pick a different-but-repeatable wander.
 */
class OGSway(
    val amplitude: Float = 1f,
    val frequency: Float = 0.6f,
    val octaves: Int = 3,
    val seed: Int = 0,
) {
    /** A single wandering value in roughly `[-amplitude, amplitude]` at time [t] (seconds). */
    fun value(t: Float): Float = OGFxNoise.fbm(t * frequency, 0f, octaves, seed) * amplitude

    /** A 2D wandering offset (two decorrelated noise channels) at time [t]. */
    fun offset(t: Float): OGPoint = OGPoint(
        OGFxNoise.fbm(t * frequency, 0f, octaves, seed) * amplitude,
        OGFxNoise.fbm(0f, t * frequency, octaves, seed + 101) * amplitude,
    )
}
