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
 * Built-in ops (all zero-dependency; boil/quantize/pixelate in [OGBoil.kt], the geometry ops in
 * [OGStyleOps.kt]):
 * - **`boil`** — [OGBoil.displace]; the living/wiggly line. Params: `amplitude`, `boilFps`,
 *   `smooth`, `seed`.
 * - **`quantize`** (aliases `step`, `stepped`) — [quantizeVertices]; snap vertices to a grid.
 *   Params: `grid` (or `resolution`).
 * - **`subdivide`** (alias `resample`) — [subdivideOutline]; insert points per edge so a
 *   low-vertex outline has enough to move under a later op. Param: `detail`.
 * - **`smooth`** (alias `round`) — [smoothOutline]; resample through the centripetal spline for a
 *   rounded outline. Params: `strength` (`0..1`), `detail`.
 * - **`roughen`** (aliases `rough`, `sketch`) — [roughenVertices]; a static hand-drawn / sketch
 *   edge (subdivide + fixed noise). Params: `amplitude`, `detail`, `seed`.
 * - **`wave`** (alias `ripple`) — [waveVertices]; a travelling sinusoidal ripple along the outline.
 *   Params: `amplitude`, `waves`, `speed`.
 * - **`pixelate`** (alias `pixel`) — [pixelateFill]; a terminal *fill* op producing a pixel grid.
 *   Param: `resolution` (or `grid`). Terminal: any op placed after it is ignored.
 *
 * Decoding/encoding + a prompt builder live on [OGStyles]. Everything here is pure + deterministic;
 * the spec is compiled to typed steps once (op names resolved, `OGBoil` instances built up front),
 * so [OGStyle.apply] is offset/round/lerp per vertex with each op allocating only its output list —
 * it holds 60fps, the same perf gate [OGBoil] clears. Unknown ops are dropped and unknown keys
 * ignored, so a newer pack degrades gracefully on an older library.
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
    /** `smooth`: roundness `0..1` (`0` = original straight edges, `1` = full centripetal rounding). */
    val strength: Float? = null,
    /** `subdivide`/`roughen`/`smooth`: points (or samples) inserted per edge — denser = finer. */
    val detail: Int? = null,
    /** `wave`: number of full ripple cycles around the outline. */
    val waves: Int? = null,
    /** `wave`: ripple cycles per second (the travel speed). */
    val speed: Float? = null,
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
    /**
     * True when the pipeline ended in a `pixelate` op — draw [pixels] as squares of [pixelSize], not
     * [outline]. Keyed on [pixelSize] (set whenever pixelate runs), **not** on [pixels] being
     * non-empty, so a pixelate that happens to produce zero cells (a tiny shape on a coarse grid)
     * still renders as "empty pixels" rather than silently falling back to a solid [outline] fill.
     */
    val isPixelated: Boolean get() = pixelSize > 0f
}

/**
 * A live, compiled [OGStyleSpec]: feeds an outline and a rising time through the op pipeline and
 * returns an [OGStyleFrame]. Build one via [OGStyleSpec.toStyle] or [OGStyles.decode]. Re-usable
 * and stateless — call [apply] every frame with a monotonically rising `timeMs`.
 */
class OGStyle internal constructor(ops: List<OGStyleOp>) {
    /**
     * One compiled pipeline step, resolved from an [OGStyleOp] once so [apply] does no per-frame
     * name dispatch or parameter boxing (the [OGBoil] is built here, up front, not each frame).
     */
    private sealed interface Step {
        class Boil(val boil: OGBoil) : Step
        class Quantize(val grid: Int) : Step
        class Pixelate(val resolution: Int) : Step
        class Subdivide(val detail: Int) : Step
        class Smooth(val strength: Float, val detail: Int) : Step
        class Roughen(val amplitude: Float, val detail: Int, val seed: Int) : Step
        class Wave(val amplitude: Float, val waves: Int, val speed: Float) : Step
    }

