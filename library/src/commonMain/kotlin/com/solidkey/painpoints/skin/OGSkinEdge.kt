package com.solidkey.painpoints.skin

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import com.solidkey.painpoints.ai.toSpecColor
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * **Raised, 3-D-ish protuberances that rim a shape's edge** — the skin's scales/beads bulging out along
 * the silhouette so the creature (or image) reads as a dimensional, studded form rather than a flat
 * cut-out. Each bump is a convex bead lit from one direction (a bright spot on the lit side, a shaded
 * far side, and a soft contact shadow where it seats on the body) — pure gradients, no shader, no blur,
 * identical on Android & iOS.
 *
 * The bumps take their colour, size ([OGSkinSpec.cell]) and per-element randomness
 * ([OGSkinSpec.sizeJitter] / [OGSkinSpec.colorJitter]) from the same [OGSkinSpec] that fills the surface,
 * so the raised rim matches the skin. [OGSkinEdgeSpec] then tunes how far they protrude, how packed they
 * sit and where the light comes from.
 *
 * Fill a shape AND rim it:
 * `Box(Modifier.size(w, h).ogSkin(spec, shape).ogSkinEdge(spec, shape))`
 * or draw the rim onto an outline you already have inside a `Canvas` / `DrawScope`:
 * `drawOGSkinEdge(headPath, spec, OGSkinEdgeSpec(protrude = 0.7f))`.
 *
 * Note: the bumps bulge **outside** the outline, so leave a little room around the shape (padding / a
 * taller slot) or they clip at the composable's bounds.
 */

/** Tunes the raised edge: how proud the bumps stand, how densely they pack, and the light direction that
 *  makes them read as 3-D. Colours / bump size / jitter all come from the [OGSkinSpec] they rim. */
@Serializable
data class OGSkinEdgeSpec(
    /** How far each bump bulges past the edge, as a fraction of its own radius. `0` = flush, `~0.6`
     *  default, up to `1.5` for a tall spiky ridge. Higher = a more pronounced 3-D crest. */
    val protrude: Float = 0.6f,
    /** How packed the bumps sit along the edge, `0.1..1`. `1` = bumps touch (a continuous beaded rim);
     *  lower spaces them into a sparse studded edge. */
    val coverage: Float = 1f,
    /** Multiplies the skin-derived bump radius (`OGSkinSpec.cell × shorter-side ÷ 2`), `0.2..4`. Bigger
     *  bumps than the surface scales, or smaller studs. */
    val bumpScale: Float = 1f,
    /** Light direction in degrees (`0` = right, `-90` = straight up, `-120` ≈ upper-left). The lit side of
     *  every bump faces this; the far side falls into shadow — this is what reads as 3-D. */
    val lightAngleDeg: Float = -120f,
) {
    fun sanitized(): OGSkinEdgeSpec = copy(
        protrude = protrude.coerceIn(0f, 1.5f),
        coverage = coverage.coerceIn(0.1f, 1f),
        bumpScale = bumpScale.coerceIn(0.2f, 4f),
    )
}

// ── Public API ──────────────────────────────────────────────────────────────────────────────────────

/** Rim [shape]'s outline with [skin]'s raised 3-D protuberances, drawn **on top of** this composable's
 *  content. The bumps bulge outside the shape, so pad the composable or they clip at its bounds. */
