package com.solidkey.painpoints.control

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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

/** The family of non-uniform 3D die-cast sculpted game tokens. */
enum class OGMedallionShape {
    /** 3D sculpted chiseled Play Triangle (▶) with raised perimeter bevel and center ridge lines. */
    PLAY_TRIANGLE,
    /** 12-point scalloped star medal with concentric toroidal rim and 3D faceted star. */
    SCALLOP_STAR,
    /** Molded circular coin with concentric raised rims and embossed return arc (↺). */
    RETURN_COIN,
    /** Two physical overlapping wooden/ivory game tiles with exchange arrows (⇄). */
    OVERLAPPING_TILES,
    /** Sculpted forward chevron arrow plaque with metallic bevel edges (⏭). */
    CHEVRON_PLAQUE,
    /** Sculpted handheld magnifying glass with angled handle and glass lens. */
    MAGNIFYING_GLASS,
}

private fun Color.shift(f: Float): Color {
    return if (f >= 0f) {
        Color(
            red = (red + (1f - red) * f).coerceIn(0f, 1f),
            green = (green + (1f - green) * f).coerceIn(0f, 1f),
            blue = (blue + (1f - blue) * f).coerceIn(0f, 1f),
            alpha = alpha,
        )
    } else {
        val dark = -f
        Color(
            red = (red * (1f - dark)).coerceIn(0f, 1f),
            green = (green * (1f - dark)).coerceIn(0f, 1f),
            blue = (blue * (1f - dark)).coerceIn(0f, 1f),
            alpha = alpha,
        )
    }
}

/**
 * A drop-in **3D Sculpted Game Medallion / Die-Cast Token** composable.
 * Unlike standard flat/rounded buttons, the physical silhouette of the token matches
 * its exact sculpted emblem (scalloped star medal, overlapping tiles, handheld glass, return coin).
 */
@Composable
fun OGMedallion(
    shape: OGMedallionShape,
    modifier: Modifier = Modifier,
    baseColor: Color = Color(0xFFC68A4C), // Rich warm bronze/gold default
    accentColor: Color = Color(0xFF10B981), // Emerald accent
    enabled: Boolean = true,
    isActive: Boolean = false, // e.g. for toggles like magnifier
    isArmed: Boolean = false,  // e.g. for Play word with tiles ready
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .pointerInput(enabled) {
                if (!enabled) {
                    detectTapGestures(
                        onTap = { onLongClick?.invoke() ?: onClick() },
                        onLongPress = { onLongClick?.invoke() },
                    )
                } else {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            val success = tryAwaitRelease()
                            isPressed = false
                            if (success) onClick()
                        },
                        onLongPress = { onLongClick?.invoke() },
                    )
                }
            }
            .graphicsLayer {
                val s = if (isPressed) 0.96f else 1f
                scaleX = s
                scaleY = s
                translationY = if (isPressed) 2f else 0f
                alpha = if (enabled) 1f else 0.50f
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val minDim = minOf(size.width, size.height)

            when (shape) {
                OGMedallionShape.PLAY_TRIANGLE -> {
                    draw3DPlayTriangleMedallion(
                        center = Offset(cx, cy),
                        size = minDim * 0.92f,
                        baseColor = if (isArmed) accentColor else baseColor,
                        isPressed = isPressed,
                        isArmed = isArmed,
                    )
                }
                OGMedallionShape.SCALLOP_STAR -> {
                    draw3DScallopedMedallion(
                        center = Offset(cx, cy),
                        radius = minDim * 0.48f,
                        baseColor = if (isArmed) Color(0xFFD4AF37) else baseColor,
                        coreColor = if (isArmed) accentColor else baseColor,
                        isPressed = isPressed,
                        isArmed = isArmed,
                    )
                }
                OGMedallionShape.RETURN_COIN -> {
                    draw3DReturnCoin(
                        center = Offset(cx, cy),
                        radius = minDim * 0.46f,
                        baseColor = baseColor,
                        isPressed = isPressed,
                    )
                }
                OGMedallionShape.OVERLAPPING_TILES -> {
                    draw3DOverlappingTiles(
                        center = Offset(cx, cy),
                        width = size.width * 0.90f,
                        height = size.height * 0.88f,
                        baseColor = baseColor,
                        isPressed = isPressed,
                    )
                }
                OGMedallionShape.CHEVRON_PLAQUE -> {
                    draw3DChevronPlaque(
                        center = Offset(cx, cy),
                        width = size.width * 0.92f,
                        height = size.height * 0.82f,
                        baseColor = baseColor,
                        isPressed = isPressed,
                    )
                }
                OGMedallionShape.MAGNIFYING_GLASS -> {
                    draw3DMagnifyingGlass(
                        center = Offset(cx, cy),
                        size = minDim * 0.90f,
                        baseColor = baseColor,
                        activeColor = accentColor,
                        isActive = isActive,
                        isPressed = isPressed,
                    )
                }
            }
        }
    }
}

