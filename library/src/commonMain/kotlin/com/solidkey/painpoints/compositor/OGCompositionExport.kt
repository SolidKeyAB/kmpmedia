package com.solidkey.painpoints.compositor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap

/**
 * Render this composition to a shareable **animated GIF** (`0xAARRGGBB` frames → GIF89a bytes), the
 * "export" half of the on-device compositor (Bet 3). Every frame from `0` to [OGComposition.durationMs]
 * is rendered offscreen at [OGComposition.fps], quantized to a shared palette and LZW-compressed by the
 * pure-Kotlin [OGGifEncoder], so the exact same bytes come out on Android and iOS.
 *
 * The library returns the encoded bytes rather than writing a file — where to save/share is the app's
 * choice and stays platform-free here. This is a one-shot, allocation-heavy call (one bitmap per frame);
 * use `OGCompositionView` for a live preview, not this.
 *
 * @param loopCount `0` = loop forever, `n` = play `n` times then stop.
 * @param alphaThreshold pixels below this alpha become the single transparent colour; pass `> 255` to
 *   force an opaque GIF.
 * @param dither palette mapping; [OGGifDither.FLOYD_STEINBERG] (default) gives smooth, band-free
 *   gradients, [OGGifDither.NONE] a plain nearest match.
 */
fun OGComposition.exportGif(
    loopCount: Int = 0,
    alphaThreshold: Int = OGGifEncoder.DEFAULT_ALPHA_THRESHOLD,
    dither: OGGifDither = OGGifDither.FLOYD_STEINBERG,
): ByteArray {
    val frames = ArrayList<OGGifFrame>(frameCount)
    for (i in 0 until frameCount) {
        val bitmap = renderFrame(frameTimeMs(i))
        frames.add(OGGifFrame(bitmap.toArgbPixels(), frameDelayCs))
    }
    return OGGifEncoder.encode(width, height, frames, loopCount, alphaThreshold, dither)
}

/**
 * Read an [ImageBitmap] into a row-major `0xAARRGGBB` array (length `width * height`). Goes through
 * Compose's `toPixelMap()` + `Color.toArgb()`, which normalize colour-space and premultiplication the
 * same way on both platforms — so a rendered frame quantizes identically everywhere.
 */
fun ImageBitmap.toArgbPixels(): IntArray {
    val pixels = toPixelMap()
    val out = IntArray(width * height)
    var i = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            out[i++] = pixels[x, y].toArgb()
        }
    }
    return out
}
