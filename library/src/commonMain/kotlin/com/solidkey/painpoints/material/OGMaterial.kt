package com.solidkey.painpoints.material

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.solidkey.painpoints.ai.OGAiVector
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Codec + presets for all four material specs: [OGSurfaceSpec], [OGIridescenceSpec],
 * [OGTranslucencySpec], [OGMetalSpec].
 *
 * Layer them on top of each other — and on top of [com.solidkey.painpoints.light.OGLightSpec]
 * — to dress a flat vector in a full physical material: form-shade the body (OGLight), then
 * apply surface finish (OGSurface), add a metallic sheen or iridescent overlay (OGMetal /
 * OGIridescence), and let light bleed through the edges (OGTranslucency).
 */
object OGMaterial {
    val json get() = OGAiVector.json

    fun decodeSurface(text: String): OGSurfaceSpec =
        json.decodeFromString(OGSurfaceSpec.serializer(), OGAiVector.extractJson(text))
    fun decodeSurfaceOrNull(text: String): OGSurfaceSpec? = runCatching { decodeSurface(text) }.getOrNull()
    fun encode(spec: OGSurfaceSpec): String = json.encodeToString(OGSurfaceSpec.serializer(), spec)

    fun decodeIridescence(text: String): OGIridescenceSpec =
        json.decodeFromString(OGIridescenceSpec.serializer(), OGAiVector.extractJson(text))
    fun decodeIridescenceOrNull(text: String): OGIridescenceSpec? = runCatching { decodeIridescence(text) }.getOrNull()
    fun encode(spec: OGIridescenceSpec): String = json.encodeToString(OGIridescenceSpec.serializer(), spec)

    fun decodeTranslucency(text: String): OGTranslucencySpec =
        json.decodeFromString(OGTranslucencySpec.serializer(), OGAiVector.extractJson(text))
    fun decodeTranslucencyOrNull(text: String): OGTranslucencySpec? = runCatching { decodeTranslucency(text) }.getOrNull()
    fun encode(spec: OGTranslucencySpec): String = json.encodeToString(OGTranslucencySpec.serializer(), spec)

    fun decodeMetal(text: String): OGMetalSpec =
        json.decodeFromString(OGMetalSpec.serializer(), OGAiVector.extractJson(text))
    fun decodeMetalOrNull(text: String): OGMetalSpec? = runCatching { decodeMetal(text) }.getOrNull()
    fun encode(spec: OGMetalSpec): String = json.encodeToString(OGMetalSpec.serializer(), spec)

    // ── Surface presets ──────────────────────────────────────────────────────────────────────────────
    val CERAMIC = OGSurfaceSpec(name = "ceramic", finish = OGFinish.SATIN, specularColor = "#FFFFFF", lightAngleDeg = -120f)
    val LACQUER = OGSurfaceSpec(name = "lacquer", finish = OGFinish.GLOSS, specularColor = "#FFFFFF", lightAngleDeg = -120f)
    val CHROME  = OGSurfaceSpec(name = "chrome",  finish = OGFinish.MIRROR, specularColor = "#FFFFFF", lightAngleDeg = -120f)
    val RUBBER  = OGSurfaceSpec(name = "rubber",  finish = OGFinish.MATTE,  specularColor = "#FFFFFF", lightAngleDeg = -120f)
    val surfacePresets: Map<String, OGSurfaceSpec> = listOf(CERAMIC, LACQUER, CHROME, RUBBER).associateBy { it.name!! }

    // ── Iridescence presets ──────────────────────────────────────────────────────────────────────────
    val SOAP        = OGIridescenceSpec(name = "soap",        hueRange = 240f, baseHue = 180f, sweepAngleDeg = -45f, intensity = 0.5f)
    val BEETLE      = OGIridescenceSpec(name = "beetle",      hueRange = 60f,  baseHue = 120f, sweepAngleDeg = -30f, intensity = 0.55f, saturation = 0.9f)
    val HOLOGRAPHIC = OGIridescenceSpec(name = "holographic", hueRange = 360f, baseHue = 0f,   sweepAngleDeg = -55f, intensity = 0.6f,  saturation = 0.85f, lightness = 0.6f)
    val PEARL       = OGIridescenceSpec(name = "pearl",       hueRange = 80f,  baseHue = 200f, sweepAngleDeg = -40f, intensity = 0.3f,  saturation = 0.5f,  lightness = 0.78f)
    val iridescencePresets: Map<String, OGIridescenceSpec> = listOf(SOAP, BEETLE, HOLOGRAPHIC, PEARL).associateBy { it.name!! }

