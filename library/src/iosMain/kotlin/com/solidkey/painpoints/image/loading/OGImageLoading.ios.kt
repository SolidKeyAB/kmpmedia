package com.solidkey.painpoints.image.loading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSLock
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

/**
 * Longest edge (pt) we keep when decoding — the iOS twin of Android's `MAX_DECODE_DIM`. A raw gallery
 * photo can be 12 MP; capping it keeps memory + the PNG round-trip in [toComposeImageBitmap] cheap
 * with no visible quality loss at typical display sizes. Kept equal to Android so both platforms
 * behave the same.
 */
private const val MAX_DECODE_DIM = 1536.0

// A small, bounded cache of decoded bitmaps keyed by source (path / url / "res:<name>"), so repeated
// OGImageViews of the same image reuse ONE decode instead of each re-decoding — the iOS twin of
// Android's OGBitmapCache. Guarded by an NSLock because the URL loader completes on a background
// queue. Evicted entries are just dropped (Skia ImageBitmaps are immutable + GC-managed).
private const val OG_CACHE_MAX = 24
private val ogCacheLock = NSLock()
private val ogBitmapCache = mutableMapOf<String, ImageBitmap>()

private fun ogCacheGet(key: String): ImageBitmap? {
    ogCacheLock.lock()
    try {
        return ogBitmapCache[key]
    } finally {
        ogCacheLock.unlock()
    }
}

private fun ogCachePut(key: String, bitmap: ImageBitmap) {
    ogCacheLock.lock()
    try {
        if (ogBitmapCache.size >= OG_CACHE_MAX) {
            ogBitmapCache.keys.firstOrNull()?.let { ogBitmapCache.remove(it) }
        }
        ogBitmapCache[key] = bitmap
    } finally {
        ogCacheLock.unlock()
    }
}

/** Downscale a [UIImage] so its longest edge is ≤ [maxDim] (drawn at scale 1 so pixels == points). */
@OptIn(ExperimentalForeignApi::class)
private fun UIImage.downscaledToMax(maxDim: Double): UIImage {
    val (w, h) = this.size.useContents { Pair(width, height) }
    val longest = maxOf(w, h)
    if (longest <= maxDim || longest <= 0.0) return this
    val scale = maxDim / longest
    val newW = w * scale
    val newH = h * scale
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(newW, newH), false, 1.0)
    this.drawInRect(CGRectMake(0.0, 0.0, newW, newH))
    val resized = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return resized ?: this
}


@Composable
actual fun loadImageFromUrl(url: String, onError: ((String) -> Unit)?, onLoaded: (Painter) -> Unit) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(url) {
        ogCacheGet(url)?.let { onLoaded(BitmapPainter(it)); return@LaunchedEffect }
        scope.launch {
            try {
                fetchImageFromUrl(url) { bitmap ->
                    if (bitmap == null) onError?.invoke("Failed to load image from URL: $url")
                    else ogCachePut(url, bitmap)
                    onLoaded(bitmap?.let { BitmapPainter(it) } ?: ColorPainter(Color.Cyan))
                }
            } catch (e: Exception) {
                onError?.invoke("Failed to load image from URL: $url (${e.message})")
                onLoaded(ColorPainter(Color.Cyan))
            }
        }
    }
}

private fun fetchImageFromUrl(url: String, onComplete: (ImageBitmap?) -> Unit) {
    val nsUrl = NSURL(string = url)
    val request = NSURLRequest.requestWithURL(nsUrl)
    val session = NSURLSession.sharedSession
    val task = session.dataTaskWithRequest(request) { data, _, error ->
        if (error != null) {
            println("❌ Error loading image from URL: ${error.localizedDescription}")
            onComplete(null)
            return@dataTaskWithRequest
        }
        val uiImage = data?.let { UIImage.imageWithData(it) }?.downscaledToMax(MAX_DECODE_DIM)
        onComplete(uiImage?.toComposeImageBitmap())
    }
    task.resume()
}


@Composable
actual fun loadImageFromPath(path: String, onError: ((String) -> Unit)?, onLoaded: (Painter) -> Unit) {
    LaunchedEffect(path) {
        ogCacheGet(path)?.let { onLoaded(BitmapPainter(it)); return@LaunchedEffect }
        // Decode OFF the main thread (mirrors Android's Dispatchers.IO) so a large gallery photo
        // doesn't stall the UI on first spawn — the same main-thread stall we removed on Android —
        // then cache it for reuse. downscaledToMax is already run off-main in the URL path below.
        val bitmap = withContext(Dispatchers.Default) { decodeImageFromFile(path) }
        if (bitmap == null) onError?.invoke("Failed to load image from path: $path")
        else ogCachePut(path, bitmap)
        onLoaded(bitmap?.let { BitmapPainter(it) } ?: ColorPainter(Color.Gray))
    }
}

@Composable
actual fun loadImageFromResource(resource: String, onError: ((String) -> Unit)?, onLoaded: (Painter) -> Unit) {
    LaunchedEffect(resource) {
        val key = "res:$resource"
        ogCacheGet(key)?.let { onLoaded(BitmapPainter(it)); return@LaunchedEffect }
        val bitmap = withContext(Dispatchers.Default) { decodeImageFromResource(resource) }
        if (bitmap == null) onError?.invoke("Failed to load image resource: $resource")
        else ogCachePut(key, bitmap)
        onLoaded(bitmap?.let { BitmapPainter(it) } ?: ColorPainter(Color.Gray))
    }
}

private fun decodeImageFromFile(path: String): ImageBitmap? =
    UIImage.imageWithContentsOfFile(path)?.downscaledToMax(MAX_DECODE_DIM)?.toComposeImageBitmap()

private fun decodeImageFromResource(resource: String): ImageBitmap? =
    UIImage.imageWithContentsOfFile(resource)?.downscaledToMax(MAX_DECODE_DIM)?.toComposeImageBitmap()


// Helper: convert a decoded UIImage into a Compose ImageBitmap (via PNG → Skia).
fun UIImage.toComposeImageBitmap(): ImageBitmap {
    val pngData = UIImagePNGRepresentation(this)
        ?: throw IllegalArgumentException("❌ OGImageProcessor >> Failed to get PNG data from UIImage")

    val byteArray = pngData.toByteArray()
    val skiaImage = Image.makeFromEncoded(byteArray)
    return skiaImage.toComposeImageBitmap()
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    return this.bytes?.reinterpret<ByteVar>()?.readBytes(this.length.toInt()) ?: ByteArray(0)
}

@Composable
actual fun getDefaultImagePath(imageName: String): String {
    return platform.Foundation.NSSearchPathForDirectoriesInDomains(
        platform.Foundation.NSDocumentDirectory, platform.Foundation.NSUserDomainMask, true
    ).firstOrNull()?.let { "$it/$imageName" } ?: ""
}
