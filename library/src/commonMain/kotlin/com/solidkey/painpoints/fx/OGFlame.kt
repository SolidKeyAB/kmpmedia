package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec + presets** for [OGFlameSpec] — decode a JSON pack, encode one back, build a model prompt,
 * and reach for a ready-made fire ([CANDLE], [CAMPFIRE], [TORCH], [INFERNO]). Mirrors the rest of the FX /
 * data layers: lenient JSON, tolerant extraction, no network, no AI SDK.
 */
object OGFlame {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGFlameSpec =
        json.decodeFromString(OGFlameSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGFlameSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGFlameSpec): String = json.encodeToString(OGFlameSpec.serializer(), spec)

    /** Look up a built-in preset by name (case-insensitive), or `null`. */
    fun preset(name: String): OGFlameSpec? = presets[name.lowercase()]

    fun flamePrompt(instruction: String): String = """
        You output ONLY a JSON object describing a body of fire for the KMPMedia library.

        It renders as upward-licking tongues rooted along a baseline, flickering and waving, with a hot
        core colour at the base fading to a cooler tip colour. Coordinates are 0..1 of the view: height,
        width, sway and wind are fractions of the view; baseY is where the fire sits (1 = the bottom).
        wind bends the whole flame sideways (+ right, - left) and is meant to track a scene's breeze.

        Fields (all optional): name, coreColor, tipColor, tongues, height, heightJitter, width, sway,
        wind, swaySpeed, flicker, flickerSpeed, octaves, opacity, baseY, seed.

        Schema: {"name":"<short name>", ...fields...}
        Example: {"name":"campfire","coreColor":"#FFD24D","tipColor":"#FF3B10","tongues":10,"height":0.6,"flicker":0.35}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /** A single calm candle flame — one slim warm tongue, a gentle flicker, easy to bend in a draught. */
    val CANDLE = OGFlameSpec(
        name = "candle", coreColor = "#FFF3C4", tipColor = "#FF8A2A",
        tongues = 1, height = 0.5f, heightJitter = 0.12f, width = 0.09f,
        sway = 0.03f, swaySpeed = 0.9f, flicker = 0.22f, flickerSpeed = 2.0f, octaves = 2,
        opacity = 0.95f, seed = 7,
    )

    /** A warm hearth fire — a spread of overlapping orange tongues, lively but contained. */
    val CAMPFIRE = OGFlameSpec(
        name = "campfire", coreColor = "#FFD24D", tipColor = "#FF3B10",
        tongues = 10, height = 0.6f, heightJitter = 0.5f, width = 0.17f,
        sway = 0.06f, swaySpeed = 1.1f, flicker = 0.34f, flickerSpeed = 2.4f, octaves = 3,
        opacity = 0.92f, seed = 19,
    )

    /** A tight hand-torch flame — fewer, taller tongues with a strong steady lean, as if carried. */
    val TORCH = OGFlameSpec(
        name = "torch", coreColor = "#FFE08A", tipColor = "#FF4312",
        tongues = 4, height = 0.78f, heightJitter = 0.3f, width = 0.13f,
        sway = 0.05f, wind = 0.08f, swaySpeed = 1.4f, flicker = 0.28f, flickerSpeed = 2.8f, octaves = 3,
        opacity = 0.94f, seed = 29,
    )

    /** A roaring blaze — many tall, ragged, fast-flickering tongues that fill the frame. */
    val INFERNO = OGFlameSpec(
        name = "inferno", coreColor = "#FFE65C", tipColor = "#E01A00",
        tongues = 20, height = 0.92f, heightJitter = 0.6f, width = 0.2f,
        sway = 0.09f, swaySpeed = 1.6f, flicker = 0.45f, flickerSpeed = 3.2f, octaves = 4,
        opacity = 0.95f, seed = 41,
    )

    /** All built-in presets, keyed by lowercase name. */
    val presets: Map<String, OGFlameSpec> = listOf(CANDLE, CAMPFIRE, TORCH, INFERNO).associateBy { it.name!! }
}