/**
 * 1. 12-point Scalloped Star Medal with concentric 3D toroidal bevel rim and 3D faceted star.
 * Inspired by the die-cast molded reference medal.
 */
/**
 * 3D Sculpted Play Triangle button.
 * The physical silhouette is an embossed, chiseled Play Triangle (▶) with raised perimeter bevels,
 * recessed dish, and center ridge lines, built using the exact 3D die-cast technique.
 */
fun DrawScope.draw3DPlayTriangleMedallion(
    center: Offset,
    size: Float,
    baseColor: Color,
    isPressed: Boolean,
    isArmed: Boolean,
) {
    val w = size * 0.95f
    val h = size * 0.88f
    val cx = center.x + size * 0.04f
    val cy = center.y

    val pTip = Offset(cx + w * 0.44f, cy)
    val pTop = Offset(cx - w * 0.40f, cy - h * 0.48f)
    val pBot = Offset(cx - w * 0.40f, cy + h * 0.48f)
    val cr = 10f

    val triPath = Path().apply {
        moveTo(pTop.x + cr, pTop.y + cr * 0.3f)
        lineTo(pTip.x - cr * 1.2f, pTip.y - cr * 0.7f)
        quadraticTo(pTip.x, pTip.y, pTip.x - cr * 1.2f, pTip.y + cr * 0.7f)
        lineTo(pBot.x + cr, pBot.y - cr * 0.3f)
        quadraticTo(pBot.x, pBot.y, pBot.x, pBot.y - cr * 1.2f)
        lineTo(pTop.x, pTop.y + cr * 1.2f)
        quadraticTo(pTop.x, pTop.y, pTop.x + cr, pTop.y + cr * 0.3f)
        close()
    }

    if (!isPressed) {
        val shadowPath = Path().apply {
            moveTo(pTop.x + cr + 2f, pTop.y + cr * 0.3f + 3.5f)
            lineTo(pTip.x - cr * 1.2f + 2f, pTip.y - cr * 0.7f + 3.5f)
            quadraticTo(pTip.x + 2f, pTip.y + 3.5f, pTip.x - cr * 1.2f + 2f, pTip.y + cr * 0.7f + 3.5f)
            lineTo(pBot.x + cr + 2f, pBot.y - cr * 0.3f + 3.5f)
            quadraticTo(pBot.x + 2f, pBot.y + 3.5f, pBot.x + 2f, pBot.y - cr * 1.2f + 3.5f)
            lineTo(pTop.x + 2f, pTop.y + cr * 1.2f + 3.5f)
            quadraticTo(pTop.x + 2f, pTop.y + 3.5f, pTop.x + cr + 2f, pTop.y + cr * 0.3f + 3.5f)
            close()
        }
        drawPath(shadowPath, color = Color.Black.copy(alpha = 0.28f))
    }

    val baseGrad = Brush.linearGradient(
        colors = listOf(baseColor.shift(0.40f), baseColor, baseColor.shift(-0.35f)),
        start = Offset(cx - w * 0.5f, cy - h * 0.5f),
        end = Offset(cx + w * 0.5f, cy + h * 0.5f),
    )
    drawPath(triPath, brush = baseGrad)

    drawPath(
        triPath,
        color = baseColor.shift(-0.55f),
        style = Stroke(width = 4.5f, join = StrokeJoin.Round),
    )
    drawPath(
        triPath,
        color = Color.White.copy(alpha = 0.65f),
        style = Stroke(width = 1.8f, join = StrokeJoin.Round),
    )

    val apex = Offset(cx - w * 0.08f, cy)

    val upperFacet = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(pTop.x + cr, pTop.y + cr * 0.5f)
        lineTo(pTip.x - cr, pTip.y)
        close()
    }
    drawPath(upperFacet, color = baseColor.shift(0.28f))

    val lowerFacet = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(pTip.x - cr, pTip.y)
        lineTo(pBot.x + cr, pBot.y - cr * 0.5f)
        close()
    }
    drawPath(lowerFacet, color = baseColor.shift(-0.45f))

    val backFacet = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(pBot.x + cr, pBot.y - cr * 0.5f)
        lineTo(pTop.x + cr, pTop.y + cr * 0.5f)
        close()
    }
    drawPath(backFacet, color = baseColor.shift(-0.15f))

    drawLine(
        color = Color.White.copy(alpha = 0.85f),
        start = apex,
        end = Offset(pTip.x - cr, pTip.y),
        strokeWidth = 2.0f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color.White.copy(alpha = 0.65f),
        start = apex,
        end = Offset(pTop.x + cr, pTop.y + cr * 0.5f),
        strokeWidth = 1.5f,
        cap = StrokeCap.Round,
    )
}

