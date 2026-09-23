package com.solidkey.painpoints.image.svg

import androidx.compose.ui.graphics.Color
import com.solidkey.painpoints.image.OGColor
import com.solidkey.painpoints.image.loading.ViewBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers [applyPaintOverride] and the [OGSvgNodeOverride.hasPaint] / [hasTransform] flags — the
 * pure logic behind runtime-editable SVG. Paint fields fold into the node's style (reusing
 * [OGSVGStyle.combine]); transform fields never touch the tree (applied at draw time).
 */
class OGSvgNodeOverrideTest {

    private fun node(
        id: String? = "n1",
        style: OGSVGStyle = OGSVGStyle(fill = OGColor(Color.Red), strokeWidth = 2f),
    ) = OGSVGTreeElement(
        tagName = "circle",
        attributes = mutableMapOf(),
        style = style,
        id = id,
    )

    // ---- flags ----

    @Test
    fun hasPaint_and_hasTransform_detectSetFields() {
        assertFalse(OGSvgNodeOverride().hasPaint)
        assertFalse(OGSvgNodeOverride().hasTransform)

        assertTrue(OGSvgNodeOverride(fill = Color.Green).hasPaint)
        assertTrue(OGSvgNodeOverride(stroke = Color.Blue).hasPaint)
        assertTrue(OGSvgNodeOverride(strokeWidth = 3f).hasPaint)
        assertFalse(OGSvgNodeOverride(fill = Color.Green).hasTransform)

        assertTrue(OGSvgNodeOverride(rotation = 45f).hasTransform)
        assertTrue(OGSvgNodeOverride(translateX = 10f).hasTransform)
        assertTrue(OGSvgNodeOverride(scaleX = 2f).hasTransform)
        assertFalse(OGSvgNodeOverride(rotation = 45f).hasPaint)
    }

    // ---- applyPaintOverride ----

    @Test
    fun nullOverride_returnsSameInstance() {
        val n = node()
        assertSame(n, applyPaintOverride(n, null))
    }

    @Test
    fun transformOnlyOverride_returnsSameInstance() {
        // Transforms are applied at draw time, so the tree/element must be left untouched.
        val n = node()
        assertSame(n, applyPaintOverride(n, OGSvgNodeOverride(rotation = 90f, translateX = 5f)))
    }

    @Test
    fun fillOverride_replacesFill_keepsOtherStyle() {
        val n = node(style = OGSVGStyle(fill = OGColor(Color.Red), strokeWidth = 2f))
        val out = applyPaintOverride(n, OGSvgNodeOverride(fill = Color.Green))
        assertEquals(OGColor(Color.Green), out.style.fill)
        assertEquals(2f, out.style.strokeWidth) // untouched base value
    }

    @Test
    fun strokeAndWidthOverride_apply() {
        val n = node(style = OGSVGStyle(fill = OGColor(Color.Red)))
        val out = applyPaintOverride(n, OGSvgNodeOverride(stroke = Color.Blue, strokeWidth = 7f))
        assertEquals(OGColor(Color.Blue), out.style.stroke)
        assertEquals(7f, out.style.strokeWidth)
        assertEquals(OGColor(Color.Red), out.style.fill) // base fill preserved
    }

    @Test
    fun nullPaintField_keepsBaseValue() {
        // Overriding only stroke must not wipe the base fill.
        val n = node(style = OGSVGStyle(fill = OGColor(Color.Red), stroke = OGColor(Color.Black)))
        val out = applyPaintOverride(n, OGSvgNodeOverride(stroke = Color.Blue))
        assertEquals(OGColor(Color.Red), out.style.fill)
        assertEquals(OGColor(Color.Blue), out.style.stroke)
    }

    @Test
    fun copySharesChildrenList_soTraversalIsUnaffected() {
        val child = node(id = "c1")
        val n = node().also { it.children.add(child) }
        val out = applyPaintOverride(n, OGSvgNodeOverride(fill = Color.Green))
        // Shallow copy: the children list is shared by reference (same traversal targets).
        assertSame(n.children, out.children)
        assertEquals(1, out.children.size)
    }