fun Modifier.ogSkinEdge(skin: OGSkinSpec, shape: Shape = RectangleShape, edge: OGSkinEdgeSpec = OGSkinEdgeSpec()): Modifier =
    this.drawWithCache {
        val outline = when (val o = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Generic -> o.path
            is Outline.Rectangle -> Path().apply { addRect(o.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(o.roundRect) }
        }
        onDrawWithContent {
            drawContent()
            drawOGSkinEdge(outline, skin, edge)
        }
    }

/** Draw [skin]'s raised 3-D protuberances around [outline] (a closed silhouette in this scope's pixel
 *  space). Call it after you've filled the shape — e.g. right after `drawPath(headPath, skinBrush)` —
 *  so the bumps sit on the rim. The bumps bulge outside the outline. */
fun DrawScope.drawOGSkinEdge(outline: Path, skin: OGSkinSpec, edge: OGSkinEdgeSpec = OGSkinEdgeSpec()) {
    val s = skin.sanitized()
    val e = edge.sanitized()
    val base = s.base.toSpecColor() ?: Color(0xFF4E9A4E)
    val pattern = s.pattern.toSpecColor() ?: lerp(base, Color.Black, 0.3f)
    val highlight = s.highlight?.toSpecColor() ?: lerp(base, Color.White, 0.4f)

    val short = min(size.width, size.height)
    val r0 = s.cell * short * 0.5f * e.bumpScale
    if (r0 < 0.5f) return

    val pm = PathMeasure().apply { setPath(outline, true) } // forceClosed: rim the whole silhouette
    val len = pm.length
    if (len <= 0f) return

    val bounds = outline.getBounds()
    val cx = bounds.center.x
    val cy = bounds.center.y
    val ang = e.lightAngleDeg * (PI.toFloat() / 180f)
    val light = Offset(cos(ang), sin(ang))

    // bases tile along the edge at a constant pitch so bumps sit shoulder-to-shoulder (a scalloped rim);
    // only the height varies, which keeps the valleys between bumps clean.
    val step = (2f * r0) / e.coverage
    val halfBase = step * 0.5f
    var d = halfBase
    var i = 0
    while (d < len) {
        val pos = pm.getPosition(d)
        val tan = pm.getTangent(d)
        val tl = hypot(tan.x, tan.y).coerceAtLeast(1e-4f)
        val tx = tan.x / tl
        val ty = tan.y / tl
        // outward normal = tangent rotated 90°, flipped to point away from the shape centre
        var nx = ty
        var ny = -tx
        if ((pos.x - cx) * nx + (pos.y - cy) * ny < 0f) { nx = -nx; ny = -ny }

        val mul = sizeMul(hash2(i, 0, s.seed + 311), s.sizeJitter)
        // Taper the bump where the silhouette turns sharply (a pointy tip / corner). Without this, a bump
        // seated at an apex projects its full height along the outward normal and visibly OVERSHOOTS the
        // point; shrinking the rise as the edge's turn across the bump's own footprint grows makes the
        // bumps hug a tip instead of spiking past it. (Smooth edges turn little → taper ≈ 1, unchanged.)
        val ta = pm.getTangent((d - halfBase).coerceAtLeast(0f))
        val tb = pm.getTangent((d + halfBase).coerceAtMost(len))
        val al = hypot(ta.x, ta.y).coerceAtLeast(1e-4f)
        val bl = hypot(tb.x, tb.y).coerceAtLeast(1e-4f)
        val turn = acos(((ta.x * tb.x + ta.y * tb.y) / (al * bl)).coerceIn(-1f, 1f)) // 0 straight → PI cusp
        // A sharp point (turn ≳ 72°) tapers to ZERO so the apex stays a clean bare tip — bumps that would
        // otherwise overshoot it just vanish — and both a left and a right tip read the same regardless of
        // where the bump tiling happens to land (one catching a bump + the other a gap looked asymmetric).
        val taper = (1f - turn / (PI.toFloat() * 0.4f)).coerceIn(0f, 1f)
        val h = r0 * (0.55f + e.protrude) * mul * taper // how far this bump rises out of the edge
        val k = (0.2f + s.contrast * 0.4f + (hash2(i, 0, s.seed) - 0.5f) * s.roughness * 0.5f).coerceIn(0f, 1f)
        val body = jitterColor(lerp(base, pattern, k), highlight, pattern, hash2(i, 0, s.seed + 411), s.colorJitter)
        drawEdgeBump(pos, tx, ty, nx, ny, halfBase, h, light, body, highlight)

        d += step
        i++
    }
}

// ── One raised bump of the skin edge ──────────────────────────────────────────────────────────────────

/** A single bump of the skin's edge — a rounded lobe whose flat base sits ON the contour (from
 *  `pos ± tangent·halfBase`) and whose top bulges OUT along the normal by [h]. It is filled with a
 *  directional light→body→shadow gradient (clipped to the lobe), so the lit face catches the light and
 *  the far face falls into shadow while the middle stays the skin's own colour — the lobe fuses with the
 *  body and the silhouette simply bulges. No circle, no outline: a bump on the skin, not a bead on it. */
private fun DrawScope.drawEdgeBump(
    pos: Offset,
    tx: Float,
    ty: Float,
    nx: Float,
    ny: Float,
    halfBase: Float,
    h: Float,
    light: Offset,
    body: Color,
    highlight: Color,
) {
    if (h <= 0.5f) return
    val baseL = Offset(pos.x - tx * halfBase, pos.y - ty * halfBase)
    val baseR = Offset(pos.x + tx * halfBase, pos.y + ty * halfBase)
    val apex = Offset(pos.x + nx * h, pos.y + ny * h)
    // control points pushed out along the normal give the lobe a rounded, shouldered top
    val cL = Offset(baseL.x + nx * h, baseL.y + ny * h)
    val cR = Offset(baseR.x + nx * h, baseR.y + ny * h)
    val lobe = Path().apply {
        moveTo(baseL.x, baseL.y)
        quadraticTo(cL.x, cL.y, apex.x, apex.y)
        quadraticTo(cR.x, cR.y, baseR.x, baseR.y)
        close()
    }
    val lit = lerp(highlight, Color.White, 0.18f)
    val shade = lerp(body, Color.Black, 0.30f)
    val span = halfBase + h
    clipPath(lobe) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(lit, body, shade),
                start = Offset(pos.x + light.x * span, pos.y + light.y * span),
                end = Offset(pos.x - light.x * span, pos.y - light.y * span),
            ),
        )
    }
}
