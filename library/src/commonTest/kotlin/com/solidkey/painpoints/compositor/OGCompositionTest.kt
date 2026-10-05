package com.solidkey.painpoints.compositor

import androidx.compose.foundation.shape.CircleShape
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pure-math coverage of the on-device compositor (Bet 3): keyframe/easing evaluation, composition
 * timeline arithmetic + layer resolution, and a full **GIF encode → decode roundtrip** (the encoder is
 * validated against a self-contained, spec-faithful GIF decoder written below, so the LZW code-width
 * growth, the 4096 dictionary clear, the shared palette and the transparent index are all exercised).
 * Nothing here touches Compose drawing or platform bitmaps, so it runs identically on JVM and iOS.
 */
class OGCompositionTest {

    // ---- easing --------------------------------------------------------------------------------

    @Test
    fun easingCurvesHitTheirAnchors() {
        for (e in OGEasing.entries) {
            assertEquals(0f, ogEase(e, 0f), 1e-4f, "$e at 0")
            assertEquals(1f, ogEase(e, 1f), 1e-4f, "$e at 1")
        }
        assertEquals(0.5f, ogEase(OGEasing.LINEAR, 0.5f), 1e-4f)
        assertEquals(0.25f, ogEase(OGEasing.EASE_IN, 0.5f), 1e-4f)
        assertEquals(0.75f, ogEase(OGEasing.EASE_OUT, 0.5f), 1e-4f)
        assertEquals(0.5f, ogEase(OGEasing.EASE_IN_OUT, 0.5f), 1e-4f)
    }

    @Test
    fun easingClampsOutOfRangeT() {
        assertEquals(0f, ogEase(OGEasing.EASE_IN, -1f), 1e-4f)
        assertEquals(1f, ogEase(OGEasing.EASE_OUT, 2f), 1e-4f)
    }

    // ---- keyframed float -----------------------------------------------------------------------

    @Test
    fun constTrackIsFlat() {
        val t = OGKeyframedFloat.const(7f)
        assertEquals(7f, t.valueAt(0))
        assertEquals(7f, t.valueAt(9999))
    }

    @Test
    fun trackClampsBeforeFirstAndAfterLast() {
        val t = OGKeyframedFloat.of(100L to 10f, 200L to 20f)
        assertEquals(10f, t.valueAt(0))
        assertEquals(10f, t.valueAt(100))
        assertEquals(20f, t.valueAt(200))
        assertEquals(20f, t.valueAt(10_000))
    }

    @Test
    fun trackLinearlyInterpolatesBetweenStops() {
        val t = OGKeyframedFloat.of(0L to 0f, 1000L to 100f)
        assertEquals(50f, t.valueAt(500), 1e-3f)
        assertEquals(25f, t.valueAt(250), 1e-3f)
    }

    @Test
    fun trackAppliesTheIncomingKeyframesEasing() {
        val t = OGKeyframedFloat(
            default = 0f,
            keyframes = listOf(
                OGKeyframe(0L, 0f),
                OGKeyframe(1000L, 100f, OGEasing.EASE_IN),
            ),
        )
        // EASE_IN(0.5) = 0.25 -> 25, strictly below the linear midpoint.
        assertEquals(25f, t.valueAt(500), 1e-3f)
    }

    @Test
    fun singleKeyframeIsConstant() {
        val t = OGKeyframedFloat(default = 3f, keyframes = listOf(OGKeyframe(500L, 42f)))
        assertEquals(42f, t.valueAt(0))
        assertEquals(42f, t.valueAt(9999))
    }

    // ---- composition timeline ------------------------------------------------------------------

    @Test
    fun frameCountRoundsAndIsAtLeastOne() {
        assertEquals(24, comp(durationMs = 1000, fps = 24).frameCount)
        assertEquals(30, comp(durationMs = 1000, fps = 30).frameCount)
        assertEquals(15, comp(durationMs = 500, fps = 30).frameCount)
        assertEquals(1, comp(durationMs = 0, fps = 30).frameCount)
    }

    @Test
    fun frameTimesAreEvenlySpaced() {
        val c = comp(durationMs = 1000, fps = 4)
        assertEquals(0L, c.frameTimeMs(0))
        assertEquals(250L, c.frameTimeMs(1))
        assertEquals(750L, c.frameTimeMs(3))
    }

