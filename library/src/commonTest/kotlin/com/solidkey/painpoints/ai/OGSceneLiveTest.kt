package com.solidkey.painpoints.ai

import com.solidkey.painpoints.look.OGLookSpec
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for the v4 "make it live" motion layer: pure, t-addressable evaluation + round-trip. */
class OGSceneLiveTest {

    private fun close(a: Float, b: Float, eps: Float = 1e-4f) = assertTrue(abs(a - b) < eps, "expected $b, got $a")

    private fun square() = listOf(
        OGPointSpec(0.4f, 0.4f), OGPointSpec(0.6f, 0.4f), OGPointSpec(0.6f, 0.6f), OGPointSpec(0.4f, 0.6f),
    )

    // --- timeline maths ---

    @Test fun oncePhaseClampsAndHolds() {
        val m = OGRegionMotion(duration = 2f, mode = "once", easing = "linear")
        close(m.phase(0f), 0f); close(m.phase(1f), 0.5f); close(m.phase(2f), 1f); close(m.phase(10f), 1f)
    }

    @Test fun loopPhaseWrapsSawtooth() {
        val m = OGRegionMotion(duration = 2f, mode = "loop", easing = "linear")
        close(m.phase(0f), 0f); close(m.phase(1f), 0.5f); close(m.phase(2f), 0f); close(m.phase(3f), 0.5f)
    }

    @Test fun reversePhasePingPongs() {
        val m = OGRegionMotion(duration = 2f, mode = "reverse", easing = "linear")
        close(m.phase(0f), 0f); close(m.phase(1f), 0.5f); close(m.phase(2f), 1f)
        close(m.phase(3f), 0.5f); close(m.phase(4f), 0f)
    }

    @Test fun delayHoldsAtStart() {
        val m = OGRegionMotion(duration = 1f, mode = "once", easing = "linear", delay = 1f)
        close(m.phase(0f), 0f); close(m.phase(0.9f), 0f); close(m.phase(1.5f), 0.5f)
    }

    // --- per-region channels ---

    @Test fun translateMovesEveryVertex() {
        val s = OGPlacedShapeSpec(
            points = square(), label = "sun",
            motion = OGRegionMotion(duration = 1f, mode = "once", easing = "linear",
                translate = OGMotionOffset(OGPointSpec(0f, 0f), OGPointSpec(0.1f, 0.2f))),
        )
        val mid = s.animatedAt(0.5f)                 // phase 0.5 → +half the offset
        close(mid.points[0].x, 0.45f); close(mid.points[0].y, 0.5f)
        val end = s.animatedAt(1f)
        close(end.points[0].x, 0.5f); close(end.points[0].y, 0.6f)
    }

    @Test fun scaleAboutCentroidGrows() {
        val s = OGPlacedShapeSpec(
            points = square(), label = "sun",
            motion = OGRegionMotion(duration = 1f, mode = "once", easing = "linear",
                scale = OGMotionScalar(1f, 2f)),
        )
        val end = s.animatedAt(1f)                    // centroid (0.5,0.5); corner 0.4 → 0.3 at 2×
        close(end.points[0].x, 0.3f); close(end.points[0].y, 0.3f)
    }

    @Test fun opacityChannelOverrides() {
        val s = OGPlacedShapeSpec(
            points = square(), opacity = 1f,
            motion = OGRegionMotion(duration = 1f, mode = "once", easing = "linear",
                opacity = OGMotionScalar(1f, 0f)),
        )
        close(s.animatedAt(0f).opacity!!, 1f); close(s.animatedAt(1f).opacity!!, 0f)
    }

    @Test fun noMotionIsIdentity() {
        val s = OGPlacedShapeSpec(points = square(), fill = "#FF0000")
        val out = s.animatedAt(3.3f)
        assertEquals(s.points, out.points)
    }

    // --- scene-level grade / background ---

    @Test fun sceneLookInterpolates() {
        val motion = OGSceneMotion(duration = 1f, mode = "once", easing = "linear",
            look = OGLookMotion(OGLookSpec.Identity, OGLookSpec(brightness = -0.4f)))
        val mid = motion.lookAt(0.5f); assertNotNull(mid)
        close(mid.brightness, -0.2f)
    }

