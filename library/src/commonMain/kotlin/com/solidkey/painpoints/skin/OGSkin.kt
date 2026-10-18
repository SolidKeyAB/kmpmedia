package com.solidkey.painpoints.skin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Render an [OGSkinSpec] to ONE seamless, repeating tile ([ogSkinTile]) and fill a surface with it —
 * reptile scales, an animal hide / horse coat, or a woven fabric. The tile is a small cached
 * [ImageBitmap] that Compose repeats as an [ImageShader] brush, so a whole shape costs one bitmap + a
 * GPU tile (no per-pixel work, no shader source, byte-identical on Android & iOS).
 *
 * Usage: `Box(Modifier.size(w, h).ogSkin(spec, shape))` to fill a shape, or grab the
 * [androidx.compose.ui.graphics.Brush] with [rememberOGSkinBrush] and `drawPath(path, brush)` yourself.
 *
 * The pattern is **parametric**: [OGSkinSpec.density] thins it from a packed material into a sparse
 * scatter (the [OGSkinSpec.base] shows through), while [OGSkinSpec.sizeJitter] and
 * [OGSkinSpec.colorJitter] randomise each element's size and colour within a limited range. All three
 * are driven by the same per-cell hash as the base pattern, so the tile still repeats seamlessly.
 */

// ── Public API ──────────────────────────────────────────────────────────────────────────────────────

/** Fill this composable's background with [spec]'s repeating skin, clipped to [shape]. The tile size is
 *  derived from [OGSkinSpec.cell] × the shorter side, so the pattern scales with the surface. */
fun Modifier.ogSkin(spec: OGSkinSpec, shape: Shape = RectangleShape): Modifier =
    this.clip(shape).drawWithCache {
        val short = min(size.width, size.height)
        val tilePx = tilePxFor(spec, short)
        val brush = ShaderBrush(ImageShader(ogSkinTile(spec, tilePx), TileMode.Repeated, TileMode.Repeated))
        onDrawBehind { drawRect(brush) }
    }

/** A repeating [ShaderBrush] for [spec], its tile sized from [shortSidePx] (the surface's shorter side).
 *  Use it to `drawRect`/`drawPath` a procedural skin into your own `Canvas` / `DrawScope`. */
@Composable
fun rememberOGSkinBrush(spec: OGSkinSpec, shortSidePx: Float): ShaderBrush {
    val tilePx = tilePxFor(spec, shortSidePx)
    return remember(spec, tilePx) {
        ShaderBrush(ImageShader(ogSkinTile(spec, tilePx), TileMode.Repeated, TileMode.Repeated))
    }
}

/** Render one seamless tile of [spec] at [tilePx]×[tilePx]. The tile repeats with no seam (edge cells are
 *  wrapped). One-shot + allocation-heavy — call from `remember` / `drawWithCache`, never per frame. */
fun ogSkinTile(spec: OGSkinSpec, tilePx: Int): ImageBitmap {
    val px = tilePx.coerceIn(8, 1024)
    val s = spec.sanitized()
    val base = s.base.toSpecColor() ?: Color(0xFF4E9A4E)
    val pattern = s.pattern.toSpecColor() ?: lerp(base, Color.Black, 0.3f)
    val highlight = s.highlight?.toSpecColor() ?: lerp(base, Color.White, 0.4f)
    val accent = s.accent?.toSpecColor()

    val bitmap = ImageBitmap(px, px)
    val canvas = Canvas(bitmap)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(px.toFloat(), px.toFloat()),
    ) {
        when (s.skinKind) {
            OGSkinKind.REPTILE -> drawReptileTile(s, base, pattern, highlight)
            OGSkinKind.HIDE -> drawHideTile(s, base, pattern, highlight, accent)
            OGSkinKind.FABRIC -> drawFabricTile(s, base, pattern, highlight)
        }
    }
    return bitmap
}

/** The seamless tile size in px. One pattern "unit" is `cell × shortSide`; when the skin is sparse
 *  (density < 1) the tile spans [spreadOf] units so the scatter has a longer, less-obvious period while
 *  each element keeps the same on-screen size. */
private fun tilePxFor(spec: OGSkinSpec, shortSidePx: Float): Int =
    (shortSidePx * spec.cell.coerceIn(0.02f, 0.5f) * spreadOf(spec)).roundToInt().coerceIn(8, 1024)

