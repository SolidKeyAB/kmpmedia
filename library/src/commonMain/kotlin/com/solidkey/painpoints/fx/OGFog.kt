package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec + presets** for [OGFogSpec] — decode a JSON pack, encode one back, build a model prompt,
 * and reach for a ready-made haze ([FOG], [SMOKE], [MIST], [STEAM]). Mirrors the rest of the FX / data
 * layers: lenient JSON, tolerant extraction, no network, no AI SDK.
 */
object OGFog {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGFogSpec =
        json.decodeFromString(OGFogSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGFogSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGFogSpec): String = json.encodeToString(OGFogSpec.serializer(), spec)

    /** Look up a built-in preset by name (case-insensitive), or `null`. */
    fun preset(name: String): OGFogSpec? = presets[name.lowercase()]

    fun fogPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a drifting fog / smoke haze for the KMPMedia library.

        It renders as soft overlapping puffs that drift and wander. Coordinates: drift is a fraction of
        the view per second (driftY negative rises like smoke); scale is the puff radius as a fraction of
        the shorter side; opacity and softness are 0..1.

        Fields (all optional): name, color, density, opacity, scale, driftX, driftY, turbulence,
        wanderSpeed, octaves, softness, seed.

        Schema: {"name":"<short name>", ...fields...}
        Example: {"name":"smoke","color":"#8A8F98","density":16,"opacity":0.5,"scale":0.45,"driftY":-0.06,"turbulence":0.12}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /** Soft pale ground fog — slow, wide, barely-moving haze that sits over a scene. */
    val FOG = OGFogSpec(
        name = "fog", color = "#F2F4F7", density = 20, opacity = 0.42f, scale = 0.6f,
        driftX = 0.025f, driftY = 0.004f, turbulence = 0.06f, wanderSpeed = 0.1f, octaves = 3, seed = 11,
    )

    /** Grey smoke curling and rising (driftY up), more turbulent and churny than fog. */
    val SMOKE = OGFogSpec(
        name = "smoke", color = "#8A8F98", density = 16, opacity = 0.5f, scale = 0.45f,
        driftX = 0.01f, driftY = -0.06f, turbulence = 0.12f, wanderSpeed = 0.25f, octaves = 4, seed = 23,
    )

    /**
     * A calm, low, enveloping white veil. The gentlest-moving of the four, but tuned to actually *read*
     * as mist — a continuous soft bank, not a scattered haze. Every field is settable: raise [OGFogSpec.density]
     * and [OGFogSpec.opacity] for a thicker fog, drop them for a barely-there breath.
     */
    val MIST = OGFogSpec(
        name = "mist", color = "#FFFFFF", density = 24, opacity = 0.42f, scale = 0.8f,
        driftX = 0.012f, driftY = 0.004f, turbulence = 0.05f, wanderSpeed = 0.08f, octaves = 2, seed = 5,
    )

    /** Wispy white steam rising fast from below — small, quick, very turbulent. */
    val STEAM = OGFogSpec(
        name = "steam", color = "#FBFBFD", density = 18, opacity = 0.4f, scale = 0.35f,
        driftX = 0.02f, driftY = -0.1f, turbulence = 0.15f, wanderSpeed = 0.35f, octaves = 4, seed = 31,
    )

    /** All built-in presets, keyed by lowercase name. */
    val presets: Map<String, OGFogSpec> = listOf(FOG, SMOKE, MIST, STEAM).associateBy { it.name!! }
}
