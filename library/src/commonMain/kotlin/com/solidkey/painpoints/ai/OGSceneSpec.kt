package com.solidkey.painpoints.ai

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.solidkey.painpoints.fx.OGFxColor
import com.solidkey.painpoints.look.OGLookSpec
import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.shape.OGPolygonShape
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max

// ============================================================================================
// v2 "painted vector" additions: a shape's flat [fill] can be upgraded to a [gradient], plus
// per-shape opacity / smoothing / feather / blend, and scene-level background / look / trace.
// Everything is optional with a default, and decoded with OGAiVector.json (ignoreUnknownKeys +
// encodeDefaults = false), so a v1 scene decodes unchanged on a v2 library and a v2 scene degrades
// to the solid [fill] on a v1 library. The pixels are still just polygons + fills.
// ============================================================================================

/** One colour stop in an [OGFillSpec] gradient. On the wire it accepts a lean `[offset, "colour"]`
 *  pair (what a model emits) or the explicit `{"at":.., "color":".."}` object; it writes the pair. */
@Serializable(with = OGStopSpecSerializer::class)
data class OGStopSpec(
    /** Position `0..1` along the gradient. */
    val at: Float,
    /** Colour as an SVG string: `#RRGGBB`, `#AARRGGBB`, `rgb(...)`, or a name (alpha rides in the colour). */
    val color: String,
)

/** Reads a stop as a lean `[offset,"colour"]` pair OR an `{at,color}` object; writes the pair. */
object OGStopSpecSerializer : KSerializer<OGStopSpec> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("OGStopSpec") {
        element<Float>("at"); element<String>("color")
    }
    override fun deserialize(decoder: Decoder): OGStopSpec {
        val jd = decoder as? JsonDecoder ?: throw SerializationException("OGStopSpec requires a JSON decoder")
        return when (val el = jd.decodeJsonElement()) {
            is JsonArray -> OGStopSpec(
                el.getOrNull(0)?.jsonPrimitive?.floatOrNull ?: 0f,
                el.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: "#000000",
            )
            is JsonObject -> OGStopSpec(
                (el["at"] ?: el["offset"])?.jsonPrimitive?.floatOrNull ?: 0f,
                (el["color"] ?: el["c"])?.jsonPrimitive?.contentOrNull ?: "#000000",
            )
            else -> OGStopSpec(0f, "#000000")
        }
    }
    override fun serialize(encoder: Encoder, value: OGStopSpec) {
        val je = encoder as? JsonEncoder ?: throw SerializationException("OGStopSpec requires a JSON encoder")
        je.encodeJsonElement(buildJsonArray { add(JsonPrimitive(value.at)); add(JsonPrimitive(value.color)) })
    }
}

/**
 * A richer fill for a region: a linear or radial gradient. Coordinates are normalized `0..1` within
 * the shape's own bounding box (`(0,0)` top-left, `(1,1)` bottom-right; `radius` is a fraction of the
 * box's larger side). When present it wins over the flat [OGPlacedShapeSpec.fill], which remains the
 * fallback swatch for older libraries. [oklab] resamples adjacent stops through OKLab so sunset-style
 * ramps stop banding ([OGFxColor]) — done once, never per frame.
 */
