package com.solidkey.painpoints.light

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * A **data-defined directional light** — a single light you dial in (direction, colour, strength, fill)
 * and apply over *any* graphic so a flat vector form reads as a lit, dimensional object. It's the shared
 * "lighting pass" that sits over a whole COMBINATION (a skinned creature, an SVG, a photo cut-out, a
 * composed scene), the twin of [OGShadowSpec] (the cast shadow) and the sibling of the colour-grade
 * [OGLookSpec].
 *
 * **How it draws (the perf gate).** No shader, no per-pixel light model: the light is two directional
 * **gradients** overlaid on the shape — a [color] highlight **screened** onto the lit side and a
 * [shadowColor] core-shadow **multiplied** onto the far side, both fading out through the body. That's
 * two GPU gradient fills clipped to the silhouette — 60fps, zero per-frame allocation — and because the
 * geometry is pure maths it's visually consistent on Android & iOS. [angleDeg] follows the same compass
 * as [com.solidkey.painpoints.skin.OGSkinEdgeSpec] so an edge rim and the form shade agree on where the
 * light is.
 *
 * Decode + presets + the `Modifier.ogLight` / `DrawScope.drawOGLight` appliers live on [OGLight].
 */
@Serializable
data class OGLightSpec(
    val name: String? = null,
    /** Light direction in degrees (`0` = from the right, `-90` = from straight above, `-120` ≈ upper-left).
     *  The lit highlight falls on this side; the far side drops into the core shadow — that's what reads 3-D. */
    val angleDeg: Float = -120f,
    /** The light's colour — any SVG colour string. White is neutral key light; warm it for sun/candle. */
    val color: String = "#FFFFFF",
    /** The colour the unlit side falls toward; `null` = black (a natural form shadow). Set it for a cool
     *  bounce / coloured ambient (e.g. a dim blue sky fill on the shadow side). */
    val shadowColor: String? = null,
    /** Overall strength of the lighting `0..1`: the peak alpha of the highlight, and (scaled by [ambient])
     *  of the core shadow. `0` = a pure pass-through (no light drawn), `1` = a hard, high-key look. */
    val intensity: Float = 0.5f,
    /** Fill light `0..1`: how much ambient lifts the shadow side. `0` = dramatic, crushed shadows;
     *  `1` = flat, shadow-free fill. Only softens the dark side — the highlight is unaffected. */
    val ambient: Float = 0.35f,
    /** Rim / back light `0..1`: a bright thin band hugging the INSIDE of the lit edge (in [color]), as if a
     *  light behind the object catches its silhouette — the classic "pop off the background" 3-D cue. `0` = off. */
    val rim: Float = 0f,
    /** Ambient occlusion `0..1`: a soft dark band hugging the INSIDE edge all around (in [shadowColor]/black),
     *  so the form seats into itself and concave seams read deep — an inner shadow. `0` = off. */
    val occlusion: Float = 0f,
) {
    /** Clamp every knob into its sane range (lenient model / designer input never breaks a frame). */
    fun sanitized(): OGLightSpec = copy(
        intensity = intensity.coerceIn(0f, 1f),
        ambient = ambient.coerceIn(0f, 1f),
        rim = rim.coerceIn(0f, 1f),
        occlusion = occlusion.coerceIn(0f, 1f),
    )

    /** Unit light vector `(cos, sin)` from [angleDeg] — points from the shape toward the light. */
    fun direction(): Offset {
        val rad = angleDeg.toDouble() * PI / 180.0
        return Offset(cos(rad).toFloat(), sin(rad).toFloat())
    }

    /** Peak alpha of the highlight on the lit side (`intensity`, clamped). */
    fun litAlpha(): Float = intensity.coerceIn(0f, 1f)

    /** Peak alpha of the core shadow on the far side — `intensity`, lifted by [ambient] (full ambient ⇒ 0). */
    fun shadeAlpha(): Float = (intensity * (1f - ambient)).coerceIn(0f, 1f)

    /** True when this light changes nothing (no form, rim or occlusion) — a pure pass-through. */
    val isIdentity: Boolean get() = intensity <= 0f && rim <= 0f && occlusion <= 0f

    companion object {
        /** The neutral light: draws nothing. */
        val None = OGLightSpec(intensity = 0f)
    }
}

