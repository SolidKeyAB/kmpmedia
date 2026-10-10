package com.solidkey.painpoints.ai

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.solidkey.painpoints.fx.OGFxNoise
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================================
// v3 "richer stills": three optional, data-defined ways to paint a scene region beyond a flat
// fill — a procedural noise TEXTURE (fur/grass), an additive GLOW halo (sun, backlit rim), and
// a seeded SCATTER that stamps one region as a whole field (trees/tufts). Every generator is
// PURE (deterministic, no Compose), computed once at decode/author time — never per frame — so
// it stays off the 60fps path and is byte-for-byte identical on Android & iOS. Each is an
// optional field on [OGPlacedShapeSpec]; a scene without them decodes exactly as before, and an
// older library simply ignores the unknown keys.
// ============================================================================================

/** A tiny deterministic xorshift32 PRNG — pure integer maths, so its stream is identical on every
 *  platform (unlike `kotlin.random`, which is unspecified across targets). */
internal class OGFxRandom(seed: Int) {
    private var s: Int = if (seed == 0) 0x9E3779B7.toInt() else seed
    private fun next(): Int {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        s = x
        return x
    }
    /** Uniform `[0,1)`. */
    fun nextFloat(): Float = ((next() ushr 8) and 0xFFFFFF) / 16_777_216f
    /** Uniform `[a,b)`. */
    fun range(a: Float, b: Float): Float = a + (b - a) * nextFloat()
}

// --- Glow -----------------------------------------------------------------------------------

/**
 * An additive **glow** halo for a region — a sun's bloom, a backlit fur rim, a neon edge. Blur-free
 * and cross-platform-identical: a few outward-scaled, additive (`BlendMode.Plus`) copies of the
 * region's own path are drawn behind it, exactly the technique behind `Modifier.ogGlow`. Mapped to
 * pixels only at draw time; the fall-off plan ([glowPasses]) is pure maths.
 */
@Serializable
data class OGGlowSpec(
    /** Halo tint (`#RRGGBB` / name); `null` glows white. */
    val color: String? = null,
    /** How far the outermost copy expands (`1.0` = none, `1.4` = +40%). */
    val radius: Float = 1.4f,
    /** Halo opacity at the core `0..1`. */
    val intensity: Float = 0.6f,
    /** Number of stacked copies (softness); `1..8`. */
    val passes: Int = 4,
) {
    /** Clamp knobs into their valid ranges. */
    fun sanitized(): OGGlowSpec = copy(
        radius = radius.coerceAtLeast(1f),
        intensity = intensity.coerceIn(0f, 1f),
        passes = passes.coerceIn(1, 8),
    )

    /** The additive passes as `(scale, alpha)` pairs: a wide faint outer copy down to a tight bright
     *  one. Empty when there's nothing to draw (`radius <= 1` or `intensity <= 0`). */
    fun glowPasses(): List<Pair<Float, Float>> {
        val p = passes.coerceIn(1, 8)
        val r = radius.coerceAtLeast(1f)
        val i = intensity.coerceIn(0f, 1f)
        if (i <= 0f || r <= 1f) return emptyList()
        return (0 until p).map { k ->
            val t = if (p == 1) 1f else k / (p - 1f)      // 0 = inner/bright, 1 = outer/faint
            val scale = 1f + (r - 1f) * (0.4f + 0.6f * t)
            val alpha = (i * (1f - 0.65f * t)).coerceIn(0f, 1f)
            scale to alpha
        }
    }
}

/** Draw [spec]'s additive halo behind/around [path] (call this BEFORE drawing the region's fill so
 *  the halo sits underneath). Colours come from [spec.color] (or [override]); no blur, so it is
 *  identical on both platforms. */
fun DrawScope.drawOGGlow(path: Path, spec: OGGlowSpec, override: Color? = null) {
    val passes = spec.glowPasses()
    if (passes.isEmpty()) return
    val tint = override ?: spec.color?.toSpecColor() ?: Color.White
    val pivot = path.getBounds().center
    passes.forEach { (s, a) ->
        scale(s, s, pivot) {
            drawPath(path, color = tint.copy(alpha = a), blendMode = BlendMode.Plus)
        }
    }
}

