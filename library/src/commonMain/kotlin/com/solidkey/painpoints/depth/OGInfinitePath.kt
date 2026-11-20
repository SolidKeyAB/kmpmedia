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
import kotlin.math.cos
import kotlin.math.sin

// ── Road spec ─────────────────────────────────────────────────────────────────

/** Lane marking style. */
enum class OGLaneMarkStyle {
    /** Dashed centre/dividing lines (default). */
    DASHES,
    /** Solid continuous lines. */
    SOLID,
    /** No dividing lines (bare road surface). */
    NONE,
    /** Reflective cat's-eye dots at regular intervals. */
    CATS_EYE,
}

/**
 * Multi-lane road configuration for [OGInfinitePathSpec].
 *
 * @param laneCount Number of driving lanes. 1 = single lane (no divider); 2 = two lanes with
 *   one centre line (default); 3+ = additional dividing lines.
 * @param markStyle Visual style of the dividing and edge lines.
 * @param edgeLineColor Solid edge-line colour at the shoulder boundary.
 */
@Serializable
data class OGRoadSpec(
    val laneCount: Int = 2,
    val markStyle: OGLaneMarkStyle = OGLaneMarkStyle.DASHES,
    val edgeLineColor: String = "#AAFFFFFF",
)

/**
 * Infinite perspective path — a continuously-scrolling pseudo-3D road that bends left and right,
 * always converging toward a glowing target on the horizon. The camera follows the road heading,
 * tilting the vanishing point to reinforce each curve.
 *
 * Algorithmically:
 * - **Road surface**: scanline ground projection (OutRun 1986 technique). Per-scanline
 *   `z = focalLength × cameraHeight / (screenY − horizonY)` gives exact world depth; the road
 *   polygon is built from 70 sampled rows and filled in one [drawPath] call.
 * - **Bend**: the road centre traces `A(0.6·sin(f₁z) + 0.4·sin(f₂z))`, two harmonics for
 *   organic variation. The vanishing point tracks the derivative of this curve
 *   ([OGPathDynamics.cameraFollowStrength]), giving a natural lean-into-curve feel.
 * - **Trees**: billboard silhouettes z-sorted far→near (painter's algorithm), one triangle +
 *   rect per tree, scaled by the perspective factor.
 * - **Haze**: Lambert-Beer atmospheric depth haze rendered as a horizon gradient above the scene.
 * - **Target**: glowing beacon at the VP, pulsing gently to reinforce approach direction.
 *
 * Pure `commonMain` Canvas draw calls — zero shaders, zero per-pixel loops, zero external deps.
 *
 * Rendered by [OGInfinitePathView] / [drawOGInfinitePathFrame].
 */
@Serializable
data class OGInfinitePathSpec(
    val name: String? = null,

    val camera: OGPerspectiveCamera = OGPerspectiveCamera(),

    // ── Road ──────────────────────────────────────────────────────────────────
    /** Road half-width in world units at the near plane. */
    val roadHalfWidth: Float = 120f,
    /** Shoulder (edge strip) width in world units on each side. */
    val shoulderWidth: Float = 20f,
    val roadColor: String = "#2B3D2B",
    val shoulderColor: String = "#19271A",
    /** Dashed centre-line mark colour; use `#AARRGGBB` for transparency. */
    val laneMarkColor: String = "#99FFFFCC",
    /** World-unit spacing between lane-mark dashes. */
    val laneMarkInterval: Float = 80f,

    // ── Sky + ground ──────────────────────────────────────────────────────────
    val skyTopColor: String = "#050D05",
    val skyHorizonColor: String = "#122412",
    val groundHorizonColor: String = "#0E190E",
    val groundNearColor: String = "#182518",

    // ── Path dynamics ─────────────────────────────────────────────────────────
    val path: OGPathDynamics = OGPathDynamics(),

    // ── Atmosphere ────────────────────────────────────────────────────────────
    /** Haze overlay colour near the horizon (`#AARRGGBB`). */
    val hazeColor: String = "#992B5A2B",
    /** Multiplier for haze intensity: 0 = off, 1 = full. */
    val hazeStrength: Float = 1f,

    // ── Trees ─────────────────────────────────────────────────────────────────
    val trees: OGTreeLineSpec = OGTreeLineSpec(),

    // ── Target beacon ─────────────────────────────────────────────────────────
    val targetColor: String = "#44FF88",
    /** Outer glow colour (`#AARRGGBB`). */
    val targetGlowColor: String = "#2244FF88",

    // ── Road lanes ────────────────────────────────────────────────────────────
    val road: OGRoadSpec = OGRoadSpec(),

    // ── Motion ────────────────────────────────────────────────────────────────
    /** Forward scroll speed in world units per second. */
    val scrollSpeed: Float = 180f,
    /** Far clip plane in world units — objects beyond this are culled. */
    val farClip: Float = 1600f,
)

