package com.solidkey.painpoints.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.svg.OGGradientType
import com.solidkey.painpoints.image.svg.OGLinearGradientType
import com.solidkey.painpoints.image.svg.OGRadialGradientType
import com.solidkey.painpoints.image.svg.OGSVGGradient
import kotlin.math.min

sealed class OGColorBase {
    abstract fun toBrush(x: Float, y: Float, width: Float, height: Float): Brush

    companion object {
        // ✅ Centralized Cache for Brushes
        private val brushCache: MutableMap<String, Brush> = mutableMapOf()

        /**
         * Retrieves a cached brush or creates a new one.
         * Avoids unnecessary recompositions by maintaining a stable reference.
         */
        fun getCachedBrush(
            colorBase: OGColorBase?,
            gradient: OGSVGGradient?,
            x: Float,
            y: Float,
            width: Float,
            height: Float
        ): Brush {
            if (colorBase == null && gradient == null) return SolidColor(Color.Transparent)

            // Create a unique cache key that includes the gradient ID if present
            val cacheKey = if (gradient != null) {
                "GRADIENT-${gradient.id}-$x-$y-$width-$height"
            } else {
                "COLOR-${colorBase.hashCode()}-$x-$y-$width-$height"
            }

            Logger.e("🧠 Checking cache for key: $cacheKey")

            // Check if a cached brush is available
            val cachedBrush = brushCache[cacheKey]
            if (cachedBrush != null) {
                Logger.e("✅ Cache hit: Using cached brush for key: $cacheKey")
                return cachedBrush
            } else {
                Logger.e("❌ Cache miss: Generating new brush for key: $cacheKey")
            }

            // Create a new brush based on the gradient or solid color
            val newBrush = when {
                gradient != null -> {
                    Logger.e("🖌️ Creating gradient brush for ID: ${gradient.id}")
                    gradient.brush.toBrush(x, y, width, height)
                }
                colorBase != null -> {
                    Logger.e("🖌️ Creating solid color brush: $colorBase")
                    colorBase.toBrush(x, y, width, height)
                }
                else -> SolidColor(Color.Transparent)
            }

            // Cache and return the new brush
            brushCache[cacheKey] = newBrush
            return newBrush
        }
    }
}

// ✅ Solid Color Implementation
data class OGColor(val color: Color) : OGColorBase() {
    override fun toBrush(x: Float, y: Float, width: Float, height: Float): Brush {
        return SolidColor(color)
    }
}

// ✅ Gradient Brush Implementation
data class OGBrush(
    val type: OGGradientType,
    val colorStops: List<Pair<Float, Color>>,
    val tileMode: TileMode = TileMode.Clamp
) : OGColorBase() {

    override fun toBrush(x: Float, y: Float, width: Float, height: Float): Brush {
        return when (type) {
            is OGLinearGradientType -> Brush.linearGradient(
                colorStops = colorStops.toTypedArray(),
                start = Offset(x, (y + height) / 2),
                end = Offset(x + width, (y + height) / 2),
                tileMode = tileMode
            )
            is OGRadialGradientType -> Brush.radialGradient(
                colors = colorStops.map { it.second },
                center = Offset(x + width / 2, y + height / 2),
                radius = min(width, height) / 2f,
                tileMode = tileMode
            )
        }
    }
}

