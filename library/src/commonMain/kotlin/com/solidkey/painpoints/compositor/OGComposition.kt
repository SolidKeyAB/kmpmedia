package com.solidkey.painpoints.compositor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import com.solidkey.painpoints.look.OGBlendMode
import com.solidkey.painpoints.look.OGLookSpec
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The visual content of one [OGCompositionLayer].
 *
 * v1 layers are **still** content — a decoded [Image] bitmap or a [Solid] fill — animated by the
 * layer's keyframed transform (move / scale / rotate / fade) and shaped by its clip. Moving content
 * (a running GIF or video *inside* a composition) is intentionally out of scope for the first cut; a
 * frame can already hold any single decoded bitmap, including one grabbed from a video.
 */
sealed interface OGLayerContent {
    /** A decoded still image, drawn into the layer box per its [OGCompositionLayer.contentScale]. */
    data class Image(val bitmap: ImageBitmap) : OGLayerContent

    /** A flat colour fill of the whole layer box. */
    data class Solid(val color: Color) : OGLayerContent
}

/**
 * One layer of an [OGComposition]: some [content] placed in a `width` × `height` box whose top-left,
 * scale, rotation and opacity are each an [OGKeyframedFloat] track (all keyframe times are **absolute
 * composition time**). An optional [clip] `Shape` reuses the whole KMPMedia shape system — a built-in
 * `OGShapeType.toShape()`, an `OGPolygonShape` lasso, an `OGMultiRegionShape`, a `CircleShape`, … — so
 * a composition layer can be clipped to any shape a media surface can.
 *
 * The layer is only drawn while the composition clock is within `[startMs, endMs]` (an open-ended
 * `endMs = null` runs to the composition end), which lets layers enter and leave over the timeline.
 */
data class OGCompositionLayer(
    val id: String,
    val content: OGLayerContent,
    val width: Float,
    val height: Float,
    val x: OGKeyframedFloat = OGKeyframedFloat.const(0f),
    val y: OGKeyframedFloat = OGKeyframedFloat.const(0f),
    val scale: OGKeyframedFloat = OGKeyframedFloat.const(1f),
    val rotationDeg: OGKeyframedFloat = OGKeyframedFloat.const(0f),
    val opacity: OGKeyframedFloat = OGKeyframedFloat.const(1f),
    val clip: Shape? = null,
    val contentScale: ContentScale = ContentScale.Crop,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    /** An optional colour grade applied to this layer's content (a data-defined "live look"). */
    val look: OGLookSpec? = null,
    /** How this layer composites over the layers beneath it. [OGBlendMode.NORMAL] = source-over. */
    val blend: OGBlendMode = OGBlendMode.NORMAL,
)

/** A layer flattened at one instant — every track evaluated to a plain value, ready to draw. */
data class OGResolvedLayer(
    val content: OGLayerContent,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val scale: Float,
    val rotationDeg: Float,
    val opacity: Float,
    val clip: Shape?,
    val contentScale: ContentScale,
    val look: OGLookSpec? = null,
    val blend: OGBlendMode = OGBlendMode.NORMAL,
)

/**
 * A **declarative, on-device composition**: a fixed [width] × [height] canvas, a [durationMs]
 * timeline at [fps], a [background] fill, and a stack of [layers] (drawn back-to-front). This is the
 * "author on-device" half of the roadmap's Bet 3 — combine images, colours and shapes on a shared
 * timeline, preview it live at 60fps (`OGCompositionView`), then render it out to a shareable
 * animated GIF (`exportGif`) or grab any single frame (`renderFrame`).
 *
 * The model is pure data; nothing platform-specific lives here, and rendering/encoding are all done in
 * shared code, so a composition behaves identically on Android and iOS.
 */
data class OGComposition(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val layers: List<OGCompositionLayer>,
    val fps: Int = 24,
    val background: Color = Color.Transparent,
) {
    /** Number of frames the export walks: `round(durationMs/1000 * fps)`, at least one. */
    val frameCount: Int
        get() = max(1, (durationMs.toDouble() / 1000.0 * fps).roundToInt())

    /** Absolute time (ms) of frame [index], evenly spaced from 0. */
    fun frameTimeMs(index: Int): Long = index.toLong() * 1000L / fps.toLong()

    /**
     * Per-frame GIF delay in **centiseconds** (GIF's timing unit). Clamped to `>= 2cs`: most viewers
     * treat sub-2cs delays as "as fast as possible", which is inconsistent, so we never emit them.
     */
    val frameDelayCs: Int
        get() = max(2, (100.0 / fps).roundToInt())

    /** Every layer visible at [timeMs], flattened to plain values, in back-to-front draw order. */
    fun resolveAt(timeMs: Long): List<OGResolvedLayer> =
        layers.filter { it.isVisibleAt(timeMs, durationMs) }.map { it.resolveAt(timeMs) }
}

/** True while [timeMs] is within the layer's `[startMs, endMs]` window (`endMs == null` → [durationMs]). */
internal fun OGCompositionLayer.isVisibleAt(timeMs: Long, durationMs: Long): Boolean {
    val end = endMs ?: durationMs
    return timeMs >= startMs && timeMs <= end
}

/** Flatten one layer's tracks at [timeMs]. Pure — no Compose drawing, just value evaluation. */
internal fun OGCompositionLayer.resolveAt(timeMs: Long): OGResolvedLayer = OGResolvedLayer(
    content = content,
    x = x.valueAt(timeMs),
    y = y.valueAt(timeMs),
    width = width,
    height = height,
    scale = scale.valueAt(timeMs),
    rotationDeg = rotationDeg.valueAt(timeMs),
    opacity = opacity.valueAt(timeMs).coerceIn(0f, 1f),
    clip = clip,
    contentScale = contentScale,
    look = look,
    blend = blend,
)