/**
 * Controls how the road bends as it recedes.
 *
 * The road centre at absolute world depth Z is:
 * `bendAmplitude × (0.6·sin(f₁·Z + φ) + 0.4·sin(f₂·Z + φ'))`.
 *
 * The camera's vanishing point tracks the road heading (derivative of the above) with strength
 * [cameraFollowStrength], leaning naturally into every curve.
 */
@Serializable
data class OGPathDynamics(
    /** Maximum lateral displacement from centre, in world units. */
    val bendAmplitude: Float = 160f,
    /** Primary bend spatial frequency (radians per world unit). */
    val bendFrequency: Float = 0.0018f,
    /** Secondary harmonic for organic variation. */
    val bendFrequency2: Float = 0.0027f,
    /** VP tracking strength: 0 = fixed centre, ~0.35 = natural lean, 1 = full. */
    val cameraFollowStrength: Float = 0.35f,
    /** Deterministic seed — same value → same path shape. */
    val seed: Long = 42L,
)

/** Side tree silhouettes — billboard triangles drawn along both road edges. */
@Serializable
data class OGTreeLineSpec(
    val enabled: Boolean = true,
    val treeColor: String = "#0B190B",
    /** Soft ambient glow behind each crown (`#AARRGGBB`). */
    val treeGlowColor: String = "#18181830",
    /** World-unit spacing between consecutive trees. */
    val spacing: Float = 55f,
    /** World X offset from road centre to tree base. */
    val sideOffset: Float = 200f,
    /** Tree height as fraction of canvas height at the near plane (Z ≈ 0). */
    val baseHeightFraction: Float = 0.13f,
    /** Height variation: 0 = uniform, 1 = ±50% variance. */
    val heightVariance: Float = 0.55f,
)

// ── Resolved colours (allocated once per spec change via [OGInfinitePathSpec.resolveColors]) ──

class OGInfinitePathColors(
    val road: Color, val shoulder: Color, val laneMark: Color, val edgeLine: Color,
    val skyTop: Color, val skyHorizon: Color,
    val gndHorizon: Color, val gndNear: Color,
    val haze: Color, val target: Color, val targetGlow: Color,
    val tree: Color, val treeGlow: Color,
)

fun OGInfinitePathSpec.resolveColors() = OGInfinitePathColors(
    road        = roadColor.toSpecColor()         ?: Color(0xFF2B3D2B),
    shoulder    = shoulderColor.toSpecColor()     ?: Color(0xFF19271A),
    laneMark    = laneMarkColor.toSpecColor()     ?: Color(0x99FFFFCC),
    edgeLine    = road.edgeLineColor.toSpecColor() ?: Color(0xAAFFFFFF.toInt()),
    skyTop      = skyTopColor.toSpecColor()       ?: Color(0xFF050D05),
    skyHorizon  = skyHorizonColor.toSpecColor()   ?: Color(0xFF122412),
    gndHorizon  = groundHorizonColor.toSpecColor() ?: Color(0xFF0E190E),
    gndNear     = groundNearColor.toSpecColor()   ?: Color(0xFF182518),
    haze        = hazeColor.toSpecColor()         ?: Color(0x992B5A2B),
    target      = targetColor.toSpecColor()       ?: Color(0xFF44FF88),
    targetGlow  = targetGlowColor.toSpecColor()   ?: Color(0x2244FF88),
    tree        = trees.treeColor.toSpecColor()   ?: Color(0xFF0B190B),
    treeGlow    = trees.treeGlowColor.toSpecColor() ?: Color(0x18181830),
)

// ── Pure math helpers ─────────────────────────────────────────────────────────

