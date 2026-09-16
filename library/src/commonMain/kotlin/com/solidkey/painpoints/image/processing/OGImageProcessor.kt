package com.solidkey.painpoints.image.processing

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

/**
 * Defines common image transformations.
 */
@Serializable
@Polymorphic
sealed class OGImageTransformation {
    object None : OGImageTransformation()

    data class Resize(
        val width: Int? = null,
        val height: Int? = null,
        val maintainAspectRatio: Boolean = false
    ) : OGImageTransformation()

    data class Crop(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) : OGImageTransformation()

    data class Rotate(
        val degrees: Float
    ) : OGImageTransformation()

    data class ColorFilter(
        val filter: OGColorFilter
    ) : OGImageTransformation()
}

fun OGImageTransformation.safeCopy(): OGImageTransformation = when (this) {
    is OGImageTransformation.Resize -> copy()
    is OGImageTransformation.Crop -> copy()
    is OGImageTransformation.Rotate -> copy()
    is OGImageTransformation.ColorFilter -> copy()
    is OGImageTransformation.None -> this // stateless
}


/**
 * Defines supported color filters.
 */
expect enum class OGColorFilter {
    GRAYSCALE,
    SEPIA,
    INVERT
}

/**
 * Defines platform-specific bitmap representation.
 */
expect class OGPlatformBitmap

/**
 * Extracts bitmap data from a Painter.
 */
expect fun extractBitmap(painter: Painter, density: Density?): OGPlatformBitmap?

/**
 * Image processor for applying transformations.
 */
expect class OGImageProcessor {
    companion object {
        fun create(): OGImageProcessor
    }

    fun applyTransformations(image: Painter, density: Density? = null, transformations: List<OGImageTransformation>): Painter
}