    // Compile the spec ONCE. Op names are resolved, OGBoil instances built; an op with an
    // out-of-range grid/resolution (< 1) or an unknown name is dropped (a no-op), so every frame
    // runs a fixed, valid step list — pure offset/round/lerp with no name compares or allocation
    // beyond each op's output.
    private val steps: List<Step> = ops.mapNotNull { o ->
        when (o.op.lowercase()) {
            "boil" -> Step.Boil(
                OGBoil(
                    amplitude = o.amplitude ?: 0.02f,
                    boilFps = o.boilFps ?: 8f,
                    smooth = o.smooth ?: true,
                    seed = o.seed ?: 0,
                ),
            )
            "quantize", "step", "stepped" ->
                (o.grid ?: o.resolution ?: 16).takeIf { it >= 1 }?.let { Step.Quantize(it) }
            "pixelate", "pixel" ->
                (o.resolution ?: o.grid ?: 20).takeIf { it >= 1 }?.let { Step.Pixelate(it) }
            "subdivide", "resample" ->
                (o.detail ?: 4).takeIf { it >= 1 }?.let { Step.Subdivide(it) }
            "smooth", "round" -> Step.Smooth(
                strength = (o.strength ?: 1f).coerceIn(0f, 1f),
                detail = (o.detail ?: 6).coerceAtLeast(1),
            )
            "roughen", "rough", "sketch" -> Step.Roughen(
                amplitude = o.amplitude ?: 0.03f,
                detail = (o.detail ?: 3).coerceAtLeast(1),
                seed = o.seed ?: 0,
            )
            "wave", "ripple" -> Step.Wave(
                amplitude = o.amplitude ?: 0.02f,
                waves = (o.waves ?: 3).coerceAtLeast(1),
                speed = o.speed ?: 1f,
            )
            else -> null // unknown op: dropped (forward-compatible with newer packs)
        }
    }

    /**
     * Run [points] through the pipeline at [timeMs]; output units match the input. A `pixelate` step
     * is **terminal** — it produces the pixel fill and any later step is ignored, so "pixelate last"
     * is enforced rather than merely advised.
     */
    fun apply(points: List<OGPoint>, timeMs: Long): OGStyleFrame {
        var outline = points
        for (s in steps) when (s) {
            is Step.Boil -> outline = s.boil.displace(outline, timeMs)
            is Step.Quantize -> outline = quantizeVertices(outline, s.grid)
            is Step.Subdivide -> outline = subdivideOutline(outline, s.detail)
            is Step.Smooth -> outline = smoothOutline(outline, s.strength, s.detail)
            is Step.Roughen -> outline = roughenVertices(outline, s.amplitude, s.detail, s.seed)
            is Step.Wave -> outline = waveVertices(outline, s.amplitude, s.waves, s.speed, timeMs)
            is Step.Pixelate ->
                return OGStyleFrame(outline, pixelateFill(outline, s.resolution), 1f / s.resolution)
        }
        return OGStyleFrame(outline)
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
        - {"op":"subdivide","detail":<~2..8>} — add points per edge (use before boil/wave/roughen).
        - {"op":"smooth","strength":<0..1>,"detail":<~4..8>} — round the outline (a clean curve).
        - {"op":"roughen","amplitude":<~0.01..0.06>,"detail":<~2..6>} — a static hand-drawn / sketch edge.
        - {"op":"wave","amplitude":<~0.01..0.06>,"waves":<~2..6>,"speed":<~0.5..2>} — a travelling ripple.
        - {"op":"pixelate","resolution":<~10..32>} — a low-res pixel/mosaic fill of the shape.

        Schema:
        {"name":"<short name>","ops":[{"op":"...", ...}, ...]}

        Examples:
        {"name":"pixel sprite","ops":[{"op":"boil","amplitude":0.03,"boilFps":10},{"op":"pixelate","resolution":20}]}
        {"name":"hand-drawn","ops":[{"op":"roughen","amplitude":0.02,"detail":4},{"op":"boil","amplitude":0.012,"boilFps":7}]}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()
}
