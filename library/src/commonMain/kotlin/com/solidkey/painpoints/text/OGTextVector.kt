package com.solidkey.painpoints.text

import com.solidkey.painpoints.shape.OGPoint

/**
 * A font selection for vectorizing text. Uses the platform's default sans family unless [family]
 * names one the platform has.
 *
 * Note: Android and iOS ship **different** default fonts, so vectorized glyphs are *look-equivalent*,
 * not pixel-identical, across the two platforms (just as any native text is). Pass a [family] the
 * platform knows (`"serif"`, `"monospace"`, …) for a closer match, or bundle your own and name it.
 */
data class OGTextFont(
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** A platform font family name (e.g. `"serif"`, `"monospace"`); `null` = the platform default sans. */
    val family: String? = null,
)

/**
 * Text turned into **vector outlines**: [contours] are the glyph contours (each a closed polyline of
 * [OGPoint]s) in **em units** — `1.0` == one em of the font — with the baseline at `y = 0` and `y`
 * increasing downward (ascenders negative, descenders positive). [widthEm] is the total advance width;
 * [topEm] / [bottomEm] bound the inked height. Run the contours through an
 * [com.solidkey.painpoints.style.OGStyle] (roughen / boil / …) and draw them — see [OGStyledText].
 *
 * Produced by [ogVectorizeText], which is **expensive** (platform text layout + bezier flattening):
 * vectorize **once** and cache it, then style + draw the cached contours each frame. Because the units
 * are ems, a style amplitude like `0.02` means the same "2% of an em" at any [OGStyledText] font size.
 */
data class OGTextOutline(
    val contours: List<List<OGPoint>>,
    val widthEm: Float,
    val topEm: Float,
    val bottomEm: Float,
) {
    /** The inked height in em (`bottomEm - topEm`). */
    val heightEm: Float get() = bottomEm - topEm

    companion object {
        /** An empty outline (no glyphs). */
        val Empty = OGTextOutline(emptyList(), 0f, 0f, 0f)
    }
}

/**
 * Vectorize [text] in [font] into glyph [OGTextOutline] contours (em units). [quality] trades point
 * density (curve smoothness) for cost — roughly the samples per em of contour length; `~48–96` is a
 * good range. Platform-backed: Android `Paint.getTextPath` + `PathMeasure`, iOS Skia `Font.getPath`.
 *
 * Call it off the hot path (once per `text` / `font` / `quality`); [OGStyledText] caches it for you.
 */
expect fun ogVectorizeText(text: String, font: OGTextFont = OGTextFont(), quality: Int = 64): OGTextOutline
