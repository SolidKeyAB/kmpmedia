package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec + presets** for [OGDigitalRainSpec] — decode a JSON pack, encode one back, build a
 * model prompt, and reach for a ready-made look ([MATRIX], [SPARSE], [RED]). Mirrors the rest of the
 * FX / data layers: lenient JSON, tolerant extraction, no network, no AI SDK.
 */
object OGDigitalRain {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGDigitalRainSpec =
        json.decodeFromString(OGDigitalRainSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGDigitalRainSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGDigitalRainSpec): String = json.encodeToString(OGDigitalRainSpec.serializer(), spec)

    fun preset(name: String): OGDigitalRainSpec? = presets[name.lowercase()]

    fun digitalRainPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a falling-glyph digital rain effect for the KMPMedia library.

        Each column scrolls downward; a bright head glyph leads a fading tail. Characters flicker via
        deterministic per-cell hashing. bgAlpha 0.85 gives the classic opaque Matrix look; 0 makes it a
        transparent overlay over other content. trailColor drives the dominant rain hue.

        Fields (all optional): name, columnCount, glyphSet, speed, tailLength, glyphChangeRate,
        headColor, trailColor, bgAlpha, alpha, speedJitter, seed.

        Schema: {"name":"<short name>", ...fields...}
        Example: {"name":"matrix","columnCount":24,"speed":9.0,"trailColor":"#00FF41","bgAlpha":0.85}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /** Classic Matrix — green katakana rain on opaque black. */
    val MATRIX = OGDigitalRainSpec(
        name = "matrix", columnCount = 24, speed = 9f, tailLength = 18,
        glyphChangeRate = 14f, headColor = "#FFFFFF", trailColor = "#00FF41",
        bgAlpha = 0.85f, alpha = 1f, speedJitter = 0.45f, seed = 0,
    )

    /** Sparse — slow thin rain as a transparent overlay over other content. */
    val SPARSE = OGDigitalRainSpec(
        name = "sparse", columnCount = 12, speed = 4f, tailLength = 10,
        glyphChangeRate = 8f, headColor = "#CCFFCC", trailColor = "#00AA30",
        bgAlpha = 0f, alpha = 0.65f, speedJitter = 0.6f, seed = 3,
    )

    /** Red — danger / alert variant with hot magenta heads. */
    val RED = OGDigitalRainSpec(
        name = "red", columnCount = 24, speed = 11f, tailLength = 14,
        glyphChangeRate = 18f, headColor = "#FF9999", trailColor = "#CC0000",
        bgAlpha = 0.8f, alpha = 1f, speedJitter = 0.3f, seed = 7,
    )

    /** All built-in presets, keyed by lowercase name. */
    val presets: Map<String, OGDigitalRainSpec> = listOf(MATRIX, SPARSE, RED).associateBy { it.name!! }
}
