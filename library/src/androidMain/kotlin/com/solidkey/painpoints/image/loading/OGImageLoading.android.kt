package com.solidkey.painpoints.image.loading

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

/**
 * Largest edge (px) we keep when decoding. A phone screen is ~1080px wide, most textures render far
 * smaller, and GPUs on some devices cap textures at 2048px — so decoding a raw 12-megapixel gallery
 * photo (≈48 MB as ARGB_8888, potentially bigger than the max texture) is pure waste and a common
 * cause of jank/flicker. We downsample so the longest edge is ≤ this, which slashes memory and
 * upload cost with no visible quality loss at typical display sizes.
 */
private const val MAX_DECODE_DIM = 1536

/**
 * A small, byte-bounded cache of already-decoded bitmaps keyed by source (path / url / resource).
 * Without it, EVERY [OGImageView] instance decodes its own copy of the same image — so a screen that
 * shows the same photo many times (e.g. the game spawning several copies of one cropped photo, plus
 * every re-spawn) pays the full decode + allocation each time, thrashing the GC and the GPU. With it
 * the bitmap is decoded once and reused ("a reusable, compact format"). Evicted entries are NOT
 * recycled — a live [BitmapPainter] may still reference them, so we let GC reclaim them safely.
 */
private object OGBitmapCache {
    // ~48 MB ≈ a dozen downsampled ~1.5 MP bitmaps; plenty for reuse, small enough to be safe.
    private const val MAX_BYTES = 48 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = synchronized(cache) { cache.get(key) }
    fun put(key: String, bitmap: Bitmap) = synchronized(cache) { cache.put(key, bitmap) }
}

/**
 * A persistent, size-bounded **disk** cache of the raw bytes of remote images / GIFs, so a URL
 * fetched once isn't re-downloaded on the next app launch (the in-memory [OGBitmapCache] only lives
 * for the process). Content-addressed by [OGStableHash] of the URL, under `cacheDir/og_image_cache`.
 * When the directory grows past [MAX_BYTES] the least-recently-used files (by last-modified time) are
 * deleted. Any IO failure is swallowed — the cache is an optimisation, never a hard dependency.
 */
internal object OGImageDiskCache {
    private const val SUBDIR = "og_image_cache"
    private const val MAX_BYTES = 128L * 1024 * 1024 // 128 MB on disk

    private fun dir(cacheRoot: File): File = File(cacheRoot, SUBDIR).apply { if (!exists()) mkdirs() }

    fun read(cacheRoot: File, key: String): ByteArray? = try {
        val f = File(dir(cacheRoot), key)
        if (f.exists()) {
            f.setLastModified(System.currentTimeMillis()) // touch for LRU
            f.readBytes()
        } else null
    } catch (e: Exception) {
        Logger.w("OG>> disk cache read failed: ${e.message}"); null
    }

    fun write(cacheRoot: File, key: String, bytes: ByteArray) {
        try {
            val d = dir(cacheRoot)
            val tmp = File(d, "$key.tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(File(d, key)) // atomic swap so a reader never sees a half-written file
            evictIfNeeded(d)
        } catch (e: Exception) {
            Logger.w("OG>> disk cache write failed: ${e.message}")
        }
    }

    private fun evictIfNeeded(d: File) {
        val files = d.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        for (f in files.sortedBy { it.lastModified() }) { // oldest first
            if (total <= MAX_BYTES) break
            val len = f.length()
            if (f.delete()) total -= len
        }
    }
}

/** Power-of-two sample factor so the decoded bitmap's longest edge is ≤ [maxDim]. */
private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
    var sample = 1
    while (width / sample > maxDim || height / sample > maxDim) sample *= 2
    return sample
}

/** Decode `bytes`/file/resource with bounds-first downsampling capped at [MAX_DECODE_DIM]. */
private fun decodeSampled(decodeInto: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decodeInto(bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val opts = BitmapFactory.Options().apply {
        inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODE_DIM)
    }
    return decodeInto(opts)
}

