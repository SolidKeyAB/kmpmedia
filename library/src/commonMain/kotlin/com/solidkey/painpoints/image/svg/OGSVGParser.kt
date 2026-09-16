package com.solidkey.painpoints.image.svg

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.OGBrush
import com.solidkey.painpoints.image.OGColor
import com.solidkey.painpoints.image.OGColorBase
import com.solidkey.painpoints.image.loading.OGParsedSVGResult
import com.solidkey.painpoints.image.loading.ViewBox
import com.solidkey.painpoints.image.svg.animation.OGSVGAnimation
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

fun generateRandomId(length: Int = 8): String {
    val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}

fun toRadians(degrees: Double): Double = degrees * (PI / 180.0)

// ---------------------- //
//  EXPECT DECLARATION   //
// ---------------------- //
expect class OGSVGDocument(svgContent: String) {
    val root: OGSVGElement
}

expect class OGSVGElement {
    val tagName: String
    var animations: MutableList<OGSVGAnimation>? // ✅ Store multiple animations
    val children: List<OGSVGElement>
    fun getAttribute(name: String): String?
    fun getAllAttributes(): MutableMap<String, String>
    fun setAttribute(name: String, value: String) // 🔥 NEW: Dynamic updates
}


expect fun parseSVGDocument(svgContent: String): OGSVGDocument

// --------------------- //
//     DATA CLASSES     //
// --------------------- //

data class OGSVGTreeElement(
    val tagName: String,
    val attributes: MutableMap<String, String>,
    var style: OGSVGStyle,
    var id: String?,
    val children: MutableList<OGSVGTreeElement> = mutableListOf(),
    val shapes: MutableList<OGSVGShape> = mutableListOf(), // ✅ Updated to use `SvgShape`
    var rawPathData: String? = null, // ✅ Store original path `d`
    var parsedPath: List<OGSVGCommand>? = null, // ✅ Store parsed path commands
    val animations: MutableList<OGSVGAnimation> = mutableListOf(), // ✅ Now supports multiple animations
    var animationProgress: Float = 0f // ✅ Track animation progress (0 to 1)
)

sealed class OGSVGCommand

data class SvgColorStop(
    val offset: Float,
    val color: Color
)

data class QuadraticCurveTo(
    var controlX: Float,
    var controlY: Float,
    var x: Float,
    var y: Float
) : OGSVGCommand()

data class ArcTo(
    var rx: Float,
    var ry: Float,
    val xAxisRotation: Float,
    val largeArc: Boolean,
    val sweep: Boolean,
    var x: Float,
    var y: Float
) : OGSVGCommand()

data class HorizontalLineTo(var x: Float) : OGSVGCommand()
data class VerticalLineTo(var y: Float) : OGSVGCommand()
data class SmoothCurveTo(var x2: Float, var y2: Float, var x: Float, var y: Float) : OGSVGCommand()
data class SmoothQuadraticCurveTo(var x: Float, var y: Float) : OGSVGCommand()
data class MoveTo(var x: Float, var y: Float) : OGSVGCommand()
data class LineTo(var x: Float, var y: Float) : OGSVGCommand()
data class CurveTo(
    var x1: Float, var y1: Float,
    var x2: Float, var y2: Float,
    var x: Float, var y: Float
) : OGSVGCommand()

data class ClosePath(val reason: String = "End of Path") : OGSVGCommand()

sealed class OGSVGShape {
    var animations: MutableList<OGSVGAnimation> = mutableListOf()
}

data class OGSVGLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : OGSVGShape()
data class OGSVGPath(val commands: List<OGSVGCommand>) : OGSVGShape() {
    val bounds: Rect
        get() {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            var currentX = 0f
            var currentY = 0f

            commands.forEach { command ->
                when (command) {
                    is MoveTo -> {
                        currentX = command.x
                        currentY = command.y
                    }

                    is LineTo -> {
                        currentX = command.x
                        currentY = command.y
                    }

                    is HorizontalLineTo -> {
                        currentX = command.x
                    }

                    is VerticalLineTo -> {
                        currentY = command.y
                    }

                    is ArcTo -> {
                        currentX = command.x
                        currentY = command.y
                    }

                    is CurveTo -> {
                        minX = minOf(minX, command.x1, command.x2, command.x)
                        minY = minOf(minY, command.y1, command.y2, command.y)
                        maxX = maxOf(maxX, command.x1, command.x2, command.x)
                        maxY = maxOf(maxY, command.y1, command.y2, command.y)
                        currentX = command.x
                        currentY = command.y
                    }

                    is QuadraticCurveTo -> {
                        minX = minOf(minX, command.controlX, command.x)
                        minY = minOf(minY, command.controlY, command.y)
                        maxX = maxOf(maxX, command.controlX, command.x)
                        maxY = maxOf(maxY, command.controlY, command.y)
                        currentX = command.x
                        currentY = command.y
                    }

                    is SmoothCurveTo -> {
                        minX = minOf(minX, command.x2, command.x)
                        minY = minOf(minY, command.y2, command.y)
                        maxX = maxOf(maxX, command.x2, command.x)
                        maxY = maxOf(maxY, command.y2, command.y)
                        currentX = command.x
                        currentY = command.y
                    }

                    is SmoothQuadraticCurveTo -> {
                        minX = minOf(minX, command.x)
                        minY = minOf(minY, command.y)
                        maxX = maxOf(maxX, command.x)
                        maxY = maxOf(maxY, command.y)
                        currentX = command.x
                        currentY = command.y
                    }

                    is ClosePath -> Unit
                }

                minX = minOf(minX, currentX)
                minY = minOf(minY, currentY)
                maxX = maxOf(maxX, currentX)
                maxY = maxOf(maxY, currentY)
            }

            return Rect(minX, minY, maxX, maxY)
        }


    fun toPath(): Path {
        val path = Path()
        var lastX = 0.0f
        var lastY = 0.0f

        commands.forEach { command ->
            when (command) {
                is MoveTo -> {
                    path.moveTo(command.x, command.y)
                    lastX = command.x
                    lastY = command.y
                }

                is LineTo -> {
                    path.lineTo(command.x, command.y)
                    lastX = command.x
                    lastY = command.y
                }

                is HorizontalLineTo -> {
                    path.lineTo(command.x, lastY) // Use last known Y
                    lastX = command.x
                }

                is VerticalLineTo -> {
                    path.lineTo(lastX, command.y) // Use last known X
                    lastY = command.y
                }

                is CurveTo -> {
                    path.cubicTo(
                        command.x1, command.y1,
                        command.x2, command.y2,
                        command.x, command.y
                    )
                    lastX = command.x
                    lastY = command.y
                }

                is QuadraticCurveTo -> {
                    path.quadraticBezierTo(
                        command.controlX, command.controlY,
                        command.x, command.y
                    )
                    lastX = command.x
                    lastY = command.y
                }

                is SmoothCurveTo -> {
                    val controlX1 = 2 * lastX - command.x2
                    val controlY1 = 2 * lastY - command.y2
                    path.cubicTo(
                        controlX1, controlY1,
                        command.x2, command.y2,
                        command.x, command.y
                    )
                    lastX = command.x
                    lastY = command.y
                }

                is SmoothQuadraticCurveTo -> {
                    val controlX = 2 * lastX - command.x
                    val controlY = 2 * lastY - command.y
                    path.quadraticBezierTo(controlX, controlY, command.x, command.y)
                    lastX = command.x
                    lastY = command.y
                }

                is ArcTo -> {
                    // Convert SVG arc to Bézier curves (Not natively supported in Jetpack Compose)
                    val bezierSegments = svgArcToBezierChrome(
                        x0 = lastX.toDouble(),
                        y0 = lastY.toDouble(),
                        x1 = command.x.toDouble(),
                        y1 = command.y.toDouble(),
                        rx = command.rx.toDouble(),
                        ry = command.ry.toDouble(),
                        xAxisRotation = command.xAxisRotation.toDouble(),
                        largeArcFlag = command.largeArc,
                        sweepFlag = command.sweep
                    )
                    bezierSegments.forEach { segment ->
                        path.cubicTo(
                            segment[0].toFloat(), segment[1].toFloat(),
                            segment[2].toFloat(), segment[3].toFloat(),
                            segment[4].toFloat(), segment[5].toFloat()
                        )
                    }
                    lastX = command.x
                    lastY = command.y
                }

                is ClosePath -> {
                    path.close()
                }
            }
        }

        return path
    }
}

data class OGSVGEllipse(
    val cx: Float,
    val cy: Float,
    val rx: Float,
    val ry: Float,
    val rotation: Float
) : OGSVGShape()

data class OGSVGPolygon(val points: List<Offset>) : OGSVGShape()

data class OGSVGCircle(val cx: Float, val cy: Float, val r: Float) : OGSVGShape()
data class OGSVGRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rx: Float = 0f,
    val ry: Float = 0f
) : OGSVGShape()

data class OGSVGGradient(
    val id: String,
    val brush: OGBrush
)

sealed class OGGradientType
data class OGLinearGradientType(val x1: Float, val y1: Float, val x2: Float, val y2: Float) :
    OGGradientType()

data class OGRadialGradientType(val cx: Float, val cy: Float, val r: Float) : OGGradientType()

