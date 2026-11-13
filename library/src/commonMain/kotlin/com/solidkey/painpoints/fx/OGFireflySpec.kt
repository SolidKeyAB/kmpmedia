package com.solidkey.painpoints.fx

import kotlinx.serialization.Serializable

/**
 * Describes a swarm of synchronising fireflies governed by the **Kuramoto coupled-oscillator model**
 * (Kuramoto 1975 / Strogatz 2000). Each firefly i has a natural frequency ω_i drawn from a spread
 * around [omegaMean] and a phase θ_i. Per frame:
 *
 *   dθ_i/dt = ω_i + K · r · sin(Ψ − θ_i)
 *
 * where r·e^{iΨ} is the complex mean-field order parameter computed in O(N). When coupling
 * [coupling] K > K_c ≈ 2/π · [omegaSpread], local clusters lock phases and luminescence waves
 * propagate through the swarm — the emergent synchronization you see in Ghibli summer meadows.
 *
 * Luminescence: I_i(t) = max(0, sin θ_i)^[blinkPower]. Positions drift with toroidal Brownian
 * random walk (no allocation, pure fBm wander per firefly).
 *
 * Pure `commonMain` — zero shaders, O(N) per frame, ~0.04ms for N=50 on mobile.
 *
 * Presets and codec on [OGFirefly]. Rendered by [OGFireflyView].
 */
@Serializable
data class OGFireflySpec(
    val name: String? = null,
    /** Number of fireflies. */
    val count: Int = 48,
    /** Kuramoto coupling constant K. Above K_c ≈ 2/π·omegaSpread, clusters synchronise. */
    val coupling: Float = 2.8f,
    /** Mean natural oscillation frequency (radians/sec). Higher = faster blink rate. */
    val omegaMean: Float = 1.6f,
    /** Spread of natural frequencies. Lower = all fireflies more similar → easier to sync. */
    val omegaSpread: Float = 0.7f,
    /** Spatial drift speed as fraction of shorter view side per second. */
    val driftSpeed: Float = 0.030f,
    /** Magnitude of Brownian turbulence in the drift path. */
    val turbulence: Float = 0.55f,
    /** Firefly core radius as fraction of the shorter view side. */
    val radius: Float = 0.008f,
    /** Core glow colour (HEX). */
    val color: String = "#CCFF90",
    /** Outer soft halo colour (HEX). */
    val glowColor: String = "#F1F8E9",
    /** Glow halo radius as fraction of shorter side. */
    val glowRadius: Float = 0.038f,
    /** Exponent for the luminescence curve max(0,sin θ)^blinkPower. Higher = sharper flash. */
    val blinkPower: Float = 4f,
    /** Overall brightness scale (0..1). */
    val opacity: Float = 1f,
    val seed: Int = 0,
    /**
     * Constant scroll velocity X added to every firefly position each frame (toroidal wrap).
     * Units: **normalised screen fractions per second** — 1.0 = full view width/sec.
     * Use `scrollVX = -gameSpeed * 0.001f` to drift the mote field left with a horizontal scroller.
     * Note: because positions are normalised, scrollVX = 1 means one full view-width/sec regardless
     * of pixel density — but horizontal and vertical visual speeds will differ on non-square views.
     */
    val scrollVX: Float = 0f,
    /**
     * Constant scroll velocity Y added to every firefly position each frame (toroidal wrap).
     * Units: **normalised screen fractions per second** — 1.0 = full view height/sec.
     */
    val scrollVY: Float = 0f,
) {
    fun sanitized() = copy(
        count       = count.coerceIn(1, 500),
        coupling    = coupling.coerceIn(0f, 20f),
        omegaMean   = omegaMean.coerceIn(0.1f, 20f),
        omegaSpread = omegaSpread.coerceIn(0f, 10f),
        driftSpeed  = driftSpeed.coerceIn(0f, 1f),
        turbulence  = turbulence.coerceIn(0f, 5f),
        radius      = radius.coerceIn(0.001f, 0.1f),
        glowRadius  = glowRadius.coerceIn(0.002f, 0.3f),
        blinkPower  = blinkPower.coerceIn(1f, 16f),
        opacity     = opacity.coerceIn(0f, 1f),
    )
}
