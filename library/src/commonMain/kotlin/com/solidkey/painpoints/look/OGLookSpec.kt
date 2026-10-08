package com.solidkey.painpoints.look

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A **data-defined colour look** (a "grade"): a few perceptual knobs that compile **once** into a
 * single [ColorFilter] (a 4×5 [ColorMatrix]) applied to any graphic every frame. Like [OGStyleSpec]
 * for outlines or `OGAiVector` for shapes, a look is just JSON — a designer or a language model can
 * author, tweak and share a `.look` pack with no code and no rebuild.
 *
 * All knobs are a single linear colour transform, so the whole look is one GPU colour op: **zero
 * per-frame allocation** (the matrix/filter are cached per instance), 60fps, and **pixel-identical on
 * Android & iOS** (pure maths, no platform blur or shader). `0` / `1` on each field is "unchanged",
 * so the default [OGLookSpec] is a no-op pass-through.
 *
 * Knobs (component maths in 0..255 space, the [ColorMatrix] convention):
 * - [brightness] `-1..1` — additive exposure (`±255` offset on R/G/B). `0` = unchanged.
 * - [contrast] `0..2` — scale around mid-grey. `1` = unchanged, `0` = flat grey, `>1` = punchier.
 * - [saturation] `0..2` — `1` = unchanged, `0` = greyscale (luminance), `>1` = richer colour.
 * - [temperature] `-1..1` — white balance: `+` warms (boost red / drop blue), `-` cools.
 * - [tint] `-1..1` — green↔magenta axis: `+` magenta (drop green), `-` green.
 * - [hue] degrees `-180..180` — luminance-preserving hue rotation.
 *
 * True **vibrance** (saturation weighted by how saturated a pixel already is) is intentionally **not**
 * here: it is per-pixel non-linear and cannot be expressed as one colour matrix, so including it would
 * break the single-op / frame-identical guarantee. Use [saturation] for the linear version.
 */
@Serializable
data class OGLookSpec(
    val name: String = "",
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val hue: Float = 0f,
) {
    /** The compiled 4×5 colour matrix (20 floats, row-major). Built once per instance, then cached. */
    val colorMatrix: ColorMatrix by lazy { ColorMatrix(buildColorMatrixArray(this)) }

    /** The compiled [ColorFilter] ready to pass to any draw call / `Modifier.ogLook`. Cached. */
    val colorFilter: ColorFilter by lazy { ColorFilter.colorMatrix(colorMatrix) }

    /** True when this look changes nothing (every knob at its neutral value) — a pure pass-through. */
    val isIdentity: Boolean
        get() = brightness == 0f && contrast == 1f && saturation == 1f &&
            temperature == 0f && tint == 0f && hue == 0f

    companion object {
        /** The neutral look: changes nothing. */
        val Identity = OGLookSpec()
    }
}

/**
 * A **serializable blend mode** for compositing a looked layer over what is beneath it (the Compose
 * [BlendMode] enum is not itself serializable). [NORMAL] is ordinary source-over and keeps the
 * compositor's byte-identical export guarantee; the others composite through the platform's own Skia
 * (visually consistent across Android & iOS, but not part of the byte-identical guarantee).
 */
@Serializable
enum class OGBlendMode {
    NORMAL, MULTIPLY, SCREEN, OVERLAY, LIGHTEN, DARKEN, PLUS;

    /** Map to the Compose [BlendMode] used at draw time. */
    fun toBlendMode(): BlendMode = when (this) {
        NORMAL -> BlendMode.SrcOver
        MULTIPLY -> BlendMode.Multiply
        SCREEN -> BlendMode.Screen
        OVERLAY -> BlendMode.Overlay
        LIGHTEN -> BlendMode.Lighten
        DARKEN -> BlendMode.Darken
        PLUS -> BlendMode.Plus
    }
}

// --- Pure colour-matrix maths (extracted + public-internal so it is directly unit-testable) ---------

