package com.solidkey.painpoints.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import com.solidkey.painpoints.ai.OGPlacedShapeSpec
import com.solidkey.painpoints.ai.OGSceneSpec
import com.solidkey.painpoints.ai.animatedAt
import com.solidkey.painpoints.ai.expanded
import com.solidkey.painpoints.ai.isLive
import com.solidkey.painpoints.ai.toSpecColor
import com.solidkey.painpoints.fx.OGLightningView
import com.solidkey.painpoints.fx.OGSlashView
import com.solidkey.painpoints.fx.OGSpeedLinesView
import com.solidkey.painpoints.look.OGBlendMode
import com.solidkey.painpoints.look.OGLookSpec
import com.solidkey.painpoints.look.ogLook
import com.solidkey.painpoints.particle.OGParticleView
import kotlinx.coroutines.isActive

/**
 * Live, on-screen player for an [OGStage] — the "play" half of the engine. It runs the stage's
 * timeline on Compose's frame clock (so it holds 60fps), fits the fixed [OGStage.width] ×
 * [OGStage.height] canvas into the available space (keeping its aspect), paints the [OGStage.background]
 * base coat, then draws each visible layer back-to-front: its content transformed by one shared
 * timeline clock and graded by its [OGLookSpec] / [OGBlendMode].
 *
 * Two modes, mirroring [com.solidkey.painpoints.compositor.OGCompositionView]:
 * - **Playing** (default, [positionMs] `= null`): advances on its own, looping when [loop] is true,
 *   reporting the current time via [onProgress]. Toggle [isPlaying] to pause/resume in place.
 * - **Controlled** ([positionMs] set): shows exactly that instant and ignores the internal clock —
 *   wire a slider straight to it for a scrubber.
 *
 * Every layer's **transform** (the keyframe tracks) and every **scene** layer are closed-form
 * t-addressable, so they scrub exactly. A **particle** layer runs its own stateful, seeded simulation
 * and plays live; while controlled it freezes in place (the live preview does not retro-address it to an
 * arbitrary t — the off-screen exporter, [OGStage.exportGif], re-simulates it deterministically instead).
 */
