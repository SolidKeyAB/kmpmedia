package com.solidkey.painpoints.fx

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Draw and run an [spec]'s **fire** — upward-licking flame tongues filling this composable's bounds from
 * [OGFlameSpec.baseY] up. Set [playing] to `false` to freeze it; pass a live [windOverride] to bend the
 * whole fire with a scene's breeze (overrides [OGFlameSpec.wind] when non-null).
 *
 * 60fps + cross-platform: each tongue is one GPU-filled tapered path with a hot→cool vertical gradient,
 * its centreline waving and flickering via a few fractal-noise samples (see [OGFlameSpec]) — no per-pixel
 * work, no shader. Overlay it in a `Box`; it is transparent between and above the tongues. For a candle,
 * use the one-tongue [OGFlame.CANDLE] preset in a narrow box.
 */
@Composable
fun OGFlameView(
    spec: OGFlameSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    windOverride: Float? = null,
) {
    val s = remember(spec, windOverride) {
        (if (windOverride != null) spec.copy(wind = windOverride) else spec).sanitized()
    }
    val core = remember(s) { s.coreColor.toSpecColor() ?: Color(0xFFFFD24D) }
    val tip = remember(s) { s.tipColor.toSpecColor() ?: Color(0xFFFF4A1C) }

    var elapsed by remember(s) { mutableStateOf(0f) }
    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }

    Canvas(modifier) {
        drawOGFlameFrame(s, elapsed / 1000f, core, tip)
    }
}

/**
 * Draw one frame of a [spec]'s fire at [timeSec] with its resolved [core] (base) and [tip] colours, into
 * the current [DrawScope]. The per-frame body shared by the live [OGFlameView] and any off-screen baker —
 * one code path, so an export matches the preview. Each tongue is a tapered path around its waving spine,
 * filled with a vertical base→tip gradient.
 */
internal fun DrawScope.drawOGFlameFrame(spec: OGFlameSpec, timeSec: Float, core: Color, tip: Color) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return

    val mid = lerpColor(core, tip, 0.55f)
    flameTongues(spec, timeSec).forEachIndexed { k, tongue ->
        val spine = tongue.spine
        if (spine.size < 2) return@forEachIndexed
        val baseHalf = (tongue.halfWidth * w).coerceAtLeast(0.5f)
        val n = spine.size - 1

        // Per-point torn-edge displacement, sampled once per spine point and reused by both passes.
        // Kelvin-Helmholtz structured (see [khEdgeNoise]) rather than plain random noise.
        val edgeNoise = FloatArray(n + 1) { i ->
            khEdgeNoise(i.toFloat() / n, timeSec, k, spec)
        }

        val cx = spine[0].x * w
        val baseYPx = spine[0].y * h
        val tipYPx = spine[n].y * h

        // Pass A — outer haze: full belly width + ragged x-jittered edges, translucent tip-colour wash.
        val haze = tonguePath(spine, edgeNoise, baseHalf, w, h, widthFactor = 1f, jitter = true)
        drawPath(
            haze,
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to tip.copy(alpha = 0f),
                    0.10f to tip.copy(alpha = tip.alpha * tongue.alpha * 0.25f),
                    0.45f to tip.copy(alpha = tip.alpha * tongue.alpha * 0.65f),
                    1f to tip.copy(alpha = 0f),
                ),
                start = Offset(cx, baseYPx),
                end = Offset(cx, tipYPx),
            ),
        )

        // Pass B — inner bright core: slimmer, cleaner edge, brightness pulsing with the flicker.
        val coreAlpha = (
            tongue.alpha * (0.80f + 0.20f * (tongue.flickerPulse - 1f) / spec.flicker.coerceAtLeast(0.001f))
        ).coerceIn(0f, 1f)
        val inner = tonguePath(spine, edgeNoise, baseHalf, w, h, widthFactor = 0.60f, jitter = false)
        drawPath(
            inner,
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to core.copy(alpha = 0f),
                    0.10f to core.copy(alpha = core.alpha * coreAlpha * 0.35f),
                    0.28f to core.copy(alpha = core.alpha * coreAlpha),
                    0.65f to mid.copy(alpha = mid.alpha * coreAlpha * 0.85f),
                    1f to tip.copy(alpha = 0f),
                ),
                start = Offset(cx, baseYPx),
                end = Offset(cx, tipYPx),
            ),
        )
    }
}

// KH mode tables (allocated once): subharmonic / fundamental / first harmonic of the dominant
// roll-up wavelength — vortex pairing makes real plumes multi-scale — with each mode riding
// tip-ward at a slightly different drift speed.
private val KH_FREQ_MUL = floatArrayOf(0.6f, 1f, 1.7f)
private val KH_BASE_AMP = floatArrayOf(0.42f, 0.50f, 0.30f)
private val KH_DRIFT_MUL = floatArrayOf(0.8f, 1.1f, 1.45f)

