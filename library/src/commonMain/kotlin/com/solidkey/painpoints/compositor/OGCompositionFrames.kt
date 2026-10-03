package com.solidkey.painpoints.compositor

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Frame-sequence export — the planned follow-up to [exportGif] / [exportMp4] on the on-device compositor
 * (Bet 3). Where those encode the whole timeline into one file, this hands you the **individual frames**,
 * so you can save a PNG sequence, feed a custom encoder, run per-frame analysis, or build a filmstrip.
 *
 * Each frame is rendered with the same pure-Compose [renderFrame] the GIF/MP4 exporters use, so a frame
 * looks identical on Android and iOS. All of these allocate a bitmap per frame and should run **off the
 * main thread**; for a long composition prefer [forEachFrame] (streams one frame at a time) over
 * [exportFrames] (holds them all).
 */

/**
 * The absolute time (ms) of every frame the sequence will render — `frameCount` evenly-spaced instants
 * from 0. Pure (no rendering), so you can plan/label a frame export, or unit-test the timeline, without
 * a graphics backend.
 */
fun OGComposition.frameTimesMs(): List<Long> = List(frameCount) { frameTimeMs(it) }

/** Render the frame at [index] (`0 until frameCount`); index is clamped into range. */
fun OGComposition.renderFrameAt(index: Int): ImageBitmap =
    renderFrame(frameTimeMs(index.coerceIn(0, frameCount - 1)))

/**
 * Stream every frame of the timeline, lowest memory: [action] is called once per frame with its index,
 * absolute time (ms) and freshly-rendered bitmap, and nothing is retained between calls. Ideal for
 * writing a PNG sequence to disk without holding the whole movie in memory.
 */
fun OGComposition.forEachFrame(action: (index: Int, timeMs: Long, frame: ImageBitmap) -> Unit) {
    for (i in 0 until frameCount) {
        val t = frameTimeMs(i)
        action(i, t, renderFrame(t))
    }
}

/**
 * Render the whole timeline to a frame sequence — one [ImageBitmap] per frame, length [frameCount].
 * Allocation-heavy (holds every frame); for long compositions stream with [forEachFrame] instead.
 */
fun OGComposition.exportFrames(): List<ImageBitmap> = List(frameCount) { renderFrameAt(it) }

/**
 * Render the whole timeline to raw, row-major `0xAARRGGBB` pixel arrays (one per frame), the most
 * portable form — hand them to any custom encoder or run per-pixel analysis. Goes through the same
 * colour-normalising [toArgbPixels] the GIF exporter uses, so frames are identical across platforms.
 */
fun OGComposition.exportFramesArgb(): List<IntArray> = List(frameCount) { renderFrameAt(it).toArgbPixels() }
