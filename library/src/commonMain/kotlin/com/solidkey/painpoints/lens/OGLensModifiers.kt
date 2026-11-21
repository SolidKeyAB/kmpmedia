package com.solidkey.painpoints.lens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ── Vignette ─────────────────────────────────────────────────────────────────

/**
 * **Vignette** — dark corner fade drawn over this composable's content. Static; no [timeSec] needed.
 */
fun Modifier.ogVignette(spec: OGVignetteSpec): Modifier {
    val s = spec.sanitized()
    return drawWithContent {
        drawContent()
        val vColor = s.color.toSpecColor() ?: Color.Black
        if (s.intensity > 0f) drawVignetteLayer(s, vColor)
    }
}

private fun DrawScope.drawVignetteLayer(spec: OGVignetteSpec, color: Color) {
    val w = size.width; val h = size.height
    val cx = w * 0.5f; val cy = h * 0.5f
    val halfDiag = sqrt(w * w + h * h) * 0.5f
    if (halfDiag <= 0f) return

    val litEdge = spec.radius * min(w, h)
    val litFrac = (litEdge / halfDiag).coerceIn(0f, 0.94f)
    val softFrac = (litFrac + (1f - litFrac) * spec.softness).coerceIn(litFrac + 0.01f, 0.97f)

    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                litFrac to Color.Transparent,
                softFrac to color.copy(alpha = spec.intensity * 0.55f),
                1f to color.copy(alpha = spec.intensity),
            ),
            center = Offset(cx, cy),
            radius = halfDiag,
        ),
        radius = halfDiag,
        center = Offset(cx, cy),
    )
}

// ── Chromatic aberration ──────────────────────────────────────────────────────

/**
 * **Chromatic aberration** — R/G/B channel separation applied over this composable's content. Captures
 * the content into 3 `GraphicsLayer`s with channel-isolating `ColorMatrix` filters, then composites
 * them with `BlendMode.Plus` at ±[OGChromaSpec.shift] px offset.
 *
 * [timeSec] drives the pulsation. Use [OGChromaView] for self-animating behaviour.
 */
fun Modifier.ogChroma(spec: OGChromaSpec, timeSec: Float = 0f): Modifier = composed {
    val rLayer = rememberGraphicsLayer()
    val gLayer = rememberGraphicsLayer()
    val bLayer = rememberGraphicsLayer()
    drawWithContent {
        val s = spec.sanitized()
        val pulse = 1f - s.pulse * 0.5f + s.pulse * 0.5f * sin(timeSec * s.pulseSpeed * TAU)
        val currentShift = s.shift * pulse
        val angleRad = s.angle * PI.toFloat() / 180f
        val dx = currentShift * cos(angleRad)
        val dy = currentShift * sin(angleRad)

        rLayer.record { this@drawWithContent.drawContent() }
        gLayer.record { this@drawWithContent.drawContent() }
        bLayer.record { this@drawWithContent.drawContent() }

        rLayer.colorFilter = ColorFilter.colorMatrix(RED_CHANNEL)
        rLayer.blendMode = androidx.compose.ui.graphics.BlendMode.Plus
        gLayer.colorFilter = ColorFilter.colorMatrix(GREEN_CHANNEL)
        gLayer.blendMode = androidx.compose.ui.graphics.BlendMode.Plus
        bLayer.colorFilter = ColorFilter.colorMatrix(BLUE_CHANNEL)
        bLayer.blendMode = androidx.compose.ui.graphics.BlendMode.Plus

        translate(-dx, -dy) { drawLayer(rLayer) }
        drawLayer(gLayer)
        translate(dx, dy) { drawLayer(bLayer) }
    }
}

@Composable
fun OGChromaView(
    spec: OGChromaSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    var elapsed by remember(spec) { mutableStateOf(0f) }
    LaunchedEffect(spec, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }
    Canvas(modifier.clipToBounds().ogChroma(spec, elapsed / 1000f)) {}
}

private val RED_CHANNEL = ColorMatrix(floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 0f, 0f, 0f, 0f,
    0f, 0f, 0f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
))
private val GREEN_CHANNEL = ColorMatrix(floatArrayOf(
    0f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 0f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
))
private val BLUE_CHANNEL = ColorMatrix(floatArrayOf(
    0f, 0f, 0f, 0f, 0f,
    0f, 0f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
))

