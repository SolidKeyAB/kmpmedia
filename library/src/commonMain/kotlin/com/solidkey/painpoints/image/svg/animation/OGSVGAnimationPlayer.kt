package com.solidkey.painpoints.image.svg.animation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.solidkey.painpoints.image.loading.OGParsedSVGResult
import com.solidkey.painpoints.image.loading.OGSvgLoader
import com.solidkey.painpoints.image.loading.ViewBox
import com.solidkey.painpoints.image.svg.OGSVGTreeElement
import com.solidkey.painpoints.image.svg.RenderShape
import com.solidkey.painpoints.image.svg.SVGScalingBehavior
import com.solidkey.painpoints.image.svg.prepareRenderShapes
import com.solidkey.painpoints.source.OGSource
import com.solidkey.painpoints.source.OGSourceType

/**
 * Renders an SVG that contains SMIL animations (`<animate>` / `<animateTransform>` /
 * `<animateMotion>`) and plays them on a real time-based clock.
 *
 * The static geometry is drawn through the exact same [prepareRenderShapes]
 * pipeline as [com.solidkey.painpoints.image.svg.OGSVGView], so every shape type,
 * gradient, pattern and viewBox scaling behaves identically. Each shape is then
 * wrapped in the animated transform of its owning element, computed per frame from
 * the animation's real `from`/`to`/`values` and `dur`.
 */
@Composable
fun OGSVGAnimationPlayer(
    source: OGSourceType,
    width: Float,
    height: Float,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    loop: Boolean = true,
    scalingBehavior: SVGScalingBehavior = SVGScalingBehavior.CLIP,
    followCommonPractices: Boolean = false,
    onError: ((String) -> Unit)? = null
) {
    var isSvgLoaded by remember { mutableStateOf(false) }
    var viewBox by remember { mutableStateOf<ViewBox?>(null) }
    var parsedResult by remember { mutableStateOf<OGParsedSVGResult?>(null) }
    var renderShapes by remember { mutableStateOf<List<RenderShape>>(emptyList()) }
    var hasAnimations by remember { mutableStateOf(false) }
    // Seconds of wall-clock time elapsed since the animation started; drives every
    // animation's local progress via its own `dur`.
    var elapsedSeconds by remember { mutableStateOf(0f) }

    val location = source.getLocation() ?: run {
        onError?.invoke("❌ Failed to resolve SVG location.")
        return
    }

    val svgSource = when (source.getType()) {
        OGSourceType.SourceType.URL -> OGSource.Url(location)
        OGSourceType.SourceType.FILE -> OGSource.FilePath(location)
        OGSourceType.SourceType.RESOURCE -> OGSource.Resource(location)
    }

    OGSvgLoader.loadSvg(svgSource, width, height) { result ->
        if (result == null) {
            onError?.invoke("❌ Failed to load SVG from: $location")
            return@loadSvg
        }
        val svgTree = result.svgTree
        if (svgTree == null) {
            onError?.invoke("❌ SVG loaded but contained no renderable tree: $location")
            return@loadSvg
        }

        val resolvedViewBox = result.viewBox
        // Guard against a degenerate viewBox to avoid a divide-by-zero scale.
        val vbWidth = resolvedViewBox.width.takeIf { it != 0f } ?: width
        val vbHeight = resolvedViewBox.height.takeIf { it != 0f } ?: height

        renderShapes = prepareRenderShapes(
            svgTree = svgTree,
            scale = width / vbWidth,
            scaleX = width / vbWidth,
            scaleY = height / vbHeight,
            offsetX = 0f,
            offsetY = 0f,
            scalingBehavior = scalingBehavior,
            width = width,
            height = height,
            followCommonPractices = followCommonPractices,
            gradients = result.gradients,
            patterns = result.patterns,
            viewBox = resolvedViewBox
        )

        parsedResult = result
        viewBox = resolvedViewBox
        hasAnimations = treeHasAnimations(svgTree)
        isSvgLoaded = true
    }

    // Advance the clock every frame while playing. withFrameNanos gives an
    // accurate per-frame delta regardless of device frame rate.
    LaunchedEffect(hasAnimations, isPlaying) {
        if (!hasAnimations || !isPlaying) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    elapsedSeconds += (now - last) / 1_000_000_000f
                }
                last = now
            }
        }
    }

    if (isSvgLoaded) {
        val vb = viewBox ?: ViewBox(0f, 0f, width, height)
        val vbW = vb.width.takeIf { it != 0f } ?: width
        val vbH = vb.height.takeIf { it != 0f } ?: height
        val scale = width / vbW
        val computedHeight = width * (vbH / vbW)

        Canvas(
            modifier = modifier
                .size(width.dp, computedHeight.dp)
                .background(Color.Transparent)
        ) {
            val scaleX = width / vbW
            val scaleY = height / vbH

            withTransform({
                when (scalingBehavior) {
                    SVGScalingBehavior.CLIP -> clipRect(left = 0f, top = 0f, right = width, bottom = height)
                    SVGScalingBehavior.SCALE -> scale(scaleX, scaleY)
                    SVGScalingBehavior.FIT -> scale(scaleX, scaleY)
                    SVGScalingBehavior.ALLOW_OVERFLOW -> Unit
                }
            }) {
                // Pivot for pivot-less transforms (e.g. scale/pulse): the viewBox
                // centre in the same baked coordinate space as the shapes, so a
                // pulsing sprite grows/shrinks in place instead of toward (0,0).
                val center = Offset(vbW * scale / 2f, vbH * scale / 2f)
                renderShapes.forEach { renderShape ->
                    val anims = renderShape.element?.animations
                    if (!anims.isNullOrEmpty()) {
                        withTransform({
                            anims.forEach { anim -> applySvgAnimation(anim, anim.progressAt(elapsedSeconds), scale, center) }
                        }) {
                            renderShape.drawAction(this, Offset.Zero)
                        }
                    } else {
                        renderShape.drawAction(this, Offset.Zero)
                    }
                }
            }
        }
    }
}

