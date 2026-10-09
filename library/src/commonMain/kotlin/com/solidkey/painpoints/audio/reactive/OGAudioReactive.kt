package com.solidkey.painpoints.audio.reactive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Drive live [OGAudioBands] from an [OGAudioSource] on the Compose frame clock. Returns a [State] you
 * read like any other; it updates once per frame while [playing]. Pair it with
 * [Modifier.ogAudioReactive] to make a composable pulse, or read a band straight into a spec (e.g.
 * `OGSlashes.WATER.copy(width = 0.12f * (1f + bands.treble))`).
 *
 * ```
 * val source = remember { OGSyntheticAudioSource(bpm = 120f) } // or your mic/music tap
 * val bands by rememberOGAudioReactive(source)
 * OGSVGView(..., modifier = Modifier.ogAudioReactive({ bands.bass }, scale = 1f..1.3f))
 * if (bands.beat) { /* fire a burst on the beat */ }
 * ```
 *
 * The analyzer and its buffers are remembered against [spec], so changing the spec rebuilds them; the
 * source is polled, never blocked.
 */
@Composable
fun rememberOGAudioReactive(
    source: OGAudioSource,
    spec: OGAudioReactiveSpec = OGAudioReactiveSpec(),
    playing: Boolean = true,
): State<OGAudioBands> {
    val analyzer = remember(spec) { OGAudioAnalyzer(spec) }
    val buffer = remember(spec) { FloatArray(spec.fftSize) }
    val bands = remember { mutableStateOf(OGAudioBands.Silent) }
    LaunchedEffect(analyzer, playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            withFrameNanos {
                val read = source.read(buffer)
                if (read > 0) {
                    bands.value = analyzer.process(if (read == buffer.size) buffer else buffer.copyOf(read))
                }
            }
        }
    }
    return bands
}

/**
 * Make any composable **react to sound** by mapping a band value to a GPU transform. Pass a lambda that
 * reads the band you want (e.g. `{ bands.bass }`); it is read inside a `graphicsLayer`, so the layer
 * re-renders each frame the value changes **without recomposing** the content — cheap enough for 60fps
 * on anything (an SVG, a particle view, a photo, text).
 *
 * The band (0..1) is linearly mapped onto each supplied range: [scale] (applied to both axes),
 * optionally [alpha] and [rotationDeg]. Ranges whose ends are equal (or `null`) leave that property
 * untouched, so `scale = 1f..1.3f` gives a pulse, `alpha = 0.4f..1f` a throb, and so on.
 */
fun Modifier.ogAudioReactive(
    level: () -> Float,
    scale: ClosedFloatingPointRange<Float> = 1f..1.2f,
    alpha: ClosedFloatingPointRange<Float>? = null,
    rotationDeg: ClosedFloatingPointRange<Float>? = null,
): Modifier = this.graphicsLayer {
    val v = level().coerceIn(0f, 1f)
    val s = lerpf(scale.start, scale.endInclusive, v)
    scaleX = s
    scaleY = s
    alpha?.let { this.alpha = lerpf(it.start, it.endInclusive, v) }
    rotationDeg?.let { rotationZ = lerpf(it.start, it.endInclusive, v) }
}

private fun lerpf(a: Float, b: Float, t: Float): Float = a + (b - a) * t
