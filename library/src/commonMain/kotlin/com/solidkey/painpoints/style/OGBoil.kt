package com.solidkey.painpoints.style

import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.shape.OGPolygonShape
import kotlin.math.floor

/**
 * A **"boiling" / wiggly-line animation** — the hand-drawn "living line" look where every vertex of an
 * outline jitters to a fresh pseudo-random offset [boilFps] times a second. Unlike a static filter this
 * is a *moving* style: feed it a rising time and the outline comes alive (think boiling lines, a nervous
 * sketch, a trembling sticker edge).
 *
 * It is the first member of the procedural **`style`** family (shares the library's zero-dependency
 * promise). Pure + **deterministic**: the same `(points, timeMs)` always yields the same result, so a
 * live preview, an export, and Android vs iOS all agree frame-for-frame. No RNG, no dependencies.
 *
 * It fits the perf/simplicity gate: you own your points once, then each frame this is just a cheap
 * per-vertex offset (an integer hash + a lerp) — it holds 60fps.
 *
 * @param amplitude max displacement per vertex, in the **same units as the points you pass** — e.g.
 *   `0.02` on a normalized `0..1` [OGPolygonShape], or pixels on a pixel-space list.
 * @param boilFps how many times a second the jitter re-rolls. ~6-12 reads as a lively "boil"; lower is
 *   calmer. Independent of the render frame rate.
 * @param smooth interpolate between successive boil frames (a gentle wobble) instead of hard-stepping
 *   (the classic stop-motion stutter).
 * @param seed vary for a different random wiggle from the same inputs.
 */
data class OGBoil(
    val amplitude: Float = 0.02f,
    val boilFps: Float = 8f,
    val smooth: Boolean = true,
    val seed: Int = 0,
) {
    /** Displace every point by the boil sampled at [timeMs]. Output shares the input's units and order. */
    fun displace(points: List<OGPoint>, timeMs: Long): List<OGPoint> {
        if (amplitude <= 0f || points.isEmpty()) return points
        val fps = boilFps.coerceAtLeast(0.001f)
        val pos = timeMs * fps.toDouble() / 1000.0
        val frame = floor(pos).toInt()
        val frac = (pos - frame).toFloat()
        val t = if (smooth) frac * frac * (3f - 2f * frac) else 0f // smoothstep, or hold (stutter)
        return points.mapIndexed { i, p ->
            val dx = lerp(noise(i, frame, 0), noise(i, frame + 1, 0), t)
            val dy = lerp(noise(i, frame, 1), noise(i, frame + 1, 1), t)
            OGPoint(p.x + dx * amplitude, p.y + dy * amplitude)
        }
    }

    /** Pseudo-random value in ~[-1, 1] for vertex [i], boil [frame] and [axis] — integer hash, no RNG. */
    private fun noise(i: Int, frame: Int, axis: Int): Float {
        var h = i * 374761393 + frame * 668265263 + axis * 1013904223 + seed * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 32768f - 1f
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

/**
 * Boil an [OGPolygonShape]'s outline at [timeMs]. The result is a plain [OGPolygonShape], so it drops
 * straight into any `clipShape` slot — a photo, GIF, video or live camera clip gets a living, hand-cut
 * edge with no other change (re-create it each frame from a rising time, exactly like `OGMorphShape`).
 */
fun OGPolygonShape.boiled(boil: OGBoil, timeMs: Long): OGPolygonShape =
    OGPolygonShape(boil.displace(points, timeMs))

/**
 * Rasterize a normalized [polygon] (`0..1`) into a [resolution]×[resolution] grid and return the
 * **center of every cell that falls inside it** — a pixel-art / mosaic fill. Pair it with a boil
 * (pixelate the *boiled* outline each frame) for the chunky "8-bit sprite whose edge shimmers" look.
 *
 * Pure even-odd ray-cast, cheap for coarse grids (e.g. 20×20), zero dependencies. Draw a filled
 * `1f/resolution`-sized square at each returned center. This is the "pixelate" op of the procedural
 * style family, composable after boil: `pixelateFill(boil.displace(points, t), res)`.
 */
fun pixelateFill(polygon: List<OGPoint>, resolution: Int): List<OGPoint> {
    if (polygon.size < 3 || resolution < 1) return emptyList()
    val step = 1f / resolution
    val out = ArrayList<OGPoint>()
    for (gy in 0 until resolution) {
        val cy = (gy + 0.5f) * step
        for (gx in 0 until resolution) {
            val cx = (gx + 0.5f) * step
            if (pointInPolygon(cx, cy, polygon)) out.add(OGPoint(cx, cy))
        }
    }
    return out
}

/** Even-odd point-in-polygon test for a normalized outline. */
private fun pointInPolygon(x: Float, y: Float, poly: List<OGPoint>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val xi = poly[i].x; val yi = poly[i].y
        val xj = poly[j].x; val yj = poly[j].y
        if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) inside = !inside
        j = i
    }
    return inside
}
