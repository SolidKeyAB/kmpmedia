package com.solidkey.painpoints.fx

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Holds the live simulation state for a firefly swarm: per-fly phases, natural frequencies,
 * positions, and wander offsets. Build once via [rememberOGFireflyState] and pass to [OGFireflyView].
 * The state is mutable and updated in-place each frame — no per-frame allocation.
 */
class OGFireflyState(spec: OGFireflySpec) {
    private val N = spec.count.coerceIn(1, 500)

    internal val phases      = FloatArray(N)   // θ_i (radians)
    internal val omegas      = FloatArray(N)   // ω_i (natural frequency, rad/s)
    internal val posX        = FloatArray(N)   // normalized [0,1]
    internal val posY        = FloatArray(N)
    internal val wanderX     = FloatArray(N)
    internal val wanderY     = FloatArray(N)

    init {
        val rng = Random(spec.seed)
        val twoPI = 2f * PI.toFloat()
        for (i in 0 until N) {
            phases[i]  = rng.nextFloat() * twoPI
            omegas[i]  = spec.omegaMean + (rng.nextFloat() - 0.5f) * 2f * spec.omegaSpread
            posX[i]    = rng.nextFloat()
            posY[i]    = rng.nextFloat()
            wanderX[i] = rng.nextFloat() * twoPI
            wanderY[i] = rng.nextFloat() * twoPI
        }
    }

    /** Advance the Kuramoto simulation and positions by [dt] seconds. O(N). */
    internal fun update(spec: OGFireflySpec, dt: Float) {
        val dtC = dt.coerceIn(0f, 0.064f)
        val n   = phases.size

        // Kuramoto mean-field order parameter: r·e^{iΨ} = (1/N) Σ e^{iθ_j}
        var sumCos = 0f; var sumSin = 0f
        for (i in 0 until n) { sumCos += cos(phases[i]); sumSin += sin(phases[i]) }
        val r   = sqrt(sumCos * sumCos + sumSin * sumSin) / n
        val psi = atan2(sumSin, sumCos)

        // Phase update + Brownian drift
        val twoPI = 2f * PI.toFloat()
        for (i in 0 until n) {
            phases[i] += (omegas[i] + spec.coupling * r * sin(psi - phases[i])) * dtC

            wanderX[i] += spec.turbulence * dtC * (0.8f + omegas[i] * 0.12f)
            wanderY[i] += spec.turbulence * dtC * (0.6f + omegas[i] * 0.09f)
            posX[i] = frac(posX[i] + spec.driftSpeed * dtC * cos(wanderX[i]))
            posY[i] = frac(posY[i] + spec.driftSpeed * dtC * sin(wanderY[i]))
        }
    }

    private fun frac(x: Float): Float = x - floor(x)
}

/** Creates and [remember]s an [OGFireflyState] keyed on [spec]. */
@Composable
fun rememberOGFireflyState(spec: OGFireflySpec): OGFireflyState =
    remember(spec) { OGFireflyState(spec) }

/**
 * Renders a live firefly swarm using the [OGFireflySpec]'s Kuramoto coupled oscillator model.
 * The state is maintained inside the composable; set [playing] to `false` to freeze.
 *
 * Tip: overlay this on a dark scene. The glow halos are drawn with normal blending; for additive
 * sparkle use a parent `graphicsLayer { blendMode = BlendMode.Screen }`.
 */
@Composable
fun OGFireflyView(
    spec: OGFireflySpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val s     = remember(spec) { spec.sanitized() }
    val state = remember(s) { OGFireflyState(s) }

    val coreColor = remember(s) { s.color.toSpecColor()     ?: Color(0xFFCCFF90) }
    val haloColor = remember(s) { s.glowColor.toSpecColor() ?: Color(0xFFF1F8E9) }

    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        var prev = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                val dt = (now - prev) / 1000f
                prev = now
                state.update(s, dt)
                tick++
            }
        }
    }

    Canvas(modifier.clipToBounds()) {
        @Suppress("UNUSED_EXPRESSION") tick
        drawOGFireflyFrame(s, state, coreColor, haloColor)
    }
}

/**
 * Draw one frame of the firefly swarm from pre-computed [state].
 * Shared by [OGFireflyView] and any off-screen baker.
 */
fun DrawScope.drawOGFireflyFrame(
    spec: OGFireflySpec,
    state: OGFireflyState,
    coreColor: Color,
    haloColor: Color,
) {
    val minDim  = min(size.width, size.height)
    val coreR   = spec.radius * minDim
    val haloR   = spec.glowRadius * minDim
    val N       = state.phases.size

    for (i in 0 until N) {
        // Luminescence: max(0, sin(θ))^blinkPower
        val sinVal = sin(state.phases[i])
        if (sinVal <= 0f) continue
        val brightness = sinVal.pow(spec.blinkPower) * spec.opacity
        if (brightness < 0.008f) continue

        val px = state.posX[i] * size.width
        val py = state.posY[i] * size.height
        val pos = Offset(px, py)

        // Outer halo — soft, large
        drawCircle(haloColor.copy(alpha = brightness * 0.28f), haloR, pos)
        // Mid glow
        drawCircle(coreColor.copy(alpha = brightness * 0.55f), haloR * 0.5f, pos)
        // Core
        drawCircle(coreColor.copy(alpha = brightness * 0.92f), coreR, pos)
    }
}
