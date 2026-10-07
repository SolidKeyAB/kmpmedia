package com.solidkey.painpoints.particle

import androidx.compose.ui.graphics.Color
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The **data-defined particle system**: an emitter described as plain, serializable data — rate,
 * lifetime, velocity cone, gravity, size/colour-over-life, shape — that the library compiles once
 * and simulates each frame. It is the generative-motion sibling of
 * [com.solidkey.painpoints.style.OGStyleSpec] (styles) and [com.solidkey.painpoints.shape.OGParametricSpec]
 * (shapes): a designer or a language model authors a `.particles` pack and the lib brings it to life,
 * no code and no rebuild. Decode/encode + ready-made presets live on
 * [com.solidkey.painpoints.particle.OGParticles]; drop it on screen with
 * [com.solidkey.painpoints.particle.OGParticleView].
 *
 * **Coordinates.** The emitter origin [x]/[y] and [spawnRadius] are normalized `0..1` over the view
 * box; sizes are a fraction of the box's shorter side; velocities are normalized units per second.
 * **Angles** are degrees measured clockwise from the +x axis in screen space (y points down), so
 * `0` = right, `90` = down, `270` (or `-90`) = up. [gravityY] `> 0` pulls particles down.
 *
 * **Perf.** The simulation is a fixed-capacity struct-of-arrays ([OGParticleSystem]) — no per-frame
 * allocation, no RNG object churn (a tiny deterministic xorshift), and a hard [maxParticles] cap —
 * so it holds 60fps and, from the same [seed], runs frame-identically on Android and iOS. This is
 * the real-app/60fps perf gate the rest of the library clears.
 */
@kotlinx.serialization.Serializable
data class OGParticleSpec(
    val name: String? = null,
    /** Hard cap on live particles (the perf ceiling). Clamped to `0..10000`. */
    val maxParticles: Int = 200,
    /** Steady emission in particles/second. `0` (with a [burst]) makes a one-shot. */
    val emissionRate: Float = 60f,
    /** Particles emitted in a single puff at the start (and on `reset()`). */
    val burst: Int = 0,
    /** Mean particle lifetime in ms. */
    val lifetimeMs: Float = 1500f,
    /** Lifetime spread as a fraction (`0..1`): `0.3` = ±30%. */
    val lifetimeJitter: Float = 0.3f,
    /** Emitter origin X, normalized `0..1`. */
    val x: Float = 0.5f,
    /** Emitter origin Y, normalized `0..1`. */
    val y: Float = 0.5f,
    /** Spawn within this normalized radius of the origin (`0` = a point emitter). */
    val spawnRadius: Float = 0f,
    /** Emission direction, degrees clockwise from +x (y down): `270` = up. */
    val angleDeg: Float = 270f,
    /** Full width of the emission cone in degrees (`360` = all directions). */
    val spreadDeg: Float = 360f,
    /** Launch speed in normalized units/second. */
    val speed: Float = 0.25f,
    /** Speed spread as a fraction (`0..1`). */
    val speedJitter: Float = 0.4f,
    /** Constant horizontal acceleration (normalized units/s²). */
    val gravityX: Float = 0f,
    /** Constant vertical acceleration; `> 0` pulls down. */
    val gravityY: Float = 0f,
    /** Velocity damping per second (`0` = none; ~`1` = strong air drag). */
    val drag: Float = 0f,
    /** Size at birth, as a fraction of the box's shorter side. */
    val startSize: Float = 0.04f,
    /** Size at death, as a fraction of the box's shorter side. */
    val endSize: Float = 0.04f,
    /** Size spread as a fraction (`0..1`). */
    val sizeJitter: Float = 0.3f,
    /** Colour at birth — any SVG colour string (`#RGB`/`#RRGGBB`/`#AARRGGBB`/`rgb()`/name). */
    val startColor: String = "#FFFFFF",
    /** Colour at death; `null` keeps [startColor]. */
    val endColor: String? = null,
    /** Opacity at birth, `0..1`. */
    val startAlpha: Float = 1f,
    /** Opacity at death, `0..1` (fade-out by default). */
    val endAlpha: Float = 0f,
    /** Rotation speed in degrees/second (for non-circle [shape]s). */
    val spinDeg: Float = 0f,
    /** Spin spread as a fraction (`0..1`). */
    val spinJitter: Float = 1f,
    /** Particle shape: `circle` · `square` · `triangle` · `star`. */
    val shape: String = "circle",
    /** Seed for the deterministic jitter — change it for a different-but-repeatable burst. */
    val seed: Int = 0,
) {
    /** Build the live, reusable simulator for this spec. */
    fun toSystem(): OGParticleSystem = OGParticleSystem(this)
}

