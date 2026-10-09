package com.solidkey.painpoints.motion

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec** for the motion-design pack — decode / encode an [OGStaggerSpec] or an [OGMotionPathSpec]
 * from JSON (designer- or model-authored), reach for a ready-made stagger [staggerPresets], or build a
 * prompt that constrains a model to emit a valid pack. Mirrors `OGParticles` / `OGLooks` / `OGStyles`:
 * tolerant of code fences and stray prose, and reuses the same [OGAiVector.json] / [OGAiVector.extractJson]
 * so every data layer in the library behaves identically. No network, no AI SDK.
 */
object OGMotions {
    /** The shared lenient JSON config (reused from `OGAiVector` so all data layers agree). */
    val json get() = OGAiVector.json

    // --- Stagger ------------------------------------------------------------------------------

    /** Decode a JSON stagger pack into its [OGStaggerSpec]. Throws on malformed input. */
    fun decodeStaggerSpec(text: String): OGStaggerSpec =
        json.decodeFromString(OGStaggerSpec.serializer(), OGAiVector.extractJson(text))

    /** [decodeStaggerSpec] but returns `null` instead of throwing on malformed input. */
    fun decodeStaggerSpecOrNull(text: String): OGStaggerSpec? = runCatching { decodeStaggerSpec(text) }.getOrNull()

    /** Serialize a stagger pack to JSON (compact; defaults omitted). */
    fun encode(spec: OGStaggerSpec): String = json.encodeToString(OGStaggerSpec.serializer(), spec)

    /** Ready-made stagger profiles, keyed by name. Each is a plain [OGStaggerSpec] you can `copy()`. */
    val staggerPresets: Map<String, OGStaggerSpec> = linkedMapOf(
        // Everyone moves at once (no offset) — a plain group ease.
        "together" to OGStaggerSpec(name = "together", stagger = 0f, easing = "easeInOut"),
        // Gentle overlap — a list that cascades in.
        "cascade" to OGStaggerSpec(name = "cascade", stagger = 0.6f, easing = "easeOut"),
        // Strong overlap with a smooth curve — a travelling ripple.
        "wave" to OGStaggerSpec(name = "wave", stagger = 0.8f, easing = "easeInOut"),
        // Near-sequential, constant speed — one after another.
        "sequential" to OGStaggerSpec(name = "sequential", stagger = 0.95f, easing = "linear"),
        // Overlapping pop — each element overshoots and springs back.
        "pop" to OGStaggerSpec(name = "pop", stagger = 0.5f, easing = "overshoot"),
        // Cascade from the far end back to the first.
        "reverse" to OGStaggerSpec(name = "reverse", stagger = 0.6f, easing = "easeOut", reverse = true),
    )

    /** Look up a [staggerPresets] entry by name (case-insensitive), or `null` if there is none. */
    fun staggerPreset(name: String): OGStaggerSpec? = staggerPresets[name.lowercase()]

    // --- Motion path --------------------------------------------------------------------------

    /** Decode a JSON path pack into its [OGMotionPathSpec]. Throws on malformed input. */
    fun decodePathSpec(text: String): OGMotionPathSpec =
        json.decodeFromString(OGMotionPathSpec.serializer(), OGAiVector.extractJson(text))

    /** [decodePathSpec] but returns `null` instead of throwing on malformed input. */
    fun decodePathSpecOrNull(text: String): OGMotionPathSpec? = runCatching { decodePathSpec(text) }.getOrNull()

    /** Decode straight to a live [OGMotionPath]. Throws on malformed input. */
    fun decodePath(text: String): OGMotionPath = decodePathSpec(text).toPath()

    /** [decodePath] but returns `null` instead of throwing on malformed input. */
    fun decodePathOrNull(text: String): OGMotionPath? = decodePathSpecOrNull(text)?.toPath()

    /** Serialize a path pack to JSON (compact; defaults omitted). */
    fun encode(spec: OGMotionPathSpec): String = json.encodeToString(OGMotionPathSpec.serializer(), spec)

    // --- Prompts ------------------------------------------------------------------------------

    /**
     * Build a ready-to-send instruction constraining a model to output exactly the stagger-pack JSON
     * [decodeStagger] expects. Wire the returned string into your model; pass the reply to [decodeStagger].
     */
    fun staggerPrompt(instruction: String): String = """
        You output ONLY a JSON object describing how a GROUP of elements animates with offset timing
        (a "stagger") for the KMPMedia library. Every field is optional; omit a field to keep its default.

        Schema (default in parentheses):
        {
          "name":    "<short name>",
          "stagger": <0..1 (0.5): spread of start offsets; 0 = all together, near 1 = one after another>,
          "easing":  "<one of: linear, easeIn, easeOut, easeInOut (default), overshoot>",
          "reverse": <true|false (false): let the last element lead instead of the first>
        }

        Examples:
        {"name":"cascade","stagger":0.6,"easing":"easeOut"}
        {"name":"ripple","stagger":0.85,"easing":"easeInOut","reverse":true}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /**
     * Build a ready-to-send instruction constraining a model to output exactly the path-pack JSON
     * [decodePathSpec] expects. Wire the returned string into your model; pass the reply to [decodePath].
     */
    fun motionPathPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a PATH an element travels along, for the KMPMedia library.

        Coordinate space is NORMALIZED: (0,0) is the top-left of the box and (1,1) the bottom-right.
        Give the path as a flat list of x,y pairs in order of travel. Every other field is optional.

        Schema (default in parentheses):
        {
          "name":      "<short name>",
          "points":    [<x0>, <y0>, <x1>, <y1>, ...],
          "closed":    <true|false (false): link the last point back to the first (a loop)>,
          "smooth":    <true|false (false): round the path into a smooth curve>,
          "smoothing": <0..1 (1): roundness when smooth is true>
        }

        Example (an S-curve left to right):
        {"points":[0.0,0.8, 0.25,0.2, 0.5,0.8, 0.75,0.2, 1.0,0.8], "smooth":true}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()
}
