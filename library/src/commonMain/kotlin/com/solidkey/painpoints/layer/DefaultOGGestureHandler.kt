package com.solidkey.painpoints.layer

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.layering.OGLayerItemEvent
import com.solidkey.painpoints.image.processing.OGImageTransformation

class DefaultOGGestureHandler(
    private val density: Float, // 👈 passed in from LocalDensity
    private val transformations: SnapshotStateList<OGImageTransformation>,
    private val x: MutableState<Float>,
    private val y: MutableState<Float>,
    private val onEvent: ((OGLayerItemEvent, String) -> Unit)? = null
) : OGItemGestureHandler {

    override fun onTap(event: OGLayerItemEvent, id: String, label: OGLayerItemLabel) {
        Logger.i("✅ TAPPED on $id")
        onEvent?.invoke(event, id)
    }

    override fun onDoubleTap(event: OGLayerItemEvent, id: String, label: OGLayerItemLabel) {
        Logger.i("✅ DOUBLE TAPPED on $id")
        onEvent?.invoke(event, id)
    }

    override fun onLongPress(event: OGLayerItemEvent, id: String, label: OGLayerItemLabel) {
        Logger.i("🖐️ LONG PRESS on $id")
        onEvent?.invoke(event, id)
    }

    override fun onPinchToScale(event: OGLayerItemEvent, id: String, label: OGLayerItemLabel, zoomFactor: Float) {
        Logger.i("🔍 PINCH on $id with zoomFactor=$zoomFactor")
        onEvent?.invoke(event, id)

        val resize = transformations.filterIsInstance<OGImageTransformation.Resize>().firstOrNull()
        if (resize != null) {
            val newWidth = (resize.width ?: 100) * zoomFactor
            val newHeight = (resize.height ?: 100) * zoomFactor
            transformations.remove(resize)
            transformations.add(
                OGImageTransformation.Resize(
                    width = newWidth.toInt(),
                    height = newHeight.toInt(),
                    maintainAspectRatio = resize.maintainAspectRatio
                )
            )
        }
    }

    override fun onDrag(event: OGLayerItemEvent, id: String, label: OGLayerItemLabel, dx: Float, dy: Float) {
        x.value += dx / density
        y.value += dy / density
        Logger.i("🧲 DRAG on $id: dx=$dx dy=$dy → new x=${x.value}, y=${y.value}")
        onEvent?.invoke(event, id)
    }
}
