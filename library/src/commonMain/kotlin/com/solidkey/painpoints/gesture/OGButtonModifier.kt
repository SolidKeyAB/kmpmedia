package com.solidkey.painpoints.gesture

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.onLongClick as semanticsOnLongClick

/**
 * How a [Modifier.ogButton] gives visual press feedback. KMPMedia has no Material dependency, so the
 * usual `Indication`/ripple is unavailable — the press effect **is** the indication, drawn with one
 * `graphicsLayer` (or an extra `drawBehind` for the shadow), nothing per frame but the short settle.
 */
sealed interface OGPressEffect {
    /** Shrink to [scale] while pressed (e.g. `0.94` = a 6% squeeze), springing back on release. */
    data class Scale(val scale: Float = 0.94f) : OGPressEffect

    /** Fade to [alpha] while pressed (e.g. `0.6`), returning to opaque on release. */
    data class Dim(val alpha: Float = 0.6f) : OGPressEffect

    /**
     * The flat/pop-art "hard shadow push-in": the modifier **draws its own** hard-offset silhouette
     * shadow (no blur) behind the content at rest, and on press the content slides onto it by [offset]
     * while the shadow collapses — so the graphic looks pressed into the page. The silhouette is the
     * `hitArea` outline (so a matching [OGHitArea] is required for the shadow to trace the visible
     * shape; with a `null` hitArea it falls back to the full rectangle).
     */
    data class Brutalist(val offset: Dp = 3.dp, val shadowColor: Color = Color(0xFF111111)) : OGPressEffect

    /** No visual feedback — just the click + semantics. */
    data object None : OGPressEffect
}

/** The content transform at press depth [progress] (`0` = rest, `1` = fully pressed). Pure + testable. */
internal data class OGButtonTransform(val scale: Float, val alpha: Float, val translateFraction: Float)

internal fun OGPressEffect.transformAt(progress: Float): OGButtonTransform {
    val p = progress.coerceIn(0f, 1f)
    return when (this) {
        is OGPressEffect.Scale -> OGButtonTransform(1f + (scale - 1f) * p, 1f, 0f)
        is OGPressEffect.Dim -> OGButtonTransform(1f, 1f + (alpha - 1f) * p, 0f)
        is OGPressEffect.Brutalist -> OGButtonTransform(1f, 1f, p)
        OGPressEffect.None -> OGButtonTransform(1f, 1f, 0f)
    }
}

/**
 * Turn **any graphic into a button** — a photo ([com.solidkey.painpoints.image.OGImageView]), an SVG
 * ([com.solidkey.painpoints.image.svg.OGSVGView]), a shape-clipped or lasso-cut image, or a raw
 * `Canvas` drawing — in one modifier. It adds a tap→[onClick], a pressed-state visual ([pressEffect])
 * and `Role.Button` accessibility, with no UI component and no new dependency.
 *
 * It is the **tap twin** of [ogInteractive] (which is the *drag* half): both reuse [OGHitArea] so the
 * touch is **shape-aware**. Pass a [hitArea] matching the clip and a tap only counts **inside the real
 * silhouette** — taps in a triangle's / circle's / lasso's transparent corners fall through to whatever
 * is behind, instead of the bounding box swallowing them. With `null` the whole box is clickable.
 *
 * ### Cost
 * The hit-test is one pure ray-cast on the down event; the press visual rides one `graphicsLayer`
 * (`Brutalist` adds one cached-outline `drawBehind`), so there is nothing per frame but the short
 * press/settle animation. Identical on Android & iOS.
 *
 * ### Usage
 * ```
 * OGImageView(
 *     source = photo,
 *     clipShape = headLasso,                         // the visible cut-out
 *     modifier = Modifier
 *         .size(160.dp)
 *         .ogButton(
 *             hitArea = OGHitArea.polygon(headLasso), // only the head is tappable
 *             pressEffect = OGPressEffect.Brutalist(),
 *         ) { open() },
 * )
 * ```
 *
 * Notes: there is intentionally **no double-tap** (it would delay every single tap by the double-tap
 * timeout — a latency regression a button can't afford). When `enabled` is `false` the touch is still
 * consumed inside the silhouette (a no-op), so a disabled button never leaks taps to content behind it.
 * Don't also apply [ogInteractive] to the same node — the drag and the press would fight.
 *
 * @param hitArea normalized region a tap must start inside; `null` = the whole box.
 * @param enabled when `false`, no click/long-click and no press visual, but taps inside are still consumed.
 * @param pressEffect the pressed-state feedback ([OGPressEffect.Scale] / [OGPressEffect.Dim] /
 *   [OGPressEffect.Brutalist] / [OGPressEffect.None]).
 * @param onLongClick optional long-press action (fires after the platform long-press timeout).
 * @param contentDescription spoken label for TalkBack / VoiceOver (also exposes an activatable button action).
 * @param onClick fired on a tap released within the bounds.
 */