data class OGSVGPattern(
    val id: String,
    val elements: List<OGSVGTreeElement>, // Elements within the pattern
    val patternTransform: OGSVGStyle = OGSVGStyle(), // Support transforms
    val patternUnits: String = "objectBoundingBox", // "objectBoundingBox" or "userSpaceOnUse"
    val width: Float = 0f,
    val height: Float = 0f
)


data class OGSVGStyle(
    var fill: OGColorBase? = null, // Default to null
    var stroke: OGColorBase? = null, // Default to null
    var patternId: String? = null, // Added for pattern fills
    var strokeWidth: Float? = null,
    var scaleX: Float? = null,
    var scaleY: Float? = null,
    val translateX: Float? = null,
    val translateY: Float? = null,
    val rotation: Float? = null,
    val rotationCx: Float? = null,
    val rotationCy: Float? = null,
    val skewX: Float? = null,
    val skewY: Float? = null,
    var fillGradientId: String? = null,   // Added for gradient fills
    var strokeGradientId: String? = null  // Added for gradient strokes
) {
    fun combine(other: OGSVGStyle?): OGSVGStyle {
        if (other == null) return this
        return OGSVGStyle(
            fill = other.fill ?: this.fill,
            stroke = other.stroke ?: this.stroke,
            fillGradientId = other.fillGradientId ?: this.fillGradientId,
            strokeGradientId = other.strokeGradientId ?: this.strokeGradientId,
            strokeWidth = other.strokeWidth ?: this.strokeWidth,
            scaleX = other.scaleX ?: this.scaleX,
            scaleY = other.scaleY ?: this.scaleY,
            translateX = other.translateX ?: this.translateX,
            translateY = other.translateY ?: this.translateY,
            rotation = other.rotation ?: this.rotation,
            rotationCx = other.rotationCx ?: this.rotationCx,
            rotationCy = other.rotationCy ?: this.rotationCy,
            skewX = other.skewX ?: this.skewX,
            skewY = other.skewY ?: this.skewY
        )
    }
}

// Animation (<animate>/<animateTransform>/<animateMotion>) elements are parsed
// inline in buildSVGTree and attached to the element they target. See
// parseAnimationElement below.

internal fun parseAnimationElement(
    tagName: String,
    attributes: Map<String, String>
): OGSVGAnimation? {
    Logger.e("SVG>>parseAnimationElement>> Parsing animation: $tagName")

    // <animateMotion> animates position along a path; treat it as a transform.
    val attributeName = attributes["attributeName"]
        ?: if (tagName.lowercase() == "animatemotion") "transform" else return null

    // <animateTransform type="translate|rotate|scale|skewX|skewY">
    val type = attributes["type"]?.trim()?.lowercase()

    val dur = attributes["dur"]?.replace("s", "")?.trim()?.toFloatOrNull() ?: 1f
    val repeatCount = attributes["repeatCount"] ?: "indefinite"

    // from/to may be a single scalar ("360") OR a multi-component vector
    // ("0,0" / "0 400" for translate, "angle cx cy" for rotate).
    fun parseVec(raw: String?): List<Float>? = raw
        ?.split(",", " ")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.mapNotNull { it.toFloatOrNull() }
        ?.takeIf { it.isNotEmpty() }

    val fromStr = attributes["from"]
    val toStr = attributes["to"]
    val fromVec = parseVec(fromStr)
    val toVec = parseVec(toStr)
    val from = fromStr?.toFloatOrNull() ?: fromVec?.firstOrNull()
    val to = toStr?.toFloatOrNull() ?: toVec?.firstOrNull()
    val by = attributes["by"]?.toFloatOrNull()
    // Each `;`-separated keyframe may itself be a vector ("0,-30" / "1.2 1.2").
    val valuesVec = attributes["values"]
        ?.split(";")
        ?.mapNotNull { parseVec(it) }
        ?.takeIf { it.size > 1 }
    // Scalar view of the same keyframes (first component of each) for <animate>.
    val values = attributes["values"]
        ?.split(";")
        ?.mapNotNull { it.trim().split(",", " ").firstOrNull()?.trim()?.toFloatOrNull() }
        ?.takeIf { it.isNotEmpty() }

    val isPathAnim = attributeName == "d"
    return OGSVGAnimation(
        attributeName = attributeName,
        type = type,
        from = if (isPathAnim) null else from,
        to = if (isPathAnim) null else to,
        by = by,
        values = values,
        fromVec = if (isPathAnim) null else fromVec,
        toVec = if (isPathAnim) null else toVec,
        valuesVec = if (isPathAnim) null else valuesVec,
        duration = dur,
        repeatCount = repeatCount,
        fromPath = if (isPathAnim) fromStr else null,
        toPath = if (isPathAnim) toStr else null
    )
}


//fun updateAnimation(element: SvgTreeElement, deltaTime: Float) {
//    val animation = element.animation ?: return
//
//    element.animationProgress += deltaTime / animation.duration
//    if (element.animationProgress > 1f) {
//        if (animation.repeatCount == "indefinite") {
//            element.animationProgress = 0f // Restart loop
//        } else {
//            element.animationProgress = 1f // Stop at end
//        }
//    }
//
//    if (animation.attributeName == "d") {
//        // ✅ Path Morphing Animation
//        if (animation.fromPath != null && animation.toPath != null) {
//            element.parsedPath = interpolatePath(animation.fromPath, animation.toPath, element.animationProgress)
//        }
//    } else {
//        // ✅ Numeric Attribute Animation
//        val from = animation.from ?: 0f
//        val to = animation.to ?: 0f
//        val newValue = from + (to - from) * element.animationProgress
//        element.attributes[animation.attributeName] = newValue.toString()
//    }
//}

//fun interpolatePath(fromPath: String?, toPath: String?, progress: Float): List<SvgCommand> {
//    if (fromPath.isNullOrEmpty() || toPath.isNullOrEmpty()) {
//        return emptyList()
//    }
//
//    val fromCommands = parsePathCommands(fromPath, ViewBox(0f, 0f, 100f, 100f))
//    val toCommands = parsePathCommands(toPath, ViewBox(0f, 0f, 100f, 100f))
//
//    if (fromCommands.size != toCommands.size) {
//        Logger.e("SVG>>interpolatePath>> Path mismatch! Using initial path.")
//        return fromCommands
//    }
//
//    return fromCommands.zip(toCommands).map { (fromCmd, toCmd) ->
//        when {
//            fromCmd is MoveTo && toCmd is MoveTo -> MoveTo(
//                fromCmd.x + (toCmd.x - fromCmd.x) * progress,
//                fromCmd.y + (toCmd.y - fromCmd.y) * progress
//            )
//            fromCmd is LineTo && toCmd is LineTo -> LineTo(
//                fromCmd.x + (toCmd.x - fromCmd.x) * progress,
//                fromCmd.y + (toCmd.y - fromCmd.y) * progress
//            )
//            else -> fromCmd
//        }
//    }
//}

private fun extractNumbersFromPath(pathData: String): List<Float> {
    val numberPattern = Regex("[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?")
    return numberPattern.findAll(pathData).map { it.value.toFloat() }.toList()
}

fun parseSVGPaths(svgContent: String): OGParsedSVGResult {
    val symbolMap = mutableMapOf<String, OGSVGElement>()
    val definitions = mutableMapOf<String, OGSVGElement>()
    val gradients = parseSVGGradients(svgContent)
    val patterns = parseSVGPatterns(svgContent)
    val viewBox = parseViewBox(svgContent)

    Logger.e("SVG>>parseSvgPaths>> viewBox: $viewBox")
    Logger.e("SVG>>parseSvgPaths>> gradients: $gradients")

    val document = parseSVGDocument(svgContent)
    Logger.i("*** SVG DOCUMENT PARSED...")

    val rootSvgElement = document.root

    // ✅ Check if the root element is <svg>
    if (rootSvgElement.tagName.lowercase() != "svg") {
        Logger.e("SVG>>parseSvgPaths>> ERROR: Root element is not <svg>, found: ${rootSvgElement.tagName}")
        return OGParsedSVGResult(
            viewBox = viewBox,
            svgTree = null,
            hasClickAction = false,
            gradients = gradients,
            patterns = patterns,
        )
    }

    // Step 1: Extract Definitions First
    extractDefinitions(rootSvgElement, definitions, symbolMap)
    Logger.i("*** EXTRACTED DEFINITIONS FROM ROOT ELEMENT...")

    // Step 2: Build SVG Element Tree in a Single Pass
    val svgTreeRoot = buildSVGTree(
        element = rootSvgElement,
        rootTreeElement = null,
        parentTreeElement = null,
        definitions = definitions,
        symbolMap = symbolMap,
        viewBox = viewBox,
        gradients = gradients,
        patterns = patterns,
        inheritedStyle = OGSVGStyle()
    )

    if (svgTreeRoot == null) {
        Logger.e("SVG>>parseSvgPaths>> ERROR: Failed to build SVG element tree.")
        return OGParsedSVGResult(
            viewBox = viewBox,
            svgTree = null,
            hasClickAction = false,
            gradients = gradients,
            patterns = patterns
        )
    }

    Logger.i("*** BUILT SVG ELEMENT TREE IN ONE PASS...")

    // Step 3: Animations are parsed inline during buildSVGTree and attached to
    // the elements they target (see the <animate*> handling there).

    return OGParsedSVGResult(
        viewBox = viewBox,
        svgTree = svgTreeRoot,
        hasClickAction = svgContent.contains("onclick=", ignoreCase = true),
        gradients = gradients,
        patterns = patterns
    )
}

