package com.solidkey.painpoints.image.loading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [OGExifOrientation.transformFor] to real geometry. For every orientation value we run the
 * four corners of a stored W×H image through two independent routes and require they agree:
 *
 *  - the generic "[OGExifTransform]" route: mirror horizontally (if asked), then rotate clockwise;
 *  - a hand-written canonical route: the textbook pixel mapping for that specific orientation.
 *
 * If the lookup table ever picks the wrong rotation/mirror combo (the classic transpose/transverse
 * bug) the two routes diverge and the test fails.
 */
class OGExifOrientationTest {

    private data class Mapped(val x: Int, val y: Int, val w: Int, val h: Int)

    /** Generic route: mirror horizontally, then rotate clockwise by the transform's degrees. */
    private fun viaTransform(x: Int, y: Int, w: Int, h: Int, t: OGExifTransform): Mapped {
        var px = x
        var py = y
        if (t.mirrored) px = w - 1 - px // horizontal mirror, dims unchanged
        return when (t.rotationDegrees) {
            0 -> Mapped(px, py, w, h)
            90 -> Mapped(h - 1 - py, px, h, w)   // clockwise, dims swap
            180 -> Mapped(w - 1 - px, h - 1 - py, w, h)
            270 -> Mapped(py, w - 1 - px, h, w)  // clockwise, dims swap
            else -> error("unexpected rotation ${t.rotationDegrees}")
        }
    }

    /** Canonical route: the standard per-orientation pixel mapping (Pillow's exif_transpose table). */
    private fun canonical(x: Int, y: Int, w: Int, h: Int, orientation: Int): Mapped = when (orientation) {
        OGExifOrientation.NORMAL -> Mapped(x, y, w, h)
        OGExifOrientation.FLIP_HORIZONTAL -> Mapped(w - 1 - x, y, w, h)
        OGExifOrientation.ROTATE_180 -> Mapped(w - 1 - x, h - 1 - y, w, h)
        OGExifOrientation.FLIP_VERTICAL -> Mapped(x, h - 1 - y, w, h)
        OGExifOrientation.TRANSPOSE -> Mapped(y, x, h, w)
        OGExifOrientation.ROTATE_90 -> Mapped(h - 1 - y, x, h, w)
        OGExifOrientation.TRANSVERSE -> Mapped(h - 1 - y, w - 1 - x, h, w)
        OGExifOrientation.ROTATE_270 -> Mapped(y, w - 1 - x, h, w)
        else -> error("unexpected orientation $orientation")
    }

    @Test
    fun everyOrientationMatchesItsCanonicalGeometry() {
        val w = 4
        val h = 2
        val corners = listOf(0 to 0, w - 1 to 0, 0 to h - 1, w - 1 to h - 1)
        for (orientation in 1..8) {
            val t = OGExifOrientation.transformFor(orientation)
            for ((cx, cy) in corners) {
                assertEquals(
                    canonical(cx, cy, w, h, orientation),
                    viaTransform(cx, cy, w, h, t),
                    "orientation $orientation corner ($cx,$cy)",
                )
            }
        }
    }

    @Test
    fun normalAndUnknownAreIdentity() {
        assertTrue(OGExifOrientation.transformFor(OGExifOrientation.NORMAL).isIdentity)
        assertTrue(OGExifOrientation.transformFor(0).isIdentity)
        assertTrue(OGExifOrientation.transformFor(99).isIdentity)
    }

    @Test
    fun rotationsAreMultiplesOf90AndMirrorsAreMarked() {
        assertEquals(OGExifTransform(0, true), OGExifOrientation.transformFor(OGExifOrientation.FLIP_HORIZONTAL))
        assertEquals(OGExifTransform(90, false), OGExifOrientation.transformFor(OGExifOrientation.ROTATE_90))
        assertEquals(OGExifTransform(270, false), OGExifOrientation.transformFor(OGExifOrientation.ROTATE_270))
        for (o in 1..8) {
            val deg = OGExifOrientation.transformFor(o).rotationDegrees
            assertTrue(deg % 90 == 0 && deg in 0..270, "orientation $o rotation $deg out of range")
        }
    }
}
