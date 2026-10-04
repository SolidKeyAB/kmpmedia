package com.solidkey.painpoints.ai

import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.shape.OGPolygonShape
import kotlinx.serialization.Serializable

/**
 * One placed region in an [OGSceneSpec]: a closed polygon in normalized `0..1` image space, plus
 * an optional short [label] and a representative [fill] color (an SVG color string). It is the
 * serializable, multi-region sibling of [OGPolygonSpec] — the payload a vision model emits for
 * "trace these parts of the image".
 *
 * [toShape] drops straight into any `clipShape`; the [fill] / [label] are advisory metadata the
 * app can use to tint or name the region.
 */
@Serializable
data class OGPlacedShapeSpec(
    val points: List<OGPointSpec>,
    val label: String? = null,
    /** A representative color as an SVG color string (`#RRGGBB`, `rgb(...)`, or a name); advisory. */
    val fill: String? = null,
) {
    /** The lasso as a plain [OGPolygonSpec] (drops the label/fill metadata). */
    fun toPolygonSpec(): OGPolygonSpec = OGPolygonSpec(points)

    /** Build the live clip [OGPolygonShape]. Fewer than 3 points is degenerate (renders empty). */
    fun toShape(): OGPolygonShape = OGPolygonShape(points.map { OGPoint(it.x, it.y) })

    companion object {
        /** Capture a live [OGPolygonShape] (and optional metadata) back into its serializable form. */
        fun from(shape: OGPolygonShape, label: String? = null, fill: String? = null): OGPlacedShapeSpec =
            OGPlacedShapeSpec(shape.points.map { OGPointSpec(it.x, it.y) }, label, fill)
    }
}

/**
 * A multi-region vector scene in normalized `0..1` image space — the "image → vector scene"
 * payload. A vision model looks at an image (a photo, or a frame of a running scene) and returns
 * this: an ordered set of [OGPlacedShapeSpec] regions. [toShapes] turns them into live
 * [OGPolygonShape]s ready to drop into `clipShape` slots (one per region), so an agent can, e.g.,
 * "cut the head, the torso and each arm out of this photo" in one reply.
 *
 * Like the rest of the AI-interop layer this is a plain `@Serializable` DTO decoded once at
 * generate time, never per frame, so it never touches the 60fps hot path.
 */
@Serializable
data class OGSceneSpec(val shapes: List<OGPlacedShapeSpec> = emptyList()) {
    /** Every region as a live clip [OGPolygonShape], in order. */
    fun toShapes(): List<OGPolygonShape> = shapes.map { it.toShape() }

    /** The regions' labels (nulls dropped), in order — handy for naming the cut-outs. */
    fun labels(): List<String> = shapes.mapNotNull { it.label }

    companion object {
        /** Build a scene from placed shapes. */
        fun of(vararg shapes: OGPlacedShapeSpec): OGSceneSpec = OGSceneSpec(shapes.toList())
    }
}
