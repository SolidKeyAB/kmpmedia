package com.solidkey.painpoints.layer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.layering.OGLayerItemEvent

/**
 * Wraps common pointer gestures and delegates them to the provided [OGItemGestureHandler].
 *
 * Supports:
 * - Tap
 * - Double Tap
 * - Long Press
 * - Pinch Zoom
 */
@Composable
fun Modifier.ogPointerGestureWrapper(
    id: String,
    label: OGLayerItemLabel,
    gestureHandler: OGItemGestureHandler?
): Modifier {
    val resolvedId = rememberUpdatedState(id)

    Logger.i("OG>> ogPointerGestureWrapper... id: $id, label: $label, gestureHandler: $gestureHandler")
    return this
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    gestureHandler?.onTap(
                        event = OGLayerItemEvent.TAP,
                        id = resolvedId.value,
                        label = label
                    )
                },
                onDoubleTap = {
                    gestureHandler?.onDoubleTap(
                        event = OGLayerItemEvent.DOUBLE_TAP,
                        id = resolvedId.value,
                        label = label
                    )
                },
                onLongPress = {
                    gestureHandler?.onLongPress(
                        event = OGLayerItemEvent.LONG_PRESS,
                        id = resolvedId.value,
                        label = label
                    )
                }
            )
        }
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->

                Logger.i("OG>> ogPointerGestureWrapper... pan: $pan, zoom: $zoom")

                if (zoom != 1f) {
                    gestureHandler?.onPinchToScale(
                        event = OGLayerItemEvent.PINCH_ZOOM,
                        id = resolvedId.value,
                        label = label,
                        zoomFactor = zoom
                    )
                }
                if (pan.x != 0f || pan.y != 0f) {
                    gestureHandler?.onDrag(
                        OGLayerItemEvent.DRAG,
                        id = resolvedId.value,
                        label = label,
                        dx = pan.x,
                        dy = pan.y
                    )
                }
            }
        }
}