// --- Texture --------------------------------------------------------------------------------

/** One texture stroke in a region's own normalized `0..1` box, with its own [alpha]. */
data class OGTextureMark(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val alpha: Float)

/**
 * A procedural **texture** that breaks up a flat fill into fur, grass, hatching or speckle — the
 * biggest "cartoon → painterly" lever. A jittered grid of short strokes is laid over the region,
 * their opacity modulated by `OGFxNoise` (simplex + fBm) so it reads organic, not regular. The
 * stroke field ([marks]) is pure and deterministic (seeded); only the final `drawLine`s touch
 * Compose. Draw it clipped to the region, over the fill.
 */
@Serializable
data class OGTextureSpec(
    /** Advisory label of the look (`strokes` | `dots` | `hatch`); the geometry is driven by the knobs below. */
    val kind: String = "strokes",
    /** Strokes per axis (a `density × density` jittered grid); `2..120`. */
    val density: Int = 32,
    /** Stroke length as a fraction of the region box (`0` = a dot); `0..0.5`. */
    val length: Float = 0.03f,
    /** Stroke direction in degrees (`0` = →, `90` = ↓, good for grass blades / vertical fur). */
    val angle: Float = 90f,
    /** Positional jitter `0..1` (0 = a rigid grid, 1 = one cell of scatter). */
    val jitter: Float = 0.6f,
    /** Stroke width in pixels at draw time. */
    val weight: Float = 1.2f,
    /** Overall opacity ceiling `0..1` (the noise rides under this). */
    val amount: Float = 0.5f,
    /** fBm octaves for the opacity field; `1..8`. */
    val octaves: Int = 4,
    /** Noise seed (also seeds stroke placement) — same seed ⇒ same texture on every platform. */
    val seed: Int = 0,
    /** Stroke tint (`#RRGGBB` / name); `null` darkens (black), the usual way to add grain. */
    val color: String? = null,
) {
    /** Clamp knobs into their valid ranges. */
    fun sanitized(): OGTextureSpec = copy(
        density = density.coerceIn(2, 120),
        length = length.coerceIn(0f, 0.5f),
        jitter = jitter.coerceIn(0f, 1f),
        weight = weight.coerceAtLeast(0.1f),
        amount = amount.coerceIn(0f, 1f),
        octaves = octaves.coerceIn(1, 8),
    )

    /** The stroke field in the region's own `0..1` box — deterministic from the knobs + [seed]. */
    fun marks(): List<OGTextureMark> {
        val n = density.coerceIn(2, 120)
        val rng = OGFxRandom(if (seed == 0) 1 else seed)
        val rad = angle * (PI_F / 180f)
        val dx = cos(rad); val dy = sin(rad)
        val half = length.coerceIn(0f, 0.5f) * 0.5f
        val amt = amount.coerceIn(0f, 1f)
        val oct = octaves.coerceIn(1, 8)
        val out = ArrayList<OGTextureMark>(n * n)
        for (gy in 0 until n) for (gx in 0 until n) {
            val baseX = (gx + 0.5f) / n
            val baseY = (gy + 0.5f) / n
            val cx = (baseX + (rng.nextFloat() - 0.5f) * jitter / n).coerceIn(0f, 1f)
            val cy = (baseY + (rng.nextFloat() - 0.5f) * jitter / n).coerceIn(0f, 1f)
            val len = half * (0.6f + 0.8f * rng.nextFloat())
            val noise = 0.5f + 0.5f * OGFxNoise.fbm(cx * (4f + oct), cy * (4f + oct), oct, seed)
            val a = (amt * noise).coerceIn(0f, 1f)
            out.add(OGTextureMark(cx - dx * len, cy - dy * len, cx + dx * len, cy + dy * len, a))
        }
        return out
    }
}

/** Draw [spec]'s stroke field over the region occupying the pixel rect (`left`, `top`, `width`,
 *  `height`). Clip to the region's path first so the texture stays inside it. */
