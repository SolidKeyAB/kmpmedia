package com.solidkey.painpoints.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Holds the list of active [OGRippleEvent]s for an [OGRippleView].
 * Spawn new impacts with [spawn]; the view culls expired rings automatically each frame.
 */
class OGRippleState {
    private val _events = mutableListOf<OGRippleEvent>()
    internal val events: List<OGRippleEvent> get() = _events

    /** Spawn a new raindrop impact at normalised position ([cx], [cy]). */
    fun spawn(cx: Float, cy: Float, nowMs: Long) {
        _events += OGRippleEvent(cx = cx.coerceIn(0f, 1f), cy = cy.coerceIn(0f, 1f), startMs = nowMs)
        // Keep at most 32 active impacts (oldest culled on overflow)
        if (_events.size > 32) _events.removeAt(0)
    }

    internal fun cull(nowMs: Long, spec: OGRippleSpec) {
        val maxAgeMs = (spec.maxRadius / spec.propagationSpeed.coerceAtLeast(0.01f)).pow(1f / 0.65f) * 1400f
        _events.removeAll { (nowMs - it.startMs) > maxAgeMs }
    }
}

/** Creates and [remember]s an [OGRippleState]. */
@Composable
fun rememberOGRippleState(): OGRippleState = remember { OGRippleState() }

/**
 * Convenience overload that maintains its own [OGRippleState].
 */
@Composable
fun OGRippleView(
    spec: OGRippleSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val state = rememberOGRippleState()
    OGRippleView(state = state, spec = spec, modifier = modifier, playing = playing)
}

/**
 * Renders active ripple rings from [state] according to [spec]. Attach [Modifier.ogRippleInput]
 * to wire tap-to-spawn; or call [OGRippleState.spawn] manually (e.g. for a rain simulation that
 * spawns at random positions).
 *
 * The view drives its own animation loop — set [playing] to `false` to freeze.
 */
@Composable
fun OGRippleView(
    state: OGRippleState,
    spec: OGRippleSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val s         = remember(spec) { spec.sanitized() }
    val ringColor  = remember(s) { s.color.toSpecColor()      ?: Color(0xFF4FC3F7) }
    val crestColor = remember(s) { s.crestColor.toSpecColor() ?: Color.White }

    var nowMs by remember { mutableStateOf(0L) }
    var tick  by remember { mutableIntStateOf(0) }

    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            withFrameMillis { t ->
                nowMs = t
                state.cull(t, s)
                tick++
            }
        }
    }

    Canvas(modifier.clipToBounds()) {
        @Suppress("UNUSED_EXPRESSION") tick
        drawOGRippleFrame(s, state, nowMs, ringColor, crestColor)
    }
}

/**
 * Convenience modifier that forwards tap gestures to [state] as ripple impacts.
 * Attach this to whatever composable should be tappable (the view itself, or a parent).
 */
fun Modifier.ogRippleInput(state: OGRippleState): Modifier =
    this.pointerInput(state) {
        var nowMs = 0L
        detectTapGestures(
            onPress = { offset ->
                withFrameMillis { nowMs = it }
                state.spawn(
                    cx    = offset.x / size.width,
                    cy    = offset.y / size.height,
                    nowMs = nowMs,
                )
            },
        )
    }

/**
 * Draw one frame of the ripple system at [nowMs]. Shared by [OGRippleView] and any baker.
 *
 * Each active [OGRippleEvent] contributes:
 * 1. A primary ring expanding at r(t) = speed · t^0.65 · minDim.
 * 2. [OGRippleSpec.secondaryRings] trailing rings grouped with a Gaussian wave-packet envelope.
 * 3. Exponential amplitude decay on all rings: alpha = A₀ · e^(−λ·t).
 * 4. **3D meniscus**: dark refraction trough + diffuse water body + razor specular crest.
 * 5. Worthington crown — ballistic streak droplets + central rebound jet bead at t < 150ms.
 */
