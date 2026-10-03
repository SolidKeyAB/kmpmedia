package com.solidkey.painpoints.image.svg

import com.solidkey.painpoints.image.loading.ViewBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression for multi-subpath `d` strings (e.g. a pause glyph = two separate bars
 * `"M.. Z M.. Z"`). Each `Z` must close back to the start of *its own* subpath, not to the
 * first subpath's origin — otherwise the two bars get joined by a stray diagonal (the
 * "trousers" artifact seen in the Runtime-SVG demo's pause icon).
 */
class OGSvgPathSubpathTest {

    private val vb = ViewBox(0f, 0f, 100f, 100f)

    @Test
    fun pauseGlyph_eachSubpathClosesToItsOwnStart() {
        val d = "M35 25 H47 V75 H35 Z M53 25 H65 V75 H53 Z"
        val cmds = parsePathCommands(d, vb)

        // Two bars => exactly two MoveTo (two subpaths).
        val moves = cmds.filterIsInstance<MoveTo>()
        assertEquals(2, moves.size, "a two-bar pause glyph must parse to two subpaths")
        assertEquals(35f, moves[0].x); assertEquals(25f, moves[0].y)
        assertEquals(53f, moves[1].x); assertEquals(25f, moves[1].y)

        // The very last emitted command is the second `Z`'s closing line: it must return to the
        // SECOND subpath's start (53,25), not the first subpath's origin (35,25).
        val last = cmds.last()
        assertTrue(last is LineTo, "a trailing Z closes with a LineTo back to the subpath start")
        last as LineTo
        assertEquals(53f, last.x, "second subpath must close back to its own start x")
        assertEquals(25f, last.y, "second subpath must close back to its own start y")
    }

    @Test
    fun singleSubpath_stillClosesToItsStart() {
        val d = "M30 30 H70 V70 H30 Z"
        val cmds = parsePathCommands(d, vb)
        assertEquals(1, cmds.filterIsInstance<MoveTo>().size)
        val last = cmds.last() as LineTo
        assertEquals(30f, last.x)
        assertEquals(30f, last.y)
    }
}
