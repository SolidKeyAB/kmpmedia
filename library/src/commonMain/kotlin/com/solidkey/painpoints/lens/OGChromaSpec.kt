package com.solidkey.painpoints.lens

import kotlinx.serialization.Serializable

/**
 * **Chromatic aberration** — R/G/B channel separation. Red shifts in the negative [angle] direction,
 * blue in the positive direction. Rendered by capturing the composable into 3 `GraphicsLayer`s, each
 * with a channel-only `ColorMatrix` and a spatial offset. Apply with [Modifier.ogChroma][ogChroma] or
 * the self-animating [OGChromaView]. Codec + presets on [OGLens].
 *
 * Works best on opaque content — with `BlendMode.Plus` and partial transparency the alpha can compound.
 */
@Serializable
data class OGChromaSpec(
    val name: String? = null,
    /** Max R–B channel offset in px. Clamped `0..20`. */
    val shift: Float = 4f,
    /** Direction of R→B separation in degrees (`0` = horizontal: R left, B right). */
    val angle: Float = 0f,
    /** Pulsation depth `0..1` — shift oscillates between `shift*(1-pulse)` and `shift`. Clamped `0..1`. */
    val pulse: Float = 0f,
    /** Pulsation speed in cycles per second. Clamped `0.1..10`. */
    val pulseSpeed: Float = 2f,
) {
    fun sanitized(): OGChromaSpec = copy(
        shift = shift.coerceIn(0f, 20f),
        pulse = pulse.coerceIn(0f, 1f),
        pulseSpeed = pulseSpeed.coerceIn(0.1f, 10f),
    )
}
