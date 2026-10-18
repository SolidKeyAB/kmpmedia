package com.solidkey.painpoints.ai

import androidx.compose.ui.graphics.Color
import com.solidkey.painpoints.image.OGBrush
import com.solidkey.painpoints.image.OGColor
import com.solidkey.painpoints.image.OGColorBase
import com.solidkey.painpoints.image.loading.ViewBox
import com.solidkey.painpoints.image.svg.ArcTo
import com.solidkey.painpoints.image.svg.ClosePath
import com.solidkey.painpoints.image.svg.CurveTo
import com.solidkey.painpoints.image.svg.HorizontalLineTo
import com.solidkey.painpoints.image.svg.LineTo
import com.solidkey.painpoints.image.svg.MoveTo
import com.solidkey.painpoints.image.svg.OGLinearGradientType
import com.solidkey.painpoints.image.svg.OGRadialGradientType
import com.solidkey.painpoints.image.svg.OGSVGCircle
import com.solidkey.painpoints.image.svg.OGSVGCommand
import com.solidkey.painpoints.image.svg.OGSVGEllipse
import com.solidkey.painpoints.image.svg.OGSVGGradient
import com.solidkey.painpoints.image.svg.OGSVGLine
import com.solidkey.painpoints.image.svg.OGSVGPath
import com.solidkey.painpoints.image.svg.OGSVGPolygon
import com.solidkey.painpoints.image.svg.OGSVGRect
import com.solidkey.painpoints.image.svg.OGSVGShape
import com.solidkey.painpoints.image.svg.OGSVGTreeElement
import com.solidkey.painpoints.image.svg.QuadraticCurveTo
import com.solidkey.painpoints.image.svg.SmoothCurveTo
import com.solidkey.painpoints.image.svg.SmoothQuadraticCurveTo
import com.solidkey.painpoints.image.svg.VerticalLineTo
import com.solidkey.painpoints.image.svg.parseSVGPaths
import com.solidkey.painpoints.image.svg.svgArcToBezierChrome
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * SVG front-end for the scene IR. A model is far more fluent in SVG (its training data is full of it)
 * than in our bespoke JSON, so the cheapest way to get a compact, recognizable, FEW-part drawing is
 * to let the agent emit SVG and **compile** it here into an [OGSceneSpec]. The JSON stays the runtime
 * IR: this is just a parser front-end, like a compiler accepting an extra source language. The engine
 * (per-region motion, rig, interactivity, look/blend, compositor, GIF/MP4 export) is untouched.
 *
 * Each drawable element becomes a labeled [OGPlacedShapeSpec]: its `id` (or the nearest ancestor
 * group id) becomes the [OGPlacedShapeSpec.label] so you can animate/tap it by name, its resolved
 * `fill` becomes the swatch, and its outline is flattened to points in normalized `0..1` scene space
 * (divided through the SVG `viewBox`). A multi-subpath `d` (`"M.. Z M.. Z"`) splits into one shape
 * per subpath, matching the IR's one-ring-per-shape model (and sidestepping the "trousers" artifact).
 *
 * This reuses the library's own cross-platform [parseSVGPaths] (the same `d`-string parser the live
 * SVG view uses), so the geometry matches what the SVG renderer would draw. Curves are sampled into
 * [DEFAULT_CURVE_SAMPLES] segments each, and `A` (arc) commands go through the same arc→Bézier math
 * the renderer uses.
 *
 * Hardened pieces: SVG `linearGradient`/`radialGradient` fills map to an [OGFillSpec] (stops + type;
 * geometry is bbox-relative with a best-effort direction), and the `opacity` / `fill-opacity`
 * attributes fold into [OGPlacedShapeSpec.opacity]. Stroke-only shapes (no fill) are skipped — the IR
 * paints filled regions.
 */
object OGSvgScene {

    const val DEFAULT_CURVE_SAMPLES: Int = 12

