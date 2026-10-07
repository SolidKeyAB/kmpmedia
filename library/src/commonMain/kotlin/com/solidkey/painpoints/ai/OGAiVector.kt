package com.solidkey.painpoints.ai

import com.solidkey.painpoints.image.svg.OGSvgNodeOverride
import com.solidkey.painpoints.shape.OGPolygonShape
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** What [OGAiVector.imageToVectorPrompt] asks a vision model to return. */
enum class OGVectorTarget {
    /** One closed polygon tracing the main subject's silhouette → [OGAiVector.decodePolygon]. */
    POLYGON,

    /** Several labelled regions as polygons → [OGAiVector.decodeScene]. */
    SCENE,
}

/**
 * The single entry point for KMPMedia's **AI interop** layer — "describe → shape / patch".
 *
 * KMPMedia's differentiator is *editable, data-bound vector* (a live SVG scene graph and a
 * free-form polygon lasso). Both are already just data, which makes them a natural target for a
 * language model. This object closes the loop without pulling an AI SDK or any networking into
 * the library:
 *
 *  1. **Ask** — hand your model the exact JSON contract with [polygonPrompt] (a clip region) or
 *     [svgPatchPrompt] (an SVG runtime patch). You supply the model and the call; the library
 *     stays zero-dependency and provider-agnostic.
 *  2. **Apply** — feed the model's reply to [decodePolygon] → drop the result into any
 *     `clipShape`, or [decodeSvgPatch] → pass the map to `OGSVGView(overrides = ...)`. Decoding is
 *     tolerant of the markdown fences and stray prose models often add (see [extractJson]).
 *
 * You can also go the other way — [encodePolygon] / [encodeSvgPatch] / [encodeScene] serialize live
 * primitives back to JSON (to persist a lasso, seed a prompt with the current state, or show the
 * payload).
 *
 * **From an image, not just text.** [imageToVectorPrompt] is the vision twin of [polygonPrompt]: you
 * attach a photo (or a frame of a running scene) with [ImageBitmap.toBase64Png] and the model
 * returns the same normalized vector JSON, decoded by [decodePolygon] (one silhouette) or
 * [decodeScene] (several labelled regions → live clip shapes). Still provider-agnostic, still no
 * network in the library.
 *
 * Everything here runs at generate/patch time, not per frame, so it never touches the 60fps hot
 * path that clipping and morphing live on.
 */
object OGAiVector {

    /**
     * The JSON codec used for every decode/encode. Lenient and unknown-key-tolerant because
     * model output is rarely pristine; defaults are omitted on encode to keep payloads compact.
     * Exposed so callers can reuse the exact same configuration (e.g. to decode a wrapper type).
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    // --- Decode: model output → live primitives -----------------------------------------------

    /**
     * Parse a model reply describing a polygon lasso into a live [OGPolygonShape] ready for
     * `clipShape`. Tolerates surrounding prose / code fences. Throws if no valid JSON is present;
     * use [decodePolygonOrNull] to get `null` instead.
     */
    fun decodePolygon(text: String, smoothing: Float = 0f): OGPolygonShape =
        json.decodeFromString(OGPolygonSpec.serializer(), extractJson(text)).toShape(smoothing)

    /** [decodePolygon] but returns `null` instead of throwing on malformed input. */
    fun decodePolygonOrNull(text: String, smoothing: Float = 0f): OGPolygonShape? =
        runCatching { decodePolygon(text, smoothing) }.getOrNull()

    /**
     * Parse a model reply describing an SVG patch into the `Map<id, OGSvgNodeOverride>` that
     * `OGSVGView(overrides = ...)` consumes. Tolerates surrounding prose / code fences. Throws if
     * no valid JSON is present; use [decodeSvgPatchOrNull] to get `null` instead.
     */
    fun decodeSvgPatch(text: String): Map<String, OGSvgNodeOverride> =
        json.decodeFromString(OGSvgPatchSpec.serializer(), extractJson(text)).toOverrides()

    /** [decodeSvgPatch] but returns `null` instead of throwing on malformed input. */
    fun decodeSvgPatchOrNull(text: String): Map<String, OGSvgNodeOverride>? =
        runCatching { decodeSvgPatch(text) }.getOrNull()

    /**
     * Parse a vision model's reply describing a multi-region scene (from [imageToVectorPrompt] with
     * [OGVectorTarget.SCENE]) into an [OGSceneSpec]. Tolerates surrounding prose / code fences.
     * Throws if no valid JSON is present; use [decodeSceneOrNull] to get `null` instead.
     */
    fun decodeScene(text: String): OGSceneSpec =
        json.decodeFromString(OGSceneSpec.serializer(), extractJson(text))

