package com.solidkey.painpoints.depth

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.solidkey.painpoints.ai.toSpecColor
import kotlinx.serialization.Serializable
import kotlin.math.sin

/**
 * Shape enum for a [OGBillboardItem]. Enum-only (no Painter/ImageBitmap) so the full spec
 * stays serializable and AI-authorable.
 */
enum class OGBillboardShape {
    /** Upright rectangle (crate, sign, door). */
    RECT,
    /** Circle (boulder, portal, orb). */
    CIRCLE,
    /** 45°-rotated square (gem, power-up). */
    DIAMOND,
    /** Upward-pointing triangle (spike, arrow). */
    TRIANGLE,
    /** Tall narrow rectangle (pillar, column, stalagmite). */
    PILLAR,
    /**
     * Stick-figure runner seen from behind — head + torso + swinging arms and legs.
     * Animate by setting [OGBillboardItem.loopInterval] > 0; the run cycle is driven by camera scroll.
     */
    SILHOUETTE_RUNNER,
    /**
     * Stick-figure crowd member with one raised waving arm.
     * Animate by setting [OGBillboardItem.loopInterval] > 0; wave phase varies by world X position
     * so side-by-side crowd members wave at different times.
     */
    SILHOUETTE_CROWD,
}

/**
 * A single world-space billboard object rendered by [drawOGBillboards].
 *
 * @param worldX Lateral world position (0 = path centre; negative = left).
 * @param worldZ Absolute world depth (not relative to camera scroll). Items whose
 *   `worldZ - scroll` falls outside `[0, farClip]` are culled automatically.
 * @param worldWidth Width in world units at `z = 0` (scales with perspective).
 * @param worldHeight Height in world units at `z = 0`.
 * @param color Fill colour (`#RRGGBB` or `#AARRGGBB`).
 * @param glowColor Optional glow halo (`#AARRGGBB`).
 * @param loopInterval If > 0, this item repeats every [loopInterval] world units along Z.
 */
@Serializable
data class OGBillboardItem(
    val shape: OGBillboardShape = OGBillboardShape.RECT,
    val worldX: Float = 0f,
    val worldZ: Float = 400f,
    val worldWidth: Float = 60f,
    val worldHeight: Float = 80f,
    val color: String = "#44FF88",
    val glowColor: String = "#2244FF88",
    val loopInterval: Float = 0f,
)

/**
 * A set of [OGBillboardItem]s to overlay on a perspective scene.
 *
 * Pass this spec + live camera state to [drawOGBillboards]. Combine with
 * [OGCorridorView] or [OGInfinitePathView] using a `Box` + `Canvas` overlay.
 */
@Serializable
data class OGBillboardSpec(
    val name: String? = null,
    val items: List<OGBillboardItem> = emptyList(),
)

/** Ready-made billboard layouts for common scene types. */
object OGBillboards {
    /** Corridor pillars flanking the path at regular intervals. */
    val TemplePillars = OGBillboardSpec(
        name = "TemplePillars",
        items = listOf(
            OGBillboardItem(OGBillboardShape.PILLAR, worldX = -220f, worldZ = 300f, worldWidth = 30f, worldHeight = 180f, color = "#3D3020", loopInterval = 200f),
            OGBillboardItem(OGBillboardShape.PILLAR, worldX =  220f, worldZ = 300f, worldWidth = 30f, worldHeight = 180f, color = "#3D3020", loopInterval = 200f),
        ),
    )
    /** Scattered boulders and gems on the road. */
    val Obstacles = OGBillboardSpec(
        name = "Obstacles",
        items = listOf(
            OGBillboardItem(OGBillboardShape.RECT,   worldX = -60f, worldZ = 500f, worldWidth = 55f, worldHeight = 50f, color = "#5A3A20"),
            OGBillboardItem(OGBillboardShape.DIAMOND, worldX = 10f, worldZ = 800f, worldWidth = 35f, worldHeight = 35f, color = "#44FFAA", glowColor = "#4444FFAA"),
            OGBillboardItem(OGBillboardShape.CIRCLE, worldX = 80f, worldZ = 1100f, worldWidth = 60f, worldHeight = 60f, color = "#6B3010"),
        ),
    )

