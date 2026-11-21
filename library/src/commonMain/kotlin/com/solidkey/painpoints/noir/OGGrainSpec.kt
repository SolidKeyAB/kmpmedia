package com.solidkey.painpoints.noir

import kotlinx.serialization.Serializable

/**
 * **Film grain overlay** — scattered light-noise dots at a time-driven seed that changes at [speed]
 * per second, giving authentic celluloid chatter. Rendered via [Modifier.ogGrain][ogGrain] or the
 * self-animating [OGGrainView]. Codec + presets on [OGNoir].
 */
@Serializable
data class OGGrainSpec(
    val name: String? = null,
    /** Dot density and brightness — scales both dot count and per-dot opacity. Clamped `0..1`. */
    val intensity: Float = 0.18f,
    /** Grain pattern changes per second. `24` = film-like chatter; `1` = slow texture drift. Clamped `1..120`. */
    val speed: Float = 24f,
    /** Grain dot radius in px. `0.8` = fine sand; `2.5` = coarse. Clamped `0.3..5`. */
    val scale: Float = 1.0f,
    /** Dot colour — any SVG hex string. `#FFFFFF` = neutral silver grain. */
    val color: String = "#FFFFFF",
) {
    fun sanitized(): OGGrainSpec = copy(
        intensity = intensity.coerceIn(0f, 1f),
        speed = speed.coerceIn(1f, 120f),
        scale = scale.coerceIn(0.3f, 5f),
    )
}
