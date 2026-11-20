package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector

/**
 * Codec + presets for [OGRippleSpec].
 *
 * Presets:
 * - [RAIN]   — realistic rain on still water, tight rings, crown droplets, deep trough
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
        crownDroplets: show ballistic streak droplets on impact (true/false).
        troughColor: dark refraction colour for 3D water depression.
        troughDepth: 0..1 strength of the refraction trough (0=flat, 0.7=rich water depth).
        reboundJet: central Worthington bead at impact.

        Fields (all optional): name, propagationSpeed, decayLambda, maxRadius, secondaryRings,
        ringSpacing, color, crestColor, lineWidth, squash, crownDroplets, crownCount, seed,
        troughColor, troughDepth, reboundJet.

        Output ONLY the JSON object.

        $instruction
    """.trimIndent()

    // ── Presets ──────────────────────────────────────────────────────────────────────────────────

    /** Realistic rain — crisp rings, ballistic crown, deep refraction trough. */
    val RAIN = OGRippleSpec(
        name = "rain",
        propagationSpeed = 0.38f, decayLambda = 3.0f, maxRadius = 0.52f,
        secondaryRings = 4, ringSpacing = 0.050f,
        color = "#4FC3F7", crestColor = "#FFFFFF", lineWidth = 0.0075f,
        squash = 0.65f, crownDroplets = true, crownCount = 12,
        troughColor = "#051D33", troughDepth = 0.70f, reboundJet = true,
    )

    /** Calm Ghibli pond — slow wide rings, subtle trough, no crown. */
    val POND = OGRippleSpec(
        name = "pond",
        propagationSpeed = 0.22f, decayLambda = 1.7f, maxRadius = 0.68f,
        secondaryRings = 6, ringSpacing = 0.065f,
        color = "#81D4FA", crestColor = "#E1F5FE", lineWidth = 0.0055f,
        squash = 0.72f, crownDroplets = false, crownCount = 8,
        troughColor = "#002538", troughDepth = 0.55f, reboundJet = false,
    )

    /** Shallow ground puddle — fast, heavy squash, dark earthy trough. */
    val PUDDLE = OGRippleSpec(
        name = "puddle",
        propagationSpeed = 0.50f, decayLambda = 4.2f, maxRadius = 0.42f,
        secondaryRings = 3, ringSpacing = 0.042f,
        color = "#90CAF9", crestColor = "#FFFFFF", lineWidth = 0.0085f,
        squash = 0.40f, crownDroplets = true, crownCount = 14,
        troughColor = "#1A1510", troughDepth = 0.80f, reboundJet = true,
    )

    val presets: Map<String, OGRippleSpec> = listOf(RAIN, POND, PUDDLE).associateBy { it.name!! }
}
