package com.solidkey.painpoints.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lay a **fixed, movable glass lens** ([spec]) over [content] — a magnifier / minifier / coloured glass
 * (see [OGGlassSpec]). The lens stays put where you leave it, and — crucially — its body is
 * **pointer-transparent**, so you can keep interacting with the content underneath (place a word, tap a
 * cell) while the glass is parked over an area. Move it only by dragging its **grip handle**; dismiss it
 * with the **✕** (wired to [onRemove]). Set [enabled] to `false` to hide the glass entirely.
 *
 * The content is recorded once into a `GraphicsLayer` and the lens redraws it scaled about its centre,
 * clipped to a circle, plus tint + rim + sheen — GPU work only, no shader, cross-platform. Place it as
 * the outermost wrapper around whatever should be magnifiable.
 */
@Composable
fun OGGlass(
    spec: OGGlassSpec,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onRemove: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val s = remember(spec) { spec.sanitized() }
    val tint = remember(s) { s.tint.toSpecColor() ?: Color.Transparent }
    val rim = remember(s) { s.rimColor.toSpecColor() ?: Color.White }
    val layer = rememberGraphicsLayer()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var center by remember { mutableStateOf(Offset.Unspecified) }

    Box(modifier.onSizeChanged { size = it }) {
        // The content, with the lens drawn ON TOP but WITHOUT consuming any pointer input — so taps and
        // drags fall straight through to the content beneath the glass.
        Box(
            Modifier.fillMaxSize().drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                val lens = if (enabled) {
                    computeLens(s, resolveCenter(center, this.size.width, this.size.height), this.size.width, this.size.height)
                } else null
                if (lens != null) {
                    // Draw the base content everywhere EXCEPT the lens disc, so inside the circle only the
                    // magnified copy shows. Without this hole the un-magnified base would show through wherever
                    // the content is transparent (e.g. beside a sprite) → a "doubled" ghost under the lens.
                    clipPath(lens.path, clipOp = ClipOp.Difference) { drawLayer(layer) }
                    drawGlass(s, layer, lens, tint, rim)
                } else {
                    drawLayer(layer)
                }
            },
        ) { content() }

        // Position the ✕ and grip off the SAME lens shape the Canvas draws (computeLens coerces the centre
        // on screen). Recomputing the raw centre here would leave the handles behind when the lens is pushed
        // to an edge — they'd drift off the rim. The rim may now spill past an edge, so clamp each knob's
        // anchor onto the screen too, keeping it tappable even when its corner of the rim is off-screen.
        val handleLens = if (enabled) {
            computeLens(s, resolveCenter(center, size.width.toFloat(), size.height.toFloat()), size.width.toFloat(), size.height.toFloat())
        } else null
        if (handleLens != null) {
            val c = handleLens.center
            val halfW = handleLens.halfW
            val halfH = handleLens.halfH
            fun anchor(dx: Float, dy: Float, knob: Int): IntOffset {
                val ax = (c.x + dx).coerceIn(knob / 2f, size.width - knob / 2f)
                val ay = (c.y + dy).coerceIn(knob / 2f, size.height - knob / 2f)
                return IntOffset(ax.roundToInt() - knob / 2, ay.roundToInt() - knob / 2)
            }
            // Grip handle on the lower-right of the rim — the ONLY thing that moves the lens.
            val grip = 44
            Box(
                Modifier
                    .offset { anchor(halfW * 0.72f, halfH * 0.72f, grip) }
                    .size(grip.dp)
                    .clip(CircleShape)
                    .background(rim.copy(alpha = 0.92f))
                    .pointerInput(size) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val from = if (center == Offset.Unspecified) c else center
                            center = from + drag
                        }
                    },
            ) { Canvas(Modifier.fillMaxSize()) { drawMoveGlyph(GripInk) } }

            // Remove ✕ on the upper-right of the rim.
            if (onRemove != null) {
                val x = 34
                Box(
                    Modifier
                        .offset { anchor(halfW * 0.72f, -halfH * 0.72f, x) }
                        .size(x.dp)
                        .clip(CircleShape)
                        .background(rim.copy(alpha = 0.92f))
                        .clickable { onRemove() },
                ) { Canvas(Modifier.fillMaxSize()) { drawCloseGlyph(Color.Black) } }
            }
        }
    }
}

/** Lens centre in px: the stored [center], or the middle of a [w]×[h] view before the first move. */
private fun resolveCenter(center: Offset, w: Float, h: Float): Offset =
    if (center == Offset.Unspecified) Offset(w / 2f, h / 2f) else center

