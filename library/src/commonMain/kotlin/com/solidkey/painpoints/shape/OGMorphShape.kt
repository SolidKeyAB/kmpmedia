package com.solidkey.painpoints.shape

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/** Default number of perimeter samples per endpoint. High enough that a circle reads as smooth. */
const val OG_MORPH_SAMPLES: Int = 96

/**
 * A clip [Shape] whose outline **morphs** from [from] to [to] as [progress] runs `0f → 1f`.
 *
 * This is the *shape-morph clip*: because every KMPMedia media surface clips through a plain
 * Compose [Shape] (`OGImageView.clipShape`, `OGAVPlayer`'s `OGAVConfig.clipShape`, or any
 * `Modifier.clip`), an [OGMorphShape] drops straight into that existing slot — so a **video, GIF
 * or photo** can be masked by a shape that animates circle → diamond → lasso live, with no new
 * surface API. Drive [progress] from any Compose animation (an `animateFloatAsState`, an infinite
 * transition, a slider) exactly like `OGSvgNodeOverride.morphProgress` drives SVG path morphing.
 *
 * ### How it holds 60fps (the perf + simplicity gate)
 * The two endpoints can be *any* shapes (built-ins, a [OGPolygonShape] lasso, a `RoundedCornerShape`),
 * so we can't tween them command-for-command like the SVG engine. Instead each endpoint's outline is
 * **resampled once** into [sampleCount] equally-spaced perimeter points and cached by
 * `(from, to, size)`; driving [progress] across frames then only lerps those two point lists — no
 * re-measure, no allocation storm — the same *sample-once / lerp-per-frame* shape the SVG morph uses.
 * At `progress <= 0` / `>= 1` the raw endpoint outline passes straight through (zero resample cost),
 * so a static clip is exactly as cheap as it was before.
 *
 * Give the endpoint shapes stable identities (e.g. `remember { OGPolygonShape(pts) }`, or the
 * `CircleShape` singleton) so the resample cache hits every frame. Mismatched winding is auto-aligned
 * so shapes morph without swirling; a degenerate endpoint (empty outline) snaps to [from].
 */
class OGMorphShape(
    val from: Shape,
    val to: Shape,
    val progress: Float,
    val sampleCount: Int = OG_MORPH_SAMPLES,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = progress.coerceIn(0f, 1f)
        // Endpoints are the real shape — no resampling, so a settled clip costs nothing extra.
        if (t <= 0f) return from.createOutline(size, layoutDirection, density)
        if (t >= 1f) return to.createOutline(size, layoutDirection, density)

        val pair = morphSampleCache.resolve(from, to, size, layoutDirection, density, sampleCount)
            ?: return from.createOutline(size, layoutDirection, density) // degenerate outline → snap to start
        return Outline.Generic(polygonPath(lerpPoints(pair.from, pair.to, t)))
    }
}

/**
 * A multi-stop clip morph: one [progress] `0f → 1f` walks the whole [stops] chain
 * (e.g. `circle → diamond → triangle → lasso`). Splits [progress] evenly across the
 * `stops.size - 1` segments and returns an [OGMorphShape] for the active adjacent pair.
 * Repeat the first stop at the end for a seamless loop. Needs at least two stops.
 */
fun ogMorphSequence(stops: List<Shape>, progress: Float, sampleCount: Int = OG_MORPH_SAMPLES): Shape {
    require(stops.size >= 2) { "ogMorphSequence needs at least 2 stops, got ${stops.size}" }
    val (i, localT) = morphSegment(stops.size, progress)
    return OGMorphShape(stops[i], stops[i + 1], localT, sampleCount)
}

// ---------------------------------------------------------------------------------------------
// Pure geometry — no Density / PathMeasure, so it is directly unit-testable off-device.
// ---------------------------------------------------------------------------------------------

/** Which adjacent stop pair (index `i` → `i+1`) and local `0..1` fraction [progress] lands on. */
internal fun morphSegment(stopCount: Int, progress: Float): Pair<Int, Float> {
    require(stopCount >= 2)
    val segments = stopCount - 1
    val p = progress.coerceIn(0f, 1f)
    if (p >= 1f) return (segments - 1) to 1f
    val scaled = p * segments
    val idx = scaled.toInt().coerceIn(0, segments - 1)
    return idx to (scaled - idx)
}

