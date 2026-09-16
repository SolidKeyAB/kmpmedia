package com.solidkey.painpoints.image.loading

/**
 * 🔍 Check if the given format is supported on the current platform.
 */
actual fun isSupported(format: OGImageFormat): Boolean {
    return when (format) {
        OGImageFormat.PNG, OGImageFormat.JPG, OGImageFormat.JPEG,
        OGImageFormat.GIF, OGImageFormat.WEBP, OGImageFormat.BMP -> true
        OGImageFormat.TIFF -> false  // ❌ Not supported by default
        OGImageFormat.SVG -> true
    }
}