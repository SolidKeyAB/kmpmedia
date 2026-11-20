package com.solidkey.painpoints.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.solidkey.painpoints.ai.toSpecColor

/**
 * Renders a [spec]'s **digital-rain** waterfall — columns of flickering glyphs falling downward, each
 * with a bright head and a fading tail. Set [playing] to `false` to freeze.
 *
 * The view is transparent when [OGDigitalRainSpec.bgAlpha] is `0`, making it a pure overlay. With
 * `bgAlpha > 0`, it paints an opaque (or semi-opaque) black background first — the classic Matrix look.
 *
 * Performance: glyphs are pre-measured once per spec/size change; each frame only calls [drawText] with
 * the cached [TextLayoutResult] and a computed alpha — no per-frame allocation.
 */
@Composable
fun OGDigitalRainView(
    spec: OGDigitalRainSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val density = LocalDensity.current
    val headColor = remember(spec) { spec.headColor.toSpecColor() ?: Color.White }
    val trailColor = remember(spec) { spec.trailColor.toSpecColor() ?: Color(0xFF00FF41) }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val textMeasurer = rememberTextMeasurer()

    val glyphLayouts: Pair<Map<Char, TextLayoutResult>, Map<Char, TextLayoutResult>>? =
        remember(spec, viewSize, density) {
            if (viewSize.width <= 0) return@remember null
            val colCount = spec.columnCount.coerceIn(1, 256)
            val cellPx = viewSize.width.toFloat() / colCount
            val fontSize: TextUnit = with(density) { (cellPx * 0.88f).toSp() }
            val headStyle = TextStyle(color = headColor, fontSize = fontSize, fontFamily = FontFamily.Monospace)
            val trailStyle = TextStyle(color = trailColor, fontSize = fontSize, fontFamily = FontFamily.Monospace)
            val glyphs = spec.glyphSet.toSet()
            Pair(
                glyphs.associateWith { ch -> textMeasurer.measure(ch.toString(), headStyle) },
                glyphs.associateWith { ch -> textMeasurer.measure(ch.toString(), trailStyle) },
            )
        }

    var elapsedSec by remember(spec) { mutableStateOf(0f) }
    LaunchedEffect(spec, playing) {
        if (!playing) return@LaunchedEffect
        val startMs = withFrameMillis { it }
        while (true) withFrameMillis { elapsedSec = (it - startMs) / 1000f }
    }

    Box(modifier.onSizeChanged { viewSize = it }) {
        Canvas(Modifier.matchParentSize().clipToBounds()) {
            if (spec.bgAlpha > 0f) drawRect(Color.Black, alpha = spec.bgAlpha.coerceIn(0f, 1f))
            val layouts = glyphLayouts ?: return@Canvas
            drawOGDigitalRainFrame(spec, elapsedSec, layouts.first, layouts.second)
        }
    }
}

/**
 * Draw one frame of [spec]'s digital rain into the current [DrawScope] at [elapsedSec], using the
 * pre-measured [headLayouts] (bright head glyphs) and [trailLayouts] (fading body glyphs).
 *
 * Build the layout maps once with [TextMeasurer] and pass them here each frame — no per-frame
 * measurement. Background fill and [OGDigitalRainSpec.bgAlpha] are handled by [OGDigitalRainView];
 * this function draws only the characters.
 */
internal fun DrawScope.drawOGDigitalRainFrame(
    spec: OGDigitalRainSpec,
    elapsedSec: Float,
    headLayouts: Map<Char, TextLayoutResult>,
    trailLayouts: Map<Char, TextLayoutResult>,
) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return
    val colCount = spec.columnCount.coerceIn(1, 256)
    val tailLen = spec.tailLength.coerceIn(1, 128).toFloat()
    val cellW = w / colCount
    val cellH = cellW
    val rowCount = (h / cellH).toInt() + 1
    val totalRows = rowCount + tailLen + 4f  // +4 gives gap between repeats
    val glyphs = spec.glyphSet
    if (glyphs.isEmpty()) return

    for (col in 0 until colCount) {
        val colHash = OGFxMath.hash01(col, spec.seed)
        val colSpeed = spec.speed * (1f + spec.speedJitter * (colHash * 2f - 1f))
        val phaseOffset = OGFxMath.hash01(col * 7, spec.seed) * totalRows
        val headRow = (elapsedSec * colSpeed + phaseOffset) % totalRows

        for (row in 0 until rowCount) {
            // Distance from head to this row in the column's scroll cycle (wrapping)
            val tailDist = (headRow - row + totalRows) % totalRows
            if (tailDist > tailLen) continue

            val fadeFrac = 1f - tailDist / tailLen
            val alpha = (fadeFrac * spec.alpha).coerceIn(0f, 1f)
            if (alpha < 0.015f) continue

            // Per-cell staggered flicker: each cell has its own phase offset so glyphs don't
            // all change simultaneously
            val cellPhase = (elapsedSec * spec.glyphChangeRate +
                    OGFxMath.hash01(col * 1337 + row * 7, spec.seed)).toInt()
            val glyphIdx = (OGFxMath.hash01(col * 999 + row, cellPhase) * glyphs.length)
                .toInt().coerceIn(0, glyphs.length - 1)
            val glyph = glyphs[glyphIdx]

            val isHead = tailDist < 1f
            val layout = if (isHead) headLayouts[glyph] else trailLayouts[glyph]
            layout ?: continue

            // Centre the glyph in its cell
            val x = col * cellW + (cellW - layout.size.width) * 0.5f
            val y = row * cellH + (cellH - layout.size.height) * 0.5f
            drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(x, y), alpha = alpha)
        }
    }
}