/** Lateral world-X centre of the road at absolute world depth [worldZ]. */
internal fun pathCenterX(worldZ: Float, d: OGPathDynamics): Float {
    val phase = (d.seed % 628L).toFloat() * 0.01f
    return d.bendAmplitude * (
        0.6f * sin(d.bendFrequency  * worldZ + phase) +
        0.4f * sin(d.bendFrequency2 * worldZ + phase * 1.618f)
    )
}

/** Lateral heading (dPathCenterX/dZ) — used to tilt the VP with the road bend. */
internal fun pathHeadingX(worldZ: Float, d: OGPathDynamics): Float {
    val phase = (d.seed % 628L).toFloat() * 0.01f
    return d.bendAmplitude * (
        0.6f * d.bendFrequency  * cos(d.bendFrequency  * worldZ + phase) +
        0.4f * d.bendFrequency2 * cos(d.bendFrequency2 * worldZ + phase * 1.618f)
    )
}

/** Deterministic pseudo-random float in `0..1` from [index] and [seed]. */
internal fun treeRng(index: Int, seed: Long): Float {
    val h = (index.toLong() xor seed xor (index.toLong() * 2654435761L)) * 2246822519L
    return ((h ushr 33) and 0xFFFFFFL).toFloat() / 0xFFFFFFL.toFloat()
}

// ── Composable ────────────────────────────────────────────────────────────────

/**
 * Renders an infinite, continuously-scrolling perspective path described by [spec].
 *
 * @param playing  Pause the scroll animation when `false`.
 */
@Composable
fun OGInfinitePathView(
    spec: OGInfinitePathSpec = OGInfinitePathSpec(),
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val colors = remember(spec) { spec.resolveColors() }

    var scroll by remember { mutableStateOf(0f) }
    LaunchedEffect(spec, playing) {
        scroll = 0f
        if (!playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            scroll += (now - last).toFloat() / 1000f * spec.scrollSpeed
            last = now
        }
    }

    Canvas(modifier.clipToBounds()) {
        drawOGInfinitePathFrame(spec, colors, scroll)
    }
}

// ── Frame draw ────────────────────────────────────────────────────────────────

/**
 * Draws one frame of the infinite path at the given [scroll] offset (world units from origin).
 * Call this from [OGInfinitePathView] or from an [OGStage] export bake.
 */
