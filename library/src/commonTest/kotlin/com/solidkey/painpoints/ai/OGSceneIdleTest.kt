package com.solidkey.painpoints.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Idle motion is attached by region LABEL (a body breathes / eyes blink / a tail sways) and, for the
 * eyes, also by POSITION: a small catchlight drawn on the eyeball blinks with the eye no matter what
 * the model named it — otherwise the white dot floats over a shut eye. Eyebrows, parts that merely
 * sit elsewhere, and a region's own motion are all left alone.
 */
class OGSceneIdleTest {

    /** A small triangle with bbox `[x, y, x+s, y+s]`, so tests can place parts at distinct locations. */
    private fun triAt(x: Float, y: Float, s: Float = 0.08f) =
        listOf(OGPointSpec(x, y), OGPointSpec(x + s, y), OGPointSpec(x + s / 2f, y + s))

    @Test
    fun attachesIdleMotionByLabel() {
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = triAt(0.40f, 0.50f, 0.20f), label = "body", fill = "#aaaaaa"),
                OGPlacedShapeSpec(points = triAt(0.42f, 0.30f), label = "left_eye", fill = "#111111"),
                OGPlacedShapeSpec(points = triAt(0.44f, 0.32f, 0.03f), label = "pupil_r", fill = "#000000"),
                OGPlacedShapeSpec(points = triAt(0.10f, 0.80f), label = "tail", fill = "#7a4a28"),
                OGPlacedShapeSpec(points = triAt(0.42f, 0.20f), label = "eyebrow", fill = "#000000"),
                OGPlacedShapeSpec(points = triAt(0.00f, 0.90f, 0.30f), label = "ground", fill = "#66aa66"),
            ),
            version = 3,
        )
        val out = OGSceneIdle.animate(scene).shapes.associateBy { it.label }

        assertNotNull(out["body"]?.motion?.scale, "body should breathe (scale)")
        assertNotNull(out["left_eye"]?.motion?.opacity, "eye should blink (opacity)")
        assertNotNull(out["pupil_r"]?.motion?.opacity, "pupil should blink")
        assertNotNull(out["tail"]?.motion?.rotate, "tail should sway (rotate)")
        assertNull(out["eyebrow"]?.motion, "eyebrow should NOT blink")
        assertNull(out["ground"]?.motion, "unmatched label elsewhere stays still")
        assertTrue(OGSceneIdle.animate(scene).isLive, "an animated scene is live")
    }

    @Test
    fun catchlightOnTheEyeBlinksWhateverItsCalled() {
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = triAt(0.42f, 0.30f), label = "left_eye", fill = "#111111"),
                // A tiny white dot sitting inside the eye's bounds, labelled with a NON-eye word.
                OGPlacedShapeSpec(points = triAt(0.45f, 0.32f, 0.015f), label = "highlight", fill = "#ffffff"),
                // A same-kind dot far from any eye must stay fully opaque (no false positive).
                OGPlacedShapeSpec(points = triAt(0.90f, 0.10f, 0.015f), label = "sparkle", fill = "#ffffff"),
            ),
            version = 3,
        )
        val out = OGSceneIdle.animate(scene).shapes.associateBy { it.label }

        assertNotNull(out["highlight"]?.motion?.opacity, "a catchlight ON the eye must blink with it")
        assertNull(out["sparkle"]?.motion, "a bright dot away from the eyes must NOT blink")
    }

    @Test
    fun catchlightSynonymBlinksByLabelAnywhere() {
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = triAt(0.90f, 0.90f, 0.02f), label = "eye glint", fill = "#ffffff"),
            ),
            version = 3,
        )
        assertNotNull(
            OGSceneIdle.animate(scene).shapes[0].motion?.opacity,
            "a catchlight synonym blinks by name even when drawn off the eyeball",
        )
    }

    @Test
    fun callerRulesDriveNonEnglishLabels() {
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = triAt(0.42f, 0.30f), label = "göz", fill = "#111111"),     // "eye" (tr)
                OGPlacedShapeSpec(points = triAt(0.10f, 0.80f), label = "kuyruk", fill = "#7a4a28"),   // "tail" (tr)
            ),
            version = 3,
        )
        val out = OGSceneIdle.animate(
            scene,
            rules = mapOf("göz" to OGSceneIdle.Blink, "kuyruk" to OGSceneIdle.TailSway),
        ).shapes.associateBy { it.label }

        assertNotNull(out["göz"]?.motion?.opacity, "a caller rule blinks a non-English eye label")
        assertNotNull(out["kuyruk"]?.motion?.rotate, "a caller rule sways a non-English tail label")
    }

    @Test
    fun preservesExistingMotion() {
        val own = OGRegionMotion(rotate = OGMotionScalar(0f, 90f))
        val scene = OGSceneSpec(
            shapes = listOf(OGPlacedShapeSpec(points = triAt(0.4f, 0.4f), label = "body", fill = "#aaaaaa", motion = own)),
            version = 3,
        )
        assertEquals(90f, OGSceneIdle.animate(scene).shapes[0].motion?.rotate?.to, "a region's own motion wins")
    }
}
