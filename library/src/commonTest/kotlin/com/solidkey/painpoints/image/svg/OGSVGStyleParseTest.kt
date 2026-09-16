package com.solidkey.painpoints.image.svg

import com.solidkey.painpoints.image.loading.ViewBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers [parseStyleAttribute] and [parseViewBox].
 *
 * The star here is the inline-style regression fixed in 79d1fb2: a CSS value may
 * itself contain ':' (e.g. `url(http://…)`), and the old `split(":")` with no
 * limit produced >2 parts, so the `size == 2` guard dropped the whole rule.
 * The fix uses `split(":", limit = 2)`. These tests lock that in.
 */
class OGSVGStyleParseTest {

    // ---- parseStyleAttribute ----

    @Test
    fun simpleRulesParse() {
        val m = parseStyleAttribute("fill:red;stroke:blue")
        assertEquals("red", m["fill"])
        assertEquals("blue", m["stroke"])
        assertEquals(2, m.size)
    }

    @Test
    fun whitespaceAroundKeysAndValuesIsTrimmed() {
        val m = parseStyleAttribute("  fill : red ;  stroke : #00ff00  ")
        assertEquals("red", m["fill"])
        assertEquals("#00ff00", m["stroke"])
    }

    @Test
    fun valueContainingColonIsKept_urlWithScheme() {
        // The exact case the old unlimited split(":") dropped: value has ':'.
        val m = parseStyleAttribute("fill:red;background:url(http://example.com/a.png)")
        assertEquals("red", m["fill"])
        assertEquals("url(http://example.com/a.png)", m["background"])
        assertTrue(m.containsKey("background"), "value with ':' must not be dropped")
    }

    @Test
    fun fillUrlReferenceIsKept() {
        val m = parseStyleAttribute("fill:url(#grad1);fill-opacity:0.5")
        assertEquals("url(#grad1)", m["fill"])
        assertEquals("0.5", m["fill-opacity"])
    }

    @Test
    fun dataUriValueWithMultipleColonsIsKept() {
        // Several ':' in the value — limit=2 must keep everything after the first.
        val m = parseStyleAttribute("background:url(data:image/png;base64,AAAA)")
        // Note: ';' is the rule separator, so only the pre-';' part survives here,
        // but the point is the first ':' split keeps the scheme intact.
        assertEquals("url(data:image/png", m["background"])
    }

    @Test
    fun trailingSemicolonAndEmptyRulesAreIgnored() {
        val m = parseStyleAttribute("fill:red;;;stroke:blue;")
        assertEquals("red", m["fill"])
        assertEquals("blue", m["stroke"])
        assertEquals(2, m.size)
    }

    @Test
    fun ruleWithoutColonIsSkipped() {
        val m = parseStyleAttribute("fill:red;garbage;stroke:blue")
        assertEquals("red", m["fill"])
        assertEquals("blue", m["stroke"])
        assertFalse(m.containsKey("garbage"))
    }

    @Test
    fun emptyKeyIsSkipped() {
        val m = parseStyleAttribute(":red;fill:green")
        assertEquals("green", m["fill"])
        assertEquals(1, m.size)
    }

    @Test
    fun nullAndBlankYieldEmptyMap() {
        assertTrue(parseStyleAttribute(null).isEmpty())
        assertTrue(parseStyleAttribute("").isEmpty())
    }

    // ---- parseViewBox ----

    @Test
    fun viewBoxParsesFourNumbers() {
        val vb = parseViewBox("""<svg viewBox="0 0 100 200" xmlns="...">""")
        assertEquals(ViewBox(0f, 0f, 100f, 200f), vb)
    }

    @Test
    fun viewBoxSupportsNegativeOrigin() {
        val vb = parseViewBox("""<svg viewBox="-10 -20 30 40">""")
        assertEquals(ViewBox(-10f, -20f, 30f, 40f), vb)
    }

    @Test
    fun viewBoxSupportsDecimals() {
        val vb = parseViewBox("""<svg viewBox="0 0 24.5 24.5">""")
        assertEquals(ViewBox(0f, 0f, 24.5f, 24.5f), vb)
    }

    @Test
    fun missingViewBoxFallsBackToDefault() {
        val vb = parseViewBox("""<svg width="100" height="100">""")
        assertEquals(ViewBox(0f, 0f, 100f, 100f), vb)
    }
}