    /** [decodeScene] but returns `null` instead of throwing on malformed input. */
    fun decodeSceneOrNull(text: String): OGSceneSpec? =
        runCatching { decodeScene(text) }.getOrNull()

    /**
     * [decodeScene] → **sanitized** live clip shapes (one [OGPolygonShape] per region): degenerate
     * regions (fewer than 3 finite points) are dropped and vertices clamped to `0..1`, so a shaky
     * model reply yields only usable cut-outs. [smoothing] `0..1` rounds each outline (`0` = straight).
     * Use `decodeScene(text).toShapes()` instead if you want every region verbatim.
     */
    fun decodeSceneShapes(text: String, smoothing: Float = 0f): List<OGPolygonShape> =
        decodeScene(text).sanitized().toShapes(smoothing)

    // --- Encode: live primitives → JSON --------------------------------------------------------

    /** Serialize a live [OGPolygonShape] to the polygon JSON schema. */
    fun encodePolygon(shape: OGPolygonShape): String =
        json.encodeToString(OGPolygonSpec.serializer(), OGPolygonSpec.from(shape))

    /** Serialize a live override map to the SVG-patch JSON schema. */
    fun encodeSvgPatch(overrides: Map<String, OGSvgNodeOverride>): String =
        json.encodeToString(OGSvgPatchSpec.serializer(), OGSvgPatchSpec.from(overrides))

    /** Serialize a scene to the multi-region JSON schema (to persist a cut-out set or seed a prompt). */
    fun encodeScene(scene: OGSceneSpec): String =
        json.encodeToString(OGSceneSpec.serializer(), scene)

    // --- Prompt / schema helpers: hand a model the contract ------------------------------------

    /**
     * Build a ready-to-send instruction that constrains a language model to output exactly the
     * polygon-lasso JSON [decodePolygon] expects. Wire the returned string into your model as a
     * system/user prompt; pass the reply straight to [decodePolygon].
     *
     * @param instruction the natural-language ask, e.g. "the outline of a five-pointed star".
     */
    fun polygonPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a closed polygon clip region (a "lasso") for the
        KMPMedia library.

        Coordinate space is NORMALIZED: (0,0) is the top-left of the target box and (1,1) is the
        bottom-right. Keep every value within 0..1 (out-of-range is clamped). List the vertices in
        order around the outline; it closes automatically (the last point links back to the first).
        Use at least 3 points.

        Schema:
        {"points":[{"x":<0..1>,"y":<0..1>}, ...]}

        Example (a diamond):
        {"points":[{"x":0.5,"y":0.0},{"x":1.0,"y":0.5},{"x":0.5,"y":1.0},{"x":0.0,"y":0.5}]}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /**
     * Build a ready-to-send instruction that constrains a language model to output exactly the
     * SVG-patch JSON [decodeSvgPatch] expects. Optionally pass the [nodeIds] present in your SVG
     * so the model can only address real nodes.
     *
     * @param instruction the natural-language ask, e.g. "point the gauge needle to 80% and make it red".
     * @param nodeIds addressable `id`s in the target SVG; empty to leave the model unconstrained.
     */
    fun svgPatchPrompt(instruction: String, nodeIds: List<String> = emptyList()): String {
        val idsLine = if (nodeIds.isEmpty()) "" else
            "\n        Addressable node ids in the target SVG: ${nodeIds.joinToString(", ")}." +
                "\n        Only use ids from this list."
        return """
        You output ONLY a JSON object that patches nodes of an SVG at runtime for the KMPMedia
        library. Each key is an SVG node id and each value overrides that node. Every field is
        optional; omit a field to leave that aspect of the node unchanged.

        Fields per node:
        - fill, stroke: a color string ("#RGB", "#RRGGBB", "#AARRGGBB", "rgb(r,g,b)", or a name like "red")
        - strokeWidth: number, in SVG user units
        - translateX, translateY: number, in SVG user units
        - rotation: degrees; rotationCx, rotationCy: rotation/scale pivot in user units (default = viewBox centre)
        - scaleX, scaleY: number (1 = unchanged)
        - pathData: a replacement path "d" string for a <path> node
        - pathDataTo + morphProgress (0..1): morph the path toward pathDataTo; the two "d" strings
          must share the same command structure (same count and types of commands)$idsLine

        Schema:
        {"overrides":{"<nodeId>":{"fill":"#RRGGBB","rotation":45}, ...}}

        Example:
        {"overrides":{"needle":{"rotation":120,"fill":"#E53935"},"bg":{"fill":"#111111"}}}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
        """.trimIndent()
    }