    /**
     * A pack of runners racing down the road — three athletes seen from behind, spread across the
     * path, looping as the camera scrolls. Combine with [OGInfinitePathView] underneath.
     */
    val Runners = OGBillboardSpec(
        name = "Runners",
        items = listOf(
            OGBillboardItem(OGBillboardShape.SILHOUETTE_RUNNER, worldX = -40f, worldZ = 300f, worldWidth = 28f, worldHeight = 72f, color = "#FFEE44", loopInterval = 500f),
            OGBillboardItem(OGBillboardShape.SILHOUETTE_RUNNER, worldX =  10f, worldZ = 500f, worldWidth = 28f, worldHeight = 72f, color = "#FF5533", loopInterval = 500f),
            OGBillboardItem(OGBillboardShape.SILHOUETTE_RUNNER, worldX =  70f, worldZ = 700f, worldWidth = 28f, worldHeight = 72f, color = "#44AAFF", loopInterval = 500f),
        ),
    )

    /**
     * Cheering crowd lining both sides of the road — waving figures at high worldX values so they
     * appear at the screen edges. Place over an [OGInfinitePathView] or [OGAtmosphereView].
     */
    val Crowd = OGBillboardSpec(
        name = "Crowd",
        items = listOf(
            OGBillboardItem(OGBillboardShape.SILHOUETTE_CROWD, worldX = -380f, worldZ = 150f, worldWidth = 22f, worldHeight = 58f, color = "#FFCC88", loopInterval = 180f),
            OGBillboardItem(OGBillboardShape.SILHOUETTE_CROWD, worldX = -440f, worldZ = 200f, worldWidth = 22f, worldHeight = 58f, color = "#FF8866", loopInterval = 180f),
            OGBillboardItem(OGBillboardShape.SILHOUETTE_CROWD, worldX = -500f, worldZ = 250f, worldWidth = 22f, worldHeight = 58f, color = "#88DDFF", loopInterval = 180f),
            OGBillboardItem(OGBillboardShape.SILHOUETTE_CROWD, worldX =  380f, worldZ = 150f, worldWidth = 22f, worldHeight = 58f, color = "#AAFFCC", loopInterval = 180f),
            OGBillboardItem(OGBillboardShape.SILHOUETTE_CROWD, worldX =  440f, worldZ = 200f, worldWidth = 22f, worldHeight = 58f, color = "#FFEE66", loopInterval = 180f),
            OGBillboardItem(OGBillboardShape.SILHOUETTE_CROWD, worldX =  500f, worldZ = 250f, worldWidth = 22f, worldHeight = 58f, color = "#FF99BB", loopInterval = 180f),
        ),
    )
}

class OGBillboardColors(val fill: Color, val glow: Color)

fun OGBillboardItem.resolveColors() = OGBillboardColors(
    fill = color.toSpecColor()     ?: Color(0xFF44FF88.toInt()),
    glow = glowColor.toSpecColor() ?: Color(0x2244FF88),
)

/**
 * Draws all visible [OGBillboardSpec.items] into the current [DrawScope].
 *
 * Items are z-sorted far→near (painter's algorithm). Items with `localZ < 0` or
 * `localZ > farClip` are culled. Items with [OGBillboardItem.loopInterval] > 0 are
 * repeated modulo the interval.
 *
 * @param camera   The same camera used for the scene underneath.
 * @param scroll   Current scroll (camera Z) in world units.
 * @param horizonY Horizon screen Y (use `size.height * camera.horizonFraction`).
 * @param vpX      Vanishing-point screen X.
 * @param farClip  Far clip distance — items beyond this are culled.
 */
