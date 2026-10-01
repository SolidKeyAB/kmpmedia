package com.solidkey.painpoints.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch

/**
 * Make any composable **grab-drag-pinch-rotate interactive**, with momentum fling and a spring
 * settle — the interactivity half of KMPMedia's Bet 4 primitives, and a natural partner to the clip
 * shapes it already draws.
 *
 * It does two things in one modifier:
 *  1. **Applies** the [state]'s pan / zoom / rotation through a single `graphicsLayer` — a GPU-layer
 *     transform, so motion never triggers recomposition and holds 60fps.
 *  2. **Drives** that state from touch: one- or two-finger drag pans, pinch zooms, twist rotates
 *     (each toggled in [OGInteractionConfig]). On release the pan carries on with an exponential
 *     momentum fling and then springs back inside the configured bounds.
 *
 * Pass a [hitArea] to make the gesture **shape-aware**: a drag only starts if the finger lands
 * inside that normalized region, so when the content is clipped to a triangle / circle / lasso, the
 * transparent corners no longer grab it (and touches there fall through to whatever is behind). With
 * no [hitArea] the whole rectangle is interactive.
 *
 * ### Cost
 * The transform rides one `graphicsLayer` (the same primitive the depth/animation code uses); the
 * gesture loop is event-driven (nothing per frame); the hit-test is one pure ray-cast on the down
 * event only. The fling/settle are plain Compose [androidx.compose.animation.core.Animatable]
 * animations. No new dependency, identical on Android & iOS.
 *
 * ### Usage
 * ```
 * val interaction = rememberOGInteractionState(OGInteractionConfig.PanZoom)
 * OGImageView(
 *     source = photo,
 *     clipShape = TriangleShape(TriangleDirection.UP),
 *     modifier = Modifier
 *         .size(240.dp)
 *         .ogInteractive(interaction, hitArea = OGHitArea.TRIANGLE_UP),
 * )
 * ```
 *
 * @param state the transform to apply and drive; read it for a HUD or to call [OGInteractionState.reset].
 * @param hitArea optional normalized region a drag must start inside; `null` = the whole box.
 */
@Composable
fun Modifier.ogInteractive(
    state: OGInteractionState,
    hitArea: OGHitArea? = null,
): Modifier {
    val scope = rememberCoroutineScope()
    val config = state.config
    return this
        .graphicsLayer {
            val o = state.offset.value
            translationX = o.x
            translationY = o.y
            val s = state.scale.value
            scaleX = s
            scaleY = s
            rotationZ = state.rotation.value
        }
        .pointerInput(state, hitArea) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Shaped hit-test gate: ignore (and pass through) touches outside the clip shape.
                if (hitArea != null && size.width > 0 && size.height > 0) {
                    val nx = down.position.x / size.width
                    val ny = down.position.y / size.height
                    if (!hitArea.containsNormalized(nx, ny)) return@awaitEachGesture
                }

                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)

                // Accumulate the transform synchronously (ordered) and snap to ABSOLUTE values,
                // so concurrent snap coroutines under a fast fling can't lose deltas.
                var panTotal = state.offset.value
                var scaleTotal = state.scale.value
                var rotationTotal = state.rotation.value

                var canceled = false
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.isConsumed }) {
                        canceled = true
                        break
                    }
                    if (config.enableZoom) {
                        scaleTotal = clampScale(scaleTotal * event.calculateZoom(), config.minScale, config.maxScale)
                    }
                    if (config.enableRotate) rotationTotal += event.calculateRotation()
                    if (config.enablePan) panTotal += event.calculatePan()
                    scope.launch { state.snapTransform(panTotal, scaleTotal, rotationTotal) }
                    event.changes.firstOrNull { it.pressed }
                        ?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                    if (event.changes.none { it.pressed }) break // all fingers lifted
                }

                if (!canceled) {
                    val velocity = tracker.calculateVelocity()
                    scope.launch { state.settle(velocity, size) }
                }
            }
        }
}

/**
 * Convenience [ogInteractive] that remembers its own [OGInteractionState] from [config] — for when
 * you don't need to read the transform yourself. Equivalent to
 * `ogInteractive(rememberOGInteractionState(config), hitArea)`.
 */
@Composable
fun Modifier.ogInteractive(
    config: OGInteractionConfig = OGInteractionConfig(),
    hitArea: OGHitArea? = null,
): Modifier = ogInteractive(rememberOGInteractionState(config), hitArea)
