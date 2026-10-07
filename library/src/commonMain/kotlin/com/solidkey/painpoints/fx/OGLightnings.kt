package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec + presets** for [OGLightningSpec] — decode a JSON bolt pack, encode one back, build a
 * model prompt, and reach for a ready-made bolt ([BOLT], [STORM], [SPARK]). Mirrors [OGSlashes] /
 * [com.solidkey.painpoints.particle.OGParticles]: the same lenient JSON config and tolerant extraction,
 * no network, no AI SDK.
 */
object OGLightnings {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGLightningSpec =
        json.decodeFromString(OGLightningSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGLightningSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGLightningSpec): String = json.encodeToString(OGLightningSpec.serializer(), spec)

    fun preset(name: String): OGLightningSpec? = presets[name.lowercase()]

    fun lightningPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a forked lightning bolt for the KMPMedia library.

        Coordinates: x1/y1 (origin) and x2/y2 (target) are 0..1 over the view box; width and jaggedness
        are fractions of the shorter side; times are ms.

        Fields (all optional): name, x1, y1, x2, y2, width, jaggedness, detail (midpoint-displacement
        depth), branches, branchLength, color, coreColor, glow (0..1), glowColor,
        strikeMs, decayMs, gapMs, loop, seed.

        Schema: {"name":"<short name>", ...fields...}
        Example: {"name":"bolt","x1":0.5,"y1":0.02,"x2":0.45,"y2":0.98,"branches":5,"color":"#B3E5FC","glow":1}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /** A classic sky-to-ground strike, pale blue with a white core. */
    val BOLT = OGLightningSpec(
        name = "bolt", x1 = 0.5f, y1 = 0.02f, x2 = 0.46f, y2 = 0.98f,
        width = 0.014f, jaggedness = 0.16f, detail = 6, branches = 5, branchLength = 0.4f,
        color = "#B3E5FC", coreColor = "#FFFFFF", glow = 1f,
        strikeMs = 90f, decayMs = 240f, gapMs = 420f, seed = 0,
    )

    /** A fiercer, more-branched violet storm bolt that strikes faster. */
    val STORM = OGLightningSpec(
        name = "storm", x1 = 0.5f, y1 = 0.02f, x2 = 0.5f, y2 = 0.98f,
        width = 0.016f, jaggedness = 0.2f, detail = 7, branches = 9, branchLength = 0.5f,
        color = "#E1BEE7", coreColor = "#FFFFFF", glowColor = "#CE93D8", glow = 1f,
        strikeMs = 70f, decayMs = 180f, gapMs = 260f, seed = 5,
    )

    /** A short, sharp golden spark arcing across — good as a horizontal zap. */
    val SPARK = OGLightningSpec(
        name = "spark", x1 = 0.08f, y1 = 0.5f, x2 = 0.92f, y2 = 0.46f,
        width = 0.01f, jaggedness = 0.1f, detail = 5, branches = 3, branchLength = 0.3f,
        color = "#FFF59D", coreColor = "#FFFFFF", glowColor = "#FFE082", glow = 1f,
        strikeMs = 60f, decayMs = 150f, gapMs = 300f, seed = 9,
    )

    val presets: Map<String, OGLightningSpec> = listOf(BOLT, STORM, SPARK).associateBy { it.name!! }
}
