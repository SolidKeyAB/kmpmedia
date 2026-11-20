package com.solidkey.painpoints.fx

import com.solidkey.painpoints.ai.OGAiVector
import com.solidkey.painpoints.ai.clamp01
import com.solidkey.painpoints.shape.OGPoint

/**
 * A **directional fluid stream** — a high-pressure jet of liquid (water/flood/lava) that shoots from
 * origin to target, featuring animated overlapping sine-wave currents, splashing particle droplets at the
 * impact site, and foaming edges.
 */
@kotlinx.serialization.Serializable
data class OGFluidStreamSpec(
    val name: String? = null,
    val x1: Float = 0.5f,
    val y1: Float = 0.9f,
    val x2: Float = 0.5f,
    val y2: Float = 0.1f,
    /** Width of the stream as a fraction of the shorter screen side. */
    val width: Float = 0.08f,
    val coreColor: String = "#81D4FA",
    val edgeColor: String = "#01579B",
    val foamColor: String = "#FFFFFF",
    /** How turbulent the flow is (0..1). */
    val turbulence: Float = 0.5f,
    /** Duration of the blast in milliseconds. */
    val durationMs: Float = 800f,
    /** Number of splashing droplet particles at the target. */
    val splashParticles: Int = 20,
    val loop: Boolean = false,
    val seed: Int = 0,
) {
    fun sanitized() = copy(
        width = width.coerceIn(0.01f, 0.5f),
        turbulence = turbulence.coerceIn(0f, 1f),
        splashParticles = splashParticles.coerceIn(0, 100),
    )
}

internal fun OGFluidStreamSpec.endpointA() = OGPoint(clamp01(x1), clamp01(y1))
internal fun OGFluidStreamSpec.endpointB() = OGPoint(clamp01(x2), clamp01(y2))

object OGFluidStreams {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGFluidStreamSpec =
        json.decodeFromString(OGFluidStreamSpec.serializer(), OGAiVector.extractJson(text))

    fun encode(spec: OGFluidStreamSpec): String = json.encodeToString(OGFluidStreamSpec.serializer(), spec)

    fun preset(name: String): OGFluidStreamSpec? = presets[name.lowercase()]

    /** A high-pressure cinematic water flood stream. */
    val FLOOD_BLAST = OGFluidStreamSpec(
        name = "flood_blast", x1 = 0.5f, y1 = 0.9f, x2 = 0.5f, y2 = 0.2f,
        width = 0.12f, coreColor = "#4FC3F7", edgeColor = "#0D47A1", foamColor = "#E1F5FE",
        turbulence = 0.75f, durationMs = 1200f, splashParticles = 40,
    )

    /** A corrosive acid stream. */
    val ACID_SPIT = OGFluidStreamSpec(
        name = "acid_spit", x1 = 0.2f, y1 = 0.8f, x2 = 0.8f, y2 = 0.2f,
        width = 0.06f, coreColor = "#CCFF90", edgeColor = "#33691E", foamColor = "#F4FF81",
        turbulence = 0.4f, durationMs = 700f, splashParticles = 25,
    )

    val presets = listOf(FLOOD_BLAST, ACID_SPIT).associateBy { it.name!! }
}
