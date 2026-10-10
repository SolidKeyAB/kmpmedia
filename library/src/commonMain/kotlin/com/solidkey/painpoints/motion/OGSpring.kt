package com.solidkey.painpoints.motion

import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A **second-order spring** — the core of natural motion *feel*. It is an exact, analytically-solved
 * damped harmonic oscillator (not a Euler approximation), so it is unconditionally stable and
 * deterministic, and it comes in two forms sharing one maths core:
 *
 *  - **Live** — [OGSpringValue], a tiny mutable holder you `update(target, dt)` each frame; it mutates
 *    its value + velocity in place with **no allocation**, so you can run one per joint of a rig, per
 *    particle, or per UI property at 60fps.
 *  - **Scrubbable / exportable** — [OGSpring.valueAt], a **closed-form** `value at time t` for a
 *    `from → to` step. Because it is addressable by `t` (not driven by the live frame clock), it is the
 *    one dynamics form the compositor can **scrub** (`positionMs`) and **sample for GIF / MP4 export**
 *    deterministically — which `androidx.compose.animation.core.spring()` structurally cannot do.
 *
 * Feel is two numbers: [OGSpringSpec.stiffness] (how hard it pulls) and [OGSpringSpec.dampingRatio]
 * (`<1` bouncy / overshooting, `1` critically damped = fastest with no overshoot, `>1` sluggish). Feed
 * any existing target (a keyframe pose, a gesture position) through a spring and it gains lag,
 * overshoot and settle — the difference between a robotic `sin()` joint and one that feels alive.
 *
 * Pure maths, zero dependency, frame-identical on Android & iOS.
 */
@Serializable
data class OGSpringSpec(
    val name: String? = null,
    /** How hard the spring pulls toward the target (natural frequency ω₀ = √stiffness). Higher = faster. */
    val stiffness: Float = 200f,
    /** Damping ratio ζ: `<1` bouncy (overshoots), `1` critically damped (fastest, no overshoot), `>1` sluggish. */
    val dampingRatio: Float = 0.6f,
)

/**
 * A live, mutable spring-driven value. Hold one (remember it in Compose) and call [update] with the
 * current target and frame `dt` each frame; [value] and [velocity] advance toward the target with the
 * spring's feel. Mutates in place — no per-frame allocation. [velocity] is exposed so you can derive
 * squash / lean from how fast it is moving (see [OGSquash]).
 */
class OGSpringValue(var value: Float, var velocity: Float = 0f) {
    /** Advance toward [target] over [dt] seconds with [spec]'s feel. `dt` is clamped so a stalled frame can't explode it. */
    fun update(target: Float, dt: Float, spec: OGSpringSpec) {
        if (dt <= 0f) return
        val w0 = sqrt(spec.stiffness.coerceAtLeast(1e-4f))
        val p = springStep(w0, spec.dampingRatio, value - target, velocity, dt.coerceIn(0f, 0.064f))
        value = target + unpackFirst(p)
        velocity = unpackSecond(p)
    }

    /** Jump to [v] with zero velocity (e.g. to seed the spring before the first frame). */
    fun snapTo(v: Float) {
        value = v
        velocity = 0f
    }
}

/** Closed-form, time-addressable spring evaluation — the scrub / export form. */
object OGSpring {
    /**
     * The spring's value at time [t] (seconds) for a step from [from] to [to] with optional initial
     * [velocity]. Exact (closed-form), so it is deterministic and can be sampled at any `t` — the form
     * the compositor scrubs and the GIF / MP4 exporter samples.
     */
    fun valueAt(spec: OGSpringSpec, t: Float, from: Float, to: Float, velocity: Float = 0f): Float {
        if (t <= 0f) return from
        val w0 = sqrt(spec.stiffness.coerceAtLeast(1e-4f))
        return to + unpackFirst(springStep(w0, spec.dampingRatio, from - to, velocity, t))
    }

    /** The spring's velocity at time [t] for the same `from → to` step (for squash / lean off speed). */
    fun velocityAt(spec: OGSpringSpec, t: Float, from: Float, to: Float, velocity: Float = 0f): Float {
        if (t <= 0f) return velocity
        val w0 = sqrt(spec.stiffness.coerceAtLeast(1e-4f))
        return unpackSecond(springStep(w0, spec.dampingRatio, from - to, velocity, t))
    }
}

// --- shared analytic core -------------------------------------------------------------------------

/**
 * Advance the homogeneous damped-oscillator `y'' + 2ζω₀y' + ω₀²y = 0` from initial displacement [y0]
 * (value − target) and velocity [v0] by time [t], returning the new (displacement, velocity) **packed
 * into a `Long`** (two `Float`s, zero allocation). Exact per case (under / critically / over damped),
 * so it is stable for any `t` and deterministic across platforms. Shared by the live stepper and the
 * closed-form evaluator. `w0` = √stiffness.
 */
internal fun springStep(w0: Float, z: Float, y0: Float, v0: Float, t: Float): Long {
    if (t <= 0f) return packFloats(y0, v0)
    val zc = z.coerceAtLeast(0f)
    return when {
        zc < 1f - 1e-4f -> { // underdamped — oscillates and decays (overshoot)
            val wd = w0 * sqrt(1f - zc * zc)
            val e = exp(-zc * w0 * t)
            val c = cos(wd * t)
            val s = sin(wd * t)
            val b = (v0 + zc * w0 * y0) / wd
            val y = e * (y0 * c + b * s)
            val v = e * ((-zc * w0 * y0 + b * wd) * c + (-zc * w0 * b - y0 * wd) * s)
            packFloats(y, v)
        }
        zc <= 1f + 1e-4f -> { // critically damped — fastest return, no overshoot
            val e = exp(-w0 * t)
            val k = v0 + w0 * y0
            val y = e * (y0 + k * t)
            val v = e * (v0 - w0 * k * t)
            packFloats(y, v)
        }
        else -> { // overdamped — two real decays, no oscillation
            val r = w0 * sqrt(zc * zc - 1f)
            val r1 = -zc * w0 + r
            val r2 = -zc * w0 - r
            val a = (v0 - r2 * y0) / (r1 - r2)
            val bo = y0 - a
            val e1 = exp(r1 * t)
            val e2 = exp(r2 * t)
            val y = a * e1 + bo * e2
            val v = a * r1 * e1 + bo * r2 * e2
            packFloats(y, v)
        }
    }
}

// Pack two Floats into a Long (bit-exact, zero-allocation) and unpack them.
internal fun packFloats(a: Float, b: Float): Long =
    (a.toRawBits().toLong() shl 32) or (b.toRawBits().toLong() and 0xffffffffL)

internal fun unpackFirst(p: Long): Float = Float.fromBits((p ushr 32).toInt())

internal fun unpackSecond(p: Long): Float = Float.fromBits(p.toInt())
