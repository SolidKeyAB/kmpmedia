package com.solidkey.painpoints.lens

import com.solidkey.painpoints.ai.OGAiVector

/**
 * Codec + presets for the retro lens effect family: [OGVignetteSpec], [OGChromaSpec],
 * [OGLightLeakSpec], [OGAnamorphicSpec], [OGLensFlareSpec].
 *
 * **Vignette:** [SUBTLE_VIG], [MOODY_VIG], [LOMO_VIG]
 * **Chroma:** [CHROMA_RETRO], [CHROMA_GLITCH]
 * **Light leak:** [LEAK_WARM], [LEAK_COOL]
 * **Anamorphic:** [ANAMORPHIC_BLUE], [ANAMORPHIC_AMBER]
 * **Lens flare:** [FLARE_CINEMA], [FLARE_SUN], [FLARE_SUBTLE]
 */
object OGLens {
    val json get() = OGAiVector.json

    // ── Vignette codec ───────────────────────────────────────────────────────

    fun decodeVignetteSpec(text: String): OGVignetteSpec =
        json.decodeFromString(OGVignetteSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeVignetteSpecOrNull(text: String): OGVignetteSpec? =
        runCatching { decodeVignetteSpec(text) }.getOrNull()

    fun encodeVignette(spec: OGVignetteSpec): String =
        json.encodeToString(OGVignetteSpec.serializer(), spec)

    // ── Chroma codec ─────────────────────────────────────────────────────────

    fun decodeChromaSpec(text: String): OGChromaSpec =
        json.decodeFromString(OGChromaSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeChromaSpecOrNull(text: String): OGChromaSpec? =
        runCatching { decodeChromaSpec(text) }.getOrNull()

    fun encodeChroma(spec: OGChromaSpec): String =
        json.encodeToString(OGChromaSpec.serializer(), spec)

    // ── Light leak codec ─────────────────────────────────────────────────────

    fun decodeLightLeakSpec(text: String): OGLightLeakSpec =
        json.decodeFromString(OGLightLeakSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeLightLeakSpecOrNull(text: String): OGLightLeakSpec? =
        runCatching { decodeLightLeakSpec(text) }.getOrNull()

    fun encodeLightLeak(spec: OGLightLeakSpec): String =
        json.encodeToString(OGLightLeakSpec.serializer(), spec)

    // ── Anamorphic codec ─────────────────────────────────────────────────────

    fun decodeAnamorphicSpec(text: String): OGAnamorphicSpec =
        json.decodeFromString(OGAnamorphicSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeAnamorphicSpecOrNull(text: String): OGAnamorphicSpec? =
        runCatching { decodeAnamorphicSpec(text) }.getOrNull()

    fun encodeAnamorphic(spec: OGAnamorphicSpec): String =
        json.encodeToString(OGAnamorphicSpec.serializer(), spec)

    // ── Lens flare codec ─────────────────────────────────────────────────────

    fun decodeLensFlareSpec(text: String): OGLensFlareSpec =
        json.decodeFromString(OGLensFlareSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeLensFlareSpecOrNull(text: String): OGLensFlareSpec? =
        runCatching { decodeLensFlareSpec(text) }.getOrNull()

    fun encodeLensFlare(spec: OGLensFlareSpec): String =
        json.encodeToString(OGLensFlareSpec.serializer(), spec)

    // ── Prompt ───────────────────────────────────────────────────────────────

    fun lensPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a retro lens effect for the KMPMedia library.
        Choose ONE of five effect types and output that type's schema.

        TYPE 1 — Vignette (OGVignetteSpec):
        Fields: name, intensity (0–1), radius (0–1.5, lit-centre fraction), softness (0–1), color (hex).
        Example: {"name":"moody","intensity":0.72,"radius":0.55,"softness":0.45,"color":"#000000"}

        TYPE 2 — Chromatic aberration (OGChromaSpec):
        Fields: name, shift (0–20 px), angle (degrees), pulse (0–1), pulseSpeed (0.1–10).
        Example: {"name":"retro","shift":4.0,"angle":0.0,"pulse":0.3,"pulseSpeed":1.5}

        TYPE 3 — Light leak (OGLightLeakSpec):
        Fields: name, color (hex), opacity (0–1), cornerX (0–1), cornerY (0–1), spread (0.1–1), pulse (0–1), pulseSpeed (0.1–5).
        Example: {"name":"warm","color":"#FF8040","opacity":0.35,"cornerX":0.0,"cornerY":0.0,"spread":0.65,"pulse":0.4,"pulseSpeed":0.5}

        TYPE 4 — Anamorphic streak (OGAnamorphicSpec):
        Fields: name, color (hex), opacity (0–1), width (0.5–10 px), centerY (0–1), spread (0.1–1), pulse (0–1), pulseSpeed (0.1–5).
        Example: {"name":"blue","color":"#80C8FF","opacity":0.65,"width":2.5,"centerY":0.5,"spread":0.9,"pulse":0.6,"pulseSpeed":1.5}

        TYPE 5 — Lens flare (OGLensFlareSpec):
        Fields: name, color (hex), tint (hex), opacity (0–1), centerX (0–1), centerY (0–1), scale (0.1–3),
        streakLength (0.05–1), streakCount (2–12), bokehCount (0–10), pulse (0–1), pulseSpeed (0.1–5).
        Example: {"name":"cinema","color":"#FFFFFF","tint":"#FFD080","opacity":0.7,"centerX":0.75,"centerY":0.25,"streakCount":6,"bokehCount":5}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    // ── Vignette presets ─────────────────────────────────────────────────────

    /** Gentle portrait vignette — subtle corner fade. */
    val SUBTLE_VIG = OGVignetteSpec(
        name = "subtle_vig", intensity = 0.45f, radius = 0.72f, softness = 0.68f,
    )

    /** Moody cinematic vignette — stronger, tighter. */
    val MOODY_VIG = OGVignetteSpec(
        name = "moody_vig", intensity = 0.72f, radius = 0.55f, softness = 0.45f,
    )

    /** Lomo heavy crush — very aggressive dark corners. */
    val LOMO_VIG = OGVignetteSpec(
        name = "lomo_vig", intensity = 0.90f, radius = 0.38f, softness = 0.22f,
    )

    // ── Chroma presets ───────────────────────────────────────────────────────

    /** Classic retro fringing — 3 px shift, subtle horizontal pulse. */
    val CHROMA_RETRO = OGChromaSpec(
        name = "chroma_retro", shift = 3f, angle = 0f, pulse = 0.30f, pulseSpeed = 1.5f,
    )

    /** Aggressive glitch-style separation — 8 px, rapid pulse. */
    val CHROMA_GLITCH = OGChromaSpec(
        name = "chroma_glitch", shift = 8f, angle = 0f, pulse = 0.80f, pulseSpeed = 4f,
    )

    // ── Light leak presets ───────────────────────────────────────────────────

    /** Warm orange leak from the top-left corner. */
    val LEAK_WARM = OGLightLeakSpec(
        name = "leak_warm", color = "#FF8040", opacity = 0.35f,
        cornerX = 0f, cornerY = 0f, spread = 0.65f, pulse = 0.40f, pulseSpeed = 0.50f,
    )

    /** Cool cyan/teal leak from the right edge. */
    val LEAK_COOL = OGLightLeakSpec(
        name = "leak_cool", color = "#40C8FF", opacity = 0.28f,
        cornerX = 1f, cornerY = 0.5f, spread = 0.55f, pulse = 0.30f, pulseSpeed = 0.40f,
    )

    // ── Anamorphic presets ───────────────────────────────────────────────────

    /** Classic blue anamorphic — thin bright blue streak. */
    val ANAMORPHIC_BLUE = OGAnamorphicSpec(
        name = "anamorphic_blue", color = "#80C8FF", opacity = 0.65f,
        width = 2.5f, centerY = 0.5f, spread = 0.90f, pulse = 0.60f, pulseSpeed = 1.5f,
    )

    /** Warm amber streak — golden anamorphic lens. */
    val ANAMORPHIC_AMBER = OGAnamorphicSpec(
        name = "anamorphic_amber", color = "#FFC040", opacity = 0.52f,
        width = 3.0f, centerY = 0.5f, spread = 0.85f, pulse = 0.42f, pulseSpeed = 1.2f,
    )

    // ── Lens flare presets ───────────────────────────────────────────────────

    /** Cinema anamorphic flare — 6 arms, 5 warm bokeh discs, upper-right source. */
    val FLARE_CINEMA = OGLensFlareSpec(
        name = "flare_cinema", color = "#FFFFFF", tint = "#FFD080", opacity = 0.70f,
        centerX = 0.75f, centerY = 0.22f, scale = 1.0f, streakLength = 0.50f,
        streakCount = 6, bokehCount = 5, pulse = 0.25f, pulseSpeed = 2.0f,
    )

    /** Intense sun flare — 8 arms, full brightness, centred high. */
    val FLARE_SUN = OGLensFlareSpec(
        name = "flare_sun", color = "#FFFEF0", tint = "#FFE060", opacity = 0.85f,
        centerX = 0.50f, centerY = 0.18f, scale = 1.3f, streakLength = 0.65f,
        streakCount = 8, bokehCount = 3, pulse = 0.35f, pulseSpeed = 1.5f,
    )

    /** Subtle ambient lens shimmer — 4 arms, low opacity. */
    val FLARE_SUBTLE = OGLensFlareSpec(
        name = "flare_subtle", color = "#FFFFFF", tint = "#FFD080", opacity = 0.40f,
        centerX = 0.80f, centerY = 0.20f, scale = 0.7f, streakLength = 0.35f,
        streakCount = 4, bokehCount = 2, pulse = 0.20f, pulseSpeed = 2.5f,
    )

    // ── Preset maps ──────────────────────────────────────────────────────────

    val vignettePresets: Map<String, OGVignetteSpec> =
        listOf(SUBTLE_VIG, MOODY_VIG, LOMO_VIG).associateBy { it.name!! }

    val chromaPresets: Map<String, OGChromaSpec> =
        listOf(CHROMA_RETRO, CHROMA_GLITCH).associateBy { it.name!! }

    val lightLeakPresets: Map<String, OGLightLeakSpec> =
        listOf(LEAK_WARM, LEAK_COOL).associateBy { it.name!! }

    val anamorphicPresets: Map<String, OGAnamorphicSpec> =
        listOf(ANAMORPHIC_BLUE, ANAMORPHIC_AMBER).associateBy { it.name!! }

    val flarePresets: Map<String, OGLensFlareSpec> =
        listOf(FLARE_CINEMA, FLARE_SUN, FLARE_SUBTLE).associateBy { it.name!! }
}