fun DrawScope.drawOGRippleFrame(
    spec: OGRippleSpec,
    state: OGRippleState,
    nowMs: Long,
    ringColor: Color,
    crestColor: Color,
) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return
    val minDim = min(w, h)
    val s = spec.sanitized()
    val maxR = s.maxRadius * minDim
    val strokeW = s.lineWidth * minDim
    val troughCol = s.troughColor.toSpecColor() ?: Color(0xFF051D33)

    for (event in state.events) {
        val ageSec = ((nowMs - event.startMs) / 1000f).coerceAtLeast(0f)

        val primaryR = s.propagationSpeed * ageSec.pow(0.65f) * minDim
        if (primaryR > maxR) continue

        val amp = exp(-s.decayLambda * ageSec)
        if (amp < 0.015f) continue

        val ox = event.cx * w
        val oy = event.cy * h

        // 1. Worthington Splash Crown — ballistic streak droplets + rebound bead
        if (s.crownDroplets && ageSec < 0.15f) {
            val crownProgress = ageSec / 0.15f
            val crownAlpha = (1f - crownProgress) * amp
            val twoPI = 2f * PI.toFloat()

            for (k in 0 until s.crownCount) {
                val angle = k * twoPI / s.crownCount
                val speedJitter = 1.0f + 0.5f * OGFxMath.hash01(k, s.seed)
                val dist = primaryR * (1.1f + crownProgress * 0.6f * speedJitter)
                val arcHeight = 0.045f * minDim * sin(crownProgress * PI.toFloat()) * speedJitter

                val dropX = ox + dist * cos(angle)
                val dropY = oy + dist * sin(angle) * s.squash - arcHeight

                // Motion streak along the launch vector
                val prevDist = dist * 0.88f
                val prevX = ox + prevDist * cos(angle)
                val prevY = oy + prevDist * sin(angle) * s.squash - arcHeight * 0.85f

                drawLine(
                    color = crestColor.copy(alpha = crownAlpha * 0.85f),
                    start = Offset(prevX, prevY),
                    end = Offset(dropX, dropY),
                    strokeWidth = strokeW * 0.9f,
                    cap = StrokeCap.Round,
                )
            }

            // Central Worthington rebound bead
            if (s.reboundJet && ageSec < 0.10f) {
                val jetT = ageSec / 0.10f
                val jetH = 0.035f * minDim * sin(jetT * PI.toFloat())
                drawCircle(
                    color = crestColor.copy(alpha = (1f - jetT) * amp * 0.9f),
                    radius = strokeW * 1.3f,
                    center = Offset(ox, oy - jetH),
                )
            }
        }

        // 2. Wave Packet Rings with 3D Meniscus
        for (ring in 0..s.secondaryRings) {
            val r = primaryR - ring * s.ringSpacing * minDim
            if (r <= 0f || r > maxR) continue

            // Gaussian wave-packet envelope — natural capillary grouping
            val packetWeight = exp(-((ring - 0.5f) * (ring - 0.5f)) / (2f * 1.6f * 1.6f))
            val ringAlpha = amp * packetWeight

            val rx = r; val ry = r * s.squash

            // Layer A: 3D dark refraction trough (water depression just inside the crest)
            if (s.troughDepth > 0f) {
                val troughR = (r - strokeW * 0.7f).coerceAtLeast(0.5f)
                val trX = troughR; val trY = troughR * s.squash
                drawOval(
                    color = troughCol.copy(alpha = ringAlpha * s.troughDepth),
                    topLeft = Offset(ox - trX, oy - trY),
                    size = Size(trX * 2f, trY * 2f),
                    style = Stroke(width = strokeW * 1.25f),
                )
            }

            // Layer B: diffuse water body
            drawOval(
                color = ringColor.copy(alpha = ringAlpha * (if (ring == 0) 0.70f else 0.45f)),
                topLeft = Offset(ox - rx, oy - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = strokeW * (1f - ring * 0.12f).coerceAtLeast(0.4f)),
            )

            // Layer C: razor specular crest highlight on the two leading rings
            if (ring <= 1) {
                val crestAlpha = ringAlpha * (if (ring == 0) 0.95f else 0.50f)
                drawOval(
                    color = crestColor.copy(alpha = crestAlpha),
                    topLeft = Offset(ox - rx, oy - ry),
                    size = Size(rx * 2f, ry * 2f),
                    style = Stroke(width = strokeW * 0.55f, cap = StrokeCap.Round),
                )
            }
        }
    }
}