fun parseSVGPatterns(svgContent: String): Map<String, OGSVGPattern> {
    val patterns = mutableMapOf<String, OGSVGPattern>()

    val defsRegex = Regex("""<defs>([\s\S]*?)</defs>""")
    val defsMatch = defsRegex.find(svgContent)?.groupValues?.get(1) ?: return patterns

    val patternRegex = Regex("""<pattern\s+[^>]*id=["']([^"']+)["'][^>]*>([\s\S]*?)</pattern>""")

    patternRegex.findAll(defsMatch).forEach { match ->
        val id = match.groupValues[1]
        val patternContent = match.groupValues[2]

        Logger.e("✅ Found pattern with ID: $id")

        // Extract pattern attributes
        val width = extractAttribute(match.value, "width")?.toFloatOrNull() ?: 0f
        val height = extractAttribute(match.value, "height")?.toFloatOrNull() ?: 0f
        val patternUnits = extractAttribute(match.value, "patternUnits") ?: "objectBoundingBox"

        // Parse pattern transform if available
        val transform =
            extractAttribute(match.value, "patternTransform")?.let { parseTransformAttributes(it) }
                ?: OGSVGStyle()

        // Parse child elements within the pattern
        val tempDocument = parseSVGDocument("<svg>$patternContent</svg>")
        val tempRoot = tempDocument.root

        val elements = mutableListOf<OGSVGTreeElement>()

        tempRoot.children.forEach { childElement ->
            val svgTreeElement = buildSVGTree(
                element = childElement,
                rootTreeElement = null,
                parentTreeElement = null,
                definitions = emptyMap(),
                symbolMap = emptyMap(),
                viewBox = ViewBox(0f, 0f, width, height),
                gradients = emptyMap(),
                patterns = emptyMap(),
                inheritedStyle = OGSVGStyle()
            )

            if (svgTreeElement != null) {
                elements.add(svgTreeElement)
            }
        }

        patterns[id] = OGSVGPattern(
            id = id,
            elements = elements,
            patternTransform = transform,
            patternUnits = patternUnits,
            width = width,
            height = height
        )

        Logger.e("✅ Stored pattern: ID=$id, Elements=${elements.size}")
    }

    Logger.i("✅ Found ${patterns.size} patterns!")
    return patterns
}


fun parseExistingAttributes(
    element: OGSVGElement,
    existingStyle: OGSVGStyle,
    gradients: Map<String, OGSVGGradient>? = null,
    patterns: Map<String, OGSVGPattern>? = null
): OGSVGStyle {
    val newStyle = existingStyle.copy() // Start with the existing style

    element.getAllAttributes().forEach { (key, value) ->
        when (key.lowercase()) {
            "fill" -> {
                if (value.startsWith("url(#") && value.endsWith(")")) {
                    val referenceId = value.removePrefix("url(#").removeSuffix(")")

                    if (patterns?.containsKey(referenceId) == true) {
                        newStyle.patternId = referenceId
                        newStyle.fill = null // Clear direct color if pattern is set
                        newStyle.fillGradientId = null // Clear gradient if pattern is set
                    } else if (gradients?.containsKey(referenceId) == true) {
                        newStyle.fillGradientId = referenceId
                        newStyle.fill = null // Clear direct color if gradient is set
                        newStyle.patternId = null // Clear pattern if gradient is set
                    }
                } else {
                    newStyle.fill = parseColor(value, gradients, isFill = true)
                    newStyle.fillGradientId = null // Clear gradient if direct color is set
                    newStyle.patternId = null // Clear pattern if direct color is set
                }
            }

            "stroke" -> {
                if (value.startsWith("url(#") && value.endsWith(")")) {
                    newStyle.strokeGradientId = value.removePrefix("url(#").removeSuffix(")")
                    newStyle.stroke = null // Clear direct color if gradient is set
                } else {
                    newStyle.stroke = parseColor(value, gradients, isFill = false)
                    newStyle.strokeGradientId = null // Clear gradient if direct color is set
                }
            }

            "stroke-width" -> {
                newStyle.strokeWidth = value.toFloatOrNull() ?: existingStyle.strokeWidth
            }

            "transform" -> {
                newStyle.combine(parseTransformAttributes(value))
            }

            else -> {
                Logger.w("SVG>>parseExistingAttributes>> Attribute: $key is not a recognized style attribute.")
            }
        }
    }

    return newStyle
}


fun buildSVGTree(
    element: OGSVGElement,
    parentTreeElement: OGSVGTreeElement?,
    rootTreeElement: OGSVGTreeElement?,
    inheritedStyle: OGSVGStyle?,
    definitions: Map<String, OGSVGElement>,
    symbolMap: Map<String, OGSVGElement>,
    viewBox: ViewBox,
    gradients: Map<String, OGSVGGradient>,
    patterns: Map<String, OGSVGPattern> // Added support for patterns
): OGSVGTreeElement? {
    Logger.e("SVG>>buildSvgTree>> Processing element: ${element.tagName}")

    // Parse transformations and merge styles
    val localTransform = parseTransformAttributes(element.getAttribute("transform"))
    val mergedStyle = if (element.tagName.lowercase() == "use") {
        val referenceStyle = inheritedStyle ?: OGSVGStyle()
        parseExistingAttributes(element, referenceStyle, gradients, patterns)
    } else {
        parseSVGElementAttributes(
            element,
            inheritedStyle?.combine(localTransform) ?: localTransform ?: inheritedStyle
            ?: OGSVGStyle(),
            gradients,
            patterns
        )
    }

    // Determine the shape based on the element type
    val shape = when (element.tagName.lowercase()) {
        "path" -> {
            val d = element.getAttribute("d") ?: return null
            OGSVGPath(parsePathCommands(d, viewBox, mergedStyle))
        }

        "line" -> parseLine(element, mergedStyle)
        "circle" -> parseCircle(element, mergedStyle)
        "ellipse" -> parseEllipse(element, mergedStyle)
        "polygon", "polyline" -> parsePolygon(element, mergedStyle)
        "rect" -> parseRect(element, mergedStyle)
        else -> null
    }

    // Create the current tree element
    val currentTreeElement = OGSVGTreeElement(
        tagName = element.tagName,
        attributes = element.getAllAttributes(),
        style = mergedStyle,
        id = element.getAttribute("id"),
        children = mutableListOf(),
        shapes = shape?.let { mutableListOf(it) } ?: mutableListOf()
    )

    // Attach SMIL animation children (<animate>, <animateTransform>,
    // <animateMotion>) to THIS element. Shape elements (circle/rect/path/…) never
    // recurse into their children in the branches below, so without handling them
    // here these animation nodes would be dropped entirely and nothing would ever
    // animate.
    element.children.forEach { child ->
        when (child.tagName.lowercase()) {
            "animate", "animatetransform", "animatemotion" ->
                parseAnimationElement(child.tagName, child.getAllAttributes())
                    ?.let { currentTreeElement.animations.add(it) }
        }
    }

    // Special handling for <use> elements
    if (element.tagName.lowercase() == "use") {
        Logger.e("SVG>>buildSvgTree>> Processing <use> element: ${element.getAttribute("id")}")
        val success = resolveUseElement(
            useElement = element,
            root = rootTreeElement ?: currentTreeElement,
            parentElement = parentTreeElement ?: currentTreeElement,
            viewBox = viewBox
        )
        if (!success) {
            Logger.e("SVG>>buildSvgTree>> ERROR: <use> element failed to resolve.")
            return null
        }
    }
    // Handling container elements like <g>, <symbol>, <pattern>, and <svg>
    else if (element.tagName.lowercase() in listOf("g", "symbol", "pattern", "svg")) {
        Logger.e("SVG>>buildSvgTree>> Processing container element: ${element.tagName}")

        parentTreeElement?.children?.add(currentTreeElement)

        // Recursively process child elements
        element.children.forEach { childElement ->
            buildSVGTree(
                element = childElement,
                parentTreeElement = currentTreeElement,
                rootTreeElement = rootTreeElement ?: currentTreeElement,
                inheritedStyle = mergedStyle,
                definitions = definitions,
                symbolMap = symbolMap,
                viewBox = viewBox,
                gradients = gradients,
                patterns = patterns
            )
        }
    }
    // Non-container elements are directly added to their parent
    else {
        parentTreeElement?.children?.add(currentTreeElement)
    }

    return currentTreeElement
}


