package com.solidkey.painpoints.fx

import kotlinx.serialization.Serializable

/**
 * Describes an aurora borealis / australis curtain effect — multi-harmonic vertical rays with
 * altitude-stratified colour (emerald 557.7nm at ~100km → cyan → violet/magenta 630nm at ~200km+),
 * anisotropic simplex-noise shear, and additive-screen blending.
 *
 * Algorithmically: N curtain columns, each a subdivided vertical strip whose horizontal position
 * follows several harmonics of a sine wave (curvature increases toward the bottom — like a hanging
 * curtain). Simplex fBm shear adds organic life along the length. Drawn with [BlendMode.Screen]
 * for luminous glow without over-saturation.
 *
 * Pure `commonMain` math — zero shaders, zero per-pixel work, 60fps.
 *
 * Presets and codec on [OGAurora]. Rendered by [OGAuroraView] / [drawOGAuroraFrame].
 */
@Serializable
data class OGAuroraSpec(
    val name: String? = null,
    /** Number of parallel curtain columns (more = denser, richer curtain). */
    val columns: Int = 7,
    /** Bottom-of-curtain colour — oxygen 557.7nm at ~100km altitude, typically emerald green. */
    val bottomColor: String = "#00E676",
    /** Mid-curtain colour — the transition zone. */
    val midColor: String = "#00BCD4",
    /** Top-of-curtain colour — oxygen 630nm at ~200km+, typically violet/magenta. */
    val topColor: String = "#CE93D8",
    /** Number of harmonic layers added to each column's curvature. More = more complex waving. */
    val harmonics: Int = 3,
    /** Spatial frequency of the primary sway (higher = more oscillations along the curtain). */
    val frequency: Float = 2.6f,
    /** Maximum horizontal sway amplitude as a fraction of view width. */
    val amplitude: Float = 0.06f,
    /** Simplex fBm shear strength (random local bending) as fraction of view width. */
    val shear: Float = 0.16f,
    /** Horizontal scroll speed of the shear noise (frac/sec). */
    val shearSpeed: Float = 0.18f,
    /** Curtain column width as a fraction of inter-column spacing (>1 = overlapping columns). */
    val columnWidthFactor: Float = 1.55f,
    /** Top of the curtain as a fraction of view height (0 = top edge, 0.3 = 30% down). */
    val curtainY: Float = 0.06f,
    /** Curtain height as a fraction of view height. */
    val curtainHeight: Float = 0.74f,
    /** Animation speed — multiplier on all time-driven motion. */
    val speed: Float = 0.22f,
    /** Overall opacity (0..1). */
    val opacity: Float = 0.72f,
    val seed: Int = 0,
) {
    fun sanitized() = copy(
        columns = columns.coerceIn(2, 40),
        harmonics = harmonics.coerceIn(1, 8),
        frequency = frequency.coerceIn(0.5f, 12f),
        amplitude = amplitude.coerceIn(0f, 0.5f),
        shear = shear.coerceIn(0f, 0.6f),
        shearSpeed = shearSpeed.coerceIn(0f, 4f),
        columnWidthFactor = columnWidthFactor.coerceIn(0.5f, 4f),
        curtainY = curtainY.coerceIn(0f, 0.9f),
        curtainHeight = curtainHeight.coerceIn(0.05f, 1f),
        speed = speed.coerceIn(0f, 5f),
        opacity = opacity.coerceIn(0f, 1f),
    )
}
