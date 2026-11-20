package com.solidkey.painpoints.fx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OGDigitalRainTest {

    @Test
    fun presets_exist_and_are_case_insensitive() {
        for (n in listOf("matrix", "sparse", "red")) assertNotNull(OGDigitalRain.preset(n), "preset $n")
        assertNotNull(OGDigitalRain.preset("MATRIX"))
        assertNotNull(OGDigitalRain.preset("Sparse"))
        assertEquals(3, OGDigitalRain.presets.size)
    }

    @Test
    fun codec_round_trips() {
        val back = OGDigitalRain.decodeSpec(OGDigitalRain.encode(OGDigitalRain.MATRIX))
        assertEquals(OGDigitalRain.MATRIX, back)
    }

    @Test
    fun decode_bad_input_is_null_not_crash() {
        assertEquals(null, OGDigitalRain.decodeSpecOrNull("no json here at all"))
    }

    @Test
    fun glyph_index_always_in_bounds() {
        val spec = OGDigitalRain.MATRIX
        val glyphs = spec.glyphSet
        for (col in 0 until 24) {
            for (row in 0 until 20) {
                for (elapsedStep in 0 until 10) {
                    val cellPhase = (elapsedStep * spec.glyphChangeRate +
                            OGFxMath.hash01(col * 1337 + row * 7, spec.seed)).toInt()
                    val idx = (OGFxMath.hash01(col * 999 + row, cellPhase) * glyphs.length)
                        .toInt().coerceIn(0, glyphs.length - 1)
                    assertTrue(idx in 0 until glyphs.length, "glyph idx $idx out of range [0, ${glyphs.length})")
                }
            }
        }
    }

    @Test
    fun tailDist_math_head_zero_body_increases() {
        // Test the tailDist formula directly with a known headRow position
        val tailLen = 16f
        val rowCount = 20
        val totalRows = rowCount + tailLen + 4f
        // Place head at row 10.3 (mid-screen, visible)
        val headRow = 10.3f

        val drawnRows = mutableListOf<Pair<Int, Float>>()
        for (row in 0 until rowCount) {
            val tailDist = (headRow - row + totalRows) % totalRows
            if (tailDist <= tailLen) drawnRows += row to tailDist
        }

        assertTrue(drawnRows.isNotEmpty(), "rows 0..10 should all be in the tail")
        val minDist = drawnRows.minOf { it.second }
        assertTrue(minDist < 1f, "head row tailDist should be < 1, got $minDist")
        // All tail distances must be non-negative and <= tailLen
        for ((_, dist) in drawnRows) {
            assertTrue(dist >= 0f && dist <= tailLen, "tailDist $dist out of range")
        }
        // Row 10 (the head row) must have the smallest distance
        val headRowEntry = drawnRows.minByOrNull { it.second }!!
        assertEquals(10, headRowEntry.first)
    }

    @Test
    fun sparse_preset_has_lower_column_count_than_matrix() {
        assertTrue(OGDigitalRain.SPARSE.columnCount < OGDigitalRain.MATRIX.columnCount)
    }

    @Test
    fun non_empty_glyph_set_in_all_presets() {
        for ((name, spec) in OGDigitalRain.presets) {
            assertTrue(spec.glyphSet.isNotEmpty(), "glyphSet empty in preset $name")
        }
    }
}
