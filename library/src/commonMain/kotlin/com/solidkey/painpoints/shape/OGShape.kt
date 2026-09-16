package com.solidkey.painpoints.shape

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Built-in clip shapes shared by every KMPMedia media surface — video (via
 * [com.solidkey.painpoints.video.playing.OGAVPlayer]) and raster/photo images
 * (via [com.solidkey.painpoints.image.OGImageView]).
 *
 * Clipping to any of these is a Compose `Modifier.clip(shape)` mask, which the GPU
 * composites for free — for a still image it is drawn once, so there is no runtime
 * cost. To add a new primitive (e.g. hexagon, star): add an enum entry here, a
 * `Shape` subclass below, and a branch in [toShape].
 */
enum class OGShapeType {
    CIRCLE, TRIANGLE_UP, TRIANGLE_DOWN, SQUARE, RECTANGLE, DIAMOND
}

/** Resolve an [OGShapeType] into a concrete Compose [Shape], honoring [cornerRadius]. */
fun OGShapeType.toShape(cornerRadius: Dp = 0.dp): Shape = when (this) {
    OGShapeType.CIRCLE -> CircleShape
    OGShapeType.TRIANGLE_UP -> TriangleShape(TriangleDirection.UP, cornerRadius)
    OGShapeType.TRIANGLE_DOWN -> TriangleShape(TriangleDirection.DOWN, cornerRadius)
    OGShapeType.SQUARE -> RoundedCornerShape(cornerRadius)
    OGShapeType.RECTANGLE -> RoundedCornerShape(cornerRadius)
    OGShapeType.DIAMOND -> DiamondShape(cornerRadius)
}

enum class TriangleDirection { UP, DOWN }

class TriangleShape(private val direction: TriangleDirection, private val cornerRadius: Dp = 0.dp) : Shape {
    override fun createOutline(
        size: Size, layoutDirection: LayoutDirection, density: Density
    ): Outline {
        val radiusPx = with(density) { cornerRadius.toPx() }
        val path = Path().apply {
            when (direction) {
                TriangleDirection.UP -> {
                    moveTo(size.width / 2f, radiusPx) // Top
                    lineTo(size.width - radiusPx, size.height - radiusPx) // Bottom right
                    lineTo(radiusPx, size.height - radiusPx) // Bottom left
                }
                TriangleDirection.DOWN -> {
                    moveTo(radiusPx, radiusPx) // Top left
                    lineTo(size.width - radiusPx, radiusPx) // Top right
                    lineTo(size.width / 2f, size.height - radiusPx) // Bottom
                }
            }
            close()
        }
        return Outline.Generic(path)
    }
}

class DiamondShape(private val cornerRadius: Dp = 0.dp) : Shape {
    override fun createOutline(
        size: Size, layoutDirection: LayoutDirection, density: Density
    ): Outline {
        val path = Path().apply {
            val radiusPx = with(density) { cornerRadius.toPx() }
            moveTo(size.width / 2f, radiusPx)  // Top middle
            lineTo(size.width - radiusPx, size.height / 2f) // Right middle
            lineTo(size.width / 2f, size.height - radiusPx) // Bottom middle
            lineTo(radiusPx, size.height / 2f) // Left middle
            close()
        }
        return Outline.Generic(path)
    }
}
