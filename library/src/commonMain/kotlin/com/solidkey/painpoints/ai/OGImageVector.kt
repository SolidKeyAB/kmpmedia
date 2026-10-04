package com.solidkey.painpoints.ai

import androidx.compose.ui.graphics.ImageBitmap

/**
 * The pixel dimensions of an image you hand to a vision model, so a prompt can state the subject's
 * aspect ratio (and the model keeps proportions). Coordinates in the reply stay normalized `0..1`,
 * so this is context only — no scaling is implied.
 */
data class OGImageInfo(val width: Int, val height: Int) {
    /** `width / height` (returns `1f` for a zero-height image rather than diverging). */
    val aspect: Float get() = if (height != 0) width.toFloat() / height.toFloat() else 1f

    companion object {
        /** Read the dimensions straight off a decoded [ImageBitmap]. */
        fun of(bitmap: ImageBitmap): OGImageInfo = OGImageInfo(bitmap.width, bitmap.height)
    }
}

/**
 * Encode this image as PNG bytes. This is an **app-side convenience** for attaching an image to a
 * vision call — the library itself performs **no** network I/O and bundles **no** AI SDK; this only
 * serializes the pixels you already hold. Implemented per platform (Android `Bitmap.compress`,
 * iOS Skia). Runs at generate time, never per frame.
 */
expect fun ImageBitmap.toPngBytes(): ByteArray

/**
 * Encode this image as a base64 PNG string, ready to drop into a vision model's image payload
 * (e.g. an Anthropic `image` content block's `data`, or an OpenAI `data:image/png;base64,...` URL).
 * Pairs with [OGAiVector.imageToVectorPrompt]. Network-free: it only serializes the bytes.
 */
fun ImageBitmap.toBase64Png(): String = toPngBytes().encodeBase64()

// --- A tiny, dependency-free base64 encoder ------------------------------------------------------
// Deliberately hand-rolled (not kotlin.io.encoding.Base64) to avoid leaking an @ExperimentalEncodingApi
// opt-in into the public surface and to guarantee byte-identical output on every platform.

private const val B64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

internal fun ByteArray.encodeBase64(): String {
    if (isEmpty()) return ""
    val out = StringBuilder((size + 2) / 3 * 4)
    var i = 0
    while (i + 2 < size) {
        val n = (this[i].toInt() and 0xFF shl 16) or
            (this[i + 1].toInt() and 0xFF shl 8) or
            (this[i + 2].toInt() and 0xFF)
        out.append(B64_ALPHABET[n ushr 18 and 0x3F])
        out.append(B64_ALPHABET[n ushr 12 and 0x3F])
        out.append(B64_ALPHABET[n ushr 6 and 0x3F])
        out.append(B64_ALPHABET[n and 0x3F])
        i += 3
    }
    when (size - i) {
        1 -> {
            val n = this[i].toInt() and 0xFF shl 16
            out.append(B64_ALPHABET[n ushr 18 and 0x3F])
            out.append(B64_ALPHABET[n ushr 12 and 0x3F])
            out.append("==")
        }
        2 -> {
            val n = (this[i].toInt() and 0xFF shl 16) or (this[i + 1].toInt() and 0xFF shl 8)
            out.append(B64_ALPHABET[n ushr 18 and 0x3F])
            out.append(B64_ALPHABET[n ushr 12 and 0x3F])
            out.append(B64_ALPHABET[n ushr 6 and 0x3F])
            out.append("=")
        }
    }
    return out.toString()
}