fun DrawScope.drawOGBillboards(
    spec: OGBillboardSpec,
    camera: OGPerspectiveCamera,
    scroll: Float,
    horizonY: Float,
    vpX: Float,
    farClip: Float = 1600f,
) {
    // Build the visible item list: expand looping items + pre-resolve colors
    data class VisibleItem(
        val item: OGBillboardItem,
        val localZ: Float,
        val colors: OGBillboardColors,
    )

    val visible = mutableListOf<VisibleItem>()
    for (item in spec.items) {
        if (item.loopInterval > 0f) {
            // Emit one copy per loop cycle in [0, farClip]
            val firstK = ((scroll) / item.loopInterval).toInt() - 1
            val lastK  = ((scroll + farClip) / item.loopInterval).toInt() + 1
            val colors = item.resolveColors()
            for (k in firstK..lastK) {
                val absoluteZ = k * item.loopInterval + (item.worldZ % item.loopInterval)
                val localZ = absoluteZ - scroll
                if (localZ in 0f..farClip) visible += VisibleItem(item, localZ, colors)
            }
        } else {
            val localZ = item.worldZ - scroll
            if (localZ in 0f..farClip) visible += VisibleItem(item, localZ, item.resolveColors())
        }
    }

    // Painter's algorithm: draw far items first
    visible.sortByDescending { it.localZ }

    for ((item, localZ, colors) in visible) {
        val scale    = camera.perspectiveScale(localZ)
        val screenX  = camera.projectX(item.worldX, localZ, vpX)
        val screenY  = camera.screenYAt(localZ, horizonY)
        if (screenY < 0f || screenY > size.height + item.worldHeight * scale) continue
        val sw = item.worldWidth  * scale
        val sh = item.worldHeight * scale
        val fade = (1f - localZ / farClip).coerceIn(0f, 1f)

        if (colors.glow.alpha > 0.01f && sw > 2f) {
            drawCircle(
                Brush.radialGradient(
                    listOf(colors.glow.copy(alpha = colors.glow.alpha * fade), Color.Transparent),
                    center = Offset(screenX, screenY - sh * 0.5f),
                    radius = sw * 1.4f,
                ),
                radius = sw * 1.4f, center = Offset(screenX, screenY - sh * 0.5f),
            )
        }

        val fillColor = colors.fill.copy(alpha = colors.fill.alpha * fade)
        // Phase for silhouette animation: driven by scroll + worldX so crowd members desync
        val phase = scroll * 0.08f + item.worldX * 0.012f
        drawBillboardShape(item.shape, screenX, screenY, sw, sh, fillColor, phase)
    }
}

private fun DrawScope.drawBillboardShape(
    shape: OGBillboardShape,
    cx: Float, baseY: Float,
    w: Float, h: Float,
    color: Color,
    phase: Float = 0f,
) {
    when (shape) {
        OGBillboardShape.RECT ->
            drawRect(color, Offset(cx - w * 0.5f, baseY - h), Size(w, h))
        OGBillboardShape.CIRCLE ->
            drawCircle(color, radius = w * 0.5f, center = Offset(cx, baseY - h * 0.5f))
        OGBillboardShape.DIAMOND -> {
            val path = Path()
            path.moveTo(cx, baseY - h)
            path.lineTo(cx + w * 0.5f, baseY - h * 0.5f)
            path.lineTo(cx, baseY)
            path.lineTo(cx - w * 0.5f, baseY - h * 0.5f)
            path.close()
            drawPath(path, color)
        }
        OGBillboardShape.TRIANGLE -> {
            val path = Path()
            path.moveTo(cx, baseY - h)
            path.lineTo(cx + w * 0.5f, baseY)
            path.lineTo(cx - w * 0.5f, baseY)
            path.close()
            drawPath(path, color)
        }
        OGBillboardShape.PILLAR ->
            drawRect(color, Offset(cx - w * 0.5f, baseY - h), Size(w, h))
        OGBillboardShape.SILHOUETTE_RUNNER ->
            drawSilhouetteRunner(cx, baseY, w, h, color, phase)
        OGBillboardShape.SILHOUETTE_CROWD ->
            drawSilhouetteCrowd(cx, baseY, w, h, color, phase)
    }
}

