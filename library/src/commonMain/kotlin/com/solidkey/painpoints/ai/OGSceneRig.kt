package com.solidkey.painpoints.ai

import kotlin.math.sqrt

// ============================================================================================
// "clickable joints" — a deform RIG for a dense scene mesh. Everything else in the scene pipeline
// caches: rasterise the mesh ONCE, then only transform/grade the cached layer per frame (that's how
// VectorizedScreen holds 60fps on a 20k-triangle lion). That trick cannot bend the *interior* of the
// mesh — scale/rotate move a whole region rigidly, never a few thousand vertices independently. A rig
// is the one case that forces a genuine per-frame re-tessellate: grab an interior joint, drag it, and
// the mesh warps around your finger.
//
// The maths is linear-blend skinning (LBS) with pure-translation bones — the simplest, collapse-free
// flavour. Bind ONCE: every vertex gets a fixed weight to its K nearest joints (distance falloff,
// normalised to sum to 1). Pose: each joint carries a live [OGJoint.offset] (zero at rest). Deform:
//     vertex' = vertexRest + Σ_j  weight[v][j] · offset[j]
// a handful of multiply-adds per vertex, O(vertices × K) per frame, with the expensive binding never
// repeated. Because the weights are a partition of unity (sum to 1), translating every joint by the
// same vector translates the whole mesh rigidly — no shear, no collapse.
//
// Pure data + math, no Compose, no clock, no per-frame allocation in [deformInto] — so it unit-tests
// headless and runs frame-identically on Android & iOS, exactly like the rest of the scene codec.
// ============================================================================================

/**
 * One control point ("pin") of an [OGRig]: a [rest] position in normalized `0..1` scene space and a
 * live [offset] applied by a drag ([offset] is `(0,0)` at rest). Its drawn/grabbed position is [pose].
 */
data class OGJoint(
    /** Where the pin sits on the undeformed mesh (normalized `0..1`). */
    val rest: OGPointSpec,
    /** Live displacement from [rest] (normalized), set while dragging; `(0,0)` = home. */
    val offset: OGPointSpec = OGPointSpec(0f, 0f),
) {
    /** The pin's current position = [rest] + [offset]. This is what you draw and hit-test against. */
    val pose: OGPointSpec get() = OGPointSpec(rest.x + offset.x, rest.y + offset.y)

    /** This joint with its [pose] dragged to [target] (recomputes [offset] from [rest]). */
    fun draggedTo(target: OGPointSpec): OGJoint = copy(offset = OGPointSpec(target.x - rest.x, target.y - rest.y))

    /** This joint back home ([offset] = `(0,0)`). */
    fun relaxed(): OGJoint = copy(offset = OGPointSpec(0f, 0f))
}

/**
 * A linear-blend-skinning rig **bound once** to a mesh (the flattened vertices of an [OGSceneSpec]'s
 * regions). It stores, per vertex, the fixed weight to each of its [k] nearest joints; posing the
 * joints then deforms the mesh with [deform] / [deformScene] / [deformInto].
 *
 * Build it with [bind]. Hit-test a touch with [nearestJoint]. The binding is the only heavy step and
 * it never repeats; a deform is `vertices × k` multiply-adds.
 */
