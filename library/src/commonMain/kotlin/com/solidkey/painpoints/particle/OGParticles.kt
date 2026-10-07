package com.solidkey.painpoints.particle

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec + preset library** for [OGParticleSpec] — decode a JSON particle pack (designer- or
 * model-authored) into a live [OGParticleSystem], encode one back, build a model prompt, and reach
 * for a ready-made effect ([CONFETTI], [SPARKS], [SNOW], [BOKEH], [RAIN], [FIREWORKS]). Mirrors
 * [com.solidkey.painpoints.style.OGStyles] and [OGAiVector]: reuses the same lenient JSON config and
 * tolerant extraction (code fences / stray prose survive), with no network and no AI SDK.
 */
object OGParticles {
    /** The shared lenient JSON config (reused from [OGAiVector] so every data layer agrees). */
    val json get() = OGAiVector.json

    /** Decode a JSON particle pack into its [OGParticleSpec]. Throws on malformed input. */
    fun decodeSpec(text: String): OGParticleSpec =
        json.decodeFromString(OGParticleSpec.serializer(), OGAiVector.extractJson(text))

    /** [decodeSpec] but returns `null` instead of throwing on malformed input. */
    fun decodeSpecOrNull(text: String): OGParticleSpec? = runCatching { decodeSpec(text) }.getOrNull()

    /** Decode straight into a live [OGParticleSystem]. Throws on malformed input. */
    fun decode(text: String): OGParticleSystem = decodeSpec(text).toSystem()

    /** [decode] but returns `null` instead of throwing on malformed input. */
    fun decodeOrNull(text: String): OGParticleSystem? = decodeSpecOrNull(text)?.toSystem()

    /** Serialize a particle pack to JSON (compact; defaults omitted). */
    fun encode(spec: OGParticleSpec): String = json.encodeToString(OGParticleSpec.serializer(), spec)

    /** Look up a built-in preset by name (case-insensitive), or `null`. */
    fun preset(name: String): OGParticleSpec? = presets[name.lowercase()]

