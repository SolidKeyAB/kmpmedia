package com.solidkey.painpoints.motion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OGMotionsTest {

    @Test
    fun staggerSpec_roundTrips() {
        val spec = OGStaggerSpec(name = "x", stagger = 0.72f, easing = "easeOut", reverse = true)
        val decoded = OGMotions.decodeStaggerSpec(OGMotions.encode(spec))
        assertEquals(spec, decoded)
    }

    @Test
    fun staggerDecode_tolerantAndNullSafe() {
        assertNull(OGMotions.decodeStaggerSpecOrNull("not json at all"))
        // Empty object decodes to all-default spec (lenient), not null.
        val def = OGMotions.decodeStaggerSpecOrNull("{}")
        assertNotNull(def)
        assertEquals(OGStaggerSpec(), def)
    }

    @Test
    fun encode_omitsDefaults() {
        // encodeDefaults=false in the shared config → an all-default spec serializes to "{}".
        assertEquals("{}", OGMotions.encode(OGStaggerSpec()))
    }

    @Test
    fun closedSmoothSpec_buildsAClosedRoundedLoop() {
        val spec = OGMotionPathSpec(points = listOf(0.5f, 0f, 1f, 1f, 0f, 1f), closed = true, smooth = true)
        val path = spec.toPath()
        assertTrue(path.length > 0f)
        val a = path.pointAt(0f)
        val b = path.pointAt(1f)
        assertTrue(kotlin.math.abs(a.x - b.x) <= 1e-4f && kotlin.math.abs(a.y - b.y) <= 1e-4f, "loop closes")
    }

    @Test
    fun twoPointClosedSpec_actuallyCloses() {
        // Regression: a 2-point closed path must link back (pointAt(1) == pointAt(0)), not stay open.
        val path = OGMotions.decodePath("""{"points":[0.0,0.0, 1.0,0.0], "closed":true}""")
        val a = path.pointAt(0f)
        val b = path.pointAt(1f)
        assertTrue(kotlin.math.abs(a.x - b.x) <= 1e-4f && kotlin.math.abs(a.y - b.y) <= 1e-4f, "2-pt loop closes")
    }

    @Test
    fun staggerPresets_lookupIsCaseInsensitive() {
        assertNotNull(OGMotions.staggerPreset("cascade"))
        assertNotNull(OGMotions.staggerPreset("CASCADE"))
        assertNull(OGMotions.staggerPreset("does-not-exist"))
        assertTrue(OGMotions.staggerPresets.containsKey("wave"))
    }

    @Test
    fun pathSpec_roundTrips() {
        val spec = OGMotionPathSpec(points = listOf(0f, 0f, 0.5f, 1f, 1f, 0f), smooth = true, closed = false)
        val decoded = OGMotions.decodePathSpec(OGMotions.encode(spec))
        assertEquals(spec, decoded)
    }

    @Test
    fun pathSpec_toPoints_dropsDanglingValue() {
        val spec = OGMotionPathSpec(points = listOf(0f, 0f, 1f)) // trailing lone value
        val pts = spec.toPoints()
        assertEquals(1, pts.size)
        assertEquals(0f, pts[0].x)
        assertEquals(0f, pts[0].y)
    }

    @Test
    fun decodePath_tolerantOfFencesAndProse() {
        val reply = """
            Sure! Here is the path:
            ```json
            {"points":[0.0, 0.0, 1.0, 0.0]}
            ```
        """.trimIndent()
        val path = OGMotions.decodePath(reply)
        assertTrue(kotlin.math.abs(path.length - 1f) <= 1e-4f, "should be a unit-length horizontal line")
        assertNull(OGMotions.decodePathOrNull("garbage"))
    }

    @Test
    fun decodePath_closedLoopHasLength() {
        val path = OGMotions.decodePath("""{"points":[0.0,0.0, 1.0,0.0, 1.0,1.0], "closed":true}""")
        assertTrue(path.length > 0f)
        // Closed: the end returns to the start point.
        val a = path.pointAt(0f)
        val b = path.pointAt(1f)
        assertTrue(kotlin.math.abs(a.x - b.x) <= 1e-4f && kotlin.math.abs(a.y - b.y) <= 1e-4f, "loop closes")
    }
}
