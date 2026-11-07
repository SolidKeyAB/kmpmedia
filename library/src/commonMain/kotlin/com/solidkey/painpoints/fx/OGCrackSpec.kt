package com.solidkey.painpoints.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * *spreads*. Rendering uses: a directional cast-shadow (depth relief), a tapered bright crack line,
 * an epicenter impact void, and leading tip sparks — plain `drawLine` / `drawPath` calls, no shader.
 * Geometry is pure + deterministic from [seed], so Android & iOS are frame-identical.
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
    /** Fraction to taper arm stroke root-to-tip (`0` = uniform, `0.85` tapers to 15% at tip). */
    val taper: Float = 0.75f,
    /** Dark chasm / void fill colour at the central impact crater. */
    val voidColor: String = "#000000",
    /** Radius of central impact void as fraction of minSide (`0` = disabled). */
    val voidRadius: Float = 0.035f,
    /** Deepness multiplier for the impact crater chasm. */
    val voidDepth: Float = 1.2f,
    /** Bright glow colour at the advancing crack tip. */
    val tipGlowColor: String = "#FFFFFF",
    /** Size of leading tip spark as fraction of minSide (`0` = disabled). */
    val tipGlowRadius: Float = 0.012f,
    /** Ambient light angle in degrees — shifts shadow pass for 3D crevice relief. */
    val lightAngleDeg: Float = 315f,
) {
    /** Clamp every field into its sane range (lenient model/designer input never breaks a frame). */
    fun sanitized(): OGCrackSpec = copy(
        arms = arms.coerceIn(2, 20),
        depth = depth.coerceIn(0, 3),
        length = length.coerceIn(0.05f, 1f),
        crackWidth = crackWidth.coerceIn(0.001f, 0.1f),
        shadowWidth = shadowWidth.coerceIn(0.001f, 0.2f),
        irregularity = irregularity.coerceIn(0f, 1f),
        taper = taper.coerceIn(0f, 0.95f),
        voidRadius = voidRadius.coerceIn(0f, 0.2f),
        voidDepth = voidDepth.coerceIn(0f, 5f),
        tipGlowRadius = tipGlowRadius.coerceIn(0f, 0.05f),
    )
}

/** One point on a crack arm, normalized `0..1` of the board/view. */
data class OGCrackPoint(val x: Float, val y: Float)

/**
 * One resolved crack arm: a list of [OGCrackPoint] from its start outward.
 * [startFraction] is the global `progress` value at which this arm becomes visible — 0f for main
 * arms (they start immediately), and `> 0f` for sub-branches (they only appear once the parent
 * trunk has grown past the branch spawn point).
 */
data class OGCrackArm(val points: List<OGCrackPoint>, val startFraction: Float = 0f)

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

/**
 * Grow one arm as a bending polyline, recursively spawning sub-branches while [depthLeft] allows.
 * [startFraction] is the global progress value at which this arm first becomes visible.
 */
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
    startFraction: Float = 0f,
) {
    var angle = startAngle
    var x = startX
    var y = startY
    val segLen = length / segCount
    val points = mutableListOf(OGCrackPoint(x, y))
    val branchAt = (segCount * 2) / 3
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
            val branchStart = startFraction + (branchAt.toFloat() / segCount) * (1f - startFraction)
            growArm(out, spec, r, x, y, angle + deviation, remaining * 0.5f, subSegs, depthLeft - 1, branchStart)
        }
        angle += r.nextFloat() * 44f * spec.irregularity - 22f * spec.irregularity
    }
    out += OGCrackArm(points, startFraction)
}

