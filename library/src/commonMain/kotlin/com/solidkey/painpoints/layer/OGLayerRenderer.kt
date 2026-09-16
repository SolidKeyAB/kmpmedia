package com.solidkey.painpoints.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex

@Composable
fun OGLayerRenderer(
    rootLayer: OGLayer,
    baseZ: Float = 0f
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Gray)) {
        renderLayerRecursively(rootLayer, baseZ)
    }
}

@Composable
private fun renderLayerRecursively(
    layer: OGLayer,
    currentZ: Float
) {
    // Apply z-index for this layer
    Box(modifier = Modifier.zIndex(layer.zOverride ?: currentZ)) {
        layer.display()
    }

    // Render children with increasing z-index
    layer.layerItems.forEachIndexed { index, item ->
        val childZ = item.zOverride ?: (currentZ + index + 1f)

        Box(modifier = Modifier.zIndex(childZ)) {
            item.display()
        }

        if (item is OGLayer) {
            // Use a deeper z-range for children of sublayers
            renderLayerRecursively(item, childZ + 100f)
        }
    }
}
