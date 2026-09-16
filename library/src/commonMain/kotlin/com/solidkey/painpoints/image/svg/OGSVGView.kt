package com.solidkey.painpoints.image.svg

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.OGColor
import com.solidkey.painpoints.image.OGColorBase
import com.solidkey.painpoints.image.loading.OGParsedSVGResult
import com.solidkey.painpoints.image.loading.OGSvgLoader
import com.solidkey.painpoints.image.loading.ViewBox
import com.solidkey.painpoints.source.OGSource
import com.solidkey.painpoints.source.OGSourceType
import kotlin.math.absoluteValue
import kotlin.math.min

@Composable
fun OGSVGView(
    source: OGSourceType,
    width: Float,
    height: Float,
    modifier: Modifier = Modifier,
    enableDrag: Boolean = false,
    scalingBehavior: SVGScalingBehavior = SVGScalingBehavior.CLIP,
    followCommonPractices: Boolean = false, // New flag to follow common practices
    onError: ((String) -> Unit)? = null,
    onScaleComputed: ((Float) -> Unit)? = null  // Optional callback, default is null
) {
    var isSvgLoaded by remember { mutableStateOf(false) }
    var viewBox by remember { mutableStateOf<ViewBox?>(null) }
    var OGParsedSVGResult by remember { mutableStateOf<OGParsedSVGResult?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var renderShapes by remember { mutableStateOf<List<RenderShape>>(emptyList()) }

    val disableScrolling = rememberScrollableState { delta ->
        if (isDragging) 0f else delta
    }

    val location = source.getLocation() ?: run {
        onError?.invoke("❌ Failed to resolve SVG location.")
        return
    }

    val svgSource = when (source.getType()) {
        OGSourceType.SourceType.URL -> OGSource.Url(location)
        OGSourceType.SourceType.FILE -> OGSource.FilePath(location)
        OGSourceType.SourceType.RESOURCE -> OGSource.Resource(location)
    }

    OGSvgLoader.loadSvg(svgSource, width, height) { result ->
        if (result == null) {
            onError?.invoke("❌ Failed to load SVG from: $location")
            return@loadSvg
        }
        val svgTree = result.svgTree
        if (svgTree == null) {
            onError?.invoke("❌ SVG loaded but contained no renderable tree: $location")
            return@loadSvg
        }

        val resolvedViewBox = result.viewBox
        // Guard against a degenerate viewBox (e.g. viewBox="0 0 0 0") to avoid a
        // divide-by-zero that would produce an infinite/NaN scale.
        val vbWidth = resolvedViewBox.width.takeIf { it != 0f } ?: width
        val vbHeight = resolvedViewBox.height.takeIf { it != 0f } ?: height

        isSvgLoaded = true
        OGParsedSVGResult = result
        viewBox = resolvedViewBox

        renderShapes = prepareRenderShapes(
            svgTree = svgTree,
            scale = width / vbWidth,
            scaleX = width / vbWidth,
            scaleY = height / vbHeight,
            offsetX = 0f,
            offsetY = 0f,
            scalingBehavior = scalingBehavior,
            width = width,
            height = height,
            followCommonPractices = followCommonPractices,
            gradients = result.gradients,
            patterns = result.patterns,
            viewBox = resolvedViewBox
        )
    }

    if (isSvgLoaded) {
        val computedHeight = viewBox?.let { width * (it.height / it.width) } ?: height

        Canvas(
            modifier = modifier
                .size(width.dp, computedHeight.dp)
                .background(Color.Transparent)
                .let { baseModifier ->
                    if (enableDrag) {
                        baseModifier
                            .scrollable(disableScrolling, orientation = Orientation.Vertical)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDragEnd = { isDragging = false },
                                    onDragCancel = { isDragging = false },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount
                                    }
                                )
                            }
                    } else baseModifier
                }
        ) {
            val vb = viewBox ?: ViewBox(0f, 0f, width, height)
            val scaleX = width / vb.width
            val scaleY = height / vb.height
            val scale = min(scaleX, scaleY)
            val scaleFactor = min(scaleX, scaleY)

            // Report the computed scale factor only if a callback is provided.
            onScaleComputed?.invoke(scaleFactor)

            // Global withTransform block
            withTransform({
                when (scalingBehavior) {
                    SVGScalingBehavior.CLIP -> clipRect(left = 0f, top = 0f, right = width, bottom = height)
                    SVGScalingBehavior.SCALE -> scale(scaleX, scaleY)
                    SVGScalingBehavior.FIT -> scale(scaleX, scaleY)
                    SVGScalingBehavior.ALLOW_OVERFLOW -> Unit
                }
            }) {
                renderShapes.forEach { renderShape ->
                    renderShape.drawAction(this, if (enableDrag) dragOffset else Offset.Zero)
                }
            }
        }
    }
}