    // ── Translucency presets ─────────────────────────────────────────────────────────────────────────
    val JADE     = OGTranslucencySpec(name = "jade",   scatterColor = "#A8F0C8", depth = 0.28f, scatterIntensity = 0.4f, rimStrength = 0.3f,  rimAngleDeg = 60f)
    val WAX      = OGTranslucencySpec(name = "wax",    scatterColor = "#FFE8C0", depth = 0.35f, scatterIntensity = 0.5f, rimStrength = 0.45f, rimAngleDeg = 55f)
    val SKIN_T   = OGTranslucencySpec(name = "skin",   scatterColor = "#FFD0B0", depth = 0.22f, scatterIntensity = 0.35f, rimStrength = 0.25f, rimAngleDeg = 65f)
    val FROSTED  = OGTranslucencySpec(name = "frosted", scatterColor = "#DDEEFF", depth = 0.4f, scatterIntensity = 0.45f, rimStrength = 0.15f, rimAngleDeg = 90f)
    val translucencyPresets: Map<String, OGTranslucencySpec> = listOf(JADE, WAX, SKIN_T, FROSTED).associateBy { it.name!! }

    // ── Metal presets ────────────────────────────────────────────────────────────────────────────────
    val SILVER        = OGMetalSpec(name = "silver",        baseColor = "#C8CCCE", reflectivity = 0.75f, anisotropy = 0f,    lightAngleDeg = -120f)
    val GOLD          = OGMetalSpec(name = "gold",          baseColor = "#D4A93A", reflectivity = 0.7f,  anisotropy = 0f,    lightAngleDeg = -120f)
    val COPPER        = OGMetalSpec(name = "copper",        baseColor = "#B87333", reflectivity = 0.65f, anisotropy = 0f,    lightAngleDeg = -120f)
    val BRUSHED_STEEL = OGMetalSpec(name = "brushed_steel", baseColor = "#A8AAAC", reflectivity = 0.55f, anisotropy = 0.8f,  brushAngleDeg = 0f, lightAngleDeg = -120f)
    val ROSE_GOLD     = OGMetalSpec(name = "rose_gold",     baseColor = "#C8847A", reflectivity = 0.6f,  anisotropy = 0f,    lightAngleDeg = -120f)
    val metalPresets: Map<String, OGMetalSpec> = listOf(SILVER, GOLD, COPPER, BRUSHED_STEEL, ROSE_GOLD).associateBy { it.name!! }
}

// ── DrawScope: surface specular highlight ────────────────────────────────────────────────────────────

/**
 * Draw a specular highlight onto [outline] matching [surface]'s finish — from a subtle satin blob
 * to a mirror-sharp pinpoint. Screen-blended so it only brightens the surface, never flips dark areas.
 * Call after [com.solidkey.painpoints.light.OGLight.drawOGLight] so the highlight sits on top of the
 * form-shade.
 */
fun DrawScope.drawOGSurface(outline: Path, surface: OGSurfaceSpec) {
    val s = surface.sanitized()
    if (s.isIdentity) return
    val b = outline.getBounds()
    if (b.width <= 0f || b.height <= 0f) return
    val span = 0.5f * hypot(b.width, b.height)
    val c = b.center
    val dir = s.lightDirection()
    val litPt = Offset(c.x + dir.x * span * 0.6f, c.y + dir.y * span * 0.6f)
    val radius = (s.specularSize * span).coerceAtLeast(1f)
    val tint = s.specularColor.toSpecColor() ?: Color.White
    clipPath(outline) {
        drawCircle(
            brush = Brush.radialGradient(
                0f    to tint.copy(alpha = s.specularIntensity),
                0.5f  to tint.copy(alpha = s.specularIntensity * 0.3f),
                1f    to tint.copy(alpha = 0f),
                center = litPt,
                radius = radius,
            ),
            radius = radius,
            center = litPt,
            blendMode = BlendMode.Screen,
        )
    }
}

/** Apply [surface] finish highlight on top of this composable's content, clipped to [shape]. */
fun Modifier.ogSurface(surface: OGSurfaceSpec, shape: Shape = RectangleShape): Modifier =
    this.drawWithCache {
        val outline = shapeToPath(shape, this)
        onDrawWithContent {
            drawContent()
            drawOGSurface(outline, surface)
        }
    }

// ── DrawScope: iridescence ───────────────────────────────────────────────────────────────────────────

/**
 * Draw a thin-film iridescent colour-shift gradient across [outline] — the visual signature of soap
 * bubbles, beetle shells, and holographic foil. Overlay-blended to tint the surface without washing
 * it out; multiple gradient stops cycle the hue wheel so the transition stays perceptually smooth.
 */