@Composable
fun Modifier.ogButton(
    hitArea: OGHitArea? = null,
    enabled: Boolean = true,
    pressEffect: OGPressEffect = OGPressEffect.Scale(),
    onLongClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    onClick: () -> Unit,
): Modifier {
    val pressed = remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (pressed.value) 1f else 0f,
        // Press in quickly; spring back with a little bounce on release.
        animationSpec = if (pressed.value) {
            tween(durationMillis = 40)
        } else {
            spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
        },
        label = "ogButtonPress",
    )
    val onClickState = rememberUpdatedState(onClick)
    val onLongClickState = rememberUpdatedState(onLongClick)

    // The silhouette the Brutalist shadow traces (cached). Empty for the other effects.
    val shadowOutline = remember(hitArea, pressEffect) {
        if (pressEffect is OGPressEffect.Brutalist) {
            (hitArea ?: OGHitArea.RECT).outlineNormalized()
        } else {
            emptyList()
        }
    }

    var m: Modifier = this

    if (pressEffect is OGPressEffect.Brutalist && shadowOutline.size >= 3) {
        // Drawn BEFORE the graphicsLayer below, so the shadow sits still while the content slides onto it.
        m = m.drawBehind {
            val rest = 1f - progress.coerceIn(0f, 1f)
            if (rest <= 0f) return@drawBehind
            val off = pressEffect.offset.toPx() * rest
            val path = Path().apply {
                shadowOutline.forEachIndexed { i, pt ->
                    val x = pt.x * size.width + off
                    val y = pt.y * size.height + off
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(path, color = pressEffect.shadowColor)
        }
    }

    m = m.graphicsLayer {
        val t = pressEffect.transformAt(progress)
        scaleX = t.scale
        scaleY = t.scale
        alpha = t.alpha
        if (pressEffect is OGPressEffect.Brutalist) {
            val off = pressEffect.offset.toPx() * t.translateFraction
            translationX = off
            translationY = off
        }
    }

    m = m.pointerInput(hitArea, enabled, pressEffect) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Shape-aware gate: a touch outside the silhouette is ignored and falls through.
            if (hitArea != null && size.width > 0 && size.height > 0) {
                val nx = down.position.x / size.width
                val ny = down.position.y / size.height
                if (!hitArea.containsNormalized(nx, ny)) return@awaitEachGesture
            }
            // Inside the silhouette → this press is ours (claim it so it doesn't fall through).
            down.consume()
            if (!enabled) return@awaitEachGesture // disabled: consumed, but a no-op.

            pressed.value = true
            try {
                val longClick = onLongClickState.value
                val released = if (longClick != null) {
                    try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) { awaitButtonUp(down.id) }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        longClick()
                        awaitButtonUp(down.id) // swallow the eventual lift; no click after a long-press
                        null
                    }
                } else {
                    awaitButtonUp(down.id)
                }
                if (released != null) {
                    released.consume()
                    onClickState.value()
                }
            } finally {
                pressed.value = false
            }
        }
    }

    m = m.semantics(mergeDescendants = true) {
        role = Role.Button
        contentDescription?.let { this.contentDescription = it }
        if (!enabled) disabled()
        semanticsOnClick {
            onClickState.value()
            true
        }
        onLongClickState.value?.let { lc ->
            semanticsOnLongClick {
                lc()
                true
            }
        }
    }

    return m
}

/**
 * Wait for the pressing pointer ([pointerId]) to lift, **consuming its movement** as it goes — the one
 * thing that makes a tap survive an ancestor scroll. A real finger is never perfectly still; once its
 * drift crosses the touch-slop threshold, a parent `verticalScroll`/`LazyColumn` will otherwise claim
 * the pointer and cancel the press (the plain `waitForUpOrCancellation` gives it up the moment someone
 * else consumes a move). By owning the in-bounds movement here — exactly as
 * [ogInteractive]'s drag loop does — the button keeps the gesture, so a slightly-moving tap still fires.
 *
 * Returns the up change to click on, or `null` to cancel: the pointer was consumed elsewhere, or it
 * slid **outside the box** (a deliberate drag-off — the press cancels and, since that move is left
 * unconsumed, the parent scroll can take over from there, so you can still scroll starting on a button).
 */
private suspend fun AwaitPointerEventScope.awaitButtonUp(pointerId: PointerId): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return null
        if (change.isConsumed) return null // another node already claimed this gesture
        val inside = change.position.x in 0f..size.width.toFloat() &&
            change.position.y in 0f..size.height.toFloat()
        if (change.changedToUp()) return if (inside) change else null // lift inside = click; outside = cancel
        if (!inside) return null // slid off the button → cancel, leaving the move for the parent (scroll)
        if (change.positionChanged()) change.consume() // own in-bounds drift so the scroll can't steal the tap
    }
}