    @Test fun sceneBackgroundInterpolates() {
        val motion = OGSceneMotion(duration = 1f, mode = "once", easing = "linear",
            background = OGColorMotion("#000000", "#FFFFFF"))
        val mid = motion.backgroundAt(0.5f); assertNotNull(mid)
        close(mid.red, 0.5f, 0.02f)
    }

    // --- whole-scene evaluation + fallbacks ---

    @Test fun liveAtAppliesRegionMotionAndSceneGrade() {
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = square(), label = "sun", fill = "#FFCC00",
                    motion = OGRegionMotion(duration = 1f, mode = "once", easing = "linear",
                        translate = OGMotionOffset(OGPointSpec(0f, 0f), OGPointSpec(0f, 0.2f)))),
            ),
            background = "#101010",
            motion = OGSceneMotion(duration = 1f, mode = "once", easing = "linear",
                look = OGLookMotion(OGLookSpec.Identity, OGLookSpec(brightness = -0.5f))),
        )
        val frame = scene.liveAt(1f)
        close(frame.scene.shapes[0].points[0].y, 0.6f)   // sun fell by 0.2
        assertNotNull(frame.look); close(frame.look.brightness, -0.5f)
        assertNotNull(frame.background)                  // falls back to the static #101010
    }

    @Test fun liveAtFallsBackToStaticLookWhenNoMotion() {
        val scene = OGSceneSpec(shapes = listOf(OGPlacedShapeSpec(points = square(), fill = "#00FF00")),
            look = OGLookSpec(saturation = 1.3f))
        val frame = scene.liveAt(5f)
        assertNotNull(frame.look); close(frame.look.saturation, 1.3f)
    }

    @Test fun isLiveReflectsMotionPresence() {
        assertTrue(!OGSceneSpec(shapes = listOf(OGPlacedShapeSpec(points = square()))).isLive)
        assertTrue(OGSceneSpec(shapes = listOf(OGPlacedShapeSpec(points = square(),
            motion = OGRegionMotion()))).isLive)
        assertTrue(OGSceneSpec(shapes = listOf(OGPlacedShapeSpec(points = square())),
            motion = OGSceneMotion()).isLive)
    }

    // --- JSON round-trip: the whole point (an AI can author it) ---

    @Test fun motionSurvivesJsonRoundTrip() {
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = square(), label = "sun", fill = "#FFCC00",
                    motion = OGRegionMotion(duration = 6f, mode = "reverse", easing = "easeInOut",
                        translate = OGMotionOffset(OGPointSpec(0f, -0.1f), OGPointSpec(0.05f, 0.3f)),
                        opacity = OGMotionScalar(1f, 0.8f))),
            ),
            background = "#0A1024",
            motion = OGSceneMotion(duration = 12f, mode = "reverse",
                look = OGLookMotion(OGLookSpec.Identity, OGLookSpec(brightness = -0.4f, temperature = -0.3f)),
                background = OGColorMotion("#4C7AB8", "#0B1030")),
        )
        val json = OGAiVector.encodeScene(scene)
        val back = OGAiVector.decodeScene(json)
        val rm = back.shapes[0].motion; assertNotNull(rm)
        assertEquals(6f, rm.duration); assertEquals("reverse", rm.mode)
        assertNotNull(rm.translate); close(rm.translate.to.y, 0.3f)
        assertNotNull(rm.opacity)
        val sm = back.motion; assertNotNull(sm)
        assertNotNull(sm.look); close(sm.look.to.temperature, -0.3f)
        assertNotNull(sm.background)
        // and it still evaluates
        val frame = back.liveAt(6f); assertNotNull(frame.look)
    }

    @Test fun absentMotionStaysNullAfterRoundTrip() {
        val scene = OGSceneSpec(shapes = listOf(OGPlacedShapeSpec(points = square(), fill = "#FF0000")))
        val back = OGAiVector.decodeScene(OGAiVector.encodeScene(scene))
        assertNull(back.shapes[0].motion); assertNull(back.motion)
    }
}
