package com.solidkey.painpoints.motion

import com.solidkey.painpoints.shape.OGPoint
import kotlin.math.sqrt

/**
 * **Follow-through / secondary motion** — a chain of [count] points that trail a leader, each one
 * spring-following the one before it. This is the single most visible "alive" cue on a rig: hair, a
 * tail, a cape, a scarf, or a trailing forearm that lags the body and settles after it stops. Drive
 * the head of the chain with [update] (the leader position each frame) and read the lagged links with
 * [x] / [y] / [point].
 *
 * It is [count] independent [OGSpring]s applied in series (link `i` springs toward link `i-1`), so it
 * inherits the spring's feel from [spec] — a bouncier spring gives whippier hair, a stiffer one a
 * tighter follow. State lives in preallocated `FloatArray`s and advances in place, so a whole chain is
 * allocation-free per frame. Pure maths, deterministic, frame-identical on Android & iOS.
 */
class OGFollowChain(val count: Int, var spec: OGSpringSpec = OGSpringSpec()) {
    private val px = FloatArray(count.coerceAtLeast(0))
    private val py = FloatArray(count.coerceAtLeast(0))
    private val vx = FloatArray(count.coerceAtLeast(0))
    private val vy = FloatArray(count.coerceAtLeast(0))
    private var seeded = false

    /** Snap every link onto ([leaderX], [leaderY]) with zero velocity (call on first placement / teleport). */
    fun reset(leaderX: Float, leaderY: Float) {
        for (i in 0 until count) {
            px[i] = leaderX; py[i] = leaderY; vx[i] = 0f; vy[i] = 0f
        }
        seeded = true
    }

    /** Advance the chain one frame so link 0 springs toward ([leaderX], [leaderY]) and each later link toward the previous. */
    fun update(leaderX: Float, leaderY: Float, dt: Float) {
        if (count == 0) return
        if (!seeded) { reset(leaderX, leaderY); return }
        if (dt <= 0f) return
        val w0 = sqrt(spec.stiffness.coerceAtLeast(1e-4f))
        val z = spec.dampingRatio
        val step = dt.coerceIn(0f, 0.064f)
        var tx = leaderX
        var ty = leaderY
        for (i in 0 until count) {
            val sx = springStep(w0, z, px[i] - tx, vx[i], step)
            val sy = springStep(w0, z, py[i] - ty, vy[i], step)
            px[i] = tx + unpackFirst(sx); vx[i] = unpackSecond(sx)
            py[i] = ty + unpackFirst(sy); vy[i] = unpackSecond(sy)
            tx = px[i]; ty = py[i] // the next link follows this one
        }
    }

    /** X of link [i] (0 = closest to the leader). */
    fun x(i: Int): Float = px[i]

    /** Y of link [i]. */
    fun y(i: Int): Float = py[i]

    /** Link [i] as an [OGPoint]. */
    fun point(i: Int): OGPoint = OGPoint(px[i], py[i])
}