private val TRANSLATE_X_ATTRS = setOf("x", "cx", "translatex")
private val TRANSLATE_Y_ATTRS = setOf("y", "cy", "translatey")

/** Local 0..1 progress for a single animation, honouring its own `dur` and repeat. */
private fun OGSVGAnimation.progressAt(elapsedSeconds: Float): Float {
    val d = if (duration > 0f) duration else 1f
    return if (repeatCount == "indefinite") {
        (elapsedSeconds % d) / d
    } else {
        (elapsedSeconds / d).coerceIn(0f, 1f)
    }
}

/**
 * Applies one animation as a draw transform. Offsets/pivots are multiplied by
 * [scale] so they live in the same (pre-scale) coordinate space as the shapes
 * produced by prepareRenderShapes.
 */
private fun DrawTransform.applySvgAnimation(anim: OGSVGAnimation, progress: Float, scale: Float, center: Offset) {
    when {
        // <animateTransform type="translate" from="x,y" to="x,y" | values="x,y; x,y; …">
        anim.attributeName == "transform" && anim.type == "translate" -> {
            val v = anim.getInterpolatedVec(progress)
            translate((v?.getOrNull(0) ?: 0f) * scale, (v?.getOrNull(1) ?: 0f) * scale)
        }
        // <animateTransform type="rotate" from="angle [cx cy]" to="angle [cx cy]">
        anim.attributeName == "transform" && anim.type == "rotate" -> {
            val v = anim.getInterpolatedVec(progress)
            val angle = v?.getOrNull(0) ?: anim.getInterpolatedValue(progress) ?: 0f
            // Rotate around the SVG-provided pivot (cx,cy) when present, else the
            // viewBox centre so an in-place spinner rotates about its middle.
            val pivot = if (v != null && v.size >= 3) Offset(v[1] * scale, v[2] * scale) else center
            rotate(angle, pivot = pivot)
        }
        // <animateTransform type="scale" from="sx[,sy]" to="sx[,sy]" | values="…">
        anim.attributeName == "transform" && anim.type == "scale" -> {
            val v = anim.getInterpolatedVec(progress)
            val sx = v?.getOrNull(0) ?: anim.getInterpolatedValue(progress) ?: 1f
            val sy = v?.getOrNull(1) ?: sx
            scale(sx, sy, pivot = center)
        }
        // <animate attributeName="x|cx"> — translate by the delta from the baseline.
        anim.attributeName.lowercase() in TRANSLATE_X_ATTRS -> {
            val cur = anim.getInterpolatedValue(progress)
            val base = anim.from
            if (cur != null && base != null) translate((cur - base) * scale, 0f)
        }
        // <animate attributeName="y|cy"> — translate by the delta from the baseline.
        anim.attributeName.lowercase() in TRANSLATE_Y_ATTRS -> {
            val cur = anim.getInterpolatedValue(progress)
            val base = anim.from
            if (cur != null && base != null) translate(0f, (cur - base) * scale)
        }
        else -> Unit // Unsupported attribute: draw in place.
    }
}

private fun treeHasAnimations(element: OGSVGTreeElement): Boolean =
    element.animations.isNotEmpty() || element.children.any { treeHasAnimations(it) }