/** Index-for-index lerp of two equal-length point lists. */
internal fun lerpPoints(from: List<Offset>, to: List<Offset>, t: Float): List<Offset> {
    val p = t.coerceIn(0f, 1f)
    val n = minOf(from.size, to.size)
    return List(n) { i ->
        Offset(from[i].x + (to[i].x - from[i].x) * p, from[i].y + (to[i].y - from[i].y) * p)
    }
}

/** Cyclically rotates [pts] left by [shift]. */
internal fun rotatePoints(pts: List<Offset>, shift: Int): List<Offset> {
    val n = pts.size
    if (n == 0) return pts
    val s = ((shift % n) + n) % n
    return List(n) { pts[(it + s) % n] }
}

/**
 * Returns [to] re-indexed (cyclic shift, and reversal if that fits better) to line up with [from],
 * so morphing between two outlines whose sample-0 sits at different corners — or that wind opposite
 * ways — doesn't visibly swirl. O(n²) but runs once per resample (cached), never per frame. Winding
 * is irrelevant to the filled clip region, so reversing [to] only ever helps the alignment.
 */
internal fun alignPoints(from: List<Offset>, to: List<Offset>): List<Offset> {
    val n = from.size
    if (n == 0 || to.size != n) return to
    var best = to
    var bestCost = Float.MAX_VALUE
    for (candidate in listOf(to, to.asReversed())) {
        for (shift in 0 until n) {
            var cost = 0f
            for (i in 0 until n) {
                val a = from[i]
                val b = candidate[(i + shift) % n]
                val dx = a.x - b.x
                val dy = a.y - b.y
                cost += dx * dx + dy * dy
                if (cost >= bestCost) break // can't win — abandon this shift early
            }
            if (cost < bestCost) {
                bestCost = cost
                best = rotatePoints(candidate.toList(), shift)
            }
        }
    }
    return best
}

// ---------------------------------------------------------------------------------------------
// Platform-backed geometry (PathMeasure / Path) + the sample cache.
// ---------------------------------------------------------------------------------------------

private fun polygonPath(pts: List<Offset>): Path = Path().apply {
    if (pts.size >= 3) {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }
}

/** Any [Outline] → a [Path] we can walk with a [PathMeasure]. */
private fun Outline.toPath(): Path = when (this) {
    is Outline.Generic -> path
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
}

/** Resamples an outline's (first) contour into [n] equally-spaced points; null if it has no length. */
private fun resampleOutline(outline: Outline, n: Int): List<Offset>? {
    val measure = PathMeasure().apply { setPath(outline.toPath(), forceClosed = true) }
    val length = measure.length
    if (length <= 0f || n < 3) return null
    val step = length / n
    return List(n) { i -> measure.getPosition(step * i) }
}

private class AlignedPair(val from: List<Offset>, val to: List<Offset>)

/**
 * Bounded, insertion-ordered cache of resampled+aligned endpoint pairs, keyed by
 * `(from, to, rounded size, sampleCount)`. Only ever touched on the UI thread (Compose layout/draw),
 * so no synchronization is needed. Stable endpoint identities → a hit every frame → per-frame work is
 * just the lerp in [OGMorphShape.createOutline].
 */
private class MorphSampleCache(private val capacity: Int = 16) {
    private data class Key(val from: Shape, val to: Shape, val w: Int, val h: Int, val n: Int)
    private val entries = LinkedHashMap<Key, AlignedPair>()

    fun resolve(
        from: Shape, to: Shape, size: Size, ld: LayoutDirection, density: Density, n: Int,
    ): AlignedPair? {
        val key = Key(from, to, size.width.roundToInt(), size.height.roundToInt(), n)
        entries[key]?.let { return it }

        val fromPts = resampleOutline(from.createOutline(size, ld, density), n) ?: return null
        val toPts = resampleOutline(to.createOutline(size, ld, density), n) ?: return null
        val pair = AlignedPair(fromPts, alignPoints(fromPts, toPts))

        entries[key] = pair
        if (entries.size > capacity) entries.remove(entries.keys.first()) // evict eldest
        return pair
    }
}

private val morphSampleCache = MorphSampleCache()