// ── Light leak ────────────────────────────────────────────────────────────────

/**
 * **Light leak** — a coloured radial glow bleeding from a corner/edge, drawn over content.
 * [timeSec] drives the opacity pulse. Use [OGLightLeakView] for self-animating behaviour.
 */
fun Modifier.ogLightLeak(spec: OGLightLeakSpec, timeSec: Float = 0f): Modifier {
    val s = spec.sanitized()
    return drawWithContent {
        drawContent()
        if (s.opacity <= 0f) return@drawWithContent
        val leakColor = s.color.toSpecColor() ?: Color(0xFFFF8040)
        drawLightLeakLayer(s, timeSec, leakColor)
    }
}

@Composable
fun OGLightLeakView(
    spec: OGLightLeakSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val s = remember(spec) { spec.sanitized() }
    val leakColor = remember(s) { s.color.toSpecColor() ?: Color(0xFFFF8040) }
    var elapsed by remember(s) { mutableStateOf(0f) }
    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }
    Canvas(modifier.clipToBounds()) {
        if (size.width > 0f && size.height > 0f && s.opacity > 0f)
            drawLightLeakLayer(s, elapsed / 1000f, leakColor)
    }
}

private fun DrawScope.drawLightLeakLayer(spec: OGLightLeakSpec, timeSec: Float, color: Color) {
    val w = size.width; val h = size.height
    val cx = spec.cornerX * w
    val cy = spec.cornerY * h
    val leakR = spec.spread * sqrt(w * w + h * h)
    val pulse = 1f - spec.pulse * 0.5f + spec.pulse * 0.5f * sin(timeSec * spec.pulseSpeed * TAU)
    val alpha = spec.opacity * pulse

    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to color.copy(alpha = alpha),
                0.30f to color.copy(alpha = alpha * 0.65f),
                0.60f to color.copy(alpha = alpha * 0.25f),
                1f to Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = leakR,
        ),
        radius = leakR,
        center = Offset(cx, cy),
    )
}

// ── Anamorphic streak ─────────────────────────────────────────────────────────

/**
 * **Anamorphic lens streak** — horizontal flare line drawn over content.
 * [timeSec] drives the brightness pulse. Use [OGAnamorphicView] for self-animating behaviour.
 */
fun Modifier.ogAnamorphic(spec: OGAnamorphicSpec, timeSec: Float = 0f): Modifier {
    val s = spec.sanitized()
    return drawWithContent {
        drawContent()
        if (s.opacity <= 0f) return@drawWithContent
        val streakColor = s.color.toSpecColor() ?: Color(0xFF80C8FF)
        drawAnamorphicLayer(s, timeSec, streakColor)
    }
}

@Composable
fun OGAnamorphicView(
    spec: OGAnamorphicSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val s = remember(spec) { spec.sanitized() }
    val streakColor = remember(s) { s.color.toSpecColor() ?: Color(0xFF80C8FF) }
    var elapsed by remember(s) { mutableStateOf(0f) }
    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }
    Canvas(modifier.clipToBounds()) {
        if (size.width > 0f && size.height > 0f && s.opacity > 0f)
            drawAnamorphicLayer(s, elapsed / 1000f, streakColor)
    }
}

private fun DrawScope.drawAnamorphicLayer(spec: OGAnamorphicSpec, timeSec: Float, color: Color) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return

    val pulse = 1f - spec.pulse * 0.5f + spec.pulse * 0.5f * sin(timeSec * spec.pulseSpeed * TAU)
    val alpha = spec.opacity * pulse

    val margin = w * (1f - spec.spread) * 0.5f
    val startX = margin
    val endX = w - margin
    val lineY = spec.centerY * h

    val gradBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.08f to color.copy(alpha = alpha * 0.55f),
            0.50f to color.copy(alpha = alpha),
            0.92f to color.copy(alpha = alpha * 0.55f),
            1f to Color.Transparent,
        ),
        start = Offset(startX, lineY),
        end = Offset(endX, lineY),
    )

    // Soft diffuse halo
    drawRect(
        brush = gradBrush,
        topLeft = Offset(startX, lineY - spec.width * 2.5f),
        size = Size(endX - startX, spec.width * 5f),
    )
    // Bright core line
    drawRect(
        brush = gradBrush,
        topLeft = Offset(startX, lineY - spec.width * 0.5f),
        size = Size(endX - startX, spec.width),
    )
}

