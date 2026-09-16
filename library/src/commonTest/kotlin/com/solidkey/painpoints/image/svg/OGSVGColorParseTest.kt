package com.solidkey.painpoints.image.svg

import androidx.compose.ui.graphics.Color
import com.solidkey.painpoints.image.OGBrush
import com.solidkey.painpoints.image.OGColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Covers [parseColor] and the OGColorBase contract that the gradient-stop cast
 * in parseSVGGradients relies on: solid inputs must yield an [OGColor], while a
 * resolved url(...) yields an [OGBrush]. The gradient parser now guards that
 * cast with `as?`, so this test documents exactly which inputs would have
 * blown up the old unguarded `color as OGColor`.
 */
class OGSVGColorParseTest {

    private fun solid(input: String?, gradients: Map<String, OGSVGGradient>? = null): Color {
        val parsed = parseColor(input, gradients)
        assertIs<OGColor>(parsed, "expected a solid OGColor for input=$input")
        return parsed.color
    }

    private fun assertRgb(c: Color, r: Float, g: Float, b: Float, a: Float = 1f) {
        assertEquals(r, c.red, 0.01f)
        assertEquals(g, c.green, 0.01f)
        assertEquals(b, c.blue, 0.01f)
        assertEquals(a, c.alpha, 0.01f)
    }

    @Test
    fun nullAndEmptyFallBackToFillDefault() {
        // Fill defaults to opaque black, non-fill to transparent.
        assertRgb(solid(null), 0f, 0f, 0f, 1f)
        assertRgb(solid(""), 0f, 0f, 0f, 1f)
        assertEquals(Color.Transparent, (parseColor(null, isFill = false) as OGColor).color)
    }

    @Test
    fun noneIsTransparent() {
        assertEquals(Color.Transparent, solid("none"))
    }

    @Test
    fun hexSixDigitsParse() {
        assertRgb(solid("#ff0000"), 1f, 0f, 0f)
        assertRgb(solid("#00ff00"), 0f, 1f, 0f)
        assertRgb(solid("#0000ff"), 0f, 0f, 1f)
    }

    @Test
    fun hexThreeDigitsExpand() {
        assertRgb(solid("#f00"), 1f, 0f, 0f)
        assertRgb(solid("#0f0"), 0f, 1f, 0f)
    }

    @Test
    fun hexEightDigitsCarryAlpha() {
        // 0x80 alpha ~= 0.502
        assertRgb(solid("#80ff0000"), 1f, 0f, 0f, 0.502f)
    }

    @Test
    fun invalidHexIsTransparent() {
        assertEquals(Color.Transparent, solid("#zzz"))
        assertEquals(Color.Transparent, solid("#12345"))
    }

    @Test
    fun namedColorsParse() {
        assertRgb(solid("red"), 1f, 0f, 0f)
        assertRgb(solid("blue"), 0f, 0f, 1f)
        assertEquals(Color.Transparent, solid("transparent"))
    }

    @Test
    fun unknownNameIsTransparent() {
        assertEquals(Color.Transparent, solid("notacolor"))
    }

    @Test
    fun rgbAndRgbaParse() {
        assertRgb(solid("rgb(255,0,0)"), 1f, 0f, 0f)
        assertRgb(solid("rgba(0, 0, 255, 0.5)"), 0f, 0f, 1f, 0.5f)
    }

    @Test
    fun resolvedGradientUrlYieldsBrushNotColor() {
        val gradient = OGSVGGradient(
            id = "g1",
            brush = OGBrush(
                type = OGLinearGradientType(0f, 0f, 1f, 0f),
                colorStops = listOf(0f to Color.Red, 1f to Color.Blue)
            )
        )
        val parsed = parseColor("url(#g1)", mapOf("g1" to gradient))
        // This is the case the old `color as OGColor` would have thrown on.
        assertIs<OGBrush>(parsed)
    }

    @Test
    fun unresolvedGradientUrlIsTransparent() {
        assertEquals(Color.Transparent, solid("url(#missing)", emptyMap()))
    }
}
