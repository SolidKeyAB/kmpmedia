package com.solidkey.painpoints.fx

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modifier FX for the `com.solidkey.painpoints.fx` action family. Each captures the composable's drawn
 * content into `GraphicsLayer`(s) and re-draws it — so they work on *any* content (a shape, a lasso-cut
 * photo, an SVG, text) with no per-content code. [ogGlow] and [ogAfterImage] are pure composites
 * (additive scaled copies / fading ghosts): zero-dep, 60fps, identical on Android & iOS. [ogBloom] is
 * the opt-in true-gaussian variant (a platform `RenderEffect` blur, Android 31+, not frame-identical).
 *
 * Implementation note — each effect draws a few **distinct** composites per frame, so it uses a small
 * pool of layers, one per composite. A `GraphicsLayer`'s transform / alpha / blend / `renderEffect`
 * are RenderNode properties read at *rasterization* time (frame end), not at `drawLayer()` time — so a
 * single reused layer mutated between draws would collapse every composite to its final state (the
 * halo/fade/blur would vanish). Canvas ops like `translate` DO bake per draw, so position is fine.
 */

/** Max ghosts [ogAfterImage] draws (one pooled layer each); longer trails are capped to this. */
private const val AFTER_IMAGE_MAX = 16

/**
 * **After-image / motion smear** — draw a trail of fading copies of this composable behind it, offset
 * by [offsets] (in px, index `0` = nearest/brightest), for the god-speed dash look. Supply the trail
 * from your animation's recent positions. [maxAlpha] is the nearest ghost's opacity. At most
 * [AFTER_IMAGE_MAX] ghosts are drawn (a longer [offsets] list is capped).
 */
fun Modifier.ogAfterImage(offsets: List<Offset>, maxAlpha: Float = 0.5f): Modifier = composed {
    // One DEDICATED pooled layer per ghost: a reused layer's `alpha` (a RenderNode property) is read at
    // rasterize time, so every ghost would otherwise rasterize at the same final alpha and the fade
    // would vanish. (Position via translate() is a per-draw canvas op, so it never needed this.)
    val pool = List(AFTER_IMAGE_MAX) { rememberGraphicsLayer() }
    drawWithContent {
        val n = offsets.size.coerceAtMost(AFTER_IMAGE_MAX)
        // Farthest (faintest) first so the nearer ghosts layer on top.
        for (i in n - 1 downTo 0) {
            val layer = pool[i]
            layer.record { this@drawWithContent.drawContent() }
            layer.alpha = (maxAlpha * (1f - i / (offsets.size + 1f))).coerceIn(0f, 1f)
            translate(offsets[i].x, offsets[i].y) { drawLayer(layer) }
        }
        drawContent() // the crisp current frame on top
    }
}

/**
 * **After-image** convenience: a straight trail of [count] ghosts each offset by [step] px from the
 * last (a static directional smear), fading from [maxAlpha].
 */
fun Modifier.ogAfterImage(count: Int, step: Offset, maxAlpha: Float = 0.5f): Modifier =
    ogAfterImage(List(count.coerceAtLeast(0)) { Offset(step.x * (it + 1), step.y * (it + 1)) }, maxAlpha)

/**
 * **Glow / bloom halo** around this composable, blur-free and cross-platform: a few additive
 * (`BlendMode.Plus`), outward-scaled copies of the content are drawn behind it. Pass [color] to tint
 * the halo (e.g. a neon colour); `null` blooms the content's own colours. [radius] is how far the
 * outer copy expands (`1.0` = none), [intensity] the halo opacity, [passes] the softness.
 *
 * This is the zero-dependency bloom. A true gaussian bloom needs a platform shader (API-floored and
 * not frame-identical); that remains an opt-in, not this.
 */
fun Modifier.ogGlow(
    color: Color? = null,
    radius: Float = 1.3f,
    intensity: Float = 0.6f,
    passes: Int = 3,
): Modifier = composed {
    // One DEDICATED pooled layer per pass. A GraphicsLayer's scale/alpha/blend are RenderNode properties
    // read at rasterize time, so a single reused layer drawn N times would rasterize every pass at the
    // LAST scale+alpha (a crisp redraw, no halo). One layer per pass keeps each pass's transform intact.
    val pool = List(6) { rememberGraphicsLayer() }
    drawWithContent {
        val p = passes.coerceIn(1, 6)
        val tint = color?.let { ColorFilter.tint(it) }
        for (k in 0 until p) {
            val t = if (p == 1) 0f else k / (p - 1f)
            val layer = pool[k]
            layer.record { this@drawWithContent.drawContent() }
            layer.blendMode = BlendMode.Plus
            layer.colorFilter = tint
            layer.alpha = (intensity * (1f - 0.6f * t)).coerceIn(0f, 1f)
            val s = 1f + (radius - 1f) * (0.45f + 0.55f * t)
            layer.scaleX = s; layer.scaleY = s
            drawLayer(layer)
        }
        drawContent() // the crisp content on top
    }
}

