package com.solidkey.painpoints.stage

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import com.solidkey.painpoints.ai.toSpecColor
import com.solidkey.painpoints.motion.OGClothStrip
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The single, pure per-frame draw of an [OGStageContent.Cloth] layer — used by BOTH the live
 * [OGStageView] and the off-screen [OGStageExport], so a preview frame and an exported frame are
 * byte-identical. It **deterministically re-simulates** the Verlet strip from `0` to the layer-local
 * time at the fixed internal substep (the strip has no closed form), then paints it as a tapered ribbon.
 *
 * Cost is O(timeSec) per call — fine for a short stage reel at a handful of nodes; it is a per-frame
 * snapshot, not a long-running loop (same contract as the particle/FX export path).
 */
internal fun DrawScope.drawOGStageClothFrame(
    c: OGStageContent.Cloth,
    localTimeSec: Float,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    blendMode: BlendMode = BlendMode.SrcOver,
) {
    val n = (c.cloth.nodes).coerceAtLeast(2)
    val strip = OGClothStrip(c.cloth)
    val h = 1f / 120f
    strip.reset(c.anchorX, c.anchorY)
    var t = 0f
    // Re-sim to the target time. step() accumulates dt and runs whole substeps; feeding exactly one
    // substep per call keeps it deterministic (same inputs → same frame on Android & iOS).
    while (t < localTimeSec) {
        strip.step(h, c.anchorX, c.anchorY, c.tailX, c.tailY)
        t += h
    }

    val w = size.width
    val hgt = size.height
    val short = min(w, hgt)
    val half = (c.width * short).coerceAtLeast(0.5f)
    val col = c.color.toSpecColor() ?: Color.White

    // Node centres in pixels.
    val xs = FloatArray(n) { strip.x(it) * w }
    val ys = FloatArray(n) { strip.y(it) * hgt }

    // Walk the strip, offsetting each node by ±halfWidth along the local normal to make a ribbon; the
    // free end tapers to a point when requested (a hair lock / pennant), stays full for a strap/banner.
    val path = Path()
    // Forward pass along the LEFT edge.
    for (i in 0 until n) {
        val (nx, ny) = normalAt(xs, ys, i, n)
        val hw = half * widthScale(i, n, c.taper)
        val px = xs[i] + nx * hw
        val py = ys[i] + ny * hw
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    // Back pass along the RIGHT edge.
    for (i in n - 1 downTo 0) {
        val (nx, ny) = normalAt(xs, ys, i, n)
        val hw = half * widthScale(i, n, c.taper)
        path.lineTo(xs[i] - nx * hw, ys[i] - ny * hw)
    }
    path.close()
    drawPath(path, col, alpha = alpha.coerceIn(0f, 1f), style = Fill, colorFilter = colorFilter, blendMode = blendMode)
}

/** Half-width multiplier along the strip: full at the pinned head, tapering to ~0 at the free end. */
private fun widthScale(i: Int, n: Int, taper: Boolean): Float {
    if (!taper || n <= 1) return 1f
    val f = i.toFloat() / (n - 1)
    // sqrt keeps the ribbon full most of its length and narrows only near the tip (natural for hair/cloth).
    return sqrt((1f - f).coerceIn(0f, 1f))
}

/** Unit normal at node [i] (perpendicular to the local tangent); falls back to vertical for a degenerate run. */
private fun normalAt(xs: FloatArray, ys: FloatArray, i: Int, n: Int): Pair<Float, Float> {
    val a = if (i > 0) i - 1 else i
    val b = if (i < n - 1) i + 1 else i
    var tx = xs[b] - xs[a]
    var ty = ys[b] - ys[a]
    val len = sqrt(tx * tx + ty * ty)
    if (len < 1e-4f) { tx = 0f; ty = 1f } else { tx /= len; ty /= len }
    // Normal = tangent rotated 90°.
    return -ty to tx
}
