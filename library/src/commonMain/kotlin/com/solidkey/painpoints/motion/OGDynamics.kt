package com.solidkey.painpoints.motion

import com.solidkey.painpoints.ai.OGAiVector
import kotlinx.serialization.Serializable

/**
 * The **data form of a motion "feel"** — a bundle of the dynamics knobs that make motion read as
 * natural, as plain serializable JSON. This is what makes the whole dynamics pack **AI-authorable**
 * the same way `OGStyles` / `OGLooks` / `OGMotions` are: a developer describes the feel in plain
 * language, a model returns this JSON (see [OGDynamics.dynamicsPrompt]), and the library decodes and
 * evaluates it — with no AI SDK and no networking in the library.
 *
 * @param spring the core [OGSpringSpec] (stiffness + damping) — the lag / overshoot / settle.
 * @param followLinks length of a trailing [OGFollowChain] (0 = none) for hair / cape / limb follow-through.
 * @param squash squash-and-stretch intensity off speed (`0` = none; see [OGSquash]).
 * @param sway idle noise-sway amplitude (`0` = none; see [OGSway]).
 * @param easing an [OGEasing] name for discrete A→B moves ("anticipate", "overshoot", "easeOut", …).
 */
@Serializable
data class OGDynamicsSpec(
    val name: String? = null,
    val spring: OGSpringSpec = OGSpringSpec(),
    val followLinks: Int = 0,
    val squash: Float = 0f,
    val sway: Float = 0f,
    val easing: String = "easeOut",
) {
    /** The [OGEasing] this spec's [easing] name resolves to. */
    fun easingMode(): OGEasing = ogEasingOf(easing)
}

/**
 * The **codec** for the motion-dynamics pack — decode / encode an [OGDynamicsSpec] from JSON
 * (designer- or model-authored), reach for a ready-made feel [presets], or build a [dynamicsPrompt]
 * that constrains a model to emit a valid feel. Mirrors `OGMotions` / `OGLooks` / `OGStyles`: tolerant
 * of code fences and stray prose, reusing the same [OGAiVector.json] / [OGAiVector.extractJson] so
 * every data layer in the library behaves identically. No network, no AI SDK.
 */
object OGDynamics {
    /** The shared lenient JSON config (reused from `OGAiVector` so all data layers agree). */
    val json get() = OGAiVector.json

    /** Decode a JSON dynamics pack into its [OGDynamicsSpec]. Throws on malformed input. */
    fun decodeSpec(text: String): OGDynamicsSpec =
        json.decodeFromString(OGDynamicsSpec.serializer(), OGAiVector.extractJson(text))

    /** [decodeSpec] but returns `null` instead of throwing on malformed input. */
    fun decodeSpecOrNull(text: String): OGDynamicsSpec? = runCatching { decodeSpec(text) }.getOrNull()

    /** Serialize a dynamics pack to JSON (compact; defaults omitted). */
    fun encode(spec: OGDynamicsSpec): String = json.encodeToString(OGDynamicsSpec.serializer(), spec)

    /** Ready-made feels, keyed by name. Each is a plain [OGDynamicsSpec] you can `copy()`. */
    val presets: Map<String, OGDynamicsSpec> = linkedMapOf(
        // Lively, cartoonish — overshoots and springs, with squash on the bounce.
        "bouncy" to OGDynamicsSpec(name = "bouncy", spring = OGSpringSpec(stiffness = 240f, dampingRatio = 0.35f), squash = 0.3f, easing = "overshoot"),
        // Weighty — slow, heavy settle with a touch of follow-through and squash.
        "heavy" to OGDynamicsSpec(name = "heavy", spring = OGSpringSpec(stiffness = 90f, dampingRatio = 0.7f), followLinks = 3, squash = 0.2f, easing = "easeOut"),
        // Crisp — fast and controlled, barely any overshoot.
        "snappy" to OGDynamicsSpec(name = "snappy", spring = OGSpringSpec(stiffness = 520f, dampingRatio = 0.75f), easing = "easeOut"),
        // Soft — gentle, no overshoot, slight idle sway (alive-but-calm).
        "gentle" to OGDynamicsSpec(name = "gentle", spring = OGSpringSpec(stiffness = 120f, dampingRatio = 1f), sway = 0.4f, easing = "easeInOut"),
        // Rigid — stiff and immediate (a mechanical / UI feel).
        "stiff" to OGDynamicsSpec(name = "stiff", spring = OGSpringSpec(stiffness = 700f, dampingRatio = 1f), easing = "easeOut"),
    )

    /** Look up a [presets] entry by name (case-insensitive), or `null` if there is none. */
    fun preset(name: String): OGDynamicsSpec? = presets[name.lowercase()]

    /**
     * Build a ready-to-send instruction constraining a model to output exactly the dynamics-pack JSON
     * [decodeSpec] expects. Wire it into your model; pass the reply to [decodeSpec]. The developer
     * describes the FEEL ("a heavy, bouncy walk that overshoots and settles slowly, hair trailing")
     * and the model returns the numbers.
     */
    fun dynamicsPrompt(instruction: String): String = """
        You output ONLY a JSON object describing the FEEL of a motion for the KMPMedia library (how it
        moves, not what moves). Every field is optional; omit a field to keep its default.

        Schema (default in parentheses):
        {
          "name":        "<short name>",
          "spring": {
            "stiffness":    <>0 (200): how hard it pulls to the target; higher = faster/snappier>,
            "dampingRatio": <>=0 (0.6): <1 bouncy (overshoots), 1 = no overshoot, >1 sluggish>
          },
          "followLinks": <int (0): length of a trailing chain for hair/cape/limb follow-through>,
          "squash":      <0..1 (0): squash-and-stretch intensity with speed>,
          "sway":        <0..1 (0): idle noise-sway amplitude so it is never a statue>,
          "easing":      "<one of: linear, easeIn, easeOut, easeInOut, overshoot, anticipate, anticipateOvershoot>"
        }

        Examples:
        {"name":"bouncy","spring":{"stiffness":240,"dampingRatio":0.35},"squash":0.3,"easing":"overshoot"}
        {"name":"heavy walk","spring":{"stiffness":90,"dampingRatio":0.7},"followLinks":3,"squash":0.2}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()
}