fun DrawScope.draw3DScallopedMedallion(
    center: Offset,
    radius: Float,
    baseColor: Color,
    coreColor: Color,
    isPressed: Boolean,
    isArmed: Boolean,
) {
    val scallops = 12
    val rOuter = radius
    val rInner = radius * 0.86f
    val rPlate = radius * 0.72f
    val rTorus = radius * 0.65f
    val rStar = radius * 0.46f

    // Build the scalloped outer contour
    val scallopPath = Path().apply {
        val totalSteps = scallops * 2
        val stepAngle = 2f * PI.toFloat() / totalSteps
        for (i in 0 until totalSteps) {
            val a = i * stepAngle - (PI.toFloat() / 2f)
            val r = if (i % 2 == 0) rOuter else rInner
            val px = center.x + cos(a) * r
            val py = center.y + sin(a) * r
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }

    // A. Outer drop shadow
    if (!isPressed) {
        drawPath(
            scallopPath,
            color = Color.Black.copy(alpha = 0.28f),
            style = Stroke(width = 6f),
        )
    }

    // B. Scalloped metallic base surface with 135-deg light gradient
    val baseGrad = Brush.linearGradient(
        colors = listOf(baseColor.shift(0.35f), baseColor, baseColor.shift(-0.35f)),
        start = Offset(center.x - radius, center.y - radius),
        end = Offset(center.x + radius, center.y + radius),
    )
    drawPath(scallopPath, brush = baseGrad)

    // C. Molded outer ridge bevel following the scallop contour
    val bevelColorTop = Color.White.copy(alpha = 0.60f)
    val bevelColorBottom = baseColor.shift(-0.55f)
    drawPath(
        scallopPath,
        color = bevelColorBottom,
        style = Stroke(width = 4.5f, join = StrokeJoin.Round),
    )
    drawPath(
        scallopPath,
        color = bevelColorTop,
        style = Stroke(width = 2.0f, join = StrokeJoin.Round),
    )

    // D. Recessed concentric circular plate
    val plateGrad = Brush.radialGradient(
        colors = listOf(baseColor.shift(-0.15f), baseColor.shift(-0.38f)),
        center = center,
        radius = rPlate,
    )
    drawCircle(plateGrad, radius = rPlate, center = center)

    // E. Raised concentric toroidal ring / rim
    val ringGrad = Brush.linearGradient(
        colors = listOf(baseColor.shift(0.40f), baseColor.shift(-0.25f)),
        start = Offset(center.x - rTorus, center.y - rTorus),
        end = Offset(center.x + rTorus, center.y + rTorus),
    )
    drawCircle(ringGrad, radius = rTorus, center = center, style = Stroke(width = 5.5f))
    drawCircle(Color.White.copy(alpha = 0.50f), radius = rTorus + 2f, center = center, style = Stroke(width = 1.2f))
    drawCircle(baseColor.shift(-0.50f), radius = rTorus - 2f, center = center, style = Stroke(width = 1.2f))

    // F. 3D Faceted Star in the center!
    draw3DFacetedStar(
        center = center,
        outerRadius = rStar,
        innerRadius = rStar * 0.45f,
        points = 5,
        baseColor = coreColor,
    )
}

/**
 * Renders a true 3D Faceted Star where each point has a central ridge dividing it into
 * illuminated specular faces and shaded ambient faces.
 */
fun DrawScope.draw3DFacetedStar(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    points: Int = 5,
    baseColor: Color,
) {
    val stepAngle = 2f * PI.toFloat() / points
    val halfStep = stepAngle / 2f
    val baseAngle = -PI.toFloat() / 2f // Top point points straight up

    val litColor = baseColor.shift(0.45f)
    val midColor = baseColor.shift(0.12f)
    val shadeColor = baseColor.shift(-0.40f)
    val deepShadeColor = baseColor.shift(-0.55f)

    // Cast contact shadow beneath the star
    val shadowPath = Path()
    for (i in 0 until points) {
        val aTip = baseAngle + i * stepAngle
        val aValley = aTip + halfStep
        val tip = Offset(center.x + cos(aTip) * outerRadius + 1.5f, center.y + sin(aTip) * outerRadius + 2.5f)
        val valley = Offset(center.x + cos(aValley) * innerRadius + 1.5f, center.y + sin(aValley) * innerRadius + 2.5f)
        if (i == 0) shadowPath.moveTo(tip.x, tip.y) else shadowPath.lineTo(tip.x, tip.y)
        shadowPath.lineTo(valley.x, valley.y)
    }
    shadowPath.close()
    drawPath(shadowPath, color = Color.Black.copy(alpha = 0.35f))

    // Draw the 10 faceted triangles (2 per star arm, divided by the center ridge)
    for (i in 0 until points) {
        val aTip = baseAngle + i * stepAngle
        val aPrevValley = aTip - halfStep
        val aNextValley = aTip + halfStep

        val pTip = Offset(center.x + cos(aTip) * outerRadius, center.y + sin(aTip) * outerRadius)
        val pLeftValley = Offset(center.x + cos(aPrevValley) * innerRadius, center.y + sin(aPrevValley) * innerRadius)
        val pRightValley = Offset(center.x + cos(aNextValley) * innerRadius, center.y + sin(aNextValley) * innerRadius)

        // Light direction is top-left (-x, -y).
        // Left facet of top and left arms catches bright specular light:
        val isTopLeft = cos(aTip) <= 0.2f && sin(aTip) <= 0.2f

        // Facet 1: (Center, Tip, LeftValley)
        val pathLeft = Path().apply {
            moveTo(center.x, center.y)
            lineTo(pTip.x, pTip.y)
            lineTo(pLeftValley.x, pLeftValley.y)
            close()
        }
        drawPath(pathLeft, color = if (isTopLeft) litColor else midColor)

        // Facet 2: (Center, Tip, RightValley)
        val pathRight = Path().apply {
            moveTo(center.x, center.y)
            lineTo(pTip.x, pTip.y)
            lineTo(pRightValley.x, pRightValley.y)
            close()
        }
        drawPath(pathRight, color = if (isTopLeft) midColor else (if (i == 2 || i == 3) deepShadeColor else shadeColor))

        // Center ridge highlight line from center to tip
        drawLine(
            color = Color.White.copy(alpha = 0.55f),
            start = center,
            end = pTip,
            strokeWidth = 1.4f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 2. Molded circular coin with concentric raised rims and embossed return arc (↺).
 */
fun DrawScope.draw3DReturnCoin(
    center: Offset,
    radius: Float,
    baseColor: Color,
    isPressed: Boolean,
) {
    if (!isPressed) {
        drawCircle(Color.Black.copy(alpha = 0.22f), radius = radius, center = Offset(center.x + 2f, center.y + 3f))
    }

    // Outer coin surface with 135-deg bevel gradient
    val coinGrad = Brush.linearGradient(
        colors = listOf(baseColor.shift(0.30f), baseColor, baseColor.shift(-0.30f)),
        start = Offset(center.x - radius, center.y - radius),
        end = Offset(center.x + radius, center.y + radius),
    )
    drawCircle(coinGrad, radius = radius, center = center)

    // Concentric raised toroidal rim
    val rRim = radius * 0.88f
    drawCircle(baseColor.shift(0.40f), radius = rRim, center = center, style = Stroke(width = 3.5f))
    drawCircle(baseColor.shift(-0.45f), radius = rRim - 3.5f, center = center, style = Stroke(width = 1.2f))

    // Inner recessed plate
    drawCircle(baseColor.shift(-0.15f), radius = radius * 0.72f, center = center)

    // Embossed return arc arrow (↺)
    val rArc = radius * 0.44f
    val arcPath = Path().apply {
        // Draw 270-degree circular arc
        val arcRect = androidx.compose.ui.geometry.Rect(center.x - rArc, center.y - rArc, center.x + rArc, center.y + rArc)
        arcTo(arcRect, startAngleDegrees = -120f, sweepAngleDegrees = 270f, forceMoveTo = true)
    }
    // Shadow bevel under arrow
    drawPath(arcPath, color = baseColor.shift(-0.50f), style = Stroke(width = 4.5f, cap = StrokeCap.Round))
    drawPath(arcPath, color = Color.White.copy(alpha = 0.75f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))

    // Arrowhead at the end of the arc
    val aHead = -120f * (PI.toFloat() / 180f)
    val hx = center.x + cos(aHead) * rArc
    val hy = center.y + sin(aHead) * rArc
    val arrowHead = Path().apply {
        moveTo(hx - 8f, hy - 4f)
        lineTo(hx + 1f, hy)
        lineTo(hx - 2f, hy + 8f)
    }
    drawPath(arrowHead, color = Color.White.copy(alpha = 0.85f), style = Stroke(width = 2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * 3. Two physical overlapping wooden/ivory game tiles with exchange arrows (⇄).
 */
fun DrawScope.draw3DOverlappingTiles(
    center: Offset,
    width: Float,
    height: Float,
    baseColor: Color,
    isPressed: Boolean,
) {
    val tileW = width * 0.65f
    val tileH = height * 0.75f
    val cr = 8f

    // Tile 1 (Bottom tile, shifted top-left)
    val t1Left = center.x - width * 0.42f
    val t1Top = center.y - height * 0.44f
    if (!isPressed) {
        drawRoundRect(Color.Black.copy(alpha = 0.20f), topLeft = Offset(t1Left + 2f, t1Top + 3f), size = Size(tileW, tileH), cornerRadius = CornerRadius(cr))
    }
    drawRoundRect(
        Brush.linearGradient(listOf(baseColor.shift(0.20f), baseColor.shift(-0.15f)), Offset(t1Left, t1Top), Offset(t1Left + tileW, t1Top + tileH)),
        topLeft = Offset(t1Left, t1Top),
        size = Size(tileW, tileH),
        cornerRadius = CornerRadius(cr),
    )
    drawRoundRect(baseColor.shift(-0.40f), topLeft = Offset(t1Left, t1Top), size = Size(tileW, tileH), cornerRadius = CornerRadius(cr), style = Stroke(width = 2f))

    // Tile 2 (Top tile, shifted bottom-right, overlapping Tile 1)
    val t2Left = center.x - width * 0.22f
    val t2Top = center.y - height * 0.26f
    if (!isPressed) {
        drawRoundRect(Color.Black.copy(alpha = 0.32f), topLeft = Offset(t2Left + 2f, t2Top + 3.5f), size = Size(tileW, tileH), cornerRadius = CornerRadius(cr))
    }
    drawRoundRect(
        Brush.linearGradient(listOf(baseColor.shift(0.35f), baseColor, baseColor.shift(-0.25f)), Offset(t2Left, t2Top), Offset(t2Left + tileW, t2Top + tileH)),
        topLeft = Offset(t2Left, t2Top),
        size = Size(tileW, tileH),
        cornerRadius = CornerRadius(cr),
    )
    drawRoundRect(Color.White.copy(alpha = 0.60f), topLeft = Offset(t2Left + 1f, t2Top + 1f), size = Size(tileW - 2f, tileH - 2f), cornerRadius = CornerRadius(cr - 1f), style = Stroke(width = 1.2f))
    drawRoundRect(baseColor.shift(-0.45f), topLeft = Offset(t2Left, t2Top), size = Size(tileW, tileH), cornerRadius = CornerRadius(cr), style = Stroke(width = 2f))

    // Embossed exchange arrows (⇄) on Tile 2
    val mid2X = t2Left + tileW / 2f
    val mid2Y = t2Top + tileH / 2f
    val arrowLen = tileW * 0.28f

    // Top arrow (pointing right)
    val y1 = mid2Y - 5f
    drawLine(Color.White.copy(alpha = 0.85f), Offset(mid2X - arrowLen, y1), Offset(mid2X + arrowLen, y1), strokeWidth = 2.4f, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.85f), Offset(mid2X + arrowLen - 4f, y1 - 4f), Offset(mid2X + arrowLen, y1), strokeWidth = 2.4f, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.85f), Offset(mid2X + arrowLen - 4f, y1 + 4f), Offset(mid2X + arrowLen, y1), strokeWidth = 2.4f, cap = StrokeCap.Round)

    // Bottom arrow (pointing left)
    val y2 = mid2Y + 5f
    drawLine(Color.White.copy(alpha = 0.85f), Offset(mid2X + arrowLen, y2), Offset(mid2X - arrowLen, y2), strokeWidth = 2.4f, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.85f), Offset(mid2X - arrowLen + 4f, y2 - 4f), Offset(mid2X - arrowLen, y2), strokeWidth = 2.4f, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.85f), Offset(mid2X - arrowLen + 4f, y2 + 4f), Offset(mid2X - arrowLen, y2), strokeWidth = 2.4f, cap = StrokeCap.Round)
}

/**
 * 4. Sculpted forward chevron arrow plaque with metallic bevel edges (⏭).
 */
fun DrawScope.draw3DChevronPlaque(
    center: Offset,
    width: Float,
    height: Float,
    baseColor: Color,
    isPressed: Boolean,
) {
    val cr = height * 0.42f
    val left = center.x - width / 2f
    val top = center.y - height / 2f

    if (!isPressed) {
        drawRoundRect(Color.Black.copy(alpha = 0.22f), topLeft = Offset(left + 2f, top + 3f), size = Size(width, height), cornerRadius = CornerRadius(cr))
    }

    // Base surface with horizontal/diagonal bevel
    drawRoundRect(
        Brush.linearGradient(listOf(baseColor.shift(0.25f), baseColor, baseColor.shift(-0.25f)), Offset(left, top), Offset(left + width, top + height)),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(cr),
    )
    drawRoundRect(Color.White.copy(alpha = 0.50f), topLeft = Offset(left + 1f, top + 1f), size = Size(width - 2f, height - 2f), cornerRadius = CornerRadius(cr - 1f), style = Stroke(width = 1.2f))
    drawRoundRect(baseColor.shift(-0.45f), topLeft = Offset(left, top), size = Size(width, height), cornerRadius = CornerRadius(cr), style = Stroke(width = 2f))

    // Embossed double skip chevrons (⏭)
    val iconColor = Color.White.copy(alpha = 0.85f)
    val chevH = height * 0.28f
    val offset1 = -7f
    val offset2 = 2f
    val barX = 11f

    // Chevron 1
    drawLine(iconColor, Offset(center.x + offset1 - 5f, center.y - chevH), Offset(center.x + offset1 + 1f, center.y), strokeWidth = 2.6f, cap = StrokeCap.Round)
    drawLine(iconColor, Offset(center.x + offset1 + 1f, center.y), Offset(center.x + offset1 - 5f, center.y + chevH), strokeWidth = 2.6f, cap = StrokeCap.Round)

    // Chevron 2
    drawLine(iconColor, Offset(center.x + offset2 - 5f, center.y - chevH), Offset(center.x + offset2 + 1f, center.y), strokeWidth = 2.6f, cap = StrokeCap.Round)
    drawLine(iconColor, Offset(center.x + offset2 + 1f, center.y), Offset(center.x + offset2 - 5f, center.y + chevH), strokeWidth = 2.6f, cap = StrokeCap.Round)

    // End vertical bar
    drawLine(iconColor, Offset(center.x + barX, center.y - chevH), Offset(center.x + barX, center.y + chevH), strokeWidth = 2.6f, cap = StrokeCap.Round)
}

/**
 * 5. Sculpted handheld magnifying glass with angled handle and glass lens.
 */
fun DrawScope.draw3DMagnifyingGlass(
    center: Offset,
    size: Float,
    baseColor: Color,
    activeColor: Color,
    isActive: Boolean,
    isPressed: Boolean,
) {
    val lensR = size * 0.32f
    val lensC = Offset(center.x - size * 0.10f, center.y - size * 0.10f)
    val handleLen = size * 0.38f

    // Contact shadow
    if (!isPressed) {
        drawCircle(Color.Black.copy(alpha = 0.22f), radius = lensR, center = Offset(lensC.x + 2f, lensC.y + 3f))
        drawLine(Color.Black.copy(alpha = 0.22f), Offset(lensC.x + lensR * 0.707f + 2f, lensC.y + lensR * 0.707f + 3f), Offset(center.x + handleLen + 2f, center.y + handleLen + 3f), strokeWidth = 7f, cap = StrokeCap.Round)
    }

    // Sculpted brass/metallic handle
    val handleStart = Offset(lensC.x + lensR * 0.707f, lensC.y + lensR * 0.707f)
    val handleEnd = Offset(center.x + handleLen, center.y + handleLen)
    drawLine(baseColor.shift(-0.35f), handleStart, handleEnd, strokeWidth = 7.5f, cap = StrokeCap.Round)
    drawLine(Brush.linearGradient(listOf(baseColor.shift(0.35f), baseColor), handleStart, handleEnd), handleStart, handleEnd, strokeWidth = 5.5f, cap = StrokeCap.Round)

    // Toroidal metallic lens rim
    val rimGrad = Brush.linearGradient(
        colors = listOf(baseColor.shift(0.40f), baseColor, baseColor.shift(-0.40f)),
        start = Offset(lensC.x - lensR, lensC.y - lensR),
        end = Offset(lensC.x + lensR, lensC.y + lensR),
    )
    drawCircle(rimGrad, radius = lensR, center = lensC, style = Stroke(width = 6f))
    drawCircle(Color.White.copy(alpha = 0.60f), radius = lensR + 2.5f, center = lensC, style = Stroke(width = 1.2f))
    drawCircle(baseColor.shift(-0.50f), radius = lensR - 2.5f, center = lensC, style = Stroke(width = 1.2f))

    // Glass lens center
    val glassColor = if (isActive) activeColor.copy(alpha = 0.85f) else Color(0x33FFFFFF)
    drawCircle(glassColor, radius = lensR - 3f, center = lensC)

    // Glass reflection glare crescent
    drawCircle(Color.White.copy(alpha = if (isActive) 0.50f else 0.25f), radius = lensR * 0.60f, center = Offset(lensC.x - lensR * 0.22f, lensC.y - lensR * 0.22f))

    // Plus sign inside lens
    val glyphColor = if (isActive) Color.White else baseColor.shift(-0.50f)
    val plus = lensR * 0.40f
    drawLine(glyphColor, Offset(lensC.x - plus, lensC.y), Offset(lensC.x + plus, lensC.y), strokeWidth = 2.4f, cap = StrokeCap.Round)
    drawLine(glyphColor, Offset(lensC.x, lensC.y - plus), Offset(lensC.x, lensC.y + plus), strokeWidth = 2.4f, cap = StrokeCap.Round)
}
