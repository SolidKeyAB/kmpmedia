package com.solidkey.painpoints.compositor

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * The streaming encoder ([OGGifEncoder.encodeStreaming]) must produce byte-for-byte the same GIF as the
 * batch [OGGifEncoder.encode] for the same frames at a constant delay — that equivalence is the whole
 * reason the exporters can stream (render → sample → drop, render → write → drop) instead of retaining
 * every frame's ARGB (a default stage would otherwise hold ~1.1GB and OOM a phone).
 */
class OGGifEncoderStreamingTest {

    private val W = 9
    private val H = 7
    private fun opaque(seed: Int): IntArray = IntArray(W * H) { i ->
        val r = (i * 7 + seed * 13) % 256
        val g = (i * 5 + seed * 31) % 256
        val b = (i * 3 + seed * 17) % 256
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun withHoles(seed: Int): IntArray = IntArray(W * H) { i ->
        if (i % 5 == 0) 0x00000000 else (0xFF shl 24) or ((i * 9 + seed * 29) and 0xFFFFFF)
    }

    @Test
    fun streamingMatchesBatchOpaque() {
        val n = 6
        val delay = 4
        val frames = (0 until n).map { OGGifFrame(opaque(it), delay) }
        val batch = OGGifEncoder.encode(W, H, frames)
        val stream = OGGifEncoder.encodeStreaming(W, H, n, delay) { opaque(it) }
        assertContentEquals(batch, stream, "streaming GIF bytes must equal batch encoding (opaque)")
        assertTrue(stream.size > 6 && stream[0] == 'G'.code.toByte(), "looks like a GIF")
    }

    @Test
    fun streamingMatchesBatchWithTransparency() {
        val n = 4
        val delay = 5
        val frames = (0 until n).map { OGGifFrame(withHoles(it), delay) }
        val batch = OGGifEncoder.encode(W, H, frames)
        val stream = OGGifEncoder.encodeStreaming(W, H, n, delay) { withHoles(it) }
        assertContentEquals(batch, stream, "streaming GIF bytes must equal batch encoding (with transparency)")
    }

    @Test
    fun streamingMatchesBatchNoDither() {
        val n = 5
        val delay = 3
        val frames = (0 until n).map { OGGifFrame(opaque(it * 2 + 1), delay) }
        val batch = OGGifEncoder.encode(W, H, frames, dither = OGGifDither.NONE)
        val stream = OGGifEncoder.encodeStreaming(W, H, n, delay, dither = OGGifDither.NONE) { opaque(it * 2 + 1) }
        assertContentEquals(batch, stream, "streaming GIF bytes must equal batch encoding (NONE dither)")
    }
}
