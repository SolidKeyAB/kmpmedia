package com.solidkey.painpoints.lens

import kotlinx.serialization.Serializable

/**
 * **Lens flare** — a central starburst glow with radiating arms and secondary bokeh discs along the
 * optical axis. Pure Canvas 2D: central radial gradient + arm `Path` strokes + axis-aligned bokeh
 * circles. Apply with [Modifier.ogLensFlare][ogLensFlare] or [OGLensFlareView].
 * Codec + presets on [OGLens].
 */
@Serializable
data class OGLensFlareSpec(
    val name: String? = null,
    /** Primary flare colour. */
    val color: String = "#FFFFFF",
    /** Secondary bokeh element tint. */
    val tint: String = "#FFD080",
    /** Overall opacity. Clamped `0..1`. */
    val opacity: Float = 0.70f,
    /** Light source X, normalised `0..1`. Clamped `0..1`. */
    val centerX: Float = 0.75f,
    /** Light source Y, normalised `0..1`. Clamped `0..1`. */
    val centerY: Float = 0.25f,
    /** Overall size scale multiplier. Clamped `0.1..3`. */
    val scale: Float = 1.0f,
    /** Star arm length as a fraction of `min(w, h)`. Clamped `0.05..1`. */
    val streakLength: Float = 0.50f,
    /** Number of star arms radiating from the light source. Clamped `2..12`. */
    val streakCount: Int = 6,
    /** Number of secondary bokeh discs along the optical axis. Clamped `0..10`. */
    val bokehCount: Int = 5,
    /** Subtle twinkle pulsation depth `0..1`. Clamped `0..1`. */
    val pulse: Float = 0.25f,
    /** Pulsation speed in cycles per second. Clamped `0.1..5`. */
    val pulseSpeed: Float = 2.0f,
) {
    fun sanitized(): OGLensFlareSpec = copy(
        opacity = opacity.coerceIn(0f, 1f),
        centerX = centerX.coerceIn(0f, 1f),
        centerY = centerY.coerceIn(0f, 1f),
        scale = scale.coerceIn(0.1f, 3f),
        streakLength = streakLength.coerceIn(0.05f, 1f),
        streakCount = streakCount.coerceIn(2, 12),
        bokehCount = bokehCount.coerceIn(0, 10),
        pulse = pulse.coerceIn(0f, 1f),
        pulseSpeed = pulseSpeed.coerceIn(0.1f, 5f),
    )
}
