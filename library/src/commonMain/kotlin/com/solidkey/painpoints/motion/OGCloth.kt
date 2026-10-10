package com.solidkey.painpoints.motion

import com.solidkey.painpoints.fx.OGFxNoise
import com.solidkey.painpoints.shape.OGPoint
import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Where an [OGCloth] sheet is held.
 *
 * The anchor passed to [OGCloth.step] / [OGCloth.reset] is a **segment** `(x0,y0)→(x1,y1)`; which nodes that
 * segment pins depends on this mode (it is the 2-D generalisation of the strip's head+tail):
 * - [NONE] — nothing is pinned: the sheet is *thrown / draped*, held up only by gravity and whatever
 *   [OGCloth.colliders] "poles" it lands on (a tarp over a bar). The segment is only the initial seed edge.
 * - [TOP_EDGE] — the whole top row is pinned along the segment (a curtain on a rod).
 * - [TOP_CORNERS] — only the two top corners are pinned, to the segment ends (a banner strung between two poles).
 * - [LEFT_EDGE] — the whole left column is pinned along the segment (a flag on a vertical pole).
 */
enum class OGClothPinMode { NONE, TOP_EDGE, TOP_CORNERS, LEFT_EDGE }

/**
 * A **capsule collider** — the one and only shape an [OGCloth] sheet drapes over: a line segment
 * `(ax,ay)→(bx,by)` inflated by [radius]. A scaffold pole, a bar, a rod, a table edge are each one capsule;
 * a **circle / ball** is a zero-length capsule (`a == b`); a **floor** is a long flat one. Box colliders are
 * deliberately *not* offered (corners snag in a position-based solver; four capsules approximate one anyway).
 *
 * All fields are `var` and in normalized `0..1` units, so the app can **move a pole between frames** (mutate
 * then [OGCloth.step]); the solver reads them fresh each substep. ([OGCloths.sampleAt] assumes static colliders.)
 */
class OGClothCollider(
    var ax: Float,
    var ay: Float,
    var bx: Float = ax,
    var by: Float = ay,
    var radius: Float = 0.03f,
)

/**
 * The **data form of a 2-D cloth sheet's feel** — plain serializable JSON so the whole primitive is
 * **AI-authorable** like [OGClothSpec] / `OGDynamics` / `OGLooks` (describe the fabric in words, a model returns
 * this JSON — see [OGCloths.clothMeshPrompt]).
 *
 * All positions are **normalized `0..1`** of the target box (the house convention). The grid size
 * ([cols] × [rows]) is fixed when the [OGCloth] is built and is clamped to a sane ceiling; every other field is
 * live (takes effect on the next [OGCloth.step]).
 *
 * @param cols nodes across (2..24). @param rows nodes down (2..24). More = smoother + heavier; ≤ 16×12 recommended.
 * @param width rest width of the sheet (normalized). @param height rest height (normalized).
 * @param gravity downward pull (normalized units/s²); higher drapes harder.
 * @param damping how fast motion bleeds off (per second); higher settles sooner, lower keeps rippling.
 * @param wind prevailing horizontal wind (signed: `+` right, `−` left); gusts ride on top. `0` = calm.
 * @param windAmplitude peak turbulent gust force on top of [wind]; `0` = steady wind only.
 * @param windFrequency how fast the gusts wander (cycles/s of the fractal-noise field).
 * @param windSeed pick a different-but-repeatable gust pattern.
 * @param friction grip against a collider surface, `0` (silk on glass, slides freely) .. `1` (canvas, grips).
 * @param pinMode how the sheet is held (see [OGClothPinMode]).
 */
@Serializable
data class OGClothMeshSpec(
    val name: String? = null,
    val cols: Int = 12,
    val rows: Int = 9,
    val width: Float = 0.5f,
    val height: Float = 0.4f,
    val gravity: Float = 0.9f,
    val damping: Float = 0.9f,
    val wind: Float = 0f,
    val windAmplitude: Float = 0.5f,
    val windFrequency: Float = 0.7f,
    val windSeed: Int = 7,
    val friction: Float = 0.3f,
    val pinMode: OGClothPinMode = OGClothPinMode.TOP_EDGE,
)

/**
 * A **2-D (planar) Verlet cloth sheet** — the drape-and-wrap member of the motion family: a tarp, a curtain, a
 * banner wall or a sail. It is a `cols × rows` grid of point-masses held together by **structural** springs
 * (grid edges) and **shear** springs (both cell diagonals — without them a flat grid collapses to a line), kept
 * inextensible by position-based **distance constraints**, with gravity and ambient wind. Unlike [OGClothStrip]
 * (a 1-D line that *hangs & flutters*), this sheet *drapes & wraps* — and it genuinely **collides** with
 * [colliders] (capsule "poles"), so you can throw it over a scaffold bar and watch it hang down both sides.
 *
 * It is a **planar, side-view** sheet: positions are 2-D only — there are **no normals, no lighting, no
 * tessellation** and no 3-D cloth sim. The library exposes the node positions ([x] / [y] / [point]); the caller
 * draws them (a quad grid, a filled path, whatever). That is what keeps this a media/vector primitive and not a
 * physics engine: there is **no self-collision, no tearing, and no public constraint/solver API** (all
 * deliberately rejected). For arbitrary collision geometry, approximate it with a few [colliders].
 *
 * Like [OGClothStrip] it advances on a **fixed internal substep** (not the raw frame `dt`): position Verlet is
 * only stable and *deterministic* at a constant step, so [step] clamps `dt`, accumulates it and runs whole
 * substeps — which is also what makes it **frame-identical on Android & iOS** and exact for fixed-step export.
 * It is a **live stepper, not closed-form**; to *scrub* it, re-simulate from `0` (deterministic, see
 * [OGCloths.sampleAt]). Pure maths, zero dependency, no per-frame allocation (preallocated `FloatArray`s).
 */
class OGCloth(spec: OGClothMeshSpec) {
    /** The live feel. Grid size is fixed at construction; other fields take effect on the next [step]. */
    var spec: OGClothMeshSpec = spec

    /** Nodes across. */
    val cols: Int = spec.cols.coerceIn(2, MAX_DIM)

    /** Nodes down. */
    val rows: Int = spec.rows.coerceIn(2, MAX_DIM)

    /** Total node count (`cols * rows`). */
    val count: Int = cols * rows

    /** Capsule "poles" the sheet drapes over. App-owned and mutable; the first [MAX_COLLIDERS] are honoured. */
    val colliders: MutableList<OGClothCollider> = mutableListOf()

    private val px = FloatArray(count)
    private val py = FloatArray(count)
    private val ox = FloatArray(count) // previous positions (Verlet's implicit velocity)
    private val oy = FloatArray(count)
    private val pinned = BooleanArray(count)
    private var seeded = false
    private var accum = 0f
    private var simTime = 0f // total simulated seconds (drives the deterministic wind)

    private fun idx(col: Int, row: Int): Int = row * cols + col
    private fun tcol(col: Int): Float = if (cols > 1) col.toFloat() / (cols - 1) else 0f
    private fun trow(row: Int): Float = if (rows > 1) row.toFloat() / (rows - 1) else 0f
    private fun restX(): Float = spec.width / (cols - 1).coerceAtLeast(1)
    private fun restY(): Float = spec.height / (rows - 1).coerceAtLeast(1)

    /**
     * Seed the sheet flat from the anchor segment `(x0,y0)→(x1,y1)` with zero velocity (call on placement /
     * teleport). For the top-held / draped modes the segment is the **top edge** and the sheet hangs down from
     * it; for [OGClothPinMode.LEFT_EDGE] it is the **left edge** and the sheet extends to the right.
     */
    fun reset(x0: Float, y0: Float, x1: Float, y1: Float) {
        val rx = restX()
        val ry = restY()
        if (spec.pinMode == OGClothPinMode.LEFT_EDGE) {
            for (r in 0 until rows) {
                val t = trow(r)
                val lx = lerp(x0, x1, t); val ly = lerp(y0, y1, t)
                for (c in 0 until cols) {
                    val i = idx(c, r)
                    px[i] = lx + c * rx; py[i] = ly
                    ox[i] = px[i]; oy[i] = py[i]
                }
            }
        } else {
            for (c in 0 until cols) {
                val t = tcol(c)
                val tx = lerp(x0, x1, t); val ty = lerp(y0, y1, t)
                for (r in 0 until rows) {
                    val i = idx(c, r)
                    px[i] = tx; py[i] = ty + r * ry
                    ox[i] = px[i]; oy[i] = py[i]
                }
            }
        }
        seeded = true
        accum = 0f
    }

    /**
     * Advance the sheet toward wall-clock [dt], holding whatever [OGClothSpec.pinMode] pins along the segment
     * `(x0,y0)→(x1,y1)`. Runs whole fixed substeps; a stalled frame is clamped so it can't explode. No allocation.
     */
    fun step(dt: Float, x0: Float, y0: Float, x1: Float, y1: Float) {
        if (!seeded) { reset(x0, y0, x1, y1); return }
        if (dt <= 0f) return
        accum += dt.coerceIn(0f, MAX_FRAME)
        var guard = 0
        while (accum >= SUBSTEP && guard < MAX_SUBSTEPS) {
            substep(SUBSTEP, x0, y0, x1, y1)
            accum -= SUBSTEP
            simTime += SUBSTEP
            guard++
        }
        if (guard >= MAX_SUBSTEPS) accum = 0f // drop any backlog a very long stall produced
    }

    private fun substep(h: Float, x0: Float, y0: Float, x1: Float, y1: Float) {
        val s = spec
        updatePinned()
        val drag = 1f - (s.damping * h).coerceIn(0f, 1f)
        val h2 = h * h
        val amp = s.windAmplitude
        val freq = s.windFrequency
        val seed = s.windSeed
        val grav = s.gravity
        val wind = s.wind
        // Integrate every node (pinned ones are overwritten in pin()).
        for (r in 0 until rows) {
            val v = trow(r)
            for (c in 0 until cols) {
                val i = idx(c, r)
                val vx = (px[i] - ox[i]) * drag
                val vy = (py[i] - oy[i]) * drag
                // Wind = steady prevailing push + deterministic fractal gusts that vary across the sheet
                // (over time AND position u,v) so it ripples instead of sliding like a rigid board.
                val gust = if (amp != 0f) OGFxNoise.fbm(simTime * freq + tcol(c) * WIND_SPACE, v * WIND_SPACE, 2, seed) * amp else 0f
                val windForce = wind + gust
                val nx = px[i] + vx + windForce * h2
                val ny = py[i] + vy + grav * h2
                ox[i] = px[i]; oy[i] = py[i]
                px[i] = nx; py[i] = ny
            }
        }
        pin(x0, y0, x1, y1)
        repeat(ITER) {
            solveConstraints()
            resolveColliders()
            pin(x0, y0, x1, y1) // re-pin so held nodes never drift from their anchors
        }
    }

    /** One position-based relaxation pass over the structural (grid) + shear (diagonal) distance constraints. */
    private fun solveConstraints() {
        val rx = restX()
        val ry = restY()
        val rd = sqrt(rx * rx + ry * ry)
        // Structural — horizontal.
        for (r in 0 until rows) {
            for (c in 0 until cols - 1) constrain(idx(c, r), idx(c + 1, r), rx)
        }
        // Structural — vertical.
        for (c in 0 until cols) {
            for (r in 0 until rows - 1) constrain(idx(c, r), idx(c, r + 1), ry)
        }
        // Shear — both diagonals of every cell (keeps the sheet from collapsing to a line).
        for (r in 0 until rows - 1) {
            for (c in 0 until cols - 1) {
                constrain(idx(c, r), idx(c + 1, r + 1), rd)
                constrain(idx(c + 1, r), idx(c, r + 1), rd)
            }
        }
    }

    private fun constrain(a: Int, b: Int, rest: Float) {
        val dx = px[b] - px[a]
        val dy = py[b] - py[a]
        val d = sqrt(dx * dx + dy * dy)
        if (d < 1e-6f) return
        val diff = (d - rest) / d
        val m0 = if (pinned[a]) 0f else 1f
        val m1 = if (pinned[b]) 0f else 1f
        val sum = m0 + m1
        if (sum == 0f) return
        val cx = dx * diff
        val cy = dy * diff
        px[a] += cx * (m0 / sum); py[a] += cy * (m0 / sum)
        px[b] -= cx * (m1 / sum); py[b] -= cy * (m1 / sum)
    }

    /** Push every free node out of each capsule collider (positional projection + tangential friction). */
    private fun resolveColliders() {
        val n = min(colliders.size, MAX_COLLIDERS)
        if (n == 0) return
        val fr = spec.friction.coerceIn(0f, 1f)
        for (k in 0 until n) {
            val col = colliders[k]
            val ex = col.bx - col.ax
            val ey = col.by - col.ay
            val len2 = ex * ex + ey * ey
            val rad = col.radius
            for (i in 0 until count) {
                if (pinned[i]) continue
                // Closest point on the capsule's segment to the node.
                val t = if (len2 < 1e-12f) 0f else (((px[i] - col.ax) * ex + (py[i] - col.ay) * ey) / len2).coerceIn(0f, 1f)
                val qx = col.ax + ex * t
                val qy = col.ay + ey * t
                var dx = px[i] - qx
                var dy = py[i] - qy
                val dist = sqrt(dx * dx + dy * dy)
                if (dist >= rad) continue
                val nx: Float; val ny: Float
                if (dist > 1e-6f) { nx = dx / dist; ny = dy / dist } else { nx = 0f; ny = -1f }
                // Project to the surface.
                px[i] = qx + nx * rad
                py[i] = qy + ny * rad
                // PBD friction: damp the tangential part of the implicit velocity by [friction].
                val vx = px[i] - ox[i]
                val vy = py[i] - oy[i]
                val vn = vx * nx + vy * ny
                val vtx = vx - vn * nx
                val vty = vy - vn * ny
                ox[i] = px[i] - (vn * nx + vtx * (1f - fr))
                oy[i] = py[i] - (vn * ny + vty * (1f - fr))
            }
        }
    }

    private fun updatePinned() {
        for (i in 0 until count) pinned[i] = false
        when (spec.pinMode) {
            OGClothPinMode.NONE -> {}
            OGClothPinMode.TOP_EDGE -> for (c in 0 until cols) pinned[idx(c, 0)] = true
            OGClothPinMode.TOP_CORNERS -> { pinned[idx(0, 0)] = true; pinned[idx(cols - 1, 0)] = true }
            OGClothPinMode.LEFT_EDGE -> for (r in 0 until rows) pinned[idx(0, r)] = true
        }
    }

    private fun pin(x0: Float, y0: Float, x1: Float, y1: Float) {
        when (spec.pinMode) {
            OGClothPinMode.NONE -> {}
            OGClothPinMode.TOP_EDGE -> for (c in 0 until cols) {
                val t = tcol(c); val i = idx(c, 0)
                px[i] = lerp(x0, x1, t); py[i] = lerp(y0, y1, t)
            }
            OGClothPinMode.TOP_CORNERS -> {
                val h = idx(0, 0); px[h] = x0; py[h] = y0
                val e = idx(cols - 1, 0); px[e] = x1; py[e] = y1
            }
            OGClothPinMode.LEFT_EDGE -> for (r in 0 until rows) {
                val t = trow(r); val i = idx(0, r)
                px[i] = lerp(x0, x1, t); py[i] = lerp(y0, y1, t)
            }
        }
    }

    /** X of the node at grid cell ([col], [row]). */
    fun x(col: Int, row: Int): Float = px[idx(col, row)]

    /** Y of the node at grid cell ([col], [row]). */
    fun y(col: Int, row: Int): Float = py[idx(col, row)]

    /** The node at grid cell ([col], [row]) as an [OGPoint]. */
    fun point(col: Int, row: Int): OGPoint = OGPoint(px[idx(col, row)], py[idx(col, row)])

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private companion object {
        const val MAX_DIM = 24           // grid-dimension ceiling (keeps sampleAt re-sim + simplicity honest)
        const val MAX_COLLIDERS = 8      // beyond this you're building a level, not dressing a scene
        const val WIND_SPACE = 3f        // how fast gusts vary across the sheet (the strip's `i*0.25` in 2-D)
        const val SUBSTEP = 1f / 120f    // fixed step — identical to OGClothStrip
        const val MAX_FRAME = 0.064f     // clamp a stalled frame
        const val ITER = 4               // constraint relaxation passes (internal; not a spec knob)
        const val MAX_SUBSTEPS = 16      // backstop so a huge stall can't spiral
    }
}
