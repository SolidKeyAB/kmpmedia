package com.solidkey.painpoints.shape

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * A single vertex in **normalized** coordinates: `(0,0)` = top-left of the target box,
 * `(1,1)` = bottom-right. Values outside `0..1` are clamped when the polygon is rasterized,
 * so a segmentation model / detector or a finger-drawn outline can hand over raw coordinates
 * without worrying about the exact box size.
 */
data class OGPoint(val x: Float, val y: Float)

/**
 * A free-form clip shape built from an ordered list of [points] joined by straight **line
 * segments** — a polygon "lasso". This is the AI-friendly primitive: the outline is just data
 * (a `List<OGPoint>` in `0..1` space), so the vertices an on-image drawing OR a segmentation
 * model produces drop straight in with no external image editor. The polygon is always closed
 * (the last point links back to the first).
 *
 * Clipping is the same `Modifier.clip(shape)` GPU mask the built-in [OGShapeType]s use, so for a
 * still image it is drawn once = no runtime cost. Pass it to
 * [com.solidkey.painpoints.image.OGImageView]'s `clipShape` (or to any `Modifier.clip`) to keep
 * only the region inside the outline — e.g. lasso a head out of a photo, no external tool.
 *
 * Fewer than 3 points is degenerate (no enclosed area) and yields an empty outline (nothing
 * shown) rather than throwing.
 */
class OGPolygonShape(val points: List<OGPoint>) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val abs = scalePolygonPoints(points, size.width, size.height)
        val path = Path().apply {
            if (abs.size >= 3) {
                moveTo(abs[0].x, abs[0].y)
                for (i in 1 until abs.size) lineTo(abs[i].x, abs[i].y)
                close()
            }
        }
        return Outline.Generic(path)
    }

    companion object {
        /** Convenience builder from raw `(x, y)` pairs in normalized `0..1` space. */
        fun of(vararg points: Pair<Float, Float>): OGPolygonShape =
            OGPolygonShape(points.map { OGPoint(it.first, it.second) })
    }
}

/**
 * Maps normalized [points] (`0..1`) onto a [width]×[height] box, clamping every coordinate into
 * range so out-of-bounds input can't escape the box. Returns an empty list for a degenerate
 * polygon (fewer than 3 points). Pure + platform-independent so it can be unit-tested directly;
 * [OGPolygonShape.createOutline] is a thin wrapper over it.
 */
fun scalePolygonPoints(points: List<OGPoint>, width: Float, height: Float): List<OGPoint> {
    if (points.size < 3) return emptyList()
    return points.map { p ->
        OGPoint(p.x.coerceIn(0f, 1f) * width, p.y.coerceIn(0f, 1f) * height)
    }
}
