package com.solidkey.painpoints.look

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * **Grade any graphic with a colour [look].** Captures this composable's drawn content into a
 * `GraphicsLayer` and redraws it through the look's cached [ColorFilter][OGLookSpec.colorFilter], so it
 * works on *any* content — a photo, a GIF, an SVG, a lasso-cut cut-out, a `Canvas` drawing — with no
 * per-content code. The whole grade is one GPU colour op: zero per-frame allocation (the filter is
 * compiled once per [spec]), 60fps, and pixel-identical on Android & iOS.
 *
 * Pass [blend] to composite the graded result over what is behind it with a non-default blend (e.g.
 * [BlendMode.Multiply] / [BlendMode.Screen]); the default is ordinary source-over.
 *
 * Note: this grades drawn graphics (photos / GIFs / shapes / `Canvas`). The live **video** surface is
 * a separate platform view, so a `ColorFilter` over it is not reliable — the same limit as the soft
 * masks and blur modifiers. An [OGLookSpec.isIdentity] look short-circuits to a plain draw.
 */
fun Modifier.ogLook(spec: OGLookSpec, blend: BlendMode = BlendMode.SrcOver): Modifier = composed {
    val layer = rememberGraphicsLayer()
    drawWithContent {
        if (spec.isIdentity && blend == BlendMode.SrcOver) {
            drawContent()
            return@drawWithContent
        }
        layer.record { this@drawWithContent.drawContent() }
        // colorFilter / blendMode are RenderNode props read at rasterize time; we set them every draw
        // to the same cached values and draw the layer exactly once, so they always take effect.
        layer.colorFilter = spec.colorFilter
        layer.blendMode = blend
        drawLayer(layer)
    }
}
