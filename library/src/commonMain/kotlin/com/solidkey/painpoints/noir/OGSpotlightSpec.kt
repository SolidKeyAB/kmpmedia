package com.solidkey.painpoints.noir

import kotlinx.serialization.Serializable

/**
 * **Scene spotlight** — a dark scrim with a soft circular lit hole, optionally swinging. Rendered via
 * [Modifier.ogSpotlight][ogSpotlight] or the self-animating [OGSpotlightView]. Codec + presets on [OGNoir].
 *
 * The scrim is a single radial gradient drawn over the composable: transparent at the light centre,
 * softly fading to [scrimOpacity] beyond [radius]. One `drawCircle` call; no shaders or saveLayer.
 */
@Serializable
data class OGSpotlightSpec(
    val name: String? = null,
    /** Darkness colour of the unlit areas — any SVG hex string. */
    val scrimColor: String = "#000000",
    /** Opacity of unlit areas `0..1`. `0.72` = moody noir; `0.95` = nearly black stage. Clamped `0..1`. */
    val scrimOpacity: Float = 0.72f,
    /** Spotlight centre, normalized `0..1` on X. */
    val centerX: Float = 0.5f,
    /** Spotlight centre, normalized `0..1` on Y. */
    val centerY: Float = 0.5f,
    /** Lit-circle radius as a fraction of `min(width, height)`. Clamped `0.05..1`. */
    val radius: Float = 0.35f,
    /** Edge softness: `0` = razor-hard; `1` = very gradual falloff. Clamped `0..1`. */
    val softness: Float = 0.30f,
    /** Horizontal swing amplitude as a fraction of width (0 = static). Clamped `0..0.4`. */
    val swingAmp: Float = 0f,
    /** Swing speed in cycles per second. Clamped `0.01..2`. */
    val swingSpeed: Float = 0.2f,
) {
    fun sanitized(): OGSpotlightSpec = copy(
        scrimOpacity = scrimOpacity.coerceIn(0f, 1f),
        centerX = centerX.coerceIn(0f, 1f),
        centerY = centerY.coerceIn(0f, 1f),
        radius = radius.coerceIn(0.05f, 1f),
        softness = softness.coerceIn(0f, 1f),
        swingAmp = swingAmp.coerceIn(0f, 0.4f),
        swingSpeed = swingSpeed.coerceIn(0.01f, 2f),
    )
}
