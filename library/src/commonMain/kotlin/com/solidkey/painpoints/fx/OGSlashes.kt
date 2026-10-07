package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec + preset library** for [OGSlashSpec] — decode a JSON slash pack (designer- or
 * model-authored) into a live spec, encode one back, build a model prompt, and reach for a ready-made
 * signature form ([WATER], [FLAME], [THUNDER]). Mirrors
 * [com.solidkey.painpoints.particle.OGParticles] and [OGAiVector]: reuses the same lenient JSON config
 * and tolerant extraction (code fences / stray prose survive), with no network and no AI SDK.
 *
 * The three presets are one primitive wearing three elements: a **water** sweep (rippling blue), a
 * **flame** lick (turbulent orange), a **thunder** bolt (jagged gold). Drop any on screen with
 * [OGSlashView]; pair it with an [com.solidkey.painpoints.particle.OGParticleView] (droplets / embers)
 * for the full look.
 */
object OGSlashes {
    /** The shared lenient JSON config (reused from [OGAiVector] so every data layer agrees). */
    val json get() = OGAiVector.json

    /** Decode a JSON slash pack into its [OGSlashSpec]. Throws on malformed input. */
    fun decodeSpec(text: String): OGSlashSpec =
        json.decodeFromString(OGSlashSpec.serializer(), OGAiVector.extractJson(text))

    /** [decodeSpec] but returns `null` instead of throwing on malformed input. */
    fun decodeSpecOrNull(text: String): OGSlashSpec? = runCatching { decodeSpec(text) }.getOrNull()

    /** Serialize a slash pack to JSON (compact; defaults omitted). */
    fun encode(spec: OGSlashSpec): String = json.encodeToString(OGSlashSpec.serializer(), spec)

    /** Look up a built-in preset by name (case-insensitive), or `null`. */
    fun preset(name: String): OGSlashSpec? = presets[name.lowercase()]

    /**
     * Build a ready-to-send instruction constraining a model to emit exactly the slash-pack JSON
     * [decodeSpec] expects. Wire the returned string into your model; pass the reply to [decodeSpec].
     */
    fun slashPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a "breathing slash" ribbon effect for the KMPMedia
        library — a glowing tapered arc that draws on along a path (think an anime sword slash).

        Coordinates: the path is a list of {x,y} points in 0..1 over the view box, head -> tail; width
        and edgeAmp are fractions of the shorter side; times are in ms.

        Fields (all optional, sensible defaults):
          name, path (>=2 points), width, taper, peak, smoothing, samples,
          colorStart, colorEnd, glow (0..1), glowColor,
          edge ("smooth"|"wave"|"rough"|"bolt"), edgeAmp, edgeSpeed, edgeDetail,
          breatheAmp, breatheSpeed, revealMs, holdMs, fadeMs, loop, seed.

        Pick the edge for the element: "wave" = water ripple, "rough" = flame lick, "bolt" = thunder jag.

        Schema: {"name":"<short name>","path":[{"x":..,"y":..},...], ...fields...}
        Examples:
        {"name":"water","path":[{"x":0.1,"y":0.34},{"x":0.34,"y":0.2},{"x":0.6,"y":0.34},{"x":0.88,"y":0.66}],"edge":"wave","colorStart":"#4FC3F7","colorEnd":"#01579B","glow":0.8}
        {"name":"thunder","edge":"bolt","width":0.05,"edgeAmp":0.05,"edgeSpeed":6,"colorStart":"#FFF9C4","colorEnd":"#FFD600","glowColor":"#FFFFFF","revealMs":160}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    // ── Presets ──────────────────────────────────────────────────────────────────────────────────

    /** Water Breathing — a rippling blue sweep that bulges like a wave. Pair with droplet particles. */
    val WATER = OGSlashSpec(
        name = "water",
        width = 0.12f, taper = 0.9f, peak = 0.42f, smoothing = 1f, samples = 80,
        colorStart = "#4FC3F7", colorEnd = "#01579B", glow = 0.8f, glowColor = "#B3E5FC",
        edge = "wave", edgeAmp = 0.022f, edgeSpeed = 1.2f, edgeDetail = 4,
        breatheAmp = 0.14f, breatheSpeed = 0.9f,
        revealMs = 420f, holdMs = 900f, fadeMs = 380f, loop = true, seed = 0,
    )

    /** Flame — a turbulent orange lick with a licking, noisy edge. Pair with ember particles. */
    val FLAME = OGSlashSpec(
        name = "flame",
        width = 0.13f, taper = 1.1f, peak = 0.38f, smoothing = 1f, samples = 84,
        colorStart = "#FFEE58", colorEnd = "#E65100", glow = 0.9f, glowColor = "#FFE082",
        edge = "rough", edgeAmp = 0.03f, edgeSpeed = 7f, edgeDetail = 8,
        breatheAmp = 0.2f, breatheSpeed = 1.3f,
        revealMs = 360f, holdMs = 800f, fadeMs = 420f, loop = true, seed = 7,
    )

    /** Thunderclap — a thin, jagged gold bolt that flickers and strikes fast. */
    val THUNDER = OGSlashSpec(
        name = "thunder",
        width = 0.05f, taper = 0.7f, peak = 0.5f, smoothing = 0.6f, samples = 72,
        colorStart = "#FFF9C4", colorEnd = "#FFD600", glow = 1f, glowColor = "#FFFFFF",
        edge = "bolt", edgeAmp = 0.05f, edgeSpeed = 8f, edgeDetail = 10,
        breatheAmp = 0.05f, breatheSpeed = 2f,
        revealMs = 150f, holdMs = 500f, fadeMs = 260f, loop = true, seed = 13,
    )

    /** All built-in presets, keyed by lowercase name. */
    val presets: Map<String, OGSlashSpec> = listOf(WATER, FLAME, THUNDER).associateBy { it.name!! }
}
