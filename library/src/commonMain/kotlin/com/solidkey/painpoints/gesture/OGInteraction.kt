package com.solidkey.painpoints.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * How [Modifier.ogInteractive][ogInteractive] behaves: which gestures are live, scale limits, and
 * the momentum-fling / spring-settle feel. Size-independent — pan limits are expressed as a
 * *fraction* of the content's own size ([maxPanFractionX] / [maxPanFractionY]) so the same config
 * works at any resolution. Presets [DragOnly] / [PanZoom] / [All] cover the common cases, mirroring
 * the [com.solidkey.painpoints.depth.OGDepthConfig] preset style.
 *
 * @param enablePan one- or two-finger drag translates the content.
 * @param enableZoom pinch scales the content, clamped to [minScale]..[maxScale].
 * @param enableRotate two-finger twist rotates the content.
 * @param minScale lower scale clamp.
 * @param maxScale upper scale clamp.
 * @param fling when `true`, the release velocity carries the content on with an exponential decay.
 * @param flingFriction decay friction — higher stops the fling sooner (1f = default momentum).
 * @param maxPanFractionX pan limit on X as a fraction of width (e.g. `0.5f` = half a width each way
 *   from centre). `Float.POSITIVE_INFINITY` = unbounded. Content springs back inside the limit on
 *   release.
 * @param maxPanFractionY pan limit on Y as a fraction of height. `Float.POSITIVE_INFINITY` = unbounded.
 * @param settleDampingRatio spring damping for the settle-back (lower = bouncier).
 * @param settleStiffness spring stiffness for the settle-back.
 */
data class OGInteractionConfig(
    val enablePan: Boolean = true,
    val enableZoom: Boolean = true,
    val enableRotate: Boolean = true,
    val minScale: Float = 1f,
    val maxScale: Float = 5f,
    val fling: Boolean = true,
    val flingFriction: Float = 1.1f,
    val maxPanFractionX: Float = Float.POSITIVE_INFINITY,
    val maxPanFractionY: Float = Float.POSITIVE_INFINITY,
    val settleDampingRatio: Float = Spring.DampingRatioLowBouncy,
    val settleStiffness: Float = Spring.StiffnessMedium,
) {
    /** Resolve the pan limits (a fraction of size) into pixel bounds for the given box [size]. */
    fun panBoundsPx(size: IntSize): OGPanBounds = OGPanBounds(
        maxX = if (maxPanFractionX.isInfinite()) Float.POSITIVE_INFINITY else maxPanFractionX * size.width,
        maxY = if (maxPanFractionY.isInfinite()) Float.POSITIVE_INFINITY else maxPanFractionY * size.height,
    )

    companion object {
        /** Drag + fling only — no pinch-zoom, no rotate. */
        val DragOnly: OGInteractionConfig = OGInteractionConfig(enableZoom = false, enableRotate = false)

        /** Drag + pinch-zoom, no rotate. */
        val PanZoom: OGInteractionConfig = OGInteractionConfig(enableRotate = false)

        /** Everything: drag + pinch-zoom + rotate. */
        val All: OGInteractionConfig = OGInteractionConfig()
    }
}

