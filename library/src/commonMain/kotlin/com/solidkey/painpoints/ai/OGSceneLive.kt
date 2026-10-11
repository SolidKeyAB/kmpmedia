package com.solidkey.painpoints.ai

import androidx.compose.ui.graphics.Color
import com.solidkey.painpoints.look.OGLookSpec
import com.solidkey.painpoints.motion.ease
import com.solidkey.painpoints.motion.ogEasingOf
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================================
// v4 "make it live": a still scene learns to MOVE from its own JSON. A region can declare an
// [OGRegionMotion] (translate / scale / rotate / fade over a looping timeline) and the scene an
// [OGSceneMotion] (a colour grade + base coat that swing over time — e.g. day→dusk→night). The
// evaluator is PURE and t-ADDRESSABLE: [OGSceneSpec.liveAt] / [OGPlacedShapeSpec.animatedAt] give
// the exact state at any time t with no clock, no state and no per-frame allocation beyond the
// moved points — so a compositor can scrub/export it and it holds 60fps by only re-evaluating the
// handful of regions that actually move. Everything is optional: a scene with no motion decodes
// and renders exactly as before, and an older library ignores the unknown keys.
// ============================================================================================

/** How a timeline repeats. */
enum class OGMotionMode {
    /** Play `0→1` then jump back to `0` (sawtooth). */
    LOOP,

    /** Ping-pong `0→1→0` (the natural rise-and-set / breathe). */
    REVERSE,

    /** Play `0→1` once and hold at `1`. */
    ONCE;

    companion object {
        /** Lenient, case-insensitive resolver; unknown → [REVERSE] (the friendliest default). */
        fun from(name: String?): OGMotionMode = when (name?.trim()?.lowercase()) {
            "loop", "repeat", "restart", "sawtooth" -> LOOP
            "once", "forward", "hold" -> ONCE
            else -> REVERSE
        }
    }
}

/** A `from → to` pair for a scalar channel (scale / rotation degrees / opacity). */
@Serializable
data class OGMotionScalar(val from: Float, val to: Float) {
    fun at(p: Float): Float = from + (to - from) * p
}

/** A `from → to` pair for a 2-D channel (a normalized scene-space translate offset). */
@Serializable
data class OGMotionOffset(val from: OGPointSpec = OGPointSpec(0f, 0f), val to: OGPointSpec = OGPointSpec(0f, 0f)) {
    fun at(p: Float): OGPointSpec = OGPointSpec(from.x + (to.x - from.x) * p, from.y + (to.y - from.y) * p)
}

/** A `from → to` pair for a whole colour grade (an [OGLookSpec] per end). */
@Serializable
data class OGLookMotion(val from: OGLookSpec = OGLookSpec.Identity, val to: OGLookSpec = OGLookSpec.Identity) {
    fun at(p: Float): OGLookSpec = OGLookSpec(
        name = if (to.name.isNotEmpty()) to.name else from.name,
        brightness = lerp(from.brightness, to.brightness, p),
        contrast = lerp(from.contrast, to.contrast, p),
        saturation = lerp(from.saturation, to.saturation, p),
        temperature = lerp(from.temperature, to.temperature, p),
        tint = lerp(from.tint, to.tint, p),
        hue = lerp(from.hue, to.hue, p),
    )
}

/** A `from → to` pair for a colour string (SVG `#RRGGBB` / name); the base coat / sky swing. */
@Serializable
data class OGColorMotion(val from: String, val to: String) {
    /** Blend to a live [Color] at phase [p]; falls back toward whichever end parses. */
    fun at(p: Float): Color {
        val a = from.toSpecColor() ?: to.toSpecColor() ?: Color.Transparent
        val b = to.toSpecColor() ?: a
        return Color(lerp(a.red, b.red, p), lerp(a.green, b.green, p), lerp(a.blue, b.blue, p), lerp(a.alpha, b.alpha, p))
    }
}

// --- Per-region motion ----------------------------------------------------------------------

/**
 * How a single region animates over time: a looping [duration]-second timeline, shaped by an
 * [easing] curve and repeated per [mode], driving any of four optional channels — [translate]
 * (normalized scene-space offset), [scale] (about the region's centroid), [rotate] (degrees about
 * the centroid) and [opacity]. Absent channels stay put. All pure maths, evaluated closed-form at
 * any time t.
 */
@Serializable
data class OGRegionMotion(
    /** Seconds for one play of the timeline (`>0`). */
    val duration: Float = 4f,
    /** `loop` | `reverse` | `once` (aliases accepted). */
    val mode: String = "reverse",
    /** Easing name (`linear`/`easeIn`/`easeOut`/`easeInOut`/`overshoot`/…) — see `ogEasingOf`. */
    val easing: String = "easeInOut",
    /** Seconds to wait before this region starts moving. */
    val delay: Float = 0f,
    /** Normalized scene-space offset added to every vertex. */
    val translate: OGMotionOffset? = null,
    /** Uniform scale about the centroid (`1` = unchanged). */
    val scale: OGMotionScalar? = null,
    /** Rotation in degrees about the centroid. */
    val rotate: OGMotionScalar? = null,
    /** Whole-region alpha `0..1` (overrides the region's static opacity while animating). */
    val opacity: OGMotionScalar? = null,
) {
    /** Clamp the timing knobs into a sane range. */
    fun sanitized(): OGRegionMotion = copy(duration = duration.coerceAtLeast(1e-3f), delay = delay.coerceAtLeast(0f))

    /** Raw timeline position `0..1` at time [t] seconds (before easing), honouring [mode] + [delay]. */
    fun rawPhase(t: Float): Float {
        val d = duration.coerceAtLeast(1e-3f)
        val tt = t - delay
        if (tt <= 0f) return 0f
        return when (OGMotionMode.from(mode)) {
            OGMotionMode.ONCE -> (tt / d).coerceIn(0f, 1f)
            OGMotionMode.LOOP -> (tt % d) / d
            OGMotionMode.REVERSE -> {
                val u = (tt % (2f * d)) / d
                if (u <= 1f) u else 2f - u
            }
        }
    }

    /** Eased timeline position `0..1` at time [t] seconds — the value the channels interpolate on. */
    fun phase(t: Float): Float = ogEasingOf(easing).ease(rawPhase(t))
}

