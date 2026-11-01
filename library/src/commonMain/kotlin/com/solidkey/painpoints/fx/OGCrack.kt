package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec + presets** for [OGCrackSpec] — decode a JSON pack, encode one back, build a model prompt,
 * and reach for a ready-made surface ([GROUND], [ICE], [TILE], [GLASS]). Mirrors the rest of the FX /
 * data layers: lenient JSON, tolerant extraction, no network, no AI SDK.
 */
object OGCrack {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGCrackSpec =
        json.decodeFromString(OGCrackSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGCrackSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGCrackSpec): String = json.encodeToString(OGCrackSpec.serializer(), spec)

    /** Look up a built-in preset by name (case-insensitive), or `null`. */
    fun preset(name: String): OGCrackSpec? = presets[name.lowercase()]

    fun crackPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a fracture / crack burst for the KMPMedia library.

        It renders as jagged crack arms growing outward from an impact point as an external progress goes
        0..1, drawn as a dark shadow underlay plus a bright crack line. length, crackWidth and shadowWidth
        are fractions of the view's shorter side; irregularity 0..1 is how jagged the arms bend; depth is
        how many generations of sub-branches each arm spawns.

        Fields (all optional): name, crackColor, shadowColor, arms, depth, length, crackWidth,
        shadowWidth, irregularity, seed.

        Schema: {"name":"<short name>", ...fields...}
        Example: {"name":"ice","crackColor":"#E3F2FD","shadowColor":"#1565C0","arms":12,"depth":2,"length":0.35,"irregularity":0.7}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /** Wide rough earthquake cracks in the ground — the classic impact fracture. */
    val GROUND = OGCrackSpec(
        name = "ground", crackColor = "#CCCCCC", shadowColor = "#050302", arms = 7, depth = 1,
        length = 0.40f, crackWidth = 0.012f, shadowWidth = 0.028f, irregularity = 0.5f,
    )

    /** Thin blue-white spider-web — many fine branching arms, like cracking lake ice. */
    val ICE = OGCrackSpec(
        name = "ice", crackColor = "#E3F2FD", shadowColor = "#1565C0", arms = 12, depth = 2,
        length = 0.35f, crackWidth = 0.006f, shadowWidth = 0.014f, irregularity = 0.7f,
    )

    /** Hairline cracks on a rigid tile — few arms, short, barely bending. */
    val TILE = OGCrackSpec(
        name = "tile", crackColor = "#424242", shadowColor = "#000000", arms = 5, depth = 1,
        length = 0.30f, crackWidth = 0.007f, shadowWidth = 0.018f, irregularity = 0.3f,
    )

    /** Fine radial glass fracture — very thin, very angular, densely webbed. */
    val GLASS = OGCrackSpec(
        name = "glass", crackColor = "#ECEFF1", shadowColor = "#37474F", arms = 9, depth = 2,
        length = 0.32f, crackWidth = 0.005f, shadowWidth = 0.012f, irregularity = 0.8f,
    )

    /** All built-in presets, keyed by lowercase name. */
    val presets: Map<String, OGCrackSpec> = listOf(GROUND, ICE, TILE, GLASS).associateBy { it.name!! }
}
