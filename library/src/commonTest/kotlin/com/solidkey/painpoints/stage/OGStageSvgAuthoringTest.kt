package com.solidkey.painpoints.stage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The describe→movie seam accepts an SVG reply, not just stage JSON: a model (offline / BYO-key / a
 * remote agent) can answer with an `<svg>` and it compiles into a playable [OGStage] whose scene
 * carries the SVG ids as animatable region labels. Exercises the public [OGStageAuthoring.fromReply].
 */
class OGStageSvgAuthoringTest {

    private val svgReply = """
        Sure — here is the scene:
        ```svg
        <svg viewBox="0 0 100 100">
          <rect id="sky" x="0" y="0" width="100" height="100" fill="#bcd4ff"/>
          <circle id="sun" cx="50" cy="30" r="12" fill="#ffd34d"/>
        </svg>
        ```
    """.trimIndent()

    @Test
    fun svgReplyCompilesIntoAPlayableStage() {
        val stage = assertNotNull(OGStageAuthoring.fromReply(svgReply), "SVG reply should compile")
        assertEquals(1, stage.layers.size)
        val content = stage.layers[0].content
        assertTrue(content is OGStageContent.Scene)
        val labels = content.scene.shapes.map { it.label }
        assertTrue(labels.containsAll(listOf("sky", "sun")), "labels=$labels")
    }

    @Test
    fun stageJsonStillWinsAndJunkIsNull() {
        // A real stage JSON still decodes as before (SVG path is only a fallback).
        val json = """{"width":100,"height":100,"layers":[{"id":"bg","content":{"type":"solid","color":"#000000"}}]}"""
        assertNotNull(OGStageAuthoring.fromReply(json))
        // Neither JSON nor SVG -> null, so resilient() falls back to the offline director.
        assertNull(OGStageAuthoring.fromReply("just some words, no drawing"))
    }
}
