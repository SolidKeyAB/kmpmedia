package com.solidkey.painpoints.style

import com.solidkey.painpoints.ai.OGAiVector
import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.shape.OGPolygonShape
import kotlinx.serialization.Serializable

/**
 * The **data-defined style** layer: a drawing style is just JSON — a *pipeline of ops* — that the
 * library decodes into a live [OGStyle] and applies to any outline each frame. This is what lets a
 * designer (or an LLM) author, tweak and share a ".style" pack as plain data, exactly like
 * [OGAiVector] lets a model describe a clip region as data. No code, no rebuild: hand the lib a
 * string, get a running style.
 *
 * A spec is an ordered list of [OGStyleOp]s. Each op names a procedural primitive from this package
 * and carries its (flat, all-optional) parameters, so the schema stays model-friendly:
 *
 * ```json
 * {"name":"pixel sprite",
 *  "ops":[{"op":"boil","amplitude":0.03,"boilFps":10},{"op":"pixelate","resolution":20}]}
 * ```
 *
 * Built-in ops (all zero-dependency, all in [OGBoil.kt]):
 * - **`boil`** — [OGBoil.displace]; the living/wiggly line. Params: `amplitude`, `boilFps`,
 *   `smooth`, `seed`.
 * - **`quantize`** (aliases `step`, `stepped`) — [quantizeVertices]; snap vertices to a grid.
 *   Params: `grid` (or `resolution`).
 * - **`pixelate`** (alias `pixel`) — [pixelateFill]; a terminal *fill* op producing a pixel grid.
 *   Param: `resolution` (or `grid`). Put it last.
 *
 * Decoding/encoding + a prompt builder live on [OGStyles]. Everything here is pure + deterministic
 * and the apply step is offset/round/lerp per vertex (no re-measuring, no allocation beyond the
 * output list), so it holds 60fps — the same perf gate [OGBoil] clears. Unknown ops and keys are
 * ignored on decode, so a newer pack degrades gracefully on an older library.
 */

/**
 * One step of a [OGStyleSpec] pipeline: the [op] name plus its parameters. Parameters are flat and
 * all-optional (each op reads the ones it understands and falls back to its own default), mirroring
 * the [com.solidkey.painpoints.ai.OGNodeOverrideSpec] shape so model output round-trips cleanly.
 */
@Serializable
data class OGStyleOp(
    /** The op name: `boil`, `quantize` (`step`/`stepped`), or `pixelate` (`pixel`). Case-insensitive. */
    val op: String,
    /** `boil`: max per-vertex displacement, in the outline's units (e.g. `0.03` on a `0..1` outline). */
    val amplitude: Float? = null,
    /** `boil`: jitter re-rolls per second (~6-12 reads as a lively boil). */
    val boilFps: Float? = null,
    /** `boil`: smoothstep wobble (`true`) vs hard stop-motion stutter (`false`). */
    val smooth: Boolean? = null,
    /** `boil`: vary for a different random wiggle from the same inputs. */
    val seed: Int? = null,
    /** `pixelate`/`quantize`: grid fineness (cells per side over the `0..1` box). */
    val resolution: Int? = null,
    /** `quantize`/`pixelate`: alias for [resolution]; lattice fineness. */
    val grid: Int? = null,
)

/**
 * A full style pack: an optional [name] and the ordered [ops] pipeline. Serializable, so it is the
 * unit a designer saves/shares and an LLM emits. Call [toStyle] for the live applier.
 */
@Serializable
data class OGStyleSpec(
    val name: String? = null,
    val ops: List<OGStyleOp> = emptyList(),
) {
    /** Build the live [OGStyle] that applies this pipeline each frame. */
    fun toStyle(): OGStyle = OGStyle(ops)
}

/**
 * The result of applying an [OGStyle] at one instant. [outline] is the transformed vertex list
 * (stroke it, or wrap it in an [OGPolygonShape] for a `clipShape`). If the pipeline ended in a
 * `pixelate` op, [pixels] holds the centres of the filled grid cells and [pixelSize] their side
 * length (both in the input's units) — draw a square of that size at each centre. [isPixelated]
 * tells the two apart.
 */
data class OGStyleFrame(
    val outline: List<OGPoint>,
    val pixels: List<OGPoint> = emptyList(),
    val pixelSize: Float = 0f,
) {
    /** True when the pipeline produced a pixel fill (draw [pixels] as squares, not [outline]). */
    val isPixelated: Boolean get() = pixels.isNotEmpty()
}

