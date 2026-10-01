package com.solidkey.painpoints.compositor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max

/**
 * Render this composition to a shareable **H.264 MP4** (`ByteArray`), the second "export" target of the
 * on-device compositor (Bet 3) after [exportGif]. Every frame from `0` to [OGComposition.durationMs] is
 * rendered offscreen at [OGComposition.fps], flattened onto an opaque [background] (MP4/H.264 has no
 * alpha), then handed to the platform's hardware video encoder — Android `MediaCodec` + `MediaMuxer`,
 * iOS `AVAssetWriter` — through the shared [OGMp4Encoder] contract. No third-party dependency: both
 * platforms use the OS encoder that already ships on the device.
 *
 * Unlike [exportGif], the actual byte stream is **not** identical across platforms (each OS has its own
 * H.264 encoder), but the *input* to those encoders is: the exact same rendered, flattened frames — the
 * colour math (compositing + RGB→YUV/BGRA) lives in shared, tested code here, so what each encoder is
 * asked to compress looks the same everywhere.
 *
 * H.264 requires **even** dimensions, so an odd-width/height composition is cropped by its last row /
 * column (see [evenDown]); pick even canvas sizes to avoid losing that edge. The library returns the
 * encoded bytes rather than writing a file — where to save/share is the app's choice and stays
 * platform-free here. This is a one-shot, allocation-heavy call (one bitmap per frame); use
 * `OGCompositionView` for a live preview, not this.
 *
 * @param bitRate target average bit rate in bits/sec; defaults to a resolution- and fps-scaled value
 *   (see [defaultMp4BitRate]).
 * @param background the opaque colour transparent pixels resolve to (H.264 cannot store alpha). Its own
 *   alpha is ignored — it is always treated as fully opaque.
 */
fun OGComposition.exportMp4(
    bitRate: Int = defaultMp4BitRate(width, height, fps),
    background: Color = Color.Black,
): ByteArray {
    val outW = evenDown(width)
    val outH = evenDown(height)
    val bgOpaque = background.toArgb() or ALPHA_OPAQUE
    val encoder = OGMp4Encoder(outW, outH, max(1, fps), bitRate)
    try {
        for (i in 0 until frameCount) {
            val pixels = renderFrame(frameTimeMs(i)).toArgbPixels()
            encoder.encodeFrame(cropFlattenFrame(pixels, width, height, outW, outH, bgOpaque))
        }
        return encoder.finish()
    } catch (t: Throwable) {
        encoder.abort()
        throw t
    }
}

/**
 * A device video encoder. One is created per export ([exportMp4]) sized to the final even width/height,
 * fed one opaque, correctly-sized `0xFFRRGGBB` frame at a time in order, then [finish]ed to the encoded
 * MP4 bytes. The heavy colour conversion is done by the shared helpers below; the platform actual only
 * plumbs the resulting pixels into the OS encoder and muxes the output.
 */
internal expect class OGMp4Encoder(width: Int, height: Int, fps: Int, bitRate: Int) {
    /** Encode one frame: `argb` is row-major `0xFFRRGGBB`, length exactly `width * height`, alpha ignored. */
    fun encodeFrame(argb: IntArray)

    /** Flush the encoder/muxer and return the finished MP4 file's bytes. */
    fun finish(): ByteArray

    /** Release encoder/muxer resources without producing output (called if a frame render fails midway). */
    fun abort()
}

/** A resolution- and fps-scaled default H.264 bit rate (bits/sec), clamped to a sane 0.75–16 Mbps band. */
fun defaultMp4BitRate(width: Int, height: Int, fps: Int): Int =
    (width.toLong() * height.toLong() * max(1, fps).toLong() / 5L)
        .coerceIn(750_000L, 16_000_000L)
        .toInt()

/** Largest even number `<= n`, but never below 2 (H.264 needs even, non-zero dimensions). */
internal fun evenDown(n: Int): Int = max(2, n and 1.inv())

private const val ALPHA_OPAQUE = 0xFF shl 24

/**
 * Crop a `srcW × srcH` frame to `outW × outH` (dropping the extra right column / bottom row) while
 * compositing every pixel onto the opaque [bgOpaque] (see [compositeOverOpaque]). The result is a fully
 * opaque `outW × outH` frame ready for a video encoder.
 */
