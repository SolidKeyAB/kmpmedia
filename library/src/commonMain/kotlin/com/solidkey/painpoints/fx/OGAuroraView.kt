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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Renders an aurora borealis curtain that fills this composable's bounds. Set [playing] to
 * `false` to freeze. Overlay it over a dark scene with a `Box`; the screen-blend columns are
 * transparent where there is no curtain.
 *
 * Algorithm: N curtain columns, each a subdivided vertical strip whose horizontal displacement
 * follows H harmonics of a sinusoid (curvature maximal at the bottom) plus simplex fBm shear.
 * Fill = altitude-stratified vertical gradient (bottom=557nm green → mid=cyan → top=630nm violet).
 * Drawn with [BlendMode.Screen] so overlapping columns build luminous brightness without clipping.
 *
 * Presets and codec on [OGAurora].
 */
@Composable
fun OGAuroraView(
    spec: OGAuroraSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val s = remember(spec) { spec.sanitized() }
    val bottomC = remember(s) { s.bottomColor.toSpecColor() ?: Color(0xFF00E676) }
    val midC    = remember(s) { s.midColor.toSpecColor()    ?: Color(0xFF00BCD4) }
    val topC    = remember(s) { s.topColor.toSpecColor()    ?: Color(0xFFCE93D8) }

    var elapsed by remember(s) { mutableStateOf(0f) }
    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }

    Canvas(modifier.clipToBounds()) {
        drawOGAuroraFrame(s, elapsed / 1000f, bottomC, midC, topC)
    }
}

/**
 * Draw one frame of an aurora curtain at [timeSec] with resolved colours.
 * The per-frame body shared by [OGAuroraView] and any off-screen baker.
 */
fun DrawScope.drawOGAuroraFrame(
    spec: OGAuroraSpec,
    timeSec: Float,
    bottomColor: Color,
    midColor: Color,
    topColor: Color,
) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return
    val s = spec.sanitized()

    val curtainTop = s.curtainY * h
    val curtainH   = s.curtainHeight * h
    val N          = s.columns
    val step       = if (N > 1) w / (N - 1).toFloat() else w
    val halfW      = step * s.columnWidthFactor * 0.5f
    val subdivs    = 28

    val twoPI = 2f * PI.toFloat()

    for (col in 0 until N) {
        val baseX  = col * step
        val phase0 = col * twoPI / N + OGFxMath.hash01(col, s.seed) * twoPI
        val alphaJitter = 0.55f + 0.45f * OGFxMath.hash01(col * 17 + 3, s.seed + 5)
        val alpha  = s.opacity * alphaJitter

        // Build left and right edges of this column strip (subdivided for smooth curvature)
        val leftX  = FloatArray(subdivs + 1)
        val leftY  = FloatArray(subdivs + 1)
        val rightX = FloatArray(subdivs + 1)
        val rightY = FloatArray(subdivs + 1)

        for (j in 0..subdivs) {
            val hFrac = j.toFloat() / subdivs                 // 0 = top, 1 = bottom of curtain
            val y     = curtainTop + hFrac * curtainH

            // Curvature grows quadratically toward the bottom (hanging curtain physics)
            val curvature = hFrac * hFrac

            // Multi-harmonic sway
            var sway = 0f
            for (harm in 1..s.harmonics) {
                val harmAmp   = s.amplitude * w / harm.toFloat().pow(1.4f)
                val harmFreq  = s.frequency * harm
                val harmPhase = phase0 + harm * 0.73f
                sway += curvature * harmAmp * sin(harmFreq * hFrac * twoPI + harmPhase + s.speed * timeSec)
            }

            // Simplex fBm shear: non-uniform organic bending along the column length
            val shearX = s.shear * w * OGFxNoise.fbm(
                x = hFrac * 2.8f + col * 0.52f,
                y = timeSec * s.shearSpeed + col * 0.31f,
                octaves = 2,
                seed = s.seed + col,
            )

            val cx = baseX + sway + shearX
            leftX[j]  = cx - halfW;  leftY[j]  = y
            rightX[j] = cx + halfW;  rightY[j] = y
        }

        // Build closed path: left edge top→bottom, right edge bottom→top
        val path = Path()
        path.moveTo(leftX[0], leftY[0])
        for (j in 1..subdivs) path.lineTo(leftX[j], leftY[j])
        for (j in subdivs downTo 0) path.lineTo(rightX[j], rightY[j])
        path.close()

        // Vertical gradient: top=topColor (dim) → mid=midColor → bottom=bottomColor (bright)
        val gradStart = Offset(w * 0.5f, curtainTop)
        val gradEnd   = Offset(w * 0.5f, curtainTop + curtainH)
        val fill = Brush.linearGradient(
            colorStops = arrayOf(
                0f   to topColor.copy(alpha = alpha * 0.38f),
                0.30f to midColor.copy(alpha = alpha * 0.68f),
                1f   to bottomColor.copy(alpha = alpha),
            ),
            start = gradStart,
            end   = gradEnd,
        )
        drawPath(path, fill, blendMode = BlendMode.Screen)
    }
}

private fun frac(x: Float): Float = x - floor(x)