/** The particle silhouette, resolved once from [OGParticleSpec.shape]. */
enum class OGParticleShape { CIRCLE, SQUARE, TRIANGLE, STAR, PETAL, TEARDROP }

/**
 * A live, compiled [OGParticleSpec] — a fixed-capacity particle simulation. Build via
 * [OGParticleSpec.toSystem]; call [update] once per frame with the elapsed milliseconds, then read
 * the public arrays `0 until `[count] to draw. Stateful and reusable; [reset] clears it back to the
 * spec's starting burst.
 *
 * Positions ([x]/[y]) are normalized `0..1`; [size] is a fraction of the box's shorter side; [argb]
 * is the straight packed ARGB colour (alpha already folded in); [rotationDeg] is the spin. All are
 * struct-of-arrays so a frame is a tight numeric loop with zero allocation.
 */
class OGParticleSystem(private val spec: OGParticleSpec) {

    private val cap: Int = spec.maxParticles.coerceIn(0, 10_000)

    // ── live state (struct-of-arrays, sized to the cap) ──────────────────────────────────────────
    val x = FloatArray(cap)
    val y = FloatArray(cap)
    private val vx = FloatArray(cap)
    private val vy = FloatArray(cap)
    private val age = FloatArray(cap)
    private val life = FloatArray(cap)
    private val sizeMul = FloatArray(cap)
    private val spin = FloatArray(cap)
    val rotationDeg = FloatArray(cap)

    // ── per-frame render outputs (filled by update(); read 0 until count) ────────────────────────
    val size = FloatArray(cap)
    val argb = IntArray(cap)

    /** Number of currently live particles; the valid prefix of every public array. */
    var count = 0
        private set

    val shape: OGParticleShape = when (spec.shape.lowercase()) {
        "square", "rect" -> OGParticleShape.SQUARE
        "triangle", "tri" -> OGParticleShape.TRIANGLE
        "star" -> OGParticleShape.STAR
        "petal", "leaf" -> OGParticleShape.PETAL
        "teardrop", "drop", "droplet" -> OGParticleShape.TEARDROP
        else -> OGParticleShape.CIRCLE
    }

    // Start/end colour channels (0..1), with the per-end alpha already multiplied in.
    private val cs: FloatArray
    private val ce: FloatArray

    init {
        val start = (spec.startColor.toSpecColor() ?: Color.White)
        val end = (spec.endColor?.toSpecColor() ?: start)
        cs = floatArrayOf(spec.startAlpha.coerceIn(0f, 1f), start.red, start.green, start.blue)
        ce = floatArrayOf(spec.endAlpha.coerceIn(0f, 1f), end.red, end.green, end.blue)
    }

    // Deterministic xorshift32 — seeded, allocation-free, identical on every platform.
    private var rng = 0
    private var emitAccum = 0f
    private var bursted = false

    init { reset() }

    /** Clear all particles and re-arm the opening [OGParticleSpec.burst]. */
    fun reset() {
        count = 0
        emitAccum = 0f
        bursted = false
        rng = (spec.seed * -1640531527) xor 0x9E3779B1.toInt()
        if (rng == 0) rng = 1
    }

    private fun nextUnit(): Float {
        var v = rng
        v = v xor (v shl 13)
        v = v xor (v ushr 17)
        v = v xor (v shl 5)
        rng = v
        return ((v ushr 8) and 0xFFFFFF) / 16_777_216f // [0,1)
    }

