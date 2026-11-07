package com.solidkey.painpoints.puppet

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import com.solidkey.painpoints.image.loading.loadImageFromResource

/**
 * Plays a cut-out [OGPuppetSpec] as a live raster puppet: the still base with each jointed mover rotating
 * about its pivot, breathing, leaning and watching the [look] target — the whole-figure + per-part transforms
 * come from the deterministic [OGPuppetRig]. The host feeds a `look` (`-1..1`, or `null` for a gentle idle
 * glance) and an optional [pose] (its own game beat — a score pop, a tap, a reaction) folded on top.
 *
 * This painter overload takes the already-decoded [base] + [parts] painters (and an optional [headPainter] to
 * swap the head's sprite, e.g. an expression frame), so the host keeps control of loading / sprite-swapping.
 * The [OGPuppetView] spec overload below loads the sprites for you by resource name — the fully self-contained
 * "pure-lib" path for a puppet with no runtime sprite swaps.
 *
 * Size + aspect: the puppet fills [modifier]'s box; give it a box matching [OGPuppetSpec.aspect] so the base
 * frame isn't letterboxed (then every mover's pivot fraction lines up with the drawn pixels).
 */
@Composable
fun OGPuppetView(
    base: Painter?,
    parts: List<Painter?>,
    spec: OGPuppetSpec,
    modifier: Modifier = Modifier,
    look: Float? = null,
    pose: OGPuppetPose = OGPuppetPose.Idle,
    headPainter: Painter? = null,
    playing: Boolean = true,
) {
    var tSec by remember { mutableStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var start = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { nanos ->
                if (start == 0L) start = nanos
                tSec = (nanos - start) / 1_000_000_000f
            }
        }
    }

    val look01 = OGPuppetRig.effectiveLook(look, tSec)
    val breathe = OGPuppetRig.figureBreathe(spec, tSec)
    val float = OGPuppetRig.figureFloat(spec, tSec)
    val lean = OGPuppetRig.figureLean(spec, look01)
    val bend = OGPuppetRig.figureRotation(spec, tSec, look01)

    Box(
        modifier.graphicsLayer {
            // Grounded puppets pivot about their feet and never lift; flying ones float in place.
            transformOrigin = TransformOrigin(0.5f, if (spec.grounded) 1f else 0.5f)
            translationX = lean * size.width
            translationY = (float + pose.dy) * size.height
            scaleX = breathe * pose.scale
            scaleY = breathe * pose.scale
            rotationZ = bend + pose.rot
        },
    ) {
        // The still base (Fit: the frame fills the box, so each mover's pivot fraction lines up with the pixels).
        base?.let {
            Image(it, contentDescription = spec.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        spec.parts.forEachIndexed { i, part ->
            val painter = if (part.head && headPainter != null) headPainter else parts.getOrNull(i)
            painter?.let { p ->
                val angle = OGPuppetRig.partAngle(spec, part, tSec, look01)
                val sx = OGPuppetRig.partScaleX(spec, part, look01)
                val offX = OGPuppetRig.partOffsetX(part, look01)
                val offY = OGPuppetRig.partOffsetY(part)
                Image(
                    p, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        transformOrigin =
                            if (part.head) TransformOrigin(spec.headCenterX, spec.headCenterY)
                            else TransformOrigin(part.pivotX, part.pivotY)
                        rotationZ = angle
                        scaleX = sx
                        translationX = offX * size.width
                        translationY = offY * size.height
                    },
                )
            }
        }
    }
}

/**
 * The self-loading overload: give it a decoded [OGPuppetSpec] and it resolves the base + part sprites by
 * resource name (via the platform image loader) and plays the rig — the fully "pure-lib" path for a puppet
 * with no runtime sprite swaps. For a puppet that swaps a sprite at runtime (e.g. an expression head frame),
 * use the painter overload above and pass the current painters yourself.
 */
@Composable
fun OGPuppetView(
    spec: OGPuppetSpec,
    modifier: Modifier = Modifier,
    look: Float? = null,
    pose: OGPuppetPose = OGPuppetPose.Idle,
    playing: Boolean = true,
) {
    var base by remember(spec.base) { mutableStateOf<Painter?>(null) }
    loadImageFromResource(spec.base) { base = it }
    val parts = remember(spec) { mutableStateListOf<Painter?>().apply { repeat(spec.parts.size) { add(null) } } }
    spec.parts.forEachIndexed { i, part ->
        loadImageFromResource(part.res) { parts[i] = it }
    }
    OGPuppetView(
        base = base, parts = parts, spec = spec, modifier = modifier,
        look = look, pose = pose, headPainter = null, playing = playing,
    )
}