@Serializable
data class OGFillSpec(
    /** `"linear"` (default) or `"radial"`, case-insensitive. */
    val type: String = "linear",
    /** Linear gradient start (bbox `0..1`); default `(0.5, 0)` = top. */
    val from: OGPointSpec? = null,
    /** Linear gradient end (bbox `0..1`); default `(0.5, 1)` = bottom. */
    val to: OGPointSpec? = null,
    /** Radial gradient centre (bbox `0..1`); default `(0.5, 0.5)`. */
    val center: OGPointSpec? = null,
    /** Radial radius as a fraction of the box's larger side; default `0.5`. */
    val radius: Float? = null,
    val stops: List<OGStopSpec> = emptyList(),
    /** Resample adjacent stops through OKLab at build time to kill banding. Default on. */
    val oklab: Boolean = true,
) {
    /** True when there are at least two stops to interpolate. */
    val isRenderable: Boolean get() = stops.size >= 2

    /** Clamp/sort stops, drop unparseable colours; returns `null` if fewer than two usable stops remain. */
    fun sanitized(): OGFillSpec? {
        val valid = stops.filter { it.color.toSpecColor() != null }
            .map { it.copy(at = clamp01(it.at)) }
            .sortedBy { it.at }
        if (valid.size < 2) return null
        return copy(stops = valid, radius = radius?.coerceAtLeast(1e-3f))
    }

    /**
     * Build the live Compose [Brush] for a shape occupying the pixel rect (`left`, `top`, `width`,
     * `height`). Call once (e.g. in `drawWithCache`), never per frame.
     */
    fun toBrush(left: Float, top: Float, width: Float, height: Float): Brush {
        val sorted = stops
            .map { clamp01(it.at) to (it.color.toSpecColor() ?: Color.Transparent) }
            .sortedBy { it.first }
        val cs: Array<Pair<Float, Color>> =
            (if (oklab && sorted.size >= 2) oklabExpand(sorted) else sorted).toTypedArray()
        if (cs.isEmpty()) return Brush.linearGradient(0f to Color.Transparent, 1f to Color.Transparent)
        return if (type.trim().lowercase() == "radial") {
            val c = center ?: OGPointSpec(0.5f, 0.5f)
            Brush.radialGradient(
                *cs,
                center = Offset(left + c.x * width, top + c.y * height),
                radius = (radius ?: 0.5f).coerceAtLeast(1e-3f) * max(width, height),
            )
        } else {
            val f = from ?: OGPointSpec(0.5f, 0f)
            val t = to ?: OGPointSpec(0.5f, 1f)
            Brush.linearGradient(
                *cs,
                start = Offset(left + f.x * width, top + f.y * height),
                end = Offset(left + t.x * width, top + t.y * height),
            )
        }
    }

    private fun oklabExpand(stops: List<Pair<Float, Color>>): List<Pair<Float, Color>> {
        val out = ArrayList<Pair<Float, Color>>()
        for (i in 0 until stops.size - 1) {
            val (o0, c0) = stops[i]
            val (o1, c1) = stops[i + 1]
            val ramp = OGFxColor.oklabStops(c0, c1, 6)
            val startIdx = if (i == 0) 0 else 1 // avoid duplicating the shared endpoint
            for (j in startIdx until ramp.size) {
                val t = j / (ramp.size - 1f)
                out.add((o0 + (o1 - o0) * t) to ramp[j])
            }
        }
        return out
    }
}

/** How a scene was traced from a source image (provenance, see [OGTraceSpec.method]). */
enum class OGTraceMethod {
    SEMANTIC, LOWPOLY, VORONOI, POSTERIZED, STIPPLE;

    companion object {
        /** Tolerant, case-insensitive resolver (unknown → `null`), mirroring the style-op convention. */
        fun from(name: String?): OGTraceMethod? = when (name?.trim()?.lowercase()) {
            "semantic", "regions" -> SEMANTIC
            "lowpoly", "low-poly", "mesh", "triangles", "edgemesh", "edge-aware" -> LOWPOLY
            "voronoi", "stained-glass", "cells" -> VORONOI
            "posterized", "posterize", "flat" -> POSTERIZED
            "stipple", "dots", "halftone" -> STIPPLE
            else -> null
        }
    }
}

/**
 * **How a scene was traced from a source image** — pure provenance, never used to render. The shapes
 * are always the single source of truth for pixels; this just records the generation method + knobs
 * so a tool can regenerate it or an editor can label it. All fields optional, so a missing or shaky
 * trace block can never fail a scene decode.
 */
@Serializable
data class OGTraceSpec(
    /** `semantic` | `lowpoly` | `voronoi` | `posterized` | `stipple` (aliases accepted). */
    val method: String? = null,
    /** The shape budget the generator was asked for (the realism↔editability dial's coarse knob). */
    val targetShapes: Int? = null,
    /** Dial position `0..1`: 0 = stylised/sparse/editable, 1 = dense/near-photo. */
    val realism: Float? = null,
    /** Generator seed, so an external tool can regenerate this exact scene. */
    val seed: Int? = null,
    /** Region-of-interest polygon (normalized `0..1`) where detail was concentrated. */
    val roi: List<OGPointSpec>? = null,
    /** Free-text generator id, e.g. `"kmp-photovec/0.2 delaunay-edge"` or a vision-model name. */
    val source: String? = null,
    /** Source image pixel size (normalized points lose the aspect; this preserves it). */
    val sourceWidth: Int? = null,
    val sourceHeight: Int? = null,
) {
    /** The canonical method, or `null` for unknown/absent. */
    val methodOrNull: OGTraceMethod? get() = OGTraceMethod.from(method)
}

/**
 * One placed region in an [OGSceneSpec]: a closed polygon in normalized `0..1` image space, plus an
 * optional short [label], a representative [fill] colour, and (v2) an optional richer [gradient],
 * [opacity], per-shape [smoothing], soft [feather] edge, and [blend] mode. [toShape] drops straight
 * into any `clipShape`; the paint metadata is advisory for an app's own draw pass.
 */