/**
 * A **data-defined shadow** — a soft, offset drop shadow (plus an optional tight contact shadow) cast by
 * any graphic, so the COMBINATION lifts off its background and sits grounded instead of floating. The
 * twin of [OGLightSpec]; apply the two together for a fully lit, grounded form.
 *
 * **How it draws (the perf gate).** The library floors runtime blur shaders out, so softness is faked the
 * same cheap, cross-platform way [com.solidkey.painpoints.ai.OGGlowSpec] fakes its halo: a few translucent,
 * outward-scaled copies of the silhouette, drawn **behind** the content and **offset** along the light.
 * Overlapping them builds a soft penumbra — a handful of GPU path fills, 60fps, no shader. All distances
 * are fractions of the shorter side, so a shadow scales with the graphic and looks the same at any size.
 */
@Serializable
data class OGShadowSpec(
    val name: String? = null,
    /** Direction the shadow is cast, in degrees (`0` = to the right, `90` = straight down). Usually the
     *  opposite side from where an [OGLightSpec] sits. */
    val angleDeg: Float = 90f,
    /** How far the shadow is offset from the shape, as a fraction of the shorter side. Bigger = the object
     *  reads as higher off the ground. */
    val distance: Float = 0.04f,
    /** Softness as a fraction of the shorter side — realized as stacked, growing copies (a fake blur, no
     *  shader). `0` = a hard offset silhouette; bigger = a wide, diffuse penumbra. */
    val blur: Float = 0.03f,
    /** Extra silhouette growth `0..1` on top of [blur] — a chunkier, spread shadow (like a CSS spread). */
    val spread: Float = 0f,
    /** Shadow colour — any SVG colour string. Black is neutral; tint it for a coloured/coloured-light cast. */
    val color: String = "#000000",
    /** Peak shadow opacity `0..1`. */
    val opacity: Float = 0.35f,
    /** Number of stacked copies for the soft penumbra (more = smoother, costlier); clamped `1..8`. */
    val passes: Int = 4,
    /** Strength `0..1` of an extra TIGHT contact shadow hugging the shape (a near-zero offset, full-opacity
     *  copy) that visually seats the object on the surface. `0` = none. */
    val contact: Float = 0f,
) {
    /** Clamp every knob into its sane range. */
    fun sanitized(): OGShadowSpec = copy(
        distance = distance.coerceAtLeast(0f),
        blur = blur.coerceAtLeast(0f),
        spread = spread.coerceIn(0f, 1f),
        opacity = opacity.coerceIn(0f, 1f),
        passes = passes.coerceIn(1, 8),
        contact = contact.coerceIn(0f, 1f),
    )

    /** Unit cast vector `(cos, sin)` from [angleDeg] — the direction the shadow falls. */
    fun direction(): Offset {
        val rad = angleDeg.toDouble() * PI / 180.0
        return Offset(cos(rad).toFloat(), sin(rad).toFloat())
    }

    /** The soft-drop passes as `(scaleGrowth, alpha)` pairs: a wide faint outer copy down to a tight bright
     *  one. [scaleGrowth] is the *extra* scale (0 = original size) so the caller scales by `1 + growth`.
     *  Empty when there's nothing to draw (`opacity <= 0`). Pure maths → unit-testable + identical per target. */
    fun dropPasses(): List<Pair<Float, Float>> {
        val op = opacity.coerceIn(0f, 1f)
        if (op <= 0f) return emptyList()
        val p = passes.coerceIn(1, 8)
        val grow = blur.coerceAtLeast(0f) + spread.coerceIn(0f, 1f)
        return (0 until p).map { k ->
            val t = if (p == 1) 0f else k / (p - 1f)   // 0 = inner/bright/tight, 1 = outer/faint/wide
            val growth = grow * t
            val alpha = (op * (1f - 0.6f * t)).coerceIn(0f, 1f)
            growth to alpha
        }
    }

    /** True when this shadow draws nothing. */
    val isIdentity: Boolean get() = opacity <= 0f

    companion object {
        /** The neutral shadow: draws nothing. */
        val None = OGShadowSpec(opacity = 0f)
    }
}

/**
 * An animated **sheen / specular sweep** — a bright bar of light that slides across a graphic every few
 * seconds then rests, the canonical "premium shine" juice on a tile, card or glyph. One narrow gradient
 * screened over the shape, its position advanced by time; nothing per-frame but a moved gradient, so it's
 * cheap on any number of tiles. Deterministic from (spec, time) → identical on Android & iOS; the phase is
 * offset by [seed] so a field of tiles doesn't glint in lockstep.
 */
