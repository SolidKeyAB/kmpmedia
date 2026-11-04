package com.solidkey.painpoints.fx

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds

/**
 * Renders a fracture / crack network ([spec]) growing from ([originX], [originY]) as [progress]
 * runs `0..1`. The view is transparent everywhere it isn't drawn.
 */
@Composable
fun OGCrackView(
    spec: OGCrackSpec,
    modifier: Modifier = Modifier,
    originX: Float = 0.5f,
    originY: Float = 0.5f,
    progress: Float = 1f,
) {
    val s = remember(spec) { spec.sanitized() }
    val arms = remember(s, originX, originY) { crackArms(s, originX, originY) }
    Canvas(modifier.clipToBounds()) {
        drawOGCrackFrame(s, arms, originX, originY, progress, size.width, size.height)
    }
}