fun parseSVGElementAttributes(
    element: OGSVGElement,
    inheritedStyle: OGSVGStyle,
    gradients: Map<String, OGSVGGradient>? = null,
    patterns: Map<String, OGSVGPattern>? = null
): OGSVGStyle {
    val styleMap = parseStyleAttribute(element.getAttribute("style")) // Inline styles
    val presentationFill = element.getAttribute("fill")?.takeIf { it.isNotEmpty() }
    val presentationStroke = element.getAttribute("stroke")?.takeIf { it.isNotEmpty() }
    val presentationStrokeWidth = element.getAttribute("stroke-width")?.toFloatOrNull()

    // Determine if the fill is a pattern or a gradient
    val fillPatternId = when {
        styleMap["fill"]?.startsWith("url(#") == true -> {
            val refId = styleMap["fill"]?.removePrefix("url(#")?.removeSuffix(")")
            if (refId != null && patterns?.containsKey(refId) == true) refId else null
        }

        presentationFill?.startsWith("url(#") == true -> {
            val refId = presentationFill.removePrefix("url(#").removeSuffix(")")
            if (patterns?.containsKey(refId) == true) refId else null
        }

        else -> inheritedStyle.patternId
    }

    val fillGradientId = when {
        styleMap["fill"]?.startsWith("url(#") == true -> {
            val refId = styleMap["fill"]?.removePrefix("url(#")?.removeSuffix(")")
            if (refId != null && gradients?.containsKey(refId) == true) refId else null
        }

        presentationFill?.startsWith("url(#") == true -> {
            val refId = presentationFill.removePrefix("url(#").removeSuffix(")")
            if (gradients?.containsKey(refId) == true) refId else null
        }

        else -> inheritedStyle.fillGradientId
    }

    val strokeGradientId = when {
        styleMap["stroke"]?.startsWith("url(#") == true ->
            styleMap["stroke"]?.removePrefix("url(#")?.removeSuffix(")") ?: ""

        presentationStroke?.startsWith("url(#") == true ->
            presentationStroke?.removePrefix("url(#")?.removeSuffix(")") ?: ""

        else -> inheritedStyle.strokeGradientId
    }

    // Avoid setting a solid color if a gradient or pattern ID is present
    val fillColor = if (fillGradientId == null && fillPatternId == null) {
        when {
            styleMap["fill"] != null -> parseColor(styleMap["fill"], gradients, isFill = true)
            presentationFill != null -> parseColor(presentationFill, gradients, isFill = true)
            else -> inheritedStyle.fill
        }
    } else {
        null
    }

    val strokeColor = if (strokeGradientId == null) {
        when {
            styleMap["stroke"] != null -> parseColor(
                styleMap["stroke"],
                gradients,
                isFill = false
            )

            presentationStroke != null -> parseColor(
                presentationStroke,
                gradients,
                isFill = false
            )

            else -> inheritedStyle.stroke
        }
    } else {
        null
    }

    return inheritedStyle.copy(
        fill = fillColor,
        stroke = strokeColor,
        strokeWidth = styleMap["stroke-width"]?.toFloatOrNull()
            ?: presentationStrokeWidth
            ?: inheritedStyle.strokeWidth,
        patternId = fillPatternId, // Set pattern ID if detected
        fillGradientId = fillGradientId,
        strokeGradientId = strokeGradientId
    )
}

fun parseSVGElements(
    node: OGSVGElement,
    parent: OGSVGTreeElement,
    rootElement: OGSVGTreeElement,
    inheritedStyle: OGSVGStyle?,
    definitions: Map<String, OGSVGElement>,
    symbolMap: Map<String, OGSVGElement>,
    viewBox: ViewBox,
    gradients: Map<String, OGSVGGradient>
) {
    Logger.e("SVG>>parseSvgElements>> node: ${node.tagName}")

    val childNodes = node.children
    val localTransform = parseTransformAttributes(node.getAttribute("transform"))

    // Combine styles only if the transformation is not null, avoiding default values
    val mergedStyle =
        inheritedStyle?.combine(localTransform ?: OGSVGStyle()) ?: localTransform ?: OGSVGStyle()

    val currentTreeElement = OGSVGTreeElement(
        tagName = node.tagName,
        attributes = node.getAllAttributes(),
        style = mergedStyle,
        id = node.getAttribute("id"),
        children = mutableListOf()
    )

    // Attach current element to its parent in the tree
    parent.children.add(currentTreeElement)

    for (childElement in childNodes) {
        Logger.e("SVG>>parseSvgElements>> node: ${node.tagName} child: ${childElement.tagName}")

        parseSingleSVGElement(
            element = childElement,
            parent = currentTreeElement,
            rootElement = rootElement,
            style = mergedStyle,
            definitions = definitions,
            symbolMap = symbolMap,
            viewBox = viewBox,
            gradients = gradients
        )
    }
}

fun parseSingleSVGElement(
    element: OGSVGElement,
    parent: OGSVGTreeElement,
    rootElement: OGSVGTreeElement,
    style: OGSVGStyle,
    definitions: Map<String, OGSVGElement>,
    symbolMap: Map<String, OGSVGElement>,
    viewBox: ViewBox,
    gradients: Map<String, OGSVGGradient>
) {
    Logger.e("SVG>>parseSingleSvgElement>> node: ${element.tagName}")

    // Parse transformation attributes and merge with style
    val transform = parseTransformAttributes(element.getAttribute("transform"))
    val mergedStyle = style.combine(transform ?: OGSVGStyle())

    Logger.e("SVG>>parseSingleSvgElement>> node: ${element.tagName} style: $mergedStyle")

    // Determine the shape based on the element type
    val shape = when (element.tagName.lowercase()) {
        "path" -> {
            val d = element.getAttribute("d") ?: return
            OGSVGPath(parsePathCommands(d, viewBox, mergedStyle))
        }

        "line" -> parseLine(element, mergedStyle)
        "circle" -> parseCircle(element, mergedStyle)
        "ellipse" -> parseEllipse(element, mergedStyle)
        "polygon", "polyline" -> parsePolygon(element, mergedStyle)
        "rect" -> parseRect(element, mergedStyle)
        else -> null
    }

    // Create the SVG tree element, with shape if applicable
    val childNode = OGSVGTreeElement(
        tagName = element.tagName,
        attributes = element.getAllAttributes(),
        style = mergedStyle,
        id = element.getAttribute("id"),
        children = mutableListOf(),
        shapes = shape?.let { mutableListOf(it) } ?: mutableListOf()
    )

    // Handle specific SVG elements
    when (element.tagName.lowercase()) {
        "g", "symbol" -> {
            parseSVGElements(
                element,
                childNode,
                rootElement,
                style,
                definitions,
                symbolMap,
                viewBox,
                gradients
            )
        }

        "use" -> {
            resolveUseElement(
                useElement = element,
                root = rootElement,
                parentElement = parent,
                viewBox = viewBox
            )
        }

        else -> {
            Logger.w("SVG>>parseSingleSvgElement>> Unsupported tag: ${element.tagName}")
        }
    }

    // Add the newly created child node to its parent
    parent.children.add(childNode)
}


fun parseSingleSVGPath(
    pathData: String,
    viewBox: ViewBox,
    style: OGSVGStyle
): List<OGSVGCommand> {
    val commands = mutableListOf<OGSVGCommand>()
    val position = mutableListOf(0f, 0f) // [currentX, currentY]
    val firstMove = mutableListOf<Float?>(null, null) // [firstMoveX, firstMoveY]
    val lastControlPoint = mutableListOf<Float?>(null, null) // [last control point for S/s]

    var i = 0
    while (i < pathData.length) {
        val char = pathData[i]

        // Ignore whitespace and non-command characters
        if (!char.isLetter()) {
            i++
            continue
        }

        val remainingData = pathData.substring(i + 1)
        val values = extractNumbersFromPath(remainingData)

        val (parsedCommands, lastPos) = parseCommandWithAbsoluteCoords(
            commandChar = char,
            values = values,
            position = position,
            firstMove = firstMove,
            lastControlPoint = lastControlPoint,
            viewBox = viewBox,
            style = style
        )

        commands.addAll(parsedCommands)
        position[0] = lastPos.first
        position[1] = lastPos.second

        // Move to the next command character
        i += 1 + values.joinToString(" ").length
    }

    return commands
}

fun extractDefinitions(
    root: OGSVGElement,
    definitions: MutableMap<String, OGSVGElement>,
    symbolMap: MutableMap<String, OGSVGElement>
) {
    val stack = ArrayDeque<OGSVGElement>()
    stack.add(root)

    while (stack.isNotEmpty()) {
        val node = stack.removeFirst()
        Logger.e(
            "SVG>>extractDefinitions>> Processing ${node.tagName} with ID: ${
                node.getAttribute(
                    "id"
                )
            }"
        )

        when (node.tagName.lowercase()) {
            "symbol" -> {
                node.getAttribute("id")?.let { symbolMap[it] = node }
            }

            "defs" -> {
                stack.addAll(node.children) // Directly add children of <defs> to the stack
            }

            else -> {
                node.getAttribute("id")?.let { definitions[it] = node }
            }
        }

        // Add all children to the stack for traversal
        stack.addAll(node.children)

        Logger.e("SVG>>extractDefinitions>> Processed ${node.tagName} with ID: ${node.getAttribute("id")}")
    }
}


/* ---------------------- */
/*  COMMON SHAPE PARSING FUNCTIONS  */
/* ---------------------- */

