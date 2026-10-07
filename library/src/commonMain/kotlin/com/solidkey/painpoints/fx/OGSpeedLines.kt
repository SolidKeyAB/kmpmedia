package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec + presets** for [OGSpeedLinesSpec] — decode a JSON pack, encode one back, build a model
 * prompt, and reach for a ready-made set ([IMPACT], [FOCUS], [MOTION]). Mirrors the rest of the FX /
 * data layers: lenient JSON, tolerant extraction, no network, no AI SDK.
 */
object OGSpeedLines {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGSpeedLinesSpec =
        json.decodeFromString(OGSpeedLinesSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGSpeedLinesSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGSpeedLinesSpec): String = json.encodeToString(OGSpeedLinesSpec.serializer(), spec)

    fun preset(name: String): OGSpeedLinesSpec? = presets[name.lowercase()]

    fun speedLinesPrompt(instruction: String): String = """
        You output ONLY a JSON object describing manga-style speed / impact lines for the KMPMedia library.

        Coordinates: cx/cy (focus) are 0..1; radii are fractions of the half-diagonal; thickness is a
        fraction of the shorter side; angleDeg is for linear mode (0 = right).

        Fields (all optional): name, mode ("radial"|"linear"), cx, cy, count, color, innerRadius,
        outerRadius, thickness, lengthJitter, thicknessJitter, angleDeg, speed, pulse, alpha, seed.

        Schema: {"name":"<short name>", ...fields...}
        Example: {"name":"impact","mode":"radial","cx":0.5,"cy":0.5,"count":60,"innerRadius":0.3,"color":"#111111"}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /** A dense black impact burst converging on the centre — the classic "hit" framing. */
    val IMPACT = OGSpeedLinesSpec(
        name = "impact", mode = "radial", cx = 0.5f, cy = 0.5f, count = 64,
        color = "#111111", innerRadius = 0.26f, outerRadius = 1.1f, thickness = 0.014f,
        lengthJitter = 0.5f, thicknessJitter = 0.6f, speed = 2f, pulse = 0.3f, alpha = 0.92f, seed = 0,
    )

    /** A calmer white focus burst (good over a dark scene, leaving the subject clear). */
    val FOCUS = OGSpeedLinesSpec(
        name = "focus", mode = "radial", cx = 0.5f, cy = 0.45f, count = 40,
        color = "#FFFFFF", innerRadius = 0.34f, outerRadius = 1.05f, thickness = 0.01f,
        lengthJitter = 0.6f, thicknessJitter = 0.5f, speed = 1f, pulse = 0.2f, alpha = 0.8f, seed = 3,
    )

    /** Horizontal motion streaks — the god-speed dash. */
    val MOTION = OGSpeedLinesSpec(
        name = "motion", mode = "linear", angleDeg = 0f, count = 36,
        color = "#222222", innerRadius = 0f, outerRadius = 1f, thickness = 0.008f,
        lengthJitter = 0.7f, thicknessJitter = 0.6f, speed = 3f, pulse = 0.35f, alpha = 0.85f, seed = 7,
    )

    val presets: Map<String, OGSpeedLinesSpec> = listOf(IMPACT, FOCUS, MOTION).associateBy { it.name!! }
}