fun DrawScope.drawOGTexture(spec: OGTextureSpec, left: Float, top: Float, width: Float, height: Float) {
    val tint = spec.color?.toSpecColor() ?: Color.Black
    val w = spec.weight.coerceAtLeast(0.1f)
    spec.marks().forEach { m ->
        drawLine(
            color = tint.copy(alpha = m.alpha),
            start = Offset(left + m.x0 * width, top + m.y0 * height),
            end = Offset(left + m.x1 * width, top + m.y1 * height),
            strokeWidth = w,
            cap = StrokeCap.Round,
        )
    }
}

// --- Scatter --------------------------------------------------------------------------------

/**
 * **Scatter** stamps one region as a whole seeded field — a tree-line, a bank of bushes, a meadow
 * of grass tufts — from a single template shape, so a model authors one blade and gets a hundred.
 * Pure data: [expand] returns real [OGPlacedShapeSpec] copies (position + scale varied, sorted
 * back-to-front) that render through the exact same path as any other region. No new draw code.
 */
@Serializable
data class OGScatterSpec(
    /** How many copies to stamp (capped at [CAP]). */
    val count: Int = 12,
    /** Placement box in scene `0..1` space (default the whole image). */
    val x0: Float = 0f,
    val y0: Float = 0f,
    val x1: Float = 1f,
    val y1: Float = 1f,
    /** Per-copy scale range (fraction of the template's size). */
    val scaleMin: Float = 0.7f,
    val scaleMax: Float = 1f,
    /** Seed — same seed ⇒ the same field on every platform. */
    val seed: Int = 0,
    /** Sort copies by depth (farther/higher first) so nearer ones overlap correctly. */
    val sortByDepth: Boolean = true,
) {
    /** Clamp knobs into their valid ranges. */
    fun sanitized(): OGScatterSpec = copy(
        count = count.coerceIn(0, CAP),
        x0 = clamp01(x0), y0 = clamp01(y0), x1 = clamp01(x1), y1 = clamp01(y1),
        scaleMin = scaleMin.coerceAtLeast(1e-3f),
        scaleMax = scaleMax.coerceAtLeast(1e-3f),
    )

    /** Expand [template] into [count] placed copies. Each copy keeps the template's paint (fill,
     *  gradient, texture, glow…) but drops its own `scatter` so the result never re-expands. */
    fun expand(template: OGPlacedShapeSpec): List<OGPlacedShapeSpec> {
        val n = count.coerceIn(0, CAP)
        if (n == 0 || template.points.size < 3) return emptyList()
        val cx0 = template.points.fold(0f) { a, p -> a + p.x } / template.points.size
        val cy0 = template.points.fold(0f) { a, p -> a + p.y } / template.points.size
        val rng = OGFxRandom(if (seed == 0) 1 else seed)
        val ax0 = minOf(x0, x1); val ax1 = maxOf(x0, x1)
        val ay0 = minOf(y0, y1); val ay1 = maxOf(y0, y1)
        val lo = minOf(scaleMin, scaleMax).coerceAtLeast(1e-3f)
        val hi = maxOf(scaleMin, scaleMax).coerceAtLeast(1e-3f)
        val instances = ArrayList<Pair<Float, OGPlacedShapeSpec>>(n)
        repeat(n) {
            val px = rng.range(ax0, ax1)
            val py = rng.range(ay0, ay1)
            val sc = rng.range(lo, hi)
            val pts = template.points.map {
                OGPointSpec(clamp01(px + (it.x - cx0) * sc), clamp01(py + (it.y - cy0) * sc))
            }
            instances.add(py to template.copy(points = pts, scatter = null))
        }
        val ordered = if (sortByDepth) instances.sortedBy { it.first } else instances
        return ordered.map { it.second }
    }

    companion object {
        /** Hard cap on copies, so a runaway `count` can't blow up a scene. */
        const val CAP: Int = 400
    }
}

/** Resolve every region's [OGScatterSpec] into its real copies, leaving plain regions untouched and
 *  preserving back-to-front order. Call once after [OGSceneSpec.sanitized]; the result renders with
 *  the normal shape path. */
fun OGSceneSpec.expanded(): OGSceneSpec =
    copy(shapes = shapes.flatMap { s -> s.scatter?.expand(s) ?: listOf(s) })

private const val PI_F: Float = 3.1415927f