    @Test
    fun overrideForDifferentNode_isANoOpWhenPassedNull() {
        // Simulates the lookup miss (overrides has no entry for this id → null passed in).
        val n = node(id = "unmatched")
        assertSame(n, applyPaintOverride(n, null))
        assertNull(applyPaintOverride(n, null).style.stroke)
    }

    // ---- applyPathOverride (runtime path-`d` override) ----

    private val vb = ViewBox(0f, 0f, 100f, 100f)

    private fun pathNode(
        id: String? = "p1",
        d: String = "M0 0 L10 0 L10 10 Z",
        style: OGSVGStyle = OGSVGStyle(fill = OGColor(Color.Red)),
    ) = OGSVGTreeElement(
        tagName = "path",
        attributes = mutableMapOf("d" to d),
        style = style,
        id = id,
    ).also { it.shapes.add(OGSVGPath(parsePathCommands(d, vb, style))) }

    @Test
    fun hasPath_detectsPathData() {
        assertFalse(OGSvgNodeOverride().hasPath)
        assertTrue(OGSvgNodeOverride(pathData = "M0 0 L1 1").hasPath)
        // pathData is orthogonal to paint / transform.
        assertFalse(OGSvgNodeOverride(pathData = "M0 0").hasPaint)
        assertFalse(OGSvgNodeOverride(pathData = "M0 0").hasTransform)
    }

    @Test
    fun pathOverride_nullOrNoPathData_returnsSameInstance() {
        val n = pathNode()
        assertSame(n, applyPathOverride(n, null, vb))
        assertSame(n, applyPathOverride(n, OGSvgNodeOverride(fill = Color.Green), vb))
    }

    @Test
    fun pathOverride_onNonPathNode_isNoOp() {
        // A circle node has no OGSVGPath geometry, so a `d` override can't apply.
        val circle = node() // tagName = "circle", no path shapes
        assertSame(circle, applyPathOverride(circle, OGSvgNodeOverride(pathData = "M0 0 L5 5"), vb))
    }

    @Test
    fun pathOverride_replacesGeometry_withReparsedCommands() {
        val style = OGSVGStyle(fill = OGColor(Color.Red))
        val n = pathNode(d = "M0 0 L10 0 L10 10 Z", style = style)
        val newD = "M0 0 L20 0 L20 20"
        val out = applyPathOverride(n, OGSvgNodeOverride(pathData = newD), vb)

        assertNotSame(n, out)
        val outPath = out.shapes.single() as OGSVGPath
        // Geometry equals a fresh parse of the new `d` (same viewBox + style).
        assertEquals(parsePathCommands(newD, vb, style), outPath.commands)
    }

    @Test
    fun pathOverride_doesNotMutateSourceTree() {
        // Re-resolving with a different override must always start from the original `d`, so the
        // parsed source element and its shapes list must be left untouched.
        val original = pathNode(d = "M0 0 L10 0 L10 10 Z")
        val originalCommands = (original.shapes.single() as OGSVGPath).commands
        val out = applyPathOverride(original, OGSvgNodeOverride(pathData = "M0 0 L99 0"), vb)

        assertNotSame(original.shapes, out.shapes) // fresh list
        // Source unchanged: still the original geometry.
        assertEquals(originalCommands, (original.shapes.single() as OGSVGPath).commands)
    }

    @Test
    fun pathOverride_keepsNonPathShapesOnTheNode() {
        // A node carrying both a line and a path: only the path is re-geometried.
        val n = pathNode(d = "M0 0 L10 0")
        val line = OGSVGLine(0f, 0f, 5f, 5f)
        n.shapes.add(line)
        val out = applyPathOverride(n, OGSvgNodeOverride(pathData = "M0 0 L30 0"), vb)

        assertTrue(out.shapes.any { it is OGSVGPath })
        assertSame(line, out.shapes.first { it is OGSVGLine }) // line passed through untouched
        assertEquals(2, out.shapes.size)
    }
}
