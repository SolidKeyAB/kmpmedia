package com.solidkey.painpoints.image.svg

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// -----------------------------------------------------------------------------
//  SVG elliptical-arc ("A") -> cubic Bézier conversion.
//
//  Compose has no native elliptical-arc primitive, so each arc is approximated
//  with a chain of cubic Bézier segments using the endpoint -> centre
//  parameterisation from the SVG implementation notes (appendix F.6).
//
//  Two entry points are used by the library:
//   * svgArcToBezier(...) / svgArcToBezierChrome(...) — used by the parser to
//     expand "A" commands into CurveTo segments while tokenizing the "d" string.
//   * approximateArcPath(...) — builds a standalone Path for a single arc, used
//     by the renderer / animation player when an ArcTo is drawn directly.
// -----------------------------------------------------------------------------

/**
 * Converts one arc segment (between two polar angles) into a single cubic Bézier
 * control quadruple, returned as [c1x, c1y, c2x, c2y, endX, endY].
 */
fun arcSegmentToBezier(
    cx: Double, cy: Double,
    rx: Double, ry: Double,
    theta1: Double, theta2: Double,
    rotation: Double
): DoubleArray {
    val deltaTheta = (theta2 - theta1) / 2.0
    val alpha = sin(deltaTheta) * (4.0 / 3.0) / (1.0 + cos(deltaTheta))

    val cosTheta1 = cos(theta1)
    val sinTheta1 = sin(theta1)
    val cosTheta2 = cos(theta2)
    val sinTheta2 = sin(theta2)

    val x1 = cx + rx * cosTheta1
    val y1 = cy + ry * sinTheta1
    val x2 = cx + rx * cosTheta2
    val y2 = cy + ry * sinTheta2

    val c1x = x1 - alpha * rx * sinTheta1
    val c1y = y1 + alpha * ry * cosTheta1
    val c2x = x2 + alpha * rx * sinTheta2
    val c2y = y2 - alpha * ry * cosTheta2

    return doubleArrayOf(c1x, c1y, c2x, c2y, x2, y2)
}

/**
 * Expands an SVG arc into cubic Bézier segments. Returns a list of segments as
 * [c1x, c1y, c2x, c2y, endX, endY]; if a radius is zero it returns a single
 * [endX, endY] pair signalling a straight line to the endpoint (SVG F.6.2).
 */
fun svgArcToBezier(
    x0: Double, y0: Double,
    rx: Double, ry: Double,
    xAxisRotation: Double,
    largeArcFlag: Int, sweepFlag: Int,
    x1: Double, y1: Double
): List<DoubleArray> {

    if (rx == 0.0 || ry == 0.0) return listOf(doubleArrayOf(x1, y1))

    val angleRad = toRadians(xAxisRotation)
    val cosAngle = cos(angleRad)
    val sinAngle = sin(angleRad)

    val dx = (x0 - x1) / 2f
    val dy = (y0 - y1) / 2f

    val x1Prime = cosAngle * dx + sinAngle * dy
    val y1Prime = -sinAngle * dx + cosAngle * dy

    var rxAbs = abs(rx)
    var ryAbs = abs(ry)

    val radiusCorrection =
        ((x1Prime * x1Prime) / (rxAbs * rxAbs)) + ((y1Prime * y1Prime) / (ryAbs * ryAbs))
    if (radiusCorrection > 1.05) {
        val scaleFactor = sqrt(radiusCorrection)
        rxAbs *= scaleFactor
        ryAbs *= scaleFactor
    }

    val sign = if (largeArcFlag == sweepFlag) -1 else 1
    val centerFactor = sign * sqrt(
        max(
            0.0, ((rxAbs * rxAbs * ryAbs * ryAbs) -
                    (rxAbs * rxAbs * y1Prime * y1Prime) -
                    (ryAbs * ryAbs * x1Prime * x1Prime)) /
                    ((rxAbs * rxAbs * y1Prime * y1Prime) + (ryAbs * ryAbs * x1Prime * x1Prime))
        )
    )

    val cxPrime = centerFactor * ((rxAbs * y1Prime) / ryAbs)
    val cyPrime = centerFactor * (-(ryAbs * x1Prime) / rxAbs)

    val cx = cosAngle * cxPrime - sinAngle * cyPrime + (x0 + x1) / 2f
    val cy = sinAngle * cxPrime + cosAngle * cyPrime + (y0 + y1) / 2f

    val theta1 = atan2((y0 - cy) / ry, (x0 - cx) / rx)
    var deltaTheta = atan2((-y1Prime - cyPrime) / ryAbs, (-x1Prime - cxPrime) / rxAbs) - theta1

    if (sweepFlag == 0 && deltaTheta > 0) {
        deltaTheta -= (2.0 * PI)
    } else if (sweepFlag == 1 && deltaTheta < 0.01) {
        deltaTheta += (2.0 * PI)
    }

    val segments = ceil(abs(deltaTheta) / (PI / 2)).toInt()
    val segmentAngle = deltaTheta / segments

    val bezierCurves = mutableListOf<DoubleArray>()
    var currentTheta = theta1

    for (i in 0 until segments) {
        val nextTheta = currentTheta + segmentAngle
        bezierCurves.add(
            arcSegmentToBezier(cx, cy, rxAbs, ryAbs, currentTheta, nextTheta, angleRad)
        )
        currentTheta = nextTheta
    }

    return bezierCurves
}