    @Test
    fun frameDelayIsClampedToTwoCentiseconds() {
        assertEquals(4, comp(durationMs = 1000, fps = 24).frameDelayCs)
        assertEquals(2, comp(durationMs = 1000, fps = 60).frameDelayCs)  // 100/60≈1.7 → clamp 2
        assertEquals(2, comp(durationMs = 1000, fps = 100).frameDelayCs) // 1 → clamp 2
        assertEquals(10, comp(durationMs = 1000, fps = 10).frameDelayCs)
    }

    @Test
    fun layerVisibilityWindowIsInclusive() {
        val layer = OGCompositionLayer(
            id = "a", content = OGLayerContent.Solid(androidx.compose.ui.graphics.Color.Red),
            width = 10f, height = 10f, startMs = 100L, endMs = 300L,
        )
        assertTrue(!layer.isVisibleAt(99L, 1000L))
        assertTrue(layer.isVisibleAt(100L, 1000L))
        assertTrue(layer.isVisibleAt(300L, 1000L))
        assertTrue(!layer.isVisibleAt(301L, 1000L))
    }

    @Test
    fun openEndedLayerRunsToCompositionEnd() {
        val layer = OGCompositionLayer(
            id = "a", content = OGLayerContent.Solid(androidx.compose.ui.graphics.Color.Red),
            width = 10f, height = 10f, startMs = 0L, endMs = null,
        )
        assertTrue(layer.isVisibleAt(1000L, 1000L))
        assertTrue(!layer.isVisibleAt(1001L, 1000L))
    }

    @Test
    fun resolveAtFlattensTracksAndFiltersInvisibleLayers() {
        val moving = OGCompositionLayer(
            id = "move", content = OGLayerContent.Solid(androidx.compose.ui.graphics.Color.Blue),
            width = 20f, height = 20f,
            x = OGKeyframedFloat.of(0L to 0f, 1000L to 100f),
            opacity = OGKeyframedFloat.of(0L to 0f, 1000L to 2f), // over-driven → clamped
            clip = CircleShape,
        )
        val late = OGCompositionLayer(
            id = "late", content = OGLayerContent.Solid(androidx.compose.ui.graphics.Color.Green),
            width = 10f, height = 10f, startMs = 800L,
        )
        val c = OGComposition(width = 200, height = 200, durationMs = 1000, layers = listOf(moving, late))

        val atHalf = c.resolveAt(500L)
        assertEquals(1, atHalf.size) // "late" not visible yet
        assertEquals(50f, atHalf[0].x, 1e-2f)
        assertEquals(1f, atHalf[0].opacity, 1e-3f) // clamped from 1.0..2.0 range
        assertEquals(CircleShape, atHalf[0].clip)

        assertEquals(2, c.resolveAt(900L).size) // both visible
    }

    // ---- GIF encoder: structure ----------------------------------------------------------------