// ── Lens flare ────────────────────────────────────────────────────────────────

/**
 * **Lens flare** — central glow, starburst arms, and secondary bokeh discs along the optical axis,
 * drawn over content. [timeSec] drives the twinkle. Use [OGLensFlareView] for self-animating behaviour.
 */
fun Modifier.ogLensFlare(spec: OGLensFlareSpec, timeSec: Float = 0f): Modifier {
    val s = spec.sanitized()
    return drawWithContent {
        drawContent()
        if (s.opacity <= 0f) return@drawWithContent
        val flareColor = s.color.toSpecColor() ?: Color.White
        val tintColor = s.tint.toSpecColor() ?: Color(0xFFFFD080)
        drawLensFlareLayer(s, timeSec, flareColor, tintColor)
    }
}

@Composable
fun OGLensFlareView(
    spec: OGLensFlareSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val s = remember(spec) { spec.sanitized() }
    val flareColor = remember(s) { s.color.toSpecColor() ?: Color.White }
    val tintColor = remember(s) { s.tint.toSpecColor() ?: Color(0xFFFFD080) }
    var elapsed by remember(s) { mutableStateOf(0f) }
    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }
    Canvas(modifier.clipToBounds()) {
        if (size.width > 0f && size.height > 0f && s.opacity > 0f)
            drawLensFlareLayer(s, elapsed / 1000f, flareColor, tintColor)
    }
}

internal fun DrawScope.drawLensFlareLayer(
    spec: OGLensFlareSpec,
    timeSec: Float,
    flareColor: Color,
    tintColor: Color,
) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return

    val cx = spec.centerX * w
    val cy = spec.centerY * h
    val minDim = min(w, h)

    val pulseMult = 1f - spec.pulse * 0.4f + spec.pulse * 0.4f * sin(timeSec * spec.pulseSpeed * TAU)
    val twinkleDeg = spec.pulse * 4f * sin(timeSec * spec.pulseSpeed * TAU * 1.1f)
    val alpha = spec.opacity * pulseMult

    // Central glow
    val glowR = spec.scale * minDim * 0.10f
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to flareColor.copy(alpha = alpha),
                0.45f to flareColor.copy(alpha = alpha * 0.5f),
                1f to Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = glowR * 2f,
        ),
        radius = glowR * 2f,
        center = Offset(cx, cy),
    )

    // Star arms — drawn via rotated rects from centre
    val armLen = spec.streakLength * minDim * spec.scale
    val armW = (1.5f + spec.scale * 0.5f).coerceAtMost(4f)
    for (k in 0 until spec.streakCount) {
        val angleDeg = k * 360f / spec.streakCount + twinkleDeg
        withTransform({ rotate(degrees = angleDeg, pivot = Offset(cx, cy)) }) {
            drawRect(
                color = flareColor.copy(alpha = alpha * 0.55f),
                topLeft = Offset(cx, cy - armW * 0.5f),
                size = Size(armLen, armW),
            )
        }
    }

    // Central bright disc on top of arm roots
    drawCircle(
        color = flareColor.copy(alpha = (alpha * 0.90f).coerceAtMost(1f)),
        radius = glowR * 0.6f,
        center = Offset(cx, cy),
    )

    // Bokeh discs along optical axis (flare centre → image centre, extending beyond)
    if (spec.bokehCount > 0) {
        val axisX = 0.5f * w - cx
        val axisY = 0.5f * h - cy
        val axisDist = sqrt(axisX * axisX + axisY * axisY).coerceAtLeast(1f)
        val dirX = axisX / axisDist
        val dirY = axisY / axisDist

        for (i in 0 until spec.bokehCount) {
            val t = (i + 1f) / (spec.bokehCount + 1f)
            val dist = t * axisDist * 2.2f
            val bx = cx + dirX * dist
            val by = cy + dirY * dist
            val bokeR = (2.5f + t * 5f) * spec.scale * pulseMult
            val bokeAlpha = alpha * 0.38f * (1f - t * 0.25f)
            drawCircle(tintColor.copy(alpha = bokeAlpha), bokeR, Offset(bx, by))
        }
    }
}

private val TAU = (2.0 * PI).toFloat()