/** How many pattern units the tile spans per edge: 1 when packed, more as coverage drops (capped at 3),
 *  so a sparse scatter repeats over a wider area instead of showing a tight grid. */
private fun spreadOf(spec: OGSkinSpec): Int =
    if (spec.density >= 0.999f) 1 else (1f / spec.density).roundToInt().coerceIn(1, 3)

/** Keep this cell's element? `true` always when packed; otherwise gate on its per-cell hash [h]. */
private fun kept(h: Float, density: Float): Boolean = density >= 0.999f || h <= density

/** Per-element size multiplier within a limited range (±~60 % at [jitter] = 1); `1` when [jitter] = 0. */
internal fun sizeMul(h: Float, jitter: Float): Float = 1f + (h - 0.5f) * 2f * 0.6f * jitter

/** Nudge [c] within a limited, in-palette range toward [warm] (lighter) or [cool] (darker) by [amt]. */
internal fun jitterColor(c: Color, warm: Color, cool: Color, h: Float, amt: Float): Color {
    if (amt <= 0f) return c
    val t = (h - 0.5f) * 2f // -1..1
    val reach = 0.33f * amt
    return if (t >= 0f) lerp(c, warm, t * reach) else lerp(c, cool, -t * reach)
}

// ── Generators ──────────────────────────────────────────────────────────────────────────────────────

/** Fish/reptile scales: a brick-laid grid of shaded scales, lower rows drawn first so the row above
 *  overlaps each scale's top — the classic scaled look. Edge rows/cols are wrapped so it tiles. */
private fun DrawScope.drawReptileTile(s: OGSkinSpec, base: Color, pattern: Color, highlight: Color) {
    val tile = size.width
    val spread = spreadOf(s)
    val sparse = spread > 1
    // Sparse: let the base show between scattered scales; packed: the classic dark inter-scale shadow.
    drawRect(if (sparse) base else lerp(base, pattern, 0.35f))
    val cols = 2 * spread
    val rows = 4 * spread
    val cw = tile / cols
    val rh = tile / rows
    // Packed scales are oversized so they overlap into armour; a sparse scatter inscribes them inside
    // their cell so the surviving scales read as distinct speckles with the base showing between.
    val rx0 = if (sparse) cw * 0.44f else cw * 0.66f
    val ry0 = if (sparse) rh * 0.60f else rh * 0.95f
    val stroke = max(1f, cw * 0.024f)
    val rim = lerp(pattern, Color.Black, 0.18f)
    // bottom → top so the row above overlaps the top of the row below
    for (r in rows downTo -1) {
        val odd = (((r % 2) + 2) % 2) == 1
        val brick = if (odd) cw * 0.5f else 0f
        for (c in -1..cols) {
            val mx = ((c % cols) + cols) % cols
            val my = ((r % rows) + rows) % rows
            if (!kept(hash2(mx, my, s.seed + 211), s.density)) continue
            val j = hash2(mx, my, s.seed)
            val mul = sizeMul(hash2(mx, my, s.seed + 311), s.sizeJitter)
            val rx = rx0 * mul
            val ry = ry0 * mul
            val cx = c * cw + cw * 0.5f + brick
            val cy = r * rh + rh * 0.52f
            val k = (0.12f + s.contrast * 0.4f + (j - 0.5f) * s.roughness * 0.5f).coerceIn(0f, 1f)
            val body = jitterColor(lerp(base, pattern, k), highlight, pattern, hash2(mx, my, s.seed + 411), s.colorJitter)
            val top = lerp(body, highlight, (0.45f + s.relief * 0.4f).coerceIn(0f, 1f))
            val grad = Brush.radialGradient(
                colorStops = arrayOf(0f to top, 0.55f to body, 1f to lerp(body, rim, 0.85f)),
                center = Offset(cx, cy - ry * 0.4f),
                radius = max(rx, ry) * 1.25f,
            )
            val tl = Offset(cx - rx, cy - ry)
            val sz = Size(rx * 2f, ry * 2f)
            drawOval(grad, topLeft = tl, size = sz)
            drawOval(rim.copy(alpha = 0.45f), topLeft = tl, size = sz, style = Stroke(stroke))
        }
    }
}