/**
 * A live, compiled [OGStyleSpec]: feeds an outline and a rising time through the op pipeline and
 * returns an [OGStyleFrame]. Build one via [OGStyleSpec.toStyle] or [OGStyles.decode]. Re-usable
 * and stateless — call [apply] every frame with a monotonically rising `timeMs`.
 */
class OGStyle internal constructor(ops: List<OGStyleOp>) {
    // Normalize op names once so the per-frame loop does no string allocation.
    private val ops: List<OGStyleOp> = ops.map { it.copy(op = it.op.lowercase()) }

    /** Run [points] through the pipeline at [timeMs]. Output units match the input. */
    fun apply(points: List<OGPoint>, timeMs: Long): OGStyleFrame {
        var outline = points
        var pixels = emptyList<OGPoint>()
        var pixelSize = 0f
        for (o in ops) when (o.op) {
            "boil" -> outline = OGBoil(
                amplitude = o.amplitude ?: 0.02f,
                boilFps = o.boilFps ?: 8f,
                smooth = o.smooth ?: true,
                seed = o.seed ?: 0,
            ).displace(outline, timeMs)
            "quantize", "step", "stepped" ->
                outline = quantizeVertices(outline, o.grid ?: o.resolution ?: 16)
            "pixelate", "pixel" -> {
                val res = (o.resolution ?: o.grid ?: 20).coerceAtLeast(1)
                pixels = pixelateFill(outline, res)
                pixelSize = 1f / res
            }
            // Unknown op: pass through unchanged (forward-compatible with newer packs).
        }
        return OGStyleFrame(outline, pixels, pixelSize)
    }

    /** Convenience overload: apply to an [OGPolygonShape]'s points. */
    fun apply(shape: OGPolygonShape, timeMs: Long): OGStyleFrame = apply(shape.points, timeMs)
}

/**
 * The **codec** for [OGStyleSpec] — decode a JSON style pack (designer- or model-authored) into a
 * live [OGStyle], encode one back to JSON, and build a prompt that constrains a model to emit a
 * valid pack. Mirrors [OGAiVector]: tolerant of code fences / stray prose, reuses the same lenient
 * [OGAiVector.json] config and [OGAiVector.extractJson] so behaviour is identical across the two
 * data layers. No network, no AI SDK — just the contract and the parse/serialize.
 */
object OGStyles {
    /** The shared lenient JSON config (reused from [OGAiVector] so both data layers agree). */
    val json get() = OGAiVector.json

    /** Decode a JSON style pack into its [OGStyleSpec]. Throws on malformed input. */
    fun decodeSpec(text: String): OGStyleSpec =
        json.decodeFromString(OGStyleSpec.serializer(), OGAiVector.extractJson(text))

    /** Decode a JSON style pack straight into a live [OGStyle]. Throws on malformed input. */
    fun decode(text: String): OGStyle = decodeSpec(text).toStyle()

    /** [decode] but returns `null` instead of throwing on malformed input. */
    fun decodeOrNull(text: String): OGStyle? = runCatching { decode(text) }.getOrNull()

    /** Serialize a style pack to JSON (compact; defaults omitted). */
    fun encode(spec: OGStyleSpec): String = json.encodeToString(OGStyleSpec.serializer(), spec)

    /**
     * Build a ready-to-send instruction constraining a model to output exactly the style-pack JSON
     * [decode] expects. Wire the returned string into your model; pass the reply to [decode].
     *
     * @param instruction the natural-language ask, e.g. "a nervous pencil sketch that looks hand-drawn".
     */
    fun stylePrompt(instruction: String): String = """
        You output ONLY a JSON object describing a drawing-style "pack" for the KMPMedia library: an
        ordered pipeline of ops applied to a shape's outline.

        Ops (use any subset, in order; put pixelate last if used):
        - {"op":"boil","amplitude":<~0.01..0.06>,"boilFps":<~4..12>,"smooth":<true|false>} —
          the living/wiggly hand-drawn line (amplitude is a fraction of the box, 0..1 space).
        - {"op":"quantize","grid":<~8..32>} — snap vertices to a grid (stepped, stop-motion line).
        - {"op":"pixelate","resolution":<~10..32>} — a low-res pixel/mosaic fill of the shape.

        Schema:
        {"name":"<short name>","ops":[{"op":"...", ...}, ...]}

        Example (a shimmering pixel sprite):
        {"name":"pixel sprite","ops":[{"op":"boil","amplitude":0.03,"boilFps":10},{"op":"pixelate","resolution":20}]}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()
}
