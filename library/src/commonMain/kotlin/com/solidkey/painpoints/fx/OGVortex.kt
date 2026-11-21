package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector

/**
 * Codec + presets for [OGVortexSpec] — decode JSON, encode, build a model prompt, and reach for a
 * ready-made vortex ([TORNADO], [CYCLONE], [DUST_DEVIL], [FIRE_VORTEX], [WHIRLPOOL], [PORTAL],
 * [MAELSTROM], [NEBULA]).
 */
object OGVortex {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGVortexSpec =
        json.decodeFromString(OGVortexSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGVortexSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGVortexSpec): String = json.encodeToString(OGVortexSpec.serializer(), spec)

    fun preset(name: String): OGVortexSpec? = presets[name.lowercase()]

    fun vortexPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a top-down spinning vortex for the KMPMedia library.

        It renders as a dark eye at centre, concentric spinning dust rings (Rankine differential rotation,
        inner rings fastest), optional spiral arms, orbiting particles, and optional funnel depth rings.
        All colours are SVG hex strings.
        angularVelocity = rotations per second for the innermost ring; outer rings scale down automatically.

        Fields (all optional): name, eyeRadiusFraction, rings, arms, armSweepDeg, angularVelocity,
        color, eyeColor, opacity, particles (0–250 orbiting debris dots), streakTailDeg (0–40, motion trail
        behind each dust streak), depthLayers (1–4, funnel depth rings inside the eye).

        Schema: {"name":"<short name>", ...fields...}
        Example: {"name":"eye_of_storm","color":"#B8D4FF","eyeColor":"#020510","rings":5,"arms":4,"angularVelocity":0.9,"particles":140,"streakTailDeg":28,"depthLayers":3}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /** Classic purple-storm tornado — 3 arms, 3 bands, vivid. */
    val TORNADO = OGVortexSpec(
        name = "tornado", color = "#CBB7F4", eyeColor = "#0E0520",
        eyeRadiusFraction = 0.14f, rings = 3, arms = 3, armSweepDeg = 90f,
        angularVelocity = 1.5f, opacity = 0.85f,
    )

    /** Wide slow cyclone — 4 arms, 4 rings, blue-white weather-map look. */
    val CYCLONE = OGVortexSpec(
        name = "cyclone", color = "#A8C8F4", eyeColor = "#05101E",
        eyeRadiusFraction = 0.18f, rings = 4, arms = 4, armSweepDeg = 80f,
        angularVelocity = 0.6f, opacity = 0.78f,
    )

    /** Small sandy desert whirlwind — fast, tight, no spiral arms. */
    val DUST_DEVIL = OGVortexSpec(
        name = "dust_devil", color = "#D4A96A", eyeColor = "#2A1A05",
        eyeRadiusFraction = 0.10f, rings = 2, arms = 0, armSweepDeg = 60f,
        angularVelocity = 2.8f, opacity = 0.70f,
    )

    /** Fire vortex — ember-red, 2 arms, medium speed. */
    val FIRE_VORTEX = OGVortexSpec(
        name = "fire_vortex", color = "#FF8C42", eyeColor = "#1A0500",
        eyeRadiusFraction = 0.12f, rings = 3, arms = 2, armSweepDeg = 100f,
        angularVelocity = 2.0f, opacity = 0.80f,
    )

    /** Ocean whirlpool — teal-green, wide eye, 2 broad arms, leisurely spin. */
    val WHIRLPOOL = OGVortexSpec(
        name = "whirlpool", color = "#5EC4B6", eyeColor = "#031A18",
        eyeRadiusFraction = 0.20f, rings = 3, arms = 2, armSweepDeg = 110f,
        angularVelocity = 0.8f, opacity = 0.82f,
    )

    /** Sci-fi energy portal — cyan-violet, tight eye, no arms, 4 dense rings, fast inner pulse. */
    val PORTAL = OGVortexSpec(
        name = "portal", color = "#7DFFD1", eyeColor = "#0A0020",
        eyeRadiusFraction = 0.08f, rings = 4, arms = 0, armSweepDeg = 60f,
        angularVelocity = 2.2f, opacity = 0.90f,
    )

    /** Violent ocean maelstrom — deep navy, large eye, 5 rings, 3 arms, aggressive speed. */
    val MAELSTROM = OGVortexSpec(
        name = "maelstrom", color = "#2E6EA6", eyeColor = "#020810",
        eyeRadiusFraction = 0.22f, rings = 5, arms = 3, armSweepDeg = 95f,
        angularVelocity = 1.8f, opacity = 0.88f,
    )

    /** Cosmic nebula — blue-indigo, very slow drift, 6 rings, no arms, dreamlike. */
    val NEBULA = OGVortexSpec(
        name = "nebula", color = "#7260C8", eyeColor = "#030515",
        eyeRadiusFraction = 0.16f, rings = 6, arms = 0, armSweepDeg = 120f,
        angularVelocity = 0.15f, opacity = 0.75f,
    )

    /** Looking straight down into the eye — deep funnel layers, motion trails, 140 debris particles. */
    val EYE_OF_STORM = OGVortexSpec(
        name = "eye_of_storm", color = "#B8D4FF", eyeColor = "#020510",
        eyeRadiusFraction = 0.22f, rings = 5, arms = 4, armSweepDeg = 82f,
        angularVelocity = 0.9f, opacity = 0.92f,
        particles = 140, streakTailDeg = 28f, depthLayers = 3,
    )

    val presets: Map<String, OGVortexSpec> =
        listOf(TORNADO, CYCLONE, DUST_DEVIL, FIRE_VORTEX, WHIRLPOOL, PORTAL, MAELSTROM, NEBULA, EYE_OF_STORM)
            .associateBy { it.name!! }
}
