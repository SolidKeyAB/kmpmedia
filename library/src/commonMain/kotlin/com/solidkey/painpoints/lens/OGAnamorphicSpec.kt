package com.solidkey.painpoints.lens

import kotlinx.serialization.Serializable

/**
 * **Anamorphic lens streak** — the horizontal flare line produced by anamorphic cinema lenses when
 * light hits the optics. Drawn as a thin bright core with a soft horizontal halo, fading to transparent
 * at both ends. Apply with [Modifier.ogAnamorphic][ogAnamorphic] or [OGAnamorphicView].
 * Codec + presets on [OGLens].
 */
@Serializable
data class OGAnamorphicSpec(
    val name: String? = null,
    /** Streak colour — any SVG hex string. `#80C8FF` = classic blue; `#FFC040` = amber. */
    val color: String = "#80C8FF",
    /** Peak streak opacity. Clamped `0..1`. */
    val opacity: Float = 0.65f,
    /** Core line width in px. Clamped `0.5..10`. */
    val width: Float = 2.5f,
    /** Vertical position of the streak, normalised `0..1`. `0.5` = centre. Clamped `0..1`. */
    val centerY: Float = 0.5f,
    /** Horizontal reach as fraction of frame width (`1.0` = full width). Clamped `0.1..1`. */
    val spread: Float = 0.90f,
    /** Pulsation depth `0..1`. Clamped `0..1`. */
    val pulse: Float = 0.60f,
    /** Pulsation speed in cycles per second. Clamped `0.1..5`. */
    val pulseSpeed: Float = 1.5f,
) {
    fun sanitized(): OGAnamorphicSpec = copy(
        opacity = opacity.coerceIn(0f, 1f),
        width = width.coerceIn(0.5f, 10f),
        centerY = centerY.coerceIn(0f, 1f),
        spread = spread.coerceIn(0.1f, 1f),
        pulse = pulse.coerceIn(0f, 1f),
        pulseSpeed = pulseSpeed.coerceIn(0.1f, 5f),
    )
}
