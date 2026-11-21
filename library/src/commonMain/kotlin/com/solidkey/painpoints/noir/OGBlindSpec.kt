package com.solidkey.painpoints.noir

import kotlinx.serialization.Serializable

/**
 * **Venetian blind shadow stripes** — angled dark bands drawn across any composable, with optional slow
 * oscillation. Rendered via [Modifier.ogBlinds][ogBlinds] or the self-animating [OGBlindsView].
 * Codec + presets on [OGNoir].
 */
@Serializable
data class OGBlindSpec(
    val name: String? = null,
    /** Stripe colour — any SVG hex string. */
    val color: String = "#000000",
    /** Stripe opacity `0..1`. */
    val opacity: Float = 0.65f,
    /** Stripe angle in degrees. `-38` = classic noir diagonal; `0` = horizontal; `90` = vertical. */
    val angleDeg: Float = -38f,
    /** Number of dark slat bands across the composable. Clamped `1..20`. */
    val slats: Int = 7,
    /** Gap width as a fraction of stripe pitch (`0` = no gap, `0.5` = equal stripe+gap). Clamped `0..0.9`. */
    val gapRatio: Float = 0.45f,
    /** Peak oscillation amplitude in degrees (0 = static). Clamped `0..45`. */
    val oscillationDeg: Float = 0f,
    /** Oscillation cycles per second. Clamped `0.01..2`. */
    val oscillationSpeed: Float = 0.15f,
) {
    fun sanitized(): OGBlindSpec = copy(
        opacity = opacity.coerceIn(0f, 1f),
        slats = slats.coerceIn(1, 20),
        gapRatio = gapRatio.coerceIn(0f, 0.9f),
        oscillationDeg = oscillationDeg.coerceIn(0f, 45f),
        oscillationSpeed = oscillationSpeed.coerceIn(0.01f, 2f),
    )
}
