package com.solidkey.painpoints.noir

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ── Film grain ────────────────────────────────────────────────────────────────

/**
 * **Film grain overlay** — scattered light-noise dots whose pattern changes at [OGGrainSpec.speed]
 * per second, giving authentic celluloid chatter. Draws over this composable's content.
 *
 * [timeSec] drives the grain chatter. Drive it from your animation loop or use the self-animating
 * [OGGrainView] wrapper.
 */
fun Modifier.ogGrain(spec: OGGrainSpec, timeSec: Float): Modifier {
    val s = spec.sanitized()
    return drawWithContent {
        drawContent()
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f || s.intensity <= 0f) return@drawWithContent
        val grainColor = s.color.toSpecColor() ?: Color.White
        drawGrainLayer(s, timeSec, grainColor)
    }
}

/**
 * Self-animating film grain overlay. Wraps [ogGrain] with an internal frame loop — set [playing] to
 * `false` to freeze. Draws grain dots over a transparent canvas; stack it on top of your content
 * in a `Box` or composable hierarchy.
 */
@Composable
fun OGGrainView(
    spec: OGGrainSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val s = remember(spec) { spec.sanitized() }
    val grainColor = remember(s) { s.color.toSpecColor() ?: Color.White }
    var elapsed by remember(s) { mutableStateOf(0f) }
    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }
    Canvas(modifier.clipToBounds()) {
        val w = size.width; val h = size.height
        if (w > 0f && h > 0f && s.intensity > 0f) drawGrainLayer(s, elapsed / 1000f, grainColor)
    }
}

private fun DrawScope.drawGrainLayer(spec: OGGrainSpec, timeSec: Float, color: Color) {
    val w = size.width; val h = size.height
    val frameSeed = (timeSec * spec.speed).toInt()
    val dotCount = (spec.intensity * 2500f).toInt().coerceIn(50, 2500)
    val rng = kotlin.random.Random(frameSeed)
    repeat(dotCount) {
        val x = rng.nextFloat() * w
        val y = rng.nextFloat() * h
        val alpha = ((rng.nextFloat() * 0.65f + 0.35f) * spec.intensity).coerceIn(0f, 1f)
        drawCircle(color = color.copy(alpha = alpha), radius = spec.scale, center = Offset(x, y))
    }
}

// ── Venetian blinds ───────────────────────────────────────────────────────────

/**
 * **Venetian blind stripes** — angled dark bands drawn over this composable, with optional slow
 * oscillation. Draws on top of content; ideal for noir window light.
 *
 * [timeSec] drives the oscillation. Drive from your animation loop or use [OGBlindsView].
 */
fun Modifier.ogBlinds(spec: OGBlindSpec, timeSec: Float): Modifier {
    val s = spec.sanitized()
    return drawWithContent {
        drawContent()
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f || s.opacity <= 0f) return@drawWithContent
        val stripeColor = s.color.toSpecColor() ?: Color.Black
        drawBlindsLayer(s, timeSec, stripeColor)
    }
}

/**
 * Self-animating venetian blinds overlay. Stack on top of content in a `Box`.
 */
@Composable
fun OGBlindsView(
    spec: OGBlindSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val s = remember(spec) { spec.sanitized() }
    val stripeColor = remember(s) { s.color.toSpecColor() ?: Color.Black }
    var elapsed by remember(s) { mutableStateOf(0f) }
    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }
    Canvas(modifier.clipToBounds()) {
        val w = size.width; val h = size.height
        if (w > 0f && h > 0f && s.opacity > 0f) drawBlindsLayer(s, elapsed / 1000f, stripeColor)
    }
}

private fun DrawScope.drawBlindsLayer(spec: OGBlindSpec, timeSec: Float, color: Color) {
    val w = size.width; val h = size.height
    val cx = w * 0.5f; val cy = h * 0.5f
    val diag = sqrt(w * w + h * h)

    val currentAngleDeg = spec.angleDeg +
            spec.oscillationDeg * sin(timeSec * spec.oscillationSpeed * TAU)

    withTransform({
        rotate(degrees = currentAngleDeg, pivot = Offset(cx, cy))
    }) {
        // In rotated space, draw horizontal bands covering the full diagonal extent
        val pitch = diag / spec.slats
        val stripeH = pitch * (1f - spec.gapRatio)
        val startY = cy - diag * 0.5f
        for (i in 0 until spec.slats) {
            drawRect(
                color = color.copy(alpha = spec.opacity),
                topLeft = Offset(cx - diag * 0.5f, startY + i * pitch),
                size = Size(diag, stripeH),
            )
        }
    }
}

// ── Spotlight ─────────────────────────────────────────────────────────────────

/**
 * **Scene spotlight** — a dark scrim with a soft circular lit hole drawn over this composable,
 * optionally swinging.
 *
 * [timeSec] drives the swing. Drive from your animation loop or use [OGSpotlightView].
 */
fun Modifier.ogSpotlight(spec: OGSpotlightSpec, timeSec: Float): Modifier {
    val s = spec.sanitized()
    return drawWithContent {
        drawContent()
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f || s.scrimOpacity <= 0f) return@drawWithContent
        val scrimColor = s.scrimColor.toSpecColor() ?: Color.Black
        drawSpotlightLayer(s, timeSec, scrimColor)
    }
}

/**
 * Self-animating spotlight overlay. Renders a dark scrim with a lit hole over a transparent
 * canvas; stack on top of content in a `Box`.
 */
@Composable
fun OGSpotlightView(
    spec: OGSpotlightSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val s = remember(spec) { spec.sanitized() }
    val scrimColor = remember(s) { s.scrimColor.toSpecColor() ?: Color.Black }
    var elapsed by remember(s) { mutableStateOf(0f) }
    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }
    Canvas(modifier.clipToBounds()) {
        val w = size.width; val h = size.height
        if (w > 0f && h > 0f && s.scrimOpacity > 0f) drawSpotlightLayer(s, elapsed / 1000f, scrimColor)
    }
}

internal fun DrawScope.drawSpotlightLayer(
    spec: OGSpotlightSpec,
    timeSec: Float,
    scrimColor: Color,
) {
    val w = size.width; val h = size.height

    // Swinging centre
    val swingX = spec.swingAmp * sin(timeSec * spec.swingSpeed * TAU) * w
    val cx = spec.centerX * w + swingX
    val cy = spec.centerY * h

    val litR = spec.radius * min(w, h)
    val softR = litR * (1f + spec.softness * 1.2f)
    // Large enough circle to cover corners from an off-center spotlight
    val diag = sqrt(w * w + h * h)

    val litFrac = litR / diag
    val softFrac = (softR / diag).coerceAtMost(0.98f)

    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                litFrac to Color.Transparent,
                softFrac to scrimColor.copy(alpha = spec.scrimOpacity * 0.55f),
                (softFrac + (1f - softFrac) * 0.4f) to scrimColor.copy(alpha = spec.scrimOpacity),
                1.0f to scrimColor.copy(alpha = spec.scrimOpacity),
            ),
            center = Offset(cx, cy),
            radius = diag,
        ),
        radius = diag,
        center = Offset(cx, cy),
    )
}

private val TAU = (2.0 * PI).toFloat()
