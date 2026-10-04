package com.solidkey.painpoints.ai

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** iOS: lossless PNG via Skia (already the Compose rendering backend on iOS — no new dependency). */
actual fun ImageBitmap.toPngBytes(): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap())
        .encodeToData(EncodedImageFormat.PNG)
        ?.bytes
        ?: ByteArray(0)