/**
 * Stick-figure runner seen from behind. The run cycle is driven by [phase] (radians):
 * arms and legs alternate at ±35° and ±30° respectively.
 */
private fun DrawScope.drawSilhouetteRunner(
    cx: Float, baseY: Float, w: Float, h: Float, color: Color, phase: Float,
) {
    val sw = (w * 0.18f).coerceAtLeast(1.5f)
    val cap = StrokeCap.Round

    val headR  = h * 0.09f
    val headCy = baseY - h + headR
    drawCircle(color, headR, Offset(cx, headCy))

    val shoulderY = baseY - h * 0.78f
    val hipY      = baseY - h * 0.42f

    // Torso
    drawLine(color, Offset(cx, headCy + headR), Offset(cx, hipY), sw, cap)

    // Arms — opposite phase
    val armSwing = sin(phase) * 0.6f   // radians
    val armLen   = h * 0.28f
    drawLine(color, Offset(cx, shoulderY),
        Offset(cx + sin( armSwing) * armLen, shoulderY + kotlin.math.cos( armSwing).coerceAtLeast(0f) * armLen * 0.6f), sw, cap)
    drawLine(color, Offset(cx, shoulderY),
        Offset(cx + sin(-armSwing) * armLen, shoulderY + kotlin.math.cos(-armSwing).coerceAtLeast(0f) * armLen * 0.6f), sw, cap)

    // Legs — opposite phase
    val legSwing = sin(phase) * 0.55f
    val legLen   = h * 0.42f
    drawLine(color, Offset(cx, hipY),
        Offset(cx + sin( legSwing) * legLen * 0.7f, hipY + kotlin.math.cos( legSwing.coerceIn(-1f, 1f)) * legLen), sw, cap)
    drawLine(color, Offset(cx, hipY),
        Offset(cx + sin(-legSwing) * legLen * 0.7f, hipY + kotlin.math.cos((-legSwing).coerceIn(-1f, 1f)) * legLen), sw, cap)
}

/**
 * Stick-figure crowd member with a waving arm. Left arm is raised overhead and waves; right arm
 * rests at the side. Phase is derived from worldX so adjacent crowd members desync naturally.
 */
private fun DrawScope.drawSilhouetteCrowd(
    cx: Float, baseY: Float, w: Float, h: Float, color: Color, phase: Float,
) {
    val sw  = (w * 0.18f).coerceAtLeast(1.5f)
    val cap = StrokeCap.Round

    val headR  = h * 0.09f
    val headCy = baseY - h + headR
    drawCircle(color, headR, Offset(cx, headCy))

    val shoulderY = baseY - h * 0.78f
    val hipY      = baseY - h * 0.4f

    // Torso
    drawLine(color, Offset(cx, headCy + headR), Offset(cx, hipY), sw, cap)

    // Right arm — resting down and slightly out
    drawLine(color, Offset(cx, shoulderY), Offset(cx + w * 0.5f, hipY + h * 0.05f), sw, cap)

    // Left arm — raised, waving (phase-driven swing)
    val waveAngle = -1.1f + sin(phase) * 0.4f  // raised ~63° with ±23° wave
    val armLen = h * 0.3f
    drawLine(color, Offset(cx, shoulderY),
        Offset(cx - sin(waveAngle) * armLen, shoulderY - kotlin.math.cos(waveAngle) * armLen), sw, cap)

    // Legs — static (standing in crowd)
    drawLine(color, Offset(cx, hipY), Offset(cx - w * 0.3f, baseY), sw, cap)
    drawLine(color, Offset(cx, hipY), Offset(cx + w * 0.3f, baseY), sw, cap)
}
