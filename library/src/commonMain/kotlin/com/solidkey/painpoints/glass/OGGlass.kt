package com.solidkey.painpoints.glass

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec + presets** for [OGGlassSpec] — decode a JSON pack, encode one back, build a model prompt,
 * and reach for a ready-made lens ([MAGNIFIER], [READER], [MINIFIER], [AMBER], [ROSE]). Mirrors the rest
 * of the library's data layers: lenient JSON, tolerant extraction, no network, no AI SDK.
 */
object OGGlass {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGGlassSpec =
        json.decodeFromString(OGGlassSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGGlassSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGGlassSpec): String = json.encodeToString(OGGlassSpec.serializer(), spec)

    /** Look up a built-in preset by name (case-insensitive), or `null`. */
    fun preset(name: String): OGGlassSpec? = presets[name.lowercase()]

    fun glassPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a round glass lens overlay for the KMPMedia library.

        It renders as a circle laid over content: zoom > 1 magnifies what's underneath, zoom < 1 minifies,
        tint colours the glass. radius is a fraction of the shorter side of the view. tintStrength,
        rimAlpha and highlight are 0..1; rimWidth is a fraction of the radius.

        Fields (all optional): name, zoom, radius, tint, tintStrength, rimColor, rimWidth, rimAlpha, highlight.

        Schema: {"name":"<short name>", ...fields...}
        Example: {"name":"magnifier","zoom":2.2,"radius":0.22,"tint":"#00000000","highlight":0.3}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /** A clear reading magnifier — the everyday "glass over the text" lens. */
    val MAGNIFIER = OGGlassSpec(name = "magnifier", zoom = 2.2f, radius = 0.22f, highlight = 0.32f)

    /** A gentler, wider low-vision reader — less zoom but a bigger window so more is legible at once. */
    val READER = OGGlassSpec(name = "reader", zoom = 1.6f, radius = 0.32f, tint = "#FFFDF4", tintStrength = 0.12f, highlight = 0.25f)

    /** A reducing glass — shrinks what's underneath to fit more of a big board/photo into the circle. */
    val MINIFIER = OGGlassSpec(name = "minifier", zoom = 0.55f, radius = 0.26f, highlight = 0.28f)

    /** Warm amber glass that also magnifies — a high-contrast, glare-cutting reading tint. */
    val AMBER = OGGlassSpec(name = "amber", zoom = 1.8f, radius = 0.24f, tint = "#FFB300", tintStrength = 0.35f, rimColor = "#FFE08A", highlight = 0.3f)

    /** A rose-tinted viewing glass (colour-filter lens), no magnification. */
    val ROSE = OGGlassSpec(name = "rose", zoom = 1f, radius = 0.26f, tint = "#FF4D8D", tintStrength = 0.4f, rimColor = "#FFD6E6", highlight = 0.35f)

    /** All built-in presets, keyed by lowercase name. */
    val presets: Map<String, OGGlassSpec> =
        listOf(MAGNIFIER, READER, MINIFIER, AMBER, ROSE).associateBy { it.name!! }
}
