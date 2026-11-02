package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector

/**
 * Codec + presets for [OGFireflySpec].
 *
 * Presets:
 * - [MEADOW]  — large open swarm, moderate coupling → waves of synchronised pulses
 * - [FOREST]  — smaller, dimmer, well-coupled → tight collective flashing
 * - [GHIBLI]  — wide spread, low coupling → independent glimmers with occasional cluster locks
 */
object OGFirefly {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGFireflySpec =
        json.decodeFromString(OGFireflySpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGFireflySpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGFireflySpec): String = json.encodeToString(OGFireflySpec.serializer(), spec)

    fun preset(name: String): OGFireflySpec? = presets[name.lowercase()]

    fun fireflyPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a firefly swarm using the Kuramoto coupled-oscillator
        model for the KMPMedia library.

        Each firefly has a natural blink frequency (omegaMean ± omegaSpread rad/s) and a coupling
        constant K. When K > K_c ≈ 2/π·omegaSpread, clusters synchronise and pulse together.
        radius and glowRadius are fractions of the shorter view dimension; driftSpeed is fraction/sec.

        Fields (all optional): name, count, coupling, omegaMean, omegaSpread, driftSpeed, turbulence,
        radius, color, glowColor, glowRadius, blinkPower, opacity, seed.

        Examples:
          {"name":"meadow","count":52,"coupling":2.8,"omegaMean":1.6,"color":"#CCFF90"}
          {"name":"forest","count":28,"coupling":4.5,"omegaMean":2.2,"color":"#B9F6CA","glowColor":"#E8F5E9","opacity":0.85}
          {"name":"ghibli","count":80,"coupling":1.2,"omegaSpread":1.4,"driftSpeed":0.02,"color":"#F9FBE7"}

        $instruction
        Output ONLY the JSON object.
    """.trimIndent()

    // ── Presets ──────────────────────────────────────────────────────────────────────────────────

    /** Warm summer meadow — moderate coupling, visible synchronisation waves. */
    val MEADOW = OGFireflySpec(
        name = "meadow",
        count = 52, coupling = 2.8f, omegaMean = 1.6f, omegaSpread = 0.7f,
        driftSpeed = 0.030f, turbulence = 0.55f,
        radius = 0.008f, color = "#CCFF90", glowColor = "#F1F8E9", glowRadius = 0.038f,
        blinkPower = 4f, opacity = 1f,
    )

    /** Dense forest — tight coupling, collective flash. */
    val FOREST = OGFireflySpec(
        name = "forest",
        count = 30, coupling = 4.5f, omegaMean = 2.2f, omegaSpread = 0.5f,
        driftSpeed = 0.018f, turbulence = 0.40f,
        radius = 0.007f, color = "#B9F6CA", glowColor = "#E8F5E9", glowRadius = 0.030f,
        blinkPower = 5f, opacity = 0.85f,
    )

    /** Ghibli dreamscape — wide spread, loose coupling → independent glimmers. */
    val GHIBLI = OGFireflySpec(
        name = "ghibli",
        count = 80, coupling = 1.2f, omegaMean = 1.1f, omegaSpread = 1.4f,
        driftSpeed = 0.018f, turbulence = 0.70f,
        radius = 0.006f, color = "#F9FBE7", glowColor = "#FFFFFF", glowRadius = 0.028f,
        blinkPower = 3f, opacity = 0.78f,
    )

    val presets: Map<String, OGFireflySpec> = listOf(MEADOW, FOREST, GHIBLI).associateBy { it.name!! }
}
