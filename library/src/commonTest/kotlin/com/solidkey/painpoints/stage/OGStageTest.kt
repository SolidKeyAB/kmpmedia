package com.solidkey.painpoints.stage

import com.solidkey.painpoints.ai.OGMotionOffset
import com.solidkey.painpoints.ai.OGPlacedShapeSpec
import com.solidkey.painpoints.ai.OGPointSpec
import com.solidkey.painpoints.ai.OGRegionMotion
import com.solidkey.painpoints.ai.OGSceneSpec
import com.solidkey.painpoints.compositor.OGEasing
import com.solidkey.painpoints.compositor.OGKeyframedFloat
import com.solidkey.painpoints.fx.OGLightningSpec
import com.solidkey.painpoints.fx.OGSlashSpec
import com.solidkey.painpoints.fx.OGSpeedLinesSpec
import com.solidkey.painpoints.look.OGBlendMode
import com.solidkey.painpoints.look.OGLookSpec
import com.solidkey.painpoints.motion.OGCloths
import com.solidkey.painpoints.particle.OGParticleSpec
import com.solidkey.painpoints.style.OGStyleOp
import com.solidkey.painpoints.style.OGStyleSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OGStageTest {

    /** A single red triangle region, optionally moved by [motion]. */
    private fun triangle(motion: OGRegionMotion? = null) = OGPlacedShapeSpec(
        points = listOf(OGPointSpec(0f, 0f), OGPointSpec(0.2f, 0f), OGPointSpec(0.1f, 0.2f)),
        fill = "#FF0000",
        motion = motion,
    )

    /** A scene whose one region translates +0.3 in x over 1s, once, then holds. */
    private fun liveScene() = OGSceneSpec(
        shapes = listOf(
            triangle(
                OGRegionMotion(
                    duration = 1f,
                    mode = "once",
                    easing = "linear",
                    translate = OGMotionOffset(OGPointSpec(0f, 0f), OGPointSpec(0.3f, 0f)),
                ),
            ),
        ),
    )

    private fun stillScene() = OGSceneSpec(shapes = listOf(triangle()))

    private fun sampleStage() = OGStage.of(
        OGStages.solid("bg", "#102030"),
        OGStages.scene("sky", liveScene(), x = OGStages.track(0L to 0f, 1000L to 0.5f)),
        OGStages.particles("snow", OGParticleSpec(burst = 10, emissionRate = 0f, shape = "petal")),
        width = 100,
        height = 200,
        durationMs = 2000L,
    )

    @Test
    fun wholeStageRoundTrips() {
        val stage = sampleStage()
        val back = OGStages.decode(OGStages.encode(stage))
        assertEquals(stage, back, "a stage should survive encode → decode unchanged")
    }

    @Test
    fun clothLayerRoundTripsAndIsLive() {
        val stage = OGStage.of(
            OGStages.solid("bg", "#102030"),
            OGStages.cloth(
                "hair",
                cloth = OGCloths.preset("hair")!!.copy(wind = 0.4f, windAmplitude = 0.5f),
                anchorX = 0.5f, anchorY = 0.1f, color = "#6D4C41", width = 0.05f, taper = true,
            ),
            width = 100, height = 200, durationMs = 2000L,
        )
        val back = OGStages.decode(OGStages.encode(stage))
        assertEquals(stage, back, "a cloth layer should survive encode → decode unchanged")
        val cloth = back.layers[1].content
        assertTrue(cloth is OGStageContent.Cloth && cloth.cloth.wind == 0.4f)
        // A cloth strip always flutters → the stage reports itself live.
        assertTrue(back.isLive, "a stage with a cloth layer is live")
    }

    @Test
    fun decodeToleratesFencesAndRejectsGarbage() {
        assertNull(OGStages.decodeOrNull("this is not json"))
        val fenced = "Here is your movie:\n```json\n" + OGStages.encode(sampleStage()) + "\n```"
        val back = OGStages.decodeOrNull(fenced)
        assertNotNull(back)
        assertEquals(3, back.layers.size)
    }

    @Test
    fun layerVisibilityWindowGatesResolve() {
        val stage = OGStage.of(
            OGStages.solid("x", "#ffffff", startMs = 1000L, endMs = 2000L),
            durationMs = 3000L,
        )
        assertTrue(stage.resolveAt(500L).isEmpty(), "before startMs")
        assertEquals(1, stage.resolveAt(1500L).size, "inside window")
        assertTrue(stage.resolveAt(2500L).isEmpty(), "after endMs")
    }

    @Test
    fun transformTrackInterpolatesOnAbsoluteClock() {
        val stage = OGStage.of(
            OGStages.scene("s", stillScene(), x = OGStages.track(0L to 0f, 1000L to 1f)),
            durationMs = 2000L,
        )
        assertEquals(0.5f, stage.resolveAt(500L).first().x, 1e-4f)
        assertEquals(1f, stage.resolveAt(1000L).first().x, 1e-4f)
    }

    @Test
    fun opacityIsClampedToUnitRange() {
        val stage = OGStage.of(OGStages.solid("o", "#ffffff", opacity = OGStages.track(0L to 2f)))
        assertEquals(1f, stage.resolveAt(0L).first().opacity, 1e-4f)
    }

    @Test
    fun sceneContentIsTAddressableAtLayerLocalTime() {
        val stage = OGStage.of(OGStages.scene("s", liveScene()), durationMs = 2000L)
        val frame = stage.resolveAt(1000L).first().sceneFrameAt()
        assertNotNull(frame)
        // once-mode, 1s, linear: at t=1s the region has fully translated +0.3 in x.
        assertEquals(0.3f, frame.scene.shapes.first().points.first().x, 1e-3f)
    }

    @Test
    fun contentClockStartsWhenTheLayerEnters() {
        // Same live scene, but the layer only appears at 1000ms — its motion starts from there.
        val stage = OGStage.of(OGStages.scene("late", liveScene(), startMs = 1000L), durationMs = 3000L)
        val atEntry = stage.resolveAt(1000L).first().sceneFrameAt()
        assertNotNull(atEntry)
        assertEquals(0f, atEntry.scene.shapes.first().points.first().x, 1e-3f, "no drift at local t=0")
        val oneSecLater = stage.resolveAt(2000L).first().sceneFrameAt()
        assertNotNull(oneSecLater)
        assertEquals(0.3f, oneSecLater.scene.shapes.first().points.first().x, 1e-3f, "fully drifted at local t=1s")
    }

    @Test
    fun isLiveDetectsEveryKindOfMotion() {
        assertTrue(OGStage.of(OGStages.scene("s", liveScene())).isLive, "scene motion")
        assertTrue(OGStage.of(OGStages.particles("p", OGParticleSpec())).isLive, "particles always animate")
        assertTrue(
            OGStage.of(OGStages.solid("x", "#fff", opacity = OGStages.track(0L to 1f, 500L to 0f))).isLive,
            "a keyframed transform",
        )
        assertTrue(!OGStage.of(OGStages.scene("s", stillScene())).isLive, "a still scene is not live")
        assertTrue(!OGStage.of(OGStages.solid("x", "#fff")).isLive, "a static solid is not live")
    }

    @Test
    fun frameAndAspectMathAreExact() {
        val stage = OGStage(width = 100, height = 200, durationMs = 1000L, fps = 30)
        assertEquals(30, stage.frameCount)
        assertEquals(500L, stage.frameTimeMs(15))
        assertEquals(0.5f, stage.aspect, 1e-4f)
    }

    @Test
    fun stagePromptCarriesTheContract() {
        val p = OGStages.stagePrompt("a sunrise over hills")
        assertTrue(p.contains("layers"))
        assertTrue(p.contains("\"type\":\"scene\""))
        assertTrue(p.contains("\"type\":\"particles\""))
        assertTrue(p.contains("\"type\":\"slash\""))
        assertTrue(p.contains("\"type\":\"text\""))
        assertTrue(p.contains("durationMs"))
        assertTrue(p.contains("a sunrise over hills"))
    }

    @Test
    fun keyframeTrackIsSerializableWithEasing() {
        val track = OGStages.eased(OGEasing.EASE_IN_OUT, 0L to 0f, 1000L to 10f)
        val json = OGStages.json.encodeToString(OGKeyframedFloat.serializer(), track)
        val back = OGStages.json.decodeFromString(OGKeyframedFloat.serializer(), json)
        assertEquals(track, back)
        assertEquals(10f, back.valueAt(2000L), 1e-4f) // flat after the last keyframe
    }

    @Test
    fun lookAndBlendSurviveRoundTrip() {
        val stage = OGStage.of(
            OGStages.scene("s", stillScene(), look = OGLookSpec(brightness = -0.3f), blend = OGBlendMode.MULTIPLY),
        )
        val back = OGStages.decode(OGStages.encode(stage))
        val layer = back.layers.first()
        assertEquals(-0.3f, layer.look?.brightness)
        assertEquals(OGBlendMode.MULTIPLY, layer.blend)
    }

    @Test
    fun fxLayersRoundTripAndAreLive() {
        val stage = OGStage.of(
            OGStages.solid("bg", "#000000"),
            OGStages.slash("slash", OGSlashSpec()),
            OGStages.lightning("bolt", OGLightningSpec()),
            OGStages.speedLines("lines", OGSpeedLinesSpec()),
        )
        val back = OGStages.decode(OGStages.encode(stage))
        assertEquals(stage, back, "fx layers survive the JSON round trip")
        assertEquals(4, back.layers.size)
        assertTrue(back.isLive, "any fx layer makes the stage live")
        assertTrue(back.layers[1].content is OGStageContent.Slash)
        assertTrue(back.layers[2].content is OGStageContent.Lightning)
        assertTrue(back.layers[3].content is OGStageContent.SpeedLines)
    }

    @Test
    fun textLayerRoundTripsAndStyleDrivesLiveness() {
        val roughen = OGStyleSpec(ops = listOf(OGStyleOp(op = "roughen", amplitude = 0.02f, detail = 4)))
        val boil = OGStyleSpec(ops = listOf(OGStyleOp(op = "boil", amplitude = 0.012f, boilFps = 7f)))

        val stage = OGStage.of(
            OGStages.text("title", "HELLO", size = 0.2f, color = "#FFCC00", bold = true, style = roughen),
        )
        val back = OGStages.decode(OGStages.encode(stage))
        assertEquals(stage, back, "a styled text layer survives JSON")
        val c = back.layers.first().content
        assertTrue(c is OGStageContent.Text)
        assertEquals("HELLO", c.text)
        assertEquals("#FFCC00", c.color)
        assertTrue(c.bold)
        assertEquals(roughen, c.style)

        // A static style (roughen) does not animate; a time-varying one (boil) makes the stage live.
        assertTrue(!OGStage.of(OGStages.text("t", "HI", style = roughen)).isLive, "roughen is static")
        assertTrue(OGStage.of(OGStages.text("t", "HI", style = boil)).isLive, "boil keeps the letters alive")
        assertTrue(!OGStage.of(OGStages.text("t", "HI")).isLive, "plain text is not live on its own")
        assertTrue(
            OGStage.of(OGStages.text("t", "HI", opacity = OGStages.track(0L to 0f, 500L to 1f))).isLive,
            "...but a fade-in transform track still makes it live",
        )
    }

    @Test
    fun everyContentKindResolvesAndRoundTrips() {
        // The exporter now walks all seven content kinds; lock the data path they share end to end.
        val stage = OGStage.of(
            OGStages.solid("bg", "#101010"),
            OGStages.scene("scn", liveScene()),
            OGStages.particles("star", OGParticleSpec(burst = 12, emissionRate = 0f, shape = "star")),
            OGStages.slash("sl", OGSlashSpec()),
            OGStages.lightning("lt", OGLightningSpec()),
            OGStages.speedLines("sp", OGSpeedLinesSpec()),
            OGStages.text("title", "GO", style = OGStyleSpec(ops = listOf(OGStyleOp(op = "boil")))),
            durationMs = 1000L,
        )
        assertEquals(stage, OGStages.decode(OGStages.encode(stage)), "all seven kinds survive JSON")
        assertEquals(7, stage.resolveAt(100L).size, "all seven are visible mid-timeline and flatten")
        assertTrue(stage.isLive, "a mix with fx + particles + scene motion is live")
    }

    @Test
    fun imageCutoutRoundTripsAndFrameSwapDrivesLiveness() {
        // A tappable, multi-frame raster cutout (a blink) — the puppet primitive.
        val blink = listOf(
            OGImageFrame("head_open", holdMs = 3000L),
            OGImageFrame("head_half", holdMs = 40L),
            OGImageFrame("head_shut", holdMs = 90L),
            OGImageFrame("head_half", holdMs = 40L),
        )
        val stage = OGStage.of(
            OGStages.image("head", blink, widthFrac = 0.45f, heightFrac = 0.5f,
                onTap = OGStageTrigger(OGTapAction.BOUNCE)),
        )
        val back = OGStages.decode(OGStages.encode(stage))
        assertEquals(stage, back, "an image cutout (frames + fit box + onTap) survives JSON")
        val c = back.layers.first().content
        assertTrue(c is OGStageContent.Image)
        assertEquals(4, c.frames.size)
        assertEquals("head_open", c.frames.first().resource)
        assertTrue(c.isAnimated)
        assertEquals(0.45f, c.widthFrac, 1e-4f)
        assertNotNull(back.layers.first().onTap)

        // A frame-swap sprite makes the stage live; a single still frame does not (unless transformed).
        assertTrue(stage.isLive, "a multi-frame cutout animates by frame-swap")
        assertTrue(!OGStage.of(OGStages.imageStill("still", "head_open")).isLive, "a single-frame cutout is static")
        assertTrue(
            OGStage.of(OGStages.imageStill("still", "head_open", opacity = OGStages.track(0L to 0f, 500L to 1f))).isLive,
            "...but a fade-in transform still makes it live",
        )
    }

    @Test
    fun imageFrameIndexWalksHoldsAndLoops() {
        val c = OGStageContent.Image(
            frames = listOf(
                OGImageFrame("a", holdMs = 100L),
                OGImageFrame("b", holdMs = 50L),
                OGImageFrame("c", holdMs = 50L),
            ),
        )
        assertEquals(0, c.frameIndexAt(0L))
        assertEquals(0, c.frameIndexAt(99L))
        assertEquals(1, c.frameIndexAt(100L))
        assertEquals(1, c.frameIndexAt(149L))
        assertEquals(2, c.frameIndexAt(150L))
        assertEquals(2, c.frameIndexAt(199L))
        // total span = 200ms, so it loops: t=200 is back to frame 0, t=300 back to frame 1.
        assertEquals(0, c.frameIndexAt(200L))
        assertEquals(1, c.frameIndexAt(300L))
        // A single frame (or none) always resolves to index 0.
        assertEquals(0, OGStageContent.Image(listOf(OGImageFrame("only"))).frameIndexAt(9999L))
    }

    @Test
    fun degenerateStagesResolveSafely() {
        // An empty stage resolves to nothing without throwing.
        assertTrue(OGStage().resolveAt(0L).isEmpty())
        // A layer whose window is entirely after the clock is absent, present only inside it.
        val s = OGStage.of(OGStages.solid("x", "#ffffff", startMs = 5000L, endMs = 6000L), durationMs = 10000L)
        assertTrue(s.resolveAt(0L).isEmpty())
        assertEquals(1, s.resolveAt(5500L).size)
        // A zero-duration stage still yields at least one frame and a sane frame time.
        val z = OGStage(durationMs = 0L, fps = 30)
        assertEquals(1, z.frameCount)
        assertEquals(0L, z.frameTimeMs(0))
    }

    @Test
    fun exportFrameTimingIsExact() {
        val s = OGStage(durationMs = 1000L, fps = 25)
        assertEquals(25, s.frameCount)
        assertEquals(40L, s.frameTimeMs(1))
        assertEquals(4, s.frameDelayCs) // 100/25
        assertEquals(2, OGStage(fps = 60).frameDelayCs) // 100/60 ~ 1.67 -> clamped to the 2cs floor
    }
}
