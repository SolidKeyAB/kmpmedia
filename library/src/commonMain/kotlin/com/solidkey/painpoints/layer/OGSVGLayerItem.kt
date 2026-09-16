package com.solidkey.painpoints.layer

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import com.solidkey.painpoints.image.history.OGTransformationHistoryHelper
import com.solidkey.painpoints.image.layering.OGLayerItemEvent
import com.solidkey.painpoints.image.processing.OGImageTransformation
import com.solidkey.painpoints.image.svg.OGSVGView
import com.solidkey.painpoints.image.svg.SVGScalingBehavior
import com.solidkey.painpoints.source.OGSourceType

class OGSVGLayerItem(
    override val id: String,
    x: MutableState<Float>,
    y: MutableState<Float>,
    override val source: OGSourceType, // The source for your SVG image (e.g. OGSvgUrlType)
    transformations: SnapshotStateList<OGImageTransformation> = mutableStateListOf(),
    historyHelper: OGTransformationHistoryHelper? = null,
    override val onEvent: (event: OGLayerItemEvent, id: String) -> Unit,
    gestureHandler: OGItemGestureHandler? = null,
    label: OGLayerItemLabel = OGLayerItemLabel()
) : OGPositionedLayerItem(
    id = id,
    x = x,
    y = y,
    source = source,
    // Here we provide the displayContent lambda. We wrap OGSVGView in PositionedLayerContent so that
    // the layer’s x and y are applied.
    displayContent = { tx, onEvent, gestureHandler ->
        val desiredWidth = 300f
        val desiredHeight = 300f
        var computedScale by remember { mutableStateOf(1f) }

        // Capture the instance's x and y. Either qualify as 'this.x' and 'this.y'
        DraggableLayerContent(x = x, y = y, computedScale) {
            OGSVGView(
                source = source,
                width = desiredWidth,
                height = desiredHeight,
                modifier = Modifier,
                enableDrag = false, // disable internal dragging; now drag the whole canvas
                scalingBehavior = SVGScalingBehavior.CLIP,
                followCommonPractices = true,
                onError = { errorMessage ->
                    onEvent(OGLayerItemEvent.ERROR, id)
                },
                onScaleComputed = { scale -> computedScale = scale }
            )
        }
    },
    transformations = transformations,
    historyHelper = historyHelper,
    onEvent = onEvent,
    gestureHandler = gestureHandler,
    label = label
) {
    override fun copyWithNewTransform(transformations: List<OGImageTransformation>): OGSVGLayerItem {
        return OGSVGLayerItem(
            id = this.id,
            x = this.x,
            y = this.y,
            source = this.source,
            transformations = mutableStateListOf<OGImageTransformation>().also { it.addAll(transformations) },
            historyHelper = this.historyHelper,
            onEvent = this.onEvent,
            gestureHandler = this.gestureHandler,
            label = this.label
        )
    }
}
