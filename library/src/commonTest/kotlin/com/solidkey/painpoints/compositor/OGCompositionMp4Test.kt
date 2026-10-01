package com.solidkey.painpoints.compositor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-math coverage of the MP4 export pipeline (Bet 3 follow-up). The platform encoders themselves
 * (`MediaCodec` / `AVAssetWriter`) can only run on a device, so these tests pin down everything that is
 * shared and deterministic: even-dimension rounding, the bit-rate default, transparent→opaque
 * compositing, the crop+flatten frame step, and the BT.601 RGB→YUV / RGB→BGRA colour conversions the
 * two encoders consume. A colour bug here would corrupt every exported video on both platforms, so it is
 * worth locking down without a device.
 */
class OGCompositionMp4Test {

    // ---- even dimensions -----------------------------------------------------------------------

    @Test
    fun evenDownRoundsToEvenAndNeverBelowTwo() {
        assertEquals(4, evenDown(4))
        assertEquals(4, evenDown(5))
        assertEquals(6, evenDown(7))
        assertEquals(2, evenDown(2))
        assertEquals(2, evenDown(1))
        assertEquals(2, evenDown(0))
        assertEquals(480, evenDown(480))
        assertEquals(480, evenDown(481))
    }

    // ---- bit rate default ----------------------------------------------------------------------

    @Test
    fun defaultBitRateScalesAndClamps() {
        assertEquals(750_000, defaultMp4BitRate(16, 16, 24))            // tiny → clamped to the floor
        assertEquals(16_000_000, defaultMp4BitRate(4000, 4000, 60))    // huge → clamped to the ceiling
        // 1280*720*24/5 = 4,423,680 — inside the band, returned as-is.
        assertEquals(4_423_680, defaultMp4BitRate(1280, 720, 24))
    }

    // ---- compositing ---------------------------------------------------------------------------

    @Test
    fun opaquePixelIsReturnedOpaqueUnchanged() {
        assertEquals(OPAQUE_RED, compositeOverOpaque(OPAQUE_RED, OPAQUE_BLACK))
        assertEquals(OPAQUE_GREEN, compositeOverOpaque(OPAQUE_GREEN, OPAQUE_WHITE))
    }

    @Test
    fun transparentPixelBecomesTheBackground() {
        assertEquals(OPAQUE_WHITE, compositeOverOpaque(0x00123456, OPAQUE_WHITE))
        assertEquals(OPAQUE_BLACK, compositeOverOpaque(0x00FFFFFF, OPAQUE_BLACK))
    }

    @Test
    fun halfAlphaBlendsHalfwayToTheBackground() {
        // 50% red over black → ~half-intensity red, fully opaque.
        assertEquals(0xFF800000.toInt(), compositeOverOpaque(0x80FF0000.toInt(), OPAQUE_BLACK))
        // 50% white over black → ~mid grey.
        assertEquals(0xFF808080.toInt(), compositeOverOpaque(0x80FFFFFF.toInt(), OPAQUE_BLACK))
    }

    // ---- crop + flatten ------------------------------------------------------------------------

    @Test
    fun cropFlattenDropsTheOddColumnAndRowAndFlattens() {
        // 3x3 source; crop to 2x2 keeps the top-left quad. Mix opaque + transparent pixels.
        val t = 0x00000000 // transparent
        val src = intArrayOf(
            OPAQUE_RED, OPAQUE_GREEN, OPAQUE_BLUE,
            t, OPAQUE_WHITE, OPAQUE_RED,
            OPAQUE_BLUE, OPAQUE_BLUE, OPAQUE_BLUE,
        )
        val out = cropFlattenFrame(src, srcW = 3, srcH = 3, outW = 2, outH = 2, bgOpaque = OPAQUE_BLACK)
        assertEquals(4, out.size)
        assertEquals(OPAQUE_RED, out[0])
        assertEquals(OPAQUE_GREEN, out[1])
        assertEquals(OPAQUE_BLACK, out[2]) // was transparent → background
        assertEquals(OPAQUE_WHITE, out[3])
    }

    // ---- BGRA (iOS) ----------------------------------------------------------------------------

    @Test
    fun argbToBgraPacksBlueGreenRedAlphaOpaque() {
        val bgra = argbToBgra(intArrayOf(OPAQUE_RED, OPAQUE_GREEN))
        assertEquals(8, bgra.size)
        // red 0xFFFF0000 → B0 G0 R255 A255
        assertBytes(bgra, 0, 0x00, 0x00, 0xFF, 0xFF)
        // green 0xFF00FF00 → B0 G255 R0 A255
        assertBytes(bgra, 4, 0x00, 0xFF, 0x00, 0xFF)
    }

    // ---- I420 (Android) ------------------------------------------------------------------------

    @Test
    fun argbToI420HasCorrectPlaneSizes() {
        val i420 = argbToI420(IntArray(4 * 4) { OPAQUE_BLACK }, 4, 4)
        assertEquals(16, i420.y.size)
        assertEquals(4, i420.u.size)
        assertEquals(4, i420.v.size)
    }

    @Test
    fun bt601ConvertsKnownColoursExactly() {
        // Black → Y=16, neutral chroma.
        assertEquals(16, rgbToY(0, 0, 0))
        assertEquals(128, rgbToU(0, 0, 0))
        assertEquals(128, rgbToV(0, 0, 0))
        // White → Y=235, neutral chroma.
        assertEquals(235, rgbToY(255, 255, 255))
        assertEquals(128, rgbToU(255, 255, 255))
        assertEquals(128, rgbToV(255, 255, 255))
        // Pure red → the classic BT.601 video-range triplet.
        assertEquals(82, rgbToY(255, 0, 0))
        assertEquals(90, rgbToU(255, 0, 0))
        assertEquals(240, rgbToV(255, 0, 0))
    }

    @Test
    fun argbToI420FillsPlanesFromPixels() {
        val i420 = argbToI420(IntArray(2 * 2) { OPAQUE_RED }, 2, 2)
        assertTrue(i420.y.all { (it.toInt() and 0xFF) == 82 }, "Y plane should be red luma")
        assertEquals(90, i420.u[0].toInt() and 0xFF)
        assertEquals(240, i420.v[0].toInt() and 0xFF)
    }

    // ---- helpers -------------------------------------------------------------------------------

    private fun assertBytes(a: ByteArray, off: Int, vararg expected: Int) {
        for (i in expected.indices) {
            assertEquals(expected[i], a[off + i].toInt() and 0xFF, "byte ${off + i}")
        }
    }

    private companion object {
        const val OPAQUE_RED = 0xFFFF0000.toInt()
        const val OPAQUE_GREEN = 0xFF00FF00.toInt()
        const val OPAQUE_BLUE = 0xFF0000FF.toInt()
        const val OPAQUE_WHITE = 0xFFFFFFFF.toInt()
        const val OPAQUE_BLACK = 0xFF000000.toInt()
    }
}
