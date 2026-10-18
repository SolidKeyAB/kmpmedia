package com.solidkey.painpoints.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The **capability manifest** mechanism: a small, network-free contract that lets an app declare what
 * it needs from a user's AI, carry those requests over *some* transport, and decode the replies — with
 * an on-device floor when no AI answers.
 *
 * This is the provider-agnostic generalisation of the describe→movie seam ([OGStageModel]/
 * `OGStageAuthoring`): that loop is simply one [OGAiCapability] (`id = "illustrate"`). The library owns
 * only the *contract and codec* here — no sockets, no AI SDK, no "bus". A real transport (e.g. a
 * remote agent over your own channel) implements [OGAiGateway] outside the library.
 */

/**
 * One thing an app can ask the user's AI to do. [In]/[Out] are app-side types; the wire only moves
 * strings, so [promptFor] (build the request) and [decode] (parse the reply) live here, not on the
 * wire. Publish the serializable [spec] so an AI can read what the capability is and how to answer.
 */
public interface OGAiCapability<In, Out> {
    /** Stable key, e.g. `"illustrate"`. Matches the `capabilityId` sent over an [OGAiGateway]. */
    public val id: String

    /** Human label. */
    public val title: String

    /** What it does / how the AI should answer (read by the AI from the manifest). */
    public val description: String

    /** The reply format the AI should return, e.g. `"one <svg> document, or an OGStage JSON object"`. */
    public val outputFormat: String

    /** Build the request text for [input] (what the AI receives). */
    public fun promptFor(input: In): String

    /** Parse the AI's raw [reply] into [Out], or `null` if it is unusable. */
    public fun decode(reply: String): Out?

    /** The serializable, publishable view of this capability (no code — just the contract). */
    public fun spec(): OGAiCapabilitySpec = OGAiCapabilitySpec(id, title, description, outputFormat)
}

/** The serializable face of an [OGAiCapability] — what gets published in a manifest for the AI to read. */
@Serializable
public data class OGAiCapabilitySpec(
    val id: String,
    val title: String = "",
    val description: String = "",
    val outputFormat: String = "",
)

/** Who the manifest belongs to. */
@Serializable
public data class OGAiAppInfo(
    val id: String,
    val name: String = id,
    val version: String = "",
)

/**
 * An app's declared AI requirements: who it is + the capabilities it may ask for. Serialized and
 * published so a subscribed AI can read it and answer in the right shape — "integrate your AI" becomes
 * "point it at the channel".
 */
@Serializable
public data class OGAiManifest(
    val app: OGAiAppInfo,
    val capabilities: List<OGAiCapabilitySpec> = emptyList(),
) {
    public companion object {
        /** Build a manifest from live capabilities (publishes each one's [OGAiCapability.spec]). */
        public fun of(app: OGAiAppInfo, capabilities: List<OGAiCapability<*, *>>): OGAiManifest =
            OGAiManifest(app, capabilities.map { it.spec() })
    }
}

/** JSON codec for [OGAiManifest], reusing the AI codec's lenient config. */
public object OGAiManifests {
    public val json: Json = OGAiVector.json
    public fun encode(manifest: OGAiManifest): String =
        json.encodeToString(OGAiManifest.serializer(), manifest)
    public fun decode(text: String): OGAiManifest =
        json.decodeFromString(OGAiManifest.serializer(), OGAiVector.extractJson(text))
    public fun decodeOrNull(text: String): OGAiManifest? = runCatching { decode(text) }.getOrNull()
}

/**
 * The transport seam: given a `capabilityId` and a built `prompt`, return the AI's raw reply text.
 * Generalises [OGStageModel]. Network-free interface — implementations (e.g. a remote-agent gateway)
 * live outside the library.
 */
public fun interface OGAiGateway {
    public suspend fun invoke(capabilityId: String, prompt: String): String
}

/** Ergonomic call: build the prompt, send it through [gateway], decode the reply. `null` on no/bad reply. */
public suspend fun <In, Out> OGAiCapability<In, Out>.invokeVia(gateway: OGAiGateway, input: In): Out? =
    runCatching { decode(gateway.invoke(id, promptFor(input))) }.getOrNull()

/**
 * Agent-first with an on-device floor: try [gateway]; if it throws or returns blank for a capability,
 * fall back to that capability's local producer in [floors]. Keeps every capability working with no AI.
 * Still network-free — the floors are plain `suspend` producers the app supplies.
 */
public class OGAiResilientGateway(
    private val gateway: OGAiGateway,
    private val floors: Map<String, suspend (String) -> String> = emptyMap(),
) : OGAiGateway {
    override suspend fun invoke(capabilityId: String, prompt: String): String =
        runCatching { gateway.invoke(capabilityId, prompt) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: floors[capabilityId]?.invoke(prompt)
            ?: error("no AI reply and no floor for capability '$capabilityId'")
}
