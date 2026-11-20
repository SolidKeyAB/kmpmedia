package com.solidkey.painpoints.material

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── Surface finish / specular highlight ─────────────────────────────────────────────────────────────

/**
 * Surface finish of an object — how smooth, shiny, or mirror-like its surface is. Applied as a
 * specular highlight (a bright radial spot on the lit side), sized and sharpened to match the finish.
 * No shader: a single radial gradient in Screen blend mode clipped to the shape silhouette.
 *
 * Pairs with [com.solidkey.painpoints.light.OGLightSpec]: [lightAngleDeg] should match the light's
 * `angleDeg` so the highlight sits exactly where the light hits.
 */
@Serializable
data class OGSurfaceSpec(
    val name: String? = null,
    /** Surface finish — drives the default specular size and intensity. */
    val finish: OGFinish = OGFinish.SATIN,
    /** Colour of the specular highlight — white for dielectric (plastic, ceramic), tinted for metal. */
    val specularColor: String = "#FFFFFF",
    /** Peak brightness of the specular spot `0..1`. */
    val specularIntensity: Float = -1f,  // -1 = derive from finish
    /** Radius of the specular spot as fraction of shape span `0.01..1`. */
    val specularSize: Float = -1f,       // -1 = derive from finish
    /** Light direction in degrees — aligns the highlight with the key light. */
    val lightAngleDeg: Float = -120f,
) {
    fun sanitized(): OGSurfaceSpec {
        val (defaultIntensity, defaultSize) = finish.defaults()
        return copy(
            specularIntensity = (if (specularIntensity < 0f) defaultIntensity else specularIntensity).coerceIn(0f, 1f),
            specularSize = (if (specularSize < 0f) defaultSize else specularSize).coerceIn(0.01f, 1f),
        )
    }

    fun lightDirection() = directionOf(lightAngleDeg)

    val isIdentity: Boolean get() = finish == OGFinish.MATTE && specularIntensity == 0f

    companion object {
        val None = OGSurfaceSpec(finish = OGFinish.MATTE, specularIntensity = 0f)
    }
}

/** Surface finish quality — drives the default specular size and peak intensity. */
@Serializable
enum class OGFinish {
    /** No specular highlight — clay, chalk, unfinished stone. */
    MATTE,
    /** Soft, wide specular — painted wood, skin, rubber. */
    SATIN,
    /** Sharp, bright specular — lacquer, polished plastic, ceramic. */
    GLOSS,
    /** Tiny mirror-sharp spot — chrome, polished metal, glass ball. */
    MIRROR;

    /** `(intensity, size)` defaults for this finish level. */
    fun defaults(): Pair<Float, Float> = when (this) {
        MATTE   -> 0.0f to 0.5f
        SATIN   -> 0.3f to 0.45f
        GLOSS   -> 0.65f to 0.22f
        MIRROR  -> 0.9f to 0.08f
    }
}

// ── Iridescence / thin-film color shift ─────────────────────────────────────────────────────────────

/**
 * Thin-film iridescence — a rainbow color shift that sweeps across a surface, the visual signature of
 * soap bubbles, beetle shells, holographic foil, oil slicks, and mother-of-pearl. Rendered as a
 * multi-stop hue-cycling linear gradient in Overlay blend mode, so it tints the existing surface
 * colour without washing it out. No shader; pure Canvas gradient.
 */
@Serializable
data class OGIridescenceSpec(
    val name: String? = null,
    /** Hue range in degrees — how much of the colour wheel the gradient spans. `60` = a gentle two-colour
     *  shift; `360` = a full rainbow. */
    val hueRange: Float = 90f,
    /** Starting hue in degrees `0..360`. */
    val baseHue: Float = 180f,
    /** Direction the gradient travels across the shape, in degrees. */
    val sweepAngleDeg: Float = -45f,
    /** Overall opacity `0..1` — how vivid the iridescence reads over the base color. */
    val intensity: Float = 0.45f,
    /** Color saturation of the rainbow stops `0..1`. */
    val saturation: Float = 0.75f,
    /** Lightness of the rainbow stops `0..1`. */
    val lightness: Float = 0.65f,
) {
    fun sanitized(): OGIridescenceSpec = copy(
        hueRange = hueRange.coerceIn(10f, 360f),
        baseHue = ((baseHue % 360f) + 360f) % 360f,
        intensity = intensity.coerceIn(0f, 1f),
        saturation = saturation.coerceIn(0f, 1f),
        lightness = lightness.coerceIn(0.1f, 0.9f),
    )

    fun sweepDirection() = directionOf(sweepAngleDeg)

    val isIdentity: Boolean get() = intensity <= 0f
}

