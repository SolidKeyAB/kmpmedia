package com.solidkey.painpoints.control

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Palette definitions for 3D metallic sculpted badges (chiseled highlights, base metal, cast shadows).
 */
enum class OGBadgeMaterial(
    val base: Color,
    val light: Color,
    val dark: Color,
    val deep: Color,
) {
    /** Warm burnished bronze — matches physical cast medals. */
    BRONZE(
        base = Color(0xFFB86B35),
        light = Color(0xFFFFD4A3),
        dark = Color(0xFF6B3514),
        deep = Color(0xFF331606),
    ),
    /** Radiant imperial gold. */
    GOLD(
        base = Color(0xFFD4AF37),
        light = Color(0xFFFFF59D),
        dark = Color(0xFF8D6E63),
        deep = Color(0xFF3E2723),
    ),
    /** Rich jewel-tone jade / emerald — perfect for primary action/play medals. */
    EMERALD(
        base = Color(0xFF00897B),
        light = Color(0xFFA7F3D0),
        dark = Color(0xFF004D40),
        deep = Color(0xFF00251A),
    ),
    /** Polished chrome / silver. */
    SILVER(
        base = Color(0xFFB0BEC5),
        light = Color(0xFFFFFFFF),
        dark = Color(0xFF546E7A),
        deep = Color(0xFF263238),
    ),
    /** Soft ceramic ivory. */
    IVORY(
        base = Color(0xFFEBE6F5),
        light = Color(0xFFFFFFFF),
        dark = Color(0xFFBDB4D0),
        deep = Color(0xFF8C82A2),
    ),
}

/**
 * Geometric silhouette families for 3D sculpted badges.
 */
enum class OGBadgeSilhouette {
    /** 12-point scalloped star rosette — the classic embossed medal shape. */
    ROSETTE_12,
    /** Smooth circular coin medallion. */
    COIN,
    /** Stadium pill token with rounded caps. */
    STADIUM,
}

/**
 * An interactive, 3D sculpted **Embossed Badge / Medallion Button**.
 * Free of rectangular button boxes: the button's visible shape IS the physical sculpted medal itself.
 */
@Composable
fun OGBadgeButton(
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    silhouette: OGBadgeSilhouette = OGBadgeSilhouette.ROSETTE_12,
    material: OGBadgeMaterial = OGBadgeMaterial.BRONZE,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    emblem: (DrawScope.(cx: Float, cy: Float, radius: Float) -> Unit)? = null,
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (enabled) {
                            isPressed = true
                            val success = tryAwaitRelease()
                            isPressed = false
                            if (success) onClick()
                        } else {
                            onLongClick?.invoke()
                        }
                    },
                    onLongPress = {
                        onLongClick?.invoke()
                    },
                )
            }
            .graphicsLayer {
                val s = if (isPressed) 0.96f else 1f
                scaleX = s
                scaleY = s
                translationY = if (isPressed) 2.5f else 0f
                alpha = if (enabled) 1f else 0.50f
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f
            val r = minOf(w, h) / 2f * 0.90f

            // 1. Cast shadow behind the medal
            if (!isPressed && enabled) {
                drawBadgeShadow(cx, cy + 3.5.dp.toPx(), r, silhouette)
            }

            // 2. Outer sculpted medal body & beveled rim
            drawBadgeBody(cx, cy, r, silhouette, material)

            // 3. Inner raised bezel ring (torus)
            val torusR = r * 0.68f
            val torusW = r * 0.14f
            drawBezelTorus(cx, cy, torusR, torusW, material)

            // 4. Center recessed dish floor
            val dishR = torusR - torusW / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(material.base, material.dark, material.deep),
                    center = Offset(cx, cy),
                    radius = dishR,
                ),
                radius = dishR,
                center = Offset(cx, cy),
            )

            // 5. 3D chiseled center emblem
            if (emblem != null) {
                emblem(cx, cy, dishR * 0.88f)
            }
        }
    }
}

