package com.solidkey.painpoints.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.solidkey.painpoints.ai.toSpecColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * **Crack / fracture** — jagged crack lines radiating from an impact point, described as plain data.
 * Part of the `com.solidkey.painpoints.fx` family. Decode + presets on [OGCrack] ([OGCrack.GROUND] wide
 * rough earth cracks, [OGCrack.ICE] thin spider-web, [OGCrack.TILE] hairline board cracks,
 * [OGCrack.GLASS] fine radial web); resolve the geometry once with [crackArms], then draw each frame
 * with [drawOGCrackFrame].
 *
 * A caller-driven `progress 0..1` grows the cracks outward from the origin — each arm's polyline is
 * revealed segment by segment, with the leading segment drawn partially, so the fracture visibly
 * *spreads*. Rendering is two stroke passes (a wide dark [shadowColor] underlay for depth, then the
 * bright [crackColor] line on top) — plain `drawLine` calls, no shader, no per-pixel work — and the
 * geometry is pure + deterministic from [seed], so Android & iOS are frame-identical.
 */
@kotlinx.serialization.Serializable
data class OGCrackSpec(
    val name: String? = null,
    /** Main visible crack line colour — any SVG colour string. */
    val crackColor: String = "#CCCCCC",
    /** Dark shadow drawn under the crack (reads as depth). */
    val shadowColor: String = "#050302",
    /** Number of main crack arms radiating from the origin. Clamped `2..20`. */
    val arms: Int = 7,
    /** Branching sub-crack generations per arm (`0` = no branches). Clamped `0..3`. */
    val depth: Int = 1,
    /** Max reach of each arm, as a fraction of the view's shorter side. */
    val length: Float = 0.40f,
    /** Main crack stroke width, as a fraction of the view's shorter side. */
    val crackWidth: Float = 0.012f,
    /** Shadow stroke width (wider than the crack), as a fraction of the view's shorter side. */
    val shadowWidth: Float = 0.028f,
    /** How jagged the arms are: `0` = straight rays, `1` = very angular. */
    val irregularity: Float = 0.5f,
    /** Seed for the deterministic fracture layout. */
    val seed: Int = 0,
) {
    /** Clamp every field into its sane range (lenient model/designer input never breaks a frame). */
    fun sanitized(): OGCrackSpec = copy(
        arms = arms.coerceIn(2, 20),
        depth = depth.coerceIn(0, 3),
        length = length.coerceIn(0.05f, 1f),
        crackWidth = crackWidth.coerceIn(0.001f, 0.1f),
        shadowWidth = shadowWidth.coerceIn(0.001f, 0.2f),
        irregularity = irregularity.coerceIn(0f, 1f),
    )
}

/** One point on a crack arm, normalized `0..1` of the board/view. */
data class OGCrackPoint(val x: Float, val y: Float)

/** One resolved crack arm: a list of [OGCrackPoint] from its start outward. */
data class OGCrackArm(val points: List<OGCrackPoint>)

/**
 * Resolve a [spec]'s fracture geometry around the normalized origin `(originX, originY)` — pure +
 * deterministic from [OGCrackSpec.seed], so the same `(spec, origin)` always yields the same arms (and
 * both platforms match). Main arms spread evenly around 360° with a small per-arm jitter, step outward
 * in 6–8 bending segments, and (when [OGCrackSpec.depth] `>= 1`) spawn shorter sub-branches partway
 * along. Build once per impact, then feed the list to [drawOGCrackFrame] every frame.
 */
fun crackArms(spec: OGCrackSpec, originX: Float, originY: Float): List<OGCrackArm> {
    val s = spec.sanitized()
    val r = Random(s.seed)
    val out = mutableListOf<OGCrackArm>()
    for (i in 0 until s.arms) {
        val baseAngle = i * (360f / s.arms) + OGFxMath.hashSigned(i, s.seed) * 15f
        val segCount = 6 + (i % 3)
        growArm(out, s, r, originX, originY, baseAngle, s.length, segCount, s.depth)
    }
    return out
}

