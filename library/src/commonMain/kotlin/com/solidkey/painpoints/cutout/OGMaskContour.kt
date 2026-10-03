package com.solidkey.painpoints.cutout

import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.shape.OGPolygonShape
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Turns a [OGSegmentationMask] into a free-form [OGPolygonShape] lasso by tracing the outline of the
 * subject — the genuinely reusable, **zero-dependency** half of auto-cutout. Whatever produced the
 * mask (a built-in [OGChromaKeySegmenter], ML Kit, Vision, a cloud model), this traces its largest
 * foreground blob into a simplified, normalised polygon that drops straight into
 * [com.solidkey.painpoints.image.OGImageView]'s `clipShape`.
 *
 * It runs **once** (off the main thread) to produce the polygon; the resulting clip is then the same
 * GPU mask as any other shape, so there is no per-frame cost — it respects the perf gate.
 *
 * Pipeline: threshold → largest connected component (so background speckle is ignored) →
 * Moore-neighbor boundary trace → Douglas-Peucker simplify → normalise to `0..1`.
 */
object OGMaskContour {

    private data class P(val x: Int, val y: Int)

    /**
     * @param threshold foreground cutoff on the mask's `0..1` confidence (default `0.5`).
     * @param simplifyTolerance Douglas-Peucker tolerance as a **fraction of the longest edge**
     *   (default `0.01` = 1%); larger = fewer, coarser vertices.
     * @param maxVertices hard cap on the returned vertex count (the tolerance is raised until it fits),
     *   so a jagged mask can't yield a thousand-point polygon.
     * @return the subject outline, or `null` if the mask has no foreground / too small a region.
     */
    fun maskToPolygon(
        mask: OGSegmentationMask,
        threshold: Float = 0.5f,
        simplifyTolerance: Float = 0.01f,
        maxVertices: Int = 120,
    ): OGPolygonShape? {
        val w = mask.width
        val h = mask.height
        if (w < 3 || h < 3) return null

        val binary = BooleanArray(w * h) { mask.foreground[it] >= threshold }
        val component = largestComponent(binary, w, h) ?: return null
        val boundary = traceBoundary(component, w, h)
        if (boundary.size < 3) return null

        val epsilonPx = max(1f, simplifyTolerance * max(w, h))
        var simplified = douglasPeucker(boundary, epsilonPx)
        // Raise tolerance until under the vertex cap (coarse but bounded).
        var eps = epsilonPx
        while (simplified.size > maxVertices) {
            eps *= 1.5f
            simplified = douglasPeucker(boundary, eps)
        }
        if (simplified.size < 3) return null

        val wf = (w - 1).toFloat()
        val hf = (h - 1).toFloat()
        return OGPolygonShape(simplified.map { OGPoint(it.x / wf, it.y / hf) })
    }

    /** 8-connected flood fill; returns a mask of the single largest foreground blob, or null. */
    private fun largestComponent(binary: BooleanArray, w: Int, h: Int): BooleanArray? {
        val label = IntArray(w * h) { -1 }
        val queue = ArrayDeque<Int>()
        var bestLabel = -1
        var bestSize = 0
        var current = 0
        val sizes = ArrayList<Int>()
        for (start in binary.indices) {
            if (!binary[start] || label[start] != -1) continue
            // BFS this component
            label[start] = current
            queue.addLast(start)
            var size = 0
            while (queue.isNotEmpty()) {
                val idx = queue.removeFirst()
                size++
                val cx = idx % w
                val cy = idx / w
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = cx + dx
                    val ny = cy + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val ni = ny * w + nx
                    if (binary[ni] && label[ni] == -1) {
                        label[ni] = current
                        queue.addLast(ni)
                    }
                }
            }
            sizes.add(size)
            if (size > bestSize) {
                bestSize = size
                bestLabel = current
            }
            current++
        }
        if (bestLabel == -1) return null
        return BooleanArray(w * h) { label[it] == bestLabel }
    }

    // 8 neighbours in clockwise order starting West: W, NW, N, NE, E, SE, S, SW.
    private val OFFSETS = arrayOf(
        -1 to 0, -1 to -1, 0 to -1, 1 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1,
    )

    /** Moore-neighbor boundary trace (clockwise) of the component's outer contour. */
    private fun traceBoundary(comp: BooleanArray, w: Int, h: Int): List<P> {
        fun fg(x: Int, y: Int) = x in 0 until w && y in 0 until h && comp[y * w + x]

        // Start = first foreground pixel scanning top→bottom, left→right (guaranteed on the outer edge).
        var start = -1
        for (i in comp.indices) if (comp[i]) { start = i; break }
        if (start == -1) return emptyList()
        val sx = start % w
        val sy = start / w

        val boundary = ArrayList<P>()
        boundary.add(P(sx, sy))
        var bx = sx
        var by = sy
        // We entered the start from its west neighbour (background, since start is leftmost in its row).
        var prevX = sx - 1
        var prevY = sy
        val cap = 4 * (w * h) + 16
        var steps = 0
        while (steps++ < cap) {
            // direction index of the pixel we came from, relative to current
            val fromDir = OFFSETS.indexOfFirst { it.first == prevX - bx && it.second == prevY - by }
            val d0 = if (fromDir < 0) 0 else fromDir
            var moved = false
            for (k in 1..8) {
                val d = (d0 + k) % 8
                val nx = bx + OFFSETS[d].first
                val ny = by + OFFSETS[d].second
                if (fg(nx, ny)) {
                    // the empty pixel just before n in the clockwise scan becomes our new "came from"
                    val pd = (d - 1 + 8) % 8
                    prevX = bx + OFFSETS[pd].first
                    prevY = by + OFFSETS[pd].second
                    bx = nx
                    by = ny
                    moved = true
                    break
                }
            }
            if (!moved) break // isolated pixel
            if (bx == sx && by == sy) break // closed the loop
            boundary.add(P(bx, by))
        }
        return boundary
    }

    /** Perpendicular distance from point p to the line a→b. */
    private fun perpDistance(p: P, a: P, b: P): Float {
        val dx = (b.x - a.x).toFloat()
        val dy = (b.y - a.y).toFloat()
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-6f) {
            val ex = (p.x - a.x).toFloat()
            val ey = (p.y - a.y).toFloat()
            return sqrt(ex * ex + ey * ey)
        }
        // |cross| / len
        return abs(dx * (a.y - p.y).toFloat() - (a.x - p.x).toFloat() * dy) / len
    }

    /** Douglas-Peucker polyline simplification over a closed ring. */
    private fun douglasPeucker(points: List<P>, epsilon: Float): List<P> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true

        // iterative stack-based DP
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.size - 1)
        while (stack.isNotEmpty()) {
            val (first, last) = stack.removeLast()
            var maxDist = 0f
            var index = -1
            for (i in first + 1 until last) {
                val d = perpDistance(points[i], points[first], points[last])
                if (d > maxDist) {
                    maxDist = d
                    index = i
                }
            }
            if (index != -1 && maxDist > epsilon) {
                keep[index] = true
                stack.addLast(first to index)
                stack.addLast(index to last)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }
}