    /** Compile an SVG document [svg] into an [OGSceneSpec] of labeled polygon regions. Returns an empty
     *  scene (never throws) when [svg] is prose-wrapped or malformed — same leniency as the JSON codec. */
    fun toScene(svg: String, curveSamples: Int = DEFAULT_CURVE_SAMPLES): OGSceneSpec {
        val result = runCatching { parseSVGPaths(svg) }.getOrNull() ?: return OGSceneSpec(version = 3)
        val raw = result.viewBox
        val vb = if (raw.width > 0f && raw.height > 0f) raw else ViewBox(0f, 0f, 1f, 1f)
        val tree = result.svgTree ?: return OGSceneSpec(version = 3)
        val samples = curveSamples.coerceIn(2, 64)
        val out = ArrayList<OGPlacedShapeSpec>()
        walk(tree, null, null, null, vb, result.gradients, samples, out)
        return OGSceneSpec(shapes = out, version = 3)
    }

    private fun walk(
        el: OGSVGTreeElement,
        groupLabel: String?,
        groupMotion: OGRegionMotion?,
        groupPivot: OGPointSpec?,
        vb: ViewBox,
        gradients: Map<String, OGSVGGradient>,
        samples: Int,
        out: MutableList<OGPlacedShapeSpec>,
    ) {
        val label = el.id?.takeIf { it.isNotBlank() } ?: groupLabel
        // Pass 2: the model can DECLARE motion with data-motion="blink|breathe|sway|wave|bob|nod|pulse"
        // (plus optional data-anchor / data-dur) on an element or a group. This is the one path that
        // lifts the "only English ids animate" ceiling — a part tagged data-motion="blink" blinks whatever
        // language it is labelled in. An element's own tag wins; otherwise it inherits the group's.
        val ownMotion = motionFor(el)
        val motion = ownMotion ?: groupMotion
        // A GROUP (<g>) that DECLARES a motion makes its whole subtree move as ONE rigid unit: compute a
        // single shared pivot (the group's combined-bbox anchor, in scene space) and hand it to every
        // descendant, so a multi-shape limb rotates about one shoulder instead of each piece spinning
        // about its own centre and shearing. A leaf with its OWN motion keeps its per-shape pivot; a child
        // with no motion of its own inherits the enclosing group's shared pivot.
        val pivot: OGPointSpec? = when {
            ownMotion != null && el.children.isNotEmpty() -> sharedGroupPivot(el, ownMotion, vb, samples)
            ownMotion != null -> null
            else -> groupPivot
        }
        val gradient = el.style.fillGradientId?.let { gradients[it] }?.toFillSpec()
        val solid = el.style.fill.resolve()
        val attrAlpha = (el.attributes.floatAttr("opacity")) * (el.attributes.floatAttr("fill-opacity"))
        val paintAlpha = (solid?.second ?: 1f) * attrAlpha
        val hasPaint = gradient != null || (solid != null && paintAlpha > 0.004f)

        if (hasPaint) {
            val fillHex = solid?.first ?: gradient?.stops?.firstOrNull()?.color ?: "#000000"
            val emitMotion = if (pivot != null) motion?.copy(pivotAbs = pivot) else motion
            val rings = ArrayList<List<OGPointSpec>>()
            for (shape in el.shapes) rings += shapeToRings(shape, samples)
            val multi = rings.count { it.size >= 3 } > 1
            var i = 0
            for (ring in rings) {
                if (ring.size < 3) continue
                i++
                out += OGPlacedShapeSpec(
                    points = ring.map { OGPointSpec((it.x - vb.minX) / vb.width, (it.y - vb.minY) / vb.height) },
                    label = labelFor(label, i, multi),
                    fill = fillHex,
                    gradient = gradient,
                    opacity = if (paintAlpha < 0.999f) paintAlpha else null,
                    motion = emitMotion,
                )
            }
        }
        for (child in el.children) walk(child, label, motion, pivot, vb, gradients, samples, out)
    }