/** Grow one arm as a bending polyline, recursively spawning sub-branches while [depthLeft] allows. */
private fun growArm(
    out: MutableList<OGCrackArm>,
    spec: OGCrackSpec,
    r: Random,
    startX: Float,
    startY: Float,
    startAngle: Float,
    length: Float,
    segCount: Int,
    depthLeft: Int,
) {
    var angle = startAngle
    var x = startX
    var y = startY
    val segLen = length / segCount
    val points = mutableListOf(OGCrackPoint(x, y))
    val branchAt = (segCount * 2) / 3 // spawn the sub-branch at ~60-70% of the arm
    for (seg in 0 until segCount) {
        val step = segLen * (0.7f + r.nextFloat() * 0.6f)
        val rad = angle * (PI.toFloat() / 180f)
        x += cos(rad) * step
        y += sin(rad) * step
        points += OGCrackPoint(x, y)
        if (depthLeft >= 1 && seg == branchAt) {
            val side = if (r.nextFloat() < 0.5f) -1f else 1f
            val deviation = side * (25f + 15f * spec.irregularity)
            val remaining = segLen * (segCount - seg - 1)
            val subSegs = (segCount / 2).coerceAtLeast(2)
            growArm(out, spec, r, x, y, angle + deviation, remaining * 0.5f, subSegs, depthLeft - 1)
        }
        angle += r.nextFloat() * 44f * spec.irregularity - 22f * spec.irregularity
    }
    out += OGCrackArm(points)
}

/**
 * Draw one frame of a growing fracture into the current [DrawScope]: the cached [arms] (from
 * [crackArms]), re-anchored at the normalized `(originX, originY)`, revealed up to [progress] `0..1`.
 * Each arm draws its segments up to `progress * segments`, with the leading segment interpolated, so the
 * cracks spread outward. Two passes — the wide dark shadow first, then the bright crack line on top —
 * both with round caps. [boardW]/[boardH] are the pixel size the normalized points map onto.
 */
fun DrawScope.drawOGCrackFrame(
    spec: OGCrackSpec,
    arms: List<OGCrackArm>,
    originX: Float,
    originY: Float,
    progress: Float,
    boardW: Float,
    boardH: Float,
) {
    if (progress <= 0f || arms.isEmpty() || boardW <= 0f || boardH <= 0f) return
    val s = spec.sanitized()
    val p = progress.coerceAtMost(1f)
    val minSide = min(boardW, boardH)
    val shadow = s.shadowColor.toSpecColor() ?: Color.Black
    val crack = s.crackColor.toSpecColor() ?: Color.White
    // Re-anchor: arms were built around their first arm's first point; shift so that lands on the origin.
    val base = arms.first().points.first()
    val dx = originX - base.x
    val dy = originY - base.y

    fun pass(color: Color, widthFrac: Float) {
        val stroke = (widthFrac * minSide).coerceAtLeast(1f)
        for (arm in arms) {
            val pts = arm.points
            val totalSegs = pts.size - 1
            if (totalSegs < 1) continue
            val drawn = p * totalSegs
            val full = floor(drawn).toInt()
            fun at(idx: Int) = Offset((pts[idx].x + dx) * boardW, (pts[idx].y + dy) * boardH)
            for (seg in 0 until min(full, totalSegs)) {
                drawLine(color, at(seg), at(seg + 1), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            val frac = drawn - full
            if (full < totalSegs && frac > 0f) {
                val a = at(full)
                val b = at(full + 1)
                val tip = Offset(OGFxMath.lerp(a.x, b.x, frac), OGFxMath.lerp(a.y, b.y, frac))
                drawLine(color, a, tip, strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }

    pass(shadow, s.shadowWidth) // depth underlay first
    pass(crack, s.crackWidth) // bright line on top
}
