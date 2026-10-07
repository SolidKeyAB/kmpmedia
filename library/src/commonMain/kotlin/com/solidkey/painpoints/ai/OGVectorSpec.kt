package com.solidkey.painpoints.ai

import androidx.compose.ui.graphics.Color
import com.solidkey.painpoints.image.OGColor
import com.solidkey.painpoints.image.svg.OGSvgNodeOverride
import com.solidkey.painpoints.image.svg.parseColor
import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.shape.OGPolygonShape
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * The **AI interop** layer: a stable, provider-agnostic JSON schema for KMPMedia's vector
 * primitives so that anything an LLM produces — "describe → clip region" or "describe → SVG
 * patch" — drops straight into the library with no glue code.
 *
 * These are plain `@Serializable` data-transfer objects, deliberately decoupled from the
 * Compose-facing types ([OGPolygonShape], [OGSvgNodeOverride]) which reference non-serializable
 * Compose objects (`Shape`, `Color`). Colors travel as strings (SVG syntax: `#RRGGBB`,
 * `rgb(...)`, or a name) and coordinates as plain floats, which is exactly what a language model
 * emits reliably. Conversion happens once, at generate/patch time — never per frame — so this
 * stays clear of the 60fps hot path.
 *
 * The library itself makes **no** network calls and bundles **no** AI SDK: it defines the
 * contract and the parse/serialize both directions. You wire it to whatever model you like via
 * [OGAiVector.polygonPrompt] / [OGAiVector.svgPatchPrompt] (hand the model the contract) and
 * [OGAiVector.decodePolygon] / [OGAiVector.decodeSvgPatch] (turn its reply back into primitives).
 */

/** A single normalized (`0..1`) vertex — the serializable twin of [OGPoint]. */
@Serializable
data class OGPointSpec(val x: Float, val y: Float)

/** Clamp to a finite `0..1` (`NaN`/`∞` → `0`) — used to sanitize model-supplied vertices before use. */
internal fun clamp01(v: Float): Float = if (v.isFinite()) v.coerceIn(0f, 1f) else 0f

/**
 * A closed polygon "lasso" clip region in normalized `0..1` space — the serializable twin of
 * [OGPolygonShape]. This is the "describe → clip region" payload: an LLM (or a segmentation
 * model) emits the ordered vertices as JSON and [toShape] turns them into a ready-to-use
 * `clipShape`.
 */
@Serializable
data class OGPolygonSpec(val points: List<OGPointSpec>) {
    /** Build the live clip [OGPolygonShape]. [smoothing] `0..1` rounds the outline (`0` = straight
     *  lines). Fewer than 3 points is degenerate (renders empty). */
    fun toShape(smoothing: Float = 0f): OGPolygonShape =
        OGPolygonShape(points.map { OGPoint(it.x, it.y) }, smoothing)

    /** True when this describes a renderable polygon: at least 3 finite vertices. */
    val isRenderable: Boolean
        get() = points.count { it.x.isFinite() && it.y.isFinite() } >= 3

    /** A copy with every vertex pulled to a finite `0..1` (drops `NaN`/`∞`, clamps strays into range). */
    fun sanitized(): OGPolygonSpec = OGPolygonSpec(points.map { OGPointSpec(clamp01(it.x), clamp01(it.y)) })

    companion object {
        /** Capture an existing [OGPolygonShape] back into its serializable form. */
        fun from(shape: OGPolygonShape): OGPolygonSpec =
            OGPolygonSpec(shape.points.map { OGPointSpec(it.x, it.y) })
    }
}

/**
 * The serializable twin of [OGSvgNodeOverride]: a single node's runtime patch. Every field is
 * optional; omitting a field leaves that aspect of the node unchanged. Colors are strings in SVG
 * syntax so they round-trip cleanly through a language model.
 */