enum class SVGScalingBehavior {
    CLIP, SCALE, FIT, ALLOW_OVERFLOW
}


data class RenderShape(
    val element: OGSVGTreeElement? = null,
    val drawAction: DrawScope.(Offset) -> Unit
)

fun prepareRenderShapes(
    svgTree: OGSVGTreeElement,
    scale: Float,
    scaleX: Float,
    scaleY: Float,
    offsetX: Float,
    offsetY: Float,
    scalingBehavior: SVGScalingBehavior,
    width: Float,
    height: Float,
    followCommonPractices: Boolean,
    gradients: Map<String, OGSVGGradient>? = null,
    patterns: Map<String, OGSVGPattern>? = null,
    viewBox: ViewBox
): List<RenderShape> {
    val renderShapes = mutableListOf<RenderShape>()

    fun traverse(element: OGSVGTreeElement) {
        val strokeWidth = (element.style.strokeWidth ?: 1f) * scale

        element.shapes.forEach { shape ->
            val viewBoxOffsetX = -viewBox.minX * scale
            val viewBoxOffsetY = -viewBox.minY * scale

// Check for pattern and render using the reusable method
            if (element.style.patternId != null && patterns?.containsKey(element.style.patternId) == true) {
                val pattern = patterns[element.style.patternId]
                if (pattern != null) {
                    renderShapes.add(
                        RenderShape(element) { dragOffset ->
                            drawPattern(
                                pattern = pattern,
                                element = element,
                                shape = shape,
                                scale = scale,
                                offsetX = offsetX + dragOffset.x,
                                offsetY = offsetY + dragOffset.y
                            )
                        }
                    )
                }
                return@forEach // Skip default rendering if pattern is applied
            }






            when (shape) {
                is OGSVGLine -> {
                    val minX = min(shape.x1, shape.x2) * scale
                    val minY = min(shape.y1, shape.y2) * scale
                    val shapeWidth = (shape.x2 - shape.x1).absoluteValue * scale
                    val shapeHeight = (shape.y2 - shape.y1).absoluteValue * scale

                    val strokeBrush = OGColorBase.getCachedBrush(
                        colorBase = element.style.stroke as? OGColorBase,
                        gradient = element.style.strokeGradientId?.let { gradientId ->
                            gradients?.get(gradientId)
                        },
                        x = minX,
                        y = minY,
                        width = shapeWidth,
                        height = shapeHeight
                    )
                    renderShapes.add(
                        RenderShape(element) { dragOffset ->
                            withTransform({
                                translate(viewBoxOffsetX, viewBoxOffsetY)  // Center correction

                                translate(
                                element.style.translateX?.times(scale) ?: 0f,
                                element.style.translateY?.times(scale) ?: 0f
                            )

                            translate(offsetX + dragOffset.x, offsetY + dragOffset.y)
                            }){

                                drawLine(
                                    brush = strokeBrush,
                                    start = Offset(shape.x1 * scale, shape.y1 * scale),
                                    end = Offset(shape.x2 * scale, shape.y2 * scale),
                                    strokeWidth = strokeWidth
                                )
                            }
                        })
                }

                is OGSVGCircle -> {
                    val cx = shape.cx ?: 0f
                    val cy = shape.cy ?: 0f
                    val radius = shape.r * scale
                    val fillBrush = OGColorBase.getCachedBrush(
                        colorBase = element.style.fill as? OGColorBase,
                        gradient = element.style.fillGradientId?.let { gradientId ->
                            gradients?.get(gradientId)
                        },
                        x = ((shape.cx ?: 0f) - shape.r) * scale,
                        y = ((shape.cy ?: 0f) - shape.r) * scale,
                        width = shape.r * 2 * scale,
                        height = shape.r * 2 * scale
                    )
                    renderShapes.add(
                        RenderShape(element) { dragOffset ->
                            withTransform({
                                translate(viewBoxOffsetX, viewBoxOffsetY)  // Center correction

                                translate(
                                    element.style.translateX?.times(scale) ?: 0f,
                                    element.style.translateY?.times(scale) ?: 0f
                                )

                                translate(offsetX + dragOffset.x, offsetY + dragOffset.y)
                            }){
                            drawCircle(
                                brush = fillBrush,
                                center = Offset(cx * scale, cy * scale),
                                radius = radius,
                                style = Fill
                            )}
                        })

                    // Draw stroke only if it is not "none" and strokeWidth > 0
                    if ((element.style.strokeWidth ?: 0f) > 0f) {
                        val strokeBrush = OGColorBase.getCachedBrush(
                            colorBase = element.style.stroke as? OGColorBase,
                            gradient = element.style.strokeGradientId?.let { gradientId ->
                                gradients?.get(gradientId)
                            },
                            x = ((shape.cx ?: 0f) - shape.r) * scale,
                            y = ((shape.cy ?: 0f) - shape.r) * scale,
                            width = shape.r * 2 * scale,
                            height = shape.r * 2 * scale
                        )
                        renderShapes.add(
                            RenderShape(element) { dragOffset ->
                                withTransform({
                                    translate(viewBoxOffsetX, viewBoxOffsetY)  // Center correction

                                    translate(
                                        element.style.translateX?.times(scale) ?: 0f,
                                        element.style.translateY?.times(scale) ?: 0f
                                    )

                                    translate(offsetX + dragOffset.x, offsetY + dragOffset.y)
                                }) {
                                    drawCircle(
                                        brush = strokeBrush,
                                        center = Offset(cx * scale, cy * scale),
                                        radius = radius,
                                        style = Stroke(width = strokeWidth)
                                    )
                                }
                            })
                    }
                }

                is OGSVGEllipse -> {
                    val cx = shape.cx * scale
                    val cy = shape.cy * scale
                    val rx = shape.rx * scale
                    val ry = shape.ry * scale
                    val fillBrush = OGColorBase.getCachedBrush(
                        colorBase = element.style.fill as? OGColorBase,
                        gradient = element.style.fillGradientId?.let { gradientId ->
                            gradients?.get(gradientId)
                        },
                        x = (shape.cx - shape.rx) * scale,
                        y = (shape.cy - shape.ry) * scale,
                        width = shape.rx * 2 * scale,
                        height = shape.ry * 2 * scale
                    )

                    renderShapes.add(
                        RenderShape(element) { dragOffset ->
                            withTransform({
                                translate(viewBoxOffsetX, viewBoxOffsetY)  // Center correction

                                translate(
                                    element.style.translateX?.times(scale) ?: 0f,
                                    element.style.translateY?.times(scale) ?: 0f
                                )

                                translate(offsetX + dragOffset.x, offsetY + dragOffset.y)

                                rotate(element.style.rotation ?: 0f, pivot = Offset(cx, cy))
                            }) {
                                drawOval(
                                    brush = fillBrush,
                                    topLeft = Offset(cx - rx, cy - ry),
                                    size = Size(rx * 2, ry * 2),
                                    style = Fill
                                )
                            }
                        })

                    if ((element.style.strokeWidth ?: 0f) > 0f) {
                        val strokeBrush = OGColorBase.getCachedBrush(
                            colorBase = element.style.stroke as? OGColorBase,
                            gradient = element.style.strokeGradientId?.let { gradientId ->
                                gradients?.get(gradientId)
                            },
                            x = (shape.cx - shape.rx) * scale,
                            y = (shape.cy - shape.ry) * scale,
                            width = shape.rx * 2 * scale,
                            height = shape.ry * 2 * scale
                        )
                        renderShapes.add(
                            RenderShape(element) { dragOffset ->
                                withTransform({
                                    translate(viewBoxOffsetX, viewBoxOffsetY)  // Center correction

                                    translate(
                                        element.style.translateX?.times(scale) ?: 0f,
                                        element.style.translateY?.times(scale) ?: 0f
                                    )

                                    translate(offsetX + dragOffset.x, offsetY + dragOffset.y)
                                    rotate(element.style.rotation ?: 0f, pivot = Offset(cx, cy))
                                }) {
                                    drawOval(
                                        brush = strokeBrush,
                                        topLeft = Offset(cx - rx, cy - ry),
                                        size = Size(rx * 2, ry * 2),
                                        style = Stroke(width = strokeWidth)
                                    )
                                }
                            })

                    }
                }


                is OGSVGRect -> {
                    val x = shape.x * scale
                    val y = shape.y * scale
                    val rectSize = Size(shape.width * scale, shape.height * scale)
                    val fillBrush = OGColorBase.getCachedBrush(
                        colorBase = element.style.fill as? OGColorBase,
                        gradient = element.style.fillGradientId?.let { gradientId ->
                            gradients?.get(gradientId)
                        },
                        x = shape.x * scale,
                        y = shape.y * scale,
                        width = shape.width * scale,
                        height = shape.height * scale
                    )

                    renderShapes.add(
                        RenderShape(element) { dragOffset ->
                            withTransform({
                                translate(viewBoxOffsetX, viewBoxOffsetY)  // Center correction

                                translate(
                                    element.style.translateX?.times(scale) ?: 0f,
                                    element.style.translateY?.times(scale) ?: 0f
                                )

                                translate(offsetX + dragOffset.x, offsetY + dragOffset.y)
                            }){


                            drawRect(
                                brush = fillBrush,
                                topLeft = Offset(x, y),
                                size = rectSize,
                                style = Fill
                            )
                            }
                        })

                    if ((element.style.strokeWidth ?: 0f) > 0f) {
                        val strokeBrush = OGColorBase.getCachedBrush(
                            colorBase = element.style.stroke as? OGColorBase,
                            gradient = element.style.strokeGradientId?.let { gradientId ->
                                gradients?.get(gradientId)
                            },
                            x = shape.x * scale,
                            y = shape.y * scale,
                            width = shape.width * scale,
                            height = shape.height * scale
                        )

                        renderShapes.add(
                            RenderShape(element) { dragOffset ->
                                withTransform({
                                    translate(viewBoxOffsetX, viewBoxOffsetY)  // Center correction

                                    translate(
                                        element.style.translateX?.times(scale) ?: 0f,
                                        element.style.translateY?.times(scale) ?: 0f
                                    )

                                    translate(offsetX + dragOffset.x, offsetY + dragOffset.y)
                                }) {
                                    drawRect(
                                        brush = strokeBrush,
                                        topLeft = Offset(x, y),
                                        size = rectSize,
                                        style = Stroke(width = strokeWidth)
                                    )
                                }
                            })
                    }
                }

                is OGSVGPolygon -> {
                    if (shape.points.isEmpty()) return@forEach
                    val path = Path().apply {
                        moveTo(shape.points.first().x * scale, shape.points.first().y * scale)
                        shape.points.drop(1).forEach { point ->
                            lineTo(point.x * scale, point.y * scale)
                        }
                        close()
                    }

                    val minX = shape.points.minOfOrNull { it.x } ?: 0f
                    val minY = shape.points.minOfOrNull { it.y } ?: 0f
                    val maxX = shape.points.maxOfOrNull { it.x } ?: 0f
                    val maxY = shape.points.maxOfOrNull { it.y } ?: 0f

                    val fillBrush = OGColorBase.getCachedBrush(
                        colorBase = element.style.fill as? OGColorBase,
                        gradient = element.style.fillGradientId?.let { gradientId ->
                            gradients?.get(gradientId)
                        },
                        x = minX * scale,
                        y = minY * scale,
                        width = (maxX - minX) * scale,
                        height = (maxY - minY) * scale
                    )

                    renderShapes.add(
                        RenderShape(element) { dragOffset ->
                            withTransform({
                                translate(viewBoxOffsetX, viewBoxOffsetY)  // Center correction

                                translate(
                                    element.style.translateX?.times(scale) ?: 0f,
                                    element.style.translateY?.times(scale) ?: 0f
                                )

                                translate(offsetX + dragOffset.x, offsetY + dragOffset.y)
                            }){
                            drawPath(
                                path = path,
                                brush = fillBrush,
                                style = Fill
                            )}
                        })

                    if ((element.style.strokeWidth ?: 0f) > 0f) {
                        val strokeBrush = OGColorBase.getCachedBrush(
                            colorBase = element.style.stroke as? OGColorBase,
                            gradient = element.style.strokeGradientId?.let { gradientId ->
                                gradients?.get(gradientId)
                            },
                            x = minX * scale,
                            y = minY * scale,
                            width = (maxX - minX) * scale,
                            height = (maxY - minY) * scale
                        )

                        renderShapes.add(
                            RenderShape(element) { dragOffset ->
                                withTransform({
                                    translate(viewBoxOffsetX, viewBoxOffsetY)  // Center correction

                                    translate(
                                        element.style.translateX?.times(scale) ?: 0f,
                                        element.style.translateY?.times(scale) ?: 0f
                                    )

                                    translate(offsetX + dragOffset.x, offsetY + dragOffset.y)
                                }) {
                                    drawPath(
                                        path = path,
                                        brush = strokeBrush,
                                        style = Stroke(width = strokeWidth)
                                    )
                                }
                            })
                    }
                }

                is OGSVGPath -> {
                    // Pen position in already-scaled coordinates.
                    var currentX = 0f
                    var currentY = 0f
                    // Second control point of the previous curve (scaled), used to
                    // reflect for smooth (S/T) commands per the SVG path spec.
                    var lastCtrlX = 0f
                    var lastCtrlY = 0f
                    var prevWasCubic = false
                    var prevWasQuad = false

                    val path = Path().apply {
                        shape.commands.forEach { command ->
                            when (command) {
                                is MoveTo -> {
                                    moveTo(command.x * scale, command.y * scale)
                                    currentX = command.x * scale
                                    currentY = command.y * scale
                                    prevWasCubic = false; prevWasQuad = false
                                }

                                is LineTo -> {
                                    lineTo(command.x * scale, command.y * scale)
                                    currentX = command.x * scale
                                    currentY = command.y * scale
                                    prevWasCubic = false; prevWasQuad = false
                                }

                                is HorizontalLineTo -> {
                                    lineTo(command.x * scale, currentY)
                                    currentX = command.x * scale
                                    prevWasCubic = false; prevWasQuad = false
                                }

                                is VerticalLineTo -> {
                                    lineTo(currentX, command.y * scale)
                                    currentY = command.y * scale
                                    prevWasCubic = false; prevWasQuad = false
                                }

                                is CurveTo -> {
                                    cubicTo(
                                        command.x1 * scale, command.y1 * scale,
                                        command.x2 * scale, command.y2 * scale,
                                        command.x * scale, command.y * scale
                                    )
                                    currentX = command.x * scale
                                    currentY = command.y * scale
                                    lastCtrlX = command.x2 * scale
                                    lastCtrlY = command.y2 * scale
                                    prevWasCubic = true; prevWasQuad = false
                                }

                                is SmoothCurveTo -> {
                                    // First control point = reflection of the previous
                                    // cubic's second control point about the current
                                    // point (or the current point if none).
                                    val c1x = if (prevWasCubic) 2 * currentX - lastCtrlX else currentX
                                    val c1y = if (prevWasCubic) 2 * currentY - lastCtrlY else currentY
                                    cubicTo(
                                        c1x, c1y,
                                        command.x2 * scale, command.y2 * scale,
                                        command.x * scale, command.y * scale
                                    )
                                    currentX = command.x * scale
                                    currentY = command.y * scale
                                    lastCtrlX = command.x2 * scale
                                    lastCtrlY = command.y2 * scale
                                    prevWasCubic = true; prevWasQuad = false
                                }

                                is QuadraticCurveTo -> {
                                    quadraticTo(
                                        command.controlX * scale,
                                        command.controlY * scale,
                                        command.x * scale,
                                        command.y * scale
                                    )
                                    currentX = command.x * scale
                                    currentY = command.y * scale
                                    lastCtrlX = command.controlX * scale
                                    lastCtrlY = command.controlY * scale
                                    prevWasCubic = false; prevWasQuad = true
                                }

                                is SmoothQuadraticCurveTo -> {
                                    // Control point = reflection of the previous quad's
                                    // control point about the current point.
                                    val cx = if (prevWasQuad) 2 * currentX - lastCtrlX else currentX
                                    val cy = if (prevWasQuad) 2 * currentY - lastCtrlY else currentY
                                    quadraticTo(
                                        cx, cy,
                                        command.x * scale,
                                        command.y * scale
                                    )
                                    currentX = command.x * scale
                                    currentY = command.y * scale
                                    lastCtrlX = cx
                                    lastCtrlY = cy
                                    prevWasCubic = false; prevWasQuad = true
                                }

                                is ArcTo -> {
                                    // Draw the arc starting from the current pen
                                    // position (not the canvas centre); pass the
                                    // rotation in degrees.
                                    val arcPath = approximateArcPath(
                                        Offset(currentX, currentY),
                                        (command.rx * scale).toDouble(),
                                        (command.ry * scale).toDouble(),
                                        command.xAxisRotation.toDouble(),
                                        command.largeArc,
                                        command.sweep,
                                        Offset(command.x * scale, command.y * scale)
                                    )
                                    addPath(arcPath)
                                    currentX = command.x * scale
                                    currentY = command.y * scale
                                    prevWasCubic = false; prevWasQuad = false
                                }

                                is ClosePath -> {
                                    close()
                                    prevWasCubic = false; prevWasQuad = false
                                }
                            }
                        }
                    }

                    val rotationCenter = if (followCommonPractices) {
                        Offset(
                            (shape.bounds.left + shape.bounds.right) / 2,
                            (shape.bounds.top + shape.bounds.bottom) / 2
                        )
                    } else {
                        Offset(element.style.rotationCx ?: 0f, element.style.rotationCy ?: 0f)
                    }

                    val fillBrush = OGColorBase.getCachedBrush(
                        colorBase = element.style.fill as? OGColorBase,
                        gradient = element.style.fillGradientId?.let { gradientId ->
                            gradients?.get(gradientId)
                        },
                        x = shape.bounds.left * scale,
                        y = shape.bounds.top * scale,
                        width = shape.bounds.width * scale,
                        height = shape.bounds.height * scale
                    )

                    renderShapes.add(
                        RenderShape(element) { dragOffset ->
                            withTransform({
                                translate(viewBoxOffsetX, viewBoxOffsetY)  // Center correction

                                translate(
                                    element.style.translateX?.times(scale) ?: 0f,
                                    element.style.translateY?.times(scale) ?: 0f
                                )

                                translate(offsetX + dragOffset.x, offsetY + dragOffset.y)
                                rotate(element.style.rotation ?: 0f, pivot = rotationCenter)
                            }) {
                                drawPath(
                                    path = path,
                                    brush = fillBrush,
                                    style = Fill
                                )
                            }
                        })
                    if ((element.style.strokeWidth ?: 0f) > 0f) {
                        val strokeBrush = OGColorBase.getCachedBrush(
                            colorBase = element.style.stroke as? OGColorBase,
                            gradient = element.style.strokeGradientId?.let { gradientId ->
                                gradients?.get(gradientId)
                            },
                            x = shape.bounds.left * scale,
                            y = shape.bounds.top * scale,
                            width = shape.bounds.width * scale,
                            height = shape.bounds.height * scale
                        )
                        renderShapes.add(
                            RenderShape(element) { dragOffset ->
                                withTransform({
                                    translate(viewBoxOffsetX, viewBoxOffsetY)  // Center correction

                                    translate(
                                        element.style.translateX?.times(scale) ?: 0f,
                                        element.style.translateY?.times(scale) ?: 0f
                                    )

                                    translate(offsetX + dragOffset.x, offsetY + dragOffset.y)
                                    rotate(element.style.rotation ?: 0f, pivot = rotationCenter)
                                }) {
                                    drawPath(
                                        path = path,
                                        brush = strokeBrush,
                                        style = Stroke(width = strokeWidth)
                                    )
                                }
                            })
                    }

                }


                // Add more cases for other shapes
            }
        }

        element.children.forEach { child ->
            traverse(child)
        }
    }

    traverse(svgTree)

    return renderShapes
}


