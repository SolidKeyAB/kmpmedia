package com.solidkey.painpoints.look

import androidx.compose.ui.graphics.ColorFilter
import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec** for [OGLookSpec] — decode a JSON `.look` pack (designer- or model-authored) into a
 * live [ColorFilter] or its [OGLookSpec], encode one back to JSON, and build a prompt that constrains
 * a model to emit a valid look. Mirrors `OGAiVector` / `OGStyles`: tolerant of code fences and stray
 * prose, and reuses the same lenient [OGAiVector.json] config and [OGAiVector.extractJson] so all three
 * data layers behave identically. No network, no AI SDK — just the contract and the parse/serialize.
 *
 * A handful of named [presets] (warm / cool / noir / faded / vivid) are ready-made starting points.
 */
object OGLooks {
    /** The shared lenient JSON config (reused from `OGAiVector` so all data layers agree). */
    val json get() = OGAiVector.json

    /** Decode a JSON look pack into its [OGLookSpec]. Throws on malformed input. */
    fun decodeSpec(text: String): OGLookSpec =
        json.decodeFromString(OGLookSpec.serializer(), OGAiVector.extractJson(text))

    /** Decode a JSON look pack straight into a live [ColorFilter]. Throws on malformed input. */
    fun decode(text: String): ColorFilter = decodeSpec(text).colorFilter

    /** [decode] but returns `null` instead of throwing on malformed input. */
    fun decodeOrNull(text: String): ColorFilter? = runCatching { decode(text) }.getOrNull()

    /** Serialize a look pack to JSON (compact; defaults omitted). */
    fun encode(spec: OGLookSpec): String = json.encodeToString(OGLookSpec.serializer(), spec)

    /** Ready-made looks, keyed by name. Each is a plain [OGLookSpec] you can tweak or `copy()`. */
    val presets: Map<String, OGLookSpec> = linkedMapOf(
        "warm" to OGLookSpec(name = "warm", temperature = 0.35f, saturation = 1.1f, contrast = 1.05f),
        "cool" to OGLookSpec(name = "cool", temperature = -0.35f, tint = -0.1f, contrast = 1.05f),
        "noir" to OGLookSpec(name = "noir", saturation = 0f, contrast = 1.4f, brightness = -0.03f),
        "faded" to OGLookSpec(name = "faded", contrast = 0.8f, saturation = 0.8f, brightness = 0.06f),
        "vivid" to OGLookSpec(name = "vivid", saturation = 1.45f, contrast = 1.15f),
    )

    /** Look up a [presets] entry by name, or `null` if there is none. */
    fun preset(name: String): OGLookSpec? = presets[name]

    /**
     * Build a ready-to-send instruction constraining a model to output exactly the look-pack JSON
     * [decode] expects. Wire the returned string into your model; pass the reply to [decode].
     *
     * @param instruction the natural-language ask, e.g. "a warm, faded 70s film look".
     */
    fun lookPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a colour "look" (a photo grade) for the KMPMedia
        library. Every field is optional; omit a field to leave it unchanged.

        Schema (all numbers, neutral value in parentheses):
        {
          "name": "<short name>",
          "brightness": <-1..1 (0): additive exposure>,
          "contrast":   <0..2 (1): 1 unchanged, 0 flat grey, >1 punchier>,
          "saturation": <0..2 (1): 1 unchanged, 0 greyscale, >1 richer>,
          "temperature":<-1..1 (0): + warmer, - cooler>,
          "tint":       <-1..1 (0): + magenta, - green>,
          "hue":        <-180..180 (0): hue rotation in degrees>
        }

        Examples:
        {"name":"warm film","temperature":0.3,"saturation":1.1,"contrast":1.05}
        {"name":"noir","saturation":0,"contrast":1.4}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()
}
