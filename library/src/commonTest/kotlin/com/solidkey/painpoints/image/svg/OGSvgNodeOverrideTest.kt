package com.solidkey.painpoints.image.svg

import androidx.compose.ui.graphics.Color
import com.solidkey.painpoints.image.OGColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
