package com.solidkey.painpoints.ai

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [OGRigSpec] / [OGRigs] — the serializable "scaffold" half of the rig. Pure data + codec,
 * so round-trips, tolerant decode, forward-compatibility (an unknown future field decodes), region
 * snapping, and the [OGRigSpec.bind] glue all run headless with no Compose.
 */
class OGRigSpecTest {

    private fun close(a: Float, b: Float, eps: Float = 1e-4f) = assertTrue(abs(a - b) < eps, "expected $b, got $a")

    /** An axis-aligned rectangle region (centroid = its centre). */
    private fun rect(x: Float, y: Float, w: Float, h: Float, label: String? = null) =
        OGPlacedShapeSpec(
            listOf(OGPointSpec(x, y), OGPointSpec(x + w, y), OGPointSpec(x + w, y + h), OGPointSpec(x, y + h)),
            label = label,
        )

    private val scene = OGSceneSpec(listOf(rect(0.2f, 0.2f, 0.2f, 0.2f, label = "head"), rect(0.4f, 0.6f, 0.3f, 0.2f, label = "body")))

    // --- codec round-trip + wire tolerance -----------------------------------------------------

    @Test fun roundTripPreservesJointsAndKnobs() {
        val spec = OGRigSpec(
            joints = listOf(OGRigJointSpec("jaw", x = 0.5f, y = 0.7f), OGRigJointSpec("ear", region = "head")),
            grabRadius = 0.09f, nearest = 3, falloff = 1.5f, stiffness = 180f, dampingRatio = 0.6f, version = 2,
        )
        val back = OGRigs.decode(OGRigs.encode(spec))
        assertEquals(spec, back)
    }

    @Test fun decodeToleratesProseAndFences() {
        val json = """
            Sure, here's the rig:
            ```json
            { "joints": [ { "id": "jaw", "x": 0.5, "y": 0.72 } ], "grabRadius": 0.1 }
            ```
            Hope that helps!
        """.trimIndent()
        val spec = OGRigs.decode(json)
        assertEquals(1, spec.joints.size)
        assertEquals("jaw", spec.joints[0].id)
        close(spec.joints[0].y, 0.72f)
    }

    /** The forward-compat promise: a future hierarchy upgrade adds `parent` / `angleDeg` to a joint;
     *  a rig authored with those fields must STILL decode on today's codec (unknown keys ignored). */
    @Test fun decodeIgnoresUnknownFutureHierarchyFields() {
        val json = """{ "joints": [ { "id": "hand", "x": 0.3, "y": 0.4, "parent": "shoulder", "angleDeg": 20 } ], "future": true }"""
        val spec = OGRigs.decode(json)
        assertEquals(1, spec.joints.size)
        assertEquals("hand", spec.joints[0].id)
        close(spec.joints[0].x, 0.3f)
    }

    @Test fun missingOptionalFieldsUseDefaults() {
        val spec = OGRigs.decode("""{ "joints": [ { "id": "solo" } ] }""")
        val j = spec.joints.single()
        close(j.x, 0.5f); close(j.y, 0.5f)
        assertNull(j.region)
        close(spec.grabRadius, 0.12f)
        assertEquals(4, spec.nearest)
        close(spec.falloff, 2f)
        close(spec.stiffness, 220f)
        close(spec.dampingRatio, 0.55f)
        assertEquals(1, spec.version)
    }

    @Test fun decodeOrNullReturnsNullOnGarbage() {
        assertNull(OGRigs.decodeOrNull("not json at all, no braces"))
        assertNotNull(OGRigs.decodeOrNull("""{ "joints": [] }"""))
    }

    @Test fun encodeOmitsDefaults() {
        val json = OGRigs.encode(OGRigSpec(joints = listOf(OGRigJointSpec("a"))))
        // defaults (grabRadius, nearest, …) are not emitted; only non-default content shows.
        assertTrue("grabRadius" !in json, "defaults should be omitted: $json")
        assertTrue("\"a\"" in json)
    }

    // --- restJoints resolution -----------------------------------------------------------------

    @Test fun restJointsUsesExplicitXYWhenNoRegion() {
        val spec = OGRigSpec(joints = listOf(OGRigJointSpec("p", x = 0.33f, y = 0.66f)))
        val rest = spec.restJoints(scene)
        close(rest.single().rest.x, 0.33f)
        close(rest.single().rest.y, 0.66f)
    }

    @Test fun restJointsSnapsToRegionCentroidCaseInsensitive() {
        // "HEAD" (uppercase) must still match the "head" region; centroid of that rect is (0.3, 0.3).
        val spec = OGRigSpec(joints = listOf(OGRigJointSpec("ear", x = 0.9f, y = 0.9f, region = "HEAD")))
        val rest = spec.restJoints(scene).single().rest
        close(rest.x, 0.3f); close(rest.y, 0.3f)
    }

    @Test fun restJointsFallsBackToXYWhenRegionUnmatchedOrNoScene() {
        val spec = OGRigSpec(joints = listOf(OGRigJointSpec("ear", x = 0.11f, y = 0.22f, region = "tail")))
        close(spec.restJoints(scene).single().rest.x, 0.11f)   // no "tail" region → explicit xy
        close(spec.restJoints(null).single().rest.y, 0.22f)    // no scene at all → explicit xy
    }

    @Test fun indexOfAddressesJointByName() {
        val spec = OGRigSpec(joints = listOf(OGRigJointSpec("a"), OGRigJointSpec("b"), OGRigJointSpec("c")))
        assertEquals(1, spec.indexOf("b"))
        assertEquals(-1, spec.indexOf("z"))
    }

    // --- bind glue + capture round-trip --------------------------------------------------------

    @Test fun bindMatchesJointCountAndIsIdentityAtRest() {
        val spec = OGRigSpec(joints = listOf(OGRigJointSpec("head", region = "head"), OGRigJointSpec("body", region = "body")))
        val rest = spec.restJoints(scene)
        val rig = spec.bind(scene)
        assertEquals(2, rig.jointCount)
        val posed = rig.deform(rest) // all at home
        assertEquals(rig.restVertices.size, posed.size)
        for (i in posed.indices) { close(posed[i].x, rig.restVertices[i].x); close(posed[i].y, rig.restVertices[i].y) }
    }

    @Test fun bindDeformsWhenAJointIsPosed() {
        val spec = OGRigSpec(joints = listOf(OGRigJointSpec("head", region = "head"), OGRigJointSpec("body", region = "body")))
        val rest = spec.restJoints(scene)
        val rig = spec.bind(scene)
        val poked = rest.toMutableList().also { it[0] = it[0].draggedTo(OGPointSpec(it[0].rest.x + 0.2f, it[0].rest.y)) }
        val moved = rig.deform(poked)
        val baseline = rig.deform(rest)
        assertTrue(moved.indices.any { abs(moved[it].x - baseline[it].x) > 1e-3f }, "posing a joint should move some vertices")
    }

    @Test fun fromJointsRoundTripsRestPositionsThroughTheSpec() {
        val joints = OGRig.sceneJoints(scene)
        assertTrue(joints.isNotEmpty())
        val rebuilt = OGRigSpec.fromJoints(joints).restJoints() // no scene: positions come straight from x/y
        assertEquals(joints.size, rebuilt.size)
        for (i in joints.indices) { close(rebuilt[i].rest.x, joints[i].rest.x); close(rebuilt[i].rest.y, joints[i].rest.y) }
    }
}
