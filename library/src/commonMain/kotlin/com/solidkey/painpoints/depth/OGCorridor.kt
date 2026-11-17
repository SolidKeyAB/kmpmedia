package com.solidkey.painpoints.depth

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.solidkey.painpoints.ai.toSpecColor
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/**
 * Infinite perspective tunnel — a Temple Run–style corridor that bends using the same sinusoidal
 * path as [OGInfinitePath].
 *
 * The tunnel is a rectangular box viewed from inside. At the near plane the camera sees the full
 * canvas; floor, ceiling, and walls converge to the vanishing point at the horizon. Arch frames
 * appear at regular world-Z intervals. Optional wall torches add atmosphere.
 *
 * Algorithmically identical to [OGInfinitePath] for the ground (scanline per step, painter's
 * algorithm back-to-front), plus ceiling scanlines and two wall quad-strips. Pure `commonMain`
 * Canvas — zero shaders.
 *
 * Rendered by [OGCorridorView] / [drawOGCorridorFrame].
 */
@Serializable
data class OGCorridorSpec(
    val name: String? = null,
    val camera: OGPerspectiveCamera = OGPerspectiveCamera(horizonFraction = 0.5f),
    /** Corridor half-width in world units (full width = 2 × halfWidth). */
    val halfWidth: Float = 240f,
    /** Corridor interior height in world units. Camera sits at [OGPerspectiveCamera.cameraHeight]. */
    val height: Float = 320f,
    val floorColor: String = "#1A1206",
    val floorAltColor: String = "#130E04",
    val ceilingColor: String = "#0D0D18",
    val ceilingAltColor: String = "#0A0A14",
    val leftWallColor: String = "#15110A",
    val rightWallColor: String = "#1C170D",
    val wallHighlightColor: String = "#2A2015",
    val archColor: String = "#2D2510",
    val archGlowColor: String = "#18443322",
    /** World-unit spacing between arch frames. */
    val archInterval: Float = 200f,
    /** Depth haze — tints and obscures the far end. */
    val haze: OGDepthHaze = OGDepthHaze(color = "#AA100A06", nearZ = 300f, farZ = 1400f),
    val path: OGPathDynamics = OGPathDynamics(bendAmplitude = 80f),
    /** Forward scroll speed in world units per second. */
    val scrollSpeed: Float = 180f,
    /** Far clip plane in world units. */
    val farClip: Float = 1600f,
    /** Whether to draw glowing torch spots on the walls. */
    val torches: Boolean = true,
    val torchColor: String = "#FFAA44",
    val torchGlowColor: String = "#88FF8822",
    /** World-unit spacing between torches (defaults to archInterval). */
    val torchInterval: Float = 200f,
)

/** Ready-made corridor presets. */
object OGCorridors {
    val Temple = OGCorridorSpec(name = "Temple")
    val Cave = OGCorridorSpec(
        name = "Cave",
        floorColor = "#0D0A05", floorAltColor = "#0A0802",
        ceilingColor = "#080608", ceilingAltColor = "#060506",
        leftWallColor = "#0E0A04", rightWallColor = "#120D06",
        archColor = "#1A1208",
        haze = OGDepthHaze(color = "#BB080502", nearZ = 200f, farZ = 1200f),
        torchColor = "#FF7700", torchGlowColor = "#66FF5500",
    )
    val SciFi = OGCorridorSpec(
        name = "SciFi",
        floorColor = "#080C14", floorAltColor = "#060A10",
        ceilingColor = "#050810", ceilingAltColor = "#040710",
        leftWallColor = "#080C18", rightWallColor = "#0A0E1C",
        wallHighlightColor = "#1030FF",
        archColor = "#102040",
        archGlowColor = "#2200AAFF",
        haze = OGDepthHaze(color = "#AA000814", nearZ = 400f, farZ = 1600f),
        torchColor = "#44AAFF", torchGlowColor = "#660088FF",
    )
}

class OGCorridorColors(
    val floor: Color, val floorAlt: Color,
    val ceiling: Color, val ceilingAlt: Color,
    val leftWall: Color, val rightWall: Color,
    val wallHighlight: Color,
    val arch: Color, val archGlow: Color,
    val haze: Color,
    val torch: Color, val torchGlow: Color,
)

