package com.solidkey.painpoints.image.gif

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.loading.OGIosDiskCache
import com.solidkey.painpoints.image.loading.OGStableHash
import com.solidkey.painpoints.source.OGSource
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithContentsOfURL

/**
 * iOS implementation of [rememberOGAnimatedPainter]. Reads the source bytes off the main thread,
 * decodes every frame with Skia's [Codec] (which handles GIF frame compositing / disposal), and
 * hands the frames to a shared [OGAnimatedImagePainter] that cycles them honouring `loop`/`speed`.
 */
@Composable
actual fun rememberOGAnimatedPainter(
    source: OGSource,
    loop: Boolean,
    speed: Float,
    onError: ((String) -> Unit)?
): Painter? {
    val animation by produceState<OGGifAnimation?>(null, source) {
        value = withContext(Dispatchers.Default) {
            try {
                val bytes = readGifBytes(source)
                if (bytes == null || bytes.isEmpty()) {
                    onError?.invoke("Failed to load GIF: $source")
                    null
                } else {
                    decodeGif(bytes)
                }
            } catch (e: Throwable) {
                Logger.e("OG>> GIF decode failed: ${e.message}")
                onError?.invoke("GIF decode failed: ${e.message}")
                null
            }
        }
    }

    val anim = animation
    val painter = remember(anim) { anim?.let { OGAnimatedImagePainter(it) } }
    LaunchedEffect(painter, loop, speed) { painter?.animate(loop, speed) }
    return painter
}

/** Decode all frames + per-frame durations of an animated image via Skia. */
private fun decodeGif(bytes: ByteArray): OGGifAnimation? {
    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
    try {
        val frameCount = codec.frameCount
        if (frameCount <= 0) return null

        val info = codec.imageInfo
        // Unlike Android (which decodes frames on demand), iOS holds EVERY frame in memory, so a long
        // or huge GIF can OOM. Cap per-frame edge AND the total-frames budget by decoding downscaled.
        val scale = OGGifDecodeBudget.frameScale(info.width, info.height, frameCount, allFramesInMemory = true)
        val targetW = (info.width * scale).toInt().coerceAtLeast(1)
        val targetH = (info.height * scale).toInt().coerceAtLeast(1)

        val framesInfo = codec.framesInfo
        val bitmap = Bitmap()
        bitmap.allocPixels(info)
        // Only allocate a scaling surface when we actually need to downscale.
        val surface = if (scale < 1f) Surface.makeRasterN32Premul(targetW, targetH) else null

        val frames = ArrayList<ImageBitmap>(frameCount)
        val durations = ArrayList<Int>(frameCount)
        try {
            for (i in 0 until frameCount) {
                // Decode sequentially into the SAME bitmap so Skia can composite frames that
                // depend on the previous one (disposal method "keep").
                codec.readPixels(bitmap, i)
                val frame = if (surface != null) {
                    val img = Image.makeFromBitmap(bitmap)
                    try {
                        surface.canvas.clear(0) // transparent — each bitmap is already a full composited frame
                        surface.canvas.drawImageRect(img, Rect.makeWH(targetW.toFloat(), targetH.toFloat()))
                        surface.makeImageSnapshot().toComposeImageBitmap()
                    } finally {
                        img.close()
                    }
                } else {
                    Image.makeFromBitmap(bitmap).toComposeImageBitmap()
                }
                frames += frame
                val duration = framesInfo.getOrNull(i)?.duration ?: 0
                durations += if (duration <= 0) OGGifClock.DEFAULT_FRAME_MS else duration
            }
        } finally {
            bitmap.close()
            surface?.close()
        }
        return OGGifAnimation(frames, durations)
    } finally {
        codec.close()
    }
}

/** Fetch the raw bytes of [source] (URL / file path — on iOS a resource resolves to a file path). */
@OptIn(ExperimentalForeignApi::class)
private fun readGifBytes(source: OGSource): ByteArray? {
    val data: NSData? = when (source) {
        is OGSource.Url -> {
            // Reuse a previously-downloaded GIF across launches (disk cache), else fetch + write-through.
            val key = OGStableHash.hex(source.url)
            OGIosDiskCache.read(key)
                ?: NSURL.URLWithString(source.url)?.let { NSData.dataWithContentsOfURL(it) }
                    ?.also { OGIosDiskCache.write(key, it) }
        }
        is OGSource.FilePath -> NSData.dataWithContentsOfFile(source.path)
        is OGSource.Resource -> NSData.dataWithContentsOfFile(source.resource)
    }
    return data?.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray =
    this.bytes?.reinterpret<ByteVar>()?.readBytes(this.length.toInt()) ?: ByteArray(0)
