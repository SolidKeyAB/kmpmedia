package com.solidkey.painpoints.layer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import com.solidkey.painpoints.image.history.OGTransformationHistoryHelper
import com.solidkey.painpoints.image.layering.OGLayerItemEvent
import com.solidkey.painpoints.image.processing.OGImageTransformation
import com.solidkey.painpoints.source.OGSourceType

class OGImageLayerItem(
    override val id: String,
    x: MutableState<Float>,
    y: MutableState<Float>,
    override val source: OGSourceType,
    transformations: SnapshotStateList<OGImageTransformation> = mutableStateListOf(),
    historyHelper: OGTransformationHistoryHelper? = null,
    override val displayContent: @Composable (
        (
        SnapshotStateList<OGImageTransformation>,
        onEvent: (event: OGLayerItemEvent, id: String) -> Unit,
        gestureHandler: OGItemGestureHandler?
    ) -> Unit
    )? = null,
    override val onEvent: (event: OGLayerItemEvent, id: String) -> Unit,
    gestureHandler: OGItemGestureHandler? = null,
    label: OGLayerItemLabel = OGLayerItemLabel()
) : OGPositionedLayerItem(
    id = id,
    x = x,
    y = y,
    source = source,
    displayContent = displayContent,
    transformations = transformations,
    historyHelper = historyHelper,
    onEvent = onEvent,
    gestureHandler = gestureHandler,
    label = label
) {

    override fun copyWithNewTransform(transformations: List<OGImageTransformation>): OGImageLayerItem {
        return OGImageLayerItem(
            id = this.id,
            x = this.x,
            y = this.y,
            source = this.source!!,
            transformations = mutableStateListOf<OGImageTransformation>().also { it.addAll(transformations) },
            historyHelper = this.historyHelper,
            displayContent = this.displayContent,
            onEvent = this.onEvent,
            gestureHandler = this.gestureHandler,
            label = this.label
        )
    }

}
