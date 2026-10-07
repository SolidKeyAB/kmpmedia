package com.solidkey.painpoints.fx

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers the pure slash geometry (centreline, tapered ribbon, draw-on lifecycle) and the codec/presets. */
class OGSlashTest {

    // ── centreline ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun centerline_passesThroughTheEndpoints() {
        val spec = OGSlashSpec()
        val cl = slashCenterline(spec)
        assertTrue(cl.size >= 4)
        assertEquals(spec.path.first().x, cl.first().x, 1e-5f)
        assertEquals(spec.path.first().y, cl.first().y, 1e-5f)
        assertEquals(spec.path.last().x, cl.last().x, 1e-5f)
        assertEquals(spec.path.last().y, cl.last().y, 1e-5f)
    }

    @Test
    fun centerline_respectsTheSamplesCap() {
        assertTrue(slashCenterline(OGSlashSpec(samples = 2)).size >= 4) // clamped up
        assertTrue(slashCenterline(OGSlashSpec(samples = 5000)).size <= 600) // clamped down
    }

    @Test
    fun centerline_emptyForDegeneratePath() {
        assertTrue(slashCenterline(OGSlashSpec(path = listOf(com.solidkey.painpoints.ai.OGPointSpec(0.5f, 0.5f)))).isEmpty())
    }

    // ── ribbon outline ───────────────────────────────────────────────────────────────────────────

    @Test
    fun ribbon_isAClosedEvenPolygonAtFullReveal() {
        val spec = OGSlashSpec(edge = "smooth")
        val cl = slashCenterline(spec)
        val outline = slashRibbonOutline(cl, spec, reveal = 1f, timeMs = 0L)
        assertEquals(cl.size * 2, outline.size) // left edge forward + right edge back
        assertTrue(outline.size % 2 == 0)
    }

    @Test
    fun ribbon_tapersToAPointAtBothEnds() {
        val spec = OGSlashSpec(edge = "smooth")
        val cl = slashCenterline(spec)
        val outline = slashRibbonOutline(cl, spec, reveal = 1f, timeMs = 0L, widthScale = 1f)
        val m = cl.size
        // Head is outline[0] (left) and outline[last] (right); tail is outline[m-1]/outline[m].
        val headL = outline[0]; val headR = outline[outline.size - 1]
        val tailL = outline[m - 1]; val tailR = outline[m]
        assertTrue(dist(headL, headR) < 1e-3f, "head should pinch to a point")
        assertTrue(dist(tailL, tailR) < 1e-3f, "tail should pinch to a point")
    }

    @Test
    fun ribbon_emptyWhenNotRevealed_fullWhenRevealed() {
        val spec = OGSlashSpec()
        val cl = slashCenterline(spec)
        assertTrue(slashRibbonOutline(cl, spec, reveal = 0f, timeMs = 0L).isEmpty())
        assertTrue(slashRibbonOutline(cl, spec, reveal = 1f, timeMs = 0L).isNotEmpty())
    }

    @Test
    fun ribbon_partialRevealIsShorterThanFull() {
        val spec = OGSlashSpec()
        val cl = slashCenterline(spec)
        val half = slashRibbonOutline(cl, spec, reveal = 0.5f, timeMs = 0L)
        val full = slashRibbonOutline(cl, spec, reveal = 1f, timeMs = 0L)
        assertTrue(half.isNotEmpty() && half.size < full.size)
    }

    @Test
    fun ribbon_isDeterministic_forNoisyEdges() {
        val spec = OGSlashes.FLAME // rough edge = animated noise
        val cl = slashCenterline(spec)
        val a = slashRibbonOutline(cl, spec, reveal = 1f, timeMs = 500L, widthScale = 300f)
        val b = slashRibbonOutline(cl, spec, reveal = 1f, timeMs = 500L, widthScale = 300f)
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals(a[i].x, b[i].x, 0f)
            assertEquals(a[i].y, b[i].y, 0f)
        }
    }

    @Test
    fun boltEdge_jagsTheCentreline() {
        val spec = OGSlashes.THUNDER // bolt edge displaces the centreline
        val cl = slashCenterline(spec)
        val bolt = slashRibbonOutline(cl, spec, reveal = 1f, timeMs = 300L, widthScale = 300f)
        val smooth = slashRibbonOutline(cl, spec.copy(edge = "smooth"), reveal = 1f, timeMs = 300L, widthScale = 300f)
        assertEquals(bolt.size, smooth.size)
        var differs = false
        for (i in bolt.indices) if (dist(bolt[i], smooth[i]) > 1e-3f) { differs = true; break }
        assertTrue(differs, "a bolt edge should jag away from the smooth centreline")
    }

    @Test
    fun widthScale_scalesThickness() {
        val spec = OGSlashSpec(edge = "smooth")
        val cl = slashCenterline(spec)
        val thin = maxSpan(slashRibbonOutline(cl, spec, 1f, 0L, widthScale = 100f))
        val thick = maxSpan(slashRibbonOutline(cl, spec, 1f, 0L, widthScale = 300f))
        assertTrue(thick > thin, "a larger widthScale should produce a thicker ribbon")
    }

    // ── draw-on lifecycle ────────────────────────────────────────────────────────────────────────

    @Test
    fun frame_rampsRevealThenHoldsThenFades() {
        val spec = OGSlashSpec(revealMs = 400f, holdMs = 600f, fadeMs = 200f, loop = false)
        assertEquals(0f, slashFrameAt(spec, 0f).reveal, 1e-3f)
        assertEquals(0.5f, slashFrameAt(spec, 200f).reveal, 1e-3f)
        assertEquals(1f, slashFrameAt(spec, 400f).reveal, 1e-3f)
        assertEquals(1f, slashFrameAt(spec, 700f).alpha, 1e-3f) // holding
        assertEquals(0.5f, slashFrameAt(spec, 1100f).alpha, 1e-2f) // mid-fade (1000..1200)
        assertEquals(0f, slashFrameAt(spec, 1200f).alpha, 1e-3f) // faded out
    }

    @Test
    fun frame_loopsWhenRequested() {
        val spec = OGSlashSpec(revealMs = 400f, holdMs = 600f, fadeMs = 200f, loop = true) // cycle = 1200
        // One full cycle later we are back to the start.
        assertEquals(slashFrameAt(spec, 200f).reveal, slashFrameAt(spec, 1400f).reveal, 1e-3f)
        assertEquals(0f, slashFrameAt(spec, 1200f).reveal, 1e-3f)
    }

    @Test
    fun edgeMode_resolvesFromString() {
        assertEquals(OGSlashEdge.WAVE, OGSlashSpec(edge = "water").edgeMode)
        assertEquals(OGSlashEdge.ROUGH, OGSlashSpec(edge = "flame").edgeMode)
        assertEquals(OGSlashEdge.BOLT, OGSlashSpec(edge = "thunder").edgeMode)
        assertEquals(OGSlashEdge.SMOOTH, OGSlashSpec(edge = "nonsense").edgeMode)
    }

    // ── codec + presets ──────────────────────────────────────────────────────────────────────────

    @Test
    fun codec_roundTripsEveryPreset() {
        for (p in OGSlashes.presets.values) {
            assertEquals(p, OGSlashes.decodeSpec(OGSlashes.encode(p)))
        }
    }

    @Test
    fun codec_toleratesCodeFencesAndRejectsGarbage() {
        val fenced = """```json
            {"name":"x","edge":"bolt","width":0.06}
            ```"""
        val spec = OGSlashes.decodeSpecOrNull(fenced)
        assertNotNull(spec)
        assertEquals(OGSlashEdge.BOLT, spec.edgeMode)
        assertNull(OGSlashes.decodeSpecOrNull("not json at all"))
    }

    @Test
    fun presets_lookupIsCaseInsensitive_andPromptIsConstrained() {
        assertEquals(OGSlashes.WATER, OGSlashes.preset("WaTeR"))
        assertNull(OGSlashes.preset("nope"))
        assertEquals(3, OGSlashes.presets.size)
        val prompt = OGSlashes.slashPrompt("a blue water sweep")
        assertTrue(prompt.contains("path") && prompt.contains("edge") && prompt.contains("JSON"))
    }

    // ── OKLab perceptual colour ──────────────────────────────────────────────────────────────────

    @Test
    fun oklab_roundTripsColors() {
        for (c in listOf(Color(0.1f, 0.4f, 0.9f), Color(1f, 0.55f, 0f), Color(0.2f, 0.8f, 0.3f), Color.White, Color.Black)) {
            val lab = OGFxColor.srgbToOklab(c)
            val back = OGFxColor.oklabToSrgb(lab[0], lab[1], lab[2], lab[3])
            assertEquals(c.red, back.red, 0.01f)
            assertEquals(c.green, back.green, 0.01f)
            assertEquals(c.blue, back.blue, 0.01f)
        }
    }

    @Test
    fun oklabStops_haveEndpointsAndCount() {
        val a = Color(0.1f, 0.4f, 0.9f); val b = Color(1f, 0.85f, 0f)
        val stops = OGFxColor.oklabStops(a, b, 12)
        assertEquals(12, stops.size)
        assertEquals(a.red, stops.first().red, 0.01f)
        assertEquals(b.blue, stops.last().blue, 0.01f)
    }

    @Test
    fun oklabStops_followAPerceptualPath_notTheSrgbChord() {
        val a = Color(1f, 0f, 0f); val b = Color(0f, 1f, 0f) // red → green
        val mid = OGFxColor.oklabStops(a, b, 3)[1]
        // The naive sRGB chord midpoint is (0.5, 0.5, 0); OKLab should take a visibly different path.
        val d = kotlin.math.abs(mid.red - 0.5f) + kotlin.math.abs(mid.green - 0.5f) + kotlin.math.abs(mid.blue - 0f)
        assertTrue(d > 0.05f, "OKLab midpoint should differ from the plain sRGB average")
    }

    // ── simplex / fbm noise ──────────────────────────────────────────────────────────────────────

    @Test
    fun noise_isBounded_andDeterministic() {
        for (k in 0 until 50) {
            val x = k * 0.37f; val y = k * 0.11f
            val n = OGFxNoise.noise2(x, y)
            assertTrue(n in -1.2f..1.2f, "simplex noise should stay bounded, was $n")
            assertEquals(n, OGFxNoise.noise2(x, y), 0f) // deterministic
        }
    }

    @Test
    fun fbm_isBounded_andVariesWithSeed() {
        val a = OGFxNoise.fbm(2.3f, 4.1f, octaves = 4, seed = 1)
        val b = OGFxNoise.fbm(2.3f, 4.1f, octaves = 4, seed = 2)
        assertTrue(a in -1.1f..1.1f && b in -1.1f..1.1f)
        assertTrue(a != b, "a different seed should give a different field")
        assertEquals(a, OGFxNoise.fbm(2.3f, 4.1f, octaves = 4, seed = 1), 0f) // deterministic
    }

    // ── forked lightning branches ────────────────────────────────────────────────────────────────

    @Test
    fun branches_onlyForBolt() {
        val cl = slashCenterline(OGSlashes.WATER)
        assertTrue(slashBranches(cl, OGSlashes.WATER, reveal = 1f, timeMs = 0L).isEmpty())
    }

    @Test
    fun branches_generatedForBolt_tapered_andDeterministic() {
        val spec = OGSlashes.THUNDER
        val cl = slashCenterline(spec)
        assertTrue(slashBranches(cl, spec, reveal = 0f, timeMs = 100L).isEmpty()) // nothing drawn yet
        val a = slashBranches(cl, spec, reveal = 1f, timeMs = 200L, widthScale = 300f)
        val b = slashBranches(cl, spec, reveal = 1f, timeMs = 200L, widthScale = 300f)
        assertTrue(a.isNotEmpty(), "a bolt should fork into branches")
        assertTrue(a.all { it.size >= 2 }, "each branch is a polyline")
        assertEquals(a.size, b.size)
        for (i in a.indices) for (k in a[i].indices) {
            assertEquals(a[i][k].x, b[i][k].x, 0f)
            assertEquals(a[i][k].y, b[i][k].y, 0f)
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private fun dist(a: com.solidkey.painpoints.shape.OGPoint, b: com.solidkey.painpoints.shape.OGPoint): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun maxSpan(outline: List<com.solidkey.painpoints.shape.OGPoint>): Float {
        if (outline.isEmpty()) return 0f
        var minX = outline[0].x; var maxX = outline[0].x; var minY = outline[0].y; var maxY = outline[0].y
        for (p in outline) {
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        }
        return maxOf(maxX - minX, maxY - minY)
    }
}