/** Symmetric pan limits in pixels: the content may travel `±maxX` / `±maxY` from its resting centre. */
data class OGPanBounds(val maxX: Float, val maxY: Float) {
    companion object {
        val Unbounded: OGPanBounds = OGPanBounds(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    }
}

// ---------------------------------------------------------------------------
// Pure derivations — no Compose runtime, so they are unit-testable in commonTest.
// ---------------------------------------------------------------------------

/** Clamp a scale into `[min, max]`. */
internal fun clampScale(scale: Float, min: Float, max: Float): Float = scale.coerceIn(min, max)

/** Apply a pinch [zoomChange] multiplier to [current] scale, clamped to `[min, max]`. */
internal fun applyZoom(current: Float, zoomChange: Float, min: Float, max: Float): Float =
    clampScale(current * zoomChange, min, max)

/** Clamp a pan [offset] into the symmetric pixel [bounds]. Infinite bounds leave it untouched. */
internal fun clampOffset(offset: Offset, bounds: OGPanBounds): Offset =
    Offset(offset.x.coerceIn(-bounds.maxX, bounds.maxX), offset.y.coerceIn(-bounds.maxY, bounds.maxY))

/**
 * Map a vector from the layer's **local** (rotated + uniformly scaled) space back into its
 * **parent / screen** space.
 *
 * The gesture loop lives *inside* the transformed `graphicsLayer`, so a drag delta / fling velocity
 * arrives expressed in the content's own rotated+scaled frame. The pan it feeds, however, is written
 * into `translationX/Y`, which the layer applies in parent space. Without this remap a drag drifts
 * off-finger once the content is rotated (direction wrong) or zoomed (magnitude wrong). The linear
 * part of the layer transform is `scale * R(rotationDeg)`, so recovering the parent-space vector is
 * exactly that applied to the local [v].
 */
internal fun localPanToParent(v: Offset, rotationDeg: Float, scale: Float): Offset {
    val rad = rotationDeg * (PI.toFloat() / 180f)
    val c = cos(rad)
    val s = sin(rad)
    return Offset(
        (v.x * c - v.y * s) * scale,
        (v.x * s + v.y * c) * scale,
    )
}

// ---------------------------------------------------------------------------
// State holder
// ---------------------------------------------------------------------------

/**
 * The live pan / zoom / rotate transform driven by [Modifier.ogInteractive][ogInteractive]. Each
 * channel is an [Animatable] so it can be read (for a HUD, or to drive other effects), snapped
 * during a gesture, and smoothly flung / settled on release. Create it with
 * [rememberOGInteractionState].
 *
 * `offset` is the pan translation in pixels, `scale` the zoom factor, `rotation` the angle in
 * degrees. The modifier applies all three through a single `graphicsLayer`, so updating them is a
 * GPU-layer change with no recomposition.
 */
class OGInteractionState internal constructor(
    initialOffset: Offset,
    initialScale: Float,
    initialRotation: Float,
    val config: OGInteractionConfig,
) {
    val offset: Animatable<Offset, *> = Animatable(initialOffset, Offset.VectorConverter)
    val scale: Animatable<Float, *> = Animatable(initialScale)
    val rotation: Animatable<Float, *> = Animatable(initialRotation)

    /**
     * Snap the transform to **absolute** values immediately (no animation), honoring the config's
     * enables + clamps. The modifier accumulates the running totals synchronously inside its gesture
     * loop and calls this with absolute values, so concurrent updates can't lose deltas via a
     * read-modify-write race (a plain `snapTo(value + delta)` would, under a fast fling).
     */
    internal suspend fun snapTransform(offsetPx: Offset, scaleValue: Float, rotationDeg: Float) {
        if (config.enableZoom) scale.snapTo(clampScale(scaleValue, config.minScale, config.maxScale))
        if (config.enableRotate) rotation.snapTo(rotationDeg)
        if (config.enablePan) offset.snapTo(offsetPx)
    }

    /**
     * Called on gesture release: carry the pan on with a momentum fling (if [OGInteractionConfig.fling]),
     * then spring the pan back inside the configured bounds. Scale is already clamped live, so it needs
     * no settle.
     */
    internal suspend fun settle(velocity: Velocity, size: IntSize) {
        if (!config.enablePan) return
        val bounds = config.panBoundsPx(size)
        if (config.fling && (abs(velocity.x) > FLING_MIN_VELOCITY || abs(velocity.y) > FLING_MIN_VELOCITY)) {
            val decay = exponentialDecay<Offset>(frictionMultiplier = config.flingFriction)
            offset.animateDecay(Offset(velocity.x, velocity.y), decay)
        }
        val target = clampOffset(offset.value, bounds)
        if (target != offset.value) {
            offset.animateTo(target, spring(config.settleDampingRatio, config.settleStiffness))
        }
    }

    /** Animate everything back to the resting identity transform (no pan, scale 1, no rotation). */
    suspend fun reset() = coroutineScope {
        launch { offset.animateTo(Offset.Zero, spring(config.settleDampingRatio, config.settleStiffness)) }
        launch { scale.animateTo(1f, spring(stiffness = config.settleStiffness)) }
        launch { rotation.animateTo(0f, spring(stiffness = config.settleStiffness)) }
    }

    private companion object {
        /** Below this release speed (px/s) we don't bother flinging. */
        const val FLING_MIN_VELOCITY = 50f
    }
}

/**
 * Remember an [OGInteractionState] for [Modifier.ogInteractive][ogInteractive]. The state is keyed
 * on [config], so changing the config rebuilds it (and resets the transform).
 */
@Composable
fun rememberOGInteractionState(
    config: OGInteractionConfig = OGInteractionConfig(),
    initialOffset: Offset = Offset.Zero,
    initialScale: Float = 1f,
    initialRotation: Float = 0f,
): OGInteractionState = remember(config) {
    OGInteractionState(initialOffset, initialScale, initialRotation, config)
}
