package com.solidkey.painpoints.depth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs

/**
 * **Depth / layer management** for KMPMedia surfaces.
 *
 * A single `depth` in `0f..1f` places content on a virtual front-to-back axis — `0f` = deepest
 * (far / behind → "under"), `1f` = nearest (close / in front → "over") — relative to a *focal plane*
 * (`focalDepth`, the depth that is in sharp focus). From those two numbers [ogDepth] derives three
 * effects that together read as 3D depth:
 *
 *  1. **Z-order** — `Modifier.zIndex(depth)`, so a nearer object draws in front of a farther one and
 *     content literally passes *over* or *under* its neighbours as its depth changes.
 *  2. **Depth-of-field** — a `Modifier.blur` + dim (`alpha`) that grows with the distance from the
 *     focal plane, so only content near the focus is crisp.
 *  3. **Optional parallax scale** — content nearer the camera can render slightly larger
 *     ([OGDepthConfig.depthScale]), for a stronger "coming closer" read.
 *
 * This is **not** the editor layer-stack in [com.solidkey.painpoints.layer] (draggable image/SVG/text
 * items); this is the *rendering* depth of any composable.
 *
 * ### Cost
 * `zIndex` is a parent-data hint — it only reorders draw calls, so it is effectively free. `alpha`
 * and `scale` ride one `graphicsLayer` (a GPU compositing layer, the same primitive the animation
 * primitives already use). **`blur` is the only heavy effect**: it is a `RenderEffect` (Android 31+)
 * / Skia `ImageFilter` (iOS) that renders the content to an offscreen layer and runs a Gaussian
 * shader. For a *still* surface it is drawn once and cached until invalidated (cheap); over an
 * *animating* surface it re-runs each frame. [ogDepth] therefore only ever adds a blur layer when
 * the object is actually off-focus (radius > ~0), scales the radius with the focal distance, and
 * skips blur entirely when [OGDepthConfig.blurContent] is `false` — set that for video/native
 * surfaces, where blurring a `TextureView`/`AVPlayerLayer` is unreliable and costly (see
 * [OGDepthConfig.Video]). An in-focus object with the default config gets **only** `zIndex` — no
 * extra layer at all.
 *
 * On Android < 31 `Modifier.blur` is a no-op (no crash); depth-of-field then degrades gracefully to
 * z-order + dim. On iOS the Skia blur works on every version.
 *
 * ### Usage
 * Explicit focal plane on one object:
 * ```
 * OGImageView(..., modifier = Modifier.ogDepth(depth = 0.2f, focalDepth = shipDepth))
 * ```
 * A whole field sharing one focal plane (children read it from the ambient [LocalOGFocalDepth]):
 * ```
 * OGDepthField(focalDepth = shipDepth) {
 *     OGDepthObject(depth = 0.2f) { OGImageView(...) }                       // blurred (far)
 *     OGDepthObject(depth = shipDepth) { PlayerShip() }                      // crisp (in focus)
 *     OGDepthObject(depth = 0.9f, config = OGDepthConfig.Video) { OGAVPlayer(...) } // dim, no blur
 * }
 * ```
 */

/** Ambient focal plane provided by [OGDepthField]; consumed by [OGDepthObject] when no explicit
 *  `focalDepth` is passed. Defaults to `0.5f` (mid-depth) outside any field. */
val LocalOGFocalDepth: ProvidableCompositionLocal<Float> = compositionLocalOf { 0.5f }

/**
 * Parallax scale endpoints for [OGDepthConfig.depthScale]: an object's `graphicsLayer` scale is
 * linearly interpolated from [far] (at `depth == 0f`) to [near] (at `depth == 1f`). e.g.
 * `OGDepthScale(far = 0.85f, near = 1.15f)` makes far content shrink and near content grow.
 */
data class OGDepthScale(val far: Float, val near: Float)

/**
 * Tuning for [ogDepth] / [OGDepthObject]. Defaults suit static/animated raster & vector content.
 *
 * @param maxBlur blur radius applied at the maximum focal distance (`|depth - focalDepth| == 1f`);
 *   the radius scales linearly with that distance and is `0.dp` in focus.
 * @param minAlpha the dimmest an off-focus object may become — its alpha floor.
 * @param dimFalloff how fast alpha drops with focal distance: `alpha = 1 - dz * dimFalloff`, clamped
 *   to [minAlpha]. `0f` disables dimming.
 * @param blurContent set `false` to skip blur entirely (video / native surfaces, or when you only
 *   want z-order + dim). See [Video].
 * @param depthScale optional parallax scaling by depth; `null` = no scaling.
 */
