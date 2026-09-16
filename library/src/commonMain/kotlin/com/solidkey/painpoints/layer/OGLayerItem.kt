package com.solidkey.painpoints.layer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.solidkey.painpoints.image.history.OGTransformationHistoryHelper
import com.solidkey.painpoints.image.layering.OGLayerItemEvent
import com.solidkey.painpoints.image.processing.OGImageTransformation
import com.solidkey.painpoints.source.OGSourceType

sealed class OGLayerItem(
    open val id: String,
    open val source: OGSourceType? = null,
    open val modifier: Modifier = Modifier,
    open val triggerAnimation: MutableState<Boolean> = mutableStateOf(false),
    open val externalTrigger: MutableState<Boolean> = mutableStateOf(false),
    open val displayContent: @Composable (
        (
        SnapshotStateList<OGImageTransformation>,
        onEvent: (event: OGLayerItemEvent, id: String) -> Unit,
        gestureHandler: OGItemGestureHandler?
    ) -> Unit
    )? = null,
    open var transformations: SnapshotStateList<OGImageTransformation> = mutableStateListOf(),
    open var historyHelper: OGTransformationHistoryHelper? = null,
    open val onEvent: (event: OGLayerItemEvent, id: String) -> Unit,
    open var opacity: Float = 1f,
    open var zOverride: Float? = null, // Manual z-index
    open var isVisible: Boolean = true,
    open var isLocked: Boolean = false,
    open var label: OGLayerItemLabel = OGLayerItemLabel(),
    open var gestureHandler: OGItemGestureHandler? = null
) {

    open fun copyWithNewTransform(transformations: List<OGImageTransformation> = this.transformations): OGLayerItem = this

    @Composable
    open fun display() {
        val currentTransformations = rememberUpdatedState(transformations)
        currentTransformations.value // track changes

        displayContent?.invoke(transformations, onEvent, gestureHandler)
    }


    open fun applyTransformations(newTransformations: List<OGImageTransformation>) {
        transformations.clear()
        transformations.addAll(newTransformations)
    }

    open fun applyTransformations(
        newTransformations: List<OGImageTransformation>,
        historyHelper: OGTransformationHistoryHelper?
    ) {
        historyHelper?.pushSnapshot(transformations.toList())
        applyTransformations(newTransformations)
    }
}