/** This region as it looks at time [t] seconds: vertices scaled/rotated about the centroid then
 *  translated, and opacity resolved. Returns `this` unchanged when it carries no [motion]. */
fun OGPlacedShapeSpec.animatedAt(t: Float): OGPlacedShapeSpec {
    val m = motion ?: return this
    if (points.isEmpty()) return this
    val p = m.phase(t)
    var pts = points
    val sc = m.scale?.at(p)
    val rotDeg = m.rotate?.at(p)
    if (sc != null || rotDeg != null) {
        val cx = points.fold(0f) { a, q -> a + q.x } / points.size
        val cy = points.fold(0f) { a, q -> a + q.y } / points.size
        val s = sc ?: 1f
        val rad = (rotDeg ?: 0f) * (PI_F / 180f)
        val cosA = cos(rad); val sinA = sin(rad)
        pts = pts.map { q ->
            val dx = (q.x - cx) * s; val dy = (q.y - cy) * s
            OGPointSpec(cx + dx * cosA - dy * sinA, cy + dx * sinA + dy * cosA)
        }
    }
    val tr = m.translate?.at(p)
    if (tr != null) pts = pts.map { OGPointSpec(it.x + tr.x, it.y + tr.y) }
    val op = m.opacity?.at(p) ?: opacity
    return copy(points = pts, opacity = op)
}

// --- Scene-level motion ---------------------------------------------------------------------

/**
 * The scene's own timeline: a colour grade ([look]) and/or base coat ([background]) that swing over
 * a [duration]-second loop — the one knob that turns a daylit still into a day→dusk→night cycle. The
 * resolved grade is meant to be applied over the whole frame via `Modifier.ogLook`, exactly as a
 * static [OGSceneSpec.look] would be.
 */
@Serializable
data class OGSceneMotion(
    /** Seconds for one play of the timeline (`>0`). */
    val duration: Float = 8f,
    /** `loop` | `reverse` | `once` (aliases accepted). */
    val mode: String = "reverse",
    /** Easing name — see `ogEasingOf`. */
    val easing: String = "easeInOut",
    /** A whole-frame colour grade that interpolates from→to (apply with `Modifier.ogLook`). */
    val look: OGLookMotion? = null,
    /** The base-coat / sky colour that interpolates from→to. */
    val background: OGColorMotion? = null,
) {
    fun sanitized(): OGSceneMotion = copy(duration = duration.coerceAtLeast(1e-3f))

    private fun rawPhase(t: Float): Float {
        val d = duration.coerceAtLeast(1e-3f)
        if (t <= 0f) return 0f
        return when (OGMotionMode.from(mode)) {
            OGMotionMode.ONCE -> (t / d).coerceIn(0f, 1f)
            OGMotionMode.LOOP -> (t % d) / d
            OGMotionMode.REVERSE -> {
                val u = (t % (2f * d)) / d
                if (u <= 1f) u else 2f - u
            }
        }
    }

    /** Eased timeline position `0..1` at time [t] seconds. */
    fun phase(t: Float): Float = ogEasingOf(easing).ease(rawPhase(t))

    /** The resolved colour grade at time [t], or `null` if this timeline has no [look] channel. */
    fun lookAt(t: Float): OGLookSpec? = look?.at(phase(t))

    /** The resolved base coat at time [t], or `null` if this timeline has no [background] channel. */
    fun backgroundAt(t: Float): Color? = background?.at(phase(t))
}

// --- Whole-scene evaluation -----------------------------------------------------------------

/**
 * A scene frozen at one instant: every region with its motion applied (and scatter already
 * expanded), plus the resolved whole-frame [look] grade and [background] colour. This is what a
 * renderer draws for time t — draw the [scene]'s regions, paint [background] behind them, apply
 * [look] over the frame.
 */
data class OGLiveFrame(
    val scene: OGSceneSpec,
    val look: OGLookSpec?,
    val background: Color?,
)

/**
 * The whole scene evaluated at time [t] seconds — scatter expanded, every region's motion applied,
 * and the scene-level grade/base-coat resolved (falling back to the static [OGSceneSpec.look] /
 * [OGSceneSpec.background] when there's no [motion]).
 *
 * Convenience for simple/export use: it sanitizes + expands every call, so for a 60fps live preview
 * prefer to expand ONCE and call [OGPlacedShapeSpec.animatedAt] only on the regions that move. Pure
 * and deterministic in [t], so a compositor can scrub or render it frame-by-frame.
 */
fun OGSceneSpec.liveAt(t: Float): OGLiveFrame {
    val resolved = sanitized().expanded()
    val moved = resolved.copy(shapes = resolved.shapes.map { it.animatedAt(t) })
    val look = motion?.lookAt(t) ?: this.look
    val bg = motion?.backgroundAt(t) ?: background?.toSpecColor()
    return OGLiveFrame(moved, look, bg)
}

/** True when this scene declares any motion (scene-level or on any region). */
val OGSceneSpec.isLive: Boolean
    get() = motion != null || shapes.any { it.motion != null }

// --- shared helpers -------------------------------------------------------------------------

private const val PI_F: Float = 3.1415927f

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
