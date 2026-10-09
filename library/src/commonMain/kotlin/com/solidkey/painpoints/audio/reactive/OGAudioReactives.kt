package com.solidkey.painpoints.audio.reactive

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec** for [OGAudioReactiveSpec] — decode a JSON `.reactive` pack (designer- or model-authored)
 * into a spec, encode one back, reach for a ready-made response [preset] (snappy / smooth / beat), or
 * build a [reactivePrompt] that constrains a model to emit a valid pack. Mirrors `OGLooks` / `OGStyles`:
 * tolerant of code fences and stray prose, and reuses the same [OGAiVector.json] / [OGAiVector.extractJson]
 * so every data layer in the library behaves identically. No network, no AI SDK.
 */
object OGAudioReactives {
    /** The shared lenient JSON config (reused from `OGAiVector` so all data layers agree). */
    val json get() = OGAiVector.json

    /** Decode a JSON reactive pack into its [OGAudioReactiveSpec]. Throws on malformed input. */
    fun decodeSpec(text: String): OGAudioReactiveSpec =
        json.decodeFromString(OGAudioReactiveSpec.serializer(), OGAiVector.extractJson(text))

    /** [decodeSpec] but returns `null` instead of throwing on malformed input. */
    fun decodeSpecOrNull(text: String): OGAudioReactiveSpec? = runCatching { decodeSpec(text) }.getOrNull()

    /** Serialize a reactive pack to JSON (compact; defaults omitted). */
    fun encode(spec: OGAudioReactiveSpec): String = json.encodeToString(OGAudioReactiveSpec.serializer(), spec)

    /** Ready-made response profiles, keyed by name. Each is a plain [OGAudioReactiveSpec] you can `copy()`. */
    val presets: Map<String, OGAudioReactiveSpec> = linkedMapOf(
        // Percussive: rises instantly, falls fast — punchy on drums/hits.
        "snappy" to OGAudioReactiveSpec(name = "snappy", attack = 0.9f, release = 0.25f, gain = 1.6f, beatSensitivity = 1.35f),
        // Flowing: eases both ways — good for ambient / vocals / smooth pulses.
        "smooth" to OGAudioReactiveSpec(name = "smooth", attack = 0.35f, release = 0.08f, gain = 1.4f, beatSensitivity = 1.8f),
        // Beat-biased: tuned so [OGAudioBands.beat] fires cleanly on strong onsets.
        "beat" to OGAudioReactiveSpec(name = "beat", attack = 0.95f, release = 0.3f, gain = 1.5f, beatSensitivity = 1.25f),
    )

    /** Look up a [presets] entry by name, or `null` if there is none. */
    fun preset(name: String): OGAudioReactiveSpec? = presets[name]

    /**
     * Build a ready-to-send instruction constraining a model to output exactly the reactive-pack JSON
     * [decodeSpec] expects. Wire the returned string into your model; pass the reply to [decodeSpec].
     *
     * @param instruction the natural-language ask, e.g. "a snappy response that pops on every drum hit".
     */
    fun reactivePrompt(instruction: String): String = """
        You output ONLY a JSON object configuring KMPMedia's audio-reactive analyzer. Every field is
        optional; omit a field to keep its default.

        Schema (numbers; default in parentheses):
        {
          "name": "<short name>",
          "attack":          <0..1 (0.6): how fast bands rise; higher = snappier>,
          "release":         <0..1 (0.14): how fast bands fall; lower = longer tails>,
          "gain":            <>0 (1.5): output multiplier before the 0..1 clamp>,
          "beatSensitivity": <>1 (1.5): higher = fewer, stronger-only beats>,
          "bassMaxHz":       <(250): upper edge of the bass band>,
          "midMaxHz":        <(4000): upper edge of the mid band>,
          "trebleMaxHz":     <(16000): upper edge of the treble band>
        }

        Examples:
        {"name":"punchy","attack":0.9,"release":0.25,"beatSensitivity":1.3}
        {"name":"ambient","attack":0.3,"release":0.07}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()
}
