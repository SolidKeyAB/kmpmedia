package com.solidkey.painpoints.light

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.solidkey.painpoints.ai.OGAiVector
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The **codec + presets + appliers** for the lighting pack — [OGLightSpec] (a directional form light) and
 * [OGShadowSpec] (a cast / contact shadow). Decode a JSON pack a model or designer authored, encode one
 * back, build a model prompt, reach for a ready-made [STUDIO] / [NOON] / [RIM] light or a [SOFT] / [DROP]
 * shadow — then drop the light & shadow onto *any* composable with [Modifier.ogLight] / [Modifier.ogShadow],
 * or onto an outline you already have inside a `DrawScope` with [DrawScope.drawOGLight] / [drawOGShadow].
 *
 * Mirrors the rest of the data layers (fog, looks, skin): lenient JSON, tolerant extraction, no network,
 * no AI SDK. Lighting and shadowing are the shared pass you lay over a whole COMBINATION — a skinned,
 * edged, fogged graphic — so it reads as one lit, grounded object.
 */
object OGLight {
    val json get() = OGAiVector.json

    // ── Light codec ────────────────────────────────────────────────────────────────────────────────
    fun decodeLight(text: String): OGLightSpec =
        json.decodeFromString(OGLightSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeLightOrNull(text: String): OGLightSpec? = runCatching { decodeLight(text) }.getOrNull()

    fun encode(spec: OGLightSpec): String = json.encodeToString(OGLightSpec.serializer(), spec)

    /** Look up a built-in light preset by name (case-insensitive), or `null`. */
    fun lightPreset(name: String): OGLightSpec? = lights[name.lowercase()]

    // ── Shadow codec ───────────────────────────────────────────────────────────────────────────────
    fun decodeShadow(text: String): OGShadowSpec =
        json.decodeFromString(OGShadowSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeShadowOrNull(text: String): OGShadowSpec? = runCatching { decodeShadow(text) }.getOrNull()

    fun encode(spec: OGShadowSpec): String = json.encodeToString(OGShadowSpec.serializer(), spec)

    /** Look up a built-in shadow preset by name (case-insensitive), or `null`. */
    fun shadowPreset(name: String): OGShadowSpec? = shadows[name.lowercase()]

    // ── Sheen codec ────────────────────────────────────────────────────────────────────────────────
    fun decodeSheen(text: String): OGSheenSpec =
        json.decodeFromString(OGSheenSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSheenOrNull(text: String): OGSheenSpec? = runCatching { decodeSheen(text) }.getOrNull()

    fun encode(spec: OGSheenSpec): String = json.encodeToString(OGSheenSpec.serializer(), spec)

    /** Look up a built-in sheen preset by name (case-insensitive), or `null`. */
    fun sheenPreset(name: String): OGSheenSpec? = sheens[name.lowercase()]

    // ── Spot codec ─────────────────────────────────────────────────────────────────────────────────
    fun decodeSpot(text: String): OGSpotSpec =
        json.decodeFromString(OGSpotSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpotOrNull(text: String): OGSpotSpec? = runCatching { decodeSpot(text) }.getOrNull()

    fun encode(spec: OGSpotSpec): String = json.encodeToString(OGSpotSpec.serializer(), spec)

    /** Look up a built-in spot preset by name (case-insensitive), or `null`. */
    fun spotPreset(name: String): OGSpotSpec? = spots[name.lowercase()]

    // ── Prompts ────────────────────────────────────────────────────────────────────────────────────
    fun lightPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a single directional LIGHT for the KMPMedia library.

        It shades a graphic so it reads 3-D: a coloured highlight on the lit side and a core shadow on the
        far side. angleDeg is a compass for the light (0 = from the right, -90 = from straight above,
        -120 ≈ upper-left). intensity and ambient are 0..1 (ambient lifts the shadow side; high = flat).

        Fields (all optional): name, angleDeg, color, shadowColor, intensity, ambient.

        Schema: {"name":"<short name>", ...fields...}
        Example: {"name":"key","angleDeg":-120,"color":"#FFF4E0","intensity":0.5,"ambient":0.3}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    fun shadowPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a cast SHADOW for the KMPMedia library.

        It draws a soft, offset drop shadow (optionally a tight contact shadow) behind a graphic so it sits
        grounded. angleDeg is the direction the shadow falls (0 = right, 90 = straight down). distance, blur
        and spread are fractions of the shorter side; opacity and contact are 0..1.

        Fields (all optional): name, angleDeg, distance, blur, spread, color, opacity, passes, contact.

        Schema: {"name":"<short name>", ...fields...}
        Example: {"name":"drop","angleDeg":100,"distance":0.06,"blur":0.04,"opacity":0.38,"contact":0.3}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    // ── Light presets ──────────────────────────────────────────────────────────────────────────────
    /** Soft neutral key from the upper-left — the safe default portrait light. */
    val STUDIO = OGLightSpec(name = "studio", angleDeg = -120f, color = "#FFFFFF", intensity = 0.45f, ambient = 0.4f)

    /** Bright top light with lots of fill — flat, high-key, barely any shadow (midday / product shot). */
    val NOON = OGLightSpec(name = "noon", angleDeg = -90f, color = "#FFFDF5", intensity = 0.4f, ambient = 0.6f)

    /** A strong back/rim highlight with a cool deep-blue shadow — punchy, backlit, pops off the background. */
    val RIM = OGLightSpec(name = "rim", angleDeg = 45f, color = "#FFFFFF", shadowColor = "#0B1026", intensity = 0.55f, ambient = 0.2f, rim = 0.75f)

    /** Hard side light, crushed shadows — moody, cinematic contrast. */
    val DRAMATIC = OGLightSpec(name = "dramatic", angleDeg = -155f, color = "#FFFFFF", intensity = 0.65f, ambient = 0.12f)

    /** Warm low sun / candle from the left with a cool shadow — golden-hour glow. */
    val WARM = OGLightSpec(name = "warm", angleDeg = -160f, color = "#FFB870", shadowColor = "#2E2140", intensity = 0.55f, ambient = 0.3f)

    /** Chiselled relief — a moderate key plus heavy ambient occlusion and a touch of rim, so edges and
     *  concave seams read deep. The "make a flat vector look carved" preset. */
    val SCULPT = OGLightSpec(name = "sculpt", angleDeg = -110f, color = "#FFF7EA", shadowColor = "#1A1030", intensity = 0.5f, ambient = 0.32f, rim = 0.25f, occlusion = 0.55f)

    /** All built-in light presets, keyed by lowercase name. */
    val lights: Map<String, OGLightSpec> = listOf(STUDIO, NOON, RIM, DRAMATIC, WARM, SCULPT).associateBy { it.name!! }

    // ── Shadow presets ─────────────────────────────────────────────────────────────────────────────
    /** A gentle, diffuse shadow close under the object — the safe default. */
    val SOFT = OGShadowSpec(name = "soft", angleDeg = 90f, distance = 0.03f, blur = 0.05f, opacity = 0.28f, passes = 5)

    /** A clear offset drop shadow down-and-right — the object reads as lifted off the surface. */
    val DROP = OGShadowSpec(name = "drop", angleDeg = 115f, distance = 0.06f, blur = 0.035f, opacity = 0.4f, passes = 4)

    /** A long, faint, far-thrown shadow — low sun, object standing tall. */
    val LONG = OGShadowSpec(name = "long", angleDeg = 110f, distance = 0.12f, blur = 0.06f, spread = 0.1f, opacity = 0.22f, passes = 5)

    /** A tight, dark contact shadow hugging the base — seats the object firmly on the ground. */
    val CONTACT = OGShadowSpec(name = "contact", angleDeg = 90f, distance = 0.012f, blur = 0.02f, opacity = 0.45f, passes = 3, contact = 0.5f)

    /** A wide, soft, far shadow — the object floats high above the surface. */
    val FLOATING = OGShadowSpec(name = "floating", angleDeg = 90f, distance = 0.08f, blur = 0.09f, spread = 0.15f, opacity = 0.2f, passes = 6)

    /** All built-in shadow presets, keyed by lowercase name. */
    val shadows: Map<String, OGShadowSpec> = listOf(SOFT, DROP, LONG, CONTACT, FLOATING).associateBy { it.name!! }

    // ── Sheen presets ──────────────────────────────────────────────────────────────────────────────
    /** A quick, crisp glint sweeping across every few seconds — the premium-tile shine. */
    val GLINT = OGSheenSpec(name = "glint", angleDeg = -22f, color = "#FFFFFF", intensity = 0.6f, width = 0.14f, periodMs = 2400f, dutyCycle = 0.28f)

    /** A slow, wide, soft shimmer — a gentler sheen for large surfaces / cards. */
    val SHIMMER = OGSheenSpec(name = "shimmer", angleDeg = -18f, color = "#FFFFFF", intensity = 0.4f, width = 0.3f, periodMs = 4200f, dutyCycle = 0.5f)

    /** All built-in sheen presets, keyed by lowercase name. */
    val sheens: Map<String, OGSheenSpec> = listOf(GLINT, SHIMMER).associateBy { it.name!! }

    // ── Spot presets ───────────────────────────────────────────────────────────────────────────────
    /** A warm pool of attention, high-ish on the frame, dimming the surround — the "hold on the hero" look. */
    val POOL = OGSpotSpec(name = "pool", x = 0.5f, y = 0.42f, radius = 0.6f, softness = 0.5f, dim = 0.5f, lightColor = "#FFE8B0", lightIntensity = 0.12f)

    /** A plain edge vignette — darken the corners, no tint, keep the centre clear. */
    val VIGNETTE = OGSpotSpec(name = "vignette", radius = 0.75f, softness = 0.45f, dim = 0.4f)

    /** A dark, cool, dramatic pool — stormy stagecraft with a faint blue key inside. */
    val STORM = OGSpotSpec(name = "storm", x = 0.5f, y = 0.4f, radius = 0.55f, softness = 0.4f, dimColor = "#060A18", dim = 0.6f, lightColor = "#AFC7FF", lightIntensity = 0.1f)

    /** All built-in spot presets, keyed by lowercase name. */
    val spots: Map<String, OGSpotSpec> = listOf(POOL, VIGNETTE, STORM).associateBy { it.name!! }
}

// ── Appliers: Modifiers ───────────────────────────────────────────────────────────────────────────────

/** Lay [light]'s directional form-shade over this composable's content, clipped to [shape]. Drawn on top
 *  of the content (it shades what's underneath), so it works on any graphic — a skinned shape, an SVG, a
 *  lasso-cut photo, text. Default [shape] is the full rectangular bounds. */
fun Modifier.ogLight(light: OGLightSpec, shape: Shape = RectangleShape): Modifier =
    this.drawWithCache {
        val outline = shapeToPath(shape, this)
        onDrawWithContent {
            drawContent()
            drawOGLight(outline, light)
        }
    }

/** Cast [shadow] BEHIND this composable's content (the silhouette is [shape]), so the graphic lifts off
 *  its background. The shadow is offset + soft, so leave a little padding around the composable or the
 *  offset copies clip at its bounds. Default [shape] is the full rectangular bounds. */
fun Modifier.ogShadow(shadow: OGShadowSpec, shape: Shape = RectangleShape): Modifier =
    this.drawWithCache {
        val outline = shapeToPath(shape, this)
        onDrawWithContent {
            drawOGShadow(outline, shadow)
            drawContent()
        }
    }

private fun androidx.compose.ui.draw.CacheDrawScope.shapeToPath(shape: Shape, density: androidx.compose.ui.unit.Density): Path =
    when (val o = shape.createOutline(size, layoutDirection, density)) {
        is Outline.Generic -> o.path
        is Outline.Rectangle -> Path().apply { addRect(o.rect) }
        is Outline.Rounded -> Path().apply { addRoundRect(o.roundRect) }
    }

// ── Appliers: DrawScope ───────────────────────────────────────────────────────────────────────────────

/**
 * Draw [light]'s directional form-shade over [outline] (a closed silhouette in this scope's pixel space).
 * Call it AFTER you've filled the shape — the highlight is screened onto the lit side and the core shadow
 * multiplied onto the far side, both clipped to the outline and fading out through the body, so the fill
 * underneath becomes a lit, rounded form. A neutral/zero light draws nothing.
 */
fun DrawScope.drawOGLight(outline: Path, light: OGLightSpec) {
    val s = light.sanitized()
    val litA = s.litAlpha()
    val shadeA = s.shadeAlpha()
    if (litA <= 0f && shadeA <= 0f && s.rim <= 0f && s.occlusion <= 0f) return
    val b = outline.getBounds()
    if (b.width <= 0f || b.height <= 0f) return
    val short = min(b.width, b.height)
    val litColor = s.color.toSpecColor() ?: Color.White
    val shadeColor = s.shadowColor?.toSpecColor() ?: Color.Black
    val c = b.center
    val span = 0.5f * hypot(b.width, b.height)
    val dir = s.direction()
    val litPt = Offset(c.x + dir.x * span, c.y + dir.y * span)   // lit extreme
    val darkPt = Offset(c.x - dir.x * span, c.y - dir.y * span)  // shadow extreme
    clipPath(outline) {
        // Core shadow on the far side: a translucent dark gradient multiplied onto the fill (darkens).
        if (shadeA > 0f) {
            drawRect(
                brush = Brush.linearGradient(
                    0f to shadeColor.copy(alpha = shadeA),
                    0.55f to shadeColor.copy(alpha = 0f),
                    1f to shadeColor.copy(alpha = 0f),
                    start = darkPt,
                    end = litPt,
                ),
                blendMode = BlendMode.Multiply,
            )
        }
        // Highlight on the lit side: a translucent light gradient screened onto the fill (brightens).
        if (litA > 0f) {
            drawRect(
                brush = Brush.linearGradient(
                    0f to litColor.copy(alpha = litA),
                    0.55f to litColor.copy(alpha = 0f),
                    1f to litColor.copy(alpha = 0f),
                    start = litPt,
                    end = darkPt,
                ),
                blendMode = BlendMode.Screen,
            )
        }
        // Ambient occlusion: a dark band hugging the INSIDE edge all around (an inner shadow) — the
        // outline stroked and clipped to the shape, so only its inner half shows, multiplied to darken.
        if (s.occlusion > 0f) {
            drawPath(
                outline,
                color = shadeColor.copy(alpha = (s.occlusion * 0.8f).coerceIn(0f, 1f)),
                style = Stroke(width = short * 0.14f),
                blendMode = BlendMode.Multiply,
            )
        }
        // Rim / back light: a bright inner band on the LIT edge — the outline stroked with a light
        // gradient (bright at the lit extreme, gone by the middle) and clipped to the shape, screened on.
        if (s.rim > 0f) {
            drawPath(
                outline,
                brush = Brush.linearGradient(
                    0f to litColor.copy(alpha = s.rim.coerceIn(0f, 1f)),
                    0.5f to litColor.copy(alpha = 0f),
                    1f to litColor.copy(alpha = 0f),
                    start = litPt,
                    end = darkPt,
                ),
                style = Stroke(width = short * 0.05f),
                blendMode = BlendMode.Screen,
            )
        }
    }
}

/**
 * Draw [shadow] for [outline] (a closed silhouette in this scope's pixel space). Call it BEFORE drawing
 * the content so the shadow sits behind it. A few translucent, outward-scaled copies of the silhouette
 * are offset along the cast direction (a shader-free soft penumbra); an optional [OGShadowSpec.contact]
 * copy hugs the base. A neutral/zero shadow draws nothing. The soft copies bulge outside the outline.
 */
fun DrawScope.drawOGShadow(outline: Path, shadow: OGShadowSpec) {
    val s = shadow.sanitized()
    val passes = s.dropPasses()
    if (passes.isEmpty()) return
    val b = outline.getBounds()
    if (b.width <= 0f || b.height <= 0f) return
    val tint = s.color.toSpecColor() ?: Color.Black
    val short = min(size.width, size.height)
    val dir = s.direction()
    val ox = dir.x * s.distance * short
    val oy = dir.y * s.distance * short
    val pivot = b.center
    // Outer (faint, wide) first so the tighter, darker inner copies layer on top — a soft-to-crisp penumbra.
    for (i in passes.indices.reversed()) {
        val (growth, alpha) = passes[i]
        val sc = 1f + growth
        translate(ox, oy) {
            scale(sc, sc, pivot) {
                drawPath(outline, color = tint.copy(alpha = alpha))
            }
        }
    }
    // Tight contact shadow: a near-zero-offset solid copy that seats the object on the surface.
    if (s.contact > 0f) {
        val ca = (s.opacity * s.contact).coerceIn(0f, 1f)
        translate(ox * 0.2f, oy * 0.2f) {
            drawPath(outline, color = tint.copy(alpha = ca))
        }
    }
}

// ── Sheen (animated specular sweep) ─────────────────────────────────────────────────────────────────

/**
 * Draw [sheen]'s glossy bar over [outline] at [timeMs] (ms since the effect started). A narrow light
 * gradient is screened over the shape, its position advanced by time so it sweeps across then rests. Does
 * nothing while the sheen is resting (between sweeps) or at zero intensity. Pair with [Modifier.ogSheen]
 * for a self-animating version, or call this with your own clock (e.g. a stage's timeline).
 */
fun DrawScope.drawOGSheen(outline: Path, sheen: OGSheenSpec, timeMs: Float) {
    val s = sheen.sanitized()
    val progress = s.progressAt(timeMs) ?: return
    if (s.intensity <= 0f) return
    val b = outline.getBounds()
    if (b.width <= 0f || b.height <= 0f) return
    val tint = s.color.toSpecColor() ?: Color.White
    val c = b.center
    val dir = s.direction()
    val half = 0.5f * hypot(b.width, b.height)
    val travel = (progress * 2f - 1f) * half * 1.3f           // sweep the bar centre across (with overshoot)
    val pos = Offset(c.x + dir.x * travel, c.y + dir.y * travel)
    val w = (s.width * half).coerceAtLeast(1f)
    clipPath(outline) {
        drawRect(
            brush = Brush.linearGradient(
                0f to tint.copy(alpha = 0f),
                0.5f to tint.copy(alpha = s.intensity),
                1f to tint.copy(alpha = 0f),
                start = Offset(pos.x - dir.x * w, pos.y - dir.y * w),
                end = Offset(pos.x + dir.x * w, pos.y + dir.y * w),
            ),
            blendMode = BlendMode.Screen,
        )
    }
}

/** A self-animating [sheen] over this composable, clipped to [shape] — a glint that sweeps across every
 *  few seconds on its own frame clock. Drop it on a tile, card or glyph; the per-tile phase is offset by
 *  [OGSheenSpec.seed] so a whole rack doesn't flash in lockstep. */
fun Modifier.ogSheen(sheen: OGSheenSpec, shape: Shape = RectangleShape): Modifier = composed {
    var timeMs by remember { mutableStateOf(0f) }
    LaunchedEffect(sheen) {
        var start = 0L
        while (true) {
            withFrameMillis { ms ->
                if (start == 0L) start = ms
                timeMs = (ms - start).toFloat()
            }
        }
    }
    drawWithCache {
        val outline = shapeToPath(shape, this)
        onDrawWithContent {
            drawContent()
            drawOGSheen(outline, sheen, timeMs)
        }
    }
}

// ── Spot (scene spotlight / vignette) ───────────────────────────────────────────────────────────────

/**
 * Draw [spot] over the whole current draw area — a radial pool of attention: the surround dims toward
 * [OGSpotSpec.dimColor] while the pool centre stays clear (optionally warmed by [OGSpotSpec.lightColor]).
 * Call it AFTER the scene content (it's an overlay). One or two radial fills; nothing per-shape.
 */
fun DrawScope.drawOGSpot(spot: OGSpotSpec) {
    val s = spot.sanitized()
    if (s.isIdentity) return
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val center = Offset(s.x * w, s.y * h)
    val r = (s.radius * max(w, h)).coerceAtLeast(1f)
    if (s.dim > 0f) {
        val dimColor = s.dimColor.toSpecColor() ?: Color.Black
        drawRect(
            brush = Brush.radialGradient(
                0f to dimColor.copy(alpha = 0f),
                s.softness.coerceIn(0f, 0.999f) to dimColor.copy(alpha = 0f),
                1f to dimColor.copy(alpha = s.dim),
                center = center,
                radius = r,
            ),
        )
    }
    val lc = s.lightColor?.toSpecColor()
    if (lc != null && s.lightIntensity > 0f) {
        drawRect(
            brush = Brush.radialGradient(
                0f to lc.copy(alpha = s.lightIntensity),
                1f to lc.copy(alpha = 0f),
                center = center,
                radius = r,
            ),
            blendMode = BlendMode.Screen,
        )
    }
}

/** Lay [spot]'s spotlight / vignette over this composable (drawn on top of its content). Put it on the
 *  container that holds the whole combination — a board, a scene, a card stack — to pull focus to one pool. */
fun Modifier.ogSpot(spot: OGSpotSpec): Modifier = this.drawWithContent {
    drawContent()
    drawOGSpot(spot)
}
