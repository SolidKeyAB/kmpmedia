package com.solidkey.painpoints.lens

import kotlinx.serialization.Serializable

/**
 * **Light leak** — a coloured radial glow bleeding in from a corner/edge, as if light entered a film
 * camera. Animated opacity pulse simulates the organic variation of a real film leak. Apply with
 * [Modifier.ogLightLeak][ogLightLeak] or [OGLightLeakView]. Codec + presets on [OGLens].
 */
@Serializable
data class OGLightLeakSpec(
    val name: String? = null,
    /** Leak colour — any SVG hex string. Warm orange/red = classic film; cool cyan/teal = digital. */
    val color: String = "#FF8040",
    /** Peak opacity of the leak. Clamped `0..1`. */
    val opacity: Float = 0.35f,
    /** Leak origin X: `0` = left edge, `1` = right edge. Clamped `0..1`. */
    val cornerX: Float = 0f,
    /** Leak origin Y: `0` = top edge, `1` = bottom edge. Clamped `0..1`. */
    val cornerY: Float = 0f,
    /** How far the leak spreads as a fraction of the frame diagonal. Clamped `0.1..1`. */
    val spread: Float = 0.60f,
    /** Pulsation depth `0..1`. Clamped `0..1`. */
    val pulse: Float = 0.40f,
    /** Pulsation speed in cycles per second. Clamped `0.1..5`. */
    val pulseSpeed: Float = 0.50f,
) {
    fun sanitized(): OGLightLeakSpec = copy(
        opacity = opacity.coerceIn(0f, 1f),
        cornerX = cornerX.coerceIn(0f, 1f),
        cornerY = cornerY.coerceIn(0f, 1f),
        spread = spread.coerceIn(0.1f, 1f),
        pulse = pulse.coerceIn(0f, 1f),
        pulseSpeed = pulseSpeed.coerceIn(0.1f, 5f),
    )
}
