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

/**
 * Visual style of a [OGScrollLayer].
 * Enum-only (no lambdas) so the full spec stays serializable and AI-authorable.
 */
enum class OGScrollLayerStyle {
    /** Solid colour fill. Use for far sky or ground fill layers. */
    SOLID,
    /** Vertical gradient from [OGScrollLayer.primaryColor] (top) to [OGScrollLayer.secondaryColor] (bottom). */
    GRADIENT_VERTICAL,
    /** Silhouette mountain/hill profile. Painter's-algorithm back-to-front. */
    SILHOUETTE_HILLS,
    /** Silhouette tree line of triangle conifers. */
    SILHOUETTE_TREES,
    /** Soft horizontal cloud ellipses. */
    CLOUDS,
    /** Scattered star dots. */
    STARS,
}

/**
 * One layer in an [OGScrollDepthSpec] background stack.
 *
 * @param depth Perceived depth: 0 = near (fastest scroll), 1 = far (slowest / static).
 * @param verticalFraction Fraction of canvas height this layer occupies, anchored to the bottom.
 *   Use `1f` for a full-height layer (sky), `0.4f` for a mid-height silhouette band.
 * @param density Element count — hills, trees, stars, or clouds per tile.
 * @param seed Deterministic seed for shape placement.
 */
@Serializable
data class OGScrollLayer(
    val style: OGScrollLayerStyle = OGScrollLayerStyle.SOLID,
    val depth: Float = 0.5f,
    val verticalFraction: Float = 1f,
    val primaryColor: String = "#112211",
    val secondaryColor: String = "#1A331A",
    val density: Int = 8,
    val seed: Long = 42L,
)

/**
 * Multi-layer depth-parallax background scroller. Each [OGScrollLayer] scrolls at a speed
 * proportional to `(1 - depth)` — far layers scroll slowly, near layers quickly, giving
 * natural parallax depth without 3D projection.
 *
 * Drawn by [OGScrollDepthView] / [drawOGScrollDepthFrame].
 */
@Serializable
data class OGScrollDepthSpec(
    val name: String? = null,
    /** Base scroll speed in canvas-pixels per second for a depth=0 layer. */
    val baseSpeed: Float = 80f,
    val layers: List<OGScrollLayer> = listOf(
        OGScrollLayer(OGScrollLayerStyle.GRADIENT_VERTICAL, depth = 1f, primaryColor = "#050A14", secondaryColor = "#1A2834"),
        OGScrollLayer(OGScrollLayerStyle.STARS, depth = 0.9f, primaryColor = "#AACCFF", density = 40, seed = 1L),
        OGScrollLayer(OGScrollLayerStyle.SILHOUETTE_HILLS, depth = 0.6f, primaryColor = "#0A1A0A", verticalFraction = 0.4f, seed = 10L),
        OGScrollLayer(OGScrollLayerStyle.SILHOUETTE_TREES, depth = 0.3f, primaryColor = "#061206", verticalFraction = 0.35f, seed = 20L),
    ),
)

/** Ready-made scroll-depth presets. */
object OGScrollDepths {
    val Jungle = OGScrollDepthSpec(
        name = "Jungle",
        layers = listOf(
            OGScrollLayer(OGScrollLayerStyle.GRADIENT_VERTICAL, depth = 1f, primaryColor = "#050D05", secondaryColor = "#0A1A0A"),
            OGScrollLayer(OGScrollLayerStyle.SILHOUETTE_HILLS, depth = 0.7f, primaryColor = "#0A150A", verticalFraction = 0.45f, density = 6, seed = 10L),
            OGScrollLayer(OGScrollLayerStyle.SILHOUETTE_TREES, depth = 0.4f, primaryColor = "#071007", verticalFraction = 0.4f, density = 10, seed = 20L),
            OGScrollLayer(OGScrollLayerStyle.SILHOUETTE_TREES, depth = 0.15f, primaryColor = "#040A04", verticalFraction = 0.35f, density = 7, seed = 30L),
        ),
    )
    val Space = OGScrollDepthSpec(
        name = "Space",
        layers = listOf(
            OGScrollLayer(OGScrollLayerStyle.SOLID, depth = 1f, primaryColor = "#010205"),
            OGScrollLayer(OGScrollLayerStyle.STARS, depth = 0.9f, primaryColor = "#AABBFF", density = 60, seed = 1L),
            OGScrollLayer(OGScrollLayerStyle.STARS, depth = 0.5f, primaryColor = "#FFFFFF", density = 20, seed = 2L),
            OGScrollLayer(OGScrollLayerStyle.CLOUDS, depth = 0.3f, primaryColor = "#10204040", density = 3, seed = 3L),
        ),
    )
    val Cave = OGScrollDepthSpec(
        name = "Cave",
        layers = listOf(
            OGScrollLayer(OGScrollLayerStyle.SOLID, depth = 1f, primaryColor = "#050402"),
            OGScrollLayer(OGScrollLayerStyle.SILHOUETTE_HILLS, depth = 0.6f, primaryColor = "#0A0804", verticalFraction = 0.5f, density = 5, seed = 100L),
            OGScrollLayer(OGScrollLayerStyle.SILHOUETTE_HILLS, depth = 0.3f, primaryColor = "#070503", verticalFraction = 0.4f, density = 4, seed = 200L),
        ),
    )
}

