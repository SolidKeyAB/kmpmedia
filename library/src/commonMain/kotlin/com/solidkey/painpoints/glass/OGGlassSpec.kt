package com.solidkey.painpoints.glass

/**
 * **Glass** — a round "look-behind-glass" lens laid over any content: a **magnifier** ([zoom] > 1), a
 * **minifier** ([zoom] < 1), and/or **coloured glass** ([tint]). Plain data, like the rest of the
 * library's packs; decode + presets on [OGGlass], draw with [OGGlassView].
 *
 * **How it works (60fps, no shader).** The content under the glass is recorded once into a
 * `GraphicsLayer` (the same trick as `Modifier.ogGlow`); the lens then re-draws that layer **scaled about
 * the lens centre**, clipped to a circle, so whatever sits beneath appears magnified (or shrunk). A
 * translucent [tint] + a soft rim + a specular highlight sell the "piece of glass" look. No per-pixel
 * work and no runtime shader (the library floors those out), so it's cheap and cross-platform.
 *
 * An accessibility win as much as an effect: drop it over a board, a photo or a word and older eyes get a
 * movable magnifier; flip [zoom] under 1 for a fit-more minifier, or [tint] it for a colour-filter glass.
 */
@kotlinx.serialization.Serializable
data class OGGlassSpec(
    val name: String? = null,
    /** Magnification under the glass: `> 1` magnifies, `< 1` minifies, `1` is a plain (tinted) pane. */
    val zoom: Float = 2f,
    /** Lens radius as a fraction of the shorter side of the view (`0..1`). */
    val radius: Float = 0.22f,
    /** Glass tint — any SVG colour string. Fully transparent (`#00000000`) = clear magnifier. */
    val tint: String = "#00000000",
    /** How strongly the [tint] is laid over the magnified content (`0..1`). */
    val tintStrength: Float = 0.25f,
    /** The glass edge / frame colour — any SVG colour string. */
    val rimColor: String = "#FFFFFF",
    /** Rim thickness as a fraction of the lens radius (`0..0.5`). */
    val rimWidth: Float = 0.05f,
    /** Rim opacity (`0..1`). */
    val rimAlpha: Float = 0.8f,
    /** Specular sheen strength across the top-left of the glass (`0..1`, `0` = none). */
    val highlight: Float = 0.3f,
) {
    /** Clamp every field into its sane range (lenient model/designer input never breaks a frame). */
    fun sanitized(): OGGlassSpec = copy(
        zoom = zoom.coerceIn(0.2f, 6f),
        radius = radius.coerceIn(0.03f, 1f),
        tintStrength = tintStrength.coerceIn(0f, 1f),
        rimWidth = rimWidth.coerceIn(0f, 0.5f),
        rimAlpha = rimAlpha.coerceIn(0f, 1f),
        highlight = highlight.coerceIn(0f, 1f),
    )
}