fun DrawScope.drawOGInfinitePathFrame(
    spec: OGInfinitePathSpec,
    colors: OGInfinitePathColors,
    scroll: Float,
) {
    val cam = spec.camera
    val w = size.width
    val h = size.height
    val horizonY = h * cam.horizonFraction

    // Vanishing point: shifts sideways to follow the road heading (lean-into-curve feel)
    val heading = pathHeadingX(scroll, spec.path)
    val vpX = (w * 0.5f + heading * spec.path.cameraFollowStrength * 0.07f * w)
        .coerceIn(w * 0.2f, w * 0.8f)

    // ── 1. Sky ───────────────────────────────────────────────────────────────
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(colors.skyTop, colors.skyHorizon),
            startY = 0f, endY = horizonY,
        ),
        topLeft = Offset.Zero,
        size = Size(w, horizonY),
    )

    // ── 2. Ground ────────────────────────────────────────────────────────────
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(colors.gndHorizon, colors.gndNear),
            startY = horizonY, endY = h,
        ),
        topLeft = Offset(0f, horizonY),
        size = Size(w, h - horizonY),
    )

    // ── 3. Road + shoulders (scanline-sampled curve polygon) ──────────────────
    val STEPS = 70
    val screenYs       = FloatArray(STEPS)
    val leftEdges      = FloatArray(STEPS)
    val rightEdges     = FloatArray(STEPS)
    val leftShoulders  = FloatArray(STEPS)
    val rightShoulders = FloatArray(STEPS)
    var rowCount = 0

    for (i in 0 until STEPS) {
        // Sample rows from bottom (near) up toward the horizon
        val t = i.toFloat() / (STEPS - 1)
        val screenY = h - t * (h - horizonY - 2f)
        val z = cam.groundZAt(screenY, horizonY)
        if (z <= 0f || z >= spec.farClip) continue
        val scale = cam.perspectiveScale(z)
        val cx = pathCenterX(z + scroll, spec.path) * scale
        val screenCx = vpX + cx
        val half  = spec.roadHalfWidth  * scale
        val shld  = spec.shoulderWidth  * scale
        screenYs      [rowCount] = screenY
        leftEdges     [rowCount] = screenCx - half
        rightEdges    [rowCount] = screenCx + half
        leftShoulders [rowCount] = screenCx - half - shld
        rightShoulders[rowCount] = screenCx + half + shld
        rowCount++
    }

    if (rowCount >= 2) {
        // Left shoulder
        val lsp = Path()
        lsp.moveTo(leftShoulders[0], screenYs[0])
        for (k in 0 until rowCount) lsp.lineTo(leftShoulders[k], screenYs[k])
        for (k in rowCount - 1 downTo 0) lsp.lineTo(leftEdges[k], screenYs[k])
        lsp.close()
        drawPath(lsp, colors.shoulder)

        // Right shoulder
        val rsp = Path()
        rsp.moveTo(rightEdges[0], screenYs[0])
        for (k in 0 until rowCount) rsp.lineTo(rightEdges[k], screenYs[k])
        for (k in rowCount - 1 downTo 0) rsp.lineTo(rightShoulders[k], screenYs[k])
        rsp.close()
        drawPath(rsp, colors.shoulder)

        // Road fill
        val rp = Path()
        rp.moveTo(leftEdges[0], screenYs[0])
        for (k in 0 until rowCount) rp.lineTo(leftEdges[k], screenYs[k])
        for (k in rowCount - 1 downTo 0) rp.lineTo(rightEdges[k], screenYs[k])
        rp.close()
        drawPath(rp, colors.road)
    }

    // ── 4. Lane marks ─────────────────────────────────────────────────────────
    val road        = spec.road
    val laneCount   = road.laneCount.coerceAtLeast(1)
    // Dividing-line X offsets in world units (relative to road centre).
    // laneCount lanes → (laneCount - 1) dividers at ±road intervals.
    val dividerXs = if (laneCount <= 1) emptyList()
    else List(laneCount - 1) { k ->
        -spec.roadHalfWidth + spec.roadHalfWidth * 2f * (k + 1).toFloat() / laneCount
    }

    if (road.markStyle != OGLaneMarkStyle.NONE) {
        // SOLID draws at half the interval (overlapping marks → continuous line).
        val step = if (road.markStyle == OGLaneMarkStyle.SOLID) spec.laneMarkInterval * 0.4f
                   else spec.laneMarkInterval
        var localZ = scroll % step
        while (localZ < spec.farClip) {
            val scale   = cam.perspectiveScale(localZ)
            val screenY = cam.screenYAt(localZ, horizonY)
            if (screenY in horizonY..h) {
                val cx   = pathCenterX(localZ + scroll, spec.path)
                val markW = (2.5f * scale).coerceAtLeast(0.5f)
                val markH = (if (road.markStyle == OGLaneMarkStyle.SOLID) step * scale * 1.1f else 10f * scale).coerceAtLeast(1f)
                for (dx in dividerXs) {
                    val sx = vpX + (cx + dx) * scale
                    if (road.markStyle == OGLaneMarkStyle.CATS_EYE)
                        drawCircle(colors.laneMark, markW * 1.5f, Offset(sx, screenY))
                    else
                        drawRect(colors.laneMark, Offset(sx - markW, screenY - markH), Size(markW * 2f, markH * 2f))
                }
            }
            localZ += step
        }
    }

    // Edge lines along road shoulders
    if (road.markStyle != OGLaneMarkStyle.NONE && colors.edgeLine.alpha > 0.01f) {
        val step = spec.laneMarkInterval * 0.5f
        var localZ = scroll % step
        while (localZ < spec.farClip) {
            val scale   = cam.perspectiveScale(localZ)
            val screenY = cam.screenYAt(localZ, horizonY)
            if (screenY in horizonY..h) {
                val cx   = pathCenterX(localZ + scroll, spec.path)
                val markW = (1.5f * scale).coerceAtLeast(0.5f)
                val markH = (step * scale * 1.1f).coerceAtLeast(1f)
                for (edgeSign in listOf(-1f, 1f)) {
                    val sx = vpX + (cx + edgeSign * spec.roadHalfWidth) * scale
                    drawRect(colors.edgeLine, Offset(sx - markW, screenY - markH), Size(markW * 2f, markH * 2f))
                }
            }
            localZ += step
        }
    }

    // ── 5. Trees — far → near (painter's algorithm) ───────────────────────────
    if (spec.trees.enabled) {
        val ts        = spec.trees
        val firstIdx  = (scroll / ts.spacing).toInt()
        val lastIdx   = ((scroll + spec.farClip) / ts.spacing).toInt() + 1

        for (i in lastIdx downTo firstIdx) {
            val worldZ = i.toFloat() * ts.spacing
            val lz     = worldZ - scroll
            if (lz < 1f || lz >= spec.farClip) continue

            val scale  = cam.perspectiveScale(lz)
            val baseY  = cam.screenYAt(lz, horizonY)
            if (baseY < horizonY || baseY > h + 60f) continue

            val cx     = pathCenterX(worldZ, spec.path)
            val rnd    = treeRng(i, ts.spacing.toLong())
            val treeH  = h * ts.baseHeightFraction * scale *
                (1f - ts.heightVariance * 0.5f + ts.heightVariance * rnd)

            if (treeH < 1.5f) continue
            drawSilhouetteTree(vpX + (cx - ts.sideOffset) * scale, baseY, treeH, colors.tree, colors.treeGlow)
            drawSilhouetteTree(vpX + (cx + ts.sideOffset) * scale, baseY, treeH, colors.tree, colors.treeGlow)
        }
    }

    // ── 6. Atmospheric haze (horizon gradient above the ground zone) ───────────
    if (spec.hazeStrength > 0f) {
        val a       = colors.haze.alpha * spec.hazeStrength
        val hazeTop = horizonY - 2f
        val hazeBot = horizonY + (h - horizonY) * 0.55f
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to colors.haze.copy(alpha = a * 0.85f),
                    0.4f to colors.haze.copy(alpha = a * 0.4f),
                    1.0f to colors.haze.copy(alpha = 0f),
                ),
                startY = hazeTop, endY = hazeBot,
            ),
            topLeft = Offset(0f, hazeTop),
            size    = Size(w, hazeBot - hazeTop),
        )
    }

    // ── 7. Target beacon at vanishing point ────────────────────────────────────
    val pulse  = 1f + 0.15f * sin(scroll * 0.03f)
    val glowR  = w * 0.09f * pulse
    val coreR  = w * 0.012f
    drawCircle(
        brush  = Brush.radialGradient(
            colors = listOf(colors.target.copy(alpha = 0.55f), colors.targetGlow, Color.Transparent),
            center = Offset(vpX, horizonY),
            radius = glowR,
        ),
        radius = glowR,
        center = Offset(vpX, horizonY),
    )
    drawCircle(
        color  = colors.target,
        radius = coreR,
        center = Offset(vpX, horizonY),
    )
}