fun DrawScope.drawPattern(
    pattern: OGSVGPattern,
    element: OGSVGTreeElement,
    shape: OGSVGShape,
    scale: Float,
    offsetX: Float,
    offsetY: Float
) {
    // Determine the fill area
    val (fillX, fillY, fillWidth, fillHeight) = when (shape) {
        is OGSVGRect -> Quadruple(shape.x, shape.y, shape.width, shape.height)
        else -> {
            Logger.w("SVG>>Render>> Pattern fill currently supports only SvgRect shapes")
            return
        }
    }

    // Calculate the pattern repetition bounds
    val patternWidth = pattern.width * scale
    val patternHeight = pattern.height * scale

    // Loop through the fill area, repeating the pattern
    var yPos = fillY
    while (yPos < fillY + fillHeight) {
        var xPos = fillX
        while (xPos < fillX + fillWidth) {
            withTransform({
                translate(
                    element.style.translateX?.times(scale) ?: 0f,
                    element.style.translateY?.times(scale) ?: 0f
                )
                translate(offsetX, offsetY)
                translate(xPos * scale, yPos * scale)
            }) {
                pattern.elements.forEach { patternElement ->
                    patternElement.shapes.forEach { patternShape ->
                        when (patternShape) {
                            is OGSVGRect -> drawRect(
                                color = (patternElement.style.fill as? OGColor)?.color
                                    ?: Color.Transparent,
                                topLeft = Offset(patternShape.x * scale, patternShape.y * scale),
                                size = Size(patternShape.width * scale, patternShape.height * scale)
                            )

                            is OGSVGCircle -> drawCircle(
                                color = (patternElement.style.fill as? OGColor)?.color
                                    ?: Color.Transparent,
                                center = Offset(
                                    patternShape.cx * scale,
                                    patternShape.cy * scale
                                ),
                                radius = patternShape.r * scale
                            )

                            is OGSVGEllipse -> drawOval(
                                color = (patternElement.style.fill as? OGColor)?.color
                                    ?: Color.Transparent,
                                topLeft = Offset(
                                    (patternShape.cx - patternShape.rx) * scale,
                                    (patternShape.cy - patternShape.ry) * scale
                                ),
                                size = Size(
                                    patternShape.rx * 2 * scale,
                                    patternShape.ry * 2 * scale
                                )
                            )

                            is OGSVGPolygon -> if (patternShape.points.isNotEmpty()) {
                                val path = Path().apply {
                                    moveTo(
                                        patternShape.points.first().x * scale,
                                        patternShape.points.first().y * scale
                                    )
                                    patternShape.points.drop(1).forEach { point ->
                                        lineTo(point.x * scale, point.y * scale)
                                    }
                                    close()
                                }
                                drawPath(
                                    path = path,
                                    color = (patternElement.style.fill as? OGColor)?.color
                                        ?: Color.Transparent
                                )
                            }

                            is OGSVGPath -> {
                                val path = Path().apply {
                                    patternShape.commands.forEach { command ->
                                        when (command) {
                                            is MoveTo -> moveTo(command.x * scale, command.y * scale)
                                            is LineTo -> lineTo(command.x * scale, command.y * scale)
                                            is HorizontalLineTo -> lineTo(command.x * scale, 0f)
                                            is VerticalLineTo -> lineTo(0f, command.y * scale)
                                            is ClosePath -> close()
                                            else -> Logger.w("SVG>>Render>> Unsupported path command: ${command::class.simpleName}")
                                        }
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = (patternElement.style.fill as? OGColor)?.color
                                        ?: Color.Transparent
                                )
                            }

                            else -> Logger.w("SVG>>Render>> Unsupported shape in pattern: ${patternShape::class.simpleName}")
                        }
                    }
                }
            }
            xPos += pattern.width
        }
        yPos += pattern.height
    }
}



// Helper data class to return four values
data class Quadruple(val x: Float, val y: Float, val width: Float, val height: Float)