    /** Uniform in `[a, b]`. */
    private fun range(a: Float, b: Float): Float = a + (b - a) * nextUnit()

    /** Symmetric jitter multiplier `1 ± j`, clamped non-negative. */
    private fun jitter(j: Float): Float = (1f + j * (nextUnit() * 2f - 1f)).coerceAtLeast(0f)

    private fun spawnOne() {
        if (count >= cap) return
        val i = count
        // Origin: uniform in the spawn disk (sqrt for area-uniformity).
        val rr = spec.spawnRadius * sqrt(nextUnit())
        val ra = range(0f, TWO_PI)
        x[i] = spec.x + rr * cos(ra)
        y[i] = spec.y + rr * sin(ra)
        // Velocity within the emission cone.
        val a = (spec.angleDeg + spec.spreadDeg * (nextUnit() - 0.5f)) * DEG
        val spd = (spec.speed * jitter(spec.speedJitter))
        vx[i] = spd * cos(a)
        vy[i] = spd * sin(a)
        age[i] = 0f
        life[i] = (spec.lifetimeMs * jitter(spec.lifetimeJitter)).coerceAtLeast(1f)
        sizeMul[i] = jitter(spec.sizeJitter)
        spin[i] = spec.spinDeg * (1f + spec.spinJitter * (nextUnit() * 2f - 1f))
        rotationDeg[i] = range(0f, 360f)
        count = i + 1
    }

    private fun copyParticle(from: Int, to: Int) {
        x[to] = x[from]; y[to] = y[from]
        vx[to] = vx[from]; vy[to] = vy[from]
        age[to] = age[from]; life[to] = life[from]
        sizeMul[to] = sizeMul[from]; spin[to] = spin[from]; rotationDeg[to] = rotationDeg[from]
    }

    /** Advance the simulation by [dtMs] milliseconds (clamp big gaps upstream), then refill outputs. */
    fun update(dtMs: Float) {
        if (cap == 0) return
        val dt = (dtMs.coerceAtLeast(0f)) / 1000f

        // Age, integrate, and compact out the dead in one pass.
        var w = 0
        for (r in 0 until count) {
            age[r] += dtMs
            if (age[r] >= life[r]) continue // dead: don't carry it forward
            vx[r] += spec.gravityX * dt
            vy[r] += spec.gravityY * dt
            if (spec.drag > 0f) {
                val damp = (1f - spec.drag * dt).coerceAtLeast(0f)
                vx[r] *= damp; vy[r] *= damp
            }
            x[r] += vx[r] * dt
            y[r] += vy[r] * dt
            rotationDeg[r] += spin[r] * dt
            if (w != r) copyParticle(r, w)
            w++
        }
        count = w

        // Spawn: the one-shot burst, then steady emission (both capped).
        if (!bursted && spec.burst > 0) {
            repeat(spec.burst.coerceAtMost(cap - count)) { spawnOne() }
            bursted = true
        }
        if (spec.emissionRate > 0f) {
            emitAccum += spec.emissionRate * dt
            while (emitAccum >= 1f && count < cap) { spawnOne(); emitAccum -= 1f }
            if (count >= cap) emitAccum = 0f
        }

        // Refill render outputs for every live particle (size/colour over life).
        for (i in 0 until count) {
            val f = (age[i] / life[i]).coerceIn(0f, 1f)
            size[i] = lerp(spec.startSize, spec.endSize, f) * sizeMul[i]
            argb[i] = packArgb(
                lerp(cs[0], ce[0], f),
                lerp(cs[1], ce[1], f),
                lerp(cs[2], ce[2], f),
                lerp(cs[3], ce[3], f),
            )
        }
    }

    private companion object {
        const val DEG = 0.017453292f
        const val TWO_PI = 6.2831855f
        fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
        fun packArgb(a: Float, r: Float, g: Float, b: Float): Int {
            val ai = (a.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val ri = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val gi = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val bi = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
    }
}