/** Draws the directional cast shadow beneath the medal. */
private fun DrawScope.drawBadgeShadow(cx: Float, cy: Float, r: Float, silhouette: OGBadgeSilhouette) {
    val shadowColor = Color(0x3512081C)
    when (silhouette) {
        OGBadgeSilhouette.ROSETTE_12 -> {
            val path = buildRosettePath(cx, cy, r, points = 12, innerRatio = 0.82f)
            drawPath(path, color = shadowColor)
        }
        OGBadgeSilhouette.COIN -> {
            drawCircle(shadowColor, radius = r, center = Offset(cx, cy))
        }
        OGBadgeSilhouette.STADIUM -> {
            drawRoundRect(
                color = shadowColor,
                topLeft = Offset(cx - r * 1.15f, cy - r),
                size = Size(r * 2.3f, r * 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
            )
        }
    }
}

/** Draws the main metallic outer body and raised perimeter bevel. */
private fun DrawScope.drawBadgeBody(
    cx: Float,
    cy: Float,
    r: Float,
    silhouette: OGBadgeSilhouette,
    material: OGBadgeMaterial,
) {
    val metallicBrush = Brush.linearGradient(
        colors = listOf(material.light, material.base, material.dark, material.deep),
        start = Offset(cx - r, cy - r),
        end = Offset(cx + r, cy + r),
    )

    when (silhouette) {
        OGBadgeSilhouette.ROSETTE_12 -> {
            val path = buildRosettePath(cx, cy, r, points = 12, innerRatio = 0.82f)
            drawPath(path, brush = metallicBrush)

            // Outer chiseled rim highlight stroke
            drawPath(
                path,
                brush = Brush.linearGradient(
                    colors = listOf(material.light, material.base, material.deep),
                    start = Offset(cx - r, cy - r),
                    end = Offset(cx + r, cy + r),
                ),
                style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round),
            )
        }
        OGBadgeSilhouette.COIN -> {
            drawCircle(brush = metallicBrush, radius = r, center = Offset(cx, cy))
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(material.light, material.base, material.deep),
                    start = Offset(cx - r, cy - r),
                    end = Offset(cx + r, cy + r),
                ),
                radius = r - 1.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        OGBadgeSilhouette.STADIUM -> {
            val w = r * 2.3f
            val h = r * 2f
            val topLeft = Offset(cx - w / 2f, cy - h / 2f)
            drawRoundRect(
                brush = metallicBrush,
                topLeft = topLeft,
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f),
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(material.light, material.base, material.deep),
                    start = Offset(cx - w / 2f, cy - h / 2f),
                    end = Offset(cx + w / 2f, cy + h / 2f),
                ),
                topLeft = topLeft,
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

/** Draws the raised circular bezel torus ring with directional 3D metallic highlights. */
private fun DrawScope.drawBezelTorus(
    cx: Float,
    cy: Float,
    torusR: Float,
    torusW: Float,
    material: OGBadgeMaterial,
) {
    // Outer tube base
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(material.light, material.base, material.dark, material.deep),
            start = Offset(cx - torusR, cy - torusR),
            end = Offset(cx + torusR, cy + torusR),
        ),
        radius = torusR,
        center = Offset(cx, cy),
        style = Stroke(width = torusW),
    )

    // Specular chrome glint on top-left quadrant of the torus
    val gleamBrush = Brush.sweepGradient(
        colors = listOf(
            material.base,
            material.light,
            material.base,
            material.dark,
            material.deep,
            material.base,
        ),
        center = Offset(cx, cy),
    )
    drawCircle(
        brush = gleamBrush,
        radius = torusR,
        center = Offset(cx, cy),
        style = Stroke(width = torusW * 0.40f),
    )
}

/** Builds the 12-point scalloped star rosette path (matches the reference image). */
fun buildRosettePath(cx: Float, cy: Float, outerR: Float, points: Int = 12, innerRatio: Float = 0.82f): Path {
    val path = Path()
    val innerR = outerR * innerRatio
    val step = (2.0 * PI / points).toFloat()
    val halfStep = step / 2f

    for (i in 0 until points) {
        val aTip = -PI.toFloat() / 2f + i * step
        val aVal = aTip + halfStep
        val tipX = cx + cos(aTip) * outerR
        val tipY = cy + sin(aTip) * outerR
        val valX = cx + cos(aVal) * innerR
        val valY = cy + sin(aVal) * innerR

        if (i == 0) {
            path.moveTo(tipX, tipY)
        } else {
            path.lineTo(tipX, tipY)
        }
        path.lineTo(valX, valY)
    }
    path.close()
    return path
}

// ── 3D Chiseled Emblem Drawing Helpers ────────────────────────────────────────

/**
 * Draws a 3D faceted, chiseled 5-pointed star emblem directly into the medal dish.
 * Exactly matches the chiseled star in the reference image.
 */
