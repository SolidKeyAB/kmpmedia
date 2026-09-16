package com.solidkey.painpoints.image.transform

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.processing.OGColorFilter
import com.solidkey.painpoints.image.processing.OGImageProcessor
import com.solidkey.painpoints.image.processing.OGImageTransformation

/** ✅ Resize Configuration */
data class OGResizeConfig(
    val width: Int? = null,
    val height: Int? = null,
    val maintainAspectRatio: Boolean = true // 🔹 Default to true for better UX
)

/** ✅ Crop Configuration */
data class OGCropConfig(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

/** ✅ Rotation Configuration */
data class OGRotateConfig(
    val degrees: Float? = null,
    val percentage: Float? = null // 🔹 Rotate by a percentage (0-100)
) {
    fun getRotationDegrees(): Float? {
        return when {
            degrees != null -> degrees
            percentage != null -> (percentage / 100f) * 360f // Convert percentage to degrees
            else -> null
        }
    }
}

/** ✅ Color Configuration */
data class OGColorConfig(
    val filter: OGColorFilter? = null,
    val brightness: Float? = null, // 🔹 1.0 = normal, <1.0 = darker, >1.0 = brighter
    val contrast: Float? = null,   // 🔹 1.0 = normal, <1.0 = less contrast, >1.0 = more contrast
    val saturation: Float? = null  // 🔹 1.0 = normal, <1.0 = desaturated, >1.0 = oversaturated
)

data class OGImageTransformConfig(
    val resizeConfig: OGResizeConfig? = null,
    val cropConfig: OGCropConfig? = null,
    val rotateConfig: OGRotateConfig? = null,
    val colorConfig: OGColorConfig? = null,
    val draggable: Boolean = false
)

fun OGImageTransformConfig.toTransformations(): List<OGImageTransformation> {
    val transformations = mutableListOf<OGImageTransformation>()

    resizeConfig?.let {
        transformations.add(OGImageTransformation.Resize(it.width, it.height, it.maintainAspectRatio))
    }
    cropConfig?.let {
        transformations.add(OGImageTransformation.Crop(it.x, it.y, it.width, it.height))
    }
    rotateConfig?.degrees?.let { degrees ->
        transformations.add(OGImageTransformation.Rotate(degrees))
    }
    colorConfig?.filter?.let { filter ->
        transformations.add(OGImageTransformation.ColorFilter(filter))
    }

    return transformations
}
