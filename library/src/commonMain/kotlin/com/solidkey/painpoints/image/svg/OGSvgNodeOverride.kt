package com.solidkey.painpoints.image.svg

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.solidkey.painpoints.image.OGColor

/**
 * A runtime override for a single SVG node, addressed by its `id`.
 *
 * Every field is optional — a `null` field means "leave the node's original value untouched".
 * Pass a `Map<nodeId, OGSvgNodeOverride>` to [OGSVGView] and the SVG redraws live whenever the
 * map changes (bound to Compose state), turning a static `.svg` into a live template — gauges,
 * charts, progress rings, status icons — without re-parsing the source.
 *
 * Paint changes ([fill]/[stroke]/[strokeWidth]) are folded into the node's style; transform
 * changes ([translateX]/[translateY]/[rotation]/[scaleX]/[scaleY]) are applied uniformly at draw
 * time (so they work on any shape type), pivoting around ([rotationCx], [rotationCy]) in SVG
 * user units — defaulting to the viewBox centre.
 */
@Immutable
data class OGSvgNodeOverride(
    val fill: Color? = null,
    val stroke: Color? = null,
    val strokeWidth: Float? = null,
    val translateX: Float? = null,
    val translateY: Float? = null,
    /** Rotation in degrees, applied on top of the node's own transform. */
    val rotation: Float? = null,
    /** Pivot for [rotation]/[scaleX]/[scaleY] in SVG user units; null = viewBox centre. */
    val rotationCx: Float? = null,
    val rotationCy: Float? = null,
    val scaleX: Float? = null,
    val scaleY: Float? = null,
) {
    /** True if any paint field is set (folded into the node's style at shape-prep time). */
    val hasPaint: Boolean
        get() = fill != null || stroke != null || strokeWidth != null

    /** True if any transform field is set (applied as a draw-time wrapper). */
    val hasTransform: Boolean
        get() = translateX != null || translateY != null ||
            rotation != null || scaleX != null || scaleY != null
}

/**
 * Returns [element] unchanged when there is no paint override for its id; otherwise a shallow
 * copy whose [OGSVGTreeElement.style] has the override's fill / stroke / stroke-width folded in
 * (reusing [OGSVGStyle.combine], which keeps the base value wherever the override is null).
 *
 * Transforms are intentionally NOT folded here — they're applied uniformly at draw time. The
 * returned copy shares the original children / shapes / animations lists, so tree traversal is
 * unaffected. Pure and side-effect-free, so it can be unit-tested off-device.
 */
internal fun applyPaintOverride(
    element: OGSVGTreeElement,
    override: OGSvgNodeOverride?,
): OGSVGTreeElement {
    if (override == null || !override.hasPaint) return element
    val merged = element.style.combine(
        OGSVGStyle(
            fill = override.fill?.let { OGColor(it) },
            stroke = override.stroke?.let { OGColor(it) },
            strokeWidth = override.strokeWidth,
        )
    )
    return element.copy(style = merged)
}
