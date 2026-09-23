package com.solidkey.painpoints.image.gif

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.solidkey.painpoints.source.OGSource
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 🎞️ A decoded animated image: one [ImageBitmap] per frame, plus each frame's on-screen
 * duration in milliseconds. Built by the iOS Skia decoder ([rememberOGAnimatedPainter]).
 *
 * Android does NOT build this — there the platform's own `AnimatedImageDrawable` drives the
 * animation, so we never extract individual frames on that side.
 */
class OGGifAnimation(
    val frames: List<ImageBitmap>,
    val frameDurationsMs: List<Int>
) {
    init { require(frames.isNotEmpty()) { "OGGifAnimation needs at least one frame" } }

    val frameCount: Int get() = frames.size
}

/**
 * ⏱️ The pure, platform-free timing engine behind an animated GIF: it holds the current frame
 * index (as Compose snapshot state, so reading it in a draw call re-draws when it changes) and
 * advances that index over time according to each frame's duration.
 *
 * Split out from [OGAnimatedImagePainter] with NO [ImageBitmap] dependency so it can be unit-tested
 * with virtual time on the JVM (constructing a real [ImageBitmap] needs a live Android/Skia backend).
 */
class OGGifClock(
    val frameCount: Int,
    private val frameDurationsMs: List<Int>
) {
    var currentIndex by mutableIntStateOf(0)
        private set

    /**
     * Cycles [currentIndex] through the frames, waiting each frame's duration before advancing.
     * Suspends forever while [loop] is true; plays through exactly once otherwise. Frames with a
     * missing or non-positive duration fall back to [DEFAULT_FRAME_MS]. [speed] > 1 plays faster,
     * < 1 slower (≤ 0 is treated as 1). A single-frame image returns immediately. Cancel the
     * calling coroutine (e.g. leave the composition) to stop.
     */
    suspend fun run(loop: Boolean = true, speed: Float = 1f) {
        if (frameCount <= 1) return
        val effectiveSpeed = if (speed <= 0f) 1f else speed
        do {
            for (i in 0 until frameCount) {
                currentIndex = i
                val frameMs = frameDurationsMs.getOrElse(i) { DEFAULT_FRAME_MS }
                    .let { if (it <= 0) DEFAULT_FRAME_MS else it }
                delay((frameMs / effectiveSpeed).toLong())
            }
        } while (loop)
    }

    companion object {
        /** GIF spec allows 0-delay frames; browsers clamp those, so we use a sane default too. */
        const val DEFAULT_FRAME_MS = 100
    }
}

/**
 * 🖼️ A [Painter] that renders the current frame of an [OGGifAnimation] and scales it to fill the
 * area Compose gives it (so it honours the host [androidx.compose.ui.layout.ContentScale]). The
 * frame index comes from an [OGGifClock], whose value is snapshot state — advancing it via
 * [animate] re-invokes [onDraw] with no manual invalidation.
 */
class OGAnimatedImagePainter(private val animation: OGGifAnimation) : Painter() {
    private val clock = OGGifClock(animation.frameCount, animation.frameDurationsMs)

    private var alpha: Float = 1f
    private var colorFilter: ColorFilter? = null

    private val firstFrame = animation.frames[0]
    override val intrinsicSize: Size =
        Size(firstFrame.width.toFloat(), firstFrame.height.toFloat())

    /** The frame currently being shown — exposed for inspection/testing. */
    val currentFrameIndex: Int get() = clock.currentIndex

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }

    override fun DrawScope.onDraw() {
        val frame = animation.frames[clock.currentIndex]
        drawImage(
            image = frame,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(frame.width, frame.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            alpha = alpha,
            colorFilter = colorFilter
        )
    }

    /** Drive the animation. See [OGGifClock.run]. */
    suspend fun animate(loop: Boolean = true, speed: Float = 1f) = clock.run(loop, speed)
}

/**
 * 🎬 Load [source] as an animated image and return a [Painter] that plays it, or `null` while the
 * image is still decoding / if it could not be loaded. Feed the result into a Compose
 * `Image(painter = …)` (this is what [com.solidkey.painpoints.image.OGImageView] does automatically
 * for `.gif` sources).
 *
 * - **Android:** decodes via `ImageDecoder` into a self-animating `AnimatedImageDrawable`
 *   (API 28+); on API 24–27 it shows the static first frame. `speed` is ignored (the platform
 *   drawable has no speed control); `loop` maps to the drawable's repeat count.
 * - **iOS:** decodes every frame with Skia's `Codec` and cycles them; both `loop` and `speed`
 *   are honoured.
 *
 * Decoding happens off the main thread. GIF, animated WebP and other animated formats the platform
 * decoder understands are all supported.
 */
@Composable
expect fun rememberOGAnimatedPainter(
    source: OGSource,
    loop: Boolean = true,
    speed: Float = 1f,
    onError: ((String) -> Unit)? = null
): Painter?
