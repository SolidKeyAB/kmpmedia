package com.solidkey.painpoints.cutout

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.sqrt

/**
 * A per-pixel **foreground confidence** map produced by an [OGSegmenter]: `foreground[y * width + x]`
 * is `1f` where the subject is, `0f` where the background is (soft values in between are allowed).
 * [OGMaskContour.maskToPolygon] turns it into a clip outline.
 */
class OGSegmentationMask(val width: Int, val height: Int, val foreground: FloatArray) {
    init {
        require(width > 0 && height > 0) { "mask must be non-empty" }
        require(foreground.size == width * height) { "foreground size ${foreground.size} != $width*$height" }
    }

    fun at(x: Int, y: Int): Float = foreground[y * width + x]
}

/**
 * Turns an image into a foreground/background [OGSegmentationMask]. This is the **provider-agnostic**
 * seam of auto-cutout (mirroring how [com.solidkey.painpoints.ai.OGAiVector] keeps AI out of the lib):
 * the library ships simple zero-dependency segmenters for plain-background photos, and for complex
 * scenes you plug in your own — ML Kit Subject Segmentation on Android, the Vision framework on iOS,
 * or a cloud model — by implementing this one function. The mask then becomes a live clip via
 * [OGMaskContour.maskToPolygon].
 */
fun interface OGSegmenter {
    fun segment(image: ImageBitmap): OGSegmentationMask
}

/** Straight-line RGB distance in `0..1` (alpha ignored), normalised so the max distance is `1f`. */
internal fun colorDistance(a: Color, b: Color): Float {
    val dr = a.red - b.red
    val dg = a.green - b.green
    val db = a.blue - b.blue
    return (sqrt(dr * dr + dg * dg + db * db) / sqrt(3f)).coerceIn(0f, 1f)
}

/**
 * A zero-dependency [OGSegmenter] for photos on a **plain, solid background** (a product on white, a
 * portrait on a green screen): every pixel within [tolerance] of [background] is treated as background,
 * everything else as the subject. [tolerance] is `0..1` RGB distance. Great for the common
 * flat-background case; reach for an ML segmenter for cluttered scenes.
 */
class OGChromaKeySegmenter(
    private val background: Color,
    private val tolerance: Float = 0.18f,
) : OGSegmenter {
    override fun segment(image: ImageBitmap): OGSegmentationMask {
        val pm = image.toPixelMap()
        val fg = FloatArray(pm.width * pm.height)
        for (y in 0 until pm.height) {
            for (x in 0 until pm.width) {
                fg[y * pm.width + x] = if (colorDistance(pm[x, y], background) > tolerance) 1f else 0f
            }
        }
        return OGSegmentationMask(pm.width, pm.height, fg)
    }
}

/**
 * A zero-dependency [OGSegmenter] that keys on **brightness**: a subject that is clearly lighter or
 * darker than its background (a dark object on a light table, or vice-versa). [minLuma]..[maxLuma]
 * (each `0..1`) is the luma band kept as foreground.
 */
class OGLumaKeySegmenter(
    private val minLuma: Float = 0f,
    private val maxLuma: Float = 0.6f,
) : OGSegmenter {
    override fun segment(image: ImageBitmap): OGSegmentationMask {
        val pm = image.toPixelMap()
        val fg = FloatArray(pm.width * pm.height)
        for (y in 0 until pm.height) {
            for (x in 0 until pm.width) {
                val c = pm[x, y]
                val luma = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
                fg[y * pm.width + x] = if (luma in minLuma..maxLuma) 1f else 0f
            }
        }
        return OGSegmentationMask(pm.width, pm.height, fg)
    }
}
