package com.solidkey.painpoints.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.abs
import kotlin.math.min

/**
 * **Shockwave** — an expanding impact ring, described as plain data. Part of the
 * `com.solidkey.painpoints.fx` family. Decode + presets on [OGShockwave]; draw a frame with
 * [drawOGShockwaveFrame].
 *
 * The ring expands from a centre point as a caller-driven `progress` goes `0 → 1`: the radius grows to
 * [maxRadius], the alpha fades linearly from [alpha] to zero, and the stroke tapers to half its starting
 * width. [squash] turns the circle into a ground-plane oval (`0.5` = a 2:1 ellipse), which reads as a
 * blast rolling across a floor rather than an airburst.
 *
 * It is a single `Stroke` circle/oval per frame — no shader, no per-pixel work — and fully deterministic,
 * so the same `(spec, progress)` is frame-identical on Android & iOS. Shockwaves are always driven by an
 * external progress animation (a spring, a tween, a timeline), so there is no built-in clock or view.
 */
@kotlinx.serialization.Serializable
data class OGShockwaveSpec(
    val name: String? = null,
    /** Ring colour — any SVG colour string. */
    val color: String = "#FFFFFF",
    /** Ring radius at `progress = 1`, as a fraction of `min(width, height)`. */
    val maxRadius: Float = 0.45f,
    /** Stroke width at `progress = 0`, as a fraction of `min(width, height)`; tapers to 50% at `progress = 1`. */
    val ringWidth: Float = 0.025f,
    /** Vertical squash: `1.0` = circle, `0.5` = a 2:1 ground-plane oval. */
    val squash: Float = 1.0f,
    /** Peak alpha at `progress = 0`; fades linearly to `0` at `progress = 1`. */
    val alpha: Float = 0.85f,
    /** Seed, kept for family symmetry (the ring itself is fully parametric). */
    val seed: Int = 0,
) {
    /** Clamp every field into its sane range (lenient model/designer input never breaks a frame). */
    fun sanitized(): OGShockwaveSpec = copy(
        maxRadius = maxRadius.coerceIn(0.01f, 2f),
        ringWidth = ringWidth.coerceIn(0.001f, 0.5f),
        squash = squash.coerceIn(0.1f, 1f),
        alpha = alpha.coerceIn(0f, 1f),
    )
}

/**
 * Draw one frame of a [spec]'s shockwave into the current [DrawScope], centred at the normalized
 * `(cx, cy)` (fractions of the view), at [progress] `0..1`. Outside `(0, 1)` nothing is drawn — the ring
 * only exists mid-expansion. One stroke per frame; drive [progress] with any animation you like.
 */
fun DrawScope.drawOGShockwaveFrame(spec: OGShockwaveSpec, cx: Float, cy: Float, progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val s = spec.sanitized()
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return
    val minSide = min(w, h)
    val color = s.color.toSpecColor() ?: Color.White
    val rx = progress * s.maxRadius * minSide
    val ry = rx * s.squash
    val a = (s.alpha * (1f - progress)).coerceIn(0f, 1f)
    val stroke = (s.ringWidth * minSide * (1f - 0.5f * progress)).coerceAtLeast(1f)
    if (abs(s.squash - 1f) <= 0.01f) {
        drawCircle(
            color = color.copy(alpha = a),
            radius = rx,
            center = Offset(cx * w, cy * h),
            style = Stroke(width = stroke),
        )
    } else {
        drawOval(
            color = color.copy(alpha = a),
            topLeft = Offset(cx * w - rx, cy * h - ry),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(width = stroke),
        )
    }
}
