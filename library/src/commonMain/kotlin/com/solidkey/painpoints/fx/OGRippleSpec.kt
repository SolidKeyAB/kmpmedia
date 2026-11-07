package com.solidkey.painpoints.fx

import kotlinx.serialization.Serializable

/**
 * Describes a raindrop-ripple / capillary-wave effect (Worthington 1908; Fournier & Reeves 1986).
 *
 * Each ripple impact spawns:
 * 1. A **primary ring** that expands with Andersen dispersion deceleration:
 *    r(t) = [propagationSpeed] · t^0.65 · min(W,H)
 * 2. **Secondary rings** spaced [ringSpacing] behind the leading crest, grouped with a
 *    Gaussian wave-packet amplitude envelope for natural capillary dispersion.
 * 3. An **amplitude envelope** that decays exponentially: A(t) = A₀ · e^(-[decayLambda]·t).
 * 4. Optional **Worthington splash crown** — ballistic streak droplets + a central rebound bead.
 * 5. **3D meniscus rendering**: dark refraction trough + diffuse water body + specular crest ridge.
 *
 * Rings are stroked ellipses squashed by [squash] for perspective (1.0 = circle, 0.6 = ground plane).
 * The effect is purely passive — impacts are supplied as [OGRippleEvent]s; see [OGRippleState]
 * and [rememberOGRippleState] for the interactive-tap wiring.
 *
 * Pure `commonMain` — zero shaders, vector stroked ellipses only.
 *
 * Presets and codec on [OGRipple]. Rendered by [OGRippleView].
 */
@Serializable
data class OGRippleSpec(
    val name: String? = null,
    /** Ring expansion speed (fraction of min(W,H) per second^0.65). */
    val propagationSpeed: Float = 0.38f,
    /** Exponential amplitude decay rate λ. Higher = rings fade faster. */
    val decayLambda: Float = 3.2f,
    /** Maximum ring radius as fraction of min(W,H) before the ring is culled. */
    val maxRadius: Float = 0.52f,
    /** Number of secondary ripple rings trailing the primary crest. */
    val secondaryRings: Int = 4,
    /** Radial gap between secondary rings as fraction of min(W,H). */
    val ringSpacing: Float = 0.055f,
    /** Ring stroke colour (HEX). */
    val color: String = "#4FC3F7",
    /** Leading crest highlight colour (brighter, short-lived). */
    val crestColor: String = "#FFFFFF",
    /** Ring stroke width as fraction of min(W,H). */
    val lineWidth: Float = 0.007f,
    /** Vertical squash for perspective: 1.0 = perfect circle, 0.5 = 2:1 ground-plane oval. */
    val squash: Float = 0.65f,
    /** Show Worthington splash-crown droplets at impact (t < 150ms). */
    val crownDroplets: Boolean = true,
    /** Number of crown droplet arcs (ignored when [crownDroplets] = false). */
    val crownCount: Int = 12,
    val seed: Int = 0,
    /** Dark refraction trough shadow colour — creates the 3D water depression under the crest. */
    val troughColor: String = "#051D33",
    /** Relative strength of the 3D refraction trough (`0` = disabled, `0.7` = rich water depth). */
    val troughDepth: Float = 0.65f,
    /** Draw the central Worthington rebound-jet bead on early impact. */
    val reboundJet: Boolean = true,
) {
    fun sanitized() = copy(
        propagationSpeed = propagationSpeed.coerceIn(0.05f, 5f),
        decayLambda      = decayLambda.coerceIn(0.2f, 20f),
        maxRadius        = maxRadius.coerceIn(0.05f, 1.5f),
        secondaryRings   = secondaryRings.coerceIn(0, 12),
        ringSpacing      = ringSpacing.coerceIn(0.005f, 0.3f),
        lineWidth        = lineWidth.coerceIn(0.001f, 0.05f),
        squash           = squash.coerceIn(0.1f, 1f),
        crownCount       = crownCount.coerceIn(3, 32),
        troughDepth      = troughDepth.coerceIn(0f, 1f),
    )
}

/** A single raindrop impact. Positions are normalised [0,1] relative to the view. */
data class OGRippleEvent(
    val cx: Float,
    val cy: Float,
    /** Monotonic millisecond timestamp when this impact occurred (use [withFrameMillis] or similar). */
    val startMs: Long,
)