/**
 * Draw one frame of a growing fracture into the current [DrawScope]: the cached [arms] (from
 * [crackArms]), re-anchored at the normalized `(originX, originY)`, revealed up to [progress] `0..1`.
 *
 * Rendering layers (back to front):
 * 1. **Impact void** — an irregular filled polygon at the epicenter.
 * 2. **Cast-shadow pass** — shadow stroke offset along the ambient light angle for 3D relief.
 * 3. **Crack pass** — tapered bright crack line (root = full width, tip = 12% of root).
 * 4. **Tip sparks** — dual-circle glow at each advancing fracture tip while spreading.
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
    val voidCol = s.voidColor.toSpecColor() ?: Color.Black
    val tipCol = s.tipGlowColor.toSpecColor() ?: Color.White

    val base = arms.first().points.first()
    val dx = originX - base.x
    val dy = originY - base.y

    // Directional light offset for 3D cast-shadow relief
    val lightRad = s.lightAngleDeg * (PI.toFloat() / 180f)
    val shadowOffX = cos(lightRad) * (s.shadowWidth * minSide * 0.35f)
    val shadowOffY = sin(lightRad) * (s.shadowWidth * minSide * 0.35f)

    // 1. Central Impact Void (irregular polygon crater)
    if (s.voidRadius > 0f) {
        val voidGrow = (p * 2.5f).coerceAtMost(1f)
        val vr = s.voidRadius * minSide * voidGrow
        if (vr > 0.5f) {
            val voidPath = Path()
            val steps = 14
            for (i in 0 until steps) {
                val ang = i * (2f * PI.toFloat() / steps)
                val jitter = 0.8f + 0.4f * OGFxMath.hash01(i, s.seed)
                val vx = (originX * boardW) + cos(ang) * vr * jitter
                val vy = (originY * boardH) + sin(ang) * vr * jitter
                if (i == 0) voidPath.moveTo(vx, vy) else voidPath.lineTo(vx, vy)
            }
            voidPath.close()
            
            // Draw deep 3D chasm with multiple concentric layers falling into pure darkness
            drawPath(voidPath, color = shadow.copy(alpha = 0.8f))
            val layerCount = (4 * s.voidDepth).toInt().coerceIn(4, 12)
            for (layer in 1..layerCount) {
                val depthR = vr * (1f - layer * (0.22f / s.voidDepth))
                if (depthR > 0f) {
                    val dPath = Path()
                    val pX = shadowOffX * layer * 0.15f * s.voidDepth
                    val pY = shadowOffY * layer * 0.15f * s.voidDepth
                    for (i in 0 until steps) {
                        val ang = i * (2f * PI.toFloat() / steps)
                        val jitter = 0.8f + 0.4f * OGFxMath.hash01(i, s.seed + layer) // distinct jagged inner edge
                        val vx = (originX * boardW) + pX + cos(ang) * depthR * jitter
                        val vy = (originY * boardH) + pY + sin(ang) * depthR * jitter
                        if (i == 0) dPath.moveTo(vx, vy) else dPath.lineTo(vx, vy)
                    }
                    dPath.close()
                    val layerAlpha = (0.5f + (layer.toFloat() / layerCount) * 0.5f).coerceIn(0f, 1f)
                    drawPath(dPath, color = voidCol.copy(alpha = layerAlpha))
                }
            }
            drawPath(voidPath, color = shadow.copy(alpha = 0.8f), style = Stroke(width = s.crackWidth * minSide))
        }
    }

    // Collect tip positions for the spark pass
    val tipPositions = mutableListOf<Offset>()

    fun pass(color: Color, widthFrac: Float, offsetX: Float = 0f, offsetY: Float = 0f, recordTips: Boolean = false) {
        val baseStroke = widthFrac * minSide
        for (arm in arms) {
            if (p <= arm.startFraction) continue
            val armP = if (arm.startFraction >= 1f) 0f
                       else ((p - arm.startFraction) / (1f - arm.startFraction)).coerceIn(0f, 1f)
            val pts = arm.points
            val totalSegs = pts.size - 1
            if (totalSegs < 1) continue
            val drawn = armP * totalSegs
            val full = floor(drawn).toInt()

            fun at(idx: Int) = Offset(
                (pts[idx].x + dx) * boardW + offsetX,
                (pts[idx].y + dy) * boardH + offsetY,
            )

            // Sub-branches are 30% thinner than main trunks
            val branchScale = if (arm.startFraction > 0f) 0.70f else 1.0f

            for (seg in 0 until min(full, totalSegs)) {
                val segT = seg.toFloat() / totalSegs
                val taperMult = (1f - s.taper * segT).coerceAtLeast(0.12f) * branchScale
                val stroke = (baseStroke * taperMult).coerceAtLeast(1f)
                drawLine(color, at(seg), at(seg + 1), strokeWidth = stroke, cap = StrokeCap.Round)
            }

            val frac = drawn - full
            if (full < totalSegs && frac > 0f) {
                val a = at(full)
                val b = at(full + 1)
                val tip = Offset(OGFxMath.lerp(a.x, b.x, frac), OGFxMath.lerp(a.y, b.y, frac))
                val tipT = drawn / totalSegs
                val taperMult = (1f - s.taper * tipT).coerceAtLeast(0.12f) * branchScale
                val stroke = (baseStroke * taperMult).coerceAtLeast(1f)
                drawLine(color, a, tip, strokeWidth = stroke, cap = StrokeCap.Round)
                if (recordTips && p < 0.98f) {
                    tipPositions += Offset(
                        OGFxMath.lerp(a.x - offsetX, b.x - offsetX, frac),
                        OGFxMath.lerp(a.y - offsetY, b.y - offsetY, frac),
                    )
                }
            }
        }
    }

    // 2. Cast-shadow pass (offset for 3D depth)
    pass(shadow, s.shadowWidth, offsetX = shadowOffX, offsetY = shadowOffY)
    // 3. Main crack pass (tapered)
    pass(crack, s.crackWidth, recordTips = true)

    // 4. Leading Tip Sparks (fracture stress concentration)
    if (s.tipGlowRadius > 0f) {
        val sparkR = s.tipGlowRadius * minSide * (1f - p * 0.4f)
        for (tip in tipPositions) {
            drawCircle(tipCol.copy(alpha = 0.45f), radius = sparkR * 1.8f, center = tip)
            drawCircle(Color.White.copy(alpha = 0.90f), radius = sparkR * 0.7f, center = tip)
        }
    }
}
