package com.solidkey.painpoints.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OGMotionWindTest {

    private fun makeFrame(width: Int, height: Int, fill: Int = 100): ByteArray =
        ByteArray(width * height) { fill.toByte() }

    @Test
    fun `still frames return STILL`() {
        val frame = makeFrame(64, 48, 100)
        val result = computeMotionWind(frame, frame.copyOf(), 64, 48, 18)
        assertEquals(OGMotionWindSample.STILL, result)
    }

    @Test
    fun `all-right motion returns positive directionX`() {
        val prev = makeFrame(64, 48, 50)
        val curr = makeFrame(64, 48, 50)
        // Bright blob on the right half only (x >= 32)
        for (y in 0 until 48) for (x in 32 until 64) curr[y * 64 + x] = 120
        val result = computeMotionWind(prev, curr, 64, 48, 18)
        assertTrue(result.directionX > 0.3f, "Expected directionX > 0.3 but was ${result.directionX}")
        assertTrue(result.magnitude > 0f, "Expected non-zero magnitude")
    }

    @Test
    fun `all-left motion returns negative directionX`() {
        val prev = makeFrame(64, 48, 50)
        val curr = makeFrame(64, 48, 50)
        // Bright blob on the left half only (x < 32)
        for (y in 0 until 48) for (x in 0 until 32) curr[y * 64 + x] = 120
        val result = computeMotionWind(prev, curr, 64, 48, 18)
        assertTrue(result.directionX < -0.3f, "Expected directionX < -0.3 but was ${result.directionX}")
    }

    @Test
    fun `centered motion returns directionX near zero`() {
        val prev = makeFrame(64, 48, 50)
        val curr = makeFrame(64, 48, 50)
        // Motion symmetrically around center
        for (y in 0 until 48) for (x in 20 until 44) curr[y * 64 + x] = 120
        val result = computeMotionWind(prev, curr, 64, 48, 18)
        assertTrue(kotlin.math.abs(result.directionX) < 0.1f,
            "Expected directionX near 0 but was ${result.directionX}")
    }

    @Test
    fun `below-threshold diff returns STILL`() {
        val prev = makeFrame(64, 48, 100)
        val curr = makeFrame(64, 48, 110)  // diff = 10, below threshold 18
        val result = computeMotionWind(prev, curr, 64, 48, threshold = 18)
        assertEquals(OGMotionWindSample.STILL, result)
    }

    @Test
    fun `magnitude clamped to 0_1`() {
        val prev = makeFrame(64, 48, 0)
        val curr = makeFrame(64, 48, 255)   // maximum possible diff everywhere
        val result = computeMotionWind(prev, curr, 64, 48, 18)
        assertTrue(result.magnitude in 0f..1f, "magnitude out of range: ${result.magnitude}")
        assertEquals(1f, result.magnitude)
    }

    @Test
    fun `processor smooths and applies mirror`() {
        val samples = mutableListOf<OGMotionWindSample>()
        val spec = OGMotionWindSpec(mirror = true)
        val processor = OGMotionWindProcessor(spec) { samples.add(it) }

        val prev = makeFrame(64, 48, 50)
        val curr = makeFrame(64, 48, 50).also {
            // Motion on the right (x >= 32) → raw directionX > 0 → mirrored < 0
            for (y in 0 until 48) for (x in 32 until 64) it[y * 64 + x] = 120
        }
        processor.onLumaFrame(prev, 0L)
        processor.onLumaFrame(curr, 33_000_000L)  // ~30fps

        assertEquals(1, samples.size)
        assertTrue(samples[0].directionX < 0f,
            "mirror=true should negate: got ${samples[0].directionX}")
    }

    @Test
    fun `mismatched buffer sizes return STILL`() {
        val result = computeMotionWind(ByteArray(10), ByteArray(12), 4, 3, 18)
        assertEquals(OGMotionWindSample.STILL, result)
    }
}
