package com.solidkey.painpoints.control

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A dimensional tactile **Game Card / HUD Placard** primitive.
 * Bundles:
 *  - 3D bottom extrusion bevel lip for physical game depth.
 *  - Top surface with optional glossy highlight chamfer.
 *  - Sculpted border rim with optional active glow halo (e.g. active turn indicator).
 *
 * Used for:
 *  - Player vs Rival Duel Scoreboard placards
 *  - Living Board Mat frame
 *  - Event Broadcast ticker chips
 *  - Tile Rack trays
 */
@Composable
fun OGGameCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    lipColor: Color? = null,
    borderColor: Color? = null,
    glowColor: Color? = null,
    cornerRadius: Dp = 14.dp,
    depth: Dp = 4.dp,
    borderWidth: Dp = 1.5.dp,
    highlight: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val cornerRadiusPx = cornerRadius
    val depthPx = depth

    Box(
        modifier = modifier
            .drawBehind {
                val cr = cornerRadiusPx.toPx()
                val d = depthPx.toPx()
                val w = size.width
                val h = size.height

                // 1. Optional active glow halo behind the card
                if (glowColor != null && glowColor != Color.Transparent) {
                    val haloR = 6.dp.toPx()
                    drawRoundRect(
                        color = glowColor.copy(alpha = 0.35f),
                        topLeft = Offset(-haloR * 0.5f, -haloR * 0.5f),
                        size = Size(w + haloR, h + haloR),
                        cornerRadius = CornerRadius(cr + haloR * 0.5f),
                    )
                }

                // 2. Bottom 3D extrusion lip
                if (d > 0f) {
                    val actualLip = lipColor ?: Color(
                        red = (backgroundColor.red * 0.75f).coerceIn(0f, 1f),
                        green = (backgroundColor.green * 0.75f).coerceIn(0f, 1f),
                        blue = (backgroundColor.blue * 0.75f).coerceIn(0f, 1f),
                        alpha = backgroundColor.alpha,
                    )
                    drawRoundRect(
                        color = actualLip,
                        topLeft = Offset(0f, 0f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(cr),
                    )
                }

                // 3. Top surface face
                val faceH = (h - d).coerceAtLeast(0f)
                drawRoundRect(
                    color = backgroundColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(w, faceH),
                    cornerRadius = CornerRadius(cr),
                )

                // 4. Subtle glossy highlight chamfer along the top edge
                if (highlight && faceH > 0f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.02f)),
                            startY = 0f,
                            endY = faceH * 0.45f,
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(w, faceH * 0.45f),
                        cornerRadius = CornerRadius(cr),
                    )
                }

                // 5. Sculpted border outline
                if (borderColor != null && borderWidth > 0.dp) {
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(w, faceH),
                        cornerRadius = CornerRadius(cr),
                        style = Stroke(width = borderWidth.toPx()),
                    )
                }
            }
            .clip(RoundedCornerShape(cornerRadius))
            .padding(bottom = depth),
        content = content,
    )
}