class OGScrollLayerColors(val primary: Color, val secondary: Color)

fun OGScrollLayer.resolveColors() = OGScrollLayerColors(
    primary   = primaryColor.toSpecColor()   ?: Color(0xFF112211.toInt()),
    secondary = secondaryColor.toSpecColor() ?: Color(0xFF1A331A.toInt()),
)

@Composable
fun OGScrollDepthView(
    spec: OGScrollDepthSpec = OGScrollDepthSpec(),
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val layerColors = remember(spec) { spec.layers.map { it.resolveColors() } }
    var scrollX by remember { mutableStateOf(0f) }
    LaunchedEffect(spec, playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            scrollX += spec.baseSpeed * (now - last) / 1000f
            last = now
        }
    }
    Canvas(modifier.clipToBounds()) {
        drawOGScrollDepthFrame(spec, layerColors, scrollX)
    }
}

fun DrawScope.drawOGScrollDepthFrame(
    spec: OGScrollDepthSpec,
    layerColors: List<OGScrollLayerColors>,
    scrollX: Float,
) {
    val w = size.width
    val h = size.height
    // Draw far layers first (painter's algo)
    val order = spec.layers.indices.sortedByDescending { spec.layers[it].depth }
    for (idx in order) {
        val layer      = spec.layers[idx]
        val colors     = layerColors[idx]
        val layerScroll = scrollX * (1f - layer.depth.coerceIn(0f, 1f))
        val bandH      = h * layer.verticalFraction.coerceIn(0f, 1f)
        val bandTop    = h - bandH
        drawOGScrollLayer(layer, colors, layerScroll, w, h, bandH, bandTop)
    }
}

private fun DrawScope.drawOGScrollLayer(
    layer: OGScrollLayer,
    colors: OGScrollLayerColors,
    scrollX: Float,
    w: Float, h: Float,
    bandH: Float, bandTop: Float,
) {
    when (layer.style) {
        OGScrollLayerStyle.SOLID ->
            drawRect(colors.primary, topLeft = Offset.Zero, size = Size(w, h))
        OGScrollLayerStyle.GRADIENT_VERTICAL ->
            drawRect(
                Brush.verticalGradient(listOf(colors.primary, colors.secondary), 0f, h),
                topLeft = Offset.Zero, size = Size(w, h),
            )
        OGScrollLayerStyle.STARS  -> drawLayerStars(layer, colors, scrollX, w, h)
        OGScrollLayerStyle.CLOUDS -> drawLayerClouds(layer, colors, scrollX, w, h)
        OGScrollLayerStyle.SILHOUETTE_HILLS -> drawLayerHills(layer, colors, scrollX, w, h, bandH, bandTop)
        OGScrollLayerStyle.SILHOUETTE_TREES -> drawLayerTrees(layer, colors, scrollX, w, h, bandH, bandTop)
    }
}