/** An animal hide / horse coat: a soft noise-mottled base + a dense field of fine directional hairs,
 *  plus optional dapple spots. All placed on hash-indexed wrapped grids so it tiles seamlessly. */
private fun DrawScope.drawHideTile(
    s: OGSkinSpec,
    base: Color,
    pattern: Color,
    highlight: Color,
    accent: Color?,
) {
    val tile = size.width
    val spread = spreadOf(s)
    drawRect(base)
    // Coat mottle — soft overlapping patches, warmer (highlight) or cooler (pattern) by noise.
    val mn = 5 * spread
    val mc = tile / mn
    for (gy in -1..mn) for (gx in -1..mn) {
        val mx = ((gx % mn) + mn) % mn
        val my = ((gy % mn) + mn) % mn
        if (!kept(hash2(mx, my, s.seed + 211), s.density)) continue
        val j = hash2(mx, my, s.seed)
        val j2 = hash2(mx, my, s.seed + 101)
        val cx = (gx + 0.5f + (j - 0.5f) * 0.6f) * mc
        val cy = (gy + 0.5f + (j2 - 0.5f) * 0.6f) * mc
        val rad = mc * (0.75f + j * 0.6f) * sizeMul(hash2(mx, my, s.seed + 311), s.sizeJitter)
        val tgt = jitterColor(if (j2 > 0.5f) highlight else pattern, highlight, pattern, hash2(mx, my, s.seed + 411), s.colorJitter)
        val a = ((0.08f + s.contrast * 0.22f) * (0.4f + j)).coerceIn(0f, 0.6f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to tgt.copy(alpha = a), 1f to tgt.copy(alpha = 0f)),
                center = Offset(cx, cy),
                radius = rad,
            ),
            radius = rad,
            center = Offset(cx, cy),
        )
    }
    // Fine hairs — short near-vertical strokes lying along the coat.
    val hn = 20 * spread
    val hc = tile / hn
    val hw = max(1f, hc * 0.12f)
    val ang = (96f) * (PI.toFloat() / 180f)
    val dx = cos(ang); val dy = sin(ang)
    for (gy in -1..hn) for (gx in -1..hn) {
        val mx = ((gx % hn) + hn) % hn
        val my = ((gy % hn) + hn) % hn
        if (!kept(hash2(mx, my, s.seed + 217), s.density)) continue
        val j = hash2(mx, my, s.seed + 7)
        val j2 = hash2(mx, my, s.seed + 13)
        val cx = (gx + 0.5f + (j - 0.5f) * 0.8f) * hc
        val cy = (gy + 0.5f + (j2 - 0.5f) * 0.8f) * hc
        val len = hc * (1.0f + s.relief * 1.6f) * (0.6f + 0.7f * j) * sizeMul(hash2(mx, my, s.seed + 311), s.sizeJitter)
        val col = jitterColor(if (j2 > 0.5f) highlight else pattern, highlight, pattern, hash2(mx, my, s.seed + 411), s.colorJitter)
            .copy(alpha = (0.10f + 0.20f * s.contrast).coerceIn(0f, 0.5f))
        drawLine(
            color = col,
            start = Offset(cx - dx * len * 0.5f, cy - dy * len * 0.5f),
            end = Offset(cx + dx * len * 0.5f, cy + dy * len * 0.5f),
            strokeWidth = hw,
            cap = StrokeCap.Round,
        )
    }
    // Dapples (optional) — soft round spots.
    if (accent != null) {
        val dn = 3 * spread
        val dcell = tile / dn
        for (gy in -1..dn) for (gx in -1..dn) {
            val mx = ((gx % dn) + dn) % dn
            val my = ((gy % dn) + dn) % dn
            val j = hash2(mx, my, s.seed + 31)
            if (j < 0.5f) continue
            if (!kept(hash2(mx, my, s.seed + 211), s.density)) continue
            val cx = (gx + 0.5f + (hash2(mx, my, s.seed + 41) - 0.5f) * 0.5f) * dcell
            val cy = (gy + 0.5f + (hash2(mx, my, s.seed + 43) - 0.5f) * 0.5f) * dcell
            val rad = dcell * (0.26f + 0.18f * j) * sizeMul(hash2(mx, my, s.seed + 311), s.sizeJitter)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(0f to accent.copy(alpha = 0.38f), 0.7f to accent.copy(alpha = 0.22f), 1f to accent.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = rad,
                ),
                radius = rad,
                center = Offset(cx, cy),
            )
        }
    }
}