fun OGCorridorSpec.resolveColors() = OGCorridorColors(
    floor         = floorColor.toSpecColor()         ?: Color(0xFF1A1206.toInt()),
    floorAlt      = floorAltColor.toSpecColor()      ?: Color(0xFF130E04.toInt()),
    ceiling       = ceilingColor.toSpecColor()       ?: Color(0xFF0D0D18.toInt()),
    ceilingAlt    = ceilingAltColor.toSpecColor()    ?: Color(0xFF0A0A14.toInt()),
    leftWall      = leftWallColor.toSpecColor()      ?: Color(0xFF15110A.toInt()),
    rightWall     = rightWallColor.toSpecColor()     ?: Color(0xFF1C170D.toInt()),
    wallHighlight = wallHighlightColor.toSpecColor() ?: Color(0xFF2A2015.toInt()),
    arch          = archColor.toSpecColor()          ?: Color(0xFF2D2510.toInt()),
    archGlow      = archGlowColor.toSpecColor()      ?: Color(0x18443322),
    haze          = haze.color.toSpecColor()         ?: Color(0xAA100A06.toInt()),
    torch         = torchColor.toSpecColor()         ?: Color(0xFFFFAA44.toInt()),
    torchGlow     = torchGlowColor.toSpecColor()     ?: Color(0x88FF8822.toInt()),
)

@Composable
fun OGCorridorView(
    spec: OGCorridorSpec = OGCorridorSpec(),
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val colors = remember(spec) { spec.resolveColors() }
    var scroll by remember { mutableStateOf(0f) }
    LaunchedEffect(spec, playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            scroll += spec.scrollSpeed * (now - last) / 1000f
            last = now
        }
    }
    Canvas(modifier.clipToBounds()) { drawOGCorridorFrame(spec, colors, scroll) }
}

// ── Frame draw ────────────────────────────────────────────────────────────────

/**
 * Draws one corridor frame at [scroll] into the current [DrawScope].
 * [spec] and [colors] should be `remember`ed by the caller.
 */