/**
 * Builds a standalone [Path] for a single arc, beginning at [start] and ending
 * at [end]. The path is seeded with a moveTo([start]) so it renders correctly on
 * its own and joins seamlessly when appended to a path already at [start].
 */
fun approximateArcPath(
    start: Offset,
    rx: Double,
    ry: Double,
    xAxisRotation: Double,
    largeArc: Boolean,
    sweep: Boolean,
    end: Offset
): Path {
    val path = Path()
    path.moveTo(start.x, start.y)

    val bezierCurves = svgArcToBezier(
        x0 = start.x.toDouble(),
        y0 = start.y.toDouble(),
        rx = rx,
        ry = ry,
        xAxisRotation = xAxisRotation,
        largeArcFlag = if (largeArc) 1 else 0,
        sweepFlag = if (sweep) 1 else 0,
        x1 = end.x.toDouble(),
        y1 = end.y.toDouble()
    )

    bezierCurves.forEach { b ->
        if (b.size >= 6) {
            path.cubicTo(
                b[0].toFloat(), b[1].toFloat(),
                b[2].toFloat(), b[3].toFloat(),
                b[4].toFloat(), b[5].toFloat()
            )
        } else {
            // Degenerate arc (zero radius) -> straight line to the endpoint.
            path.lineTo(b[0].toFloat(), b[1].toFloat())
        }
    }
    return path
}

/**
 * Computes the arc centre per the SVG spec (Chrome-matching radii correction).
 */
fun computeArcCenterChrome(
    x0: Double, y0: Double, x1: Double, y1: Double,
    rx: Double, ry: Double, xAxisRotation: Double,
    largeArcFlag: Boolean, sweepFlag: Boolean
): Pair<Double, Double> {
    val xAxisRotationRad = toRadians(xAxisRotation)
    val cosPhi = cos(xAxisRotationRad)
    val sinPhi = sin(xAxisRotationRad)

    val dx = (x0 - x1) / 2.0
    val dy = (y0 - y1) / 2.0

    val x1Prime = cosPhi * dx + sinPhi * dy
    val y1Prime = -sinPhi * dx + cosPhi * dy

    var rxAbs = abs(rx)
    var ryAbs = abs(ry)

    val radiusCheck = (x1Prime * x1Prime) / (rxAbs * rxAbs) + (y1Prime * y1Prime) / (ryAbs * ryAbs)
    if (radiusCheck > 1.0) {
        val scaleFactor = sqrt(radiusCheck)
        rxAbs *= scaleFactor
        ryAbs *= scaleFactor
    }

    val rxSq = rxAbs * rxAbs
    val rySq = ryAbs * ryAbs
    val x1PrimeSq = x1Prime * x1Prime
    val y1PrimeSq = y1Prime * y1Prime

    val numerator = max(0.0, (rxSq * rySq) - (rxSq * y1PrimeSq) - (rySq * x1PrimeSq))
    val denom = (rxSq * y1PrimeSq) + (rySq * x1PrimeSq)

    val sign = if (largeArcFlag == sweepFlag) -1 else 1
    val factor = sign * sqrt(numerator / denom)

    val cxPrime = factor * (rxAbs * y1Prime / ryAbs)
    val cyPrime = factor * (-ryAbs * x1Prime / rxAbs)

    val cx = cosPhi * cxPrime - sinPhi * cyPrime + (x0 + x1) / 2.0
    val cy = sinPhi * cxPrime + cosPhi * cyPrime + (y0 + y1) / 2.0

    return Pair(cx, cy)
}

/**
 * Parser-side arc expansion using [computeArcCenterChrome]. Returns cubic Bézier
 * segments as [c1x, c1y, c2x, c2y, endX, endY].
 */
fun svgArcToBezierChrome(
    x0: Double, y0: Double,
    rx: Double, ry: Double,
    xAxisRotation: Double,
    largeArcFlag: Boolean, sweepFlag: Boolean,
    x1: Double, y1: Double
): List<DoubleArray> {

    val (cx, cy) = computeArcCenterChrome(x0, y0, x1, y1, rx, ry, xAxisRotation, largeArcFlag, sweepFlag)

    val startAngle = atan2((y0 - cy) / ry, (x0 - cx) / rx)
    val endAngle = atan2((y1 - cy) / ry, (x1 - cx) / rx)

    var deltaAngle = endAngle - startAngle
    if (!sweepFlag && deltaAngle > 0) deltaAngle -= 2 * PI
    if (sweepFlag && deltaAngle < 0) deltaAngle += 2 * PI

    val numSegments = ceil(abs(deltaAngle) / (PI / 2)).toInt()
    val segmentAngle = deltaAngle / numSegments

    val bezierCurves = mutableListOf<DoubleArray>()
    var theta = startAngle

    for (i in 0 until numSegments) {
        val nextTheta = theta + segmentAngle
        bezierCurves.add(
            arcSegmentToBezier(cx, cy, rx, ry, theta, nextTheta, xAxisRotation)
        )
        theta = nextTheta
    }

    return bezierCurves
}
