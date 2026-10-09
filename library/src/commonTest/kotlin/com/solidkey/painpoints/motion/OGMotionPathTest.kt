package com.solidkey.painpoints.motion

import com.solidkey.painpoints.shape.OGPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OGMotionPathTest {

    private fun close(a: Float, b: Float, eps: Float = 1e-4f) = kotlin.math.abs(a - b) <= eps
    private fun assertPoint(ex: Float, ey: Float, p: OGPoint, msg: String = "") {
        assertTrue(close(ex, p.x) && close(ey, p.y), "$msg expected ($ex,$ey) got (${p.x},${p.y})")
    }

    @Test
    fun straightLine_endpointsAndMidpoint() {
        val path = OGMotionPath.of(0f to 0f, 1f to 0f)
        assertPoint(0f, 0f, path.pointAt(0f), "start")
        assertPoint(1f, 0f, path.pointAt(1f), "end")
        assertPoint(0.5f, 0f, path.pointAt(0.5f), "mid")
        assertTrue(close(1f, path.length), "length")
    }

    @Test
    fun progress_clampsOutOfRange() {
        val path = OGMotionPath.of(0f to 0f, 1f to 0f)
        assertPoint(0f, 0f, path.pointAt(-5f), "below")
        assertPoint(1f, 0f, path.pointAt(5f), "above")
    }

    @Test
    fun lShape_isArcLengthParameterized_notPerVertex() {
        // (0,0) -> (0,1) -> (1,1): total length 2. Halfway by distance is the corner (0,1), NOT per-vertex.
        val path = OGMotionPath.of(0f to 0f, 0f to 1f, 1f to 1f)
        assertTrue(close(2f, path.length), "length 2")
        assertPoint(0f, 1f, path.pointAt(0.5f), "halfway = corner")
        assertPoint(0f, 0.5f, path.pointAt(0.25f), "quarter down first leg")
        assertPoint(0.5f, 1f, path.pointAt(0.75f), "three-quarter along second leg")
    }

    @Test
    fun tangent_pointsAlongSegments() {
        val path = OGMotionPath.of(0f to 0f, 0f to 1f, 1f to 1f)
        // First leg goes straight down (+y) = 90 degrees; second leg goes +x = 0 degrees.
        assertTrue(close(90f, path.tangentDegAt(0.25f)), "down leg = 90")
        assertTrue(close(0f, path.tangentDegAt(0.75f)), "right leg = 0")
    }

    @Test
    fun trimmed_interpolatesEndsAndKeepsInnerVertices() {
        val path = OGMotionPath.of(0f to 0f, 0f to 1f, 1f to 1f)
        val seg = path.trimmed(0.25f, 0.75f)
        assertEquals(3, seg.size, "start + corner + end")
        assertPoint(0f, 0.5f, seg.first(), "trim start")
        assertPoint(0f, 1f, seg[1], "kept corner")
        assertPoint(0.5f, 1f, seg.last(), "trim end")
    }

    @Test
    fun trimmed_emptyWhenEndNotAfterStart() {
        val path = OGMotionPath.of(0f to 0f, 1f to 1f)
        assertTrue(path.trimmed(0.5f, 0.5f).isEmpty(), "equal")
        assertTrue(path.trimmed(0.8f, 0.2f).isEmpty(), "reversed")
    }

    @Test
    fun degenerate_emptyAndSinglePoint() {
        val empty = OGMotionPath.of(emptyList())
        assertTrue(close(0f, empty.length))
        assertPoint(0f, 0f, empty.pointAt(0.5f), "empty origin")

        val one = OGMotionPath.of(listOf(OGPoint(0.2f, 0.3f)))
        assertTrue(close(0f, one.length))
        assertPoint(0.2f, 0.3f, one.pointAt(0f), "single at 0")
        assertPoint(0.2f, 0.3f, one.pointAt(1f), "single at 1")
    }

    @Test
    fun smoothOpen_startsAndEndsOnVertices_allFinite() {
        val raw = listOf(OGPoint(0f, 0.8f), OGPoint(0.25f, 0.2f), OGPoint(0.5f, 0.8f), OGPoint(1f, 0.2f))
        val path = OGMotionPath.smoothOpen(raw, samplesPerSegment = 8)
        assertTrue(path.points.size > raw.size, "resampled denser")
        assertPoint(0f, 0.8f, path.pointAt(0f), "keeps first")
        assertPoint(1f, 0.2f, path.pointAt(1f), "keeps last")
        assertTrue(path.points.all { it.x.isFinite() && it.y.isFinite() }, "finite")
    }

    @Test
    fun loop_closesBackToStart() {
        val tri = listOf(OGPoint(0.5f, 0f), OGPoint(1f, 1f), OGPoint(0f, 1f))
        val poly = OGMotionPath.loop(tri, smoothing = 0f)
        assertTrue(poly.length > 0f, "has length")
        assertPoint(0.5f, 0f, poly.pointAt(0f), "loop start")
        assertPoint(0.5f, 0f, poly.pointAt(1f), "loop returns to start")

        val smooth = OGMotionPath.loop(tri, smoothing = 1f, samplesPerSegment = 6)
        assertTrue(smooth.points.size > poly.points.size, "smoothed denser")
        assertTrue(smooth.points.all { it.x.isFinite() && it.y.isFinite() }, "finite")
    }

    @Test
    fun trimmed_fullPathKeepsAllVertices() {
        val path = OGMotionPath.of(0f to 0f, 0f to 1f, 1f to 1f)
        val seg = path.trimmed(0f, 1f)
        assertEquals(3, seg.size, "all three vertices")
        assertPoint(0f, 0f, seg.first(), "starts at first")
        assertPoint(1f, 1f, seg.last(), "ends at last")
    }

    @Test
    fun trimmed_degeneratePathIsEmpty_notAFullList() {
        // Regression: coincident points (zero length) must not draw a permanent dot at progress 0.
        val coincident = OGMotionPath.of(listOf(OGPoint(0.4f, 0.4f), OGPoint(0.4f, 0.4f)))
        assertTrue(coincident.trimmed(0f, 1f).isEmpty(), "degenerate trim is empty")
        assertTrue(coincident.trimmed(0f, 0.3f).isEmpty(), "degenerate partial trim is empty")
    }

    @Test
    fun tangent_skipsDuplicateVertexAtStart() {
        // Regression: a duplicated first point is a zero-length segment; the angle must come from the
        // first REAL segment (here straight down = 90), not atan2(0,0) = 0.
        val path = OGMotionPath.of(listOf(OGPoint(0f, 0f), OGPoint(0f, 0f), OGPoint(0f, 1f)))
        assertTrue(close(90f, path.tangentDegAt(0f)), "duplicate start should not snap angle to 0")
    }

    @Test
    fun tangent_isAspectCorrectedWhenScaled() {
        // A 45-degree normalized diagonal in a 2:1-wide box reads shallower on screen (~26.57 degrees).
        val path = OGMotionPath.of(0f to 0f, 1f to 1f)
        assertTrue(close(45f, path.tangentDegAt(0.5f)), "normalized = 45")
        val screen = path.tangentDegAt(0.5f, scaleX = 200f, scaleY = 100f)
        assertTrue(close(26.565f, screen, eps = 0.05f), "aspect-corrected ~26.57, got $screen")
    }

    @Test
    fun nonFinite_progressIsSafe() {
        val path = OGMotionPath.of(0f to 0f, 1f to 0f)
        assertPoint(0f, 0f, path.pointAt(Float.NaN), "NaN progress clamps to start")
        assertTrue(path.tangentDegAt(Float.NaN).isFinite(), "NaN tangent finite")
    }

    @Test
    fun nonFinite_coordinatesAreSanitized() {
        val path = OGMotionPath.of(listOf(OGPoint(0f, 0f), OGPoint(Float.POSITIVE_INFINITY, 0f), OGPoint(1f, 0f)))
        assertTrue(path.length.isFinite(), "length stays finite with an infinite input coord")
        assertTrue(path.points.all { it.x.isFinite() && it.y.isFinite() }, "points sanitized")
    }

    @Test
    fun pointAt_monotonicAlongStraightLine() {
        val path = OGMotionPath.of(0f to 0f, 1f to 1f)
        var prev = -1f
        for (i in 0..10) {
            val x = path.pointAt(i / 10f).x
            assertTrue(x >= prev - 1e-5f, "x should not go backwards")
            prev = x
        }
    }
}
