package com.solidkey.painpoints.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.solidkey.painpoints.ai.OGPlacedShapeSpec
import com.solidkey.painpoints.ai.OGSceneSpec
import com.solidkey.painpoints.ai.liveAt
import com.solidkey.painpoints.ai.toSpecColor
import com.solidkey.painpoints.compositor.OGGifDither
import com.solidkey.painpoints.compositor.OGGifEncoder
import com.solidkey.painpoints.compositor.OGMp4Encoder
import com.solidkey.painpoints.compositor.cropFlattenFrame
import com.solidkey.painpoints.compositor.defaultMp4BitRate
import com.solidkey.painpoints.compositor.evenDown
import com.solidkey.painpoints.compositor.toArgbPixels
import com.solidkey.painpoints.fx.OGLightningSpec
import com.solidkey.painpoints.fx.OGSlashSpec
import com.solidkey.painpoints.fx.OGSpeedLinesSpec
import com.solidkey.painpoints.fx.drawOGLightningFrame
import com.solidkey.painpoints.fx.drawOGSlashFrame
import com.solidkey.painpoints.fx.drawOGSpeedLinesFrame
import com.solidkey.painpoints.fx.slashCenterline
import com.solidkey.painpoints.fx.speedLineParams
import com.solidkey.painpoints.particle.OGParticleSpec
import com.solidkey.painpoints.particle.unitOutline

// ============================================================================================
// OGStage EXPORT — the end-to-end loop that completes the unique path: a movie you GENERATE (as
// JSON) and PLAY ([OGStageView]) can now be RENDERED OFF-SCREEN and ENCODED to a shareable file,
// all on device. This reuses the compositor's pure-Kotlin [OGGifEncoder] and the OS video encoder
// [OGMp4Encoder] verbatim, and rasterises each stage frame with the same CanvasDrawScope path the
// compositor uses — so a frame looks the same on Android and iOS.
//
// Coverage: EVERY layer kind exports. Solid, scene (fills/gradients/motion/grade), styled text
// (vectorized glyphs, styled per frame) and particle layers (now with their real silhouette —
// circle/square/triangle/star/petal/teardrop, spun) draw directly through the same code path the
// live views use; the action-FX layers (slash/lightning/speedlines) draw through the SAME pure per-frame
// functions the live views use ([drawOGSlashFrame]/[drawOGLightningFrame]/[drawOGSpeedLinesFrame]),
// so the exported frame matches the preview. An FX layer composites through an offscreen group
// ([fxGroup]) so its internal additive (Plus) bloom accumulates inside the layer first, then the
// layer's own opacity / look / blend apply on top — exactly the graphicsLayer the player wraps it in.
//
// Perf: [renderFrame] is a one-shot snapshot (allocates a bitmap, re-simulates a particle layer from
// 0 to t, re-samples an FX centreline) — meant for export/snapshot, NOT a 60fps loop. Export a
// down-fps / down-sized `copy()` of the stage for a quick share.
// ============================================================================================

/**
 * Draw [stage] at absolute [timeMs] into the current [DrawScope], in the stage's pixel space
 * (top-left origin, `width` × `height`). The off-screen twin of [OGStageView]; pure Compose drawing,
 * no platform types. Every layer kind bakes in (see file header).
 */
fun DrawScope.drawStage(stage: OGStage, timeMs: Long) {
    stage.background?.toSpecColor()?.let { drawRect(it) }
    for (layer in stage.resolveAt(timeMs)) drawStageLayerExport(layer, stage.fps)
}

/** One resolved layer, transformed + painted with its opacity / look / blend. */
private fun DrawScope.drawStageLayerExport(r: OGResolvedStageLayer, fps: Int) {
    if (r.opacity <= 0f) return
    val w = size.width
    val h = size.height
    val pivot = Offset(w / 2f, h / 2f)
    withTransform({
        translate(r.x * w, r.y * h)
        if (r.rotationDeg != 0f) rotate(r.rotationDeg, pivot)
        if (r.scale != 1f) scale(r.scale, r.scale, pivot)
    }) {
        when (val c = r.content) {
            is OGStageContent.Solid -> {
                val col = c.color.toSpecColor()
                if (col != null) drawRect(
                    color = col, alpha = r.opacity,
                    colorFilter = r.look.cf(), blendMode = r.blend.toBlendMode(),
                )
            }

            is OGStageContent.Scene -> drawExportScene(c.scene, r, w, h)

            is OGStageContent.Text -> drawExportText(c, r)

            is OGStageContent.Particles -> drawExportParticles(c.particles, r, fps, w, h)

            // Action-FX draw through the SAME pure per-frame functions the live views use, inside an
            // offscreen group so their internal additive bloom composites like the player's graphicsLayer.
            is OGStageContent.Slash -> fxGroup(r, w, h) { drawExportSlash(c.slash, r.localTimeMs) }
            is OGStageContent.Lightning -> fxGroup(r, w, h) { drawExportLightning(c.lightning, r.localTimeMs) }
            is OGStageContent.SpeedLines -> fxGroup(r, w, h) { drawExportSpeedLines(c.speedLines, r.localTimeMs) }
        }
    }
}

