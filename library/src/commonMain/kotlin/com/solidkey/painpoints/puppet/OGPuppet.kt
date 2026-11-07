package com.solidkey.painpoints.puppet

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec** for [OGPuppetSpec] — decode a JSON puppet pack (authored by a designer, the Layer Cutter
 * tool, or a model) into a spec, encode one back, and build a model prompt. Mirrors
 * [com.solidkey.painpoints.particle.OGParticles] and [OGAiVector]: the same lenient JSON config and
 * tolerant extraction (code fences / stray prose survive), with no network and no AI SDK.
 *
 * The pack names the base + part sprites by resource; feed the decoded spec to [OGPuppetView], which
 * resolves those names with the platform image loader and plays the rig.
 */
object OGPuppet {
    /** The shared lenient JSON config (reused from [OGAiVector] so every data layer agrees). */
    val json get() = OGAiVector.json

    /** Decode a JSON puppet pack into its [OGPuppetSpec]. Throws on malformed input. */
    fun decode(text: String): OGPuppetSpec =
        json.decodeFromString(OGPuppetSpec.serializer(), OGAiVector.extractJson(text))

    /** [decode] but returns `null` instead of throwing on malformed input. */
    fun decodeOrNull(text: String): OGPuppetSpec? = runCatching { decode(text) }.getOrNull()

    /** Serialize a puppet pack to JSON (compact; defaults omitted). */
    fun encode(spec: OGPuppetSpec): String = json.encodeToString(OGPuppetSpec.serializer(), spec)

    /**
     * Build a ready-to-send instruction constraining a model to emit exactly the puppet-pack JSON [decode]
     * expects. Wire the returned string into your model; pass the reply to [decode].
     */
    fun puppetPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a cut-out puppet for the KMPMedia library.

        A puppet is a still "base" sprite plus "parts" (movers, back-to-front). Every sprite shares the
        base frame. Each part: res (resource name), pivotX/pivotY (its joint, 0..1 of the frame),
        swingDeg/speed/phase (a faint idle rotation about the joint), gaze (slide toward the look target,
        fraction of width), head (true for the one head part, which turns toward the look target).
        Spec: base, parts[], aspect (base w/h), grounded (true = stands and bends about its feet; false =
        hovers in place). Output JSON only, no prose.

        Task: $instruction
    """.trimIndent()
}
