package com.solidkey.painpoints.image.processing


import platform.CoreGraphics.*
import platform.CoreImage.*
import platform.Foundation.*
import platform.UIKit.*

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.loading.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.dataWithBytes
import platform.Foundation.setValue
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContext
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageOrientation



actual enum class OGColorFilter {
    GRAYSCALE, SEPIA, INVERT
}

actual class OGImageProcessor {
    actual companion object {
        actual fun create(): OGImageProcessor = OGImageProcessor()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun applyTransformations(image: Painter, density: Density?, transformations: List<OGImageTransformation>): Painter {
        var uiImage = extractBitmap(image, density)?.toUIImage() ?: return image

        for (transformation in transformations) {
            uiImage = when (transformation) {
                is OGImageTransformation.Resize -> resize(uiImage, transformation)
                is OGImageTransformation.Crop -> crop(uiImage, transformation)
                is OGImageTransformation.Rotate -> rotate(uiImage, transformation)
                is OGImageTransformation.ColorFilter -> applyColorFilter(uiImage, transformation)
                OGImageTransformation.None -> uiImage
            }
        }

        return BitmapPainter(uiImage.toComposeImageBitmap())
    }


    @OptIn(ExperimentalForeignApi::class)
    private fun resize(image: UIImage, config: OGImageTransformation.Resize): UIImage {
        val (width, height, maintainAspectRatio) = config
        val (imageWidth, imageHeight) = image.size.useContents { Pair(width, height) }

        val (finalWidth, finalHeight) = if (maintainAspectRatio) {
            val aspectRatio =  imageWidth!! / imageHeight!!
            if (width != null) Pair(width.toDouble(), (width / aspectRatio))
            else if (height != null) Pair((height * aspectRatio), height.toDouble())
            else Pair(imageWidth.toDouble(), imageHeight.toDouble())
        } else {
            Pair(width?.toDouble() ?: imageWidth!!, height?.toDouble() ?: imageHeight!!)
        }

        UIGraphicsBeginImageContext(CGSizeMake(finalWidth.toDouble(), finalHeight.toDouble()))
        image.drawInRect(CGRectMake(0.0, 0.0, finalWidth.toDouble(), finalHeight.toDouble()))
        val resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        return resizedImage!!
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun crop(image: UIImage, config: OGImageTransformation.Crop): UIImage {
        val cropRect = CGRectMake(config.x.toDouble(), config.y.toDouble(), config.width.toDouble(), config.height.toDouble())
        val cgImage = CGImageCreateWithImageInRect(image.CGImage!!, cropRect) ?: return image
        return UIImage.imageWithCGImage(cgImage)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun rotate(image: UIImage, config: OGImageTransformation.Rotate): UIImage {
        return UIImage.imageWithCGImage(image.CGImage!!, scale = 1.0, orientation = UIImageOrientation.UIImageOrientationRight)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun applyColorFilter(image: UIImage, config: OGImageTransformation.ColorFilter): UIImage {
        // Ensure we have a valid CGImage
        val cgImage = image.CGImage ?: return image
        val ciImage = CIImage.imageWithCGImage(cgImage)

        // Map our OGColorFilter values to iOS filter names.
        val filterName = when (config.filter) {
            OGColorFilter.GRAYSCALE -> "CIPhotoEffectMono"
            OGColorFilter.SEPIA -> "CISepiaTone"
            OGColorFilter.INVERT -> "CIColorInvert"
        }

        // Attempt to create the filter. If unavailable, log an error and return the image.
        val filter = CIFilter.filterWithName(filterName)
        if (filter == null) {
            Logger.e("Color filter '$filterName' not available.")
            return image
        }

        filter.setValue(ciImage, forKey = "inputImage")

        // For the sepia filter, adjust intensity if desired.
        if (config.filter == OGColorFilter.SEPIA) {
            filter.setValue(1.0, forKey = "inputIntensity")
        }

        // Get the output image, or fallback if null.
        val outputImage = filter.outputImage ?: return image
        val context = CIContext.contextWithOptions(null)
        val outCGImage = context.createCGImage(outputImage, outputImage.extent)

        return outCGImage?.let { UIImage.imageWithCGImage(it) } ?: image
    }

}

actual typealias OGPlatformBitmap = Bitmap

@OptIn(ExperimentalForeignApi::class)
actual fun extractBitmap(painter: Painter, density: Density?): OGPlatformBitmap? {
    if (painter !is BitmapPainter) {
        println("❌ OGImageProcessor >> EXTRACT BITMAP: Painter is NOT a BitmapPainter!")
        return null
    }

    val imageBitmap = painter.intrinsicSize
    return painter.toImageBitmap(imageBitmap, density!!, LayoutDirection.Ltr).asSkiaBitmap()
}

@OptIn(ExperimentalForeignApi::class)
fun org.jetbrains.skia.Image.toNSData(): NSData? {
    val encodedData = this.encodeToData() ?: return null
    val byteArray = encodedData.bytes
    return byteArray.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), byteArray.size.toULong())
    }
}

fun org.jetbrains.skia.Image.toUIImage(): UIImage? {
    val nsData = this.toNSData()
    return nsData?.let { UIImage.imageWithData(it) }
}

fun Painter.toImageBitmap(
    size: Size,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val bitmap = ImageBitmap(size.width.toInt(), size.height.toInt())
    val canvas = Canvas(bitmap)
    CanvasDrawScope().draw(density, layoutDirection, canvas, size) {
        draw(size)
    }
    return bitmap
}

fun Bitmap.toSkiaImage(): org.jetbrains.skia.Image {
    return org.jetbrains.skia.Image.makeFromBitmap(this)
}

fun Bitmap.toUIImage(): UIImage? {
    return this.toSkiaImage().toUIImage()
}

@OptIn(ExperimentalForeignApi::class)
fun ensureAlphaUIImage(uiImage: UIImage): UIImage {
    UIGraphicsBeginImageContextWithOptions(uiImage.size, false, uiImage.scale)
    uiImage.drawInRect(CGRectMake(0.0, 0.0, uiImage.size.useContents { width }, uiImage.size.useContents { height }))
    val newUIImage = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return newUIImage!!
}
