package com.solidkey.painpoints.image.svg.animation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.solidkey.painpoints.image.svg.OGSVGCircle
import com.solidkey.painpoints.image.svg.OGSVGEllipse
import com.solidkey.painpoints.image.svg.OGSVGLine
import com.solidkey.painpoints.image.svg.OGSVGPath
import com.solidkey.painpoints.image.svg.OGSVGPolygon
import com.solidkey.painpoints.image.svg.OGSVGRect
import com.solidkey.painpoints.image.svg.OGSVGTreeElement

@Composable
fun RenderAnimatedSVG(svgTree: OGSVGTreeElement, animationState: OGSVGAnimationState) {
    val animatedValues = animationState.currentValues

    svgTree.children.forEach { element ->
        val animatedX = animatedValues[element.id]?.value ?: element.style.translateX ?: 0f
        val animatedY = animatedValues[element.id]?.value ?: element.style.translateY ?: 0f

        DrawSvgElement(element, Offset(animatedX, animatedY))
        RenderAnimatedSVG(element, animationState)
    }
}

@Composable
fun DrawSvgElement(element: OGSVGTreeElement, offset: Offset) {
    Canvas(modifier = Modifier.size(100.dp)) {
        when (val shape = element.shapes.firstOrNull()) {
            is OGSVGRect -> drawRect(Color.Black, topLeft = Offset(shape.x + offset.x, shape.y + offset.y))
            is OGSVGCircle -> drawCircle(Color.Black, radius = shape.r, center = Offset(shape.cx + offset.x, shape.cy + offset.y))
            is OGSVGPath -> drawPath(shape.toPath(), Color.Black)
            is OGSVGEllipse -> drawOval(
                Color.Black,
                topLeft = Offset(shape.cx - shape.rx + offset.x, shape.cy - shape.ry + offset.y),
                size = Size(shape.rx * 2f, shape.ry * 2f)
            )
            is OGSVGLine -> drawLine(
                Color.Black,
                start = Offset(shape.x1 + offset.x, shape.y1 + offset.y),
                end = Offset(shape.x2 + offset.x, shape.y2 + offset.y)
            )
            is OGSVGPolygon -> if (shape.points.isNotEmpty()) {
                val path = Path()
                val first = shape.points.first()
                path.moveTo(first.x + offset.x, first.y + offset.y)
                shape.points.drop(1).forEach { path.lineTo(it.x + offset.x, it.y + offset.y) }
                path.close()
                drawPath(path, Color.Black)
            }
            null -> Unit // no shape to draw
        }
    }
}