/**
 * Read EXIF tag `0x0112` from the same source we decoded. `BitmapFactory` ignores it, so a portrait
 * photo decodes sideways unless we read the tag and rotate. Any failure (no EXIF, corrupt header)
 * degrades silently to [OGExifOrientation.NORMAL] — the pre-EXIF behaviour.
 */
private fun readExifOrientation(openStream: () -> InputStream): Int = try {
    openStream().use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }
} catch (e: Exception) {
    Logger.w("OG>> EXIF orientation read failed: ${e.message}")
    ExifInterface.ORIENTATION_NORMAL
}

/**
 * Rotate/mirror a decoded bitmap into its upright orientation using the shared [OGExifOrientation]
 * mapping (mirror-then-rotate-clockwise). Returns the bitmap unchanged when no correction is needed,
 * so the common upright case stays allocation-free.
 */
private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val t = OGExifOrientation.transformFor(orientation)
    if (t.isIdentity) return bitmap
    val matrix = Matrix().apply {
        if (t.mirrored) postScale(-1f, 1f)                 // horizontal mirror first…
        if (t.rotationDegrees != 0) postRotate(t.rotationDegrees.toFloat()) // …then clockwise rotation
    }
    return try {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: OutOfMemoryError) {
        Logger.w("OG>> EXIF rotation OOM, using un-rotated bitmap")
        bitmap
    }
}


@Composable
actual fun loadImageFromUrl(url: String, onError: ((String) -> Unit)?, onLoaded: (Painter) -> Unit) {
    Logger.i("OG>> loadImageFromUrl: $url")
    val cacheRoot = LocalContext.current.cacheDir
    LaunchedEffect(url) {
        val bitmap = OGBitmapCache.get(url)
            ?: withContext(Dispatchers.IO) { fetchImageFromUrl(url, cacheRoot) }?.also { OGBitmapCache.put(url, it) }
        if (bitmap == null) onError?.invoke("Failed to load image from URL: $url")
        onLoaded(bitmap?.let { BitmapPainter(it.asImageBitmap()) } ?: ColorPainter(Color.Yellow))
    }
}

/** Decode `bytes` into an upright, downsampled bitmap (shared by the disk-cache and network paths). */
private fun decodeUrlBytes(bytes: ByteArray): Bitmap? =
    decodeSampled { opts -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }
        ?.let { applyExifOrientation(it, readExifOrientation { ByteArrayInputStream(bytes) }) }

private fun fetchImageFromUrl(url: String, cacheRoot: File?): Bitmap? {
    val key = OGStableHash.hex(url)
    // 1) disk cache (survives app restarts), then 2) network → write-through to disk.
    cacheRoot?.let { OGImageDiskCache.read(it, key) }?.let { cached ->
        decodeUrlBytes(cached)?.let { return it }
    }
    val bytes = downloadBytes(url) ?: return null
    cacheRoot?.let { OGImageDiskCache.write(it, key, bytes) }
    return decodeUrlBytes(bytes).also { Logger.i("🌍!! OG>> Loaded image from $url, bytes=${bytes.size}") }
}

/** Download raw bytes for [url] with a Google-DNS fallback when host resolution fails. */
private fun downloadBytes(url: String): ByteArray? {
    return try {
        Logger.i("🌍 OG>> Trying to load image from: $url")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            doInput = true
            connect()
        }
        connection.inputStream.use { it.readBytes() }
    } catch (e: UnknownHostException) {
        Logger.e("⚠️ OG>> DNS resolution failed for: $url")
        Logger.w("🌐 OG>> Attempting fallback using Google DNS (8.8.8.8)")
        try {
            downloadBytesViaGoogleDNS(url)
        } catch (e2: Exception) {
            Logger.e("❌ OG>> First fallback attempt failed: ${e2.message}")
            try {
                downloadBytesViaGoogleDNS(url).also { Logger.i("✅ OG>> Fallback succeeded on second attempt.") }
            } catch (e3: Exception) {
                Logger.e("❌ OG>> Second fallback attempt failed: ${e3.message}")
                null
            }
        }
    } catch (e: Exception) {
        Logger.e("❌ OG>> Image load failed: ${e.message}")
        null
    }
}