/**
 * Composite [body] (an action-FX layer's drawing) through an offscreen group carrying the layer's
 * opacity / look / blend — the off-screen twin of the `graphicsLayer { } + ogLook` the player wraps an
 * FX view in. Needed because the FX draw their glow with additive (`BlendMode.Plus`) passes: those must
 * accumulate inside a transparent layer first and only THEN composite onto the scene with the layer's
 * own blend, or a `normal`-blended FX would wrongly brighten the pixels behind it.
 */
private inline fun DrawScope.fxGroup(r: OGResolvedStageLayer, w: Float, h: Float, body: DrawScope.() -> Unit) {
    val canvas = drawContext.canvas
    val paint = Paint().apply {
        blendMode = r.blend.toBlendMode()
        alpha = r.opacity
        r.look.cf()?.let { colorFilter = it }
    }
    canvas.saveLayer(Rect(0f, 0f, w, h), paint)
    body()
    canvas.restore()
}

/** A slash ribbon at its layer-local time; colours resolved exactly as [OGSlashView] remembers them. */
private fun DrawScope.drawExportSlash(spec: OGSlashSpec, localTimeMs: Long) {
    val head = spec.colorStart.toSpecColor() ?: Color.White
    val tail = spec.colorEnd.toSpecColor() ?: head
    val glow = spec.glowColor?.toSpecColor() ?: head
    drawOGSlashFrame(spec, localTimeMs.toFloat(), slashCenterline(spec), head, tail, glow)
}

/** A forked bolt at its layer-local time; colours resolved exactly as [OGLightningView] remembers them. */
private fun DrawScope.drawExportLightning(spec: OGLightningSpec, localTimeMs: Long) {
    val color = spec.color.toSpecColor() ?: Color.White
    val core = spec.coreColor.toSpecColor() ?: Color.White
    val glow = spec.glowColor?.toSpecColor() ?: color
    drawOGLightningFrame(spec, localTimeMs.toFloat(), color, core, glow)
}

/** Speed / impact lines at the layer-local time; colour + line field resolved as [OGSpeedLinesView] does. */
private fun DrawScope.drawExportSpeedLines(spec: OGSpeedLinesSpec, localTimeMs: Long) {
    val color = spec.color.toSpecColor() ?: Color.Black
    drawOGSpeedLinesFrame(spec, localTimeMs.toFloat(), color, speedLineParams(spec))
}

/** Bake a styled-text layer at its local time through the SAME [drawOGStageTextFrame] the player uses,
 *  applying the layer's opacity / look / blend so the exported glyphs composite like the preview. The
 *  glyphs are re-vectorized per rendered frame (cheaper than the particle re-sim this file already
 *  does, and the export is a one-shot anyway). */
private fun DrawScope.drawExportText(c: OGStageContent.Text, r: OGResolvedStageLayer) {
    drawOGStageTextFrame(
        res = OGStageTextResources.of(c),
        emHeightFraction = c.size,
        boxW = size.width,
        boxH = size.height,
        timeMs = r.localTimeMs,
        alpha = r.opacity,
        cf = r.look.cf(),
        blend = r.blend.toBlendMode(),
    )
}

/** Draw a scene layer's live frame at its local time: base coat + each region, graded by the scene's
 *  own motion look (falling back to the layer look). */
private fun DrawScope.drawExportScene(scene: OGSceneSpec, r: OGResolvedStageLayer, w: Float, h: Float) {
    val frame = scene.liveAt(r.localTimeMs / 1000f)
    val cf = (frame.look ?: r.look).cf()
    val bm = r.blend.toBlendMode()
    frame.background?.let { drawRect(color = it, alpha = r.opacity, colorFilter = cf, blendMode = bm) }
    val path = Path()
    for (s in frame.scene.shapes) drawExportRegion(path, s, w, h, r.opacity, cf, bm)
}

/** Fill one region (mirrors [OGStageView]'s live draw, plus the export's look/blend). */
private fun DrawScope.drawExportRegion(
    path: Path,
    s: OGPlacedShapeSpec,
    w: Float,
    h: Float,
    layerAlpha: Float,
    cf: ColorFilter?,
    bm: BlendMode,
) {
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
    val alpha = (s.opacity ?: 1f).coerceIn(0f, 1f) * layerAlpha
    val g = s.gradient?.sanitized()
    if (g != null && g.isRenderable) {
        val brush = g.toBrush(minX, minY, (maxX - minX).coerceAtLeast(1e-3f), (maxY - minY).coerceAtLeast(1e-3f))
        drawPath(path, brush = brush, alpha = alpha, colorFilter = cf, blendMode = bm)
    } else {
        val col = s.fill?.toSpecColor() ?: return
        drawPath(path, color = col, alpha = alpha, colorFilter = cf, blendMode = bm)
    }
}

