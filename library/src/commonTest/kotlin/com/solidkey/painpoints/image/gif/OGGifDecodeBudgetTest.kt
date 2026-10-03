package com.solidkey.painpoints.image.gif

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OGGifDecodeBudgetTest {

    @Test
    fun smallGifIsNotDownscaled() {
        assertEquals(1f, OGGifDecodeBudget.frameScale(200, 200, 30, allFramesInMemory = true))
        assertEquals(1f, OGGifDecodeBudget.frameScale(640, 480, 10, allFramesInMemory = false))
    }

    @Test
    fun hugeDimensionsAreEdgeCapped() {
        // 4096 longest edge → scale to MAX_FRAME_EDGE / 4096
        val s = OGGifDecodeBudget.frameScale(4096, 2048, 2, allFramesInMemory = false)
        assertEquals(OGGifDecodeBudget.MAX_FRAME_EDGE / 4096f, s, 0.0001f)
        assertTrue(4096 * s <= OGGifDecodeBudget.MAX_FRAME_EDGE + 1)
    }

    @Test
    fun manyFramesStayWithinTotalBudgetWhenAllInMemory() {
        // 500 frames of 400x400 = 500 * 640KB = 320MB native → must be capped to the 64MB budget
        val w = 400; val h = 400; val n = 500
        val s = OGGifDecodeBudget.frameScale(w, h, n, allFramesInMemory = true)
        assertTrue(s < 1f, "expected downscale, got $s")
        val resultingBytes = (w * s).toLong() * (h * s).toLong() * 4L * n
        assertTrue(
            resultingBytes <= OGGifDecodeBudget.TOTAL_BUDGET_BYTES,
            "resulting $resultingBytes exceeds budget ${OGGifDecodeBudget.TOTAL_BUDGET_BYTES}",
        )
    }

    @Test
    fun budgetIgnoredWhenFramesDecodedOnDemand() {
        // Same heavy GIF but Android-style (on-demand) → only the edge cap applies, not the budget.
        val onDemand = OGGifDecodeBudget.frameScale(400, 400, 500, allFramesInMemory = false)
        assertEquals(1f, onDemand) // 400px < edge cap, and budget doesn't apply
    }

    @Test
    fun degenerateSizesReturnOne() {
        assertEquals(1f, OGGifDecodeBudget.frameScale(0, 100, 10, allFramesInMemory = true))
        assertEquals(1f, OGGifDecodeBudget.frameScale(100, 0, 10, allFramesInMemory = true))
    }
}
