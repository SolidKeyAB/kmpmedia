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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
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
                drawLayer(layer)
                if (enabled) drawGlass(s, layer, resolveCenter(center, this.size.width, this.size.height), tint, rim)
            },
        ) { content() }

        if (enabled && size.width > 0 && size.height > 0) {
            val r = s.radius * min(size.width, size.height).toFloat()
            val c = resolveCenter(center, size.width.toFloat(), size.height.toFloat())
            // Grip handle on the lower-right of the rim — the ONLY thing that moves the lens.
            val grip = 44
            Box(
                Modifier
                    .offset { IntOffset((c.x + r * 0.72f).roundToInt() - grip / 2, (c.y + r * 0.72f).roundToInt() - grip / 2) }
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
            ) { Canvas(Modifier.fillMaxSize()) { drawMoveGlyph(tint.takeIf { it.alpha > 0f } ?: Color.Black) } }

            // Remove ✕ on the upper-right of the rim.
            if (onRemove != null) {
                val x = 34
                Box(
                    Modifier
                        .offset { IntOffset((c.x + r * 0.72f).roundToInt() - x / 2, (c.y - r * 0.72f).roundToInt() - x / 2) }
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

/** Draw the lens: magnified (scaled) copy of [layer] clipped to a circle, then tint, rim and sheen. */
private fun DrawScope.drawGlass(
    s: OGGlassSpec,
    layer: GraphicsLayer,
    rawCenter: Offset,
    tint: Color,
    rim: Color,
) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return
    val r = s.radius * min(w, h)
    if (r <= 0f) return
    // Keep the whole lens on screen.
    val cx = if (w > 2 * r) rawCenter.x.coerceIn(r, w - r) else w / 2f
    val cy = if (h > 2 * r) rawCenter.y.coerceIn(r, h - r) else h / 2f
    val c = Offset(cx, cy)

    val circle = Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
    // The magnified (or minified) content under the glass: scale the recorded layer about the lens centre.
    clipPath(circle) {
        scale(s.zoom, s.zoom, pivot = c) { drawLayer(layer) }
    }
    // Coloured glass: lay the tint over the lensed content (clear tint → nothing).
    val tintA = tint.alpha * s.tintStrength
    if (tintA > 0f) drawCircle(tint.copy(alpha = tintA), radius = r, center = c)
    // Specular sheen across the top-left, selling the curved-glass look.
    if (s.highlight > 0f) {
        val sheen = Brush.radialGradient(
            colorStops = arrayOf(0f to Color.White.copy(alpha = 0.55f * s.highlight), 1f to Color.Transparent),
            center = Offset(c.x - r * 0.3f, c.y - r * 0.3f),
            radius = r * 1.25f,
        )
        drawCircle(brush = sheen, radius = r, center = c)
    }
    // The glass rim / frame.
    val rimW = (s.rimWidth * r)
    if (rimW > 0f && s.rimAlpha > 0f) {
        drawCircle(rim.copy(alpha = s.rimAlpha), radius = r, center = c, style = Stroke(width = rimW))
    }
}

/** A four-arrow "move" glyph for the grip handle. */
private fun DrawScope.drawMoveGlyph(color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val a = size.minDimension * 0.28f
    val sw = size.minDimension * 0.07f
    drawLine(color, Offset(c.x - a, c.y), Offset(c.x + a, c.y), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(c.x, c.y - a), Offset(c.x, c.y + a), strokeWidth = sw, cap = StrokeCap.Round)
}

/** An ✕ glyph for the remove button. */
private fun DrawScope.drawCloseGlyph(color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val a = size.minDimension * 0.24f
    val sw = size.minDimension * 0.09f
    drawLine(color, Offset(c.x - a, c.y - a), Offset(c.x + a, c.y + a), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(c.x - a, c.y + a), Offset(c.x + a, c.y - a), strokeWidth = sw, cap = StrokeCap.Round)
}
