package com.solidkey.painpoints.layer

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger

@Composable
fun DraggableLayerContent(
    x: State<Float>,
    y: State<Float>,
    scaleFactor: Float,
    content: @Composable () -> Unit
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .offset(
                (x.value + dragOffset.x).dp,
                (y.value + dragOffset.y).dp
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Adjust the drag delta by dividing by the scale factor.
                    val adjustedDelta = dragAmount / scaleFactor
                    dragOffset += adjustedDelta
                    Logger.i("DraggableLayerContent>> Adjusted drag offset: $dragOffset")
                }
            }
    ) {
        content()
    }
}

