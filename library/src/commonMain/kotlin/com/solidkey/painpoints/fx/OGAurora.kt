package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector

/**
 * Codec + presets for [OGAuroraSpec].
 *
 * Presets:
 * - [NORTHERN] — classic Arctic green/cyan/violet, slow drift
 * - [POLAR]    — cold blue-white polar glow, minimal shear
 * - [DREAMY]   — warm pink/gold/purple Shinkai-style, fast and wide
 */
object OGAurora {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGAuroraSpec =
        json.decodeFromString(OGAuroraSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGAuroraSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGAuroraSpec): String = json.encodeToString(OGAuroraSpec.serializer(), spec)

    fun preset(name: String): OGAuroraSpec? = presets[name.lowercase()]

    fun auroraPrompt(instruction: String): String = """
        You output ONLY a JSON object describing an aurora borealis / australis curtain for the KMPMedia library.

        It renders as luminous multi-harmonic vertical curtain rays with altitude-stratified colour
        (bottomColor = low-altitude, typically green; topColor = high-altitude, typically violet/pink).
        Coordinates: curtainY and curtainHeight are fractions of view height; amplitude and shear are
        fractions of view width. Speed and shearSpeed are in scene units per second.

        Fields (all optional): name, columns, bottomColor, midColor, topColor, harmonics, frequency,
        amplitude, shear, shearSpeed, columnWidthFactor, curtainY, curtainHeight, speed, opacity, seed.

        Examples:
          {"name":"northern","bottomColor":"#00E676","midColor":"#00BCD4","topColor":"#CE93D8","columns":7,"speed":0.22}
          {"name":"polar","bottomColor":"#80D8FF","midColor":"#FFFFFF","topColor":"#8C9EFF","columns":5,"opacity":0.55}
          {"name":"dreamy","bottomColor":"#FFD54F","midColor":"#F48FB1","topColor":"#CE93D8","columns":9,"speed":0.45,"shear":0.28}

        $instruction
        Output ONLY the JSON object.
    """.trimIndent()

    // ── Presets ──────────────────────────────────────────────────────────────────────────────────

    /** Classic Arctic green/cyan/violet — *Your Name*, *Frieren*. */
    val NORTHERN = OGAuroraSpec(
        name = "northern",
        columns = 7, harmonics = 3, frequency = 2.6f, amplitude = 0.06f,
        bottomColor = "#00E676", midColor = "#00BCD4", topColor = "#CE93D8",
        shear = 0.16f, shearSpeed = 0.18f, columnWidthFactor = 1.55f,
        curtainY = 0.06f, curtainHeight = 0.74f, speed = 0.22f, opacity = 0.72f,
    )

    /** Cold blue-white polar — *Mushishi*, minimalist glow. */
    val POLAR = OGAuroraSpec(
        name = "polar",
        columns = 5, harmonics = 2, frequency = 1.8f, amplitude = 0.05f,
        bottomColor = "#80D8FF", midColor = "#E1F5FE", topColor = "#8C9EFF",
        shear = 0.08f, shearSpeed = 0.10f, columnWidthFactor = 2.0f,
        curtainY = 0.10f, curtainHeight = 0.68f, speed = 0.12f, opacity = 0.55f,
    )

    /** Warm pink/gold/purple — fast, wide, Shinkai-style fantasy aurora. */
    val DREAMY = OGAuroraSpec(
        name = "dreamy",
        columns = 9, harmonics = 4, frequency = 3.2f, amplitude = 0.09f,
        bottomColor = "#FFD54F", midColor = "#F48FB1", topColor = "#CE93D8",
        shear = 0.28f, shearSpeed = 0.32f, columnWidthFactor = 1.40f,
        curtainY = 0.04f, curtainHeight = 0.78f, speed = 0.45f, opacity = 0.68f,
    )

    val presets: Map<String, OGAuroraSpec> = listOf(NORTHERN, POLAR, DREAMY).associateBy { it.name!! }
}
