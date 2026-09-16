package com.solidkey.painpoints.image.layering

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.OGImageView
import com.solidkey.painpoints.image.animating.OGAnimationType
import kotlin.math.roundToInt

//@Composable
//fun OGImageLayerView(
//    layer: OGImageLayer,
//    modifier: Modifier = Modifier,
//    debugMode: Boolean = false,
//    onEventTriggered: (OGLayerItemEvent, OGImageLayerItem) -> Unit
//) {
//    Box(modifier = modifier) {
//        // ✅ Iterate through layer items (if they exist)
//        layer.layerItems?.forEach { layerItem ->
//            Logger.i("OG>> Rendering Layer Item: ${layerItem.id}")
//            var offsetX by remember { mutableStateOf(layerItem.x) }
//            var offsetY by remember { mutableStateOf(layerItem.y) }
//            val isAnimating = layerItem.triggerAnimation.value || layerItem.externalTrigger.value
//
//            val animatedScale by animateFloatAsState(
//                targetValue = if (isAnimating && layerItem.animationSpec?.type == OGAnimationType.SCALE) 1.5f else 1f,
//                animationSpec = tween(layerItem.animationSpec?.duration ?: 500),
//                finishedListener = {
//                    layerItem.triggerAnimation.value = false
//                    layerItem.externalTrigger.value = false
//                }
//            )
//
//            val animatedRotation by animateFloatAsState(
//                targetValue = if (isAnimating && layerItem.animationSpec?.type == OGAnimationType.ROTATE) 360f else 0f,
//                animationSpec = tween(layerItem.animationSpec?.duration ?: 500),
//                finishedListener = {
//                    layerItem.triggerAnimation.value = false
//                    layerItem.externalTrigger.value = false
//                }
//            )
//
//            val animatedAlpha by animateFloatAsState(
//                targetValue = if (isAnimating && layerItem.animationSpec?.type == OGAnimationType.FADE) 0.3f else 1f,
//                animationSpec = tween(layerItem.animationSpec?.duration ?: 500),
//                finishedListener = {
//                    layerItem.triggerAnimation.value = false
//                    layerItem.externalTrigger.value = false
//                }
//            )
//
//            Box(
//                modifier = layerItem.modifier
//                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
//                    .clickable {
//                        Logger.i("OG>> OGImageLayerView... onClick... id: ${layerItem.id}")
//                        onEventTriggered(OGLayerItemEvent.TAP, layerItem)
//                        layerItem.onClick?.invoke()
//                    }
//                    .graphicsLayer(
//                        scaleX = animatedScale,
//                        scaleY = animatedScale,
//                        rotationZ = animatedRotation,
//                        alpha = animatedAlpha
//                    )
//                    .pointerInput(Unit) {
//                        detectDragGestures { change, dragAmount ->
//                            change.consume()
//                            offsetX += dragAmount.x
//                            offsetY += dragAmount.y
//                        }
//                    }
//                    .pointerInput(Unit) {
//                        detectTapGestures(
//                            onDoubleTap = {
//                                Logger.i("OG>> OGImageLayerView... onDoubleTap... id: ${layerItem.id}")
//                                onEventTriggered(OGLayerItemEvent.DOUBLE_TAP, layerItem)
//                            }
//                        )
//                    }
//            ) {
//                OGImageView(
//                    source = layerItem.source,
//                    modifier = Modifier.fillMaxSize(),
//                    transformConfig = layerItem.transformConfig,
//                    onError = { errorMessage ->
//                        Logger.e("OG>> Image Load Error: $errorMessage")
//                    },
//                    onEventTriggered = { event ->
//                        onEventTriggered(event, layerItem) // ✅ Forward event to parent
//                    }
//                )
//
//                if (debugMode) {
//                    Box(
//                        Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.2f))
//                    )
//                }
//            }
//        }
//
//        // ✅ **Render sublayers recursively**
//        layer.layerItems?.forEach { layerItem ->
//            layerItem.subLayers.forEach { subLayer ->
//                OGImageLayerView(layer = subLayer, onEventTriggered = onEventTriggered)
//            }
//        }
//    }
//}
//
///**
// * 📌 **Logs the full layer tree structure, including newly added items**
// * This helps developers capture a snapshot of all existing items and their details.
// */
//fun logLayerStructure(layer: OGImageLayer, addedImages: List<OGImageLayerItem>) {
//    println("📜 **Current Layer Structure:**")
//
//    fun logItem(layerItem: OGImageLayerItem, indent: String = "") {
//        println("$indent📍 ID: ${layerItem.id}, x: ${layerItem.x}, y: ${layerItem.y}, group: ${layerItem.groupId}")
//
//        if (layerItem.subLayers.isEmpty()) {
//            println("$indent   (No Sublayers)")
//        } else {
//            layerItem.subLayers.forEach { subLayer ->
//                if (subLayer.layerItems.isNullOrEmpty()) {
//                    println("$indent   🔹 (Empty Layer)")
//                } else {
//                    subLayer.layerItems?.forEach { subItem ->
//                        logItem(subItem, indent + "  ")
//                    }
//                }
//            }
//        }
//    }
//
//    if (layer.layerItems.isNullOrEmpty()) {
//        println("🔹 (Empty Layer)")
//    } else {
//        layer.layerItems?.forEach { logItem(it) }
//    }
//
//    if (addedImages.isNotEmpty()) {
//        println("➕ **Added Images:**")
//        addedImages.forEach { logItem(it, "  ➕ ") }
//    }
//}


