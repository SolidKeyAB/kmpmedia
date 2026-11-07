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

        It renders using a Sedov blast curve (fast explosive start) with: an epicenter fireball flash at
        t<25%, a soft inner compression wake, a sharp Mach-stem leading edge, and a trailing secondary
        rebound ring. squash 1.0 is a circle; 0.5 is a 2:1 ground-plane oval. turbulence 0..0.15 adds
        harmonic ring flutter.

        Fields (all optional): name, color, maxRadius, ringWidth, squash, alpha, seed,
        innerGlow, secondaryRing, coreFlash, turbulence.

        Schema: {"name":"<short name>", ...fields...}
        Example: {"name":"ground","color":"#FF9800","maxRadius":0.60,"ringWidth":0.038,"squash":0.45,"alpha":0.88,"turbulence":0.055}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /** White thunder airburst — full circle expanding from the strike point with core flash. */
    val AIRBURST = OGShockwaveSpec(
        name = "airburst", color = "#E0F7FA", maxRadius = 0.50f, ringWidth = 0.028f,
        squash = 1.0f, alpha = 0.90f, innerGlow = true, secondaryRing = true,
        coreFlash = true, turbulence = 0.02f,
    )

    /** Orange earthquake ground roll — wide 2:1 oval, high turbulence, heavy flash. */
    val GROUND = OGShockwaveSpec(
        name = "ground", color = "#FF9800", maxRadius = 0.60f, ringWidth = 0.038f,
        squash = 0.45f, alpha = 0.88f, innerGlow = true, secondaryRing = true,
        coreFlash = true, turbulence = 0.055f,
    )

    /** Light-blue flood splash — water ring with gentle turbulence, no core flash. */
    val SPLASH = OGShockwaveSpec(
        name = "splash", color = "#4FC3F7", maxRadius = 0.52f, ringWidth = 0.025f,
        squash = 0.55f, alpha = 0.80f, innerGlow = true, secondaryRing = true,
        coreFlash = false, turbulence = 0.015f,
    )

    /** Pale-gold electric burst — tight circle, strong flash, wild turbulence. */
    val ELECTRIC = OGShockwaveSpec(
        name = "electric", color = "#FFF59D", maxRadius = 0.45f, ringWidth = 0.022f,
        squash = 1.0f, alpha = 0.95f, innerGlow = true, secondaryRing = true,
        coreFlash = true, turbulence = 0.075f,
    )

    /** All built-in presets, keyed by lowercase name. */
    val presets: Map<String, OGShockwaveSpec> = listOf(AIRBURST, GROUND, SPLASH, ELECTRIC).associateBy { it.name!! }
}
