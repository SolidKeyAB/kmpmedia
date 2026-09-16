package com.solidkey.painpoints.image.processing

import android.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

actual enum class OGColorFilter {
    GRAYSCALE, SEPIA, INVERT
}

actual class OGImageProcessor {
    actual companion object {
        actual fun create(): OGImageProcessor = OGImageProcessor()
    }

    actual fun applyTransformations(image: Painter, density: Density?, transformations: List<OGImageTransformation>): Painter {
        var bitmap = extractBitmap(image, density) ?: return image

        for (transformation in transformations) {
            bitmap = when (transformation) {
                is OGImageTransformation.Resize -> resize(bitmap, transformation)
                is OGImageTransformation.Crop -> crop(bitmap, transformation)
                is OGImageTransformation.Rotate -> rotate(bitmap, transformation)
                is OGImageTransformation.ColorFilter -> applyColorFilter(bitmap, transformation)
                OGImageTransformation.None -> bitmap
            }
        }

        return BitmapPainter(bitmap.asImageBitmap())
    }

    private fun resize(bitmap: Bitmap, config: OGImageTransformation.Resize): Bitmap {
        val (width, height, maintainAspectRatio) = config
        val (finalWidth, finalHeight) = if (maintainAspectRatio) {
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            if (width != null) Pair(width, (width / aspectRatio).toInt())
            else if (height != null) Pair((height * aspectRatio).toInt(), height)
            else Pair(bitmap.width, bitmap.height)
        } else {
            Pair(width ?: bitmap.width, height ?: bitmap.height)
        }
        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
    }

    private fun crop(bitmap: Bitmap, config: OGImageTransformation.Crop): Bitmap {
        return Bitmap.createBitmap(bitmap, config.x, config.y, config.width, config.height)
    }

    private fun rotate(bitmap: Bitmap, config: OGImageTransformation.Rotate): Bitmap {
        val matrix = Matrix().apply { postRotate(config.degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun applyColorFilter(bitmap: Bitmap, config: OGImageTransformation.ColorFilter): Bitmap {
        val paint = Paint()
        val colorMatrix = when (config.filter) {
            OGColorFilter.GRAYSCALE -> ColorMatrix(floatArrayOf(
                0.33f, 0.33f, 0.33f, 0f, 0f,
                0.33f, 0.33f, 0.33f, 0f, 0f,
                0.33f, 0.33f, 0.33f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            OGColorFilter.SEPIA -> ColorMatrix(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            OGColorFilter.INVERT -> ColorMatrix(floatArrayOf(
                -1f,  0f,  0f,  0f, 255f,
                0f, -1f,  0f,  0f, 255f,
                0f,  0f, -1f,  0f, 255f,
                0f,  0f,  0f,  1f,   0f
            ))
        }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        val filteredBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(filteredBitmap)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return filteredBitmap
    }
}

actual typealias OGPlatformBitmap = Bitmap

actual fun extractBitmap(painter: Painter, density: Density?): OGPlatformBitmap? {
    if (painter !is BitmapPainter) return null
    val width = painter.intrinsicSize.width.toInt().coerceAtLeast(1)
    val height = painter.intrinsicSize.height.toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val androidCanvas = Canvas(bitmap)
    val composeCanvas = ComposeCanvas(androidCanvas)
    val drawScope = CanvasDrawScope()
    drawScope.draw(
        density = density ?: Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = composeCanvas,
        size = IntSize(width, height).toSize()
    ) {
        with(painter) { draw(size) }
    }
    return bitmap
}