private fun DrawScope.drawLayerStars(
    layer: OGScrollLayer, colors: OGScrollLayerColors, scrollX: Float, w: Float, h: Float,
) {
    val n = layer.density
    for (i in 0 until n) {
        val baseX = scrollLayerRng(i, layer.seed, 0) * w
        val sy    = scrollLayerRng(i, layer.seed, 1) * h
        val r     = scrollLayerRng(i, layer.seed, 2) * 1.5f + 0.5f
        // Tile across two widths to handle wrap
        for (t in -1..1) {
            val sx = baseX + t * w - scrollX % w
            if (sx >= -r && sx <= w + r) {
                drawCircle(colors.primary, radius = r, center = Offset(sx, sy))
            }
        }
    }
}

private fun DrawScope.drawLayerClouds(
    layer: OGScrollLayer, colors: OGScrollLayerColors, scrollX: Float, w: Float, h: Float,
) {
    val n = layer.density
    for (i in 0 until n) {
        val baseX = scrollLayerRng(i, layer.seed, 0) * w
        val cy    = scrollLayerRng(i, layer.seed, 1) * h * 0.6f
        val rw    = (scrollLayerRng(i, layer.seed, 2) * 0.12f + 0.08f) * w
        val rh    = rw * (scrollLayerRng(i, layer.seed, 3) * 0.3f + 0.2f)
        for (t in -1..1) {
            val cx = baseX + t * w - scrollX % w
            if (cx >= -rw && cx <= w + rw) {
                drawOval(colors.primary, topLeft = Offset(cx - rw, cy - rh), size = Size(rw * 2f, rh * 2f))
            }
        }
    }
}

private fun DrawScope.drawLayerHills(
    layer: OGScrollLayer, colors: OGScrollLayerColors,
    scrollX: Float, w: Float, h: Float, bandH: Float, bandTop: Float,
) {
    val n    = layer.density.coerceAtLeast(2)
    val segW = w / n
    val tileW = segW * n
    val offset = scrollX % tileW
    val path = Path()
    path.moveTo(-tileW, h)
    for (t in -1..1) {
        for (i in 0 until n + 2) {
            val x = t * tileW + i * segW - offset
            val peakH = scrollLayerRng(i, layer.seed, 0) * bandH * 0.6f + bandH * 0.1f
            val peakY = bandTop + (bandH - peakH)
            path.cubicTo(x - segW * 0.4f, h, x - segW * 0.1f, peakY, x, peakY)
            path.cubicTo(x + segW * 0.1f, peakY, x + segW * 0.4f, h, x + segW, h)
        }
    }
    path.lineTo(w + tileW, h)
    path.close()
    drawPath(path, colors.primary)
}

private fun DrawScope.drawLayerTrees(
    layer: OGScrollLayer, colors: OGScrollLayerColors,
    scrollX: Float, w: Float, h: Float, bandH: Float, bandTop: Float,
) {
    val n    = layer.density.coerceAtLeast(2)
    val segW = w / n
    val tileW = segW * n
    val offset = scrollX % tileW
    for (t in -1..1) {
        for (i in 0 until n + 2) {
            val jitter = (scrollLayerRng(i, layer.seed, 4) - 0.5f) * segW * 0.4f
            val cx     = t * tileW + i * segW + jitter - offset
            val treeH  = scrollLayerRng(i, layer.seed, 0) * bandH * 0.5f + bandH * 0.3f
            val treeW  = treeH * 0.6f
            val baseY  = bandTop + bandH
            if (cx < -treeW || cx > w + treeW) continue
            val path = Path()
            path.moveTo(cx, baseY - treeH)
            path.lineTo(cx - treeW * 0.5f, baseY)
            path.lineTo(cx + treeW * 0.5f, baseY)
            path.close()
            drawPath(path, colors.primary)
        }
    }
    // Solid ground strip under tree bases
    if (bandH > 2f) {
        drawRect(colors.primary, topLeft = Offset(0f, bandTop + bandH * 0.88f), size = Size(w, h - bandTop - bandH * 0.88f))
    }
}

/** Deterministic pseudo-random float from [index], [seed], [channel]. */
internal fun scrollLayerRng(index: Int, seed: Long, channel: Int): Float {
    val h = (index.toLong() xor seed xor (index.toLong() * 2654435761L) xor (channel.toLong() * 1234567891L)) * 2246822519L
    return ((h ushr 33).toFloat() / (1L shl 31).toFloat()).coerceIn(0f, 1f)
}
