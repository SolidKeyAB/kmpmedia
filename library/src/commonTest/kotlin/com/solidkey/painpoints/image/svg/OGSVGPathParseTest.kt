package com.solidkey.painpoints.image.svg

import com.solidkey.painpoints.image.loading.ViewBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the path pipeline that the deep SVG pass (05f20f8) fixed:
 *  - [parsePathCommands] normalizes every command to MoveTo / LineTo / CurveTo,
 *    so downstream renderers only ever see those three.
 *  - Smooth-cubic 'S' reflects the previous control point (2·pen − lastControl),
 *    instead of the old bug that fed canvas width/height as a control point.
 *  - Arcs are expanded from the current pen point and end exactly on target.
 *  - [svgArcToBezier] returns a straight-line fallback for a zero radius rather
 *    than crashing, and its final segment lands on the arc endpoint.
 */
class OGSVGPathParseTest {

    private val vb = ViewBox(0f, 0f, 100f, 100f)

    @Test
    fun lineAndClosePathNormalizeToMoveAndLines() {
        val cmds = parsePathCommands("M0 0 L10 0 L10 10 Z", vb)
        assertEquals(4, cmds.size)
        assertEquals(MoveTo(0f, 0f), cmds[0])
        assertEquals(LineTo(10f, 0f), cmds[1])
        assertEquals(LineTo(10f, 10f), cmds[2])
        // Z closes back to the first move point as an explicit line.
        assertEquals(LineTo(0f, 0f), cmds[3])
    }

    @Test
    fun horizontalAndVerticalBecomeLines() {
        val cmds = parsePathCommands("M0 0 H50 V80", vb)
        assertEquals(listOf(MoveTo(0f, 0f), LineTo(50f, 0f), LineTo(50f, 80f)), cmds)
    }

    @Test
    fun smoothCubicReflectsPreviousControlPoint() {
        // After C…(control2 = 20,20) with pen at (30,30), an S's first control
        // point must be the reflection 2*30 - 20 = 40 on both axes.
        val cmds = parsePathCommands("M0 0 C10 10 20 20 30 30 S40 40 50 50", vb)
        assertEquals(3, cmds.size)
        assertEquals(CurveTo(10f, 10f, 20f, 20f, 30f, 30f), cmds[1])
        val s = cmds[2]
        assertTrue(s is CurveTo, "smooth cubic must normalize to CurveTo")
        s as CurveTo
        assertEquals(40f, s.x1, 0.001f)
        assertEquals(40f, s.y1, 0.001f)
        assertEquals(50f, s.x, 0.001f)
        assertEquals(50f, s.y, 0.001f)
    }

    @Test
    fun smoothCubicWithoutPriorCurveUsesPenAsControl() {
        // No previous cubic → reflection falls back to the current pen point.
        val cmds = parsePathCommands("M5 5 S40 40 50 50", vb)
        val s = cmds[1] as CurveTo
        assertEquals(5f, s.x1, 0.001f)
        assertEquals(5f, s.y1, 0.001f)
    }

    @Test
    fun arcNormalizesToCurvesEndingOnTarget() {
        // Quarter-circle arc from (10,0) to (0,10); must expand to CurveTo(s),
        // starting from the pen point and ending exactly on (0,10).
        val cmds = parsePathCommands("M10 0 A10 10 0 0 1 0 10", vb)
        assertTrue(cmds.first() is MoveTo)
        val curves = cmds.drop(1)
        assertTrue(curves.isNotEmpty(), "arc must produce at least one curve")
        assertTrue(curves.all { it is CurveTo }, "arc must normalize to CurveTo only")
        val last = curves.last() as CurveTo
        assertEquals(0f, last.x, 0.5f)
        assertEquals(10f, last.y, 0.5f)
    }

    // ---- svgArcToBezier (pure math) ----

    @Test
    fun zeroRadiusReturnsStraightLineFallback() {
        val segs = svgArcToBezier(
            x0 = 0.0, y0 = 0.0,
            rx = 0.0, ry = 10.0,
            xAxisRotation = 0.0,
            largeArcFlag = 0, sweepFlag = 1,
            x1 = 5.0, y1 = 7.0
        )
        assertEquals(1, segs.size)
        assertEquals(2, segs[0].size, "zero radius yields a single [x,y] line target")
        assertEquals(5.0, segs[0][0], 1e-9)
        assertEquals(7.0, segs[0][1], 1e-9)
    }

    @Test
    fun arcBezierFinalSegmentLandsOnEndpoint() {
        val segs = svgArcToBezier(
            x0 = 10.0, y0 = 0.0,
            rx = 10.0, ry = 10.0,
            xAxisRotation = 0.0,
            largeArcFlag = 0, sweepFlag = 1,
            x1 = 0.0, y1 = 10.0
        )
        assertTrue(segs.isNotEmpty())
        val last = segs.last()
        assertEquals(6, last.size, "a real arc segment is [c1x,c1y,c2x,c2y,endX,endY]")
        assertEquals(0.0, last[4], 0.01)
        assertEquals(10.0, last[5], 0.01)
    }
}