fun parseLine(
    element: OGSVGElement,
    style: OGSVGStyle
): OGSVGShape? {
    val x1 = element.getAttribute("x1")?.toFloatOrNull() ?: 0f
    val y1 = element.getAttribute("y1")?.toFloatOrNull() ?: 0f
    val x2 = element.getAttribute("x2")?.toFloatOrNull() ?: 0f
    val y2 = element.getAttribute("y2")?.toFloatOrNull() ?: 0f

    val animations = element.animations?.toMutableList() // ✅ Copy animations if they exist

    return OGSVGLine(x1, y1, x2, y2).apply {
        if (!animations.isNullOrEmpty()) this.animations = animations
    }
}

fun parseCircle(
    element: OGSVGElement,
    style: OGSVGStyle
): OGSVGShape? {
    val cx = element.getAttribute("cx")?.toFloatOrNull() ?: 0f
    val cy = element.getAttribute("cy")?.toFloatOrNull() ?: 0f
    val r = element.getAttribute("r")?.toFloatOrNull() ?: 0f

    val animations = element.animations?.toMutableList() // ✅ Copy animations if they exist

    return OGSVGCircle(cx, cy, r).apply {
        if (!animations.isNullOrEmpty()) this.animations = animations
    }
}

fun parseEllipse(
    element: OGSVGElement,
    style: OGSVGStyle
): OGSVGShape? {
    val cx = element.getAttribute("cx")?.toFloatOrNull() ?: 0f
    val cy = element.getAttribute("cy")?.toFloatOrNull() ?: 0f
    val rx = element.getAttribute("rx")?.toFloatOrNull() ?: 0f
    val ry = element.getAttribute("ry")?.toFloatOrNull() ?: 0f
    val rotation = style.rotation ?: 0f

    val animations = element.animations?.toMutableList() // ✅ Copy animations if they exist

    return OGSVGEllipse(cx, cy, rx, ry, rotation).apply {
        if (!animations.isNullOrEmpty()) this.animations = animations
    }
}

fun parsePolygon(
    element: OGSVGElement,
    style: OGSVGStyle
): OGSVGShape? {
    val points = element.getAttribute("points") ?: ""

    val commands = if (points.isNotEmpty()) {
        parsePolygonPath(points, element.tagName.lowercase() == "polygon")
    } else {
        emptyList()
    }

    val animations = element.animations?.toMutableList() // ✅ Copy animations if they exist

    return OGSVGPath(commands).apply {
        if (!animations.isNullOrEmpty()) this.animations = animations
    }
}

fun parseRect(
    element: OGSVGElement,
    style: OGSVGStyle
): OGSVGShape? {
    val x = element.getAttribute("x")?.toFloatOrNull() ?: 0f
    val y = element.getAttribute("y")?.toFloatOrNull() ?: 0f
    val width = element.getAttribute("width")?.toFloatOrNull() ?: 0f
    val height = element.getAttribute("height")?.toFloatOrNull() ?: 0f
    val rx = element.getAttribute("rx")?.toFloatOrNull() ?: 0f
    val ry = element.getAttribute("ry")?.toFloatOrNull() ?: 0f

    val animations = element.animations?.toMutableList() // ✅ Copy animations if they exist

    return OGSVGRect(x, y, width, height, rx, ry).apply {
        if (!animations.isNullOrEmpty()) this.animations = animations
    }
}


fun logSVGTree(element: OGSVGTreeElement, indentLevel: Int = 0) {
    // Generate indentation based on the level of the tree
    val indent = "    ".repeat(indentLevel)

    // Log the current element with its type and style
    println("$indent- ${element.tagName} (ID: ${element.id ?: "N/A"})")
    println("$indent  Style: ${element.style}")

    // If the element has shapes, log them as well
    if (element.shapes.isNotEmpty()) {
        println("$indent  Shapes:")
        element.shapes.forEachIndexed { index, shape ->
            println(
                "$indent    [${index + 1}] Type: ${shape::class.simpleName}, " +
                        "Style: ${element.style}"
            )
        }
    }

    // Recursively log child elements
    if (element.children.isNotEmpty()) {
        println("$indent  Children:")
        element.children.forEach { child ->
            logSVGTree(child, indentLevel + 1)
        }
    }
}


fun deepCopySVGTreeElementNonRecursive(
    original: OGSVGTreeElement,
    inheritedStyle: OGSVGStyle
): OGSVGTreeElement {

    Logger.e("SVG>>deepCopySvgTreeElementNonRecursive>> Copying element: ${original.tagName} with ID: ${original.id}")

    // Create the root copy separately to avoid double processing
    val rootCopy = original.copy(
        children = mutableListOf(),
        shapes = original.shapes.map { shape ->
            when (shape) {
                is OGSVGLine -> shape.copy(
                    x1 = shape.x1,
                    y1 = shape.y1,
                    x2 = shape.x2,
                    y2 = shape.y2
                )

                is OGSVGCircle -> shape.copy(
                    cx = shape.cx,
                    cy = shape.cy,
                    r = shape.r
                )

                is OGSVGEllipse -> shape.copy(
                    cx = shape.cx,
                    cy = shape.cy,
                    rx = shape.rx,
                    ry = shape.ry,
                    rotation = shape.rotation
                )

                is OGSVGRect -> shape.copy(
                    x = shape.x,
                    y = shape.y,
                    width = shape.width,
                    height = shape.height,
                    rx = shape.rx,
                    ry = shape.ry
                )

                is OGSVGPolygon -> shape.copy(
                    points = shape.points.toList() // Ensure a new list is created
                )

                is OGSVGPath -> shape.copy(
                    commands = shape.commands.toList(), // Copy commands list
                )

                else -> shape
            }
        }.toMutableList(),
        style = inheritedStyle,
        id = generateRandomId() // Assign a random ID to the cloned root element
    )

    Logger.i("SVG>>deepCopySvgTreeElementNonRecursive>> Copied root element: ${rootCopy.tagName} with ID: ${rootCopy.id} style: ${rootCopy.style}")
    Logger.i("SVG>>deepCopySvgTreeElementNonRecursive>> Inherited style: $inheritedStyle")

    // Stack to manage elements during the iterative deep copy process
    val stack = ArrayDeque<Pair<OGSVGTreeElement, OGSVGTreeElement>>()

    // Initialize the stack with the children of the root element
    original.children.forEach { child ->
        Logger.i("SVG>>deepCopySvgTreeElementNonRecursive>> Adding child: ${child.tagName} with ID: ${child.id} to stack")
        stack.add(Pair(child, rootCopy))
    }

    // Iterate through the elements in the stack
    while (stack.isNotEmpty()) {
        val (currentOriginal, parentCopy) = stack.removeFirst()

        Logger.i("SVG>>deepCopySvgTreeElementNonRecursive>> Processing child: ${currentOriginal.tagName} with ID: ${currentOriginal.id}")

        // Create a copy of the current element
        val elementCopy = currentOriginal.copy(
            children = mutableListOf(),
            shapes = currentOriginal.shapes.map { shape ->
                when (shape) {
                    is OGSVGLine -> shape.copy(
                        x1 = shape.x1,
                        y1 = shape.y1,
                        x2 = shape.x2,
                        y2 = shape.y2
                    )

                    is OGSVGCircle -> shape.copy(
                        cx = shape.cx,
                        cy = shape.cy,
                        r = shape.r
                    )

                    is OGSVGEllipse -> shape.copy(
                        cx = shape.cx,
                        cy = shape.cy,
                        rx = shape.rx,
                        ry = shape.ry,
                        rotation = shape.rotation
                    )

                    is OGSVGRect -> shape.copy(
                        x = shape.x,
                        y = shape.y,
                        width = shape.width,
                        height = shape.height,
                        rx = shape.rx,
                        ry = shape.ry
                    )

                    is OGSVGPolygon -> shape.copy(
                        points = shape.points.toList() // Ensure a new list is created
                    )

                    is OGSVGPath -> shape.copy(
                        commands = shape.commands.toList() // Copy commands list
                    )

                    else -> shape
                }
            }.toMutableList(),
            style = currentOriginal.style,
            id = generateRandomId() // Assign a random ID to each cloned child element
        )

        Logger.i("SVG>>deepCopySvgTreeElementNonRecursive>> Copied child: ${elementCopy.tagName} style: ${elementCopy.style}")

        // Add the copy to its parent's children list
        parentCopy.children.add(elementCopy)

        // Add all children of the current element to the stack for processing
        currentOriginal.children.forEach { child ->
            stack.add(Pair(child, elementCopy))
        }
    }

    return rootCopy
}