// ── Translucency / subsurface scatter ───────────────────────────────────────────────────────────────

/**
 * Subsurface translucency — light that enters a surface, scatters under the skin, and exits softly
 * at the edges. The hallmark of jade, wax, candle flames, skin, and frosted glass. Rendered as two
 * edge-gradient passes: an inner-glow from [scatterColor] fading toward the centre, and an optional
 * backlit rim on the opposite side from [rimAngleDeg]. No shader; gradient fills clipped to the outline.
 */
@Serializable
data class OGTranslucencySpec(
    val name: String? = null,
    /** The colour light scatters to at the edges — usually lighter and warmer than the base. */
    val scatterColor: String = "#FFFFFF",
    /** How deep the scatter band reaches, as a fraction of the shorter side `0..1`. */
    val depth: Float = 0.3f,
    /** Peak alpha of the scatter glow `0..1`. */
    val scatterIntensity: Float = 0.4f,
    /** Strength of the backlit rim `0..1` (`0` = no rim, just edge-scatter). */
    val rimStrength: Float = 0.35f,
    /** Direction the rim backlight comes from (degrees) — set it opposite the key light. */
    val rimAngleDeg: Float = 60f,
) {
    fun sanitized(): OGTranslucencySpec = copy(
        depth = depth.coerceIn(0f, 1f),
        scatterIntensity = scatterIntensity.coerceIn(0f, 1f),
        rimStrength = rimStrength.coerceIn(0f, 1f),
    )

    fun rimDirection() = directionOf(rimAngleDeg)

    val isIdentity: Boolean get() = scatterIntensity <= 0f && rimStrength <= 0f
}

// ── Metal / reflective surface ───────────────────────────────────────────────────────────────────────

/**
 * Metallic surface — the distinctive look of a material that has no diffuse component at the extreme,
 * reflecting its environment in its own base colour (gold, copper, steel, chrome). Two visual modes:
 * **polished** (a smooth, vivid gradient and bright specular) and **brushed** (fine anisotropic lines
 * running along [brushAngleDeg] plus a softer specular). Both rendered entirely with Canvas fills;
 * no shader, no image asset.
 */
@Serializable
data class OGMetalSpec(
    val name: String? = null,
    /** Base metal colour — any SVG colour string. White/silver for steel; warm gold for gold; copper for copper. */
    val baseColor: String = "#C0C0C0",
    /** How reflective `0..1`: `0` = dull / darkened metal, `1` = mirror-chrome. */
    val reflectivity: Float = 0.7f,
    /** Anisotropy `0..1`: `0` = polished / isotropic, `1` = fully brushed (fine parallel lines). */
    val anisotropy: Float = 0f,
    /** Direction of brush strokes in degrees (only relevant when [anisotropy] > 0). */
    val brushAngleDeg: Float = 0f,
    /** Direction the main reflection/specular falls, in degrees — aligns with the key light. */
    val lightAngleDeg: Float = -120f,
) {
    fun sanitized(): OGMetalSpec = copy(
        reflectivity = reflectivity.coerceIn(0f, 1f),
        anisotropy = anisotropy.coerceIn(0f, 1f),
    )

    fun lightDirection() = directionOf(lightAngleDeg)
    fun brushDirection() = directionOf(brushAngleDeg)

    val isIdentity: Boolean get() = reflectivity <= 0f

    companion object {
        val None = OGMetalSpec(reflectivity = 0f)
    }
}

// ── shared util ─────────────────────────────────────────────────────────────────────────────────────

internal fun directionOf(angleDeg: Float): androidx.compose.ui.geometry.Offset {
    val rad = angleDeg.toDouble() * PI / 180.0
    return androidx.compose.ui.geometry.Offset(cos(rad).toFloat(), sin(rad).toFloat())
}