@Composable
fun OGStageView(
    stage: OGStage,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    loop: Boolean = true,
    positionMs: Long? = null,
    onProgress: ((timeMs: Long) -> Unit)? = null,
) {
    var clockMs by remember(stage) { mutableStateOf(0L) }
    val controlled = positionMs != null

    LaunchedEffect(stage, isPlaying, loop, controlled) {
        if (controlled || !isPlaying) return@LaunchedEffect
        val duration = stage.durationMs.coerceAtLeast(1L)
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
    val livePlay = isPlaying && !controlled
    val bg = stage.background?.toSpecColor()

    Box(modifier, contentAlignment = Alignment.Center) {
        val stageBox = Modifier
            .fillMaxSize()
            .aspectRatio(stage.aspect)
            .clipToBounds()
            .then(if (bg != null) Modifier.background(bg) else Modifier)
        Box(stageBox) {
            for (layer in stage.layers) {
                if (!layer.isVisibleAt(timeMs, stage.durationMs)) continue
                key(layer.id) {
                    StageLayerBox(layer.resolveAt(timeMs), livePlay)
                }
            }
        }
    }
}

/** One resolved layer: transform via a [graphicsLayer], grade/blend via [ogLook], then its content. */
@Composable
private fun StageLayerBox(r: OGResolvedStageLayer, livePlay: Boolean) {
    // A scene's OWN live grade (its day->night) wins over the layer's static look; it changes with
    // time, so recompute per frame — ogLook caches the filter per spec instance, a cheap rebuild.
    val effLook: OGLookSpec? = when (r.content) {
        is OGStageContent.Scene -> r.sceneFrameAt()?.look ?: r.look
        else -> r.look
    }

    var m = Modifier
        .fillMaxSize()
        .graphicsLayer {
            translationX = r.x * size.width
            translationY = r.y * size.height
            scaleX = r.scale
            scaleY = r.scale
            rotationZ = r.rotationDeg
            alpha = r.opacity
            transformOrigin = TransformOrigin.Center
        }
    if (effLook != null || r.blend != OGBlendMode.NORMAL) {
        m = m.ogLook(effLook ?: OGLookSpec.Identity, r.blend.toBlendMode())
    }

    Box(m) {
        when (val c = r.content) {
            is OGStageContent.Solid -> {
                val col = c.color.toSpecColor()
                if (col != null) Box(Modifier.fillMaxSize().background(col))
            }

            is OGStageContent.Particles ->
                OGParticleView(c.particles, Modifier.fillMaxSize(), playing = livePlay)

            is OGStageContent.Slash ->
                OGSlashView(c.slash, Modifier.fillMaxSize(), playing = livePlay)

            is OGStageContent.Lightning ->
                OGLightningView(c.lightning, Modifier.fillMaxSize(), playing = livePlay)

            is OGStageContent.SpeedLines ->
                OGSpeedLinesView(c.speedLines, Modifier.fillMaxSize(), playing = livePlay)

            is OGStageContent.Scene ->
                SceneLayerCanvas(c.scene, r.localTimeMs, Modifier.fillMaxSize())

            is OGStageContent.Text ->
                TextLayerCanvas(c, r.localTimeMs, Modifier.fillMaxSize())
        }
    }
}

/**
 * Draw a styled-text layer centred in the box at its layer-local [timeMs]. The glyph vectorization +
 * style compile are cached ([OGStageTextResources]); only the cheap per-frame styling + fill runs each
 * frame, so a `boil` / `wave` style stays alive at 60fps. The layer's grade / blend / opacity are
 * applied by the caller ([StageLayerBox]'s `graphicsLayer` + `ogLook`), so this draws with identity —
 * the same split as [SceneLayerCanvas] (and matched by the exporter's shared draw path).
 */
@Composable
private fun TextLayerCanvas(c: OGStageContent.Text, timeMs: Long, modifier: Modifier) {
    val res = remember(c) { OGStageTextResources.of(c) }
    Canvas(modifier) {
        drawOGStageTextFrame(res, c.size, size.width, size.height, timeMs)
    }
}

/**
 * Draw a vector [scene] at its layer-local [timeMs]. The scene is sanitized + expanded ONCE (scatter
 * stamped into regions), and on each frame only the regions that actually move are re-evaluated with
 * [animatedAt] — the parse-once / lerp-per-frame budget. Fills + gradients + opacity + motion are
 * rendered; the scene's whole-frame grade is applied by the caller via [ogLook].
 */
@Composable
private fun SceneLayerCanvas(scene: OGSceneSpec, timeMs: Long, modifier: Modifier) {
    val expanded = remember(scene) { scene.sanitized().expanded() }
    val live = remember(scene) { scene.isLive }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val tSec = timeMs / 1000f
        val sceneBg = scene.motion?.backgroundAt(tSec) ?: scene.background?.toSpecColor()
        if (sceneBg != null) drawRect(sceneBg)
        val shapes = if (live) expanded.shapes.map { it.animatedAt(tSec) } else expanded.shapes
        val path = Path()
        for (s in shapes) drawRegion(path, s, w, h)
    }
}

/** Fill one region: build its polygon path in pixel space and paint it with its gradient or fill. */
private fun DrawScope.drawRegion(path: Path, s: OGPlacedShapeSpec, w: Float, h: Float) {
    val pts = s.points
    if (pts.size < 3) return
    path.reset()
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (i in pts.indices) {
        val px = pts[i].x * w
        val py = pts[i].y * h
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        if (px < minX) minX = px
        if (px > maxX) maxX = px
        if (py < minY) minY = py
        if (py > maxY) maxY = py
    }
    path.close()
    val alpha = (s.opacity ?: 1f).coerceIn(0f, 1f)
    val g = s.gradient?.sanitized()
    if (g != null && g.isRenderable) {
        val brush = g.toBrush(minX, minY, (maxX - minX).coerceAtLeast(1e-3f), (maxY - minY).coerceAtLeast(1e-3f))
        drawPath(path, brush, alpha = alpha)
    } else {
        val col = s.fill?.toSpecColor() ?: Color.Transparent
        if (col.alpha > 0f) drawPath(path, col, alpha = alpha)
    }
}
