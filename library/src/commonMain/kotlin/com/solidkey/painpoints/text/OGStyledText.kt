package com.solidkey.painpoints.text

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.style.OGStyle

/**
 * Run [style] over each glyph contour at [timeMs], returning the styled contours in em units — the
 * pure core behind [OGStyledText] (directly unit-testable without a platform text engine).
 */
fun OGTextOutline.styled(style: OGStyle, timeMs: Long): List<List<OGPoint>> =
    contours.map { style.apply(it, timeMs).outline }

/**
 * Draw [text] as **styled vector letters**: each glyph's outline is run through [style]
 * (`roughen` / `boil` / `smooth` / `wave` / …) so the letters themselves are hand-inked and — for a
 * time-varying style like boil or wave — alive. Perfect for a hand-drawn word-game tile.
 *
 * The expensive vectorization is cached (done once per `text` / `font` / `quality`); only the cheap
 * per-contour styling runs each frame, so it holds 60fps. The letters are filled (a terminal
 * `pixelate` op is ignored here — its pre-pixelate outline is filled instead).
 *
 * @param style the drawing style applied to every glyph, e.g.
 *   `OGStyles.decode("""{"ops":[{"op":"roughen","amplitude":0.02,"detail":4},{"op":"boil","amplitude":0.012}]}""")`.
 * @param fontSize the em size; glyph em-units scale by this.
 * @param color the fill colour of the letters.
 * @param animated drive the style on the frame clock (needed for `boil` / `wave`); `false` = a static frame.
 */
@Composable
fun OGStyledText(
    text: String,
    style: OGStyle,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 48.sp,
    color: Color = Color.Black,
    font: OGTextFont = OGTextFont(),
    quality: Int = 64,
    animated: Boolean = true,
) {
    val outline = remember(text, font, quality) { ogVectorizeText(text, font, quality) }

    var timeMs by remember { mutableStateOf(0L) }
    if (animated) {
        LaunchedEffect(outline) {
            val start = withFrameMillis { it }
            while (true) withFrameMillis { timeMs = it - start }
        }
    }

    val density = LocalDensity.current
    val px = with(density) { fontSize.toPx() }
    val wDp = with(density) { (outline.widthEm * px).toDp() }
    val hDp = with(density) { (outline.heightEm * px).toDp() }

    Canvas(modifier.size(wDp, hDp)) {
        if (outline.contours.isEmpty()) return@Canvas
        val path = Path()
        for (contour in outline.contours) {
            val styled = style.apply(contour, timeMs).outline
            if (styled.size < 2) continue
            path.moveTo(styled[0].x * px, (styled[0].y - outline.topEm) * px)
            for (i in 1 until styled.size) {
                path.lineTo(styled[i].x * px, (styled[i].y - outline.topEm) * px)
            }
            path.close()
        }
        drawPath(path, color)
    }
}
