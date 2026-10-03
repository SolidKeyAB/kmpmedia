package com.solidkey.painpoints.depth

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Parallax — the motion half of depth. Where [ogDepth] places content on the front-to-back axis with
 * z-order, blur and dim, [ogParallax] makes that same axis **move**: as a *viewpoint* shifts (a scroll
 * position, a device tilt, a drag — whatever the app feeds in), layers at different [depth]s slide by
 * different amounts, so the scene reads as having real depth.
 *
 * Motion is relative to a *focal plane* ([focalDepth]), consistent with [ogDepth]: a layer **at** the
 * focal plane doesn't move, layers **nearer** than it (`depth > focalDepth`) move *with* the viewpoint,
 * layers **farther** (`depth < focalDepth`) move *against* it — the classic "background drifts the other
 * way" look. With the default `focalDepth = 0f`, the far plane is pinned and nearer layers move most.
 *
 * Runs on one `graphicsLayer` translation (a GPU transform — no recomposition, no layout pass), so it
 * holds 60fps and composes with [ogDepth]: chain `.ogDepth(...).ogParallax(...)` on the same content.
 */
fun Modifier.ogParallax(
    depth: Float,
    viewpoint: Offset,
    focalDepth: Float = 0f,
    config: OGParallaxConfig = OGParallaxConfig(),
): Modifier = this.graphicsLayer {
    val maxPx = config.maxShift.toPx()
    translationX = if (config.horizontal) parallaxShift(depth, focalDepth, viewpoint.x, maxPx) else 0f
    translationY = if (config.vertical) parallaxShift(depth, focalDepth, viewpoint.y, maxPx) else 0f
}

/**
 * Tuning for [ogParallax].
 *
 * @param maxShift the translation applied at maximum depth distance (`|depth - focalDepth| == 1`) and
 *   full viewpoint deflection (`±1`). Larger = a stronger 3D feel.
 * @param horizontal / @param vertical which axes react to the viewpoint.
 */
data class OGParallaxConfig(
    val maxShift: Dp = 24.dp,
    val horizontal: Boolean = true,
    val vertical: Boolean = true,
)

/**
 * The pure parallax translation (px) for one axis: `(depth - focalDepth) * viewpoint * maxShiftPx`,
 * with depth/focal clamped to `0..1` and viewpoint clamped to `-1..1`. Split out so the motion is
 * unit-testable without a Compose runtime; [ogParallax] is a thin `graphicsLayer` wrapper over it.
 */
fun parallaxShift(depth: Float, focalDepth: Float, viewpoint: Float, maxShiftPx: Float): Float =
    (clampDepth(depth) - clampDepth(focalDepth)) * viewpoint.coerceIn(-1f, 1f) * maxShiftPx