    /** The ONE pivot a `<g data-motion=…>` group shares across its whole subtree, in normalized SCENE
     *  space: the group's combined bounding box, sampled at the motion's [OGRegionMotion.anchor] fraction
     *  (centre when none). Returns null for a group that resolves to fewer than two drawable shapes — one
     *  shape already pivots correctly about its own box, so there is nothing to share. */
    private fun sharedGroupPivot(
        el: OGSVGTreeElement,
        motion: OGRegionMotion,
        vb: ViewBox,
        samples: Int,
    ): OGPointSpec? {
        val rings = ArrayList<List<OGPointSpec>>()
        collectRings(el, samples, rings)
        val drawable = rings.filter { it.size >= 3 }
        if (drawable.size < 2) return null  // one shape already pivots correctly about its own box
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        for (ring in drawable) for (p in ring) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val a = motion.anchor ?: OGPointSpec(0.5f, 0.5f)
        val rawX = minX + a.x * (maxX - minX)
        val rawY = minY + a.y * (maxY - minY)
        return OGPointSpec(
            ((rawX - vb.minX) / vb.width).coerceIn(0f, 1f),
            ((rawY - vb.minY) / vb.height).coerceIn(0f, 1f),
        )
    }

    /** Every ring of every drawable shape in [el]'s subtree, in RAW svg coords (pre-normalization). */
    private fun collectRings(el: OGSVGTreeElement, samples: Int, acc: MutableList<List<OGPointSpec>>) {
        for (shape in el.shapes) acc += shapeToRings(shape, samples)
        for (child in el.children) collectRings(child, samples, acc)
    }

    /** The [OGRegionMotion] a model DECLARED on [el] via `data-motion` (+ optional `data-anchor` /
     *  `data-dur` overrides), or null when it carries no motion tag / an unknown keyword. The keyword
     *  vocabulary is shared with [OGSceneIdle.motionNamed] so a tagged part and a recognised English
     *  label animate identically. */
    private fun motionFor(el: OGSVGTreeElement): OGRegionMotion? {
        val name = el.attributes["data-motion"]?.takeIf { it.isNotBlank() } ?: return null
        val base = OGSceneIdle.motionNamed(name) ?: return null
        val anchor = parseAnchor(el.attributes["data-anchor"]) ?: base.anchor
        val dur = el.attributes["data-dur"]?.trim()?.toFloatOrNull()?.coerceAtLeast(0.05f)
        return base.copy(anchor = anchor, duration = dur ?: base.duration)
    }

