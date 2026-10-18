package com.solidkey.painpoints.ai

import com.solidkey.painpoints.stage.OGStageAuthoring
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Drive a suspend [block] that completes synchronously (our gateways do) with no coroutines-test dep. */
private fun <T> runSync(block: suspend () -> T): T {
    var out: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
    return (out ?: error("block did not complete synchronously")).getOrThrow()
}

/**
 * The capability-manifest contract ([OGAiManifest]): the serializable manifest round-trips, describe→
 * movie is wired in as the `illustrate` capability, and the resilient gateway prefers the AI but always
 * falls back to the on-device floor. All network-free.
 */
class OGAiManifestTest {

    private val sampleSvg =
        """<svg viewBox="0 0 10 10"><circle id="c" cx="5" cy="5" r="4" fill="#f00"/></svg>"""

    @Test
    fun manifest_round_trips() {
        val m = OGAiManifest(
            OGAiAppInfo("wordstorm", "WordStorm", "0.3.0"),
            listOf(OGAiCapabilitySpec("illustrate", "Living illustration", "draws it", "one <svg>")),
        )
        val back = OGAiManifests.decode(OGAiManifests.encode(m))
        assertEquals(m, back)
        assertEquals("wordstorm", back.app.id)
        assertEquals("illustrate", back.capabilities.single().id)
    }

    @Test
    fun manifest_of_capabilities_publishes_specs() {
        val m = OGAiManifest.of(OGAiAppInfo("wordstorm"), listOf(OGStageAuthoring.illustrate))
        assertEquals(listOf("illustrate"), m.capabilities.map { it.id })
        assertEquals("Living illustration", m.capabilities.single().title)
        assertEquals("wordstorm", m.app.name) // name defaults to id when omitted
    }

    @Test
    fun illustrate_capability_prompt_and_decode() {
        val cap = OGStageAuthoring.illustrate
        assertEquals("illustrate", cap.id)
        assertTrue(cap.promptFor("a cat").isNotBlank())
        assertNotNull(cap.decode(sampleSvg))   // a valid svg → a playable stage
        assertNull(cap.decode("not a drawing")) // junk → null
    }

    @Test
    fun invokeVia_decodes_gateway_reply() = runSync {
        val gw = OGAiGateway { _, _ -> sampleSvg }
        val stage = OGStageAuthoring.illustrate.invokeVia(gw, "a cat")
        assertNotNull(stage)
        assertTrue(stage.layers.isNotEmpty())
    }

    @Test
    fun invokeVia_null_on_bad_reply() = runSync {
        val gw = OGAiGateway { _, _ -> "garbage" }
        assertNull(OGStageAuthoring.illustrate.invokeVia(gw, "a cat"))
    }

    @Test
    fun resilient_prefers_gateway() = runSync {
        val gw = OGAiGateway { _, _ -> "AI-reply" }
        val floors: Map<String, suspend (String) -> String> = mapOf("x" to { _ -> "floor" })
        val r = OGAiResilientGateway(gw, floors)
        assertEquals("AI-reply", r.invoke("x", "p"))
    }

    @Test
    fun resilient_falls_back_on_throw() = runSync {
        val gw = OGAiGateway { _, _ -> error("down") }
        val floors: Map<String, suspend (String) -> String> = mapOf("x" to { _ -> "floor" })
        val r = OGAiResilientGateway(gw, floors)
        assertEquals("floor", r.invoke("x", "p"))
    }

    @Test
    fun resilient_falls_back_on_blank() = runSync {
        val gw = OGAiGateway { _, _ -> "   " }
        val floors: Map<String, suspend (String) -> String> = mapOf("x" to { p -> "floor:$p" })
        val r = OGAiResilientGateway(gw, floors)
        assertEquals("floor:p", r.invoke("x", "p"))
    }

    @Test
    fun resilient_throws_without_floor() = runSync {
        val gw = OGAiGateway { _, _ -> error("down") }
        val r = OGAiResilientGateway(gw)
        val caught = runCatching { r.invoke("x", "p") }.exceptionOrNull()
        assertTrue(caught is IllegalStateException)
    }
}
