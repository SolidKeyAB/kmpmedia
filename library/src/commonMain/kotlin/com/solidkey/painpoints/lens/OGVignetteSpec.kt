package com.solidkey.painpoints.lens

import kotlinx.serialization.Serializable

/**
 * **Vignette** — dark corner fade. Single radial gradient from transparent centre to [color] at the
 * corners. Static; apply with [Modifier.ogVignette][ogVignette]. Codec + presets on [OGLens].
 */
@Serializable
data class OGVignetteSpec(
    val name: String? = null,
    /** Corner darkness `0..1`. */
    val intensity: Float = 0.65f,
    /** Transparent-centre radius as a fraction of `min(w, h)`. Larger = bigger lit area. Clamped `0..1.5`. */
    val radius: Float = 0.60f,
    /** Gradient softness: `0` = hard edge, `1` = very gradual. Clamped `0..1`. */
    val softness: Float = 0.55f,
    /** Corner colour — any SVG hex string. */
    val color: String = "#000000",
) {
    fun sanitized(): OGVignetteSpec = copy(
        intensity = intensity.coerceIn(0f, 1f),
        radius = radius.coerceIn(0f, 1.5f),
        softness = softness.coerceIn(0f, 1f),
    )
}