    /** Resolve `data-anchor` — a named edge/corner (`top`/`bottom`/`base`/`left`/`right`/`center`/
     *  `top-left`…) or a raw `"x,y"` fraction — into a pivot, or null if it names nothing usable. */
    private fun parseAnchor(raw: String?): OGPointSpec? {
        val v = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return when (v) {
            "top", "top-center", "top-centre" -> OGPointSpec(0.5f, 0f)
            "bottom", "base", "bottom-center", "bottom-centre" -> OGPointSpec(0.5f, 1f)
            "left" -> OGPointSpec(0f, 0.5f)
            "right" -> OGPointSpec(1f, 0.5f)
            "center", "centre", "middle" -> OGPointSpec(0.5f, 0.5f)
            "top-left", "topleft" -> OGPointSpec(0f, 0f)
            "top-right", "topright" -> OGPointSpec(1f, 0f)
            "bottom-left", "bottomleft" -> OGPointSpec(0f, 1f)
            "bottom-right", "bottomright" -> OGPointSpec(1f, 1f)
            else -> {
                val parts = v.split(",")
                val x = parts.getOrNull(0)?.trim()?.toFloatOrNull()
                val y = parts.getOrNull(1)?.trim()?.toFloatOrNull()
                if (parts.size == 2 && x != null && y != null) {
                    OGPointSpec(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
                } else {
                    null
                }
            }
        }
    }

    private fun labelFor(base: String?, index: Int, multi: Boolean): String? = when {
        base == null -> null
        multi -> "${base}_$index"
        else -> base
    }

    // --- geometry -> rings ------------------------------------------------------------------------

    private fun shapeToRings(s: OGSVGShape, samples: Int): List<List<OGPointSpec>> = when (s) {
        is OGSVGPath -> flatten(s.commands, samples)
        is OGSVGPolygon -> listOf(s.points.map { OGPointSpec(it.x, it.y) })
        is OGSVGRect -> listOf(roundedRect(s.x, s.y, s.width, s.height, s.rx, s.ry, samples))
        is OGSVGCircle -> listOf(ellipse(s.cx, s.cy, s.r, s.r, samples * 3))
        is OGSVGEllipse -> listOf(ellipse(s.cx, s.cy, s.rx, s.ry, samples * 3))
        is OGSVGLine -> emptyList() // no enclosed area
        else -> emptyList()
    }

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, n: Int): List<OGPointSpec> {
        val count = n.coerceIn(8, 128)
        return List(count) { i ->
            val a = 2.0 * PI * i / count
            OGPointSpec(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
        }
    }

    /** Trace a rect, rounding the corners into quarter-ellipse arcs when `rx`/`ry` are set — so a
     *  model's "rounded rectangle" limb keeps its soft corners instead of flattening to a hard box.
     *  SVG rules: a missing radius mirrors the other, both clamp to half the side, 0 -> sharp corners. */
    private fun roundedRect(x: Float, y: Float, w: Float, h: Float, rxIn: Float, ryIn: Float, samples: Int): List<OGPointSpec> {
        var rx = if (rxIn > 0f) rxIn else ryIn
        var ry = if (ryIn > 0f) ryIn else rxIn
        rx = rx.coerceIn(0f, w / 2f)
        ry = ry.coerceIn(0f, h / 2f)
        if (rx <= 0f || ry <= 0f) {
            return listOf(
                OGPointSpec(x, y),
                OGPointSpec(x + w, y),
                OGPointSpec(x + w, y + h),
                OGPointSpec(x, y + h),
            )
        }
        val n = samples.coerceIn(3, 16)
        // corner centre + arc start (in half-PI units), swept clockwise: TR, BR, BL, TL.
        val corners = listOf(
            Triple(x + w - rx, y + ry, 1.5),
            Triple(x + w - rx, y + h - ry, 0.0),
            Triple(x + rx, y + h - ry, 0.5),
            Triple(x + rx, y + ry, 1.0),
        )
        val pts = ArrayList<OGPointSpec>(4 * (n + 1))
        for ((cx, cy, startHalfPi) in corners) {
            val a0 = startHalfPi * PI
            for (k in 0..n) {
                val a = a0 + (PI / 2.0) * k / n
                pts.add(OGPointSpec((cx + rx * cos(a)).toFloat(), (cy + ry * sin(a)).toFloat()))
            }
        }
        return pts
    }

    /** Flatten one path's commands into one polyline per subpath (split at each `MoveTo`), mirroring
     *  [OGSVGPath.toPath]'s command interpretation so the points trace the same outline. */
    private fun flatten(commands: List<OGSVGCommand>, samples: Int): List<List<OGPointSpec>> {
        val subs = ArrayList<MutableList<OGPointSpec>>()
        var cur: MutableList<OGPointSpec>? = null
        var lx = 0f
        var ly = 0f

        fun line(x: Float, y: Float) {
            cur?.add(OGPointSpec(x, y)); lx = x; ly = y
        }

        fun cubic(x1: Float, y1: Float, x2: Float, y2: Float, ex: Float, ey: Float) {
            val sx = lx; val sy = ly
            val c = cur ?: return
            for (k in 1..samples) {
                val t = k / samples.toFloat(); val u = 1f - t
                val px = u * u * u * sx + 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t * ex
                val py = u * u * u * sy + 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t * ey
                c.add(OGPointSpec(px, py))
            }
            lx = ex; ly = ey
        }

        fun quad(cx: Float, cy: Float, ex: Float, ey: Float) {
            val sx = lx; val sy = ly
            val c = cur ?: return
            for (k in 1..samples) {
                val t = k / samples.toFloat(); val u = 1f - t
                c.add(OGPointSpec(u * u * sx + 2f * u * t * cx + t * t * ex, u * u * sy + 2f * u * t * cy + t * t * ey))
            }
            lx = ex; ly = ey
        }

        for (cmd in commands) {
            when (cmd) {
                is MoveTo -> {
                    val fresh = mutableListOf(OGPointSpec(cmd.x, cmd.y)); subs.add(fresh); cur = fresh
                    lx = cmd.x; ly = cmd.y
                }
                is LineTo -> line(cmd.x, cmd.y)
                is HorizontalLineTo -> line(cmd.x, ly)
                is VerticalLineTo -> line(lx, cmd.y)
                is CurveTo -> cubic(cmd.x1, cmd.y1, cmd.x2, cmd.y2, cmd.x, cmd.y)
                is QuadraticCurveTo -> quad(cmd.controlX, cmd.controlY, cmd.x, cmd.y)
                is SmoothCurveTo -> cubic(2f * lx - cmd.x2, 2f * ly - cmd.y2, cmd.x2, cmd.y2, cmd.x, cmd.y)
                is SmoothQuadraticCurveTo -> quad(2f * lx - cmd.x, 2f * ly - cmd.y, cmd.x, cmd.y)
                is ArcTo -> {
                    // Same arc→Bézier the renderer uses, then sample each cubic to points.
                    val segs = runCatching {
                        svgArcToBezierChrome(
                            x0 = lx.toDouble(), y0 = ly.toDouble(),
                            x1 = cmd.x.toDouble(), y1 = cmd.y.toDouble(),
                            rx = cmd.rx.toDouble(), ry = cmd.ry.toDouble(),
                            xAxisRotation = cmd.xAxisRotation.toDouble(),
                            largeArcFlag = cmd.largeArc, sweepFlag = cmd.sweep,
                        )
                    }.getOrNull()
                    if (segs.isNullOrEmpty()) {
                        line(cmd.x, cmd.y)
                    } else {
                        for (s in segs) cubic(
                            s[0].toFloat(), s[1].toFloat(), s[2].toFloat(), s[3].toFloat(), s[4].toFloat(), s[5].toFloat(),
                        )
                    }
                }
                is ClosePath -> { /* the IR auto-closes every polygon ring */ }
            }
        }
        // `Z` adds an explicit line back to the start; drop that trailing duplicate since the IR
        // closes each ring itself (keeps centroids/motion clean).
        return subs.map { dropClosingDuplicate(it) }
    }

    /** Drop a trailing point coincident with the first (the explicit `Z` close-line). */
    private fun dropClosingDuplicate(ring: List<OGPointSpec>): List<OGPointSpec> {
        if (ring.size >= 2) {
            val a = ring.first(); val b = ring.last()
            if (abs(a.x - b.x) < 1e-5f && abs(a.y - b.y) < 1e-5f) return ring.subList(0, ring.size - 1)
        }
        return ring
    }

    // --- colour -----------------------------------------------------------------------------------

    /** The element's resolved solid fill as `(#RRGGBB, alpha)`, or `null` when there is no solid fill. */
    private fun OGColorBase?.resolve(): Pair<String, Float>? = when (this) {
        is OGColor -> color.toHex() to color.alpha
        is OGBrush -> colorStops.firstOrNull()?.second?.let { it.toHex() to it.alpha } // inline-gradient fallback
        else -> null
    }

    /** Map an SVG gradient to an [OGFillSpec]: stops survive exactly; geometry is bbox-relative with a
     *  best-effort direction (linear top→bottom, radial centred) since SVG gradient units vary. */
    private fun OGSVGGradient.toFillSpec(): OGFillSpec? {
        val stops = brush.colorStops
            .map { OGStopSpec(it.first.coerceIn(0f, 1f), it.second.toHex()) }
            .sortedBy { it.at }
        if (stops.size < 2) return null
        return when (brush.type) {
            is OGRadialGradientType -> OGFillSpec(
                type = "radial", center = OGPointSpec(0.5f, 0.5f), radius = 0.5f, stops = stops,
            )
            is OGLinearGradientType -> OGFillSpec(
                type = "linear", from = OGPointSpec(0.5f, 0f), to = OGPointSpec(0.5f, 1f), stops = stops,
            )
            else -> OGFillSpec(type = "linear", stops = stops)
        }
    }

    private fun Map<String, String>.floatAttr(name: String): Float =
        this[name]?.trim()?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f

    private fun Color.toHex(): String {
        fun h(v: Float): String {
            val n = (v * 255f).roundToInt().coerceIn(0, 255)
            val s = n.toString(16)
            return if (s.length == 1) "0$s" else s
        }
        return "#${h(red)}${h(green)}${h(blue)}"
    }
}
