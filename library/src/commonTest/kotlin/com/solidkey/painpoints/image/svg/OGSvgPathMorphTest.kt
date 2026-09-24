package com.solidkey.painpoints.image.svg

import androidx.compose.ui.graphics.Color
import com.solidkey.painpoints.image.OGColor
import com.solidkey.painpoints.image.loading.ViewBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers path morphing: [interpolateCommands] (the pure per-frame tween), [structurallyMorphable]
 * (the match guard), [applyMorphOverride] (wiring into a `<path>` node), and [parsePathCached]
 * (the parse-once memo that keeps morphing 60fps-cheap). All off-device, pure logic.
 */
class OGSvgPathMorphTest {

    private val vb = ViewBox(0f, 0f, 100f, 100f)
    private val style = OGSVGStyle(fill = OGColor(Color.Red))

    // ---- hasMorph flag ----

    @Test
    fun hasMorph_detectsPathDataTo() {
        assertFalse(OGSvgNodeOverride().hasMorph)
        assertTrue(OGSvgNodeOverride(pathDataTo = "M0 0 L1 1").hasMorph)
        // progress alone (no target) is not a morph, and morph is orthogonal to paint/transform.
        assertFalse(OGSvgNodeOverride(morphProgress = 0.5f).hasMorph)
        assertFalse(OGSvgNodeOverride(pathDataTo = "M0 0").hasPaint)
        assertFalse(OGSvgNodeOverride(pathDataTo = "M0 0").hasTransform)
    }

    // ---- interpolateCommands: pure per-frame math ----

    @Test
    fun interpolate_midpoint_lerpsLineCoordinates() {
        val from = listOf<OGSVGCommand>(MoveTo(0f, 0f), LineTo(10f, 20f))
        val to = listOf<OGSVGCommand>(MoveTo(0f, 0f), LineTo(30f, 60f))
        val mid = interpolateCommands(from, to, 0.5f)
        assertEquals(MoveTo(0f, 0f), mid[0])
        assertEquals(LineTo(20f, 40f), mid[1])
    }

    @Test
    fun interpolate_curve_lerpsAllControlPoints() {
        val from = listOf<OGSVGCommand>(CurveTo(0f, 0f, 0f, 0f, 0f, 0f))
        val to = listOf<OGSVGCommand>(CurveTo(10f, 20f, 30f, 40f, 50f, 60f))
        val mid = interpolateCommands(from, to, 0.5f)
        assertEquals(CurveTo(5f, 10f, 15f, 20f, 25f, 30f), mid[0])
    }

    @Test
    fun interpolate_endpoints_returnInputInstances_andClamp() {
        val from = listOf<OGSVGCommand>(MoveTo(1f, 2f))
        val to = listOf<OGSVGCommand>(MoveTo(3f, 4f))
        assertSame(from, interpolateCommands(from, to, 0f))
        assertSame(to, interpolateCommands(from, to, 1f))
        // out-of-range progress is clamped rather than extrapolated.
        assertSame(from, interpolateCommands(from, to, -0.5f))
        assertSame(to, interpolateCommands(from, to, 2f))
    }

    @Test
    fun interpolate_doesNotMutateInputs() {
        val from = listOf<OGSVGCommand>(LineTo(0f, 0f))
        val to = listOf<OGSVGCommand>(LineTo(100f, 100f))
        interpolateCommands(from, to, 0.5f)
        assertEquals(LineTo(0f, 0f), from[0])
        assertEquals(LineTo(100f, 100f), to[0])
    }

    // ---- structural mismatch → graceful snap (no crash, no garbage) ----

    @Test
    fun interpolate_differentLength_snapsAtHalfway() {
        val from = listOf<OGSVGCommand>(MoveTo(0f, 0f), LineTo(1f, 1f))
        val to = listOf<OGSVGCommand>(MoveTo(0f, 0f))
        assertFalse(structurallyMorphable(from, to))
        assertSame(from, interpolateCommands(from, to, 0.3f)) // t < 0.5 → start
        assertSame(to, interpolateCommands(from, to, 0.7f))   // t >= 0.5 → target
    }

    @Test
    fun interpolate_differentTypes_snapsAtHalfway() {
        val from = listOf<OGSVGCommand>(MoveTo(0f, 0f), LineTo(1f, 1f))
        val to = listOf<OGSVGCommand>(MoveTo(0f, 0f), CurveTo(1f, 1f, 2f, 2f, 3f, 3f))
        assertFalse(structurallyMorphable(from, to))
        assertSame(from, interpolateCommands(from, to, 0.4f))
        assertSame(to, interpolateCommands(from, to, 0.6f))
    }

    @Test
    fun structurallyMorphable_true_forSameStructure() {
        val a = listOf<OGSVGCommand>(MoveTo(0f, 0f), LineTo(1f, 1f), ClosePath())
        val b = listOf<OGSVGCommand>(MoveTo(5f, 5f), LineTo(2f, 2f), ClosePath())
        assertTrue(structurallyMorphable(a, b))
    }

