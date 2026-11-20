package com.solidkey.painpoints.fx

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds

/**
 * Renders an expanding impact shockwave ring ([spec]) centered at ([cx], [cy]) as [progress]
 * runs `0..1`. The view is transparent everywhere it isn't drawn.
 */
@Composable
fun OGShockwaveView(
    spec: OGShockwaveSpec,
    modifier: Modifier = Modifier,
    cx: Float = 0.5f,
    cy: Float = 0.5f,
    progress: Float = 1f,
) {
    val s = remember(spec) { spec.sanitized() }
    Canvas(modifier.clipToBounds()) {
        drawOGShockwaveFrame(s, cx, cy, progress)
    }
}
