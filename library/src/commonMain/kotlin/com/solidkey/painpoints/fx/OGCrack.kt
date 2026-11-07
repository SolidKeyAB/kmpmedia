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
        0..1: tapered strokes (root=full width, tip=12%), directional cast shadow for 3D relief, an
        epicenter void crater, and leading tip sparks. length, crackWidth and shadowWidth are fractions
        of the view's shorter side; irregularity 0..1 is how jagged the arms bend; taper 0..0.95 is
        root-to-tip width falloff; voidRadius is the impact crater fraction; tipGlowRadius is the leading
        spark size.

        Fields (all optional): name, crackColor, shadowColor, arms, depth, length, crackWidth,
        shadowWidth, irregularity, seed, taper, voidColor, voidRadius, tipGlowColor, tipGlowRadius,
        lightAngleDeg.

        Schema: {"name":"<short name>", ...fields...}
        Example: {"name":"ice","crackColor":"#E3F2FD","shadowColor":"#1565C0","arms":12,"depth":2,"length":0.35,"irregularity":0.7,"taper":0.85,"voidRadius":0.02,"tipGlowColor":"#FFFFFF"}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /** Wide rough earthquake cracks in the ground — the classic impact fracture. */
    val GROUND = OGCrackSpec(
        name = "ground", crackColor = "#D7CCC8", shadowColor = "#1C110A", voidColor = "#0A0604",
        arms = 7, depth = 1, length = 0.42f, crackWidth = 0.014f, shadowWidth = 0.034f,
        irregularity = 0.55f, taper = 0.82f, voidRadius = 0.045f,
        tipGlowColor = "#FFCC80", tipGlowRadius = 0.014f,
    )

    /** Thin blue-white spider-web — many fine branching arms, like cracking lake ice. */
    val ICE = OGCrackSpec(
        name = "ice", crackColor = "#E1F5FE", shadowColor = "#0D47A1", voidColor = "#011A38",
        arms = 13, depth = 2, length = 0.38f, crackWidth = 0.007f, shadowWidth = 0.018f,
        irregularity = 0.72f, taper = 0.85f, voidRadius = 0.020f,
        tipGlowColor = "#FFFFFF", tipGlowRadius = 0.012f,
    )

    /** Hairline cracks on a rigid tile — few arms, short, barely bending. */
    val TILE = OGCrackSpec(
        name = "tile", crackColor = "#757575", shadowColor = "#1A1A1A", voidColor = "#000000",
        arms = 5, depth = 1, length = 0.30f, crackWidth = 0.008f, shadowWidth = 0.020f,
        irregularity = 0.30f, taper = 0.65f, voidRadius = 0.025f, tipGlowRadius = 0.008f,
    )

    /** Fine radial glass fracture — very thin, very angular, densely webbed. */
    val GLASS = OGCrackSpec(
        name = "glass", crackColor = "#FFFFFF", shadowColor = "#37474F", voidColor = "#102027",
        arms = 10, depth = 2, length = 0.35f, crackWidth = 0.005f, shadowWidth = 0.012f,
        irregularity = 0.85f, taper = 0.90f, voidRadius = 0.010f, voidDepth = 0.5f,
        tipGlowColor = "#FFFFFF", tipGlowRadius = 0.015f,
    )

    /** A devastating, deeply cinematic tectonic quake hole with massive chasm depth and wide arms. */
    val QUAKE = OGCrackSpec(
        name = "quake", crackColor = "#FFE0B2", shadowColor = "#3E2723", voidColor = "#000000",
        arms = 12, depth = 3, length = 0.60f, crackWidth = 0.024f, shadowWidth = 0.050f,
        irregularity = 0.80f, taper = 0.70f, voidRadius = 0.080f, voidDepth = 2.8f,
        tipGlowColor = "#FF9800", tipGlowRadius = 0.025f, lightAngleDeg = 280f,
    )

    /** All built-in presets, keyed by lowercase name. */
    val presets: Map<String, OGCrackSpec> = listOf(GROUND, ICE, TILE, GLASS, QUAKE).associateBy { it.name!! }
}