internal fun cropFlattenFrame(
    argb: IntArray,
    srcW: Int,
    srcH: Int,
    outW: Int,
    outH: Int,
    bgOpaque: Int,
): IntArray {
    val out = IntArray(outW * outH)
    var o = 0
    for (y in 0 until outH) {
        val rowStart = y * srcW
        for (x in 0 until outW) {
            out[o++] = compositeOverOpaque(argb[rowStart + x], bgOpaque)
        }
    }
    return out
}

/**
 * Composite a straight-alpha `0xAARRGGBB` [src] over the opaque [bgOpaque], returning an opaque
 * `0xFFRRGGBB`. Fully-opaque and fully-transparent pixels take fast paths; everything else is a standard
 * `src*a + bg*(255-a)` per-channel blend (integer, rounded).
 */
internal fun compositeOverOpaque(src: Int, bgOpaque: Int): Int {
    val a = (src ushr 24) and 0xFF
    if (a == 0xFF) return src or ALPHA_OPAQUE
    if (a == 0) return bgOpaque or ALPHA_OPAQUE
    val inv = 255 - a
    val sr = (src ushr 16) and 0xFF
    val sg = (src ushr 8) and 0xFF
    val sb = src and 0xFF
    val br = (bgOpaque ushr 16) and 0xFF
    val bg = (bgOpaque ushr 8) and 0xFF
    val bb = bgOpaque and 0xFF
    val r = (sr * a + br * inv + 127) / 255
    val g = (sg * a + bg * inv + 127) / 255
    val b = (sb * a + bb * inv + 127) / 255
    return ALPHA_OPAQUE or (r shl 16) or (g shl 8) or b
}

/** A planar I420 (YUV 4:2:0) frame: full-res [y], then quarter-res [u] and [v]. Used by the Android encoder. */
internal class OGI420(
    val y: ByteArray,
    val u: ByteArray,
    val v: ByteArray,
    val width: Int,
    val height: Int,
)

/**
 * Convert an opaque `0xFFRRGGBB` frame to planar I420 using the integer BT.601 (video-range) transform —
 * the layout Android's `MediaCodec` flexible YUV420 input expects. Chroma is 4:2:0 subsampled by taking
 * the top-left pixel of each 2×2 block (fast and visually fine for export). Assumes even [w]/[h].
 */
internal fun argbToI420(argb: IntArray, w: Int, h: Int): OGI420 {
    val yPlane = ByteArray(w * h)
    val cw = w / 2
    val ch = h / 2
    val uPlane = ByteArray(cw * ch)
    val vPlane = ByteArray(cw * ch)
    for (j in 0 until h) {
        for (i in 0 until w) {
            val p = argb[j * w + i]
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            yPlane[j * w + i] = rgbToY(r, g, b).toByte()
        }
    }
    for (j in 0 until ch) {
        for (i in 0 until cw) {
            val p = argb[(j * 2) * w + (i * 2)]
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            uPlane[j * cw + i] = rgbToU(r, g, b).toByte()
            vPlane[j * cw + i] = rgbToV(r, g, b).toByte()
        }
    }
    return OGI420(yPlane, uPlane, vPlane, w, h)
}

/** Convert an opaque `0xFFRRGGBB` frame to packed `B,G,R,A` bytes — the layout iOS's 32BGRA pixel buffer wants. */
internal fun argbToBgra(argb: IntArray): ByteArray {
    val out = ByteArray(argb.size * 4)
    var o = 0
    for (p in argb) {
        out[o++] = (p and 0xFF).toByte()            // B
        out[o++] = ((p ushr 8) and 0xFF).toByte()   // G
        out[o++] = ((p ushr 16) and 0xFF).toByte()  // R
        out[o++] = 0xFF.toByte()                     // A (opaque)
    }
    return out
}

// BT.601 video-range, the classic integer coefficients. Each clamps to a byte.
internal fun rgbToY(r: Int, g: Int, b: Int): Int = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
internal fun rgbToU(r: Int, g: Int, b: Int): Int = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
internal fun rgbToV(r: Int, g: Int, b: Int): Int = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