fun resolveUseElement(
    useElement: OGSVGElement,
    parentElement: OGSVGTreeElement, // Parent of the <use> element
    root: OGSVGTreeElement, // Root of the SVG tree for global reference search
    viewBox: ViewBox
): Boolean {
    Logger.e("SVG>>resolveUseElement>> Initial parent children size: ${parentElement.children.size}")

    Logger.e("SVG>>resolveUseElement>> parent: ${parentElement.tagName} with ID: ${parentElement.id} style: ${parentElement.style}")
    Logger.w("SVG>>resolveUseElement>>>>> CURRENT SVG TREE")

    val href = useElement.getAttribute("href")?.takeIf { it.isNotEmpty() }
        ?: useElement.getAttribute("xlink:href")?.takeIf { it.isNotEmpty() }

    if (href.isNullOrEmpty()) {
        Logger.e("SVG>>resolveUseElement>> ERROR: 'use' element missing href attribute.")
        return false
    }

    val refId = href.removePrefix("#")

    // Locate the reference element directly from the root of the SVG tree
    val referencedElement = findElementById(root, refId)
    if (referencedElement == null) {
        Logger.e("SVG>>resolveUseElement>> ERROR: Referenced element with ID '$refId' not found.")
        return false
    }

    Logger.i("SVG>>resolveUseElement>> Found referenced element ID: $refId (${referencedElement.tagName})")

    // Perform a deep clone of the referenced element with a fresh style
    val clonedElement = deepCopySVGTreeElementNonRecursive(
        original = referencedElement,
        inheritedStyle = referencedElement.style
    )
    clonedElement.id = generateRandomId()

    Logger.e("SVG>>resolveUseElement>> Cloned element: ${clonedElement.tagName} with ID: ${clonedElement.id} style: ${clonedElement.style}")

    // Apply styles and transformations from the `use` element to the cloned element
    applyAttributesFromUseElement(clonedElement, useElement)

    Logger.e("SVG>>resolveUseElement>> Cloned element after applying use attributes: ${clonedElement.tagName} with ID: ${clonedElement.id} style: ${clonedElement.style}")

    if (clonedElement.tagName == "g") {
        clonedElement.children.forEach {
            Logger.e("SVG>>resolveUseElement>> Cloned element g child: ${it.tagName} with ID: ${it.id} style: ${it.style}")
        }
    }

    // Avoid adding the cloned element if it already exists in the parent
    if (parentElement.children.any { it.id == clonedElement.id }) {
        Logger.w("SVG>>resolveUseElement>> WARNING: Element with ID ${clonedElement.id} already exists in parent. Skipping addition.")
        return false
    }

    // Add the cloned element to the parent
    parentElement.children.add(clonedElement)
    Logger.e("SVG>>resolveUseElement>> Successfully added cloned element. Parent children size: ${parentElement.children.size}")

    return true
}


fun applyAttributesFromUseElement(
    targetElement: OGSVGTreeElement,
    useElement: OGSVGElement,
    gradients: Map<String, OGSVGGradient>? = null
) {
    val stack = ArrayDeque<OGSVGTreeElement>()
    stack.add(targetElement)

    while (stack.isNotEmpty()) {
        val currentElement = stack.removeFirst()

        var finalStyle = currentElement.style

        // Fill handling
        useElement.getAttribute("fill")?.takeIf { it.isNotEmpty() && it != "none" }?.let { fill ->
            finalStyle = if (fill.startsWith("url(#") && fill.endsWith(")")) {
                finalStyle.copy(fillGradientId = fill.removePrefix("url(#").removeSuffix(")"))
            } else {
                finalStyle.copy(fill = parseColor(fill, gradients, isFill = true))
            }
        }

        // Stroke handling
        useElement.getAttribute("stroke")?.takeIf { it.isNotEmpty() && it != "none" }
            ?.let { stroke ->
                finalStyle = if (stroke.startsWith("url(#") && stroke.endsWith(")")) {
                    finalStyle.copy(
                        strokeGradientId = stroke.removePrefix("url(#").removeSuffix(")")
                    )
                } else {
                    finalStyle.copy(stroke = parseColor(stroke, gradients, isFill = false))
                }
            }

        // Stroke width
        useElement.getAttribute("stroke-width")?.toFloatOrNull()?.let { strokeWidth ->
            finalStyle = finalStyle.copy(strokeWidth = strokeWidth)
        }

        // Parse and combine transform attributes
        parseTransformAttributes(useElement.getAttribute("transform"))?.let { useTransform ->
            finalStyle = finalStyle.combine(useTransform)
        }

        // Apply individual transformation attributes
        useElement.getAttribute("translateX")?.toFloatOrNull()?.let { translateX ->
            finalStyle = finalStyle.copy(translateX = translateX)
        }

        useElement.getAttribute("translateY")?.toFloatOrNull()?.let { translateY ->
            finalStyle = finalStyle.copy(translateY = translateY)
        }

        useElement.getAttribute("scaleX")?.toFloatOrNull()?.let { scaleX ->
            finalStyle = finalStyle.copy(scaleX = scaleX)
        }

        useElement.getAttribute("scaleY")?.toFloatOrNull()?.let { scaleY ->
            finalStyle = finalStyle.copy(scaleY = scaleY)
        }

        useElement.getAttribute("rotation")?.toFloatOrNull()?.let { rotation ->
            finalStyle = finalStyle.copy(rotation = rotation)
        }

        useElement.getAttribute("rotationCx")?.toFloatOrNull()?.let { rotationCx ->
            finalStyle = finalStyle.copy(rotationCx = rotationCx)
        }

        useElement.getAttribute("rotationCy")?.toFloatOrNull()?.let { rotationCy ->
            finalStyle = finalStyle.copy(rotationCy = rotationCy)
        }

        useElement.getAttribute("skewX")?.toFloatOrNull()?.let { skewX ->
            finalStyle = finalStyle.copy(skewX = skewX)
        }

        useElement.getAttribute("skewY")?.toFloatOrNull()?.let { skewY ->
            finalStyle = finalStyle.copy(skewY = skewY)
        }

        // Log updated style
        Logger.e("SVG>>applyAttributesFromUseElement>> Updated element ID: ${currentElement.id} with style: ${finalStyle}")

        // Apply the final computed style to the current element
        currentElement.style = finalStyle

        // Process child elements recursively
        stack.addAll(currentElement.children)
    }
}

fun applyStyleToSVGTree(rootElement: OGSVGTreeElement, style: OGSVGStyle) {
    val stack = ArrayDeque<OGSVGTreeElement>()
    stack.add(rootElement)

    while (stack.isNotEmpty()) {
        val currentElement = stack.removeFirst()

        // Apply the style to the current element
        currentElement.style = style

        // Add children to the stack for further processing
        stack.addAll(currentElement.children)
    }
}


private fun findElementById(root: OGSVGTreeElement, id: String): OGSVGTreeElement? {
    // Use a stack to traverse the tree non-recursively
    val stack = ArrayDeque<OGSVGTreeElement>()
    stack.add(root)

    while (stack.isNotEmpty()) {
        val current = stack.removeLast() // Pop from the stack

        // Check if the current element matches the desired ID
        if (current.id == id) {
            return current
        }

        // Add all children to the stack for further exploration
        stack.addAll(current.children)
    }

    return null // Return null if no matching element is found
}

fun parseTransformAttributes(transform: String?): OGSVGStyle? {
    if (transform.isNullOrEmpty()) return null

    var scaleX: Float? = null
    var scaleY: Float? = null
    var translateX: Float? = null
    var translateY: Float? = null
    var rotation: Float? = null
    var rotationCx: Float? = null
    var rotationCy: Float? = null
    var skewX: Float? = null
    var skewY: Float? = null

    val regexTransform = """(\w+)\(([^)]*)\)""".toRegex()
    val matches = regexTransform.findAll(transform)

    for (match in matches) {
        val transformType = match.groups[1]?.value
        val values =
            match.groups[2]?.value?.split(Regex("[,\\s]+"))?.mapNotNull { it.toFloatOrNull() }
                ?: continue

        when (transformType) {
            "translate" -> {
                translateX = values.getOrNull(0)
                translateY = values.getOrNull(1)
            }

            "scale" -> {
                scaleX = values.getOrNull(0)
                scaleY = values.getOrNull(1) ?: scaleX
            }

            "rotate" -> {
                rotation = values.getOrNull(0)
                rotationCx = values.getOrNull(1)
                rotationCy = values.getOrNull(2)
            }

            "skewX" -> {
                skewX = values.getOrNull(0)
            }

            "skewY" -> {
                skewY = values.getOrNull(0)
            }

            "matrix" -> {
                if (values.size == 6) {
                    val a = values[0]
                    val b = values[1]
                    val c = values[2]
                    val d = values[3]
                    translateX = values[4]
                    translateY = values[5]

                    scaleX = sqrt(a * a + b * b)
                    scaleY = sqrt(c * c + d * d)
                    skewX = atan2(b, a) * (180f / PI.toFloat())
                    skewY = atan2(c, d) * (180f / PI.toFloat())
                }
            }
        }
    }

    return OGSVGStyle(
        scaleX = scaleX,
        scaleY = scaleY,
        translateX = translateX,
        translateY = translateY,
        rotation = rotation,
        rotationCx = rotationCx,
        rotationCy = rotationCy,
        skewX = skewX,
        skewY = skewY
    ).takeIf { style ->
        // Return null if all attributes are null
        listOf(
            scaleX, scaleY, translateX, translateY, rotation,
            rotationCx, rotationCy, skewX, skewY
        ).any { it != null }
    }
}


/**
 * Extracts rotation from `transform="rotate(...)"`
 * If transform="rotate(45, cx, cy)", we ignore `cx, cy`.
 */
fun extractRotationAngle(transform: String?): Float {
    if (transform == null) return 0f
    val regex = """rotate\((-?\d+\.?\d*)""".toRegex()
    val match = regex.find(transform) ?: return 0f
    return match.groupValues[1].toFloatOrNull() ?: 0f
}

