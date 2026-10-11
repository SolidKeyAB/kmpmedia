package com.solidkey.painpoints.stage

import com.solidkey.painpoints.ai.OGAiVector
import com.solidkey.painpoints.ai.OGSceneSpec
import com.solidkey.painpoints.compositor.OGEasing
import com.solidkey.painpoints.compositor.OGKeyframe
import com.solidkey.painpoints.compositor.OGKeyframedFloat
import com.solidkey.painpoints.fx.OGLightningSpec
import com.solidkey.painpoints.fx.OGSlashSpec
import com.solidkey.painpoints.fx.OGSpeedLinesSpec
import com.solidkey.painpoints.look.OGBlendMode
import com.solidkey.painpoints.look.OGLookSpec
import com.solidkey.painpoints.particle.OGParticleSpec
import kotlinx.serialization.json.Json

/**
 * The single entry point for the **OGStage** document — encode / decode a whole animated movie as
 * JSON, hand a language model the contract that produces one, and a few builders for assembling a
 * stage in code.
 *
 * This is the generator half of "describe → play": a model emits one JSON object and [decode] turns
 * it into a live [OGStage] that [OGStageView] plays. It reuses [OGAiVector]'s lenient, unknown-key
 * tolerant [Json] verbatim (model output is rarely pristine), so a stage decodes alongside the
 * scene / polygon / patch payloads with the same rules, and runs at generate time — never per frame.
 */
object OGStages {

    /** Reuse the AI codec's lenient config (ignoreUnknownKeys + isLenient + encodeDefaults=false). */
    val json: Json = OGAiVector.json

    // --- Decode / encode -----------------------------------------------------------------------

    /** Serialize a live [OGStage] to its JSON (to persist a movie or seed a prompt with the current one). */
    fun encode(stage: OGStage): String = json.encodeToString(OGStage.serializer(), stage)

    /**
     * Parse a model reply (or a saved file) describing a whole movie into a live [OGStage]. Tolerates
     * surrounding prose / markdown fences (via [OGAiVector.extractJson]). Throws if no valid JSON is
     * present; use [decodeOrNull] for `null` instead.
     */
    fun decode(text: String): OGStage =
        json.decodeFromString(OGStage.serializer(), OGAiVector.extractJson(text))

    /** [decode] but returns `null` instead of throwing on malformed input. */
    fun decodeOrNull(text: String): OGStage? = runCatching { decode(text) }.getOrNull()

    // --- Code builders -------------------------------------------------------------------------

    /** A transform track from `timeMs -> value` stops (all linear); a single stop is a constant. */
    fun track(vararg stops: Pair<Long, Float>): OGKeyframedFloat = OGKeyframedFloat.of(*stops)

    /** A transform track with an explicit [easing] on every segment. */
    fun eased(easing: OGEasing, vararg stops: Pair<Long, Float>): OGKeyframedFloat =
        OGKeyframedFloat(
            default = stops.firstOrNull()?.second ?: 0f,
            keyframes = stops.map { OGKeyframe(it.first, it.second, easing) },
        )

    /** A flat colour-fill layer. */
    fun solid(
        id: String,
        color: String,
        startMs: Long = 0L,
        endMs: Long? = null,
        opacity: OGKeyframedFloat = OGKeyframedFloat.const(1f),
    ): OGStageLayer = OGStageLayer(
        id = id, content = OGStageContent.Solid(color), startMs = startMs, endMs = endMs, opacity = opacity,
    )

    /** A vector-scene layer (its own `motion` animates on the layer's local clock). */
    fun scene(
        id: String,
        scene: OGSceneSpec,
        startMs: Long = 0L,
        endMs: Long? = null,
        x: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        y: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        scale: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        rotationDeg: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        opacity: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        look: OGLookSpec? = null,
        blend: OGBlendMode = OGBlendMode.NORMAL,
    ): OGStageLayer = OGStageLayer(
        id = id, content = OGStageContent.Scene(scene), startMs = startMs, endMs = endMs,
        x = x, y = y, scale = scale, rotationDeg = rotationDeg, opacity = opacity, look = look, blend = blend,
    )

    /** A particle-emitter layer. */
    fun particles(
        id: String,
        spec: OGParticleSpec,
        startMs: Long = 0L,
        endMs: Long? = null,
        opacity: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        blend: OGBlendMode = OGBlendMode.NORMAL,
    ): OGStageLayer = OGStageLayer(
        id = id, content = OGStageContent.Particles(spec), startMs = startMs, endMs = endMs,
        opacity = opacity, blend = blend,
    )

    /** A breathing-slash ribbon layer (water / flame / thunder sweep); additive by default. */
    fun slash(
        id: String,
        spec: OGSlashSpec,
        startMs: Long = 0L,
        endMs: Long? = null,
        blend: OGBlendMode = OGBlendMode.PLUS,
    ): OGStageLayer = OGStageLayer(id = id, content = OGStageContent.Slash(spec), startMs = startMs, endMs = endMs, blend = blend)

