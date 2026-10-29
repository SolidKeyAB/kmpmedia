package com.solidkey.painpoints.glass

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.min

/**
 * Lay a draggable **glass lens** ([spec]) over [content] — a movable magnifier / minifier / coloured
 * glass (see [OGGlassSpec]). The user drags the lens with a finger; it starts centred. Set [enabled] to
 * `false` to hide the glass and show the content untouched (wire it to a "magnifier" toggle button).
 *
 * The content is recorded once into a `GraphicsLayer` and the lens redraws it scaled about its centre,
 * clipped to a circle, plus a tint + rim + sheen — GPU work only, no shader, cross-platform. Place it as
 * the outermost wrapper around whatever should be magnifiable (a board, a photo, a card).
 */
@Composable
fun OGGlass(
    spec: OGGlassSpec,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val s = remember(spec) { spec.sanitized() }
    val tint = remember(s) { s.tint.toSpecColor() ?: Color.Transparent }
    val rim = remember(s) { s.rimColor.toSpecColor() ?: Color.White }
    val layer = rememberGraphicsLayer()
    var center by remember { mutableStateOf(Offset.Unspecified) }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (enabled) Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { pos -> center = pos },
                            onDrag = { change, drag ->
                                change.consume()
                                val from = if (center == Offset.Unspecified) change.position else center
                                center = from + drag
                            },
                        )
                    } else Modifier,
                )
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)                 // the real content, unchanged
                    if (enabled) drawGlass(s, layer, center, tint, rim)
                },
        ) { content() }
    }
}

/** Draw the lens: magnified (scaled) copy of [layer] clipped to a circle, then tint, rim and sheen. */
private fun DrawScope.drawGlass(
    s: OGGlassSpec,
    layer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    rawCenter: Offset,
    tint: Color,
    rim: Color,
) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return
    val r = s.radius * min(w, h)
    if (r <= 0f) return
    // Keep the whole lens on screen (clamp its centre), starting from the middle before any drag.
    val cx = if (w > 2 * r) rawCenter.x.coerceIn(r, w - r) else w / 2f
    val cy = if (h > 2 * r) rawCenter.y.coerceIn(r, h - r) else h / 2f
    val c = if (rawCenter == Offset.Unspecified) Offset(w / 2f, h / 2f) else Offset(cx, cy)

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