private fun downloadBytesViaGoogleDNS(url: String): ByteArray {
    val fallbackIp = "8.8.8.8" // Google Public DNS
    val originalUrl = URL(url)
    val fallbackUrl = URL(
        originalUrl.protocol,
        fallbackIp,
        originalUrl.port.takeIf { it != -1 } ?: originalUrl.defaultPort,
        originalUrl.file
    )

    val connection = (fallbackUrl.openConnection() as HttpURLConnection).apply {
        doInput = true
        setRequestProperty("Host", originalUrl.host) // needed for SNI & CDN
        connect()
    }

    return connection.inputStream.use { it.readBytes() }
}


@Composable
actual fun loadImageFromPath(path: String, onError: ((String) -> Unit)?, onLoaded: (Painter) -> Unit) {
    if (path.isBlank()) {
        onError?.invoke("Image file path is blank.")
        return onLoaded(ColorPainter(Color.Red)) // ✅ Prevents crash
    }

    LaunchedEffect(path) {
        // Reuse the decoded bitmap if we've already loaded this file, else decode OFF the UI thread
        // (this used to run BitmapFactory.decodeFile synchronously inside the effect = a main-thread
        // stall on every spawn, a direct cause of the game's flicker) and cache it for reuse.
        val bitmap = OGBitmapCache.get(path)
            ?: withContext(Dispatchers.IO) { fetchImageFromFile(path) }?.also { OGBitmapCache.put(path, it) }
        if (bitmap == null) onError?.invoke("Image file not found or unreadable: $path")
        onLoaded(bitmap?.let { BitmapPainter(it.asImageBitmap()) } ?: ColorPainter(Color.Red))
    }
}

private fun fetchImageFromFile(path: String): Bitmap? {
    val file = File(path)
    return if (file.exists()) {
        decodeSampled { opts -> BitmapFactory.decodeFile(path, opts) }
            ?.let { applyExifOrientation(it, readExifOrientation { file.inputStream() }) }
    } else {
        Logger.w("OG>> Image file not found: $path")
        null
    }
}


@Composable
actual fun getDefaultImagePath(imageName: String): String {
    return LocalContext.current.filesDir.absolutePath + "/$imageName"
}


@Composable
actual fun loadImageFromResource(resource: String, onError: ((String) -> Unit)?, onLoaded: (Painter) -> Unit) {
    val context: Context = LocalContext.current

    LaunchedEffect(resource) {
        val key = "res:$resource"
        val bitmap = OGBitmapCache.get(key)
            ?: withContext(Dispatchers.IO) { fetchImageFromResource(resource, context) }?.also { OGBitmapCache.put(key, it) }
        if (bitmap == null) onError?.invoke("Image resource not found: $resource")
        onLoaded(bitmap?.let { BitmapPainter(it.asImageBitmap()) } ?: ColorPainter(Color.Red))
    }
}

private fun fetchImageFromResource(resource: String, context: Context): Bitmap? {
    val resourceId = context.resources.getIdentifier(resource, "drawable", context.packageName)
    if (resourceId == 0) {
        Logger.w("OG>> Image resource not found: $resource")
        return null
    }
    return decodeSampled { opts -> BitmapFactory.decodeResource(context.resources, resourceId, opts) }
}

@Composable
fun debugAvailableResources(context: Context) {
    val resources = context.resources
    val packageName = context.packageName

    val fields = Class.forName("$packageName.R\$drawable").fields
    for (field in fields) {
        try {
            val resId = field.getInt(null)
            println("🔹 Available Drawable: ${field.name} -> ID: $resId")
        } catch (e: Exception) {
            println("❌ Error reading drawable field: ${field.name}")
        }
    }
}
