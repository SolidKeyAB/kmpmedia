package com.solidkey.painpoints.layer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * A small utility composable to apply x/y positioning externally.
 * This keeps visual movement separate from content rendering.
 */
@Composable
fun PositionedLayerContent(
    baseX: State<Float>,
    baseY: State<Float>,
    dragOffset: Offset = Offset.Zero,
    content: @Composable () -> Unit
) {
    // The overall offset is the base position plus the drag offset.
    Box(
        modifier = Modifier.offset(
            (baseX.value + dragOffset.x).dp,
            (baseY.value + dragOffset.y).dp
        )
    ) {
        content()
    }
}
