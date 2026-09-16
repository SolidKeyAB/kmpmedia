package com.solidkey.painpoints.layer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.solidkey.painpoints.image.history.OGTransformationHistoryHelper
import com.solidkey.painpoints.image.layering.OGLayerItemEvent
import com.solidkey.painpoints.image.processing.OGImageTransformation
import com.solidkey.painpoints.source.OGSourceType

data class OGTextLayerItem(
    override val id: String,
    override var x: MutableState<Float>,
    override var y: MutableState<Float>,
    override val source: OGSourceType? = null,
    override val modifier: Modifier = Modifier,
    val text: String,
    override val displayContent: @Composable (
        (
        SnapshotStateList<OGImageTransformation>,
        onEvent: (event: OGLayerItemEvent, id: String) -> Unit,
        gestureHandler: OGItemGestureHandler?
    ) -> Unit
    )? = null,
    override var transformations: SnapshotStateList<OGImageTransformation> = mutableStateListOf(),
    override var historyHelper: OGTransformationHistoryHelper? = null,
    override val onEvent: (event: OGLayerItemEvent, id: String) -> Unit,
    override var opacity: Float = 1f,
    override var isVisible: Boolean = true,
    override var isLocked: Boolean = false,
    override var label: OGLayerItemLabel = OGLayerItemLabel(
        itemLabel = id,
        groupLabel = "group_$id",
        itemClass = OGTextLayerItem::class.simpleName ?: "Unknown"
    ),
    override var gestureHandler: OGItemGestureHandler? = null,
    val density: Float // 🆕 Add this
) : OGPositionedLayerItem(
    id = id,
    x = x,
    y = y,
    source = source,
    modifier = modifier,
    displayContent = displayContent,
    transformations = transformations,
    historyHelper = historyHelper,
    onEvent = onEvent,
    opacity = opacity,
    isVisible = isVisible,
    isLocked = isLocked,
    label = label,
    gestureHandler = gestureHandler ?: OGItemGestureHandler.default(transformations, x, y, density) // ✅ Updated
)
