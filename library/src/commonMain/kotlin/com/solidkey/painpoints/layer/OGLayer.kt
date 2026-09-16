package com.solidkey.painpoints.layer

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import com.solidkey.painpoints.image.history.OGTransformationHistoryHelper
import com.solidkey.painpoints.image.layering.OGLayerItemEvent
import com.solidkey.painpoints.image.processing.OGImageTransformation
import com.solidkey.painpoints.source.OGSourceType

data class OGLayer(
    override val id: String,
    val layerItems: SnapshotStateList<OGLayerItem> = mutableStateListOf(),
    override val source: OGSourceType? = null,
    override val modifier: Modifier = Modifier,
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
        itemClass = OGLayer::class.simpleName ?: "Unknown"
    ),
    override var gestureHandler: OGItemGestureHandler? = null
) : OGLayerItem(
    id = id,
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
    gestureHandler = gestureHandler
)
{
    override fun copyWithNewTransform(transformations: List<OGImageTransformation>): OGLayer {
        return this.copy(
            layerItems = layerItems.map { it.copyWithNewTransform(emptyList()) }.toMutableStateList()
        ).apply {
            this.transformations = transformations.toMutableStateList()
        }
    }

    fun addItem(item: OGLayerItem) {
        if (!isLocked) layerItems.add(item)
    }

    fun removeItemById(id: String): Boolean {
        if (isLocked) return false
        layerItems.removeAll { it.id == id }
        return layerItems.isEmpty()
    }

    fun findItemById(itemId: String): OGLayerItem? {
        layerItems.forEach { item ->
            if (item.id == itemId) return item
            if (item is OGLayer) {
                item.findItemById(itemId)?.let { return it }
            }
        }
        return null
    }

    fun moveItemUp(itemId: String) {
        if (isLocked) return
        val index = layerItems.indexOfFirst { it.id == itemId }
        if (index > 0) {
            layerItems.add(index - 1, layerItems.removeAt(index))
        }
    }

    fun moveItemDown(itemId: String) {
        if (isLocked) return
        val index = layerItems.indexOfFirst { it.id == itemId }
        if (index in 0 until layerItems.size - 1) {
            layerItems.add(index + 1, layerItems.removeAt(index))
        }
    }

    fun cloneWithoutTransform(): OGLayer {
        return this.copy(
            layerItems = layerItems.map { it.copyWithNewTransform(emptyList()) }.toMutableStateList()
        ).apply {
            this.transformations = mutableStateListOf()
        }
    }
}
