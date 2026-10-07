package com.solidkey.painpoints.style

import com.solidkey.painpoints.shape.OGPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Covers the geometry style ops in [OGStyleOps.kt] and their wiring into the [OGStyle] pipeline. */
class OGStyleOpsTest {

    private val square = listOf(OGPoint(0f, 0f), OGPoint(1f, 0f), OGPoint(1f, 1f), OGPoint(0f, 1f))

    private fun List<OGPoint>.allFinite() = all { it.x.isFinite() && it.y.isFinite() }

    // ── subdivide ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun subdivide_insertsDetailPointsPerEdge_keepingOriginals() {
        val out = subdivideOutline(square, detail = 3)
        assertEquals(square.size * (3 + 1), out.size)            // n*(detail+1)
        // Each original vertex stays, at the start of its edge's run.
        square.forEachIndexed { i, p -> assertEquals(p, out[i * 4]) }
    }

    @Test
    fun subdivide_degenerate_returnsInput() {
        assertEquals(square, subdivideOutline(square, 0))
        assertEquals(1, subdivideOutline(listOf(OGPoint(0f, 0f)), 4).size)
    }

    // ── smooth ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun smooth_resamplesToDetailPerEdge_passingThroughOriginals() {
        val detail = 5
        val out = smoothOutline(square, strength = 1f, detail = detail)
        assertEquals(square.size * detail, out.size)
        // Interpolating spline: sample 0 of each edge lands exactly on the original vertex.
        square.forEachIndexed { i, p -> assertEquals(p, out[i * detail]) }
        assertTrue(out.allFinite())
    }

    @Test
    fun smooth_strengthZero_tracesStraightEdges() {
        // strength 0 → the cubic collapses to the straight edge, so samples are linear interpolations.
        val out = smoothOutline(square, strength = 0f, detail = 2)
        // edge 0 is (0,0)->(1,0); sample 1 of 2 sits at the midpoint x=0.5, y=0.
        assertEquals(OGPoint(0.5f, 0f), out[1])
    }

    @Test
    fun smooth_fewerThanThreePoints_returnsInput() {
        val two = listOf(OGPoint(0f, 0f), OGPoint(1f, 1f))
        assertEquals(two, smoothOutline(two, 1f, 6))
    }

    // ── roughen ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun roughen_isDeterministicPerSeed_andVariesBySeed() {
        val a = roughenVertices(square, amplitude = 0.03f, detail = 3, seed = 1)
        val b = roughenVertices(square, amplitude = 0.03f, detail = 3, seed = 1)
        val c = roughenVertices(square, amplitude = 0.03f, detail = 3, seed = 2)
        assertEquals(a, b)                 // same seed → identical (Android == iOS == export)
        assertNotEquals(a, c)              // different seed → different rough edge
        assertEquals(square.size * (3 + 1), a.size)  // it subdivides
        assertTrue(a.allFinite())
    }

    @Test
    fun roughen_boundedByAmplitude_andNoOpWhenZero() {
        val dense = subdivideOutline(square, 3)
        val rough = roughenVertices(square, amplitude = 0.04f, detail = 3, seed = 7)
        rough.forEachIndexed { i, p ->
            assertTrue(abs(p.x - dense[i].x) <= 0.04f + 1e-4f)
            assertTrue(abs(p.y - dense[i].y) <= 0.04f + 1e-4f)
        }
        assertEquals(square, roughenVertices(square, amplitude = 0f, detail = 3, seed = 7))
    }

    // ── wave ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun wave_animatesOverTime_sameTimeIsStable_boundedByAmplitude() {
        val t0 = waveVertices(square, amplitude = 0.05f, waves = 3, speed = 1f, timeMs = 0L)
        val t1 = waveVertices(square, amplitude = 0.05f, waves = 3, speed = 1f, timeMs = 250L)
        val t0again = waveVertices(square, amplitude = 0.05f, waves = 3, speed = 1f, timeMs = 0L)
        assertEquals(square.size, t0.size)
        assertEquals(t0, t0again)                 // deterministic at a given time
        assertNotEquals(t0, t1)                    // it travels
        assertTrue(t0.allFinite() && t1.allFinite())
        t1.forEachIndexed { i, p ->                // displacement magnitude ≤ amplitude
            assertTrue(abs(p.x - square[i].x) <= 0.05f + 1e-4f)
            assertTrue(abs(p.y - square[i].y) <= 0.05f + 1e-4f)
        }
    }

    @Test
    fun wave_noOpWhenZeroAmplitudeOrDegenerate() {
        assertEquals(square, waveVertices(square, 0f, 3, 1f, 100L))
        val two = listOf(OGPoint(0f, 0f), OGPoint(1f, 1f))
        assertEquals(two, waveVertices(two, 0.05f, 3, 1f, 100L))
    }

    // ── pipeline wiring (decode → apply) ───────────────────────────────────────────────────────

    @Test
    fun decode_newOps_andAliases_areWired() {
        // canonical names
        assertEquals(square.size * 6, OGStyles.decode("""{"ops":[{"op":"smooth","detail":6}]}""").apply(square, 0L).outline.size)
        assertEquals(square.size * (4 + 1), OGStyles.decode("""{"ops":[{"op":"subdivide","detail":4}]}""").apply(square, 0L).outline.size)
        // aliases resolve to the same ops
        assertEquals(square.size * 6, OGStyles.decode("""{"ops":[{"op":"round","detail":6}]}""").apply(square, 0L).outline.size)
        assertEquals(square.size * (4 + 1), OGStyles.decode("""{"ops":[{"op":"resample","detail":4}]}""").apply(square, 0L).outline.size)
        val sketch = OGStyles.decode("""{"ops":[{"op":"sketch","amplitude":0.02,"detail":3}]}""").apply(square, 0L)
        assertFalse(sketch.isPixelated)
        assertTrue(sketch.outline.size > square.size)             // roughen subdivides
        val ripple = OGStyles.decode("""{"ops":[{"op":"ripple","amplitude":0.04,"waves":3}]}""")
        assertNotEquals(ripple.apply(square, 0L).outline, ripple.apply(square, 300L).outline) // wave animates
    }

    @Test
    fun decode_handwritingCombo_roughenThenBoil_isLiveAndHandDrawn() {
        // The "hand-drawn" recipe: a fixed rough edge that also gently wobbles.
        val style = OGStyles.decode(
            """{"name":"hand-drawn","ops":[{"op":"roughen","amplitude":0.02,"detail":4},{"op":"boil","amplitude":0.012,"boilFps":7}]}""",
        )
        val a = style.apply(square, 0L)
        val b = style.apply(square, 500L)
        assertFalse(a.isPixelated)
        assertEquals(a.outline.size, b.outline.size)
        assertNotEquals(a.outline, b.outline, "boil after roughen keeps the rough edge alive")
        assertTrue(a.outline.allFinite() && b.outline.allFinite())
    }

    @Test
    fun encode_thenDecode_roundTripsNewParams() {
        val spec = OGStyleSpec(
            name = "geo",
            ops = listOf(
                OGStyleOp(op = "subdivide", detail = 4),
                OGStyleOp(op = "smooth", strength = 0.5f, detail = 8),
                OGStyleOp(op = "roughen", amplitude = 0.03f, detail = 3, seed = 2),
                OGStyleOp(op = "wave", amplitude = 0.02f, waves = 4, speed = 1.5f),
            ),
        )
        assertEquals(spec, OGStyles.decodeSpec(OGStyles.encode(spec)))
    }
}