fun DrawScope.draw3DChiseledStar(
    cx: Float,
    cy: Float,
    radius: Float,
    material: OGBadgeMaterial,
    innerRatio: Float = 0.44f,
) {
    val points = 5
    val step = (2.0 * PI / points).toFloat()
    val rOuter = radius
    val rInner = radius * innerRatio

    // Pre-calculate tip and valley vertices
    val tips = List(points) { i ->
        val a = -PI.toFloat() / 2f + i * step
        Offset(cx + cos(a) * rOuter, cy + sin(a) * rOuter)
    }
    val valleys = List(points) { i ->
        val a = -PI.toFloat() / 2f + i * step + step / 2f
        Offset(cx + cos(a) * rInner, cy + sin(a) * rInner)
    }

    // 1. Cast shadow beneath the star
    val shadowPath = Path().apply {
        moveTo(tips[0].x, tips[0].y + 2.dp.toPx())
        for (i in 0 until points) {
            val v = valleys[i]
            val nextTip = tips[(i + 1) % points]
            lineTo(v.x, v.y + 2.dp.toPx())
            lineTo(nextTip.x, nextTip.y + 2.dp.toPx())
        }
        close()
    }
    drawPath(shadowPath, color = material.deep.copy(alpha = 0.60f))

    // 2. Draw 10 triangular facets meeting at the center apex
    val center = Offset(cx, cy)
    for (i in 0 until points) {
        val tip = tips[i]
        val vLeft = valleys[(i + points - 1) % points]
        val vRight = valleys[i]

        // Left facet (faces towards top-left light source: bright highlight)
        val leftFacet = Path().apply {
            moveTo(center.x, center.y)
            lineTo(vLeft.x, vLeft.y)
            lineTo(tip.x, tip.y)
            close()
        }
        val leftLight = if (i == 0 || i == 4) material.light else material.base
        drawPath(leftFacet, color = leftLight)

        // Right facet (faces towards bottom-right: deep chiseled bronze shadow)
        val rightFacet = Path().apply {
            moveTo(center.x, center.y)
            lineTo(tip.x, tip.y)
            lineTo(vRight.x, vRight.y)
            close()
        }
        val rightShade = if (i == 1 || i == 2) material.deep else material.dark
        drawPath(rightFacet, color = rightShade)

        // Specular apex-to-tip ridge line
        drawLine(
            color = material.light.copy(alpha = 0.85f),
            start = center,
            end = tip,
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Draws a 3D faceted, chiseled Play Triangle `▶` emblem. */
fun DrawScope.draw3DChiseledPlayTriangle(
    cx: Float,
    cy: Float,
    radius: Float,
    material: OGBadgeMaterial,
) {
    val pTip = Offset(cx + radius * 0.85f, cy)
    val pTop = Offset(cx - radius * 0.65f, cy - radius * 0.75f)
    val pBot = Offset(cx - radius * 0.65f, cy + radius * 0.75f)
    val apex = Offset(cx - radius * 0.15f, cy)

    // Top facet (facing light)
    val topFacet = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(pTop.x, pTop.y)
        lineTo(pTip.x, pTip.y)
        close()
    }
    drawPath(topFacet, color = material.light)

    // Bottom facet (shadow)
    val botFacet = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(pTip.x, pTip.y)
        lineTo(pBot.x, pBot.y)
        close()
    }
    drawPath(botFacet, color = material.deep)

    // Left back facet
    val backFacet = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(pBot.x, pBot.y)
        lineTo(pTop.x, pTop.y)
        close()
    }
    drawPath(backFacet, color = material.dark)

    // Ridge lines
    drawLine(material.light, apex, pTip, strokeWidth = 1.4.dp.toPx())
    drawLine(material.light, apex, pTop, strokeWidth = 1.4.dp.toPx())
    drawLine(material.deep, apex, pBot, strokeWidth = 1.4.dp.toPx())
}

/** Draws a 3D faceted, chiseled Return Arc `↺` emblem. */
fun DrawScope.draw3DChiseledReturnArc(
    cx: Float,
    cy: Float,
    radius: Float,
    material: OGBadgeMaterial,
) {
    val arcR = radius * 0.65f
    val strokeW = radius * 0.28f

    // Arc ring with 3D gradient
    drawArc(
        brush = Brush.sweepGradient(
            colors = listOf(material.dark, material.light, material.base, material.deep, material.dark),
            center = Offset(cx, cy),
        ),
        startAngle = 45f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = Offset(cx - arcR, cy - arcR),
        size = Size(arcR * 2f, arcR * 2f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round),
    )

    // Arrowhead at top-left
    val arrowTip = Offset(cx - arcR * 0.95f, cy - arcR * 0.55f)
    val pTop = Offset(arrowTip.x - strokeW * 0.8f, arrowTip.y - strokeW * 0.8f)
    val pBot = Offset(arrowTip.x + strokeW * 0.5f, arrowTip.y - strokeW * 1.1f)

    val arrowPath = Path().apply {
        moveTo(arrowTip.x, arrowTip.y)
        lineTo(pTop.x, pTop.y)
        lineTo(pBot.x, pBot.y)
        close()
    }
    drawPath(arrowPath, color = material.light)
}

