package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec + presets** for [OGShockwaveSpec] — decode a JSON pack, encode one back, build a model
 * prompt, and reach for a ready-made ring ([AIRBURST], [GROUND], [SPLASH], [ELECTRIC]). Mirrors the rest
 * of the FX / data layers: lenient JSON, tolerant extraction, no network, no AI SDK.
 */
object OGShockwave {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGShockwaveSpec =
        json.decodeFromString(OGShockwaveSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGShockwaveSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGShockwaveSpec): String = json.encodeToString(OGShockwaveSpec.serializer(), spec)

    /** Look up a built-in preset by name (case-insensitive), or `null`. */
    fun preset(name: String): OGShockwaveSpec? = presets[name.lowercase()]

    fun shockwavePrompt(instruction: String): String = """
        You output ONLY a JSON object describing an expanding impact shockwave ring for the KMPMedia library.

        It renders as a single expanding stroke ring driven by an external progress 0..1: the radius grows
        to maxRadius (fraction of the shorter side), alpha fades from alpha to 0, and the stroke tapers to
        half of ringWidth. squash 1.0 is a circle; 0.5 is a 2:1 ground-plane oval.

        Fields (all optional): name, color, maxRadius, ringWidth, squash, alpha, seed.

        Schema: {"name":"<short name>", ...fields...}
        Example: {"name":"ground","color":"#FF9800","maxRadius":0.55,"ringWidth":0.03,"squash":0.5,"alpha":0.8}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /** White thunder airburst — a full circle expanding from the strike point. */
    val AIRBURST = OGShockwaveSpec(
        name = "airburst", color = "#FFFFFF", maxRadius = 0.45f, ringWidth = 0.025f, squash = 1.0f, alpha = 0.85f,
    )

    /** Orange earthquake ground roll — a wide 2:1 oval hugging the floor plane. */
    val GROUND = OGShockwaveSpec(
        name = "ground", color = "#FF9800", maxRadius = 0.55f, ringWidth = 0.030f, squash = 0.50f, alpha = 0.80f,
    )

    /** Light-blue flood splash — a water ring spreading across a surface. */
    val SPLASH = OGShockwaveSpec(
        name = "splash", color = "#4FC3F7", maxRadius = 0.50f, ringWidth = 0.022f, squash = 0.55f, alpha = 0.75f,
    )

    /** Pale-gold electric/spark burst — a tight, bright, fast-reading circle. */
    val ELECTRIC = OGShockwaveSpec(
        name = "electric", color = "#FFE082", maxRadius = 0.40f, ringWidth = 0.020f, squash = 1.0f, alpha = 0.90f,
    )

    /** All built-in presets, keyed by lowercase name. */
    val presets: Map<String, OGShockwaveSpec> = listOf(AIRBURST, GROUND, SPLASH, ELECTRIC).associateBy { it.name!! }
}