    /**
     * Build a ready-to-send instruction constraining a model to emit exactly the particle-pack JSON
     * [decode] expects. Wire the returned string into your model; pass the reply to [decode].
     */
    fun particlePrompt(instruction: String): String = """
        You output ONLY a JSON object describing a particle effect for the KMPMedia library.

        Coordinates: emitter x/y and spawnRadius are 0..1 over the view; sizes are a fraction of the
        shorter side; speed is 0..1 units/sec; angleDeg is clockwise from +x with y DOWN (270 = up);
        gravityY > 0 falls down.

        Fields (all optional, sensible defaults):
          maxParticles, emissionRate (per sec), burst (one-shot count), lifetimeMs, lifetimeJitter,
          x, y, spawnRadius, angleDeg, spreadDeg, speed, speedJitter, gravityX, gravityY, drag,
          startSize, endSize, sizeJitter, startColor, endColor, startAlpha, endAlpha,
          spinDeg, spinJitter, shape ("circle"|"square"|"triangle"|"star"), seed.

        Schema: {"name":"<short name>", ...fields...}
        Examples:
        {"name":"confetti","shape":"square","burst":150,"emissionRate":0,"angleDeg":270,"spreadDeg":160,"speed":0.55,"gravityY":0.55,"spinDeg":220,"startColor":"#FF5252","endAlpha":0}
        {"name":"embers","emissionRate":40,"angleDeg":270,"spreadDeg":40,"speed":0.2,"gravityY":-0.05,"startColor":"#FFE082","endColor":"#FF6D00","endAlpha":0,"startSize":0.01,"endSize":0.003}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    // ── Presets ──────────────────────────────────────────────────────────────────────────────────

    /** A celebratory one-shot burst of spinning paper that arcs up and falls. */
    val CONFETTI = OGParticleSpec(
        name = "confetti", shape = "square", maxParticles = 250, burst = 150, emissionRate = 0f,
        lifetimeMs = 2600f, lifetimeJitter = 0.3f, x = 0.5f, y = 0.45f, spawnRadius = 0.06f,
        angleDeg = 270f, spreadDeg = 160f, speed = 0.55f, speedJitter = 0.5f, gravityY = 0.55f,
        drag = 0.25f, startSize = 0.016f, endSize = 0.016f, sizeJitter = 0.5f,
        startColor = "#FF5252", endAlpha = 0f, spinDeg = 220f, spinJitter = 1f, seed = 7,
    )

    /** A quick spray of hot sparks that fan out, slow against drag and cool from yellow to orange. */
    val SPARKS = OGParticleSpec(
        name = "sparks", shape = "circle", maxParticles = 160, burst = 60, emissionRate = 0f,
        lifetimeMs = 700f, lifetimeJitter = 0.4f, x = 0.5f, y = 0.5f, spawnRadius = 0.01f,
        angleDeg = 270f, spreadDeg = 360f, speed = 0.7f, speedJitter = 0.7f, gravityY = 0.4f,
        drag = 1.2f, startSize = 0.012f, endSize = 0.004f, sizeJitter = 0.4f,
        startColor = "#FFF59D", endColor = "#FF6D00", endAlpha = 0f, seed = 3,
    )

    /** Soft flakes drifting down from above, varied in size and speed. */
    val SNOW = OGParticleSpec(
        name = "snow", shape = "circle", maxParticles = 300, burst = 0, emissionRate = 70f,
        lifetimeMs = 8000f, lifetimeJitter = 0.3f, x = 0.5f, y = -0.1f, spawnRadius = 0.6f,
        angleDeg = 90f, spreadDeg = 30f, speed = 0.12f, speedJitter = 0.6f, gravityY = 0.02f,
        drag = 0.1f, startSize = 0.01f, endSize = 0.01f, sizeJitter = 0.6f,
        startColor = "#FFFFFF", startAlpha = 0.9f, endAlpha = 0f, seed = 11,
    )

    /** Dreamy out-of-focus dots drifting gently in every direction. */
    val BOKEH = OGParticleSpec(
        name = "bokeh", shape = "circle", maxParticles = 60, burst = 0, emissionRate = 12f,
        lifetimeMs = 5000f, lifetimeJitter = 0.4f, x = 0.5f, y = 0.5f, spawnRadius = 0.5f,
        angleDeg = 270f, spreadDeg = 360f, speed = 0.03f, speedJitter = 1f, gravityY = 0f,
        drag = 0.2f, startSize = 0.03f, endSize = 0.05f, sizeJitter = 0.6f,
        startColor = "#80DEEA", endColor = "#B388FF", startAlpha = 0.7f, endAlpha = 0f, seed = 5,
    )

    /** Fast thin streaks falling at a slight slant. */
    val RAIN = OGParticleSpec(
        name = "rain", shape = "square", maxParticles = 300, burst = 0, emissionRate = 120f,
        lifetimeMs = 1500f, lifetimeJitter = 0.2f, x = 0.5f, y = -0.05f, spawnRadius = 0.6f,
        angleDeg = 100f, spreadDeg = 6f, speed = 0.9f, speedJitter = 0.2f, gravityY = 0.5f,
        drag = 0f, startSize = 0.006f, endSize = 0.006f, sizeJitter = 0.3f,
        startColor = "#B3E5FC", startAlpha = 0.8f, endAlpha = 0f, seed = 2,
    )

    /** A firework shell: a star burst that spins, arcs under gravity and fades. */
    val FIREWORKS = OGParticleSpec(
        name = "fireworks", shape = "star", maxParticles = 200, burst = 140, emissionRate = 0f,
        lifetimeMs = 1400f, lifetimeJitter = 0.3f, x = 0.5f, y = 0.4f, spawnRadius = 0.005f,
        angleDeg = 270f, spreadDeg = 360f, speed = 0.5f, speedJitter = 0.5f, gravityY = 0.25f,
        drag = 1f, startSize = 0.01f, endSize = 0.004f, sizeJitter = 0.3f,
        startColor = "#FFEB3B", endColor = "#F50057", endAlpha = 0f, spinDeg = 120f, seed = 13,
    )

    // ── Elemental presets (pair these with the action-FX in com.solidkey.painpoints.fx) ──────────────

    /** Cherry-blossom petals drifting and tumbling down from above. */
    val PETALS = OGParticleSpec(
        name = "petals", shape = "petal", maxParticles = 120, burst = 0, emissionRate = 24f,
        lifetimeMs = 6000f, lifetimeJitter = 0.3f, x = 0.5f, y = -0.1f, spawnRadius = 0.6f,
        angleDeg = 90f, spreadDeg = 40f, speed = 0.08f, speedJitter = 0.6f, gravityY = 0.01f,
        drag = 0.05f, startSize = 0.02f, endSize = 0.02f, sizeJitter = 0.5f,
        startColor = "#F8BBD0", endColor = "#F48FB1", startAlpha = 0.95f, endAlpha = 0f,
        spinDeg = 120f, spinJitter = 1f, seed = 21,
    )

    /** Hot embers rising from a fire, cooling from yellow to deep orange. */
    val EMBERS = OGParticleSpec(
        name = "embers", shape = "circle", maxParticles = 200, burst = 0, emissionRate = 55f,
        lifetimeMs = 1600f, lifetimeJitter = 0.4f, x = 0.5f, y = 0.72f, spawnRadius = 0.25f,
        angleDeg = 270f, spreadDeg = 50f, speed = 0.22f, speedJitter = 0.7f, gravityY = -0.06f,
        drag = 0.2f, startSize = 0.012f, endSize = 0.003f, sizeJitter = 0.5f,
        startColor = "#FFE082", endColor = "#FF6D00", endAlpha = 0f, seed = 31,
    )

    /** Water droplets spraying and falling. */
    val DROPLETS = OGParticleSpec(
        name = "droplets", shape = "teardrop", maxParticles = 160, burst = 0, emissionRate = 40f,
        lifetimeMs = 1400f, lifetimeJitter = 0.3f, x = 0.5f, y = 0.3f, spawnRadius = 0.25f,
        angleDeg = 90f, spreadDeg = 60f, speed = 0.18f, speedJitter = 0.6f, gravityY = 0.5f,
        drag = 0.1f, startSize = 0.01f, endSize = 0.006f, sizeJitter = 0.4f,
        startColor = "#E1F5FE", endColor = "#4FC3F7", startAlpha = 0.9f, endAlpha = 0f, seed = 41,
    )

    /** Autumn leaves drifting down, turning from green to amber. */
    val LEAVES = OGParticleSpec(
        name = "leaves", shape = "petal", maxParticles = 90, burst = 0, emissionRate = 16f,
        lifetimeMs = 7000f, lifetimeJitter = 0.3f, x = 0.5f, y = -0.1f, spawnRadius = 0.6f,
        angleDeg = 90f, spreadDeg = 50f, speed = 0.09f, speedJitter = 0.7f, gravityY = 0.015f,
        drag = 0.08f, startSize = 0.024f, endSize = 0.024f, sizeJitter = 0.6f,
        startColor = "#AED581", endColor = "#FBC02D", startAlpha = 0.95f, endAlpha = 0f,
        spinDeg = 150f, spinJitter = 1f, seed = 51,
    )

    /** All built-in presets, keyed by lowercase name. */
    val presets: Map<String, OGParticleSpec> = listOf(
        CONFETTI, SPARKS, SNOW, BOKEH, RAIN, FIREWORKS,
        PETALS, EMBERS, DROPLETS, LEAVES,
    ).associateBy { it.name!! }
}
