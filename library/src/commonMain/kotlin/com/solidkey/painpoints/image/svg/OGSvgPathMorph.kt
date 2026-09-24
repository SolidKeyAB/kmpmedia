package com.solidkey.painpoints.image.svg

import com.solidkey.painpoints.image.loading.ViewBox

/**
 * Runtime path *morphing* for `<path>` SVG nodes.
 *
 * The design is built for the "must run in a real app (a game) at 60fps" bar:
 *   - Each endpoint `d` string is parsed to a command list at most **once** (memoised by
 *     [parsePathCached]); driving [OGSvgNodeOverride.morphProgress] across frames never re-parses.
 *   - Per frame we only [interpolateCommands] — plain float lerps over two already-parsed lists —
 *     so there is no regex, no string building, and no path re-parse inside the animation loop.
 *
 * Morphing is a coordinate tween, so it requires the two paths to share the same command
 * *structure* (identical count and command types, index for index). When they don't match we
 * can't tween meaningfully, so we snap (`t < 0.5` → start, else → target) rather than crash or
 * draw a garbled shape. Authoring two structurally-matched `d` strings is the caller's job — the
 * same constraint every SVG morph tool (Lottie, Rive, D3, SMIL) imposes.
 */

/** Linear interpolate. */
private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Parses [d] into commands, reusing [cache] when present so a repeated `d` (e.g. a fixed morph
 * endpoint animated across frames) is parsed only once. The cache key folds in the style scale,
 * since [parsePathCommands] bakes [OGSVGStyle.scaleX]/[OGSVGStyle.scaleY] into the coordinates.
 * With no cache it is a plain parse — keeping unit tests exercising the real parser.
 */
internal fun parsePathCached(
    d: String,
    viewBox: ViewBox,
    style: OGSVGStyle,
    cache: MutableMap<String, List<OGSVGCommand>>?,
): List<OGSVGCommand> {
    if (cache == null) return parsePathCommands(d, viewBox, style)
    val key = "$d|${style.scaleX}|${style.scaleY}"
    return cache.getOrPut(key) { parsePathCommands(d, viewBox, style) }
}

/**
 * Returns a fresh command list interpolating [from]→[to] by [t] (clamped to `0..1`). Inputs are
 * never mutated (each result command is newly constructed). At `t <= 0` returns [from] as-is and
 * at `t >= 1` returns [to] as-is (no allocation). If the two lists differ in size or in command
 * type at any index the pair can't be tweened, so it snaps: [from] for `t < 0.5`, else [to].
 */
internal fun interpolateCommands(
    from: List<OGSVGCommand>,
    to: List<OGSVGCommand>,
    t: Float,
): List<OGSVGCommand> {
    val p = t.coerceIn(0f, 1f)
    if (p <= 0f) return from
    if (p >= 1f) return to
    if (!structurallyMorphable(from, to)) return if (p < 0.5f) from else to

    val out = ArrayList<OGSVGCommand>(from.size)
    for (i in from.indices) {
        val a = from[i]
        val b = to[i]
        out.add(
            when (a) {
                is MoveTo -> { b as MoveTo; MoveTo(lerp(a.x, b.x, p), lerp(a.y, b.y, p)) }
                is LineTo -> { b as LineTo; LineTo(lerp(a.x, b.x, p), lerp(a.y, b.y, p)) }
                is HorizontalLineTo -> { b as HorizontalLineTo; HorizontalLineTo(lerp(a.x, b.x, p)) }
                is VerticalLineTo -> { b as VerticalLineTo; VerticalLineTo(lerp(a.y, b.y, p)) }
                is CurveTo -> {
                    b as CurveTo
                    CurveTo(
                        lerp(a.x1, b.x1, p), lerp(a.y1, b.y1, p),
                        lerp(a.x2, b.x2, p), lerp(a.y2, b.y2, p),
                        lerp(a.x, b.x, p), lerp(a.y, b.y, p),
                    )
                }
                is SmoothCurveTo -> {
                    b as SmoothCurveTo
                    SmoothCurveTo(
                        lerp(a.x2, b.x2, p), lerp(a.y2, b.y2, p),
                        lerp(a.x, b.x, p), lerp(a.y, b.y, p),
                    )
                }
                is QuadraticCurveTo -> {
                    b as QuadraticCurveTo
                    QuadraticCurveTo(
                        lerp(a.controlX, b.controlX, p), lerp(a.controlY, b.controlY, p),
                        lerp(a.x, b.x, p), lerp(a.y, b.y, p),
                    )
                }
                is SmoothQuadraticCurveTo -> {
                    b as SmoothQuadraticCurveTo
                    SmoothQuadraticCurveTo(lerp(a.x, b.x, p), lerp(a.y, b.y, p))
                }
                is ArcTo -> {
                    b as ArcTo
                    // rx/ry/rotation/endpoint tween; the boolean flags can't blend, so pick by t.
                    val useB = p >= 0.5f
                    ArcTo(
                        rx = lerp(a.rx, b.rx, p),
                        ry = lerp(a.ry, b.ry, p),
                        xAxisRotation = lerp(a.xAxisRotation, b.xAxisRotation, p),
                        largeArc = if (useB) b.largeArc else a.largeArc,
                        sweep = if (useB) b.sweep else a.sweep,
                        x = lerp(a.x, b.x, p),
                        y = lerp(a.y, b.y, p),
                    )
                }
                is ClosePath -> ClosePath(a.reason)
            }
        )
    }
    return out
}

/** True when [from] and [to] can be coordinate-tweened: same length and same command type per index. */
internal fun structurallyMorphable(from: List<OGSVGCommand>, to: List<OGSVGCommand>): Boolean {
    if (from.size != to.size) return false
    for (i in from.indices) {
        if (from[i]::class != to[i]::class) return false
    }
    return true
}

/**
 * Returns [element] unchanged unless the override supplies a morph target [OGSvgNodeOverride.pathDataTo]
 * AND the element holds `<path>` geometry; otherwise a shallow copy whose [OGSVGPath] is the tween of the
 * element's current commands (the "from") toward the parsed target, by [OGSvgNodeOverride.morphProgress].
 *
 * Must run *after* [applyPathOverride] so that a [OGSvgNodeOverride.pathData] start point is already in
 * `element`'s commands. The parsed target is memoised via [parseCache]; the source tree is never mutated
 * (a fresh shapes list is built). Pure/side-effect-free, so it is unit-testable off-device.
 */
internal fun applyMorphOverride(
    element: OGSVGTreeElement,
    override: OGSvgNodeOverride?,
    viewBox: ViewBox,
    parseCache: MutableMap<String, List<OGSVGCommand>>? = null,
): OGSVGTreeElement {
    if (override == null || !override.hasMorph) return element
    if (element.shapes.none { it is OGSVGPath }) return element
    val t = override.morphProgress.coerceIn(0f, 1f)
    if (t <= 0f) return element // fully at the start geometry — nothing to change.
    val toCommands = parsePathCached(override.pathDataTo!!, viewBox, element.style, parseCache)
    val newShapes = element.shapes.mapTo(mutableListOf()) { shape ->
        if (shape is OGSVGPath) {
            val morphed = interpolateCommands(shape.commands, toCommands, t)
            OGSVGPath(morphed).also { it.animations = shape.animations }
        } else shape
    }
    return element.copy(shapes = newShapes)
}