/**
 * Pure falloff plan for [ogBloom]: given a blur [radiusPx] and [intensity], returns the additive
 * passes as `(radiusPx, alpha)` pairs — a wide+soft halo under a tighter+brighter core, which sum
 * (under `BlendMode.Plus`) to a pyramid-like bloom with a hot centre. Returns empty when there is
 * nothing to draw (zero/negative radius or zero intensity). Extracted so the math is unit-testable.
 */
internal fun bloomPasses(radiusPx: Float, intensity: Float): List<Pair<Float, Float>> {
    val r = radiusPx.coerceAtLeast(0f)
    val i = intensity.coerceIn(0f, 1f)
    if (r <= 0f || i <= 0f) return emptyList()
    return listOf(
        r to (i * 0.6f),      // wide, soft halo
        (r * 0.45f) to i,     // tighter, brighter core
    )
}

/**
 * **True-gaussian bloom** around this composable — the soft, cinematic cousin of [ogGlow]. Captures
 * the content into a `GraphicsLayer` once, then redraws it through a real platform gaussian blur
 * ([BlurEffect] → Android `RenderEffect` / iOS Skia) additively (`BlendMode.Plus`): a wide soft halo
 * under a tighter bright core, so luminous areas bleed light with a smooth gaussian falloff.
 *
 * **Opt-in, by design** — unlike [ogGlow] this is NOT zero-dep / frame-identical:
 *  - It leans on a platform blur, so the soft look needs **Android 31+**; on older devices the blur
 *    is a no-op and it degrades gracefully to a crisp draw (use [ogGlow] for a cross-platform
 *    additive halo there).
 *  - Android and iOS blurs are **not pixel-identical**, so bloomed content is excluded from the
 *    byte-identical export/test guarantee. Reach for this when you want the softest look and accept that.
 *
 * [radius] is the blur reach, [intensity] the halo opacity, [color] an optional neon tint (`null`
 * blooms the content's own colours).
 */
fun Modifier.ogBloom(
    radius: Dp = 12.dp,
    intensity: Float = 0.8f,
    color: Color? = null,
): Modifier = composed {
    // One DEDICATED layer per blur pass. `renderEffect` is a RenderNode property read at
    // rasterization time (frame end), not at drawLayer() time, so a single reused-and-reset layer
    // would rasterize every pass with the LAST effect (none). A layer per pass, whose blur is set
    // once and never reset, keeps each pass's gaussian intact. (alpha/blend ARE snapshotted per draw.)
    val wide = rememberGraphicsLayer()
    val core = rememberGraphicsLayer()
    drawWithContent {
        val passes = bloomPasses(radius.toPx(), intensity)
        if (passes.isEmpty()) {
            drawContent()
            return@drawWithContent
        }
        val tint = color?.let { ColorFilter.tint(it) }
        val layers = listOf(wide, core)
        passes.forEachIndexed { i, (rPx, a) ->
            val layer = layers[i]
            layer.record { this@drawWithContent.drawContent() }
            layer.renderEffect = BlurEffect(radiusX = rPx, radiusY = rPx, edgeTreatment = TileMode.Decal)
            layer.blendMode = BlendMode.Plus
            layer.colorFilter = tint
            layer.alpha = a
            drawLayer(layer)
        }
        drawContent() // the crisp current frame on top
    }
}

/**
 * **Camera Trauma / Cinematic Screen Shake** — Applies quadratic screen shake (Squirrel Eiserloh's
 * game-feel standard: `shake = trauma²`) to any composable. Trauma decay, high-frequency rotational
 * and translational displacement gives authentic anime / blockbuster impact feel.
 *
 * [trauma] is `0..1` (values > 0 trigger shake; decaying each frame or animated via `Animatable`).
 * [timeSec] drives the continuous deterministic wobble (pass animation progress or elapsed seconds).
 * [maxTranslationPx] is the peak horizontal/vertical displacement at trauma=1.
 * [maxRotationDeg] is the peak rotational tilt in degrees at trauma=1.
 * [frequency] controls the vibration speed (default ~24Hz).
 * [seed] decorrelates distinct screen instances.
 */
