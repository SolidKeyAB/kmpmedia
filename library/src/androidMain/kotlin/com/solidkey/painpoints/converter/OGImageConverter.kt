package com.solidkey.painpoints.converter

import android.graphics.Bitmap
import java.nio.ByteBuffer

object OGImageConverter {
    fun Bitmap.toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(byteCount)
        copyPixelsToBuffer(buffer)
        return buffer.array()
    }
}
