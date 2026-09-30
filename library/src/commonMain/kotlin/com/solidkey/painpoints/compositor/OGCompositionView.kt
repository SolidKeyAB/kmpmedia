package com.solidkey.painpoints.compositor

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.isActive

/**
 * Live, on-screen preview of an [OGComposition] — the "author on-device" half of Bet 3. It runs the
 * composition's timeline on Compose's frame clock (so it holds 60fps), fit-scales the fixed
 * `width` × `height` canvas into the available space, and draws straight to the screen via
 * [drawComposition] with **no intermediate bitmap** (that path is export-only, see [OGComposition.exportGif]).
 *
 * Two modes:
 * - **Playing** (default, [positionMs] `= null`): advances on its own, looping when [loop] is true, and
 *   reports the current time through [onProgress]. Toggle [isPlaying] to pause/resume in place.
 * - **Controlled** ([positionMs] set): shows exactly that instant and ignores the internal clock — wire a
 *   slider straight to it for a scrubber.
 *
 * @param contentScale how the composition rectangle fits the view (default [ContentScale.Fit]).
 */
@Composable
fun OGCompositionView(
    composition: OGComposition,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    loop: Boolean = true,
    positionMs: Long? = null,
    contentScale: ContentScale = ContentScale.Fit,
    onProgress: ((timeMs: Long) -> Unit)? = null,
) {
    var clockMs by remember(composition) { mutableStateOf(0L) }
    val controlled = positionMs != null

    LaunchedEffect(composition, isPlaying, loop, controlled) {
        if (controlled || !isPlaying) return@LaunchedEffect
        val duration = composition.durationMs.coerceAtLeast(1L)
        var last = withFrameMillis { it }
        while (isActive) {
            withFrameMillis { now ->
                val dt = now - last
                last = now
                var t = clockMs + dt
                if (t >= duration) t = if (loop) t % duration else duration
                clockMs = t
                onProgress?.invoke(t)
            }
        }
    }

    val timeMs = positionMs ?: clockMs
    Canvas(modifier) {
        val compSize = Size(composition.width.toFloat(), composition.height.toFloat())
        if (compSize.width <= 0f || compSize.height <= 0f) return@Canvas
        val factor = contentScale.computeScaleFactor(compSize, size)
        val dw = compSize.width * factor.scaleX
        val dh = compSize.height * factor.scaleY
        val dx = (size.width - dw) / 2f
        val dy = (size.height - dh) / 2f
        withTransform({
            translate(dx, dy)
            scale(factor.scaleX, factor.scaleY, pivot = Offset.Zero)
        }) {
            drawComposition(composition, timeMs)
        }
    }
}
