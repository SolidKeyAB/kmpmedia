package com.solidkey.painpoints.control

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solidkey.painpoints.ai.toSpecColor
import com.solidkey.painpoints.gesture.OGPressEffect
import com.solidkey.painpoints.gesture.ogButton
import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.style.OGStyle
import com.solidkey.painpoints.style.OGStyleOp
import com.solidkey.painpoints.style.OGStyleSpec
import com.solidkey.painpoints.text.OGStyledText
import com.solidkey.painpoints.text.OGTextFont

/** The composable's fallback label style: a subtle hand-drawn roughen, so a lib button is inked by
 *  default. Pass `spec.labelStyle` (or the `labelStyle` arg) to override — an empty spec = crisp. */
private val DefaultControlLabelStyle: OGStyle =
    OGStyleSpec(ops = listOf(OGStyleOp(op = "roughen", amplitude = 0.02f, detail = 3))).toStyle()

/**
 * A **one-call skinnable control** — the whole hand-drawn button in a single composable, so apps stop
 * re-assembling `background` + a `drawWithCache` ink edge + [ogButton] + styled text + a garnish every
 * time. It bundles, in order:
 *
 *  1. a **fill** — a flat [backgroundColor], a gradient [fill] brush, or a data-defined
 *     [OGControlSpec.fill] gradient (brush wins over spec wins over the flat colour);
 *  2. a **hand-drawn ink outline** ([OGControlSpec.outline]) sketched just inside the fill, cached;
 *  3. a tap + **pressed-state visual** via [ogButton] (effect chosen by [OGControlSpec.pressEffect]);
 *  4. an optional **seasonal garnish** ([OGControlSpec.garnish], e.g. Halloween slime) — or a custom
 *     [garnish] content slot if you want to draw your own;
 *  5. a **styled vector label** ([OGStyledText]) centred on top, inked by [OGControlSpec.labelStyle]
 *     (or [labelStyle]).
 *
 * `enabled == false` dims the fill to [OGControlSpec.disabledAlpha], halves the label, fades the ink,
 * suppresses the garnish and makes the tap a consumed no-op (the button never leaks taps behind it).
 *
 * ### Cost
 * The vectorized label and the roughened outline are cached (parse-once); only a moving garnish
 * redraws per frame, and it is small (one Canvas per control). It clears the same 60fps gate as the
 * rest of the library and is identical on Android & iOS.
 *
 * ### Usage
 * ```
 * OGControl(
 *     label = "Play word",
 *     backgroundColor = Accent,
 *     contentColor = Color.White,
 *     enabled = canPlay,
 *     seed = 1,
 *     spec = OGControls.preset("slime")!!,   // or OGControlSpec()
 *     modifier = Modifier.weight(1f),
 * ) { controller.submit() }
 * ```
 *
 * @param label the button text.
 * @param onClick fired on a tap released within the bounds.
 * @param backgroundColor the flat fill (used when no [fill] brush and no [OGControlSpec.fill]).
 * @param contentColor the label colour (and the base the ink edge is tinted from).
 * @param fill an explicit gradient/brush fill that overrides both colours and the spec gradient.
 * @param enabled when `false`: dimmed, label halved, garnish hidden, tap a consumed no-op.
 * @param seed varies the sketch edge and garnish so adjacent buttons differ (e.g. 1..6).
 * @param spec the control skin (geometry, outline, label style, gradient, garnish).
 * @param labelStyle the fallback label style when [OGControlSpec.labelStyle] is `null`.
 * @param font the label font (bold / italic / family).
 * @param garnish a custom overlay slot drawn above the fill and below the label (wins over the spec
 *   garnish); handy for an app-specific flourish the data garnish can't express.
 * @param contentDescription spoken label for TalkBack / VoiceOver (defaults to [label]).
 * @param labelPadding horizontal breathing room reserved around the label. This is what gives a
 *   width-less (shrink-wrapped) control its natural width, so the sketch edge never clips the first
 *   or last glyph. Callers that drive the width explicitly (`Modifier.width`/`weight`) still get this
 *   inset INSIDE that width. Set to `0.dp` for an edge-to-edge label.
 */
