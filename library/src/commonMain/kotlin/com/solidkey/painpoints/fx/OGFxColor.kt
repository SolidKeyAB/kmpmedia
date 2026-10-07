package com.solidkey.painpoints.fx

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * **Perceptual colour interpolation** for the FX family, via Björn Ottosson's **OKLab** colour space
 * (2020). Blending two colours straight in sRGB (what a plain gradient does) cuts a chord across the
 * colour solid and dips through a muddy, desaturated middle — a blue→yellow ramp passes through grey.
 * OKLab is fit to perceptual data, so a straight line in it stays bright and even. We sample the OKLab
 * line into a handful of stops and hand those to the GPU gradient, so the hot path is unchanged — the
 * conversion is pure maths done once per spec, deterministic, and identical on Android & iOS.
 *
 * See `docs/SLASH.md` (References) — Ottosson, "A perceptual color space for image processing", 2020.
 */
internal object OGFxColor {

    /**
     * Sample the OKLab-interpolated ramp from [a] to [b] into [stops] evenly-spaced colours
     * (inclusive of both ends). Feed the result to `Brush.linearGradient(colors = ...)`: with enough
     * stops the GPU's per-segment sRGB blend closely traces the true OKLab curve, so the gradient stays
     * perceptually even with no muddy middle. [stops] is clamped to `2..64`.
     */
    fun oklabStops(a: Color, b: Color, stops: Int = 12): List<Color> {
        val n = stops.coerceIn(2, 64)
        val la = srgbToOklab(a)
        val lb = srgbToOklab(b)
        return List(n) { i ->
            val t = i / (n - 1f)
            oklabToSrgb(
                la[0] + (lb[0] - la[0]) * t,
                la[1] + (lb[1] - la[1]) * t,
                la[2] + (lb[2] - la[2]) * t,
                la[3] + (lb[3] - la[3]) * t, // alpha carried linearly
            )
        }
    }

    /** Convert a Compose sRGB [Color] to OKLab `[L, a, b, alpha]`. */
    fun srgbToOklab(c: Color): FloatArray {
        val r = linearize(c.red); val g = linearize(c.green); val bl = linearize(c.blue)
        val l = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * bl
        val m = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * bl
        val s = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * bl
        val l_ = cbrt(l); val m_ = cbrt(m); val s_ = cbrt(s)
        return floatArrayOf(
            0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_,
            1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_,
            0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_,
            c.alpha,
        )
    }

    /** Convert OKLab `L, a, b` (+ [alpha]) back to a Compose sRGB [Color], clamped in gamut. */
    fun oklabToSrgb(L: Float, a: Float, b: Float, alpha: Float): Color {
        val l_ = L + 0.3963377774f * a + 0.2158037573f * b
        val m_ = L - 0.1055613458f * a - 0.0638541728f * b
        val s_ = L - 0.0894841775f * a - 1.2914855480f * b
        val l = l_ * l_ * l_; val m = m_ * m_ * m_; val s = s_ * s_ * s_
        val r = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
        val g = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
        val bl = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s
        return Color(
            delinearize(r).coerceIn(0f, 1f),
            delinearize(g).coerceIn(0f, 1f),
            delinearize(bl).coerceIn(0f, 1f),
            alpha.coerceIn(0f, 1f),
        )
    }

    /** sRGB → linear light. */
    private fun linearize(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

    /** Linear light → sRGB. */
    private fun delinearize(c: Float): Float =
        if (c <= 0.0031308f) 12.92f * c else (1.055f * c.toDouble().pow(1.0 / 2.4) - 0.055).toFloat()

    /** Cube root that is defined for negatives (sign-preserving). */
    private fun cbrt(x: Float): Float {
        val d = x.toDouble()
        return (if (d >= 0.0) d.pow(1.0 / 3.0) else -((-d).pow(1.0 / 3.0))).toFloat()
    }
}
