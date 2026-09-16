package com.solidkey.painpoints.image.loading

/**
 * 🔍 Which image formats iOS can decode.
 *
 * Kept in LOCKSTEP with the Android actual ([OGSourceFormat.android.kt]) so a source that loads on
 * one platform loads on the other — no cross-platform deviation. **WebP is supported**: Apple added
 * native WebP decoding in iOS 14 (ImageIO / `UIImage`), and the app targets iOS 16, so every device
 * has it. The previous guard here predated iOS 14 and wrongly rejected WebP.
 */
actual fun isSupported(format: OGImageFormat): Boolean = when (format) {
    OGImageFormat.PNG, OGImageFormat.JPG, OGImageFormat.JPEG,
    OGImageFormat.GIF, OGImageFormat.WEBP, OGImageFormat.BMP -> true
    OGImageFormat.TIFF -> false  // ❌ not decoded by default (matches Android)
    OGImageFormat.SVG -> true    // handled by the library's own SVG renderer
}
