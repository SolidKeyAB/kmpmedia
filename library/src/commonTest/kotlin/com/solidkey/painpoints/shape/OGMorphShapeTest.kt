package com.solidkey.painpoints.shape

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers the pure geometry behind [OGMorphShape] / [ogMorphSequence] — segment selection, point
 * lerp, cyclic rotation and swirl-killing alignment — plus that the `progress <= 0 / >= 1` endpoints
 * pass the raw endpoint outline straight through (the zero-cost fast path). The resample step itself
 * is platform-backed (PathMeasure) and validated by the demo; pinning this math pins the morph.
 */
class OGMorphShapeTest {

    // ---- morphSegment: one 0..1 progress walking a multi-stop chain ----------------------------

    @Test
    fun twoStopsIsASingleSegment() {
        assertEquals(0 to 0f, morphSegment(2, 0f))
        assertEquals(0 to 0.5f, morphSegment(2, 0.5f))
        assertEquals(0 to 1f, morphSegment(2, 1f))
    }

    @Test
    fun threeStopsSplitEvenlyAcrossTwoSegments() {
        assertEquals(0 to 0f, morphSegment(3, 0f))
        assertEquals(0 to 0.5f, morphSegment(3, 0.25f)) // quarter of the way = mid of first segment
        assertEquals(1 to 0f, morphSegment(3, 0.5f))    // exactly on the middle stop
        assertEquals(1 to 0.5f, morphSegment(3, 0.75f))
        assertEquals(1 to 1f, morphSegment(3, 1f))      // last segment, fully at the end stop
    }

    @Test
    fun morphSegmentClampsOutOfRange() {
        assertEquals(0 to 0f, morphSegment(3, -2f))
        assertEquals(1 to 1f, morphSegment(3, 5f))
    }

    // ---- lerpPoints ----------------------------------------------------------------------------

    @Test
    fun lerpPointsInterpolatesEachAxis() {
        val a = listOf(Offset(0f, 0f), Offset(10f, 0f))
        val b = listOf(Offset(0f, 10f), Offset(10f, 20f))
        val mid = lerpPoints(a, b, 0.5f)
        assertEquals(Offset(0f, 5f), mid[0])
        assertEquals(Offset(10f, 10f), mid[1])
    }

    @Test
    fun lerpPointsEndpointsAndClamp() {
        val a = listOf(Offset(0f, 0f))
        val b = listOf(Offset(8f, 4f))
        assertEquals(a, lerpPoints(a, b, 0f))
        assertEquals(a, lerpPoints(a, b, -1f)) // clamps to start
        assertEquals(b, lerpPoints(a, b, 1f))
        assertEquals(b, lerpPoints(a, b, 2f))  // clamps to end
    }

    @Test
    fun lerpPointsUsesTheShorterLength() {
        val a = listOf(Offset(0f, 0f), Offset(1f, 1f), Offset(2f, 2f))
        val b = listOf(Offset(0f, 0f))
        assertEquals(1, lerpPoints(a, b, 0.5f).size)
    }

    // ---- rotatePoints --------------------------------------------------------------------------

    @Test
    fun rotatePointsShiftsCyclically() {
        val pts = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(2f, 0f), Offset(3f, 0f))
        assertEquals(listOf(Offset(1f, 0f), Offset(2f, 0f), Offset(3f, 0f), Offset(0f, 0f)), rotatePoints(pts, 1))
        assertEquals(pts, rotatePoints(pts, 4))          // full turn is identity
        assertEquals(rotatePoints(pts, 3), rotatePoints(pts, -1)) // negatives wrap
        assertTrue(rotatePoints(emptyList(), 2).isEmpty())
    }

    // ---- alignPoints: kill the swirl -----------------------------------------------------------

    private val square = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))

    @Test
    fun alignIdenticalIsUnchanged() {
        assertEquals(square, alignPoints(square, square))
    }

    @Test
    fun alignReindexesARotatedCopyBackToMatch() {
        // `to` starts two corners over; alignment should re-index it to line up with `from` exactly.
        val to = rotatePoints(square, 2)
        assertEquals(square, alignPoints(square, to))
    }

    @Test
    fun alignHandlesReversedWinding() {
        // Opposite winding order still aligns (reversal is tried) — same filled region, no fold.
        val to = square.asReversed().toList()
        assertEquals(square, alignPoints(square, to))
    }

    // ---- ogMorphSequence -----------------------------------------------------------------------

    @Test
    fun sequenceNeedsAtLeastTwoStops() {
        assertFailsWith<IllegalArgumentException> { ogMorphSequence(listOf(CircleShape), 0.5f) }
    }

    @Test
    fun sequencePicksTheActiveAdjacentPair() {
        val a = CircleShape
        val b = DiamondShape()
        val c = TriangleShape(TriangleDirection.UP)
        val shape = ogMorphSequence(listOf(a, b, c), 0.25f) as OGMorphShape
        assertSame(a, shape.from)
        assertSame(b, shape.to)
        assertEquals(0.5f, shape.progress, 0.0001f) // quarter of 2 segments = mid of segment 0
    }

    // ---- endpoint passthrough (no resample) ----------------------------------------------------

    @Test
    fun endpointsPassTheRawOutlineThrough() {
        val density = Density(2f)
        val size = Size(120f, 120f)
        val ltr = LayoutDirection.Ltr
        // Endpoints whose outlines are pure RoundRect/Rect math (no platform Path), so the fast-path
        // branch selection is verifiable on the JVM host too; the branch itself is shape-agnostic.
        val from = CircleShape                  // -> Outline.Rounded
        val to = RoundedCornerShape(0.dp)       // -> Outline.Rectangle

        val atStart = OGMorphShape(from, to, 0f).createOutline(size, ltr, density)
        val atEnd = OGMorphShape(from, to, 1f).createOutline(size, ltr, density)

        // progress<=0 returns exactly the `from` outline; progress>=1 returns exactly the `to` outline.
        assertEquals(from.createOutline(size, ltr, density), atStart)
        assertEquals(to.createOutline(size, ltr, density), atEnd)
        assertTrue(atStart is Outline.Rounded, "progress<=0 should be the raw circle outline")
        assertTrue(atEnd is Outline.Rectangle, "progress>=1 should be the raw rectangle outline")
    }
}