// ── Tree silhouette ───────────────────────────────────────────────────────────

private fun DrawScope.drawSilhouetteTree(
    screenX: Float, baseY: Float, treeH: Float,
    color: Color, glowColor: Color,
) {
    val crownW = treeH * 0.52f
    val trunkW = (treeH * 0.1f).coerceAtLeast(0.5f)
    val trunkH = treeH * 0.32f

    // Soft glow halo around the crown
    if (treeH > 8f && glowColor.alpha > 0.01f) {
        val glow = Path()
        glow.moveTo(screenX, baseY - treeH * 1.08f)
        glow.lineTo(screenX - crownW * 0.62f, baseY - trunkH)
        glow.lineTo(screenX + crownW * 0.62f, baseY - trunkH)
        glow.close()
        drawPath(glow, glowColor)
    }

    // Trunk
    drawRect(
        color   = color,
        topLeft = Offset(screenX - trunkW * 0.5f, baseY - trunkH),
        size    = Size(trunkW, trunkH),
    )

    // Crown triangle
    val crown = Path()
    crown.moveTo(screenX,              baseY - treeH)
    crown.lineTo(screenX - crownW * 0.5f, baseY - trunkH)
    crown.lineTo(screenX + crownW * 0.5f, baseY - trunkH)
    crown.close()
    drawPath(crown, color)
}