class OGRig private constructor(
    /** Mesh vertices at rest, flattened across all regions in order. */
    val restVertices: List<OGPointSpec>,
    /** Vertices-per-region, so a flat result can be re-split back into [OGSceneSpec] regions. */
    private val regionSizes: IntArray,
    /** Rest X/Y as flat arrays (hot-loop friendly; parallel to [restVertices]). */
    private val rx: FloatArray,
    private val ry: FloatArray,
    /** For each vertex, the indices of its [k] influencing joints (row-major `v*k + i`). */
    private val boneIndex: IntArray,
    /** For each vertex, the matching weights (row-major `v*k + i`), summing to 1 per vertex. */
    private val boneWeight: FloatArray,
    /** Influences retained per vertex. */
    val k: Int,
    /** Number of joints this rig was bound to. */
    val jointCount: Int,
) {
    /** Total mesh vertices across all regions. */
    val vertexCount: Int get() = rx.size

    /**
     * Deform the flat vertex set for [joints] into caller-owned [outX]/[outY] (each ≥ [vertexCount]).
     * **Zero allocation** — the 60fps hot path. [joints] must be the same list length the rig was
     * bound to; extra joints are ignored, missing ones treated as home.
     */
    fun deformInto(joints: List<OGJoint>, outX: FloatArray, outY: FloatArray) {
        val n = vertexCount
        // Snapshot the joint offsets once (avoid a List get() per influence per vertex).
        val jox = FloatArray(jointCount)
        val joy = FloatArray(jointCount)
        val m = if (joints.size < jointCount) joints.size else jointCount
        for (j in 0 until m) { jox[j] = joints[j].offset.x; joy[j] = joints[j].offset.y }
        var v = 0
        while (v < n) {
            var dx = 0f
            var dy = 0f
            val base = v * k
            var i = 0
            while (i < k) {
                val b = boneIndex[base + i]
                if (b >= 0) {
                    val w = boneWeight[base + i]
                    dx += w * jox[b]
                    dy += w * joy[b]
                }
                i++
            }
            outX[v] = rx[v] + dx
            outY[v] = ry[v] + dy
            v++
        }
    }

    /** Deform into a fresh list of posed vertices (allocating; for tests/export, not the 60fps path). */
    fun deform(joints: List<OGJoint>): List<OGPointSpec> {
        val ox = FloatArray(vertexCount)
        val oy = FloatArray(vertexCount)
        deformInto(joints, ox, oy)
        return List(vertexCount) { OGPointSpec(ox[it], oy[it]) }
    }

    /**
     * Deform [scene] for [joints], returning a copy whose regions carry the posed vertices (paint,
     * labels, motion and ordering preserved). The scene MUST be the one this rig was [bind]ed to (same
     * region vertex counts). Allocates one scene; fine for an export frame — for live preview prefer
     * [deformInto] and rebuild your draw paths from the flat arrays.
     */
    fun deformScene(scene: OGSceneSpec, joints: List<OGJoint>): OGSceneSpec {
        val posed = deform(joints)
        var cursor = 0
        val shapes = ArrayList<OGPlacedShapeSpec>(scene.shapes.size)
        for ((idx, s) in scene.shapes.withIndex()) {
            val sz = if (idx < regionSizes.size) regionSizes[idx] else s.points.size
            if (sz <= 0 || cursor + sz > posed.size) { shapes.add(s); cursor += sz; continue }
            shapes.add(s.copy(points = posed.subList(cursor, cursor + sz).toList()))
            cursor += sz
        }
        return scene.copy(shapes = shapes)
    }

    /**
     * The index of the joint whose [OGJoint.pose] is closest to [at] within [grabRadius] (normalized
     * distance), or `-1` if none is in reach. Use on touch-down to pick the pin under the finger.
     */
    fun nearestJoint(joints: List<OGJoint>, at: OGPointSpec, grabRadius: Float): Int {
        var best = -1
        var bestD2 = grabRadius * grabRadius
        for (i in joints.indices) {
            val p = joints[i].pose
            val dx = p.x - at.x
            val dy = p.y - at.y
            val d2 = dx * dx + dy * dy
            if (d2 <= bestD2) { bestD2 = d2; best = i }
        }
        return best
    }

    companion object {
        /**
         * Bind a rig: weight every vertex of [scene]'s regions to its [nearest] closest [joints] by a
         * `1 / (distance^falloff)` falloff, normalised to a partition of unity. Heavy (O(vertices ×
         * joints)) but done ONCE. [nearest] is clamped to `1..joints.size`; [falloff] controls how
         * local a drag feels (higher = tighter bulge). A vertex sitting on a joint follows it fully.
         */
        fun bind(
            scene: OGSceneSpec,
            joints: List<OGJoint>,
            nearest: Int = 4,
            falloff: Float = 2f,
        ): OGRig {
            val verts = ArrayList<OGPointSpec>()
            val sizes = IntArray(scene.shapes.size)
            for ((idx, s) in scene.shapes.withIndex()) {
                sizes[idx] = s.points.size
                verts.addAll(s.points)
            }
            val n = verts.size
            val j = joints.size
            val kk = if (j <= 0) 0 else nearest.coerceIn(1, j)
            val rx = FloatArray(n)
            val ry = FloatArray(n)
            for (v in 0 until n) { rx[v] = verts[v].x; ry[v] = verts[v].y }

            val boneIndex = IntArray(n * kk) { -1 }
            val boneWeight = FloatArray(n * kk)
            if (kk == 0) {
                return OGRig(verts, sizes, rx, ry, boneIndex, boneWeight, 0, 0)
            }

            // Scratch reused across vertices (single-threaded bind, so safe to share).
            val d2 = FloatArray(j)
            val pickIdx = IntArray(kk)
            val pickW = FloatArray(kk)
            val eps = 1e-6f
            // falloff acts on distance: weight = 1 / distance^falloff = 1 / (d2)^(falloff/2).
            val halfFalloff = falloff * 0.5f

            for (v in 0 until n) {
                val px = rx[v]
                val py = ry[v]
                for (ji in 0 until j) {
                    val p = joints[ji].rest
                    val dx = p.x - px
                    val dy = p.y - py
                    d2[ji] = dx * dx + dy * dy
                }
                // Select the kk smallest distances (simple partial selection; kk and j are both small).
                for (slot in 0 until kk) {
                    var bestJ = -1
                    var bestD = Float.MAX_VALUE
                    for (ji in 0 until j) {
                        val dj = d2[ji]
                        if (dj < bestD) {
                            // skip already-picked joints
                            var taken = false
                            for (s2 in 0 until slot) if (pickIdx[s2] == ji) { taken = true; break }
                            if (!taken) { bestD = dj; bestJ = ji }
                        }
                    }
                    pickIdx[slot] = bestJ
                    // weight ∝ 1 / d^falloff, with eps so a vertex on a joint gets a huge (→ dominant) weight.
                    pickW[slot] = 1f / (pow(bestD, halfFalloff) + eps)
                }
                var sum = 0f
                for (s2 in 0 until kk) sum += pickW[s2]
                val inv = if (sum > 0f) 1f / sum else 0f
                val base = v * kk
                for (s2 in 0 until kk) {
                    boneIndex[base + s2] = pickIdx[s2]
                    boneWeight[base + s2] = pickW[s2] * inv
                }
            }
            return OGRig(verts, sizes, rx, ry, boneIndex, boneWeight, kk, j)
        }

        /**
         * A convenience [cols]×[rows] grid of joints spanning `[margin, 1-margin]` in both axes — the
         * generic "pin board" over a scene when you have no semantic landmarks. Row-major (top-left
         * first). For `cols=rows=1` returns a single centre pin.
         */
        fun gridJoints(cols: Int, rows: Int, margin: Float = 0.12f): List<OGJoint> {
            val c = cols.coerceAtLeast(1)
            val r = rows.coerceAtLeast(1)
            val lo = margin.coerceIn(0f, 0.49f)
            val span = 1f - 2f * lo
            val out = ArrayList<OGJoint>(c * r)
            for (iy in 0 until r) {
                val fy = if (r == 1) 0.5f else lo + span * (iy / (r - 1f))
                for (ix in 0 until c) {
                    val fx = if (c == 1) 0.5f else lo + span * (ix / (c - 1f))
                    out.add(OGJoint(OGPointSpec(fx, fy)))
                }
            }
            return out
        }
    }
}

/** `base^exp` for non-negative [base] (our distances²), avoiding a kotlin.math.pow import churn and
 *  short-circuiting the common `exp == 1f` (falloff 2) case to a plain `sqrt`-free multiply path. */
private fun pow(base: Float, exp: Float): Float {
    if (base <= 0f) return 0f
    if (exp == 1f) return base          // falloff 2 → distance² directly
    if (exp == 0.5f) return sqrt(base)  // falloff 1 → distance
    // general case: a^b = exp(b·ln a)
    return kotlin.math.exp(exp * kotlin.math.ln(base))
}