/**
 * For an SVG that might have `<svg onclick="someGroup()">`, parse out `someGroup`.
 */
fun parseInitialGroup(svgContent: String): String? {
    val onclickRegex = Regex("""<svg[^>]*onclick=["']([^"']+)["']""")
    val matchResult = onclickRegex.find(svgContent)
    return matchResult?.groups?.get(1)?.value?.substringBefore('(')?.trim()
}

// ---------------------- //
//      PATH PARSING      //
// ---------------------- //

fun parsePathCommands(
    d: String,
    viewBox: ViewBox,
    style: OGSVGStyle = OGSVGStyle()
): List<OGSVGCommand> {
    val commandRegex = Regex("([a-zA-Z])|(-?\\d*\\.?\\d+(?:e[-+]?\\d+)?)")
    val matches = commandRegex.findAll(d)

    val commands = mutableListOf<OGSVGCommand>()
    var currentCommand: Char? = null
    val currentValues = mutableListOf<Float>()
    val position = mutableListOf(0f, 0f) // ✅ [curX, curY]
    val firstMove = mutableListOf<Float?>(null, null) // ✅ [firstMoveX, firstMoveY]
    val scale = mutableListOf(style.scaleX, style.scaleY) // ✅ [scaleX, scaleY]
    val lastControlPoint = mutableListOf<Float?>(null, null)  // ✅ [lastControlX, lastControlY]

    for (match in matches) {
        val token = match.value
        if (token[0].isLetter()) {
            // parse previous command
            if (currentCommand != null) {
                val (subCommands, newCursor) = parseCommandWithAbsoluteCoords(
                    commandChar = currentCommand,
                    values = currentValues,
                    position,
                    firstMove,
                    lastControlPoint,
                    viewBox,
                    style
                )
                commands.addAll(subCommands)
                position[0] = newCursor.first
                position[1] = newCursor.second
            }
            currentCommand = token[0]
            currentValues.clear()
        } else {
            currentValues.add(token.toFloat())
        }
    }

    // Handle final command
    if (currentCommand != null) {
        val (subCommands, newCursor) = parseCommandWithAbsoluteCoords(
            commandChar = currentCommand,
            values = currentValues,
            position,
            firstMove,
            lastControlPoint,
            viewBox,
            style
        )
        commands.addAll(subCommands)

        position[0] = newCursor.first
        position[1] = newCursor.second
    }

    return commands
}


/**
 * Converts uppercase vs. lowercase (absolute vs. relative).
 * Example: 'm 10 20' => adds (10,20) to currentX/Y
 */
private fun parseCommandWithAbsoluteCoords(
    commandChar: Char,
    values: List<Float>,
    position: MutableList<Float>,  // ✅ Stores [curX, curY] (mutable reference)
    firstMove: MutableList<Float?>, // ✅ Stores [firstMoveX, firstMoveY] (mutable reference)
    lastControlPoint: MutableList<Float?>,
    viewBox: ViewBox,
    style: OGSVGStyle
): Pair<List<OGSVGCommand>, Pair<Float, Float>> {
    val result = mutableListOf<OGSVGCommand>()

    val isRelative = commandChar.isLowerCase()
    val type = commandChar.uppercaseChar()

    fun Float.toAbsX() =
        (if (isRelative) position[0] + this * (style.scaleX ?: 1f) else this * (style.scaleX ?: 1f))

    fun Float.toAbsY() =
        (if (isRelative) position[1] + this * (style.scaleY ?: 1f) else this * (style.scaleY ?: 1f))

    var i = 0
    while (i < values.size) {
        when (type) {
            'M' -> {
                val x = values[i++].toAbsX()
                val y = values[i++].toAbsY()

                if (firstMove[0] == null && firstMove[1] == null) {
                    firstMove[0] = x
                    firstMove[1] = y
                }

                if (result.isEmpty()) {
                    result.add(MoveTo(x, y)) // First pair => MoveTo
                } else {
                    result.add(LineTo(x, y)) // Subsequent pairs => LineTo
                }
                position[0] = x
                position[1] = y
                lastControlPoint[0] = null
                lastControlPoint[1] = null
            }

            'L' -> {
                val x = values[i++].toAbsX()
                val y = values[i++].toAbsY()
                result.add(LineTo(x, y))

                position[0] = x
                position[1] = y
                lastControlPoint[0] = null
                lastControlPoint[1] = null
            }

            'H' -> {
                val x = values[i++].toAbsX()
                result.add(LineTo(x, position[1]))
                position[0] = x
                lastControlPoint[0] = null
                lastControlPoint[1] = null
            }

            'V' -> {
                val y = values[i++].toAbsY()
                result.add(LineTo(position[0], y))
                position[1] = y
                lastControlPoint[0] = null
                lastControlPoint[1] = null
            }

            'C' -> {
                val x1 = values[i++].toAbsX()
                val y1 = values[i++].toAbsY()
                val x2 = values[i++].toAbsX()
                val y2 = values[i++].toAbsY()
                val x = values[i++].toAbsX()
                val y = values[i++].toAbsY()
                result.add(CurveTo(x1, y1, x2, y2, x, y))

                position[0] = x
                position[1] = y
                lastControlPoint[0] = x2
                lastControlPoint[1] = y2
            }

            'S' -> {
                val x2 = values[i++].toAbsX()
                val y2 = values[i++].toAbsY()
                val x = values[i++].toAbsX()
                val y = values[i++].toAbsY()

                val mirroredX1 = lastControlPoint[0]?.let { 2 * position[0] - it } ?: position[0]
                val mirroredY1 = lastControlPoint[1]?.let { 2 * position[1] - it } ?: position[1]

                result.add(CurveTo(mirroredX1, mirroredY1, x2, y2, x, y))

                lastControlPoint[0] = x2
                lastControlPoint[1] = y2
                position[0] = x
                position[1] = y
            }

            'A' -> {
                val rx = values[i++]
                val ry = values[i++]
                val rotation = values[i++]
                val largeArcFlag = values[i++] != 0f
                val sweepFlag = values[i++] != 0f
                val x = values[i++].toAbsX()
                val y = values[i++].toAbsY()

                val bezierSegments = svgArcToBezierChrome(
                    x0 = position[0].toDouble(), y0 = position[1].toDouble(),
                    x1 = x.toDouble(), y1 = y.toDouble(),
                    rx = rx.toDouble(), ry = ry.toDouble(),
                    xAxisRotation = rotation.toDouble(),
                    largeArcFlag = largeArcFlag,
                    sweepFlag = sweepFlag
                )

                if (bezierSegments.isNotEmpty()) {
                    bezierSegments.forEach { segment ->
                        result.add(
                            CurveTo(
                                segment[0].toFloat(),
                                segment[1].toFloat(),
                                segment[2].toFloat(),
                                segment[3].toFloat(),
                                segment[4].toFloat(),
                                segment[5].toFloat()
                            )
                        )
                    }
                }

                position[0] = x
                position[1] = y
                lastControlPoint[0] = null
                lastControlPoint[1] = null
            }

            'Z' -> {
                lastControlPoint[0] = null
                lastControlPoint[1] = null
            }

            else -> {
                Logger.w("SVG>> Unsupported path command '$type' (values=${values.size}); skipping")
                i = values.size
            }
        }
    }

    if (type == 'Z') {
        val firstX = firstMove[0] ?: position[0]
        val firstY = firstMove[1] ?: position[1]

        result.add(LineTo(firstX, firstY))
    }

    return Pair(result, Pair(position[0], position[1]))
}


// -------------------- //
//    VIEWBOX PARSER    //
// -------------------- //

