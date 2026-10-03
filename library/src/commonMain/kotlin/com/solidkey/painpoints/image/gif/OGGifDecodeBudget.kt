package com.solidkey.painpoints.image.gif

import kotlin.math.sqrt

/**
 * Decides how far to downscale an animated GIF's frames at decode time so a large GIF can't blow up
 * memory.
 *
 * Two independent caps, combined by taking the more aggressive (smaller) scale:
 *  - **Per-frame edge cap** — the longest edge of a frame is capped to [MAX_FRAME_EDGE], the same
 *    idea as still-image downsampling. Protects against a few huge-dimension frames.
 *  - **Total-frames budget** — only relevant where *every* frame is held decoded in memory at once
 *    (iOS Skia decode). The sum `frameCount × width × height × 4` is capped to [TOTAL_BUDGET_BYTES].
 *    Protects against a long GIF of hundreds of modest frames. Android's `AnimatedImageDrawable`
 *    decodes on demand, so it passes `allFramesInMemory = false` and only the edge cap applies.
 *
 * Pure maths, unit-tested on JVM + iOS; the actual pixel resampling is done per platform.
 */
internal object OGGifDecodeBudget {
    const val MAX_FRAME_EDGE = 1024
    const val TOTAL_BUDGET_BYTES = 64L * 1024 * 1024 // 64 MB of decoded frames

    /**
     * Scale factor in `(0, 1]` to decode GIF frames at. `1f` means no downscale needed. Taking the
     * min of both caps guarantees the resulting in-memory total (`totalNative × scale²`) stays within
     * [TOTAL_BUDGET_BYTES] when [allFramesInMemory] is set.
     */
    fun frameScale(width: Int, height: Int, frameCount: Int, allFramesInMemory: Boolean): Float {
        if (width <= 0 || height <= 0) return 1f
        val longest = maxOf(width, height)
        var scale = if (longest > MAX_FRAME_EDGE) MAX_FRAME_EDGE.toFloat() / longest else 1f
        if (allFramesInMemory && frameCount > 1) {
            val totalBytes = width.toLong() * height.toLong() * 4L * frameCount.toLong()
            if (totalBytes > TOTAL_BUDGET_BYTES) {
                val budgetScale = sqrt(TOTAL_BUDGET_BYTES.toDouble() / totalBytes.toDouble()).toFloat()
                scale = minOf(scale, budgetScale)
            }
        }
        return scale.coerceIn(0.05f, 1f)
    }
}