    /** A forked-lightning layer; additive by default. */
    fun lightning(
        id: String,
        spec: OGLightningSpec,
        startMs: Long = 0L,
        endMs: Long? = null,
        blend: OGBlendMode = OGBlendMode.PLUS,
    ): OGStageLayer = OGStageLayer(id = id, content = OGStageContent.Lightning(spec), startMs = startMs, endMs = endMs, blend = blend)

    /** A manga speed / impact-lines layer. */
    fun speedLines(
        id: String,
        spec: OGSpeedLinesSpec,
        startMs: Long = 0L,
        endMs: Long? = null,
        blend: OGBlendMode = OGBlendMode.NORMAL,
    ): OGStageLayer = OGStageLayer(id = id, content = OGStageContent.SpeedLines(spec), startMs = startMs, endMs = endMs, blend = blend)

    // --- Prompt: hand a model the contract -----------------------------------------------------

    /**
     * Build a ready-to-send instruction that constrains a language model to output exactly the
     * [OGStage] JSON [decode] expects — a whole animated movie: a timeline of layers, each a scene,
     * a particle emitter or a solid fill, transformed by keyframe tracks and graded by a look.
     *
     * The full per-region scene grammar (gradients, textures, glow, scatter, region motion) is the
     * one [OGAiVector.imageToVectorPrompt] already teaches for `OGVectorTarget.SCENE`; this prompt
     * references it rather than repeating it, and focuses on the stage/timeline wrapper.
     *
     * @param instruction the natural-language ask, e.g. "a sunrise over hills with petals drifting".
     */
    fun stagePrompt(instruction: String): String = """
        You output ONLY a JSON object describing a short animated "movie" (an OGStage) for the
        KMPMedia library. One object = a timeline of layers the library plays at 60fps.

        Top level:
        {
          "width": <px, e.g. 1080>, "height": <px>, "durationMs": <total length ms>,
          "fps": <e.g. 60>, "background": "#RRGGBB" (optional base coat),
          "layers": [ <layer>, ... ]   // drawn back-to-front: layers[0] is BEHIND
        }

        Each layer:
        {
          "id": "<unique>",
          "content": <one of the three content objects below>,
          "startMs": <when it appears, default 0>, "endMs": <when it leaves, default = movie end>,
          "x": <track>, "y": <track>,          // translate as a FRACTION of width/height (0 = none)
          "scale": <track>,                     // about the layer centre (1 = unchanged)
          "rotationDeg": <track>,               // degrees about the centre
          "opacity": <track>,                   // 0..1
          "look": { "brightness":0,"contrast":1,"saturation":1,"temperature":0,"tint":0,"hue":0 },
          "blend": "normal"|"multiply"|"screen"|"overlay"|"lighten"|"darken"|"plus"
        }

        A transform <track> is a keyframed float on ABSOLUTE movie time:
          { "default": <v>, "keyframes": [ {"timeMs":<ms>,"value":<v>,"easing":"LINEAR"|"EASE_IN"|
            "EASE_OUT"|"EASE_IN_OUT"}, ... ] }
        Omit a track (or give it one keyframe) to hold it constant. A layer's content has its OWN
        internal clock that starts at the layer's startMs (so a scene's day->night begins when it
        enters), while these transform tracks run on the whole-movie clock.

        The content objects (note the "type" discriminator):
          { "type":"solid", "color":"#RRGGBB" }
          { "type":"scene", "scene": <an OGSceneSpec> }
          { "type":"particles", "particles": <an OGParticleSpec> }
          { "type":"slash", "slash": <an OGSlashSpec> }                 // a water/flame/thunder sweep
          { "type":"lightning", "lightning": <an OGLightningSpec> }     // a forked bolt
          { "type":"speedlines", "speedLines": <an OGSpeedLinesSpec> }  // manga impact lines
        The slash/lightning/speedlines specs are the KMPMedia action-FX packs, authored as their own
        JSON; they play as live, self-animating layers (good on a key beat, usually blend "plus").

        An OGSceneSpec is the SAME model-authored vector scene used elsewhere: labelled polygon
        regions in normalized 0..1 space with fills / gradients / smoothing / opacity / blend, an
        optional scene "background", a scene "look", per-region "motion" (translate/scale/rotate/
        opacity) and a scene-level "motion" that swings the grade + sky (day->night). Author it exactly
        as the KMPMedia image-to-scene contract specifies.

        An OGParticleSpec is a data-defined emitter: fields include "maxParticles", "emissionRate",
        "burst", "lifetimeMs", "x","y" (0..1 origin), "angleDeg" (270 = up), "spreadDeg", "speed",
        "gravityY", "startSize","endSize", "startColor","endColor", "shape"
        ("circle"|"square"|"triangle"|"star"|"petal"|"teardrop"), and "seed".

        Compose the movie from these: e.g. a scene layer for the backdrop (with its own day->night
        motion), a particle layer of drifting petals on top with blend "plus", and a gentle "scale"
        track on the backdrop for a slow push-in. Keep it resolution-independent (normalized coords).

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()
}