fun parseViewBox(svgContent: String): ViewBox {
    val regex = Regex("""viewBox="([\d.-]+) ([\d.-]+) ([\d.-]+) ([\d.-]+)"""")
    val match = regex.find(svgContent)
    return if (match != null) {
        val (minX, minY, w, h) = match.destructured
        ViewBox(minX.toFloat(), minY.toFloat(), w.toFloat(), h.toFloat())
    } else {
        ViewBox(0f, 0f, 100f, 100f)
    }
}

// -------------------- //
//  COLOR PARSING LOGIC //
// -------------------- //

fun parseColor(
    colorString: String?,
    gradients: Map<String, OGSVGGradient>? = null,
    isFill: Boolean = true
): OGColorBase {
    Logger.e("Parsing OGColorBase: $colorString (isFill=$isFill)")
    Logger.e("SVG>>parseOGColorBase>> Gradient Count: ${gradients?.size}")

    // ✅ Default Colors
    if (colorString.isNullOrEmpty()) {
        return if (isFill) OGColor(Color.Black) else OGColor(Color.Transparent)
    }

    if (colorString == "none") return OGColor(Color.Transparent)

    // ✅ Handle Gradients
    if (colorString.startsWith("url(#") && colorString.endsWith(")")) {
        val gradientId = colorString.removePrefix("url(#").removeSuffix(")")
        val gradient = gradients?.get(gradientId)
        if (gradient != null) {
            Logger.e("Applying gradient: $gradientId")
            return gradient.brush
        }
        return OGColor(Color.Transparent)
    }

    // ✅ Handle Hex Colors
    if (colorString.startsWith("#")) {
        return try {
            OGColor(parseHexColor(colorString))
        } catch (e: Exception) {
            Logger.e("Error parsing hex color: $colorString. Using default transparent.")
            OGColor(Color.Transparent)
        }
    }

    // ✅ Named Colors
    val namedColor = parseNamedColor(colorString)
    if (namedColor != null) return OGColor(namedColor)

    // ✅ RGB(A) Colors
    val rgbColor = parseRgbColor(colorString)
    if (rgbColor != null) return OGColor(rgbColor)

    Logger.e("Unknown color: $colorString. Using default transparent.")
    return OGColor(Color.Transparent)
}


fun parseRgbColor(colorString: String): Color? {
    val regex = """rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([0-9.]+))?\)""".toRegex()
    val match = regex.find(colorString)
    return if (match != null) {
        val (r, g, b, a) = match.destructured
        Color(
            red = r.toInt() / 255f,
            green = g.toInt() / 255f,
            blue = b.toInt() / 255f,
            alpha = a.toFloatOrNull() ?: 1f
        )
    } else {
        Logger.e("Error parsing RGB color: $colorString. Using transparent.")
        Color.Transparent
    }
}

fun parseNamedColor(colorString: String): Color? {
    Logger.e("Named color: $colorString")
    return when (colorString.lowercase()) {
        "black" -> Color.Black
        "white" -> Color.White
        "red" -> Color.Red
        "brown" -> Color(0xFFA52A2A)
        "green" -> Color.Green
        "blue" -> Color.Blue
        "purple" -> Color(128 / 255f, 0f, 128 / 255f, 1f) // #800080
        "yellow" -> Color.Yellow
        "orange" -> Color(1.0f, 0.647f, 0.0f, 1.0f)
        "cyan" -> Color.Cyan
        "magenta" -> Color.Magenta
        "gray" -> Color.Gray
        "lightyellow" -> Color(1f, 1f, 0.88f, 1f)
        "darkred" -> Color(139 / 255f, 0f, 0f, 1f)
        "goldenrod" -> Color(218 / 255f, 165 / 255f, 32 / 255f, 1f)
        "transparent" -> Color.Transparent
        else -> {
            Logger.e("Unknown color name: $colorString. Using transparent.")
            null
        }
    }
}

private fun parseHexColor(hex: String): Color {
    Logger.e("parseHexColor>> Received hex string: $hex")

    val cleanHex = hex.removePrefix("#")

    val expandedHex = when (cleanHex.length) {
        3 -> cleanHex.map { "$it$it" }.joinToString("") // Expand "#3A5" → "#33AA55"
        6, 8 -> cleanHex
        else -> {
            Logger.e("Invalid hex color: $hex. Using transparent.")
            return Color.Transparent
        }
    }

    return try {
        val colorLong = expandedHex.toLong(16)
        val alpha = if (expandedHex.length == 8) ((colorLong shr 24) and 0xFF) / 255f else 1f
        val r = ((colorLong shr 16) and 0xFF) / 255f
        val g = ((colorLong shr 8) and 0xFF) / 255f
        val b = (colorLong and 0xFF) / 255f
        val parsedColor = Color(r, g, b, alpha)

        Logger.e("Parsed Hex Color: r=$r, g=$g, b=$b, alpha=$alpha")
        parsedColor
    } catch (e: Exception) {
        Logger.e("Error parsing hex color: $hex. Using transparent.")
        Color.Transparent
    }
}


// --------------------- //
//  POLYGON & GRADIENTS //
// --------------------- //

fun parsePolygonPath(points: String, isPolygon: Boolean): List<OGSVGCommand> {
    val commands = mutableListOf<OGSVGCommand>()
    val values = points.trim().split(Regex("[,\\s]+")).mapNotNull { it.toFloatOrNull() }
    if (values.size < 4) return commands

    // MoveTo first point
    commands.add(MoveTo(values[0], values[1]))
    // Then lineTo subsequent
    for (i in 2 until values.size step 2) {
        commands.add(LineTo(values[i], values[i + 1]))
    }
    if (isPolygon) {
        commands.add(ClosePath())
    }
    return commands
}

fun parseSVGGradients(svgContent: String): Map<String, OGSVGGradient> {
    val gradients = mutableMapOf<String, OGSVGGradient>()
    val visited = mutableSetOf<String>()

    val defsRegex = Regex("""<defs>([\s\S]*?)</defs>""")
    val defsMatch = defsRegex.find(svgContent)?.groupValues?.get(1) ?: return gradients

    val gradientTypes = listOf("linearGradient", "radialGradient")

    gradientTypes.forEach { tag ->
        val gradientRegex = Regex("""<$tag\s+[^>]*id=["']([^"']+)["'][^>]*>([\s\S]*?)</$tag>""")

        gradientRegex.findAll(defsMatch).forEach { match ->
            val id = match.groupValues[1]
            var gradientContent = match.groupValues[2]

            Logger.e("✅ Found $tag with ID: $id")

            // Check for xlink:href for gradient inheritance
            val hrefMatch = Regex("""xlink:href=["']#([^"']+)["']""").find(match.value)
            var referencedId = hrefMatch?.groupValues?.get(1)

            val stops = mutableListOf<Pair<Float, Color>>()
            val stopRegex =
                Regex("""<stop\s+[^>]*offset=["']([\d.]+%)["']\s+stop-color=["']([^"']+)["'][^>]*\/?>""")

            stopRegex.findAll(gradientContent).forEach { stopMatch ->
                val offset = stopMatch.groupValues[1].removeSuffix("%").toFloat() / 100f
                val color = parseColor(stopMatch.groupValues[2])

                // parseColor returns OGColorBase; only a solid OGColor is a valid
                // gradient stop. Guard the cast so a non-solid value (e.g. a
                // nested url(...) resolving to an OGBrush) is skipped instead of
                // throwing ClassCastException.
                val solid = (color as? OGColor)?.color
                if (solid != null) {
                    stops.add(offset to solid)
                    Logger.e("  ➡️ Added stop: offset=$offset, color=$solid")
                } else {
                    Logger.e("  ⚠️ Skipped stop with non-solid color: ${stopMatch.groupValues[2]}")
                }
            }

            // Handle recursive xlink:href resolution
            val resolvedStops = mutableListOf<Pair<Float, Color>>()
            val visitedRefs = mutableSetOf<String>()

            while (referencedId != null && !visitedRefs.contains(referencedId)) {
                visitedRefs.add(referencedId)
                val referencedGradient = gradients[referencedId]

                if (referencedGradient != null) {
                    resolvedStops.addAll(referencedGradient.brush.colorStops)
                    Logger.e("🔗 Merged stops from referenced gradient: $referencedId")
                }

                // Check if the referenced gradient itself has an xlink:href
                val refGradientRegex =
                    Regex("""id=["']$referencedId["'][^>]*xlink:href=["']#([^"']+)["']""")
                referencedId = refGradientRegex.find(defsMatch)?.groupValues?.get(1)
            }

            // Merge stops with priority to local gradient stops
            if (resolvedStops.isNotEmpty()) {
                stops.addAll(resolvedStops)
                stops.sortBy { it.first }
            }

            val OGGradientType: OGGradientType = if (tag == "linearGradient") {
                OGLinearGradientType(
                    x1 = extractAttribute(match.value, "x1")?.toFloatOrNull() ?: 0f,
                    y1 = extractAttribute(match.value, "y1")?.toFloatOrNull() ?: 0f,
                    x2 = extractAttribute(match.value, "x2")?.toFloatOrNull() ?: 1f,
                    y2 = extractAttribute(match.value, "y2")?.toFloatOrNull() ?: 0f
                )
            } else {
                OGRadialGradientType(
                    cx = extractAttribute(match.value, "cx")?.toFloatOrNull() ?: 0.5f,
                    cy = extractAttribute(match.value, "cy")?.toFloatOrNull() ?: 0.5f,
                    r = extractAttribute(match.value, "r")?.toFloatOrNull() ?: 0.5f
                )
            }

            if (stops.isNotEmpty()) {
                gradients[id] =
                    OGSVGGradient(id, OGBrush(type = OGGradientType, colorStops = stops))
                Logger.e("✅ Stored gradient: ID=$id, Stops=${stops.size}")
            } else {
                Logger.e("⚠️ Skipped gradient: ID=$id, no stops found!")
            }
        }
    }

    Logger.i("✅ Found ${gradients.size} gradients!")
    return gradients
}


fun extractAttribute(attributes: String, attributeName: String): String? {
    val regex = """$attributeName="([^"]*)"""".toRegex()
    return regex.find(attributes)?.groups?.get(1)?.value?.takeIf { it.isNotBlank() }
}


fun parseStyleAttribute(style: String?): Map<String, String> {
    val attributes = mutableMapOf<String, String>()
    if (!style.isNullOrEmpty()) {
        style.split(";").forEach { rule ->
            // Split on the FIRST ':' only: a value may itself contain colons
            // (e.g. `fill:url(#g)`, `background:url(http://…)`), so an unlimited
            // split would produce >2 parts and the rule would be dropped.
            val (key, value) = rule.split(":", limit = 2).map { it.trim() }
                .takeIf { it.size == 2 && it[0].isNotEmpty() }
                ?: return@forEach
            attributes[key] = value
        }
    }
    return attributes
}
