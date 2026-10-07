package com.solidkey.painpoints.text

import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.style.OGStyles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the pure core of styled vector text: [OGTextOutline] and [OGTextOutline.styled] (applying an
 * [com.solidkey.painpoints.style.OGStyle] to each glyph contour). The platform glyph extraction
 * ([ogVectorizeText]) is exercised on-device (iosTest + the Android demo), not here.
 */
class OGStyledTextTest {

    private val triA = listOf(OGPoint(0f, 0f), OGPoint(0.5f, 0f), OGPoint(0.25f, 0.5f))
    private val triB = listOf(OGPoint(0.6f, 0f), OGPoint(1f, 0f), OGPoint(0.8f, 0.5f))
    private val outline = OGTextOutline(listOf(triA, triB), widthEm = 1f, topEm = 0f, bottomEm = 0.5f)

    @Test
    fun heightEm_isBottomMinusTop_andEmptyHasNoContours() {
        assertEquals(0.5f, outline.heightEm, 1e-6f)
        assertTrue(OGTextOutline.Empty.contours.isEmpty())
    }

    @Test
    fun styled_emptyStyle_keepsEachContourUnchanged() {
        val styled = outline.styled(OGStyles.decode("""{"ops":[]}"""), 0L)
        assertEquals(2, styled.size)
        assertEquals(triA, styled[0])
        assertEquals(triB, styled[1])
    }

    @Test
    fun styled_roughen_subdividesEachContourIndependently() {
        val styled = outline.styled(OGStyles.decode("""{"ops":[{"op":"roughen","amplitude":0.02,"detail":3}]}"""), 0L)
        assertEquals(2, styled.size)
        styled.forEach { assertEquals(3 * (3 + 1), it.size) } // roughen subdivides: n*(detail+1) per contour
        assertTrue(styled.all { c -> c.all { it.x.isFinite() && it.y.isFinite() } })
    }

    @Test
    fun styled_wave_movesGlyphContoursOverTime() {
        val style = OGStyles.decode("""{"ops":[{"op":"wave","amplitude":0.03,"waves":2}]}""")
        val a = outline.styled(style, 0L)
        val b = outline.styled(style, 300L)
        assertTrue(a.zip(b).any { (x, y) -> x != y }, "wave must animate the glyph contours")
    }
}
