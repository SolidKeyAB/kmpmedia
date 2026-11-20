package com.solidkey.painpoints.shape

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.solidkey.painpoints.ai.OGAiVector
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ── Codec ─────────────────────────────────────────────────────────────────────

/**
 * Codec + ready-made presets for [OGPatternSpec] — decode JSON, encode back, and reach for
 * the built-in named patterns. Each preset is a distinct seeded instance so they all look
 * different from each other.
 *
 * Draw with [OGPatternView] (Composable) or `DrawScope.drawOGPattern` (Canvas extension).
 */
object OGPatterns {
    private val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGPatternSpec =
        json.decodeFromString(OGPatternSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGPatternSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGPatternSpec): String = json.encodeToString(OGPatternSpec.serializer(), spec)

    // ── Snowflake presets ────────────────────────────────────────────────────

    /** A classic 6-arm dendrite ice crystal. */
    val SnowCrystal = OGPatternSpec(OGPatternType.SNOWFLAKE, seed = 42, color = "#CCFFFFFF",
        strokeFraction = 0.016f, complexity = 2, count = 6)

    /** A 12-arm super-crystal — denser and more elaborate. */
    val SuperCrystal = OGPatternSpec(OGPatternType.SNOWFLAKE, seed = 7, color = "#AAFFFFFF",
        strokeFraction = 0.012f, complexity = 3, count = 12)

    /** Simple 6-arm prong snowflake (no fractal sub-twigs). */
    val SimpleProng = OGPatternSpec(OGPatternType.SNOWFLAKE, seed = 13, color = "#BBFFFFFF",
        strokeFraction = 0.022f, complexity = 0, count = 6)

    // ── Spider web presets ───────────────────────────────────────────────────

    /** A classic 8-spoke orb web. */
    val OrbWeb = OGPatternSpec(OGPatternType.SPIDER_WEB, seed = 3, color = "#99FFFFFF",
        strokeFraction = 0.012f, complexity = 5, count = 8)

    /** A dense 12-spoke web with many rings. */
    val DenseWeb = OGPatternSpec(OGPatternType.SPIDER_WEB, seed = 17, color = "#88CCCCCC",
        strokeFraction = 0.010f, complexity = 8, count = 12)

    /** A sparse 6-spoke web — minimalist. */
    val SparseWeb = OGPatternSpec(OGPatternType.SPIDER_WEB, seed = 99, color = "#AAFFFFFF",
        strokeFraction = 0.018f, complexity = 3, count = 6)

    // ── Honeycomb presets ────────────────────────────────────────────────────

    /** Golden honeycomb cells — warm amber tones. */
    val GoldenHive = OGPatternSpec(OGPatternType.HONEYCOMB, seed = 0, color = "#CCFFCC66",
        strokeFraction = 0.014f, count = 5)

    /** Cool carbon-fibre look. */
    val CarbonGrid = OGPatternSpec(OGPatternType.HONEYCOMB, seed = 0, color = "#8844AAFF",
        strokeFraction = 0.018f, count = 7)

    // ── Leaf vein presets ────────────────────────────────────────────────────

    /** A fresh tropical leaf with lush branching. */
    val TropicalLeaf = OGPatternSpec(OGPatternType.LEAF_VEINS, seed = 5, color = "#CC88FF66",
        strokeFraction = 0.018f, complexity = 2, count = 5)

    /** A sparse autumn leaf — fewer veins, bare feel. */
    val AutumnLeaf = OGPatternSpec(OGPatternType.LEAF_VEINS, seed = 23, color = "#CCFFAA44",
        strokeFraction = 0.022f, complexity = 1, count = 3)
}

// ── View ──────────────────────────────────────────────────────────────────────

/**
 * Renders a single [spec] pattern centered and scaled to fill the composable's bounds.
 * Static — patterns are not animated; nest inside a Composable that changes [spec.seed] to
 * cycle through unique variants.
 */
@Composable
fun OGPatternView(spec: OGPatternSpec, modifier: Modifier = Modifier) {
    val color = remember(spec.color) { spec.color.toSpecColor() ?: Color.White }
    Canvas(modifier) {
        drawOGPattern(spec, color)
    }
}

// ── Canvas extension ──────────────────────────────────────────────────────────

/**
 * Draw the [spec] pattern into this [DrawScope], centered and scaled to fill the available bounds.
 * Pass a pre-resolved [color] to avoid re-parsing the hex string on every frame.
 */
