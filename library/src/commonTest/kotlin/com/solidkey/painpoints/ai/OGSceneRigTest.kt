package com.solidkey.painpoints.ai

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [OGSceneRig] — the clickable-joints mesh-deform rig. All pure maths, so the correctness
 * properties (rest = identity, partition-of-unity rigid translation, on-joint follow, locality) and a
 * 20k-vertex deform-cost sanity run headless, no Compose.
 */
class OGSceneRigTest {

    private fun close(a: Float, b: Float, eps: Float = 1e-4f) = assertTrue(abs(a - b) < eps, "expected $b, got $a")

    /** A single triangle region. */
    private fun tri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, fill: String? = null, label: String? = null) =
        OGPlacedShapeSpec(listOf(OGPointSpec(ax, ay), OGPointSpec(bx, by), OGPointSpec(cx, cy)), label = label, fill = fill)

    /** A grid mesh of `cols*rows` cells, each split into two triangles → `cols*rows*6` vertices. */
    private fun gridMesh(cols: Int, rows: Int): OGSceneSpec {
        val shapes = ArrayList<OGPlacedShapeSpec>(cols * rows * 2)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x0 = c / cols.toFloat(); val x1 = (c + 1) / cols.toFloat()
                val y0 = r / rows.toFloat(); val y1 = (r + 1) / rows.toFloat()
                shapes.add(tri(x0, y0, x1, y0, x1, y1))
                shapes.add(tri(x0, y0, x1, y1, x0, y1))
            }
        }
        return OGSceneSpec(shapes)
    }

    // --- identity -------------------------------------------------------------------------------

    @Test fun restPoseIsIdentity() {
        val scene = gridMesh(4, 4)
        val joints = OGRig.gridJoints(3, 3)
        val rig = OGRig.bind(scene, joints)
        val posed = rig.deform(joints) // all joints at rest
        assertEquals(rig.restVertices.size, posed.size)
        for (i in posed.indices) {
            close(posed[i].x, rig.restVertices[i].x)
            close(posed[i].y, rig.restVertices[i].y)
        }
    }

    @Test fun noJointsLeavesMeshUntouched() {
        val scene = gridMesh(3, 3)
        val rig = OGRig.bind(scene, emptyList())
        assertEquals(0, rig.jointCount)
        val posed = rig.deform(emptyList())
        for (i in posed.indices) {
            close(posed[i].x, rig.restVertices[i].x)
            close(posed[i].y, rig.restVertices[i].y)
        }
    }

    // --- partition of unity: a uniform joint translation translates the whole mesh rigidly ---------

    @Test fun uniformJointShiftTranslatesWholeMeshRigidly() {
        val scene = gridMesh(5, 5)
        val rest = OGRig.gridJoints(4, 4)
        val rig = OGRig.bind(scene, rest)
        val dx = 0.1f; val dy = -0.05f
        val shifted = rest.map { it.copy(offset = OGPointSpec(dx, dy)) }
        val posed = rig.deform(shifted)
        for (i in posed.indices) {
            close(posed[i].x, rig.restVertices[i].x + dx, 1e-4f)
            close(posed[i].y, rig.restVertices[i].y + dy, 1e-4f)
        }
    }

    // --- a vertex sitting on a joint follows that joint almost fully -------------------------------

    @Test fun vertexOnJointFollowsIt() {
        // One joint sits exactly on the triangle's first vertex.
        val scene = OGSceneSpec(listOf(tri(0.5f, 0.5f, 0.9f, 0.5f, 0.9f, 0.9f)))
        val joints = listOf(
            OGJoint(OGPointSpec(0.5f, 0.5f)),  // on vertex 0
            OGJoint(OGPointSpec(0.1f, 0.1f)),  // far away
            OGJoint(OGPointSpec(0.1f, 0.9f)),
        )
        val rig = OGRig.bind(scene, joints, nearest = 3, falloff = 2f)
        val dragged = joints.toMutableList()
        dragged[0] = joints[0].draggedTo(OGPointSpec(0.7f, 0.5f)) // move the on-vertex joint +0.2 x
        val posed = rig.deform(dragged)
        // vertex 0 should track the joint to within a hair (weight ≈ 1 because its distance is 0).
        close(posed[0].x, 0.7f, 2e-3f)
        close(posed[0].y, 0.5f, 2e-3f)
    }

    // --- locality: a near vertex moves more than a far one ----------------------------------------

    @Test fun dragIsLocal() {
        val scene = gridMesh(6, 6)
        val joints = OGRig.gridJoints(4, 4, margin = 0.1f)
        val rig = OGRig.bind(scene, joints, nearest = 4, falloff = 3f)
        val target = joints[5] // an interior pin
        val dragged = joints.toMutableList()
        dragged[5] = target.draggedTo(OGPointSpec(target.rest.x, target.rest.y + 0.15f))
        val posed = rig.deform(dragged)

        // nearest mesh vertex to the pin vs. the farthest — measure each one's displacement.
        fun disp(idx: Int): Float {
            val a = rig.restVertices[idx]; val b = posed[idx]
            return abs(b.x - a.x) + abs(b.y - a.y)
        }
        var nearIdx = 0; var nearD = Float.MAX_VALUE
        var farIdx = 0; var farD = -1f
        for (i in rig.restVertices.indices) {
            val v = rig.restVertices[i]
            val d = abs(v.x - target.rest.x) + abs(v.y - target.rest.y)
            if (d < nearD) { nearD = d; nearIdx = i }
            if (d > farD) { farD = d; farIdx = i }
        }
        assertTrue(disp(nearIdx) > disp(farIdx), "near vertex should move more than far vertex")
        assertTrue(disp(nearIdx) > 0.01f, "near vertex should actually move")
    }

    // --- hit test ---------------------------------------------------------------------------------

    @Test fun nearestJointPicksWithinRadiusElseMinusOne() {
        val scene = gridMesh(2, 2)
        val joints = OGRig.gridJoints(2, 2, margin = 0.2f) // pins at 0.2 / 0.8
        val rig = OGRig.bind(scene, joints)
        val hit = rig.nearestJoint(joints, OGPointSpec(0.22f, 0.18f), grabRadius = 0.1f)
        assertEquals(0, hit) // closest to the top-left pin (0.2,0.2)
        val miss = rig.nearestJoint(joints, OGPointSpec(0.5f, 0.5f), grabRadius = 0.05f)
        assertEquals(-1, miss) // dead centre, no pin in reach
    }

    @Test fun nearestJointTracksPoseNotRest() {
        val joints = listOf(OGJoint(OGPointSpec(0.2f, 0.2f)), OGJoint(OGPointSpec(0.8f, 0.8f)))
        val scene = gridMesh(2, 2)
        val rig = OGRig.bind(scene, joints)
        val dragged = listOf(joints[0].draggedTo(OGPointSpec(0.5f, 0.5f)), joints[1])
        // The grab point is near where joint 0 was DRAGGED to, not its rest.
        assertEquals(0, rig.nearestJoint(dragged, OGPointSpec(0.5f, 0.5f), grabRadius = 0.1f))
    }

    // --- scene round-trip -------------------------------------------------------------------------

    @Test fun deformScenePreservesStructureAndPaint() {
        val scene = OGSceneSpec(
            listOf(
                tri(0.1f, 0.1f, 0.3f, 0.1f, 0.2f, 0.3f, fill = "#ff0000", label = "a"),
                OGPlacedShapeSpec(
                    listOf(OGPointSpec(0.5f, 0.5f), OGPointSpec(0.7f, 0.5f), OGPointSpec(0.7f, 0.7f), OGPointSpec(0.5f, 0.7f)),
                    label = "b", fill = "#00ff00",
                ),
            ),
        )
        val joints = OGRig.gridJoints(2, 2)
        val rig = OGRig.bind(scene, joints)
        val dragged = joints.toMutableList().also { it[0] = joints[0].draggedTo(OGPointSpec(0.3f, 0.3f)) }
        val out = rig.deformScene(scene, dragged)
        assertEquals(2, out.shapes.size)
        assertEquals(3, out.shapes[0].points.size)
        assertEquals(4, out.shapes[1].points.size)
        assertEquals("a", out.shapes[0].label); assertEquals("#ff0000", out.shapes[0].fill)
        assertEquals("b", out.shapes[1].label); assertEquals("#00ff00", out.shapes[1].fill)
        // the drag actually moved something
        val moved = out.shapes[0].points.zip(scene.shapes[0].points).any { (a, b) -> abs(a.x - b.x) + abs(a.y - b.y) > 1e-4f }
        assertTrue(moved, "deform should have moved region 0")
    }

    // --- joint grid helper ------------------------------------------------------------------------

    @Test fun gridJointsLayout() {
        assertEquals(12, OGRig.gridJoints(4, 3).size)
        val one = OGRig.gridJoints(1, 1)
        assertEquals(1, one.size)
        close(one[0].rest.x, 0.5f); close(one[0].rest.y, 0.5f)
        for (jt in OGRig.gridJoints(5, 5, margin = 0.1f)) {
            assertTrue(jt.rest.x in 0.099f..0.901f && jt.rest.y in 0.099f..0.901f) // epsilon for 0.1+0.8 float rounding
        }
    }

    // --- perf sanity: a ~20k-vertex mesh deforms cleanly, many times -------------------------------

    @Test fun denseMeshDeformsFiniteAndCheap() {
        val scene = gridMesh(58, 58) // 58*58*6 = 20,184 vertices
        val joints = OGRig.gridJoints(4, 4)
        val rig = OGRig.bind(scene, joints, nearest = 4)
        assertTrue(rig.vertexCount >= 20_000, "expected a dense mesh, got ${rig.vertexCount}")

        val ox = FloatArray(rig.vertexCount)
        val oy = FloatArray(rig.vertexCount)
        val mark = kotlin.time.TimeSource.Monotonic.markNow()
        var frames = 0
        repeat(120) { f ->
            val p = (f % 30) / 30f
            val posed = joints.toMutableList()
            posed[5] = joints[5].draggedTo(OGPointSpec(joints[5].rest.x + 0.1f * p, joints[5].rest.y))
            rig.deformInto(posed, ox, oy)
            frames++
        }
        val ms = mark.elapsedNow().inWholeMicroseconds / 1000.0
        // every output finite
        for (i in 0 until rig.vertexCount) assertTrue(ox[i].isFinite() && oy[i].isFinite())
        println("OGSceneRig: ${rig.vertexCount} verts × $frames deforms in ${ms} ms (${ms / frames} ms/frame)")
    }
}