@Serializable
data class OGPlacedShapeSpec(
    val points: List<OGPointSpec>,
    val label: String? = null,
    /** A representative solid colour (`#RRGGBB`, `rgb(...)`, or a name); the fallback when [gradient] is absent. */
    val fill: String? = null,
    /** A richer gradient fill; when present it wins over [fill]. */
    val gradient: OGFillSpec? = null,
    /** Whole-shape alpha `0..1` (multiplies any per-stop alpha). */
    val opacity: Float? = null,
    /** Per-shape outline roundness `0..1`; `null` falls back to the caller's decode default. */
    val smoothing: Float? = null,
    /** Soft-edge width as a fraction of `min(width,height)`, `0..0.25` (rendered via `ogSoftClip`). */
    val feather: Float? = null,
    /** Blend mode name: `normal` | `multiply` | `screen` | `overlay` | `lighten` | `darken` | `plus` (alias `add`). */
    val blend: String? = null,
    /** (v3) A procedural noise texture (fur / grass / grain) drawn over the fill; see [OGTextureSpec]. */
    val texture: OGTextureSpec? = null,
    /** (v3) An additive glow halo behind the region (sun bloom, backlit rim); see [OGGlowSpec]. */
    val glow: OGGlowSpec? = null,
    /** (v3) Stamp this region as a whole seeded field (trees / tufts); resolve with [OGSceneSpec.expanded]. */
    val scatter: OGScatterSpec? = null,
    /** (v4) Make this region MOVE over time — translate/scale/rotate/fade; apply with [animatedAt]. */
    val motion: OGRegionMotion? = null,
) {
    /** The lasso as a plain [OGPolygonSpec] (drops the metadata). */
    fun toPolygonSpec(): OGPolygonSpec = OGPolygonSpec(points)

    /** Build the live clip [OGPolygonShape], honouring this shape's own [smoothing] when set, else the
     *  [default]. Fewer than 3 points is degenerate (renders empty). */
    fun toShape(default: Float = 0f): OGPolygonShape =
        OGPolygonShape(points.map { OGPoint(it.x, it.y) }, this.smoothing ?: default)

    /** True when this region can render: at least 3 finite vertices. */
    val isRenderable: Boolean
        get() = points.count { it.x.isFinite() && it.y.isFinite() } >= 3

    /** A copy with vertices pulled to a finite `0..1`, paint knobs clamped, and the gradient sanitized
     *  (dropped if it has fewer than two usable stops). Label/fill/blend unchanged. */
    fun sanitized(): OGPlacedShapeSpec = copy(
        points = points.map { OGPointSpec(clamp01(it.x), clamp01(it.y)) },
        gradient = gradient?.sanitized(),
        opacity = opacity?.coerceIn(0f, 1f),
        smoothing = smoothing?.coerceIn(0f, 1f),
        feather = feather?.coerceIn(0f, 0.25f),
        texture = texture?.sanitized(),
        glow = glow?.sanitized(),
        scatter = scatter?.sanitized(),
        motion = motion?.sanitized(),
    )

    companion object {
        /** Capture a live [OGPolygonShape] (and optional metadata) back into its serializable form. */
        fun from(shape: OGPolygonShape, label: String? = null, fill: String? = null): OGPlacedShapeSpec =
            OGPlacedShapeSpec(shape.points.map { OGPointSpec(it.x, it.y) }, label, fill)
    }
}

/**
 * A multi-region vector scene in normalized `0..1` image space — the "image → vector scene" payload.
 * A vision model (or a tracer tool) returns this; [toShapes] turns the regions into live
 * [OGPolygonShape]s. v2 adds optional scene-level [background], a [look] colour grade, and a [trace]
 * provenance record (how it was generated). Decoded once at generate time, never per frame.
 */
@Serializable
data class OGSceneSpec(
    val shapes: List<OGPlacedShapeSpec> = emptyList(),
    /** Advisory format version (telemetry/debug only; decoding never branches on it). */
    val version: Int = 1,
    /** A solid base coat painted before shape 0 (SVG colour string); prevents white gaps. */
    val background: String? = null,
    /** One colour grade for the whole scene (apply via `Modifier.ogLook`). */
    val look: OGLookSpec? = null,
    /** How this scene was traced from a source image — provenance, never rendered. */
    val trace: OGTraceSpec? = null,
    /** (v4) A scene-wide timeline — a colour grade / base coat that swings over time (day→night);
     *  resolve with [liveAt] or [OGSceneMotion.lookAt]. */
    val motion: OGSceneMotion? = null,
) {
    /** Every region as a live clip [OGPolygonShape], in order. [smoothing] is the per-shape default. */
    fun toShapes(smoothing: Float = 0f): List<OGPolygonShape> = shapes.map { it.toShape(smoothing) }

    /** The regions' labels (nulls dropped), in order. */
    fun labels(): List<String> = shapes.mapNotNull { it.label }

    /** Drop regions that can't render and pull every vertex to finite `0..1`; keeps scene-level
     *  metadata ([version]/[background]/[look]/[trace]) and label↔shape alignment. */
    fun sanitized(): OGSceneSpec = copy(shapes = shapes.filter { it.isRenderable }.map { it.sanitized() }, motion = motion?.sanitized())

    companion object {
        /** Build a scene from placed shapes. */
        fun of(vararg shapes: OGPlacedShapeSpec): OGSceneSpec = OGSceneSpec(shapes.toList())
    }
}
