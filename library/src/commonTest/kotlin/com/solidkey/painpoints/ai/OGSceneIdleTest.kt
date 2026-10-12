package com.solidkey.painpoints.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Idle motion is attached by region LABEL: a body breathes (scale), eyes blink (opacity), a tail
 * sways (rotate); eyebrows and unrelated regions stay still; a region's own motion is preserved.
 */
class OGSceneIdleTest {

    private fun tri() = listOf(OGPointSpec(0f, 0f), OGPointSpec(0.1f, 0f), OGPointSpec(0.05f, 0.1f))

    @Test
    fun attachesIdleMotionByLabel() {
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = tri(), label = "body", fill = "#aaaaaa"),
                OGPlacedShapeSpec(points = tri(), label = "left_eye", fill = "#111111"),
                OGPlacedShapeSpec(points = tri(), label = "pupil_r", fill = "#000000"),
                OGPlacedShapeSpec(points = tri(), label = "tail", fill = "#7a4a28"),
                OGPlacedShapeSpec(points = tri(), label = "eyebrow", fill = "#000000"),
                OGPlacedShapeSpec(points = tri(), label = "ground", fill = "#66aa66"),
            ),
            version = 3,
        )
        val out = OGSceneIdle.animate(scene).shapes.associateBy { it.label }

        assertNotNull(out["body"]?.motion?.scale, "body should breathe (scale)")
        assertNotNull(out["left_eye"]?.motion?.opacity, "eye should blink (opacity)")
        assertNotNull(out["pupil_r"]?.motion?.opacity, "pupil should blink")
        assertNotNull(out["tail"]?.motion?.rotate, "tail should sway (rotate)")
        assertNull(out["eyebrow"]?.motion, "eyebrow should NOT blink")
        assertNull(out["ground"]?.motion, "unmatched label stays still")
        assertTrue(OGSceneIdle.animate(scene).isLive, "an animated scene is live")
    }

    @Test
    fun preservesExistingMotion() {
        val own = OGRegionMotion(rotate = OGMotionScalar(0f, 90f))
        val scene = OGSceneSpec(
            shapes = listOf(OGPlacedShapeSpec(points = tri(), label = "body", fill = "#aaaaaa", motion = own)),
            version = 3,
        )
        assertEquals(90f, OGSceneIdle.animate(scene).shapes[0].motion?.rotate?.to, "a region's own motion wins")
    }
}
