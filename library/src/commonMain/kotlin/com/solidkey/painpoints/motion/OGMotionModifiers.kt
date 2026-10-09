package com.solidkey.painpoints.motion

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Send a composable **gliding along** [path] by [progress] (`0..1`). The path is normalized `0..1`
 * inside [containerSize] (the container's pixel size — read it once, e.g. with `onSizeChanged` or
 * `BoxWithConstraints`), so `pointAt(progress)` places the element there. Set [orient] to also turn
 * the element to face its travel direction (computed in the container's pixel space, so it is exact
 * even in a non-square box), and [center] to ride the element's centre on the path (the default)
 * rather than its top-left.
 *
 * [progress] is a lambda read **inside** a `graphicsLayer`, so an animated value re-positions the
 * element each frame **without recomposing** it — cheap enough for many elements at 60fps. Until the
 * container size is known (still `Size.Zero`) the modifier is a no-op, so the element doesn't jump.
 *
 * ```
 * var box by remember { mutableStateOf(Size.Zero) }
 * val t by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(4000)))
 * Box(Modifier.fillMaxSize().onSizeChanged { box = it.toSize() }) {
 *     OGSVGView(..., modifier = Modifier.ogMotionPath(path, { t }, box, orient = true))
 * }
 * ```
 */
fun Modifier.ogMotionPath(
    path: OGMotionPath,
    progress: () -> Float,
    containerSize: Size,
    orient: Boolean = false,
    center: Boolean = true,
): Modifier = this.graphicsLayer {
    if (containerSize.isEmpty()) return@graphicsLayer
    val t = progress().coerceIn(0f, 1f)
    val p = path.pointAt(t)
    val px = p.x * containerSize.width
    val py = p.y * containerSize.height
    translationX = px - if (center) size.width / 2f else 0f
    translationY = py - if (center) size.height / 2f else 0f
    if (orient) rotationZ = path.tangentDegAt(t, containerSize.width, containerSize.height)
}

/**
 * Draw [path] as a stroke **revealed** from arc-length fraction [start] to [progress] (`0..1`) — the
 * core of **draw-on**. The path is normalized `0..1` and scaled to this [DrawScope]'s `size`; [width]
 * is in pixels. Animate [progress] `0 -> 1` and the line draws itself on; animate [start] too for a
 * travelling dash.
 */
fun DrawScope.drawOGStroke(
    path: OGMotionPath,
    progress: Float,
    color: Color,
    width: Float,
    cap: StrokeCap = StrokeCap.Round,
    start: Float = 0f,
) {
    val built = buildStrokePath(path, start, progress) ?: return
    drawPath(built, color = color, style = Stroke(width = width, cap = cap))
}

/** [drawOGStroke] with a [Brush] (e.g. a gradient) instead of a flat [color]. */
fun DrawScope.drawOGStroke(
    path: OGMotionPath,
    progress: Float,
    brush: Brush,
    width: Float,
    cap: StrokeCap = StrokeCap.Round,
    start: Float = 0f,
) {
    val built = buildStrokePath(path, start, progress) ?: return
    drawPath(built, brush = brush, style = Stroke(width = width, cap = cap))
}

// Build the revealed sub-path as a Compose Path in this DrawScope's pixel space, or null if nothing to draw.
private fun DrawScope.buildStrokePath(path: OGMotionPath, start: Float, end: Float): Path? {
    val pts = path.trimmed(start, end)
    if (pts.size < 2) return null
    val w = size.width
    val h = size.height
    return Path().apply {
        moveTo(pts[0].x * w, pts[0].y * h)
        for (i in 1 until pts.size) lineTo(pts[i].x * w, pts[i].y * h)
    }
}

/**
 * A composable that **draws [path] onto itself** as [progress] (`0..1`) advances — a line, logo
 * outline or signature animating into existence. [progress] is read inside the `Canvas` draw, so an
 * animated value redraws only this layer (no recomposition), holding 60fps.
 *
 * The path is normalized `0..1` and fills this composable's bounds; size it with [modifier].
 *
 * ```
 * val t by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(1500)))
 * OGDrawOnStroke(logoPath, { t }, Color(0xFFFFC107), strokeWidth = 6f, modifier = Modifier.size(160.dp))
 * ```
 */
@Composable
fun OGDrawOnStroke(
    path: OGMotionPath,
    progress: () -> Float,
    color: Color,
    strokeWidth: Float,
    modifier: Modifier = Modifier,
    cap: StrokeCap = StrokeCap.Round,
    start: () -> Float = { 0f },
) {
    Canvas(modifier) {
        drawOGStroke(path, progress().coerceIn(0f, 1f), color, strokeWidth, cap, start().coerceIn(0f, 1f))
    }
}
