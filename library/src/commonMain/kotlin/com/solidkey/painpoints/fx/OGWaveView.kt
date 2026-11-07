package com.solidkey.painpoints.fx

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.PI
import kotlin.math.sin

/**
 * Draw and run a [spec]'s **water surface**, filling this composable's bounds with scrolling wave
 * bands (and optional foam crests). Set [playing] to `false` to freeze it.
 *
 * 60fps + cross-platform: each band is one closed path filled with a single vertical gradient — no
 * per-pixel work, no shaders (see [OGWaveSpec]). Overlay it in a `Box` over your scene; everything
 * above the crest line stays transparent. For a rougher sea, raise [OGWaveSpec.layers] or
 * [OGWaveSpec.turbulence].
 */
@Composable
fun OGWaveView(
    spec: OGWaveSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val s = remember(spec) { spec.sanitized() }
    val deep = remember(s) { s.deepColor.toSpecColor() ?: Color(0xFF1565C0) }
    val shallow = remember(s) { s.shallowColor.toSpecColor() ?: Color(0xFF4FC3F7) }

    var elapsed by remember(s) { mutableStateOf(0f) }
    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }

    Canvas(modifier.clipToBounds()) {
        drawOGWaveFrame(s, elapsed / 1000f, deep, shallow)
    }
}

/**
 * Draw one frame of a [spec]'s waves at [timeSec] with its resolved colours, into the current
 * [DrawScope]. The per-frame body shared by the live [OGWaveView] and any off-screen baker — one
 * code path, so an export matches the preview. Back bands are drawn first; each band is a closed
 * crest-to-bottom path with a vertical shallow→deep gradient, then an optional round-capped white
 * foam stroke along the crest. Clip to the bounds first.
 */
internal fun DrawScope.drawOGWaveFrame(
    spec: OGWaveSpec,
    timeSec: Float,
    deepColor: Color,
    shallowColor: Color,
) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return

    val bands = waveBands(spec, timeSec)
    // Back layers first (last in the list), front band last so it reads on top.
    for (i in bands.indices.reversed()) {
        val band = bands[i]
        val crests = band.crests
        if (crests.size < 2) continue

        val path = Path().apply {
            moveTo(0f, crests[0].y * h)
            for (c in crests) lineTo(c.x * w, c.y * h)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        val minCrestY = crests.minOf { it.y }
        val fill = Brush.linearGradient(
            colorStops = arrayOf(
                0f to shallowColor.copy(alpha = band.fillAlpha),
                0.4f to deepColor.copy(alpha = band.fillAlpha * 0.8f),
                1f to deepColor.copy(alpha = band.fillAlpha * 0.3f),
            ),
            start = Offset(w * 0.5f, minCrestY * h - spec.amplitude * h),
            end = Offset(w * 0.5f, h),
        )
        drawPath(path, brush = fill)

        if (band.foam) {
            // Draw multi-layered, broken cinematic foam instead of a single "worm" line
            val rngF = kotlin.random.Random(spec.seed + i)
            val foamColor = Color.White.copy(alpha = band.fillAlpha * 0.75f)
            val baseW = (spec.amplitude * spec.foamThickness * h).coerceAtLeast(1f)
            
            for (layer in 0..2) {
                val foamPath = Path()
                var isDown = false
                for (j in 0 until crests.size) {
                    val cx = crests[j].x * w
                    val cy = crests[j].y * h + if (layer == 0) 0f else (rngF.nextFloat() - 0.5f) * baseW * 1.5f
                    if (j == 0) {
                        foamPath.moveTo(cx, cy)
                        isDown = true
                    } else {
                        // Randomly break the path for a dashed/scattered foam look
                        if (rngF.nextFloat() > 0.15f) {
                            if (!isDown) foamPath.moveTo(cx, cy)
                            else foamPath.lineTo(cx, cy)
                            isDown = true
                        } else {
                            isDown = false
                        }
                    }
                }
                val fw = if (layer == 0) baseW else baseW * (0.4f + 0.6f * rngF.nextFloat())
                drawPath(
                    foamPath,
                    brush = SolidColor(if (layer == 0) foamColor.copy(alpha = foamColor.alpha * 0.6f) else foamColor),
                    style = Stroke(width = fw, cap = StrokeCap.Round)
                )
            }
        }
    }
}