/** Draw a deterministic particle snapshot: re-simulate the seeded system from 0 to the layer's local
 *  time in frame-sized steps, then draw each live particle as its real shape — a disc for `circle`,
 *  else the shared [unitOutline] silhouette (square/triangle/star/petal/teardrop), spun by the
 *  particle's rotation, so the exported burst matches the on-screen [OGParticleView]. */
private fun DrawScope.drawExportParticles(spec: OGParticleSpec, r: OGResolvedStageLayer, fps: Int, w: Float, h: Float) {
    val system = spec.toSystem()
    val step = (1000f / fps.coerceAtLeast(1)).coerceIn(1f, 64f)
    val target = r.localTimeMs.coerceAtLeast(0L).toFloat()
    var t = 0f
    while (t < target) {
        val dt = minOf(step, target - t)
        system.update(dt)
        t += dt
    }
    val short = minOf(w, h)
    val cf = r.look.cf()
    val bm = r.blend.toBlendMode()
    val outline = unitOutline(system.shape)
    val path = if (outline != null) Path() else null
    for (i in 0 until system.count) {
        val radius = system.size[i] * short
        if (radius <= 0f) continue
        val color = Color(system.argb[i])
        val cx = system.x[i] * w
        val cy = system.y[i] * h
        if (outline == null || path == null) {
            drawCircle(color, radius, Offset(cx, cy), alpha = r.opacity, colorFilter = cf, blendMode = bm)
        } else {
            path.reset()
            path.moveTo(cx + outline[0].x * radius, cy + outline[0].y * radius)
            for (k in 1 until outline.size) path.lineTo(cx + outline[k].x * radius, cy + outline[k].y * radius)
            path.close()
            rotate(system.rotationDeg[i], pivot = Offset(cx, cy)) {
                drawPath(path, color, alpha = r.opacity, colorFilter = cf, blendMode = bm)
            }
        }
    }
}

/** The look's cached [ColorFilter], or `null` when the look is absent or a pass-through. */
private fun com.solidkey.painpoints.look.OGLookSpec?.cf(): ColorFilter? =
    this?.takeIf { !it.isIdentity }?.colorFilter

/**
 * Render a single frame of the stage at absolute [timeMs] to an offscreen [ImageBitmap] the size of
 * the stage. Allocates a bitmap (and re-simulates particles from 0), so it is for export/snapshot —
 * use [OGStageView] for a live 60fps preview.
 */
fun OGStage.renderFrame(timeMs: Long): ImageBitmap {
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    val bitmap = ImageBitmap(w, h)
    val canvas = Canvas(bitmap)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(w.toFloat(), h.toFloat()),
    ) {
        drawStage(this@renderFrame, timeMs)
    }
    return bitmap
}

/**
 * Render this stage to a shareable **animated GIF** (`0xAARRGGBB` frames → GIF89a bytes) — the
 * generate→play→**export** loop, on device, via the pure-Kotlin [OGGifEncoder] (identical bytes on
 * Android & iOS). One bitmap per frame, so this is a one-shot, allocation-heavy call — run it off the
 * main thread, and prefer a down-fps / down-sized `copy()` of the stage for a quick share.
 *
 * @param loopCount `0` = loop forever, `n` = play `n` times then stop.
 * @param dither palette mapping; [OGGifDither.FLOYD_STEINBERG] (default) kills gradient banding.
 */
fun OGStage.exportGif(
    loopCount: Int = 0,
    alphaThreshold: Int = OGGifEncoder.DEFAULT_ALPHA_THRESHOLD,
    dither: OGGifDither = OGGifDither.FLOYD_STEINBERG,
): ByteArray {
    // Stream frames through the encoder so a full-size stage GIF can't OOM by retaining every frame's ARGB
    // (a default 1080×1080×60fps×4s stage is ~1.1GB of pixels). Byte-identical to the materialized-list path.
    return OGGifEncoder.encodeStreaming(width, height, frameCount, frameDelayCs, loopCount, alphaThreshold, dither) { i ->
        renderFrame(frameTimeMs(i)).toArgbPixels()
    }
}

/**
 * Render this stage to a shareable **H.264 MP4** via the OS encoder ([OGMp4Encoder]: Android
 * MediaCodec+MediaMuxer / iOS AVAssetWriter). Frames are flattened onto the opaque [background]
 * (MP4 has no alpha). One-shot and allocation-heavy; run off the main thread. H.264 needs even
 * dimensions, so an odd width/height loses its last row/column.
 */
fun OGStage.exportMp4(
    bitRate: Int = defaultMp4BitRate(width, height, fps),
    background: Color = Color.Black,
): ByteArray {
    val outW = evenDown(width)
    val outH = evenDown(height)
    val bgOpaque = background.toArgb() or (0xFF shl 24)
    val encoder = OGMp4Encoder(outW, outH, maxOf(1, fps), bitRate)
    try {
        for (i in 0 until frameCount) {
            val pixels = renderFrame(frameTimeMs(i)).toArgbPixels()
            encoder.encodeFrame(cropFlattenFrame(pixels, width, height, outW, outH, bgOpaque))
        }
        return encoder.finish()
    } catch (t: Throwable) {
        encoder.abort()
        throw t
    }
}