    /**
     * Build a ready-to-send instruction for a **vision** model that turns an *image* into editable
     * KMPMedia vectors. You attach the image yourself (see [ImageBitmap.toBase64Png]); this returns
     * the text half of the call. Pass the model's reply to [decodePolygon] (for [OGVectorTarget.POLYGON])
     * or [decodeScene] (for [OGVectorTarget.SCENE]).
     *
     * This is the image twin of [polygonPrompt] — same normalized `0..1` contract, same library,
     * still provider-agnostic and network-free (the library makes no model call).
     *
     * Honest limit: a general vision model returns *approximate / semantic* vectors (a silhouette,
     * rough regions), not pixel-accurate tracing. For a precise cut-out pair it with a segmentation
     * model and feed those points through [OGPolygonSpec] / [OGSceneSpec] instead.
     *
     * @param hint optional guidance, e.g. "trace the character's silhouette" or "icon-ify this".
     * @param target [OGVectorTarget.POLYGON] for one silhouette, [OGVectorTarget.SCENE] for regions.
     * @param maxShapes cap on regions for [OGVectorTarget.SCENE] (ignored for POLYGON).
     * @param imageInfo optional dimensions so the prompt can state the aspect ratio.
     */
    fun imageToVectorPrompt(
        hint: String? = null,
        target: OGVectorTarget = OGVectorTarget.POLYGON,
        maxShapes: Int = 1,
        imageInfo: OGImageInfo? = null,
    ): String {
        val aspectLine = imageInfo?.let {
            "\n        The image is ${it.width}x${it.height}px (aspect ${aspectText(it.aspect)}); keep its proportions."
        } ?: ""
        val hintLine = hint?.takeIf { it.isNotBlank() }?.let { "\n        Guidance: $it." } ?: ""
        return when (target) {
            OGVectorTarget.POLYGON -> """
                Look at the image provided and output ONLY a JSON object describing ONE closed polygon
                that traces the main subject's silhouette, for the KMPMedia library.

                Coordinate space is NORMALIZED to the image: (0,0) is the top-left and (1,1) the
                bottom-right. Keep every value within 0..1. List the vertices in order around the
                outline; it closes automatically (the last point links back to the first). Use enough
                points to capture the shape, typically 8 to 40.

                Schema:
                {"points":[{"x":<0..1>,"y":<0..1>}, ...]}$aspectLine$hintLine

                Output the JSON only, with no prose and no markdown fences.
            """.trimIndent()

            OGVectorTarget.SCENE -> """
                Look at the image provided and output ONLY a JSON object describing up to $maxShapes
                region(s) of it as closed polygons, for the KMPMedia library.

                Coordinate space is NORMALIZED to the image: (0,0) is the top-left and (1,1) the
                bottom-right. Keep every value within 0..1. For each region give the ordered outline
                vertices (it closes automatically), an optional short "label", and an optional
                representative "fill" color as an SVG string ("#RRGGBB" or a name like "red").

                Schema:
                {"shapes":[{"label":"<name>","fill":"#RRGGBB","points":[{"x":<0..1>,"y":<0..1>}, ...]}, ...]}$aspectLine$hintLine

                Output the JSON only, with no prose and no markdown fences.
            """.trimIndent()
        }
    }

    /** Format an aspect ratio to 2 decimals without `String.format` (not in common Kotlin). */
    private fun aspectText(a: Float): String {
        val r = (a * 100f).roundToInt()
        return "${r / 100}.${(r % 100).toString().padStart(2, '0')}"
    }

    // --- Tolerant extraction -------------------------------------------------------------------

    /**
     * Pull the JSON payload out of a raw model reply. Strips a leading ```` ```json ```` (or plain
     * ```` ``` ````) code fence and any surrounding prose by taking the span from the first
     * `{`/`[` to the last matching `}`/`]`. Returns the input trimmed if nothing bracket-like is
     * found (the decoder then reports a precise error).
     */
    fun extractJson(text: String): String {
        var s = text.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```")
            val firstBreak = s.indexOf('\n')
            if (firstBreak != -1) {
                val tag = s.substring(0, firstBreak).trim()
                // Drop an optional language tag line (e.g. "json") that follows the opening fence.
                if (tag.isEmpty() || tag.all { it.isLetter() }) s = s.substring(firstBreak + 1)
            }
            val closingFence = s.indexOf("```")
            if (closingFence != -1) s = s.substring(0, closingFence)
            s = s.trim()
        }
        val start = s.indexOfFirst { it == '{' || it == '[' }
        val end = s.indexOfLast { it == '}' || it == ']' }
        return if (start != -1 && end >= start) s.substring(start, end + 1) else s
    }
}