/**
 * Draw a single traveling wave-crest band that sweeps top→bottom of the view.
 * [travelPos] is the band's normalised travel position (0 = at the view top, 1 = at the view
 * bottom, values slightly above 1 let it exit below). [bandHeightFrac] controls band thickness
 * as a fraction of the view height. [phase] (radians) offsets the undulation so staggered bands
 * look distinct. Colours come from [OGWaveSpec.shallowColor] (crest) and [OGWaveSpec.deepColor]
 * (body). Foam is drawn when [OGWaveSpec.foam] is true.
 */
fun DrawScope.drawOGWaveFrontFrame(
    spec: OGWaveSpec,
    travelPos: Float,
    bandHeightFrac: Float = 0.18f,
    phase: Float = 0f,
) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return
    val s = spec.sanitized()

    // Smoothstep fade-in 0→0.15 and fade-out 0.85→1.15
    val uIn  = (travelPos / 0.15f).coerceIn(0f, 1f)
    val fadeIn = uIn * uIn * (3f - 2f * uIn)
    val uOut = ((travelPos - 0.85f) / 0.30f).coerceIn(0f, 1f)
    val fadeOut = 1f - uOut * uOut * (3f - 2f * uOut)
    val bandAlpha = fadeIn * fadeOut
    if (bandAlpha <= 0f) return

    val crestColor = s.shallowColor.toSpecColor() ?: Color.White
    val bodyColor  = s.deepColor.toSpecColor()   ?: Color(0xFF003A6EL)

    val steps = 40
    val twoPI = 2f * PI.toFloat()
    val yBase = (travelPos - 0.35f) * h * 1.4f
    val bandH = bandHeightFrac * h

    // Crest stroke
    val crestPath = Path()
    for (si in 0..steps) {
        val xFrac = si.toFloat() / steps
        val x = xFrac * w
        val y = yBase + sin(xFrac * s.frequency * twoPI + phase) * s.amplitude * h
        if (si == 0) crestPath.moveTo(x, y) else crestPath.lineTo(x, y)
    }
    drawPath(crestPath, crestColor.copy(alpha = bandAlpha * 0.95f),
        style = Stroke(width = h * 0.024f, cap = StrokeCap.Round))

    // Filled body
    val bodyPath = Path()
    for (si in 0..steps) {
        val xFrac = si.toFloat() / steps
        val x = xFrac * w
        val y = yBase + sin(xFrac * s.frequency * twoPI + phase) * s.amplitude * h
        if (si == 0) bodyPath.moveTo(x, y) else bodyPath.lineTo(x, y)
    }
    bodyPath.lineTo(w, yBase + bandH)
    for (si in steps downTo 0) {
        val xFrac = si.toFloat() / steps
        val x = xFrac * w
        val y = yBase + bandH + sin(xFrac * s.frequency * twoPI + phase + 1.1f) * s.amplitude * h * 0.6f
        bodyPath.lineTo(x, y)
    }
    bodyPath.close()
    drawPath(bodyPath, bodyColor.copy(alpha = bandAlpha * 0.82f))

    // Foam flecks
    if (s.foam) {
        val rngF = kotlin.random.Random(s.seed + phase.toInt())
        val foamW = h * 0.015f
        for (layer in 0..2) {
            val fPath = Path()
            var isDown = false
            for (si in 0..steps) {
                val xFrac = si.toFloat() / steps
                val x = xFrac * w
                val y = yBase + bandH * 0.22f + sin(xFrac * s.frequency * twoPI + phase + 0.5f) * s.amplitude * h * 0.7f + if (layer == 0) 0f else (rngF.nextFloat() - 0.5f) * foamW * 4.5f
                if (si == 0) {
                    fPath.moveTo(x, y)
                    isDown = true
                } else {
                    if (rngF.nextFloat() > 0.15f) {
                        if (!isDown) fPath.moveTo(x, y)
                        else fPath.lineTo(x, y)
                        isDown = true
                    } else {
                        isDown = false
                    }
                }
            }
            val fw = if (layer == 0) foamW else foamW * (0.5f + 0.5f * rngF.nextFloat())
            drawPath(
                fPath,
                Color.White.copy(alpha = bandAlpha * (if(layer == 0) 0.45f else 0.75f)),
                style = Stroke(width = fw, cap = StrokeCap.Round)
            )
        }
    }
}