fun DrawScope.drawOGPattern(spec: OGPatternSpec, color: Color = Color.White) {
    when (spec.type) {
        OGPatternType.SNOWFLAKE  -> drawSnowflakePattern(spec, color)
        OGPatternType.SPIDER_WEB -> drawSpiderWebPattern(spec, color)
        OGPatternType.HONEYCOMB  -> drawHoneycombPattern(spec, color)
        OGPatternType.LEAF_VEINS -> drawLeafVeinsPattern(spec, color)
    }
}

// ── SNOWFLAKE ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawSnowflakePattern(spec: OGPatternSpec, color: Color) {
    val radius = min(size.width, size.height) * 0.46f
    val cx = size.width * 0.5f; val cy = size.height * 0.5f
    val strokeW = radius * 2f * spec.strokeFraction.coerceIn(0.005f, 0.08f)
    val segs = OGSnowflake.generate(spec.seed, spec.count, depth = spec.complexity)
    val stroke = Stroke(width = strokeW, cap = StrokeCap.Round)
    for (seg in segs) {
        drawLine(color, Offset(cx + seg.a.x * radius, cy - seg.a.y * radius),
            Offset(cx + seg.b.x * radius, cy - seg.b.y * radius), strokeW, StrokeCap.Round)
    }
    drawLine(color, Offset(cx, cy), Offset(cx, cy), strokeW * 1.5f, StrokeCap.Round)
    // suppress unused
    stroke.toString()
}

// ── SPIDER WEB ────────────────────────────────────────────────────────────────

private fun DrawScope.drawSpiderWebPattern(spec: OGPatternSpec, color: Color) {
    val radius = min(size.width, size.height) * 0.46f
    val cx = size.width * 0.5f; val cy = size.height * 0.5f
    val strokeW = radius * 2f * spec.strokeFraction.coerceIn(0.004f, 0.06f)
    val segs = OGSpiderWeb.generate(spec.seed, spec.count, rings = spec.complexity)
    for (seg in segs) {
        drawLine(color, Offset(cx + seg.a.x * radius, cy + seg.a.y * radius),
            Offset(cx + seg.b.x * radius, cy + seg.b.y * radius), strokeW, StrokeCap.Round)
    }
}

// ── HONEYCOMB ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawHoneycombPattern(spec: OGPatternSpec, color: Color) {
    val cells = spec.count.coerceIn(2, 20)
    val cellR = size.width / (cells * 1.5f + 0.5f)   // vertex-to-centre radius
    val strokeW = cellR * 2f * spec.strokeFraction.coerceIn(0.005f, 0.1f)
    val hexH = cellR * sqrt(3f)

    val cols = (size.width / (cellR * 1.5f)).toInt() + 2
    val rows = (size.height / hexH).toInt() + 2

    for (col in -1..cols) {
        for (row in -1..rows) {
            val cx = col * cellR * 1.5f
            val cy = row * hexH + (if (col and 1 == 1) hexH * 0.5f else 0f)
            drawHexagon(cx, cy, cellR, strokeW, color)
        }
    }
}

private fun DrawScope.drawHexagon(cx: Float, cy: Float, r: Float, strokeW: Float, color: Color) {
    val path = Path()
    for (k in 0..5) {
        val angle = k * (PI / 3f) + PI / 6f   // flat-top hex
        val x = cx + cos(angle) * r
        val y = cy + sin(angle) * r
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Stroke(width = strokeW, cap = StrokeCap.Round))
}

// ── LEAF VEINS ────────────────────────────────────────────────────────────────

private fun DrawScope.drawLeafVeinsPattern(spec: OGPatternSpec, color: Color) {
    val halfW = size.width * 0.38f
    val halfH = size.height * 0.46f
    val cx = size.width * 0.5f; val cy = size.height * 0.5f
    val scale = min(halfW, halfH)
    val strokeW = scale * 2f * spec.strokeFraction.coerceIn(0.005f, 0.07f)
    val segs = OGLeafVeins.generate(spec.seed, veins = spec.count, depth = spec.complexity)
    for (seg in segs) {
        // Leaf is 1x2 unit box — map x→halfW, y→halfH
        drawLine(color,
            Offset(cx + seg.a.x * halfW, cy + seg.a.y * halfH),
            Offset(cx + seg.b.x * halfW, cy + seg.b.y * halfH),
            strokeW, StrokeCap.Round)
    }
}

private const val PI = 3.1415927f
