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

abstract class OGPositionedLayerItem(
    override val id: String,
    open var x: MutableState<Float>,
    open var y: MutableState<Float>,
    source: OGSourceType? = null,
    modifier: Modifier = Modifier,
    triggerAnimation: MutableState<Boolean> = mutableStateOf(false),
    externalTrigger: MutableState<Boolean> = mutableStateOf(false),
    displayContent: @Composable (
        (
        SnapshotStateList<OGImageTransformation>,
        onEvent: (event: OGLayerItemEvent, id: String) -> Unit,
        gestureHandler: OGItemGestureHandler?
    ) -> Unit
    )? = null,
    transformations: SnapshotStateList<OGImageTransformation> = mutableStateListOf(),
    historyHelper: OGTransformationHistoryHelper? = null,
    onEvent: (event: OGLayerItemEvent, id: String) -> Unit,
    opacity: Float = 1f,
    isVisible: Boolean = true,
    isLocked: Boolean = false,
    label: OGLayerItemLabel = OGLayerItemLabel(),
    gestureHandler: OGItemGestureHandler? = null
) : OGLayerItem(
    id = id,
    source = source,
    modifier = modifier,
    triggerAnimation = triggerAnimation,
    externalTrigger = externalTrigger,
    displayContent = displayContent,
    transformations = transformations,
    historyHelper = historyHelper,
    onEvent = onEvent,
    opacity = opacity,
    isVisible = isVisible,
    isLocked = isLocked,
    label = label,
    gestureHandler = gestureHandler
)
