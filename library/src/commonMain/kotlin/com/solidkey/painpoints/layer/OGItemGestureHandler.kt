package com.solidkey.painpoints.layer

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.solidkey.painpoints.image.layering.OGLayerItemEvent
import com.solidkey.painpoints.image.processing.OGImageTransformation

interface OGItemGestureHandler {
    fun onTap(event: OGLayerItemEvent, id: String, label: OGLayerItemLabel)
    fun onDoubleTap(event: OGLayerItemEvent, id: String, label: OGLayerItemLabel)
    fun onLongPress(event: OGLayerItemEvent, id: String, label: OGLayerItemLabel)
    fun onPinchToScale(event: OGLayerItemEvent, id: String, label: OGLayerItemLabel, zoomFactor: Float)
    fun onDrag(event: OGLayerItemEvent, id: String, label: OGLayerItemLabel, dx: Float, dy: Float)

    companion object
}


fun OGItemGestureHandler.Companion.default(
    transformations: SnapshotStateList<OGImageTransformation>,
    x: MutableState<Float>,
    y: MutableState<Float>,
    density: Float,
    onEvent: ((OGLayerItemEvent, String) -> Unit)? = null // ✅ optional event hook
): OGItemGestureHandler {
    return DefaultOGGestureHandler(density, transformations, x, y, onEvent)
}