/** Draws 3D faceted, chiseled Fast-Forward Chevrons `⏭` emblem. */
fun DrawScope.draw3DChiseledChevrons(
    cx: Float,
    cy: Float,
    radius: Float,
    material: OGBadgeMaterial,
) {
    val h = radius * 0.80f
    val w = radius * 0.40f

    // First chevron
    drawSingleChevron(cx - w * 0.6f, cy, w, h, material)
    // Second chevron
    drawSingleChevron(cx + w * 0.4f, cy, w, h, material)
    // Vertical stop bar
    drawLine(
        color = material.light,
        start = Offset(cx + w * 1.0f, cy - h / 2f),
        end = Offset(cx + w * 1.0f, cy + h / 2f),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawSingleChevron(cx: Float, cy: Float, w: Float, h: Float, material: OGBadgeMaterial) {
    val topFacet = Path().apply {
        moveTo(cx - w, cy - h / 2f)
        lineTo(cx, cy)
        lineTo(cx - w * 0.35f, cy)
        lineTo(cx - w * 1.35f, cy - h / 2f)
        close()
    }
    drawPath(topFacet, color = material.light)

    val botFacet = Path().apply {
        moveTo(cx, cy)
        lineTo(cx - w, cy + h / 2f)
        lineTo(cx - w * 1.35f, cy + h / 2f)
        lineTo(cx - w * 0.35f, cy)
        close()
    }
    drawPath(botFacet, color = material.deep)
}

/** Draws 3D faceted, chiseled Exchange Arrows `⇄` emblem. */
fun DrawScope.draw3DChiseledExchange(
    cx: Float,
    cy: Float,
    radius: Float,
    material: OGBadgeMaterial,
) {
    val w = radius * 0.85f
    val dy = radius * 0.35f
    val sw = radius * 0.22f

    // Top arrow (pointing right)
    drawLine(
        brush = Brush.horizontalGradient(listOf(material.base, material.light)),
        start = Offset(cx - w, cy - dy),
        end = Offset(cx + w, cy - dy),
        strokeWidth = sw,
        cap = StrokeCap.Round,
    )
    val tipR = Offset(cx + w, cy - dy)
    val pathR = Path().apply {
        moveTo(tipR.x, tipR.y)
        lineTo(tipR.x - sw * 1.2f, tipR.y - sw * 1.1f)
        lineTo(tipR.x - sw * 1.2f, tipR.y + sw * 1.1f)
        close()
    }
    drawPath(pathR, color = material.light)

    // Bottom arrow (pointing left)
    drawLine(
        brush = Brush.horizontalGradient(listOf(material.light, material.base)),
        start = Offset(cx + w, cy + dy),
        end = Offset(cx - w, cy + dy),
        strokeWidth = sw,
        cap = StrokeCap.Round,
    )
    val tipL = Offset(cx - w, cy + dy)
    val pathL = Path().apply {
        moveTo(tipL.x, tipL.y)
        lineTo(tipL.x + sw * 1.2f, tipL.y - sw * 1.1f)
        lineTo(tipL.x + sw * 1.2f, tipL.y + sw * 1.1f)
        close()
    }
    drawPath(pathL, color = material.deep)
}

/** Draws a 3D sculpted handheld magnifying glass tool with brass rim and glass lens. */
fun DrawScope.draw3DHandheldMagnifier(
    cx: Float,
    cy: Float,
    radius: Float,
    material: OGBadgeMaterial,
    active: Boolean = false,
) {
    val lensR = radius * 0.58f
    val lensC = Offset(cx - radius * 0.15f, cy - radius * 0.15f)
    val rimW = radius * 0.22f

    // Glass lens dish
    val lensGlass = if (active) Color(0xFF00E5FF).copy(alpha = 0.50f) else Color(0x30FFFFFF)
    drawCircle(lensGlass, radius = lensR, center = lensC)

    // Raised brass/metallic lens bezel
    drawCircle(
        brush = Brush.linearGradient(
            listOf(material.light, material.base, material.deep),
            start = Offset(lensC.x - lensR, lensC.y - lensR),
            end = Offset(lensC.x + lensR, lensC.y + lensR),
        ),
        radius = lensR,
        center = lensC,
        style = Stroke(width = rimW),
    )

    // Inner chrome reflection arc
    drawArc(
        color = Color.White.copy(alpha = 0.85f),
        startAngle = 180f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(lensC.x - lensR * 0.7f, lensC.y - lensR * 0.7f),
        size = Size(lensR * 1.4f, lensR * 1.4f),
        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
    )

    // Sculpted angled handle
    val edge = Offset(lensC.x + lensR * 0.707f, lensC.y + lensR * 0.707f)
    val handleEnd = Offset(cx + radius * 0.88f, cy + radius * 0.88f)
    drawLine(
        brush = Brush.linearGradient(
            listOf(material.light, material.base, material.deep),
            start = edge,
            end = handleEnd,
        ),
        start = edge,
        end = handleEnd,
        strokeWidth = rimW * 1.35f,
        cap = StrokeCap.Round,
    )
}
