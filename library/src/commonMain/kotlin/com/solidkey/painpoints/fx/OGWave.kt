package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec + presets** for [OGWaveSpec] — decode a JSON pack, encode one back, build a model
 * prompt, and reach for a ready-made surface ([CALM], [OCEAN], [FLOOD], [LAVA]). Mirrors the rest of
 * the FX / data layers: lenient JSON, tolerant extraction, no network, no AI SDK.
 */
object OGWave {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGWaveSpec =
        json.decodeFromString(OGWaveSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGWaveSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGWaveSpec): String = json.encodeToString(OGWaveSpec.serializer(), spec)

    /** Look up a built-in preset by name (case-insensitive), or `null`. */
    fun preset(name: String): OGWaveSpec? = presets[name.lowercase()]

    fun wavePrompt(instruction: String): String = """
        You output ONLY a JSON object describing a scrolling water surface for the KMPMedia library.

        It renders as overlapping wave bands (travelling Gerstner waves — sharp crests, broad troughs —
        + fractal noise) with an optional white foam strip at the crests. Coordinates: surfaceY is 0..1
        of the view (0 = top); amplitude is a fraction of the view height; speed is a fraction of the
        view per second (+ = right); steepness 0..1 controls crest sharpness (0.65 = classic ocean);
        opacity is 0..1; a slow dark-red/orange pair reads as lava.

        Fields (all optional): name, deepColor, shallowColor, layers, amplitude, frequency, speed,
        turbulence, foam, foamThickness, opacity, surfaceY, octaves, steepness, seed.

        Schema: {"name":"<short name>", ...fields...}
        Example: {"name":"ocean","deepColor":"#0D47A1","shallowColor":"#29B6F6","layers":4,"amplitude":0.1,"frequency":3,"speed":0.3,"turbulence":0.55,"foam":true}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /** A quiet lake — two slow, low bands with no foam. */
    val CALM = OGWaveSpec(
        name = "calm", deepColor = "#1565C0", shallowColor = "#4FC3F7", layers = 2,
        amplitude = 0.05f, frequency = 2f, speed = 0.15f, turbulence = 0.3f,
        foam = false, surfaceY = 0.55f, seed = 7,
    )

    /** Choppy open ocean — four bands, taller and faster, with foam crests. */
    val OCEAN = OGWaveSpec(
        name = "ocean", deepColor = "#0D47A1", shallowColor = "#29B6F6", layers = 4,
        amplitude = 0.10f, frequency = 3f, speed = 0.30f, turbulence = 0.55f,
        foam = true, surfaceY = 0.45f, seed = 17,
    )

    /** Rising flood water — a high surface racing sideways, turbulent, foaming. */
    val FLOOD = OGWaveSpec(
        name = "flood", deepColor = "#1A237E", shallowColor = "#42A5F5", layers = 3,
        amplitude = 0.07f, frequency = 2.5f, speed = 0.55f, turbulence = 0.65f,
        foam = true, surfaceY = 0.35f, seed = 29,
    )

    /** Slow molten lava — low, viscous red-orange swells, very turbulent, no foam. */
    val LAVA = OGWaveSpec(
        name = "lava", deepColor = "#B71C1C", shallowColor = "#FF8F00", layers = 2,
        amplitude = 0.06f, frequency = 1.5f, speed = 0.08f, turbulence = 0.80f,
        foam = false, surfaceY = 0.60f, seed = 41,
    )

    /** All built-in presets, keyed by lowercase name. */
    val presets: Map<String, OGWaveSpec> = listOf(CALM, OCEAN, FLOOD, LAVA).associateBy { it.name!! }
}