private const val LR = 0.213f // Rec. 709-ish luminance weights, shared by saturation + hue rotation
private const val LG = 0.715f
private const val LB = 0.072f

/**
 * Build the row-major 4×5 colour matrix (20 floats) for [spec], composing the active knobs in a fixed
 * order (hue → saturation → white-balance → contrast → brightness). Neutral knobs are skipped, so a
 * single-knob spec yields exactly that knob's matrix. Pure + deterministic → identical on every target.
 */
fun buildColorMatrixArray(spec: OGLookSpec): FloatArray {
    var m = identityMatrix()
    if (spec.hue != 0f) m = concat(hueMatrix(spec.hue), m)
    if (spec.saturation != 1f) m = concat(saturationMatrix(spec.saturation), m)
    if (spec.temperature != 0f || spec.tint != 0f) m = concat(whiteBalanceMatrix(spec.temperature, spec.tint), m)
    if (spec.contrast != 1f) m = concat(contrastMatrix(spec.contrast), m)
    if (spec.brightness != 0f) m = concat(brightnessMatrix(spec.brightness), m)
    return m
}

/** Compose two 4×5 colour matrices as 5×5 affine transforms: the result applies [before] then [after]. */
internal fun concat(after: FloatArray, before: FloatArray): FloatArray {
    val r = FloatArray(20)
    for (i in 0 until 4) {
        for (j in 0 until 5) {
            var sum = 0f
            for (k in 0 until 4) sum += after[i * 5 + k] * before[k * 5 + j]
            if (j == 4) sum += after[i * 5 + 4] // after's own translate column
            r[i * 5 + j] = sum
        }
    }
    return r
}

private fun identityMatrix() = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

/** Saturation around luminance: `s=1` identity, `s=0` greyscale, `s>1` richer. */
internal fun saturationMatrix(s: Float): FloatArray {
    val inv = 1f - s
    return floatArrayOf(
        LR * inv + s, LG * inv, LB * inv, 0f, 0f,
        LR * inv, LG * inv + s, LB * inv, 0f, 0f,
        LR * inv, LG * inv, LB * inv + s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** Contrast scaled around mid-grey (127.5): `c=1` identity. */
internal fun contrastMatrix(c: Float): FloatArray {
    val t = 127.5f * (1f - c)
    return floatArrayOf(
        c, 0f, 0f, 0f, t,
        0f, c, 0f, 0f, t,
        0f, 0f, c, 0f, t,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** Additive brightness: [b] in `-1..1` → a `±255` offset on R/G/B. */
internal fun brightnessMatrix(b: Float): FloatArray {
    val o = b * 255f
    return floatArrayOf(
        1f, 0f, 0f, 0f, o,
        0f, 1f, 0f, 0f, o,
        0f, 0f, 1f, 0f, o,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** White balance as channel scales: [temp] warms R / cools B, [tint] on the green↔magenta axis. */
internal fun whiteBalanceMatrix(temp: Float, tint: Float): FloatArray {
    val rF = 1f + 0.2f * temp
    val bF = 1f - 0.2f * temp
    val gF = 1f - 0.2f * tint
    return floatArrayOf(
        rF, 0f, 0f, 0f, 0f,
        0f, gF, 0f, 0f, 0f,
        0f, 0f, bF, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** Luminance-preserving hue rotation by [deg] degrees (`0` → identity). */
internal fun hueMatrix(deg: Float): FloatArray {
    val rad = deg.toDouble() * PI / 180.0
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    return floatArrayOf(
        LR + c * (1f - LR) + s * (-LR), LG + c * (-LG) + s * (-LG), LB + c * (-LB) + s * (1f - LB), 0f, 0f,
        LR + c * (-LR) + s * (0.143f), LG + c * (1f - LG) + s * (0.140f), LB + c * (-LB) + s * (-0.283f), 0f, 0f,
        LR + c * (-LR) + s * (-(1f - LR)), LG + c * (-LG) + s * (LG), LB + c * (1f - LB) + s * (LB), 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}