    @Test
    fun rejectsEmptyOrMismatchedFrames() {
        assertFailsWith<IllegalArgumentException> { OGGifEncoder.encode(4, 4, emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            OGGifEncoder.encode(4, 4, listOf(OGGifFrame(IntArray(9), 10))) // 9 != 16
        }
    }

    @Test
    fun writesGif89aHeaderLoopExtensionAndTrailer() {
        val bytes = OGGifEncoder.encode(2, 2, listOf(OGGifFrame(IntArray(4) { OPAQUE_RED }, 10)))
        assertEquals("GIF89a", bytes.copyOfRange(0, 6).decodeToString())
        assertEquals(0x3B.toByte(), bytes.last()) // trailer
        assertTrue(containsAscii(bytes, "NETSCAPE2.0"), "expected looping application extension")
    }

    // ---- GIF encoder: decode roundtrips --------------------------------------------------------

    @Test
    fun roundtripsASmallOpaqueImageExactly() {
        val w = 8; val h = 8
        val palette = intArrayOf(OPAQUE_RED, OPAQUE_GREEN, OPAQUE_BLUE, OPAQUE_WHITE)
        val src = IntArray(w * h) { palette[(it / 3) % palette.size] }
        val decoded = decodeGif(OGGifEncoder.encode(w, h, listOf(OGGifFrame(src, 8))))
        assertEquals(1, decoded.frames.size)
        assertContentEquals4(src, decoded.frames[0].argb)
    }

    @Test
    fun roundtripsTransparencyWithACorrectTransparentIndex() {
        val w = 4; val h = 4
        val src = IntArray(w * h) { if (it % 2 == 0) OPAQUE_BLUE else 0 } // every other pixel transparent
        val decoded = decodeGif(OGGifEncoder.encode(w, h, listOf(OGGifFrame(src, 5))))
        val out = decoded.frames[0].argb
        for (i in src.indices) {
            if (src[i] == 0) assertEquals(0, out[i] ushr 24 and 0xFF, "pixel $i should be transparent")
            else assertEquals(OPAQUE_BLUE, out[i], "pixel $i should be opaque blue")
        }
    }

    @Test
    fun stressesLzwGrowthAndDictionaryClearWithTwoColourNoise() {
        // High-entropy 2-colour noise over 16384 px forces the LZW dictionary to fill to 4096, emit a
        // clear code and keep going — the hardest path in the encoder. 2 colours → exact roundtrip.
        val w = 128; val h = 128
        var state = 0x12345
        val src = IntArray(w * h) {
            state = (state * 1103515245 + 12345) and 0x7FFFFFFF
            if (state and 1 == 0) OPAQUE_BLACK else OPAQUE_WHITE
        }
        val decoded = decodeGif(OGGifEncoder.encode(w, h, listOf(OGGifFrame(src, 3))))
        assertContentEquals4(src, decoded.frames[0].argb)
    }

    @Test
    fun roundtripsAMultiFrameAnimationWithPerFrameDelays() {
        val w = 6; val h = 6
        val f0 = IntArray(w * h) { OPAQUE_RED }
        val f1 = IntArray(w * h) { OPAQUE_GREEN }
        val f2 = IntArray(w * h) { OPAQUE_BLUE }
        val gif = OGGifEncoder.encode(
            w, h,
            listOf(OGGifFrame(f0, 5), OGGifFrame(f1, 10), OGGifFrame(f2, 20)),
            loopCount = 0,
        )
        val decoded = decodeGif(gif)
        assertEquals(3, decoded.frames.size)
        assertEquals(listOf(5, 10, 20), decoded.frames.map { it.delayCs })
        assertContentEquals4(f0, decoded.frames[0].argb)
        assertContentEquals4(f1, decoded.frames[1].argb)
        assertContentEquals4(f2, decoded.frames[2].argb)
    }

    // ---- GIF encoder: dithering ----------------------------------------------------------------

    @Test
    fun ditheringIsOnByDefaultAndEncodesDeterministically() {
        val w = 32; val h = 32
        val src = gradient2d(w, h)
        val a = OGGifEncoder.encode(w, h, listOf(OGGifFrame(src, 8))) // default = FLOYD_STEINBERG
        val b = OGGifEncoder.encode(w, h, listOf(OGGifFrame(src, 8)), dither = OGGifDither.FLOYD_STEINBERG)
        assertContentEquals(a, b, "same input + same dither must produce identical bytes")
        val decoded = decodeGif(a)
        assertEquals(1, decoded.frames.size)
        assertEquals(w * h, decoded.frames[0].argb.size)
    }

    @Test
    fun ditheringReducesBandingErrorOnAGradient() {
        // >256 distinct colours force the palette to merge, so plain nearest-match bands. Floyd–Steinberg
        // should make the LOCAL (block-averaged) colour track the source far more closely.
        val w = 64; val h = 64
        val src = gradient2d(w, h) // up to 4096 distinct colours → quantized to ≤256
        val plain = decodeGif(OGGifEncoder.encode(w, h, listOf(OGGifFrame(src, 8)), dither = OGGifDither.NONE))
            .frames[0].argb
        val dithered = decodeGif(OGGifEncoder.encode(w, h, listOf(OGGifFrame(src, 8)), dither = OGGifDither.FLOYD_STEINBERG))
            .frames[0].argb
        val plainErr = blockAverageError(src, plain, w, h, block = 8)
        val ditherErr = blockAverageError(src, dithered, w, h, block = 8)
        assertTrue(ditherErr < plainErr, "dither block-error ($ditherErr) should beat nearest ($plainErr)")
    }

    @Test
    fun ditheringKeepsTransparentPixelsTransparent() {
        val w = 32; val h = 32
        val grad = gradient2d(w, h)
        val src = IntArray(w * h) { i -> if ((i % w) < w / 2) 0 else grad[i] } // left half transparent
        val out = decodeGif(OGGifEncoder.encode(w, h, listOf(OGGifFrame(src, 8)))).frames[0].argb
        for (i in src.indices) {
            if (src[i] == 0) assertEquals(0, out[i] ushr 24 and 0xFF, "transparent pixel $i must stay transparent")
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** A 2-D RGB gradient: red follows x, green follows y, blue constant — many distinct colours to quantize. */
    private fun gradient2d(w: Int, h: Int): IntArray = IntArray(w * h) { i ->
        val r = (i % w) * 255 / (w - 1)
        val g = (i / w) * 255 / (h - 1)
        0xFF000000.toInt() or (r shl 16) or (g shl 8) or 128
    }

    /** Sum over BLOCKS of the squared error between the source's and the decoded image's average colour —
     *  the metric dithering is built to win: local averages match even when pixels snap to a small palette. */
    private fun blockAverageError(src: IntArray, out: IntArray, w: Int, h: Int, block: Int): Long {
        var total = 0L
        var by = 0
        while (by < h) {
            var bx = 0
            while (bx < w) {
                var sr = 0L; var sg = 0L; var sb = 0L; var or = 0L; var og = 0L; var ob = 0L; var n = 0
                for (yy in by until minOf(by + block, h)) for (xx in bx until minOf(bx + block, w)) {
                    val s = src[yy * w + xx]; val o = out[yy * w + xx]
                    sr += (s ushr 16 and 0xFF).toLong(); sg += (s ushr 8 and 0xFF).toLong(); sb += (s and 0xFF).toLong()
                    or += (o ushr 16 and 0xFF).toLong(); og += (o ushr 8 and 0xFF).toLong(); ob += (o and 0xFF).toLong()
                    n++
                }
                val dr = sr / n - or / n; val dg = sg / n - og / n; val db = sb / n - ob / n
                total += dr * dr + dg * dg + db * db
                bx += block
            }
            by += block
        }
        return total
    }

    private fun comp(durationMs: Long, fps: Int) =
        OGComposition(width = 10, height = 10, durationMs = durationMs, layers = emptyList(), fps = fps)

    private fun assertContentEquals4(expected: IntArray, actual: IntArray) {
        assertEquals(expected.size, actual.size, "pixel count")
        for (i in expected.indices) {
            assertEquals(expected[i], actual[i], "pixel $i (0x${expected[i].toUInt().toString(16)} vs 0x${actual[i].toUInt().toString(16)})")
        }
    }

    private fun containsAscii(bytes: ByteArray, needle: String): Boolean {
        val n = needle.encodeToByteArray()
        outer@ for (i in 0..bytes.size - n.size) {
            for (j in n.indices) if (bytes[i + j] != n[j]) continue@outer
            return true
        }
        return false
    }

    private companion object {
        const val OPAQUE_RED = 0xFFFF0000.toInt()
        const val OPAQUE_GREEN = 0xFF00FF00.toInt()
        const val OPAQUE_BLUE = 0xFF0000FF.toInt()
        const val OPAQUE_WHITE = 0xFFFFFFFF.toInt()
        const val OPAQUE_BLACK = 0xFF000000.toInt()
    }
}

// ============================================================================================
// A minimal, spec-faithful GIF89a decoder used ONLY by the tests above to prove the encoder's
// output is decodable by a standard reader (independent of the encoder's internal bookkeeping).
// ============================================================================================

private class DecodedGifFrame(val argb: IntArray, val delayCs: Int)
private class DecodedGif(val width: Int, val height: Int, val frames: List<DecodedGifFrame>)

private fun decodeGif(bytes: ByteArray): DecodedGif {
    var p = 0
    fun u8(): Int = bytes[p++].toInt() and 0xFF
    fun u16(): Int { val lo = u8(); val hi = u8(); return lo or (hi shl 8) }

    check(bytes.copyOfRange(0, 6).decodeToString().startsWith("GIF")) { "not a GIF" }
    p = 6
    val width = u16(); val height = u16()
    val packed = u8()
    /* bg */ u8(); /* aspect */ u8()
    val gctFlag = packed and 0x80 != 0
    val gctSize = if (gctFlag) 1 shl ((packed and 0x07) + 1) else 0
    val gct = IntArray(gctSize)
    for (i in 0 until gctSize) {
        val r = u8(); val g = u8(); val b = u8()
        gct[i] = (r shl 16) or (g shl 8) or b
    }

    val frames = ArrayList<DecodedGifFrame>()
    var pendingDelayCs = 0
    var transparentIndex = -1

    fun skipSubBlocks() { while (true) { val n = u8(); if (n == 0) break; p += n } }
    fun readSubBlocks(): ByteArray {
        val out = ArrayList<Byte>()
        while (true) {
            val n = u8(); if (n == 0) break
            for (i in 0 until n) out.add(bytes[p++])
        }
        return out.toByteArray()
    }

    loop@ while (p < bytes.size) {
        when (u8()) {
            0x3B -> break@loop // trailer
            0x21 -> { // extension
                when (u8()) {
                    0xF9 -> { // graphic control
                        val size = u8() // 4
                        val flags = u8()
                        pendingDelayCs = u16()
                        val tIdx = u8()
                        u8() // block terminator
                        transparentIndex = if (flags and 0x01 != 0) tIdx else -1
                        // (size is always 4 here; guard against oddities)
                        if (size != 4) { /* tolerate */ }
                    }
                    else -> skipSubBlocks()
                }
            }
            0x2C -> { // image descriptor
                /* left */ u16(); /* top */ u16()
                val iw = u16(); val ih = u16()
                val imgPacked = u8()
                val lctFlag = imgPacked and 0x80 != 0
                val lctSize = if (lctFlag) 1 shl ((imgPacked and 0x07) + 1) else 0
                val lct = IntArray(lctSize)
                for (i in 0 until lctSize) {
                    val r = u8(); val g = u8(); val b = u8()
                    lct[i] = (r shl 16) or (g shl 8) or b
                }
                val table = if (lctFlag) lct else gct
                val minCodeSize = u8()
                val data = readSubBlocks()
                val indices = lzwDecode(data, minCodeSize, iw * ih)
                val argb = IntArray(iw * ih) { i ->
                    val idx = indices[i]
                    if (idx == transparentIndex) 0
                    else 0xFF000000.toInt() or (table.getOrElse(idx) { 0 })
                }
                frames.add(DecodedGifFrame(argb, pendingDelayCs))
                transparentIndex = -1
            }
            else -> break@loop
        }
    }
    return DecodedGif(width, height, frames)
}

/** Standard variable-width, LSB-first GIF LZW decompression. */
private fun lzwDecode(data: ByteArray, minCodeSize: Int, pixelCount: Int): IntArray {
    val clearCode = 1 shl minCodeSize
    val endCode = clearCode + 1
    val out = IntArray(pixelCount)
    var outPos = 0

    var bitPos = 0
    fun readCode(size: Int): Int {
        var v = 0
        for (i in 0 until size) {
            val byteIndex = bitPos ushr 3
            if (byteIndex >= data.size) return endCode
            val bit = (data[byteIndex].toInt() ushr (bitPos and 7)) and 1
            v = v or (bit shl i)
            bitPos++
        }
        return v
    }

    val dict = ArrayList<IntArray>()
    var codeSize = minCodeSize + 1
    fun reset() {
        dict.clear()
        for (i in 0 until clearCode) dict.add(intArrayOf(i))
        dict.add(IntArray(0)) // clearCode slot
        dict.add(IntArray(0)) // endCode slot
        codeSize = minCodeSize + 1
    }
    reset()

    var prev: IntArray? = null
    while (outPos < pixelCount) {
        val code = readCode(codeSize)
        if (code == clearCode) { reset(); prev = null; continue }
        if (code == endCode) break
        val entry: IntArray = when {
            code < dict.size -> dict[code]
            else -> { val pr = prev!!; pr + pr[0] } // KwKwK
        }
        for (idx in entry) { if (outPos < pixelCount) out[outPos++] = idx }
        if (prev != null && dict.size < 4096) {
            dict.add(prev!! + entry[0])
            if (dict.size == (1 shl codeSize) && codeSize < 12) codeSize++
        }
        prev = entry
    }
    return out
}