fun DrawScope.drawOGIridescence(outline: Path, iridescence: OGIridescenceSpec) {
    val s = iridescence.sanitized()
    if (s.isIdentity) return
    val b = outline.getBounds()
    if (b.width <= 0f || b.height <= 0f) return
    val span = 0.5f * hypot(b.width, b.height)
    val c = b.center
    val dir = s.sweepDirection()
    val start = Offset(c.x - dir.x * span, c.y - dir.y * span)
    val end   = Offset(c.x + dir.x * span, c.y + dir.y * span)
    val stops = 12
    val colors = List(stops) { i ->
        val t = i / (stops - 1f)
        colorFromHsl(
            hue        = s.baseHue + s.hueRange * t,
            saturation = s.saturation,
            lightness  = s.lightness,
            alpha      = s.intensity,
        )
    }
    clipPath(outline) {
        drawRect(
            brush = Brush.linearGradient(colors = colors, start = start, end = end),
            blendMode = BlendMode.Overlay,
        )
    }
}

/** Apply iridescent colour-shift over this composable's content, clipped to [shape]. */
fun Modifier.ogIridescence(iridescence: OGIridescenceSpec, shape: Shape = RectangleShape): Modifier =
    this.drawWithCache {
        val outline = shapeToPath(shape, this)
        onDrawWithContent {
            drawContent()
            drawOGIridescence(outline, iridescence)
        }
    }

// ── DrawScope: translucency / subsurface scatter ─────────────────────────────────────────────────────

/**
 * Draw a subsurface scatter edge-glow onto [outline] — light that enters the material at the edges
 * and bleeds inward in [OGTranslucencySpec.scatterColor], plus an optional bright rim on the backlit
 * side. Screen-blended so it only brightens; call it after the form-shade for a correct layering.
 */
fun DrawScope.drawOGTranslucency(outline: Path, translucency: OGTranslucencySpec) {
    val s = translucency.sanitized()
    if (s.isIdentity) return
    val b = outline.getBounds()
    if (b.width <= 0f || b.height <= 0f) return
    val short = min(b.width, b.height)
    val tint = s.scatterColor.toSpecColor() ?: Color.White
    // Edge scatter: a wide inner stroke in scatterColor, only the inside-half visible through clipPath.
    if (s.scatterIntensity > 0f) {
        clipPath(outline) {
            drawPath(
                outline,
                color = tint.copy(alpha = s.scatterIntensity),
                style = Stroke(width = short * s.depth * 2f),
                blendMode = BlendMode.Screen,
            )
        }
    }
    // Backlit rim: a thin gradient band on the rim side.
    if (s.rimStrength > 0f) {
        val span = 0.5f * hypot(b.width, b.height)
        val c = b.center
        val rimDir = s.rimDirection()
        val rimPt  = Offset(c.x + rimDir.x * span, c.y + rimDir.y * span)
        val darkPt = Offset(c.x - rimDir.x * span, c.y - rimDir.y * span)
        clipPath(outline) {
            drawPath(
                outline,
                brush = Brush.linearGradient(
                    0f   to tint.copy(alpha = s.rimStrength),
                    0.4f to tint.copy(alpha = 0f),
                    1f   to tint.copy(alpha = 0f),
                    start = rimPt,
                    end   = darkPt,
                ),
                style = Stroke(width = short * 0.06f),
                blendMode = BlendMode.Screen,
            )
        }
    }
}

/** Apply subsurface edge glow + rim over this composable's content, clipped to [shape]. */
fun Modifier.ogTranslucency(translucency: OGTranslucencySpec, shape: Shape = RectangleShape): Modifier =
    this.drawWithCache {
        val outline = shapeToPath(shape, this)
        onDrawWithContent {
            drawContent()
            drawOGTranslucency(outline, translucency)
        }
    }

// ── DrawScope: metal ─────────────────────────────────────────────────────────────────────────────────

/**
 * Draw a metallic surface treatment onto [outline]: a tinted directional-reflection gradient plus a
 * sharp specular for polished metal, or fine anisotropic lines plus a softer specular for brushed
 * metal. Screen-blended over existing content; call after [drawOGSurface] when stacking both.
 */
