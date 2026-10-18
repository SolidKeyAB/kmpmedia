package com.solidkey.painpoints.skin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OGSkinTest {

    @Test
    fun presets_exist_and_are_case_insensitive() {
        for (n in listOf("reptile", "snake", "hide", "dapple_grey", "denim", "linen", "speckle", "gauze")) {
            assertNotNull(OGSkins.preset(n), "preset $n")
        }
        assertNotNull(OGSkins.preset("REPTILE"))
        assertNotNull(OGSkins.preset("Speckle"))
        assertEquals(8, OGSkins.presets.size)
    }

    @Test
    fun codec_round_trips() {
        for (p in OGSkins.presets.values) {
            assertEquals(p, OGSkins.decodeSpec(OGSkins.encode(p)))
        }
    }

    @Test
    fun decode_tolerates_prose_and_partial_json() {
        val s = OGSkins.decodeSpec("here you go: {\"kind\":\"hide\",\"base\":\"#102030\",\"cell\":0.2} thanks")
        assertEquals("hide", s.kind)
        assertEquals("#102030", s.base)
        assertEquals(0.2f, s.cell)
    }

    @Test
    fun decode_bad_input_is_null_not_crash() {
        assertEquals(null, OGSkins.decodeSpecOrNull("no json at all"))
    }

    @Test
    fun kind_maps_synonyms_case_insensitively() {
        assertEquals(OGSkinKind.REPTILE, OGSkinSpec(kind = "Reptile").skinKind)
        assertEquals(OGSkinKind.HIDE, OGSkinSpec(kind = "HORSE").skinKind)
        assertEquals(OGSkinKind.HIDE, OGSkinSpec(kind = "fur").skinKind)
        assertEquals(OGSkinKind.FABRIC, OGSkinSpec(kind = "Denim").skinKind)
        assertEquals(OGSkinKind.FABRIC, OGSkinSpec(kind = "weave").skinKind)
        assertEquals(OGSkinKind.REPTILE, OGSkinSpec(kind = "nonsense").skinKind) // fallback
    }

    @Test
    fun sanitized_clamps_out_of_range() {
        val s = OGSkinSpec(cell = 9f, roughness = 5f, contrast = -1f, relief = 9f).sanitized()
        assertTrue(s.cell in 0.02f..0.5f)
        assertTrue(s.roughness in 0f..1f)
        assertTrue(s.contrast in 0f..1f)
        assertTrue(s.relief in 0f..1f)
    }

    @Test
    fun default_is_packed_and_unjittered() {
        val d = OGSkinSpec()
        assertEquals(1f, d.density)
        assertEquals(0f, d.sizeJitter)
        assertEquals(0f, d.colorJitter)
    }

    @Test
    fun sanitized_clamps_sparse_and_jitter_knobs() {
        val s = OGSkinSpec(density = 9f, sizeJitter = -3f, colorJitter = 7f).sanitized()
        assertTrue(s.density in 0.05f..1f)
        assertTrue(s.sizeJitter in 0f..1f)
        assertTrue(s.colorJitter in 0f..1f)
        // a zero/negative density floors at the minimum coverage, never 0
        assertEquals(0.05f, OGSkinSpec(density = 0f).sanitized().density)
    }

    @Test
    fun sparse_and_jitter_knobs_round_trip() {
        val spec = OGSkinSpec(kind = "reptile", density = 0.3f, sizeJitter = 0.6f, colorJitter = 0.45f)
        val back = OGSkins.decodeSpec(OGSkins.encode(spec))
        assertEquals(0.3f, back.density)
        assertEquals(0.6f, back.sizeJitter)
        assertEquals(0.45f, back.colorJitter)
    }

    @Test
    fun speckle_preset_is_a_sparse_scatter() {
        val s = OGSkins.SPECKLE
        assertTrue(s.density < 1f, "SPECKLE should be sparse")
        assertTrue(s.sizeJitter > 0f && s.colorJitter > 0f, "SPECKLE should randomise size & colour")
    }

    // ── Raised edge (OGSkinEdgeSpec) ──────────────────────────────────────────────────────────────────

    @Test
    fun edge_presets_exist_and_are_case_insensitive() {
        for (n in listOf("beaded", "crest", "studs")) assertNotNull(OGSkins.edgePreset(n), "edge preset $n")
        assertNotNull(OGSkins.edgePreset("CREST"))
        assertEquals(3, OGSkins.edgePresets.size)
    }

    @Test
    fun edge_defaults_are_a_full_beaded_rim() {
        val e = OGSkinEdgeSpec()
        assertEquals(1f, e.coverage)
        assertEquals(1f, e.bumpScale)
        assertTrue(e.protrude > 0f)
    }

    @Test
    fun edge_sanitized_clamps_out_of_range() {
        val e = OGSkinEdgeSpec(protrude = 9f, coverage = 9f, bumpScale = 99f).sanitized()
        assertTrue(e.protrude in 0f..1.5f)
        assertTrue(e.coverage in 0.1f..1f)
        assertTrue(e.bumpScale in 0.2f..4f)
        // a zero/negative coverage floors above zero (never divide-by-zero in the renderer)
        assertEquals(0.1f, OGSkinEdgeSpec(coverage = 0f).sanitized().coverage)
    }

    @Test
    fun edge_codec_round_trips_presets_and_prose() {
        for (p in OGSkins.edgePresets.values) {
            assertEquals(p, OGSkins.decodeEdge(OGSkins.encodeEdge(p)))
        }
        val s = OGSkins.decodeEdge("sure! {\"protrude\":1.2,\"coverage\":0.4} done")
        assertEquals(1.2f, s.protrude)
        assertEquals(0.4f, s.coverage)
        assertEquals(null, OGSkins.decodeEdgeOrNull("no json here"))
    }
}
