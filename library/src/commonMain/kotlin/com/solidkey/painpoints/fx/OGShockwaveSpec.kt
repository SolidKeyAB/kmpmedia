package com.solidkey.painpoints.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * **Shockwave** — an expanding impact ring, described as plain data. Part of the
 * `com.solidkey.painpoints.fx` family. Decode + presets on [OGShockwave]; draw a frame with
 * [drawOGShockwaveFrame].
 *
 * The ring expands from a centre point as a caller-driven `progress` goes `0 → 1` using a Sedov
 * blast curve ($r \propto p^{0.52}$) — explosively fast at detonation, decelerating as mass is
 * pushed. Visual layers: an initial core flash, a soft inner compression wake, a razor Mach-stem
 * leading edge, and a trailing secondary rebound ring. Optional harmonic turbulence distorts the
 * ring boundary with deterministic sine harmonics. No shader, no per-pixel work — pure Canvas.
 */
@kotlinx.serialization.Serializable
data class OGShockwaveSpec(
    val name: String? = null,
    /** Ring colour — any SVG colour string. */
    val color: String = "#FFFFFF",
    /** Ring radius at `progress = 1`, as a fraction of `min(width, height)`. */
    val maxRadius: Float = 0.45f,
    /** Stroke width at `progress = 0`, as a fraction of `min(width, height)`. */
    val ringWidth: Float = 0.025f,
    /** Vertical squash: `1.0` = circle, `0.5` = 2:1 ground-plane oval. */
    val squash: Float = 1.0f,
    /** Peak alpha at `progress = 0`; fades toward `0` at `progress = 1`. */
    val alpha: Float = 0.85f,
    /** Seed for deterministic harmonic turbulence phase. */
    val seed: Int = 0,
    /** Soft diffused inner compression wake behind the leading edge. */
    val innerGlow: Boolean = true,
    /** Trailing secondary harmonic rebound ring. */
    val secondaryRing: Boolean = true,
    /** Epicenter detonation fireball flash at `progress < 25%`. */
    val coreFlash: Boolean = true,
    /** Edge flutter (`0` = smooth circle, `0.05` = fiery distorted blast). */
    val turbulence: Float = 0.035f,
) {
    /** Clamp every field into its sane range. */
    fun sanitized(): OGShockwaveSpec = copy(
        maxRadius = maxRadius.coerceIn(0.01f, 2f),
        ringWidth = ringWidth.coerceIn(0.001f, 0.5f),
        squash = squash.coerceIn(0.1f, 1f),
        alpha = alpha.coerceIn(0f, 1f),
        turbulence = turbulence.coerceIn(0f, 0.15f),
    )
}

/**
 * Draw one frame of a [spec]'s shockwave into the current [DrawScope], centred at the normalized
 * `(cx, cy)`, at [progress] `0..1`. Nothing is drawn outside that range.
 *
 * Rendering layers (back to front):
 * 1. **Core flash** — brilliant epicenter fireball at progress < 0.25 that rapidly collapses.
 * 2. **Secondary rebound ring** — trailing harmonic wave behind the main front.
 * 3. **Inner compression wake** — soft wide glow body just inside the crest.
 * 4. **Mach stem** — razor-thin bright leading edge (optionally turbulence-distorted).
 */
fun DrawScope.drawOGShockwaveFrame(spec: OGShockwaveSpec, cx: Float, cy: Float, progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val s = spec.sanitized()
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return
    val minSide = min(w, h)
    val color = s.color.toSpecColor() ?: Color.White
    val center = Offset(cx * w, cy * h)

    // Sedov blast expansion curve — explosive fast start, deceleration over time
    val blastP = progress.pow(0.52f)
    val baseAlpha = (s.alpha * (1f - progress.pow(1.3f))).coerceIn(0f, 1f)

    // 1. Core Detonation Flash (epicenter fireball that collapses)
    if (s.coreFlash && progress < 0.25f) {
        val flashT = progress / 0.25f
        val flashAlpha = ((1f - flashT).pow(2f) * s.alpha).coerceIn(0f, 1f)
        val flashR = minSide * 0.10f * (1f + flashT * 0.8f)
        drawOval(
            color = Color.White.copy(alpha = flashAlpha * 0.85f),
            topLeft = Offset(center.x - flashR, center.y - flashR * s.squash),
            size = Size(flashR * 2f, flashR * 2f * s.squash),
        )
    }

    // Helper: draw a single shock ring (smooth oval or turbulence-distorted path)
    fun drawRing(rFrac: Float, strokeFrac: Float, ringAlpha: Float, crestHighlight: Boolean) {
        if (ringAlpha <= 0.01f || rFrac <= 0f) return
        val rx = rFrac * s.maxRadius * minSide
        val ry = rx * s.squash
        val stroke = (strokeFrac * minSide).coerceAtLeast(1f)
        val ringColor = if (crestHighlight) Color.White.copy(alpha = ringAlpha) else color.copy(alpha = ringAlpha)

        if (s.turbulence <= 0.002f) {
            drawOval(
                color = ringColor,
                topLeft = Offset(center.x - rx, center.y - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = stroke),
            )
        } else {
            val path = Path()
            val steps = 48
            val turbAmp = s.turbulence * (1f - progress)
            for (i in 0 until steps) {
                val theta = i * (2f * PI.toFloat() / steps)
                val flutter = 1f + turbAmp * sin(7f * theta + s.seed.toFloat()) * cos(3f * theta)
                val px = center.x + cos(theta) * rx * flutter
                val py = center.y + sin(theta) * ry * flutter
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, color = ringColor, style = Stroke(width = stroke))
        }
    }

    // 2. Trailing Secondary Harmonic Wave (behind main front)
    if (s.secondaryRing && blastP > 0.18f) {
        val secP = ((blastP - 0.18f) / 0.82f).coerceIn(0f, 1f)
        val secAlpha = baseAlpha * 0.45f * (1f - secP)
        val secStroke = s.ringWidth * 0.65f * (1f - 0.4f * secP)
        drawRing(secP * 0.82f, secStroke, secAlpha, crestHighlight = false)
    }

    // 3. Inner Compression Wake / Glow (soft wide body)
    if (s.innerGlow) {
        val glowStroke = s.ringWidth * 2.4f * (1f - 0.5f * blastP)
        drawRing(blastP * 0.96f, glowStroke, baseAlpha * 0.32f, crestHighlight = false)
    }

    // 4. Razor Mach Stem (sharp bright leading edge)
    val crestStroke = (s.ringWidth * 0.70f * (1f - 0.4f * blastP)).coerceAtLeast(1f / minSide)
    drawRing(blastP, crestStroke, baseAlpha * 0.95f, crestHighlight = true)
}