/** The resolved lens shape — its on-screen [center], half-[width]/half-[height] and the rounded-rect
 *  [path] — shared by the base-content hole-punch and the lens draw so they line up exactly. A circle is
 *  just the square (half-width == half-height), fully-rounded case. */
private class LensShape(val center: Offset, val halfW: Float, val halfH: Float, val path: Path)

/** Resolve the lens shape for a [w]×[h] view, clamping [rawCenter] so the lens **centre** stays on screen
 *  — the rim is allowed to spill past an edge so the loupe can be dragged right onto edge content (a wide
 *  pill lens whose centre was pinned ≥ halfW could never reach the first column, magnifying its leading
 *  letters off-screen → "UYRUK" read as "YRUK"). [OGGlassSpec.radius] sets the half-height, [aspect] the
 *  width, [cornerRadius] the end rounding. Returns `null` when the view or size is degenerate. */
private fun computeLens(s: OGGlassSpec, rawCenter: Offset, w: Float, h: Float): LensShape? {
    if (w <= 0f || h <= 0f) return null
    val halfH = s.radius * min(w, h)
    if (halfH <= 0f) return null
    val halfW = halfH * s.aspect
    val cx = rawCenter.x.coerceIn(0f, w)
    val cy = rawCenter.y.coerceIn(0f, h)
    val c = Offset(cx, cy)
    val corner = s.cornerRadius * minOf(halfW, halfH)
    val path = Path().apply {
        addRoundRect(RoundRect(Rect(c.x - halfW, c.y - halfH, c.x + halfW, c.y + halfH), CornerRadius(corner, corner)))
    }
    return LensShape(c, halfW, halfH, path)
}

/** Draw the lens: magnified (scaled) copy of [layer] clipped to the lens shape, then tint, rim and sheen. */
private fun DrawScope.drawGlass(
    s: OGGlassSpec,
    layer: GraphicsLayer,
    lens: LensShape,
    tint: Color,
    rim: Color,
) {
    val c = lens.center
    // The magnified (or minified) content under the glass: scale the recorded layer about the lens centre.
    clipPath(lens.path) {
        scale(s.zoom, s.zoom, pivot = c) { drawLayer(layer) }
    }
    // Coloured glass: lay the tint over the lensed content (clear tint → nothing).
    val tintA = tint.alpha * s.tintStrength
    if (tintA > 0f) drawPath(lens.path, tint.copy(alpha = tintA))
    // Specular sheen across the top-left, selling the curved-glass look.
    if (s.highlight > 0f) {
        val sheen = Brush.radialGradient(
            colorStops = arrayOf(0f to Color.White.copy(alpha = 0.55f * s.highlight), 1f to Color.Transparent),
            center = Offset(c.x - lens.halfW * 0.3f, c.y - lens.halfH * 0.3f),
            radius = maxOf(lens.halfW, lens.halfH) * 1.25f,
        )
        drawPath(lens.path, brush = sheen)
    }
    // The glass rim / frame.
    val rimW = (s.rimWidth * lens.halfH)
    if (rimW > 0f && s.rimAlpha > 0f) {
        drawPath(lens.path, rim.copy(alpha = s.rimAlpha), style = Stroke(width = rimW))
    }
}

/** Fixed dark ink for the grip dots — a readable charcoal that contrasts on the light knob regardless of
 *  the lens tint (a pale tint would wash the dots out). */
private val GripInk = Color(0xFF3A3A3A)

/** A **drag-handle** glyph for the grip — a 2×3 grid of dots, the universal "grab here to move" affordance.
 *  (Deliberately NOT a "+" or arrows, which read as zoom / direction rather than "hold me".) */
private fun DrawScope.drawMoveGlyph(color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val dot = size.minDimension * 0.07f
    val gx = size.minDimension * 0.17f  // column half-spacing
    val gy = size.minDimension * 0.17f  // row spacing
    for (col in intArrayOf(-1, 1)) {
        for (row in intArrayOf(-1, 0, 1)) {
            drawCircle(color, radius = dot, center = Offset(c.x + col * gx, c.y + row * gy))
        }
    }
}

/** An ✕ glyph for the remove button. */
private fun DrawScope.drawCloseGlyph(color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val a = size.minDimension * 0.24f
    val sw = size.minDimension * 0.09f
    drawLine(color, Offset(c.x - a, c.y - a), Offset(c.x + a, c.y + a), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(c.x - a, c.y + a), Offset(c.x + a, c.y - a), strokeWidth = sw, cap = StrokeCap.Round)
}
