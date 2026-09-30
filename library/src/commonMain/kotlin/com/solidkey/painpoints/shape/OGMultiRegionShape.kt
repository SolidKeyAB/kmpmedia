package com.solidkey.painpoints.shape

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * How the [regions][OGClipRegion] of an [OGMultiRegionShape] combine into one clip outline.
 * Maps to Compose's [PathOperation]: [UNION] shows every region, [INTERSECT] only their overlap,
 * [DIFFERENCE] the first region minus the rest, [XOR] everything except the overlap.
 */
enum class OGClipOp { UNION, INTERSECT, DIFFERENCE, XOR }

/**
 * One sub-shape of an [OGMultiRegionShape], placed inside a **normalized** sub-rectangle of the clip
 * box: `(0,0)` = top-left, `(1,1)` = bottom-right — the same `0..1` space as [OGPoint]. The [shape]
 * is laid out to fill that sub-rect, so two `CircleShape` regions at `left..right` `0f..0.5f` and
 * `0.5f..1f` give two side-by-side circular windows. Values outside `0..1` are clamped, and the
 * rect is normalized so `left/right` (or `top/bottom`) may be given in either order.
 *
 * Defaults cover the full box, so `OGClipRegion(shape)` is simply that shape at full size.
 */
data class OGClipRegion(
    val shape: Shape,
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
)

/**
 * A clip [Shape] made of **more than one region** — several placed sub-shapes combined with a path
 * [op] (union / intersect / difference / xor). It lets a single [OGImageView.clipShape] /
 * [com.solidkey.painpoints.video.playing.OGPlayerConfig] `clipShape` / `Modifier.clip` mask keep
 * *two or more* windows at once: e.g. a photo showing through two circular portholes, a diamond with
 * a circular bite taken out, or a lasso plus a spotlight.
 *
 * Because it is an ordinary Compose `Shape`, it drops into the **exact same clip slot** a single
 * shape already uses — **no media-surface API changed** — so it works for a photo, an animated GIF
 * and a running video alike.
 *
 * ### Cost
 * The regions are placed and their outlines combined with [Path.op] **once per size** (inside
 * `createOutline`, keyed by Compose off the shape's value equality), never per frame — the same
 * budget as any other static clip. Give the regions stable identities (`remember { … }`, the
 * `CircleShape` singleton, or the value-equal built-ins / [OGPolygonShape]) so the outline isn't
 * rebuilt needlessly.
 */
class OGMultiRegionShape(
    val regions: List<OGClipRegion>,
    val op: OGClipOp = OGClipOp.UNION,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val paths = regions.mapNotNull { placeRegionPath(it, size, layoutDirection, density) }
        if (paths.isEmpty()) return Outline.Generic(Path())

        var acc = paths[0]
        if (paths.size > 1) {
            val pathOp = op.toPathOperation()
            for (i in 1 until paths.size) {
                val result = Path()
                result.op(acc, paths[i], pathOp)   // result = acc <op> paths[i]
                acc = result
            }
        }
        return Outline.Generic(acc)
    }

    // Value equality (by regions + op) so a re-created multi-region clip is a Compose-skip / cache hit,
    // exactly like the other KMPMedia shapes — provided the inner shapes compare by value too.
    override fun equals(other: Any?): Boolean =
        other is OGMultiRegionShape && other.regions == regions && other.op == op

    override fun hashCode(): Int = 31 * regions.hashCode() + op.hashCode()

    companion object {
        /** Union of several shapes, each filling the whole box (a quick multi-window overlay). */
        fun union(vararg shapes: Shape): OGMultiRegionShape =
            OGMultiRegionShape(shapes.map { OGClipRegion(it) }, OGClipOp.UNION)

        /** Combine several already-placed [regions] with [op] (default [OGClipOp.UNION]). */
        fun of(vararg regions: OGClipRegion, op: OGClipOp = OGClipOp.UNION): OGMultiRegionShape =
            OGMultiRegionShape(regions.toList(), op)
    }
}

// ---------------------------------------------------------------------------------------------
// Pure geometry — no Density / Path, so it is directly unit-testable off-device.
// ---------------------------------------------------------------------------------------------

/**
 * Maps a region's normalized (`0..1`) placement onto a [width]×[height] box, clamping every edge
 * into range and normalizing order so `left <= right` / `top <= bottom`. Pure + platform-independent
 * so it can be unit-tested directly; [OGMultiRegionShape.createOutline] uses it to place each region.
 */
internal fun regionRect(region: OGClipRegion, width: Float, height: Float): Rect {
    val l = region.left.coerceIn(0f, 1f) * width
    val r = region.right.coerceIn(0f, 1f) * width
    val t = region.top.coerceIn(0f, 1f) * height
    val b = region.bottom.coerceIn(0f, 1f) * height
    return Rect(minOf(l, r), minOf(t, b), maxOf(l, r), maxOf(t, b))
}

// ---------------------------------------------------------------------------------------------
// Platform-backed geometry (Path).
// ---------------------------------------------------------------------------------------------

/** Builds one region's outline at its sub-rect size and translates it into place. Null if empty. */
private fun placeRegionPath(
    region: OGClipRegion, size: Size, ld: LayoutDirection, density: Density,
): Path? {
    val rect = regionRect(region, size.width, size.height)
    if (rect.width <= 0f || rect.height <= 0f) return null
    val outline = region.shape.createOutline(Size(rect.width, rect.height), ld, density)
    val path = outlineToPath(outline)
    path.translate(Offset(rect.left, rect.top))
    return path
}

/** Any [Outline] → a fresh, mutable [Path] we can translate and op-combine without touching the source. */
private fun outlineToPath(outline: Outline): Path = when (outline) {
    is Outline.Generic -> Path().apply { addPath(outline.path) }
    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
}

private fun OGClipOp.toPathOperation(): PathOperation = when (this) {
    OGClipOp.UNION -> PathOperation.Union
    OGClipOp.INTERSECT -> PathOperation.Intersect
    OGClipOp.DIFFERENCE -> PathOperation.Difference
    OGClipOp.XOR -> PathOperation.Xor
}
