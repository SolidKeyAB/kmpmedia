package com.solidkey.painpoints.stage

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.solidkey.painpoints.ai.toSpecColor
import com.solidkey.painpoints.style.OGStyle
import com.solidkey.painpoints.text.OGTextFont
import com.solidkey.painpoints.text.OGTextOutline
import com.solidkey.painpoints.text.ogVectorizeText

// ============================================================================================
// OGStage TEXT — the shared draw path for a [OGStageContent.Text] layer, so the live [OGStageView]
// and the off-screen exporter ([OGStage.renderFrame]) draw a title identically (exactly how the
// action-FX layers share their per-frame `drawOG{Slash,Lightning,SpeedLines}Frame`). The expensive
// glyph vectorization + style compile happen ONCE in [OGStageTextResources]; [drawOGStageTextFrame]
// is the cheap per-frame styling + fill.
// ============================================================================================

/** Samples/em used when vectorizing a stage text layer's glyphs (curve smoothness vs cost). */
private const val STAGE_TEXT_QUALITY = 72

/**
 * The vectorize-once resources behind a [OGStageContent.Text] layer: its glyphs turned into em-unit
 * [outline] contours, its optional [style] compiled to a live [OGStyle], and its [color] parsed. Build
 * it once per layer (the player caches it with `remember`; the exporter rebuilds it per rendered frame,
 * acceptably — see [OGStageExport]) and hand it to [drawOGStageTextFrame] each frame.
 */
internal class OGStageTextResources(
    val outline: OGTextOutline,
    val style: OGStyle?,
    val color: Color,
) {
    companion object {
        fun of(c: OGStageContent.Text): OGStageTextResources = OGStageTextResources(
            outline = ogVectorizeText(c.text, OGTextFont(c.bold, c.italic, c.family), STAGE_TEXT_QUALITY),
            style = c.style?.takeIf { it.ops.isNotEmpty() }?.toStyle(),
            color = c.color.toSpecColor() ?: Color.White,
        )
    }
}

/**
 * Draw a stage text layer's glyphs at layer-local [timeMs], centred in a [boxW] × [boxH] box with em
 * height `emHeightFraction * boxH` (so it is resolution-independent). The SINGLE code path the live
 * [OGStageView] and the exporter both call.
 *
 * The player passes identity [alpha] / [cf] / [blend] (its `graphicsLayer` + `ogLook` grade the whole
 * layer); the exporter passes the resolved layer opacity / look / blend so the baked pixels composite
 * the same. Glyph outlines run through [OGStageTextResources.style] each frame — cheap offset/lerp per
 * vertex, so a `boil` / `wave` style stays alive at 60fps; a terminal `pixelate` op is ignored (its
 * pre-pixelate outline is filled), matching [com.solidkey.painpoints.text.OGStyledText].
 */
internal fun DrawScope.drawOGStageTextFrame(
    res: OGStageTextResources,
    emHeightFraction: Float,
    boxW: Float,
    boxH: Float,
    timeMs: Long,
    alpha: Float = 1f,
    cf: ColorFilter? = null,
    blend: BlendMode = DrawScope.DefaultBlendMode,
) {
    val outline = res.outline
    if (outline.contours.isEmpty() || boxW <= 0f || boxH <= 0f) return
    val emPx = emHeightFraction * boxH
    if (emPx <= 0f) return
    val textW = outline.widthEm * emPx
    val textH = outline.heightEm * emPx
    val left = (boxW - textW) / 2f
    val top = (boxH - textH) / 2f
    val path = Path()
    for (contour in outline.contours) {
        val pts = res.style?.apply(contour, timeMs)?.outline ?: contour
        if (pts.size < 2) continue
        path.moveTo(left + pts[0].x * emPx, top + (pts[0].y - outline.topEm) * emPx)
        for (i in 1 until pts.size) {
            path.lineTo(left + pts[i].x * emPx, top + (pts[i].y - outline.topEm) * emPx)
        }
        path.close()
    }
    drawPath(path, res.color, alpha = alpha, colorFilter = cf, blendMode = blend)
}
