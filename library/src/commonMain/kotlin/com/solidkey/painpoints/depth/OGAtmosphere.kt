package com.solidkey.painpoints.depth

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.solidkey.painpoints.ai.toSpecColor
import kotlinx.serialization.Serializable

/**
 * Sky backdrop: gradient + sun/moon disk with glow corona + fog band + cloud wisps.
 *
 * [OGAtmosphereView] fills the full canvas but only draws above [horizonFraction].
 * Combine with [OGInfinitePathView] or [OGCorridorView] by layering in a `Box`.
 *
 * All positions are fractions of the canvas so the spec is resolution-independent.
 */
@Serializable
data class OGAtmosphereSpec(
    val name: String? = null,
    /** Fraction of canvas height where the horizon sits; sky fills above this. */
    val horizonFraction: Float = 0.42f,
    val skyTopColor: String = "#050A14",
    val skyHorizonColor: String = "#1A2834",
    val sunEnabled: Boolean = true,
    val sunColor: String = "#FFFFCC",
    val sunGlowColor: String = "#44FFFF88",
    /** Where the sun sits within the sky band: 0 = sky-top, 1 = horizon. */
    val sunFraction: Float = 0.75f,
    /** Sun horizontal position: 0 = left edge, 1 = right edge. */
    val sunHorizontalFraction: Float = 0.65f,
    /** Sun radius as a fraction of canvas width. */
    val sunRadiusFraction: Float = 0.04f,
    val fogEnabled: Boolean = true,
    val fogColor: String = "#66AABBAA",
    /** Fog band height as a fraction of canvas height. */
    val fogThicknessFraction: Float = 0.06f,
    val cloudCount: Int = 4,
    val cloudColor: String = "#18FFFFFF",
    /** Seed for deterministic cloud placement. */
    val cloudSeed: Long = 42L,
)

/** Ready-made atmosphere presets. */
object OGAtmospheres {
    val Dawn = OGAtmosphereSpec(
        name = "Dawn",
        skyTopColor = "#1A0A05", skyHorizonColor = "#6B2F0F",
        sunColor = "#FFCC44", sunGlowColor = "#66FF8800",
        fogColor = "#55AA6633",
    )
    val Night = OGAtmosphereSpec(
        name = "Night",
        skyTopColor = "#020408", skyHorizonColor = "#080D14",
        sunEnabled = false,
        fogColor = "#33445566",
        cloudColor = "#08FFFFFF", cloudCount = 2,
    )
    val Alien = OGAtmosphereSpec(
        name = "Alien",
        skyTopColor = "#050A02", skyHorizonColor = "#1A3010",
        sunColor = "#CCFF44", sunGlowColor = "#5588FF44",
        fogColor = "#4466AA44",
    )
    val Overcast = OGAtmosphereSpec(
        name = "Overcast",
        skyTopColor = "#1A1A1A", skyHorizonColor = "#2A2A2A",
        sunEnabled = false,
        fogColor = "#66AAAAAA", fogThicknessFraction = 0.1f,
        cloudCount = 6, cloudColor = "#22FFFFFF",
    )
}

class OGAtmosphereColors(
    val skyTop: Color, val skyHorizon: Color,
    val sun: Color, val sunGlow: Color,
    val fog: Color, val cloud: Color,
)

fun OGAtmosphereSpec.resolveColors() = OGAtmosphereColors(
    skyTop    = skyTopColor.toSpecColor()     ?: Color(0xFF050A14.toInt()),
    skyHorizon = skyHorizonColor.toSpecColor() ?: Color(0xFF1A2834.toInt()),
    sun       = sunColor.toSpecColor()        ?: Color(0xFFFFFFCC.toInt()),
    sunGlow   = sunGlowColor.toSpecColor()    ?: Color(0x44FFFF88),
    fog       = fogColor.toSpecColor()        ?: Color(0x66AABBAA.toInt()),
    cloud     = cloudColor.toSpecColor()      ?: Color(0x18FFFFFF),
)

@Composable
fun OGAtmosphereView(
    spec: OGAtmosphereSpec = OGAtmosphereSpec(),
    modifier: Modifier = Modifier,
) {
    val colors = remember(spec) { spec.resolveColors() }
    Canvas(modifier.clipToBounds()) { drawOGAtmosphereFrame(spec, colors) }
}

fun DrawScope.drawOGAtmosphereFrame(spec: OGAtmosphereSpec, colors: OGAtmosphereColors) {
    val w        = size.width
    val h        = size.height
    val skyBottom = h * spec.horizonFraction

    // Sky gradient
    drawRect(
        brush = Brush.verticalGradient(
            listOf(colors.skyTop, colors.skyHorizon),
            startY = 0f, endY = skyBottom,
        ),
        topLeft = Offset.Zero, size = Size(w, skyBottom),
    )

    // Cloud wisps (deterministic)
    if (spec.cloudCount > 0) {
        for (i in 0 until spec.cloudCount) {
            val cx = atmosphereRng(i, spec.cloudSeed, 0) * w
            val cy = atmosphereRng(i, spec.cloudSeed, 1) * skyBottom * 0.65f
            val rw = (atmosphereRng(i, spec.cloudSeed, 2) * 0.12f + 0.06f) * w
            val rh = rw * (atmosphereRng(i, spec.cloudSeed, 3) * 0.3f + 0.15f)
            drawOval(colors.cloud, topLeft = Offset(cx - rw, cy - rh), size = Size(rw * 2f, rh * 2f))
        }
    }

    // Sun / moon disk with radial glow
    if (spec.sunEnabled) {
        val sunX = spec.sunHorizontalFraction * w
        val sunY = skyBottom * (1f - spec.sunFraction.coerceIn(0f, 1f))
        val r    = spec.sunRadiusFraction * w
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colors.sunGlow, Color.Transparent),
                center = Offset(sunX, sunY), radius = r * 3.5f,
            ),
            radius = r * 3.5f, center = Offset(sunX, sunY),
        )
        drawCircle(color = colors.sun, radius = r, center = Offset(sunX, sunY))
    }

    // Fog band straddling the horizon
    if (spec.fogEnabled && colors.fog.alpha > 0.01f) {
        val fogH   = h * spec.fogThicknessFraction
        val fogTop = skyBottom - fogH * 0.5f
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, colors.fog, Color.Transparent),
                startY = fogTop, endY = fogTop + fogH,
            ),
            topLeft = Offset(0f, fogTop), size = Size(w, fogH),
        )
    }
}

/** Deterministic pseudo-random float from [index], [seed], [channel]. */
internal fun atmosphereRng(index: Int, seed: Long, channel: Int): Float {
    val h = ((index.toLong() * 2654435761L) xor (seed * 2246822519L) xor (channel.toLong() * 1234567891L)) * 1234567891L
    return ((h ushr 33).toFloat() / (1L shl 31).toFloat()).coerceIn(0f, 1f)
}
