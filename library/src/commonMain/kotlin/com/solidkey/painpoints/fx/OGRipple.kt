package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector

/**
 * Codec + presets for [OGRippleSpec].
 *
 * Presets:
 * - [RAIN]   — realistic rain on still water, tight rings, crown droplets
 * - [POND]   — wider, slower, fewer rings — calm Ghibli pond
 * - [PUDDLE] — shallow perspective, fast decay, heavy squash
 */
object OGRipple {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGRippleSpec =
        json.decodeFromString(OGRippleSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGRippleSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGRippleSpec): String = json.encodeToString(OGRippleSpec.serializer(), spec)

    fun preset(name: String): OGRippleSpec? = presets[name.lowercase()]

    fun ripplePrompt(instruction: String): String = """
        You output ONLY a JSON object describing a raindrop ripple / capillary-wave effect for the
        KMPMedia library. Impacts are supplied separately at runtime; this spec describes the look of
        each ring set.

        propagationSpeed: fraction of min(W,H) per second (Worthington dispersion, exponent 0.65).
        decayLambda: exponential ring fade rate (higher = faster fade).
        maxRadius: how far rings travel before culling, fraction of min(W,H).
        squash: 1.0 = circles, 0.5 = 2:1 ground-plane ovals (perspective).
        crownDroplets: show splash crown micro-droplets on impact (true/false).

        Fields (all optional): name, propagationSpeed, decayLambda, maxRadius, secondaryRings,
        ringSpacing, color, crestColor, lineWidth, squash, crownDroplets, crownCount, seed.

        Examples:
          {"name":"rain","propagationSpeed":0.38,"decayLambda":3.2,"squash":0.65,"crownDroplets":true}
          {"name":"pond","propagationSpeed":0.22,"decayLambda":1.8,"maxRadius":0.68,"squash":0.72,"secondaryRings":6}
          {"name":"puddle","propagationSpeed":0.50,"decayLambda":4.5,"squash":0.40,"color":"#90CAF9"}

        $instruction
        Output ONLY the JSON object.
    """.trimIndent()

    // ── Presets ──────────────────────────────────────────────────────────────────────────────────

    /** Realistic rain — crisp rings, Worthington crown, slight perspective squash. */
    val RAIN = OGRippleSpec(
        name = "rain",
        propagationSpeed = 0.38f, decayLambda = 3.2f, maxRadius = 0.52f,
        secondaryRings = 4, ringSpacing = 0.055f,
        color = "#4FC3F7", crestColor = "#FFFFFF", lineWidth = 0.007f,
        squash = 0.65f, crownDroplets = true, crownCount = 12,
    )

    /** Calm Ghibli pond — slow, wide rings, subtle. */
    val POND = OGRippleSpec(
        name = "pond",
        propagationSpeed = 0.22f, decayLambda = 1.8f, maxRadius = 0.68f,
        secondaryRings = 6, ringSpacing = 0.065f,
        color = "#81D4FA", crestColor = "#E1F5FE", lineWidth = 0.005f,
        squash = 0.72f, crownDroplets = false, crownCount = 8,
    )

    /** Shallow ground puddle — fast, heavy squash (view from above at an angle). */
    val PUDDLE = OGRippleSpec(
        name = "puddle",
        propagationSpeed = 0.50f, decayLambda = 4.5f, maxRadius = 0.42f,
        secondaryRings = 3, ringSpacing = 0.045f,
        color = "#90CAF9", crestColor = "#FFFFFF", lineWidth = 0.008f,
        squash = 0.40f, crownDroplets = true, crownCount = 10,
    )

    val presets: Map<String, OGRippleSpec> = listOf(RAIN, POND, PUDDLE).associateBy { it.name!! }
}