@Serializable
data class OGNodeOverrideSpec(
    /** Fill color as an SVG color string (`#RGB`, `#RRGGBB`, `#AARRGGBB`, `rgb(...)`, or a name). */
    val fill: String? = null,
    /** Stroke color as an SVG color string. */
    val stroke: String? = null,
    val strokeWidth: Float? = null,
    val translateX: Float? = null,
    val translateY: Float? = null,
    /** Rotation in degrees. */
    val rotation: Float? = null,
    /** Rotation/scale pivot in SVG user units; omit for the viewBox centre. */
    val rotationCx: Float? = null,
    val rotationCy: Float? = null,
    val scaleX: Float? = null,
    val scaleY: Float? = null,
    /** Replacement path `d` for a `<path>` node. */
    val pathData: String? = null,
    /** Morph *target* path `d`; drive [morphProgress] `0f`→`1f` to tween toward it. */
    val pathDataTo: String? = null,
    /** Morph position `0f..1f` (only meaningful with [pathDataTo] set). */
    val morphProgress: Float = 0f,
) {
    /** Convert into the live [OGSvgNodeOverride] used by `OGSVGView(overrides = ...)`. */
    fun toOverride(): OGSvgNodeOverride = OGSvgNodeOverride(
        fill = fill?.toSpecColor(),
        stroke = stroke?.toSpecColor(),
        strokeWidth = strokeWidth,
        translateX = translateX,
        translateY = translateY,
        rotation = rotation,
        rotationCx = rotationCx,
        rotationCy = rotationCy,
        scaleX = scaleX,
        scaleY = scaleY,
        pathData = pathData,
        pathDataTo = pathDataTo,
        morphProgress = morphProgress,
    )

    companion object {
        /** Capture an existing [OGSvgNodeOverride] back into its serializable form. */
        fun from(o: OGSvgNodeOverride): OGNodeOverrideSpec = OGNodeOverrideSpec(
            fill = o.fill?.toHexString(),
            stroke = o.stroke?.toHexString(),
            strokeWidth = o.strokeWidth,
            translateX = o.translateX,
            translateY = o.translateY,
            rotation = o.rotation,
            rotationCx = o.rotationCx,
            rotationCy = o.rotationCy,
            scaleX = o.scaleX,
            scaleY = o.scaleY,
            pathData = o.pathData,
            pathDataTo = o.pathDataTo,
            morphProgress = o.morphProgress,
        )
    }
}

/**
 * A full SVG runtime patch: node id → override. This is the "describe → SVG patch" payload; pass
 * [toOverrides] straight to `OGSVGView(overrides = ...)`.
 */
@Serializable
data class OGSvgPatchSpec(val overrides: Map<String, OGNodeOverrideSpec> = emptyMap()) {
    /** Convert into the `Map<id, OGSvgNodeOverride>` that `OGSVGView` consumes. */
    fun toOverrides(): Map<String, OGSvgNodeOverride> = overrides.mapValues { it.value.toOverride() }

    companion object {
        /** Capture an existing override map back into its serializable form. */
        fun from(overrides: Map<String, OGSvgNodeOverride>): OGSvgPatchSpec =
            OGSvgPatchSpec(overrides.mapValues { OGNodeOverrideSpec.from(it.value) })
    }
}

/**
 * Parse an SVG color string into a Compose [Color], reusing the same parser the SVG renderer
 * uses (hex, `rgb()`/`rgba()`, and named colors) so the AI contract matches SVG semantics.
 * Gradients (`url(#id)`) resolve to no solid color here since a node override carries a plain
 * color; unparseable input yields transparent, mirroring the renderer.
 */
internal fun String.toSpecColor(): Color? = when (val base = parseColor(this)) {
    is OGColor -> base.color
    else -> null
}

/** Render a [Color] as `#RRGGBB` (or `#AARRGGBB` when not fully opaque) for the JSON schema. */
internal fun Color.toHexString(): String {
    fun ch(v: Float): String = (v * 255f).roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0')
    val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
    val body = "${ch(red)}${ch(green)}${ch(blue)}"
    return if (a == 255) "#$body" else "#${a.toString(16).padStart(2, '0')}$body"
}