@Composable
fun OGControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF54A69B),
    contentColor: Color = Color.White,
    fill: Brush? = null,
    enabled: Boolean = true,
    seed: Int = 0,
    spec: OGControlSpec = OGControlSpec(),
    labelStyle: OGStyle = DefaultControlLabelStyle,
    font: OGTextFont = OGTextFont(),
    garnish: (@Composable BoxScope.() -> Unit)? = null,
    contentDescription: String? = label,
    labelPadding: Dp = 14.dp,
) {
    val style = remember(spec.labelStyle, labelStyle) { spec.labelStyle?.toStyle() ?: labelStyle }
    val effect = remember(spec.pressEffect) { ogControlPressEffect(spec.pressEffect) }
    val fillColor = if (enabled) backgroundColor else backgroundColor.copy(alpha = spec.disabledAlpha)
    val textColor = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
    val inkColor = spec.outline?.let {
        contentColor.copy(alpha = if (enabled) it.inkAlpha else it.inkAlpha * 0.44f)
    }
    val specFill = spec.fill

    Box(
        modifier
            .height(spec.height.dp)
            .clip(RoundedCornerShape(spec.cornerRadius.dp))
            .drawWithCache {
                val brush: Brush? = fill ?: specFill?.sanitized()?.toBrush(0f, 0f, size.width, size.height)
                val o = spec.outline
                val outlinePath: Path? = o?.let {
                    val pts = ogControlOutline(
                        width = size.width,
                        height = size.height,
                        cornerRadius = spec.cornerRadius.dp.toPx(),
                        inset = it.inset.dp.toPx(),
                        amplitude = size.height * it.amplitude,
                        detail = it.detail,
                        seed = seed,
                    )
                    pts.toClosedPath()
                }
                val stroke = o?.let {
                    Stroke(width = it.width.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                }
                onDrawBehind {
                    if (brush != null) drawRect(brush, alpha = if (enabled) 1f else spec.disabledAlpha)
                    else drawRect(fillColor)
                    if (outlinePath != null && inkColor != null && stroke != null) {
                        drawPath(outlinePath, inkColor, style = stroke)
                    }
                }
            }
            .ogButton(
                enabled = enabled,
                pressEffect = effect,
                contentDescription = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) {
            when {
                garnish != null -> garnish()
                spec.garnish != null -> OGControlGarnish(spec.garnish, seed)
            }
        }
        Box(Modifier.padding(horizontal = labelPadding)) {
            OGStyledText(
                text = label,
                style = style,
                fontSize = spec.fontSize.sp,
                color = textColor,
                font = font,
                animated = false,
            )
        }
    }
}

/** The [OGPressEffect] named by an [OGControlSpec.pressEffect] string. */
fun ogControlPressEffect(name: String): OGPressEffect = when (name.trim().lowercase()) {
    "dim" -> OGPressEffect.Dim()
    "brutalist" -> OGPressEffect.Brutalist()
    "none" -> OGPressEffect.None
    else -> OGPressEffect.Scale()
}

/** The live seasonal garnish — green goo / snow / … oozing off one edge, drawn from [ogGarnishRing]. */
@Composable
private fun OGControlGarnish(spec: OGGarnishSpec, seed: Int) {
    var timeMs by remember { mutableStateOf(0L) }
    LaunchedEffect(spec, seed) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { timeMs = it - start }
    }
    val body = remember(spec.color) { spec.color.toSpecColor() ?: Color.Transparent }
    val deep = remember(spec.deepColor) { spec.deepColor?.toSpecColor() }
    val shine = remember(spec.shineColor) { spec.shineColor?.toSpecColor() }
    Canvas(Modifier.fillMaxSize()) {
        val ring = ogGarnishRing(spec, size.width, size.height, seed, timeMs)
        if (ring.size < 3) return@Canvas
        drawPath(ring.toClosedPath(), body)
        if (deep != null && ring.size > 3) {
            // The drippy inner edge = everything after the two straight-edge corners.
            val rim = Path()
            for (i in 2 until ring.size) {
                val p = ring[i]
                if (i == 2) rim.moveTo(p.x, p.y) else rim.lineTo(p.x, p.y)
            }
            drawPath(rim, deep, style = Stroke(width = size.height * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (shine != null) {
            val yy = if (spec.isBottom()) size.height * 0.94f else size.height * 0.06f
            val streak = Path().apply {
                moveTo(size.width * 0.1f, yy)
                lineTo(size.width * 0.4f, yy)
            }
            drawPath(streak, shine.copy(alpha = 0.7f), style = Stroke(width = size.height * 0.05f, cap = StrokeCap.Round))
        }
    }
}

private fun List<OGPoint>.toClosedPath(): Path = Path().apply {
    if (isEmpty()) return@apply
    moveTo(this@toClosedPath[0].x, this@toClosedPath[0].y)
    for (i in 1 until size) lineTo(this@toClosedPath[i].x, this@toClosedPath[i].y)
    close()
}
