package com.solidkey.painpoints.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import com.solidkey.painpoints.image.svg.OGGradientType
import com.solidkey.painpoints.image.svg.OGLinearGradientType
import com.solidkey.painpoints.image.svg.OGRadialGradientType
import com.solidkey.painpoints.image.svg.OGSVGGradient
import kotlin.math.min

sealed class OGColorBase {
    abstract fun toBrush(x: Float, y: Float, width: Float, height: Float): Brush

    companion object {
        /** Upper bound on cached brushes. An animated SVG mints a fresh bbox-keyed entry per frame, so the
         *  cache must evict or it grows without limit — bound it and drop the oldest on overflow. */
        private const val BRUSH_CACHE_MAX = 128

        // Insertion-ordered (mutableMapOf == LinkedHashMap), so keys.first() is the oldest entry to evict.
        private val brushCache: MutableMap<String, Brush> = mutableMapOf()

        /**
         * Retrieves a cached brush or creates a new one, keeping a stable reference so identical paints
         * don't recompose. The cache is bounded (see [BRUSH_CACHE_MAX]); this runs in the draw path, so it
         * does no logging.
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

            // A unique cache key. For a solid colour use the data-class value (not hashCode(): a 32-bit
            // hash collision would otherwise hand back the wrong brush); gradients key on their id.
            val cacheKey = if (gradient != null) {
                "GRADIENT-${gradient.id}-$x-$y-$width-$height"
            } else {
                "COLOR-$colorBase-$x-$y-$width-$height"
            }

            brushCache[cacheKey]?.let { return it }

            val newBrush = when {
                gradient != null -> gradient.brush.toBrush(x, y, width, height)
                colorBase != null -> colorBase.toBrush(x, y, width, height)
                else -> SolidColor(Color.Transparent)
            }

            if (brushCache.size >= BRUSH_CACHE_MAX) {
                brushCache.keys.firstOrNull()?.let { brushCache.remove(it) }
            }
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