fun DrawScope.drawOGCorridorFrame(
    spec: OGCorridorSpec,
    colors: OGCorridorColors,
    scroll: Float,
) {
    val cam      = spec.camera
    val w        = size.width
    val h        = size.height
    val horizonY = h * cam.horizonFraction

    // VP tracks path heading (same as OGInfinitePath)
    val heading  = pathHeadingX(scroll, spec.path)
    val vpX      = w * 0.5f + heading * spec.path.cameraFollowStrength * 0.07f * w

    // ── 1. Void background (the black void at the far end) ────────────────────
    drawRect(color = colors.haze, topLeft = Offset.Zero, size = Size(w, h))

    // ── 2. Sample geometry at STEPS z-bands ──────────────────────────────────
    val STEPS = 48
    // Near Z: where the floor scanline hits the bottom of the canvas (or near it)
    val nearZ = (cam.focalLength * cam.cameraHeight / (h - horizonY)).coerceAtLeast(1f) * 0.6f
    val dz    = (spec.farClip - nearZ) / (STEPS - 1).toFloat()

    data class CorridorRow(
        val z: Float, val scale: Float,
        val groundY: Float, val ceilingY: Float,
        val leftX: Float, val rightX: Float,
    )

    val rows = Array(STEPS) { i ->
        val z      = nearZ + i * dz
        val scale  = cam.perspectiveScale(z)
        val cx     = pathCenterX(z + scroll, spec.path) * scale
        val gY     = cam.screenYAt(z, horizonY).coerceIn(horizonY, h + 20f)
        val cY     = cam.screenYForHeight(spec.height, z, horizonY).coerceIn(-20f, horizonY)
        CorridorRow(
            z      = z,
            scale  = scale,
            groundY  = gY,
            ceilingY = cY,
            leftX  = vpX + cx - spec.halfWidth * scale,
            rightX = vpX + cx + spec.halfWidth * scale,
        )
    }

    // ── 3. Draw far → near (painter's algorithm) ──────────────────────────────
    for (i in STEPS - 2 downTo 0) {
        val far  = rows[i + 1]
        val near = rows[i]
        val shade = 1f - i.toFloat() / STEPS * 0.65f   // near is brighter
        val alt   = i % 2 == 0

        // Floor quad
        val floorColor = (if (alt) colors.floor else colors.floorAlt).copy(alpha = shade)
        drawCorridorQuad(
            far.leftX, far.groundY, far.rightX, far.groundY,
            near.rightX, near.groundY, near.leftX, near.groundY,
            floorColor,
        )

        // Ceiling quad
        val ceilColor = (if (alt) colors.ceiling else colors.ceilingAlt).copy(alpha = shade)
        drawCorridorQuad(
            far.leftX, far.ceilingY, far.rightX, far.ceilingY,
            near.rightX, near.ceilingY, near.leftX, near.ceilingY,
            ceilColor,
        )

        // Left wall quad (inner face — right side of left wall)
        val lwColor = colors.leftWall.copy(alpha = shade)
        drawCorridorQuad(
            far.leftX, far.ceilingY, far.leftX, far.groundY,
            near.leftX, near.groundY, near.leftX, near.ceilingY,
            lwColor,
        )

        // Right wall quad (inner face — left side of right wall)
        val rwColor = colors.rightWall.copy(alpha = shade)
        drawCorridorQuad(
            far.rightX, far.ceilingY, far.rightX, far.groundY,
            near.rightX, near.groundY, near.rightX, near.ceilingY,
            rwColor,
        )
    }

    // ── 4. Arch frames ────────────────────────────────────────────────────────
    val firstArchIdx = (scroll / spec.archInterval).toInt()
    val lastArchIdx  = ((scroll + spec.farClip) / spec.archInterval).toInt() + 1
    // Draw far arches first
    for (k in lastArchIdx downTo firstArchIdx) {
        val worldZ = k * spec.archInterval
        val localZ = worldZ - scroll
        if (localZ < nearZ || localZ >= spec.farClip) continue
        val scale    = cam.perspectiveScale(localZ)
        val cx       = pathCenterX(worldZ, spec.path) * scale
        val groundY  = cam.screenYAt(localZ, horizonY).coerceIn(horizonY, h + 10f)
        val ceilingY = cam.screenYForHeight(spec.height, localZ, horizonY).coerceIn(-10f, horizonY)
        val leftX    = vpX + cx - spec.halfWidth * scale
        val rightX   = vpX + cx + spec.halfWidth * scale
        val archW    = (8f * scale).coerceAtLeast(1f)
        val fade     = 1f - localZ / spec.farClip
        val archColor = colors.arch.copy(alpha = colors.arch.alpha * fade)
        // Glow rect behind arch
        if (colors.archGlow.alpha > 0.01f) {
            drawRect(
                colors.archGlow.copy(alpha = colors.archGlow.alpha * fade),
                topLeft = Offset(leftX - archW, ceilingY),
                size = Size(rightX - leftX + archW * 2f, groundY - ceilingY),
            )
        }
        // Top beam
        drawRect(archColor, Offset(leftX - archW, ceilingY), Size(rightX - leftX + archW * 2f, archW * 2f))
        // Bottom sill
        drawRect(archColor, Offset(leftX - archW, groundY - archW), Size(rightX - leftX + archW * 2f, archW * 2f))
        // Left pillar
        drawRect(archColor, Offset(leftX - archW, ceilingY), Size(archW * 2f, groundY - ceilingY))
        // Right pillar
        drawRect(archColor, Offset(rightX - archW, ceilingY), Size(archW * 2f, groundY - ceilingY))
    }

    // ── 5. Torches ────────────────────────────────────────────────────────────
    if (spec.torches && spec.torchInterval > 0f) {
        val firstT = (scroll / spec.torchInterval).toInt()
        val lastT  = ((scroll + spec.farClip) / spec.torchInterval).toInt() + 1
        for (k in lastT downTo firstT) {
            val worldZ = k * spec.torchInterval
            val localZ = worldZ - scroll
            if (localZ < nearZ || localZ >= spec.farClip) continue
            val scale    = cam.perspectiveScale(localZ)
            val cx       = pathCenterX(worldZ, spec.path) * scale
            val midY     = cam.screenYForHeight(spec.height * 0.65f, localZ, horizonY)
            if (midY < 0f || midY > h) continue
            for (side in listOf(-1f, 1f)) {
                val torchX = vpX + cx + side * spec.halfWidth * scale
                val r      = (12f * scale).coerceAtLeast(1f)
                val fade   = 1f - localZ / spec.farClip
                drawCircle(
                    Brush.radialGradient(
                        listOf(colors.torchGlow, Color.Transparent),
                        center = Offset(torchX, midY), radius = r * 3f,
                    ),
                    radius = r * 3f, center = Offset(torchX, midY),
                )
                drawCircle(colors.torch.copy(alpha = colors.torch.alpha * fade), r, Offset(torchX, midY))
            }
        }
    }

    // ── 6. Atmospheric haze ────────────────────────────────────────────────────
    val hazeAlpha = colors.haze.alpha
    if (hazeAlpha > 0.01f) {
        drawRect(
            Brush.verticalGradient(
                listOf(
                    colors.haze.copy(alpha = hazeAlpha * 0.6f),
                    colors.haze.copy(alpha = hazeAlpha * 0.2f),
                    colors.haze.copy(alpha = 0f),
                    colors.haze.copy(alpha = 0f),
                    colors.haze.copy(alpha = hazeAlpha * 0.2f),
                    colors.haze.copy(alpha = hazeAlpha * 0.6f),
                ),
                startY = 0f, endY = h,
            ),
            topLeft = Offset.Zero, size = Size(w, h),
        )
    }
}

/** Fills a quadrilateral with vertices in order: top-left, top-right, bottom-right, bottom-left. */
private fun DrawScope.drawCorridorQuad(
    x0: Float, y0: Float, x1: Float, y1: Float,
    x2: Float, y2: Float, x3: Float, y3: Float,
    color: Color,
) {
    val path = Path()
    path.moveTo(x0, y0)
    path.lineTo(x1, y1)
    path.lineTo(x2, y2)
    path.lineTo(x3, y3)
    path.close()
    drawPath(path, color)
}
