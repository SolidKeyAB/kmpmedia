package com.solidkey.painpoints.image.loading

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

/**
 * The iOS twin of Android's `OGImageDiskCache`: a persistent, size-bounded cache of the raw bytes of
 * remote images / GIFs under `Caches/og_image_cache`, keyed by [OGStableHash] of the URL, so a URL
 * fetched once survives app restarts. iOS also purges the Caches directory itself under storage
 * pressure, so this adds only a soft [MAX_BYTES] backstop (oldest files evicted first). Every call is
 * best-effort — any failure is swallowed and treated as a cache miss.
 */
@OptIn(ExperimentalForeignApi::class)
internal object OGIosDiskCache {
    private const val SUBDIR = "og_image_cache"
    private const val MAX_BYTES = 128L * 1024 * 1024 // 128 MB on disk

    private val fm: NSFileManager get() = NSFileManager.defaultManager

    private fun dir(): String? {
        val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: return null
        val d = "$caches/$SUBDIR"
        if (!fm.fileExistsAtPath(d)) fm.createDirectoryAtPath(d, true, null, null)
        return d
    }

    fun read(key: String): NSData? {
        val path = dir()?.let { "$it/$key" } ?: return null
        if (!fm.fileExistsAtPath(path)) return null
        return NSData.dataWithContentsOfFile(path)
    }

    fun write(key: String, data: NSData) {
        val d = dir() ?: return
        val dest = "$d/$key"
        val tmp = "$d/$key.tmp"
        if (data.writeToFile(tmp, atomically = true)) {
            if (fm.fileExistsAtPath(dest)) fm.removeItemAtPath(dest, null)
            fm.moveItemAtPath(tmp, dest, null)
            evictIfNeeded(d)
        }
    }

    private fun evictIfNeeded(d: String) {
        val names = fm.contentsOfDirectoryAtPath(d, null) ?: return
        data class Entry(val path: String, val size: Long, val modified: Double)
        val entries = names.mapNotNull { name ->
            val path = "$d/$name"
            val attrs = fm.attributesOfItemAtPath(path, null) ?: return@mapNotNull null
            val size = (attrs[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
            val modified = (attrs[NSFileModificationDate] as? platform.Foundation.NSDate)
                ?.timeIntervalSince1970 ?: 0.0
            Entry(path, size, modified)
        }
        var total = entries.sumOf { it.size }
        if (total <= MAX_BYTES) return
        for (e in entries.sortedBy { it.modified }) { // oldest first
            if (total <= MAX_BYTES) break
            if (fm.removeItemAtPath(e.path, null)) total -= e.size
        }
    }
}