data class OGDepthConfig(
    val maxBlur: Dp = 12.dp,
    val minAlpha: Float = 0.4f,
    val dimFalloff: Float = 0.5f,
    val blurContent: Boolean = true,
    val depthScale: OGDepthScale? = null,
) {
    companion object {
        /**
         * Preset for **video / native surfaces**: z-order + dim only, no blur — a `RenderEffect`
         * blur over a `TextureView`/`AVPlayerLayer` is costly and unreliable, so depth reads via
         * z-order and alpha instead.
         */
        val Video: OGDepthConfig = OGDepthConfig(blurContent = false)
    }
}

// ---------------------------------------------------------------------------
// Pure derivations — no Compose runtime, so they are unit-testable in commonTest.
// [ogDepth] is a thin wiring layer over exactly these.
// ---------------------------------------------------------------------------

/** Clamp a raw depth/focal value into the valid `0f..1f` range. */
internal fun clampDepth(value: Float): Float = value.coerceIn(0f, 1f)

/** Focal distance: `|depth - focalDepth|` after clamping both to `0f..1f`. `0f` = in focus. */
fun depthDistance(depth: Float, focalDepth: Float): Float =
    abs(clampDepth(depth) - clampDepth(focalDepth))

/** Alpha for content at [depth] given [focalDepth]: `1f` in focus, falling to [OGDepthConfig.minAlpha]. */
fun OGDepthConfig.alphaFor(depth: Float, focalDepth: Float): Float =
    (1f - depthDistance(depth, focalDepth) * dimFalloff).coerceIn(minAlpha, 1f)

/** Blur radius for content at [depth] given [focalDepth]: `0.dp` in focus (or when [blurContent] is
 *  `false`), rising to [OGDepthConfig.maxBlur] at the maximum focal distance. */
fun OGDepthConfig.blurRadiusFor(depth: Float, focalDepth: Float): Dp =
    if (!blurContent) 0.dp else maxBlur * depthDistance(depth, focalDepth)

/** Parallax scale for content at [depth]: `1f` when [depthScale] is `null`, else lerp far→near. */
fun OGDepthConfig.scaleFor(depth: Float): Float {
    val s = depthScale ?: return 1f
    return s.far + (s.near - s.far) * clampDepth(depth)
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Places the modified content at [depth] on the front-to-back axis relative to [focalDepth],
 * applying z-order, depth-of-field (blur + dim) and optional parallax scale per [config].
 *
 * Apply it to any composable that lives in a shared parent (a `Box`, or an [OGDepthField]); the
 * z-order is only meaningful among siblings of that parent. In focus with the default config this
 * adds only `zIndex` (no compositing layer). See the file header for the cost model.
 */
fun Modifier.ogDepth(
    depth: Float,
    focalDepth: Float,
    config: OGDepthConfig = OGDepthConfig(),
): Modifier {
    val d = clampDepth(depth)
    val alpha = config.alphaFor(depth, focalDepth)
    val scale = config.scaleFor(depth)
    val blur = config.blurRadiusFor(depth, focalDepth)

    var m = this.zIndex(d)
    if (alpha != 1f || scale != 1f) {
        m = m.graphicsLayer {
            this.alpha = alpha
            if (scale != 1f) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
    // Sub-pixel radii round to nothing on screen but still force an offscreen layer — skip them.
    if (blur.value > 0.5f) {
        m = m.blur(blur)
    }
    return m
}

/**
 * A field of depth-layered content sharing one [focalDepth]. Provides [LocalOGFocalDepth] so any
 * [OGDepthObject] nested inside can omit its own `focalDepth`. It is a plain [Box] otherwise, so its
 * children stack in the same coordinate space that [ogDepth]'s z-order needs.
 */
@Composable
fun OGDepthField(
    focalDepth: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalOGFocalDepth provides focalDepth) {
        Box(modifier = modifier, content = content)
    }
}

/**
 * Convenience wrapper that puts [content] in a [Box] carrying [Modifier.ogDepth]. When [focalDepth]
 * is omitted it is read from the ambient [LocalOGFocalDepth] (set by an enclosing [OGDepthField]),
 * so a field of objects only has to declare each object's own [depth].
 */
@Composable
fun OGDepthObject(
    depth: Float,
    modifier: Modifier = Modifier,
    focalDepth: Float = LocalOGFocalDepth.current,
    config: OGDepthConfig = OGDepthConfig(),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.ogDepth(depth, focalDepth, config), content = content)
}
