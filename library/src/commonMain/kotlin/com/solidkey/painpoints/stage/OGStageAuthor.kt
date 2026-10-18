package com.solidkey.painpoints.stage

import com.solidkey.painpoints.ai.OGAiCapability

/**
 * The seam that turns a plain-language description into a playable [OGStage] movie — the app side of
 * KMPMedia's **"describe → movie"** loop.
 *
 * The library owns the hard, portable half (the [OGStages.stagePrompt] contract and the tolerant
 * [OGStages.decode]); an [OGStageAuthor] is the pluggable other half that actually produces the stage.
 * Two ready shapes:
 *  - [OGOfflineStageAuthor] — a deterministic, **network-free** director that composes a real movie
 *    from the words alone (no model, no key, no call). The honest floor: the loop always works.
 *  - [OGStageAuthoring.modelAuthor] — wraps your own language model (bring your own key; the network
 *    call lives in *your* app via [OGStageModel]) so the words become a richer, model-written movie.
 *
 * Mirrors the library's other provider seams (`OGAudioSource`, `OGSegmenter`): one interface, a
 * zero-dependency default, and a place for the app to plug in the real thing — with no network and no
 * AI SDK anywhere in the library.
 */
public fun interface OGStageAuthor {
    /** Turn [instruction] (e.g. `"a stormy night over the sea"`) into a live, playable movie. */
    public suspend fun author(instruction: String): OGStage
}

/**
 * The raw text-completion seam an app implements **once** to wire a language model into the loop:
 * given a fully-built prompt, return the model's raw text reply. This is the only place a network call
 * lives, and it lives in *your* app — the library never makes one. Wrap it into an [OGStageAuthor]
 * with [OGStageAuthoring.modelAuthor] (builds the prompt + tolerantly decodes), or make it fall back
 * gracefully with [OGStageAuthoring.resilient].
 *
 * A reference Claude implementation (bring your own key) is in `docs/AI_AUTHORING.md`.
 */
public fun interface OGStageModel {
    /** Send [prompt] to the model and return its raw reply (prose / fences are fine; [OGStages.decode]
     *  strips them). */
    public suspend fun complete(prompt: String): String
}

/**
 * Ties the pieces of the describe→movie loop together: build the prompt, ask a model, decode the
 * reply, and fall back when a model is absent or returns junk. Pure glue — all the network stays in
 * the caller's [OGStageModel].
 */
public object OGStageAuthoring {

    /** The exact string to send a model for [instruction] (the [OGStages.stagePrompt] contract). */
    public fun promptFor(instruction: String): String = OGStages.stagePrompt(instruction)

    /** Turn a model's raw [reply] into a stage, or `null` if it won't decode into a non-empty movie.
     *  Tries the stage JSON first, then falls back to lifting an `<svg>…</svg>` out of the reply and
     *  compiling it ([OGStageSvg]) — a model is often more fluent in SVG. (A decode that yields zero
     *  layers is treated as a failure, so an empty `{}` triggers a fallback rather than a blank screen.) */
    public fun fromReply(reply: String): OGStage? =
        OGStages.decodeOrNull(reply)?.takeIf { it.layers.isNotEmpty() }
            ?: OGStageSvg.fromReply(reply)

    /**
     * Wrap a raw text [model] into an [OGStageAuthor]: build the stage prompt from the plain
     * instruction, ask the model, tolerantly decode the reply. The network call is inside [model]
     * (your app, your key); this adds no I/O of its own. Throws [IllegalStateException] if the reply
     * does not decode into a non-empty movie — use [resilient] to fall back instead.
     */
    public fun modelAuthor(model: OGStageModel): OGStageAuthor = OGStageAuthor { instruction ->
        fromReply(model.complete(promptFor(instruction)))
            ?: error("model reply did not decode into a playable OGStage")
    }

    /**
     * The production shape: try [model] first; if it throws or returns something that won't decode
     * into a non-empty movie, fall back to [fallback] (the offline director by default) so the user
     * *always* gets a movie. Rich when a key is present, still working when it is not.
     */
    public fun resilient(
        model: OGStageModel,
        fallback: OGStageAuthor = OGOfflineStageAuthor(),
    ): OGStageAuthor = OGStageAuthor { instruction ->
        val fromModel = runCatching { fromReply(model.complete(promptFor(instruction))) }.getOrNull()
        fromModel ?: fallback.author(instruction)
    }

    /**
     * Describe→movie as an [OGAiCapability] — the first citizen of the capability-manifest mechanism
     * (see [com.solidkey.painpoints.ai.OGAiManifest]). An app lists this in its manifest; the user's AI
     * reads it and answers with an `<svg>` or stage JSON, which [fromReply] decodes. `promptFor`/`decode`
     * reuse the existing contract verbatim, so nothing is duplicated.
     */
    public val illustrate: OGAiCapability<String, OGStage> = object : OGAiCapability<String, OGStage> {
        override val id: String = "illustrate"
        override val title: String = "Living illustration"
        override val description: String =
            "Author a short living vector 'movie' (an OGStage) of the described subject."
        override val outputFormat: String = "one <svg> document, or an OGStage JSON object"
        override fun promptFor(input: String): String = OGStageAuthoring.promptFor(input)
        override fun decode(reply: String): OGStage? = OGStageAuthoring.fromReply(reply)
    }
}
