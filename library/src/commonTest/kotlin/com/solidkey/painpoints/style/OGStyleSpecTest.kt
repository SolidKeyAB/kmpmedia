package com.solidkey.painpoints.style

import com.solidkey.painpoints.shape.OGPoint
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OGStyleSpecTest {

    private val square = listOf(OGPoint(0f, 0f), OGPoint(1f, 0f), OGPoint(1f, 1f), OGPoint(0f, 1f))

    // ── quantizeVertices ──────────────────────────────────────────────────────────────────

    @Test
    fun quantize_snapsToGrid() {
        val pts = listOf(OGPoint(0.13f, 0.37f), OGPoint(0.62f, 0.88f))
        val out = quantizeVertices(pts, 10)
        // Every coordinate must be a multiple of 1/10.
        out.forEach { p ->
            assertEquals(p.x, (p.x * 10f).roundToInt() / 10f, 1e-5f)
            assertEquals(p.y, (p.y * 10f).roundToInt() / 10f, 1e-5f)
        }
        assertEquals(OGPoint(0.1f, 0.4f), out[0])
        assertEquals(OGPoint(0.6f, 0.9f), out[1])
    }

    @Test
    fun quantize_preservesCountAndHandlesDegenerate() {
        assertEquals(square.size, quantizeVertices(square, 8).size)
        assertEquals(square, quantizeVertices(square, 0))   // grid < 1 → unchanged
        assertTrue(quantizeVertices(emptyList(), 8).isEmpty())
    }

    // ── decode / pipeline apply ─────────────────────────────────────────────────────────────

    @Test
    fun decode_boilThenPixelate_producesPixelFrame() {
        val json = """{"name":"pixel sprite","ops":[{"op":"boil","amplitude":0.03,"boilFps":10},{"op":"pixelate","resolution":8}]}"""
        val frame = OGStyles.decode(json).apply(square, 120L)
        assertTrue(frame.isPixelated, "a pipeline ending in pixelate must yield pixels")
        assertTrue(frame.pixels.isNotEmpty())
        assertEquals(1f / 8f, frame.pixelSize, 1e-6f)
    }

    @Test
    fun decode_boilOnly_movesOutlineAndIsNotPixelated() {
        val style = OGStyles.decode("""{"ops":[{"op":"boil","amplitude":0.05,"boilFps":8,"smooth":false}]}""")
        val a = style.apply(square, 0L)
        val b = style.apply(square, 200L)
        assertFalse(a.isPixelated)
        assertEquals(square.size, a.outline.size)
        assertNotEquals(a.outline, b.outline, "boil must move the outline across boil frames")
        // ...but stay within amplitude of the source vertices.
        a.outline.forEachIndexed { i, p ->
            assertTrue(abs(p.x - square[i].x) <= 0.05f + 1e-4f)
            assertTrue(abs(p.y - square[i].y) <= 0.05f + 1e-4f)
        }
    }

    @Test
    fun decode_quantizeStepsTheOutlineToGrid() {
        val style = OGStyles.decode("""{"ops":[{"op":"quantize","grid":4}]}""")
        val out = style.apply(listOf(OGPoint(0.13f, 0.62f)), 0L).outline
        assertEquals(OGPoint(0.25f, 0.5f), out[0]) // 0.13→0.25, 0.62→0.5 on a 1/4 lattice
    }

    @Test
    fun decode_isDeterministic_sameTimeSameFrame() {
        val style = OGStyles.decode("""{"ops":[{"op":"boil","amplitude":0.04,"boilFps":9}]}""")
        assertEquals(style.apply(square, 333L).outline, style.apply(square, 333L).outline)
    }

    @Test
    fun opNameIsCaseInsensitive() {
        val frame = OGStyles.decode("""{"ops":[{"op":"PIXELATE","resolution":4}]}""").apply(square, 0L)
        assertTrue(frame.isPixelated)
        assertEquals(16, frame.pixels.size) // full box over a 4×4 grid
    }

    @Test
    fun unknownOp_isIgnored_outlinePassesThrough() {
        val frame = OGStyles.decode("""{"ops":[{"op":"glitch","amplitude":9.0}]}""").apply(square, 50L)
        assertFalse(frame.isPixelated)
        assertEquals(square, frame.outline, "an unknown op must leave the outline unchanged")
    }

    @Test
    fun emptyPipeline_returnsOutlineUnchanged() {
        val frame = OGStyles.decode("""{"name":"noop","ops":[]}""").apply(square, 10L)
        assertEquals(square, frame.outline)
        assertFalse(frame.isPixelated)
    }

    // ── tolerant input + round-trip ──────────────────────────────────────────────────────────

    @Test
    fun decode_tolerantOfCodeFencesAndProse() {
        val messy = "Here is your style:\n```json\n{\"ops\":[{\"op\":\"pixelate\",\"resolution\":4}]}\n```\nEnjoy!"
        val frame = OGStyles.decode(messy).apply(square, 0L)
        assertTrue(frame.isPixelated)
    }

    @Test
    fun decodeOrNull_returnsNullOnGarbage() {
        assertNull(OGStyles.decodeOrNull("not json at all"))
    }

    @Test
    fun encode_thenDecode_roundTripsOps() {
        val spec = OGStyleSpec(
            name = "stepped boil",
            ops = listOf(
                OGStyleOp(op = "boil", amplitude = 0.03f, boilFps = 10f, smooth = false),
                OGStyleOp(op = "quantize", grid = 16),
            ),
        )
        val back = OGStyles.decodeSpec(OGStyles.encode(spec))
        assertEquals(spec, back)
    }

    // ── terminal pixelate + degenerate / alias edges (Fable review follow-ups) ───────────────

    @Test
    fun pixelate_isTerminal_laterOpsIgnored() {
        // [pixelate, boil]: pixelate returns first, so boil never runs — outline stays the raw square.
        val frame = OGStyles.decode(
            """{"ops":[{"op":"pixelate","resolution":4},{"op":"boil","amplitude":0.3,"boilFps":8}]}""",
        ).apply(square, 100L)
        assertTrue(frame.isPixelated)
        assertEquals(16, frame.pixels.size)   // full square over a 4×4 grid
        assertEquals(square, frame.outline)   // the boil placed after pixelate was ignored
    }

    @Test
    fun pixelate_zeroCells_stillReportsPixelated() {
        // A polygon entirely outside the 0..1 box rasterizes to zero cells, but the frame is still a
        // pixelate result (empty pixels) — it must NOT fall back to a solid outline fill.
        val outside = listOf(OGPoint(2f, 2f), OGPoint(3f, 2f), OGPoint(2.5f, 3f))
        val frame = OGStyles.decode("""{"ops":[{"op":"pixelate","resolution":8}]}""").apply(outside, 0L)
        assertTrue(frame.isPixelated, "pixelSize is set, so the frame is pixelated even with zero cells")
        assertTrue(frame.pixels.isEmpty())
    }

    @Test
    fun aliases_andCrossAliasedParams() {
        // 'pixel' alias + 'grid' used as pixelate's resolution.
        val px = OGStyles.decode("""{"ops":[{"op":"pixel","grid":4}]}""").apply(square, 0L)
        assertEquals(16, px.pixels.size)
        // 'stepped' aliases quantize; snaps 0.13 → 0.25 on a 1/4 lattice.
        val stepped = OGStyles.decode("""{"ops":[{"op":"stepped","grid":4}]}""").apply(listOf(OGPoint(0.13f, 0.13f)), 0L)
        assertEquals(OGPoint(0.25f, 0.25f), stepped.outline[0])
        assertFalse(stepped.isPixelated)
    }

    @Test
    fun boil_bareOp_usesDefaults() {
        val frame = OGStyles.decode("""{"ops":[{"op":"boil"}]}""").apply(square, 140L)
        assertFalse(frame.isPixelated)
        assertEquals(square.size, frame.outline.size)
        // default amplitude is 0.02 → every vertex stays within 0.02 of its source.
        frame.outline.forEachIndexed { i, p ->
            assertTrue(abs(p.x - square[i].x) <= 0.02f + 1e-4f)
            assertTrue(abs(p.y - square[i].y) <= 0.02f + 1e-4f)
        }
    }

    @Test
    fun pixelate_resolutionZero_isDroppedNotInfinite() {
        // res < 1 → the op is dropped (a no-op), so no 1/0 pixelSize and no fallback surprise.
        val frame = OGStyles.decode("""{"ops":[{"op":"pixelate","resolution":0}]}""").apply(square, 0L)
        assertFalse(frame.isPixelated)
        assertEquals(0f, frame.pixelSize)
        assertEquals(square, frame.outline)
    }

    @Test
    fun apply_emptyInput_doesNotCrash() {
        val frame = OGStyles.decode(
            """{"ops":[{"op":"boil","amplitude":0.05},{"op":"pixelate","resolution":8}]}""",
        ).apply(emptyList(), 0L)
        assertTrue(frame.outline.isEmpty())
        assertTrue(frame.pixels.isEmpty())
    }
}