/** A plain-weave fabric: warp (vertical) + weft (horizontal) threads crossing over/under per cell, each
 *  a rounded bar with a cross-width sheen. A 2×2 crossing tile repeats seamlessly. */
private fun DrawScope.drawFabricTile(s: OGSkinSpec, base: Color, pattern: Color, highlight: Color) {
    val tile = size.width
    val spread = spreadOf(s)
    drawRect(lerp(base, pattern, 0.55f)) // the recessed gap/shadow between threads
    val cells = 2 * spread
    val cp = tile / cells
    val baseTw = cp * (0.58f + s.relief * 0.32f)
    val ext = cp * 0.12f // extend a thread segment into its neighbours so crossings read continuous
    // warp = base, weft = a touch lighter so the weave direction reads (e.g. denim's indigo warp / pale weft)
    val warpCol = base
    val weftCol = lerp(base, highlight, (0.25f + s.contrast * 0.4f).coerceIn(0f, 1f))
    // per-cell hash on wrapped coords so a cell and its wrapped copy agree → seamless
    fun ch(i: Int, j: Int, salt: Int) = hash2(((i % cells) + cells) % cells, ((j % cells) + cells) % cells, salt)

    fun DrawScope.warpSeg(i: Int, j: Int) {
        val cx = i * cp + cp * 0.5f
        // thread thickness jitters per column (whole thread, not per crossing) within a limited range
        val tw = baseTw * sizeMul(ch(i, 0, s.seed + 311), s.sizeJitter)
        val r = CornerRadius(tw * 0.5f, tw * 0.5f)
        val tint0 = lerp(warpCol, pattern, (ch(i, j, s.seed) - 0.5f).coerceIn(-0.5f, 0.5f) * s.roughness * 0.6f + 0.05f)
        val tint = jitterColor(tint0, highlight, pattern, ch(i, j, s.seed + 411), s.colorJitter)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(0f to lerp(tint, Color.Black, 0.18f), 0.5f to lerp(tint, highlight, 0.25f), 1f to lerp(tint, Color.Black, 0.18f)),
                startX = cx - tw / 2f, endX = cx + tw / 2f,
            ),
            topLeft = Offset(cx - tw / 2f, j * cp - ext),
            size = Size(tw, cp + ext * 2f),
            cornerRadius = r,
        )
    }
    fun DrawScope.weftSeg(i: Int, j: Int) {
        val cy = j * cp + cp * 0.5f
        val tw = baseTw * sizeMul(ch(0, j, s.seed + 313), s.sizeJitter)
        val r = CornerRadius(tw * 0.5f, tw * 0.5f)
        val col = jitterColor(weftCol, highlight, pattern, ch(i, j, s.seed + 413), s.colorJitter)
        drawRoundRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(0f to lerp(col, Color.Black, 0.16f), 0.5f to lerp(col, Color.White, 0.22f), 1f to lerp(col, Color.Black, 0.16f)),
                startY = cy - tw / 2f, endY = cy + tw / 2f,
            ),
            topLeft = Offset(i * cp - ext, cy - tw / 2f),
            size = Size(cp + ext * 2f, tw),
            cornerRadius = r,
        )
    }
    // Draw the full ring (-1..cells) so edge crossings wrap; per cell, the "under" thread then the "over".
    // A sparse weave drops whole crossings (density gate) → a loose, open cloth with gaps showing through.
    for (j in -1..cells) for (i in -1..cells) {
        if (!kept(ch(i, j, s.seed + 211), s.density)) continue
        val warpOver = ((i + j) % 2 + 2) % 2 == 0
        if (warpOver) { weftSeg(i, j); warpSeg(i, j) } else { warpSeg(i, j); weftSeg(i, j) }
    }
}

// ── Seamless per-cell hash (same value for a cell and its wrapped copies) ─────────────────────────────

/** Deterministic `0..1` hash of an integer cell, same on every platform. Index cells by `coord mod n`
 *  so a cell and its wrapped neighbour (coord and coord±n) hash identically → the tile has no seam. */
internal fun hash2(x: Int, y: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + seed * 1274126177
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0x7fffffff).toFloat() / 0x7fffffff.toFloat()
}
