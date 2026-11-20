package com.solidkey.painpoints.fx

import com.solidkey.painpoints.stage.OGOfflineStageAuthor
import com.solidkey.painpoints.stage.OGStageContent
import com.solidkey.painpoints.stage.OGStages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OGEventsTest {

    @Test
    fun thunderStage_buildsPlayableLiveMovie() {
        val stage = OGEvents.thunderStage(width = 1200, height = 800, durationMs = 4000L)
        assertTrue(stage.isLive, "thunder stage must be live")
        assertTrue(stage.layers.size >= 5, "thunder stage should have at least 5 layers")

        val contents = stage.layers.map { it.content }
        assertTrue(contents.any { it is OGStageContent.Scene }, "has sky backdrop")
        assertTrue(contents.any { it is OGStageContent.Solid }, "has ambient flash layer")
        assertTrue(contents.any { it is OGStageContent.Lightning }, "has lightning bolt")
        assertTrue(contents.any { it is OGStageContent.Particles }, "has particles (sparks/rain)")

        val text = contents.filterIsInstance<OGStageContent.Text>().firstOrNull()
        assertNotNull(text)
        assertEquals("THUNDER", text.text)

        // Resolves cleanly across timeline
        for (i in 0..8) {
            val t = stage.durationMs * i / 8
            val resolved = stage.resolveAt(t)
            assertTrue(resolved.isNotEmpty())
        }

        // Round trips through JSON codec
        val encoded = OGStages.encode(stage)
        val decoded = OGStages.decode(encoded)
        assertEquals(stage.layers.size, decoded.layers.size)
    }

    @Test
    fun earthquakeStage_buildsPlayableLiveMovieWithTremor() {
        val stage = OGEvents.earthquakeStage(width = 1200, height = 800, durationMs = 4000L)
        assertTrue(stage.isLive, "earthquake stage must be live")
        assertTrue(stage.layers.size >= 3, "earthquake stage should have at least 3 layers")

        // Backdrop carries camera tremor tracks
        val backdrop = stage.layers.first { it.id == "earth_backdrop" }
        assertTrue(backdrop.x.keyframes.isNotEmpty(), "tremor shake X track present")
        assertTrue(backdrop.y.keyframes.isNotEmpty(), "tremor shake Y track present")

        val contents = stage.layers.map { it.content }
        val text = contents.filterIsInstance<OGStageContent.Text>().firstOrNull()
        assertNotNull(text)
        assertEquals("EARTHQUAKE", text.text)

        // Resolves cleanly across timeline
        for (i in 0..8) {
            val t = stage.durationMs * i / 8
            val resolved = stage.resolveAt(t)
            assertTrue(resolved.isNotEmpty())
        }

        // Round trips through JSON codec
        val encoded = OGStages.encode(stage)
        val decoded = OGStages.decode(encoded)
        assertEquals(stage.layers.size, decoded.layers.size)
    }

    @Test
    fun floodStage_buildsPlayableLiveMovieWithWaterCrests() {
        val stage = OGEvents.floodStage(width = 1200, height = 800, durationMs = 4000L)
        assertTrue(stage.isLive, "flood stage must be live")
        assertTrue(stage.layers.size >= 4, "flood stage should have at least 4 layers")

        val contents = stage.layers.map { it.content }
        assertTrue(contents.any { it is OGStageContent.Scene }, "has stormy horizon")
        assertTrue(contents.any { it is OGStageContent.Particles }, "has spray/rain particles")

        val text = contents.filterIsInstance<OGStageContent.Text>().firstOrNull()
        assertNotNull(text)
        assertEquals("FLOOD", text.text)

        // Resolves cleanly across timeline
        for (i in 0..8) {
            val t = stage.durationMs * i / 8
            val resolved = stage.resolveAt(t)
            assertTrue(resolved.isNotEmpty())
        }

        // Round trips through JSON codec
        val encoded = OGStages.encode(stage)
        val decoded = OGStages.decode(encoded)
        assertEquals(stage.layers.size, decoded.layers.size)
    }

    @Test
    fun offlineAuthor_routesDisasterEventsToCinematicStages() {
        val author = OGOfflineStageAuthor()

        val quake = author.direct("an earthquake shattering the earth")
        assertTrue(quake.layers.any { it.id == "earth_backdrop" }, "earthquake ask routes to earthquake stage")

        val flood = author.direct("a torrential flood raging over the land")
        assertTrue(flood.layers.any { it.id == "horizon" }, "flood ask routes to flood stage")

        val thunder = author.direct("thunderstorm in the night sky")
        assertTrue(thunder.layers.any { it.id == "sky_flash" }, "thunder ask routes to thunder stage with sky flash")
    }

    @Test
    fun newStageContent_roundTripsPolymorphically() {
        val waveLayer = OGStages.wave("w", OGWave.FLOOD)
        val crackLayer = OGStages.crack("c", OGCrack.GROUND)
        val shockLayer = OGStages.shockwave("s", OGShockwave.GROUND)
        val auroraLayer = OGStages.aurora("a", OGAurora.NORTHERN)
        val rippleLayer = OGStages.ripple("r", OGRipple.RAIN)
        val fireflyLayer = OGStages.firefly("f", OGFirefly.MEADOW)

        val stage = com.solidkey.painpoints.stage.OGStage.of(
            waveLayer, crackLayer, shockLayer, auroraLayer, rippleLayer, fireflyLayer,
            width = 800, height = 600, durationMs = 2000L,
        )

        val json = OGStages.encode(stage)
        val decoded = OGStages.decode(json)

        assertEquals(6, decoded.layers.size)
        assertTrue(decoded.layers[0].content is OGStageContent.Wave)
        assertTrue(decoded.layers[1].content is OGStageContent.Crack)
        assertTrue(decoded.layers[2].content is OGStageContent.Shockwave)
        assertTrue(decoded.layers[3].content is OGStageContent.Aurora)
        assertTrue(decoded.layers[4].content is OGStageContent.Ripple)
        assertTrue(decoded.layers[5].content is OGStageContent.Firefly)
    }

    @Test
    fun eventStages_verifyDurationAndFpsDefaults() {
        val tStage = OGEvents.thunderStage()
        val eStage = OGEvents.earthquakeStage()
        val fStage = OGEvents.floodStage()

        assertEquals(5000L, tStage.durationMs)
        assertEquals(5000L, eStage.durationMs)
        assertEquals(5000L, fStage.durationMs)

        assertTrue(tStage.fps in 24..60)
        assertTrue(eStage.fps in 24..60)
        assertTrue(fStage.fps in 24..60)
    }

    @Test
    fun gridTarget_mapsBoardIndicesToNormalizedCoordinates() {
        val (cx, cy) = OGEvents.gridTarget(col = 2, row = 2, gridCols = 5, gridRows = 5)
        assertEquals(0.5f, cx, 0.001f)
        assertEquals(0.5f, cy, 0.001f)

        val (c0, r0) = OGEvents.gridTarget(col = 0, row = 0, gridCols = 5, gridRows = 5)
        assertTrue(c0 < 0.25f)
        assertTrue(r0 < 0.25f)
    }

    @Test
    fun thunderStage_hasSeismicTraumaTracksAndAirburst() {
        val stage = OGEvents.thunderStage()
        val sky = stage.layers.first { it.id == "sky" }
        assertTrue(sky.x.keyframes.isNotEmpty(), "sky carries seismic tremor X")
        assertTrue(sky.y.keyframes.isNotEmpty(), "sky carries seismic tremor Y")

        val shock = stage.layers.firstOrNull { it.id == "shockwave" }
        assertNotNull(shock, "has ground airburst shockwave")
        assertTrue(shock.content is OGStageContent.Shockwave)
    }

    @Test
    fun floodStage_hasTidalSwellTrack() {
        val stage = OGEvents.floodStage()
        val horizon = stage.layers.first { it.id == "horizon" }
        assertTrue(horizon.y.keyframes.isNotEmpty(), "horizon carries tidal swell Y track")
    }
}