/**
 * **Kelvin-Helmholtz structured edge displacement** at height [s] (`0` = base, `1` = tip) for tongue
 * [k] at [timeSec], roughly in `[-1, 1]`.
 *
 * Real flame edges are not randomly noisy: the velocity shear between the hot rising column and the
 * still ambient air rolls the interface up into a train of **Kelvin-Helmholtz vortices**. The linear
 * instability grows as `σ ∝ k·|Δv|` — so a faster flame rolls up at a shorter wavelength — and each
 * perturbation grows ~exponentially with height until nonlinear saturation. We model that with three
 * travelling spatial modes (subharmonic / fundamental / first harmonic of the dominant KH wavelength,
 * which shrinks with [OGFlameSpec.flickerSpeed]), each with an `exp`-growth height envelope saturating
 * at 1, drifting tip-ward with the gas at ~[OGFlameSpec.swaySpeed], plus a small fractal-noise detail
 * layer riding at 30% of the KH amplitude. Deterministic from the spec seed.
 */
private fun khEdgeNoise(s: Float, timeSec: Float, k: Int, spec: OGFlameSpec): Float {
    val twoPi = 2f * PI.toFloat()
    // Dominant KH mode: faster flame (stronger shear) → shorter roll-up wavelength → higher frequency.
    val fDom = (spec.flickerSpeed * 1.5f + 1f)
    val drift = spec.swaySpeed * twoPi

    val growth = 3.5f
    // Exponential instability growth with height, saturating at 1 (nonlinear roll-up limit).
    val envelope = min(1f, exp(growth * s) - 1f)

    var kh = 0f
    var ampSum = 0f
    for (m in 0..2) {
        val phase = OGFxMath.hash01(m * 19 + k * 3 + 5, spec.seed + 77) * twoPi
        val a = KH_BASE_AMP[m] * envelope
        kh += a * sin(s * fDom * KH_FREQ_MUL[m] * twoPi - timeSec * drift * KH_DRIFT_MUL[m] + phase)
        ampSum += a
    }
    // Fine turbulent detail at 30% of the KH amplitude — texture, not structure.
    val detail = 0.3f * ampSum * OGFxNoise.fbm(
        s * 4.5f + timeSec * spec.swaySpeed * 1.3f + k * 3.1f,
        timeSec * spec.flickerSpeed * 0.4f + k * 0.7f,
        2, spec.seed + 77,
    )
    return ((kh + detail) * 0.8f).coerceIn(-1.2f, 1.2f)
}

/**
 * Build one tongue's tapered outline: up the left edge (base → tip), then down the right edge. Width
 * follows the [bellTaper] belly profile scaled by [widthFactor]; when [jitter] is set the per-point
 * [edgeNoise] also shears the edges sideways (growing toward the tip) for a torn, turbulent silhouette.
 */
private fun tonguePath(
    spine: List<OGFlamePoint>,
    edgeNoise: FloatArray,
    baseHalf: Float,
    w: Float,
    h: Float,
    widthFactor: Float,
    jitter: Boolean,
): Path {
    val n = spine.size - 1
    val path = Path()
    for (i in 0..n) {
        val s = i.toFloat() / n
        val hw = baseHalf * bellTaper(s, edgeNoise[i]) * widthFactor
        val jx = if (jitter) edgeNoise[i] * s * s * baseHalf * 0.20f else 0f
        val x = spine[i].x * w - hw + jx
        val y = spine[i].y * h
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    for (i in n downTo 0) {
        val s = i.toFloat() / n
        val hw = baseHalf * bellTaper(s, edgeNoise[i]) * widthFactor
        val jx = if (jitter) edgeNoise[i] * s * s * baseHalf * 0.20f else 0f
        path.lineTo(spine[i].x * w + hw + jx, spine[i].y * h)
    }
    path.close()
    return path
}

/**
 * Half-width factor along a tongue with a **buoyancy belly**: narrowest at the root (dense fuel rising
 * in a tight column), swelling to full width ~38% up — where buoyant acceleration and ambient mixing
 * entrain the most volume — then smoothstepping down to a sharp tip as the gas cools. [noise] tears
 * the profile more and more toward the tip for a ragged, turbulent crown.
 */
private fun bellTaper(s: Float, noise: Float): Float {
    val base = if (s < 0.38f) {
        // Root taper: smooth teardrop root curving to 0 width at s=0 so the fire base is organic, never a cut line!
        val rootTaper = if (s < 0.16f) {
            val u = s / 0.16f
            u * u * (3f - 2f * u) // smoothstep ramp from 0 at s=0 to 1 at s=0.16
        } else 1f
        (0.4f + (s / 0.38f) * 0.6f) * rootTaper
    } else {
        val t = (s - 0.38f) / 0.62f
        val smooth = t * t * (3f - 2f * t) // smoothstep
        1f - smooth
    }
    return (base + noise * s * s * 0.35f).coerceIn(0f, 1.4f)
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