fun Modifier.ogCameraTrauma(
    trauma: Float,
    timeSec: Float = 0f,
    maxTranslationPx: Float = 24f,
    maxRotationDeg: Float = 3.5f,
    frequency: Float = 24f,
    seed: Int = 42,
): Modifier = graphicsLayer {
    val t = trauma.coerceIn(0f, 1f)
    if (t > 0.001f) {
        val shake = t * t // Non-linear falloff: low trauma feels subtle, high trauma feels violent
        val phase = timeSec * frequency * 2f * PI.toFloat()
        val rnd1 = sin(phase + seed * 1.7f)
        val rnd2 = cos(phase * 1.25f + seed * 3.1f)
        val rnd3 = sin(phase * 0.85f + seed * 5.3f)

        translationX = rnd1 * maxTranslationPx * shake
        translationY = rnd2 * maxTranslationPx * 0.75f * shake
        rotationZ = rnd3 * maxRotationDeg * shake
    }
}

/**
 * **Electrocute / High-Voltage Plasma Arc** modifier — applies electrical spasm jitter,
 * inverted/overexposed plasma flash, and branching electric arcing over the composable's bounds.
 *
 * [intensity] is `0..1` (driving the flash and arc density).
 * [color] is the electric arc corona colour (default bright cyan `#80D8FF`).
 * [seed] decorrelates the fractal arcs.
 */
fun Modifier.ogElectrocute(
    intensity: Float,
    color: Color = Color(0xFF80D8FF),
    seed: Int = 42,
): Modifier = composed {
    val i = intensity.coerceIn(0f, 1f)
    if (i <= 0.001f) return@composed this
    drawWithContent {
        drawContent()
        if (i > 0.02f) {
            // High-voltage electric corona wash
            drawRect(color = color.copy(alpha = i * 0.28f), blendMode = BlendMode.Plus)
            // Branching electrical spark discharge arcs around the perimeter
            val w = size.width; val h = size.height
            if (w > 0f && h > 0f) {
                val rng = kotlin.random.Random(seed + (i * 200).toInt())
                val arcCount = (3 + (i * 4).toInt()).coerceAtMost(8)
                for (a in 0 until arcCount) {
                    val startX = rng.nextFloat() * w
                    val startY = rng.nextFloat() * h
                    val path = Path().apply {
                        moveTo(startX, startY)
                        var curX = startX; var curY = startY
                        repeat(4) {
                            curX += (rng.nextFloat() - 0.5f) * w * 0.35f
                            curY += (rng.nextFloat() - 0.5f) * h * 0.35f
                            lineTo(curX, curY)
                        }
                    }
                    drawPath(path, color = Color.White, style = Stroke(width = 2.5f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
                    drawPath(path, color = color, style = Stroke(width = 5.5f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
                }
            }
        }
    }
}

/**
 * **Underwater Caustic Light Web** modifier — draws shimmering solar refraction ribbons across
 * submerged content, simulating deep underwater lighting.
 *
 * [timeSec] drives the continuous caustic undulation.
 * [depth] is `0..1` (controlling caustic brightness and water murk).
 * [color] is the caustic refraction light colour.
 */
fun Modifier.ogWaterCaustics(
    timeSec: Float,
    depth: Float = 0.5f,
    color: Color = Color(0xFF81D4FA),
): Modifier = composed {
    val d = depth.coerceIn(0f, 1f)
    if (d <= 0.001f) return@composed this
    drawWithContent {
        drawContent()
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f) return@drawWithContent
        val ribbonCount = 5
        for (r in 0 until ribbonCount) {
            val yBase = (r.toFloat() / ribbonCount) * h + sin(timeSec * 3f + r) * 12f
            val path = Path().apply {
                moveTo(0f, yBase)
                val steps = 8
                for (s in 1..steps) {
                    val x = (s.toFloat() / steps) * w
                    val y = yBase + sin(x * 0.03f + timeSec * 4f + r * 1.5f) * 10f
                    lineTo(x, y)
                }
            }
            drawPath(
                path,
                color = color.copy(alpha = d * 0.35f),
                style = Stroke(width = 6f, cap = StrokeCap.Round),
                blendMode = BlendMode.Plus,
            )
        }
    }
}