fun DrawScope.drawOGMetal(outline: Path, metal: OGMetalSpec) {
    val s = metal.sanitized()
    if (s.isIdentity) return
    val b = outline.getBounds()
    if (b.width <= 0f || b.height <= 0f) return
    val span = 0.5f * hypot(b.width, b.height)
    val c = b.center
    val dir = s.lightDirection()
    val litPt  = Offset(c.x + dir.x * span, c.y + dir.y * span)
    val darkPt = Offset(c.x - dir.x * span, c.y - dir.y * span)
    val base   = s.baseColor.toSpecColor() ?: Color.LightGray
    val light  = base.lighten(0.45f * s.reflectivity)
    val dark   = base.darken(0.35f * s.reflectivity)

    clipPath(outline) {
        if (s.anisotropy < 0.05f) {
            // Polished: smooth lit→dark→mid-tone bands (a natural metallic reflection gradient).
            drawRect(
                brush = Brush.linearGradient(
                    0f    to light.copy(alpha = 0.85f * s.reflectivity),
                    0.35f to dark.copy(alpha = 0.6f * s.reflectivity),
                    0.65f to light.copy(alpha = 0.5f * s.reflectivity),
                    1f    to dark.copy(alpha = 0.3f * s.reflectivity),
                    start = litPt,
                    end   = darkPt,
                ),
                blendMode = BlendMode.Overlay,
            )
            // Tight specular highlight.
            val specRadius = span * 0.12f * (1f - s.reflectivity * 0.5f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f   to light.copy(alpha = s.reflectivity),
                    0.5f to light.copy(alpha = s.reflectivity * 0.2f),
                    1f   to light.copy(alpha = 0f),
                    center = Offset(c.x + dir.x * span * 0.55f, c.y + dir.y * span * 0.55f),
                    radius = specRadius.coerceAtLeast(1f),
                ),
                radius = specRadius.coerceAtLeast(1f),
                center = Offset(c.x + dir.x * span * 0.55f, c.y + dir.y * span * 0.55f),
                blendMode = BlendMode.Screen,
            )
        } else {
            // Brushed: anisotropic fine-line gradient perpendicular to the brush direction, then soft spec.
            val brushDir  = s.brushDirection()
            // gradient direction is perpendicular to the brush direction
            val perpStart = Offset(c.x + brushDir.y * span, c.y - brushDir.x * span)
            val perpEnd   = Offset(c.x - brushDir.y * span, c.y + brushDir.x * span)
            val lineCount = 24
            val stops = buildList {
                for (i in 0 until lineCount) {
                    val t = i / lineCount.toFloat()
                    val isLight = i % 2 == 0
                    val bandColor = if (isLight) light else dark
                    add(t to bandColor.copy(alpha = s.anisotropy * s.reflectivity * 0.7f))
                }
                add(1f to dark.copy(alpha = s.anisotropy * s.reflectivity * 0.4f))
            }
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = stops.toTypedArray(),
                    start = perpStart,
                    end   = perpEnd,
                ),
                blendMode = BlendMode.Overlay,
            )
            // Softer wide specular for brushed.
            val specRadius = span * 0.3f
            drawCircle(
                brush = Brush.radialGradient(
                    0f   to light.copy(alpha = s.reflectivity * 0.5f),
                    1f   to light.copy(alpha = 0f),
                    center = Offset(c.x + dir.x * span * 0.4f, c.y + dir.y * span * 0.4f),
                    radius = specRadius.coerceAtLeast(1f),
                ),
                radius = specRadius.coerceAtLeast(1f),
                center = Offset(c.x + dir.x * span * 0.4f, c.y + dir.y * span * 0.4f),
                blendMode = BlendMode.Screen,
            )
        }
    }
}

/** Apply a metallic surface treatment over this composable's content, clipped to [shape]. */
fun Modifier.ogMetal(metal: OGMetalSpec, shape: Shape = RectangleShape): Modifier =
    this.drawWithCache {
        val outline = shapeToPath(shape, this)
        onDrawWithContent {
            drawContent()
            drawOGMetal(outline, metal)
        }
    }

// ── Helpers ──────────────────────────────────────────────────────────────────────────────────────────

private fun androidx.compose.ui.draw.CacheDrawScope.shapeToPath(
    shape: Shape,
    density: androidx.compose.ui.unit.Density,
): Path = when (val o = shape.createOutline(size, layoutDirection, density)) {
    is Outline.Generic   -> o.path
    is Outline.Rectangle -> Path().apply { addRect(o.rect) }
    is Outline.Rounded   -> Path().apply { addRoundRect(o.roundRect) }
}

/** HSL to Compose [Color]. Hue in degrees; saturation, lightness, alpha in `0..1`. */
internal fun colorFromHsl(hue: Float, saturation: Float, lightness: Float, alpha: Float = 1f): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f  -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else     -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m, alpha)
}

private fun Color.lighten(amount: Float): Color = copy(
    red   = (red   + (1f - red)   * amount).coerceIn(0f, 1f),
    green = (green + (1f - green) * amount).coerceIn(0f, 1f),
    blue  = (blue  + (1f - blue)  * amount).coerceIn(0f, 1f),
)

private fun Color.darken(amount: Float): Color = copy(
    red   = (red   * (1f - amount)).coerceIn(0f, 1f),
    green = (green * (1f - amount)).coerceIn(0f, 1f),
    blue  = (blue  * (1f - amount)).coerceIn(0f, 1f),
)