    // ---- applyMorphOverride ----

    private fun pathNode(
        id: String? = "p1",
        d: String = "M0 0 L10 0 L10 10 Z",
        s: OGSVGStyle = style,
    ) = OGSVGTreeElement(
        tagName = "path",
        attributes = mutableMapOf("d" to d),
        style = s,
        id = id,
    ).also { it.shapes.add(OGSVGPath(parsePathCommands(d, vb, s))) }

    @Test
    fun morphOverride_nullOrNoTarget_returnsSameInstance() {
        val n = pathNode()
        assertSame(n, applyMorphOverride(n, null, vb))
        assertSame(n, applyMorphOverride(n, OGSvgNodeOverride(fill = Color.Green), vb))
    }

    @Test
    fun morphOverride_progressZero_returnsSameInstance() {
        // Target set but progress 0 → still the start geometry; no work, no allocation.
        val n = pathNode()
        val ov = OGSvgNodeOverride(pathDataTo = "M0 0 L20 0 L20 20 Z", morphProgress = 0f)
        assertSame(n, applyMorphOverride(n, ov, vb))
    }

    @Test
    fun morphOverride_progressOne_equalsParsedTarget() {
        val n = pathNode(d = "M0 0 L10 0 L10 10 Z")
        val targetD = "M0 0 L20 0 L20 20 Z"
        val out = applyMorphOverride(n, OGSvgNodeOverride(pathDataTo = targetD, morphProgress = 1f), vb)
        assertNotSame(n, out)
        val outCmds = (out.shapes.single() as OGSVGPath).commands
        assertEquals(parsePathCommands(targetD, vb, style), outCmds)
    }

    @Test
    fun morphOverride_midpoint_tweensGeometry() {
        val fromD = "M0 0 L10 0 L10 10 Z"
        val toD = "M0 0 L30 0 L30 30 Z"
        val n = pathNode(d = fromD)
        val out = applyMorphOverride(n, OGSvgNodeOverride(pathDataTo = toD, morphProgress = 0.5f), vb)
        val outCmds = (out.shapes.single() as OGSVGPath).commands
        val expected = interpolateCommands(
            parsePathCommands(fromD, vb, style),
            parsePathCommands(toD, vb, style),
            0.5f,
        )
        assertEquals(expected, outCmds)
    }

    @Test
    fun morphOverride_doesNotMutateSourceTree() {
        val n = pathNode(d = "M0 0 L10 0 L10 10 Z")
        val originalCmds = (n.shapes.single() as OGSVGPath).commands
        applyMorphOverride(n, OGSvgNodeOverride(pathDataTo = "M0 0 L99 0 L99 99 Z", morphProgress = 0.5f), vb)
        assertEquals(originalCmds, (n.shapes.single() as OGSVGPath).commands)
    }

    @Test
    fun morphOverride_onNonPathNode_isNoOp() {
        val circle = OGSVGTreeElement(
            tagName = "circle", attributes = mutableMapOf(), style = OGSVGStyle(), id = "c1",
        )
        val ov = OGSvgNodeOverride(pathDataTo = "M0 0 L5 5", morphProgress = 0.5f)
        assertSame(circle, applyMorphOverride(circle, ov, vb))
    }

    @Test
    fun morphOverride_startsFromPathDataOverride_notOriginal() {
        // The full pipeline: pathData sets the "from", pathDataTo the "to". Morph must tween
        // between THOSE, not the node's original geometry.
        val n = pathNode(d = "M0 0 L1 0 L1 1 Z") // original geometry, should be ignored
        val ov = OGSvgNodeOverride(
            pathData = "M0 0 L10 0 L10 10 Z",
            pathDataTo = "M0 0 L30 0 L30 30 Z",
            morphProgress = 0.5f,
        )
        val out = applyMorphOverride(applyPathOverride(n, ov, vb), ov, vb)
        val outCmds = (out.shapes.single() as OGSVGPath).commands
        val expected = interpolateCommands(
            parsePathCommands("M0 0 L10 0 L10 10 Z", vb, style),
            parsePathCommands("M0 0 L30 0 L30 30 Z", vb, style),
            0.5f,
        )
        assertEquals(expected, outCmds)
    }

    // ---- parsePathCached: parse-once memoisation ----

    @Test
    fun parsePathCached_reusesParseForSameKey() {
        val cache = mutableMapOf<String, List<OGSVGCommand>>()
        val a = parsePathCached("M0 0 L10 10", vb, style, cache)
        val b = parsePathCached("M0 0 L10 10", vb, style, cache)
        assertSame(a, b) // second call is a cache hit — the same parsed list instance
        assertEquals(1, cache.size)
    }

    @Test
    fun parsePathCached_noCache_parsesEachTime() {
        val a = parsePathCached("M0 0 L10 10", vb, style, null)
        val b = parsePathCached("M0 0 L10 10", vb, style, null)
        assertEquals(a, b)       // equal content
        assertNotSame(a, b)      // but a fresh parse each call
    }
}
