package com.solidkey.painpoints.noir

import com.solidkey.painpoints.ai.OGAiVector

/**
 * Codec + presets for the noir effect trio: [OGGrainSpec], [OGBlindSpec], [OGSpotlightSpec].
 *
 * **Grain presets:** [KODAK], [SILVER], [HEAVY]
 * **Blind presets:** [WINDOW], [HARSH], [WIDE]
 * **Spotlight presets:** [INTERROGATION], [STAGE], [ALLEY]
 */
object OGNoir {
    val json get() = OGAiVector.json

    // ── Grain ────────────────────────────────────────────────────────────────

    fun decodeGrainSpec(text: String): OGGrainSpec =
        json.decodeFromString(OGGrainSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeGrainSpecOrNull(text: String): OGGrainSpec? =
        runCatching { decodeGrainSpec(text) }.getOrNull()

    fun encodeGrain(spec: OGGrainSpec): String =
        json.encodeToString(OGGrainSpec.serializer(), spec)

    // ── Blind ────────────────────────────────────────────────────────────────

    fun decodeBlindSpec(text: String): OGBlindSpec =
        json.decodeFromString(OGBlindSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeBlindSpecOrNull(text: String): OGBlindSpec? =
        runCatching { decodeBlindSpec(text) }.getOrNull()

    fun encodeBlinds(spec: OGBlindSpec): String =
        json.encodeToString(OGBlindSpec.serializer(), spec)

    // ── Spotlight ────────────────────────────────────────────────────────────

    fun decodeSpotlightSpec(text: String): OGSpotlightSpec =
        json.decodeFromString(OGSpotlightSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpotlightSpecOrNull(text: String): OGSpotlightSpec? =
        runCatching { decodeSpotlightSpec(text) }.getOrNull()

    fun encodeSpotlight(spec: OGSpotlightSpec): String =
        json.encodeToString(OGSpotlightSpec.serializer(), spec)

    // ── Prompt ───────────────────────────────────────────────────────────────

    fun noirPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a noir effect for the KMPMedia library.
        Choose ONE of three effect types and output that type's schema.

        TYPE 1 — Film grain (OGGrainSpec):
        Fields (all optional): name, intensity (0–1), speed (1–120, 24=film-like), scale (0.3–5 px), color (hex).
        Example: {"name":"kodak","intensity":0.22,"speed":24,"scale":1.2,"color":"#FFF8F0"}

        TYPE 2 — Venetian blinds (OGBlindSpec):
        Fields (all optional): name, color (hex), opacity (0–1), angleDeg (-90–90), slats (1–20), gapRatio (0–0.9),
        oscillationDeg (0–45), oscillationSpeed (0.01–2).
        Example: {"name":"window","color":"#000000","opacity":0.65,"angleDeg":-38,"slats":7,"gapRatio":0.45,"oscillationDeg":8,"oscillationSpeed":0.15}

        TYPE 3 — Spotlight (OGSpotlightSpec):
        Fields (all optional): name, scrimColor (hex), scrimOpacity (0–1), centerX (0–1), centerY (0–1),
        radius (0.05–1), softness (0–1), swingAmp (0–0.4), swingSpeed (0.01–2).
        Example: {"name":"interrogation","scrimColor":"#000000","scrimOpacity":0.82,"centerX":0.5,"centerY":0.4,"radius":0.28,"softness":0.22,"swingAmp":0.08,"swingSpeed":0.18}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    // ── Grain presets ────────────────────────────────────────────────────────

    /** Warm 35mm Kodak-style grain — subtle, slightly warm, film-like chatter. */
    val KODAK = OGGrainSpec(
        name = "kodak", intensity = 0.22f, speed = 24f, scale = 1.2f, color = "#FFF8F0",
    )

    /** Cool fine-grain silver gelatin — high contrast B&W photography look. */
    val SILVER = OGGrainSpec(
        name = "silver", intensity = 0.16f, speed = 18f, scale = 0.9f, color = "#F0F0F0",
    )

    /** Heavy push-processed grain — gritty, coarse, high intensity. */
    val HEAVY = OGGrainSpec(
        name = "heavy", intensity = 0.42f, speed = 24f, scale = 2.2f, color = "#FFFFFF",
    )

    // ── Blind presets ────────────────────────────────────────────────────────

    /** Classic noir diagonal window — 7 slats, gentle sway. */
    val WINDOW = OGBlindSpec(
        name = "window", color = "#000000", opacity = 0.65f, angleDeg = -38f,
        slats = 7, gapRatio = 0.45f, oscillationDeg = 8f, oscillationSpeed = 0.15f,
    )

    /** Harsh midday blinds — high contrast, tight gaps, no motion. */
    val HARSH = OGBlindSpec(
        name = "harsh", color = "#000000", opacity = 0.80f, angleDeg = -50f,
        slats = 9, gapRatio = 0.30f, oscillationDeg = 0f, oscillationSpeed = 0.15f,
    )

    /** Wide moody bands — few broad stripes, slow swing. */
    val WIDE = OGBlindSpec(
        name = "wide", color = "#0A0510", opacity = 0.55f, angleDeg = -32f,
        slats = 4, gapRatio = 0.60f, oscillationDeg = 12f, oscillationSpeed = 0.10f,
    )

    // ── Spotlight presets ────────────────────────────────────────────────────

    /** Police interrogation room — small hard light slightly above centre, slow pan. */
    val INTERROGATION = OGSpotlightSpec(
        name = "interrogation", scrimColor = "#000000", scrimOpacity = 0.82f,
        centerX = 0.5f, centerY = 0.42f, radius = 0.28f, softness = 0.22f,
        swingAmp = 0.08f, swingSpeed = 0.18f,
    )

    /** Theatre stage spot — wider, softer, centred. */
    val STAGE = OGSpotlightSpec(
        name = "stage", scrimColor = "#000000", scrimOpacity = 0.68f,
        centerX = 0.5f, centerY = 0.5f, radius = 0.42f, softness = 0.45f,
        swingAmp = 0f, swingSpeed = 0.2f,
    )

    /** Dark alley streetlight — off-centre upper corner, tight and hard. */
    val ALLEY = OGSpotlightSpec(
        name = "alley", scrimColor = "#050205", scrimOpacity = 0.88f,
        centerX = 0.72f, centerY = 0.22f, radius = 0.22f, softness = 0.18f,
        swingAmp = 0f, swingSpeed = 0.2f,
    )

    val grainPresets: Map<String, OGGrainSpec> =
        listOf(KODAK, SILVER, HEAVY).associateBy { it.name!! }

    val blindPresets: Map<String, OGBlindSpec> =
        listOf(WINDOW, HARSH, WIDE).associateBy { it.name!! }

    val spotlightPresets: Map<String, OGSpotlightSpec> =
        listOf(INTERROGATION, STAGE, ALLEY).associateBy { it.name!! }
}