@Serializable
data class OGSheenSpec(
    val name: String? = null,
    /** Orientation of the sweeping bar AND its travel direction, in degrees (`0` = the bar sweeps left→right). */
    val angleDeg: Float = -22f,
    /** Sheen colour — any SVG colour string. White is a neutral glossy glint. */
    val color: String = "#FFFFFF",
    /** Peak opacity of the bar `0..1`. */
    val intensity: Float = 0.55f,
    /** Bar width as a fraction of the sweep length `0.02..1` (thin = a sharp glint, wide = a soft wash). */
    val width: Float = 0.16f,
    /** One full cycle (sweep + rest) in milliseconds. */
    val periodMs: Float = 2600f,
    /** Fraction of the period the bar is actually sweeping `0.05..1`; the rest of the period it's hidden. */
    val dutyCycle: Float = 0.35f,
    /** Phase offset seed so many tiles glint out of sync (same seed ⇒ same phase on every platform). */
    val seed: Int = 0,
) {
    /** Clamp every knob into its sane range. */
    fun sanitized(): OGSheenSpec = copy(
        intensity = intensity.coerceIn(0f, 1f),
        width = width.coerceIn(0.02f, 1f),
        periodMs = periodMs.coerceAtLeast(1f),
        dutyCycle = dutyCycle.coerceIn(0.05f, 1f),
    )

    /** Unit sweep vector `(cos, sin)` from [angleDeg] — the direction the bar travels. */
    fun direction(): Offset {
        val rad = angleDeg.toDouble() * PI / 180.0
        return Offset(cos(rad).toFloat(), sin(rad).toFloat())
    }

    /** Sweep progress `0..1` at [timeMs] (0 = bar entering one side, 1 = leaving the other), or `null` while
     *  resting (nothing to draw). Pure + deterministic, so the glint is identical on every platform. */
    fun progressAt(timeMs: Float): Float? {
        val per = periodMs.coerceAtLeast(1f)
        val duty = dutyCycle.coerceIn(0.05f, 1f)
        val seedPhase = frac01(seed * 0.618_034f)
        val phase = frac01(timeMs / per + seedPhase)
        if (phase > duty) return null
        return phase / duty
    }

    val isIdentity: Boolean get() = intensity <= 0f
}

/**
 * A **spotlight / vignette** that lights the whole COMBINATION rather than one shape — a radial pool of
 * attention: the surround falls to [dimColor] while the pool centre stays clear (and can be warmed by an
 * optional [lightColor]). One radial-gradient fill over the frame, so it's a single draw at scene scale —
 * the stagecraft the per-shape effects can't give. Centre/radius are normalized to the draw area, so one
 * spec fits any size, and a compositor can keyframe them for a travelling light.
 */
@Serializable
data class OGSpotSpec(
    val name: String? = null,
    /** Pool centre, normalized `0..1` of the draw area (`0.5,0.5` = middle). */
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    /** Pool radius as a fraction of the larger side. */
    val radius: Float = 0.62f,
    /** Where the fall-off starts, as a fraction of [radius] `0..1` (`0` = hard edge, `1` = fades from the centre). */
    val softness: Float = 0.55f,
    /** Surround colour the frame dims toward — any SVG colour string. */
    val dimColor: String = "#000000",
    /** How dark the surround gets `0..1` (`0` = no vignette). */
    val dim: Float = 0.5f,
    /** Optional warm tint screened INSIDE the pool; `null`/`0` intensity = a plain dim-only vignette. */
    val lightColor: String? = null,
    /** Strength `0..1` of the inside tint. */
    val lightIntensity: Float = 0f,
) {
    /** Clamp every knob into its sane range. */
    fun sanitized(): OGSpotSpec = copy(
        radius = radius.coerceAtLeast(1e-3f),
        softness = softness.coerceIn(0f, 1f),
        dim = dim.coerceIn(0f, 1f),
        lightIntensity = lightIntensity.coerceIn(0f, 1f),
    )

    val isIdentity: Boolean get() = dim <= 0f && lightIntensity <= 0f
}

/** Fractional part in `[0,1)`, wrapping negatives too. */
private fun frac01(v: Float): Float = v - floor(v)
