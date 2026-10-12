package com.solidkey.painpoints.stage

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Drive a suspend [block] that completes synchronously (our authors never really suspend) to a
 *  result, with no coroutines-test dependency — so it compiles on every target, not just the JVM. */
private fun <T> runSync(block: suspend () -> T): T {
    var out: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
    return (out ?: error("author did not complete synchronously")).getOrThrow()
}

/**
 * The **describe → movie** loop, offline half. These pin that [OGOfflineStageAuthor] turns a plain
 * sentence into a real, live, playable [OGStage] with no model and no network — the honest floor the
 * demo and tests run on — and that [OGStageAuthoring] wires a (fake) model through the same format.
 */
class OGStageAuthoringTest {

    private val author = OGOfflineStageAuthor()

    private fun contents(stage: OGStage) = stage.layers.map { it.content }

    @Test
    fun offlineAuthorBuildsAPlayableLiveMovie() {
        val stage = author.direct("a calm afternoon in the hills")
        assertTrue(stage.layers.size >= 3, "expected backdrop + atmosphere + title at least")
        assertTrue(stage.isLive, "an authored movie should be live")
        // A backdrop scene, atmosphere particles, and a styled title are always present.
        assertTrue(contents(stage).any { it is OGStageContent.Scene }, "has a backdrop scene")
        assertTrue(contents(stage).any { it is OGStageContent.Particles }, "has atmosphere particles")
        val title = contents(stage).filterIsInstance<OGStageContent.Text>().firstOrNull()
        assertNotNull(title, "has a styled title card")
        assertTrue(title.text.isNotBlank(), "title derived from the ask")
        // Every instant resolves without throwing (same gate as the showcase movies).
        for (i in 0..8) stage.resolveAt(stage.durationMs * i / 8)
    }

    @Test
    fun stormAskGetsLightningAndRain() {
        val stage = author.direct("a stormy night over the sea")
        assertTrue(contents(stage).any { it is OGStageContent.Lightning }, "storm → a lightning beat")
        assertTrue(contents(stage).any { it is OGStageContent.Particles }, "storm → falling particles")
    }

    @Test
    fun blossomAskGetsPetalParticles() {
        val stage = author.direct("cherry blossoms drifting over pink hills at sunset")
        val petals = contents(stage).filterIsInstance<OGStageContent.Particles>()
            .firstOrNull { it.particles.shape == "petal" }
        assertNotNull(petals, "blossom → petal-shaped particles")
    }

    @Test
    fun speedAskGetsSpeedLines() {
        val stage = author.direct("a neon city speed rush")
        assertTrue(contents(stage).any { it is OGStageContent.SpeedLines }, "rush → speed lines")
    }

    @Test
    fun sameWordsProduceTheSameMovie() {
        val a = OGStages.encode(author.direct("a stormy night over the sea"))
        val b = OGStages.encode(author.direct("a stormy night over the sea"))
        assertEquals(a, b, "the director is deterministic in the instruction")
    }

    @Test
    fun differentWordsProduceDifferentMovies() {
        val a = OGStages.encode(author.direct("a stormy night over the sea"))
        val b = OGStages.encode(author.direct("cherry blossoms at sunset"))
        assertTrue(a != b, "different asks → different movies")
    }

    @Test
    fun everyAuthoredMovieReEncodesAndReDecodes() {
        // The director's output must round-trip through the SAME codec a model's reply goes through.
        for (ask in listOf("fireworks over the harbour", "autumn leaves in a forest", "a snowy winter morning", "")) {
            val stage = author.direct(ask)
            val round = OGStages.decode(OGStages.encode(stage))
            assertEquals(stage.layers.size, round.layers.size, "round-trip layer count for \"$ask\"")
            for (i in 0..6) round.resolveAt(round.durationMs * i / 6)
        }
    }

    @Test
    fun promptCarriesTheInstruction() {
        val prompt = OGStageAuthoring.promptFor("a lonely lighthouse")
        assertTrue(prompt.contains("a lonely lighthouse"), "the ask is embedded in the model prompt")
        assertTrue(prompt.contains("OGStage"), "the stage contract is present")
    }

    @Test
    fun fromReplyAcceptsValidAndRejectsJunk() {
        val good = """{"layers":[{"id":"a","content":{"type":"solid","color":"#112233"}}]}"""
        assertNotNull(OGStageAuthoring.fromReply(good), "valid stage JSON decodes")
        assertNull(OGStageAuthoring.fromReply("not json at all"), "junk → null")
        assertNull(OGStageAuthoring.fromReply("{}"), "an empty (zero-layer) stage is treated as a miss")
    }

    @Test
    fun modelAuthorDecodesAModelReply() = runSync {
        val fake = OGStageModel { _ ->
            // A model might wrap its JSON in prose / fences — decode must survive it.
            """Sure! Here you go:
               ```json
               {"width":800,"height":800,"durationMs":3000,"layers":[
                 {"id":"bg","content":{"type":"solid","color":"#223344"}},
                 {"id":"t","content":{"type":"text","text":"HELLO"}}]}
               ```"""
        }
        val stage = OGStageAuthoring.modelAuthor(fake).author("anything")
        assertEquals(2, stage.layers.size)
        assertEquals("HELLO", stage.layers.mapNotNull { it.content as? OGStageContent.Text }.first().text)
    }

    @Test
    fun resilientFallsBackWhenTheModelFails() = runSync {
        val broken = OGStageModel { _ -> throw RuntimeException("no api key") }
        val stage = OGStageAuthoring.resilient(broken).author("a stormy night over the sea")
        // Fell back to the offline director → a real, multi-layer movie, not an error.
        assertTrue(stage.layers.size >= 3)
        assertTrue(stage.layers.any { it.content is OGStageContent.Lightning })
    }
}
