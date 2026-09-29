package com.solidkey.painpoints.ai

import androidx.compose.ui.graphics.Color
import com.solidkey.painpoints.image.svg.OGSvgNodeOverride
import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.shape.OGPolygonShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the AI-interop layer ([OGAiVector] + the `*Spec` DTOs): the contract that lets an LLM's
 * JSON reply become a live [OGPolygonShape] / [OGSvgNodeOverride] and back. These are the
 * invariants a model integration depends on — tolerant decoding, faithful field mapping, color
 * round-trips through SVG syntax, and prompts that actually state the schema.
 */
class OGAiVectorTest {

    // Compare colors per-component with a small tolerance — matches the codebase's own SVG color
    // test, since a hex/float-constructed sRGB Color need not be byte-identical to a Color(Long).
    private fun assertColor(actual: Color?, r: Float, g: Float, b: Float, a: Float = 1f) {
        assertTrue(actual != null, "expected a color, got null")
        assertEquals(r, actual.red, 0.01f)
        assertEquals(g, actual.green, 0.01f)
        assertEquals(b, actual.blue, 0.01f)
        assertEquals(a, actual.alpha, 0.01f)
    }

    // --- Polygon (describe → clip region) -----------------------------------------------------

    @Test
    fun decodesPolygonFromSchemaJson() {
        val json = """{"points":[{"x":0.5,"y":0.0},{"x":1.0,"y":0.5},{"x":0.5,"y":1.0},{"x":0.0,"y":0.5}]}"""
        val shape = OGAiVector.decodePolygon(json)
        assertEquals(4, shape.points.size)
        assertEquals(OGPoint(0.5f, 0f), shape.points.first())
        assertEquals(OGPoint(0f, 0.5f), shape.points.last())
    }

    @Test
    fun decodesPolygonWrappedInCodeFenceAndProse() {
        // Models routinely wrap JSON in a ```json fence and add a sentence — decoding must cope.
        val reply = """
            Sure! Here is the lasso you asked for:
            ```json
            {"points":[{"x":0.0,"y":0.0},{"x":1.0,"y":0.0},{"x":0.5,"y":1.0}]}
            ```
            Hope that helps.
        """.trimIndent()
        val shape = OGAiVector.decodePolygon(reply)
        assertEquals(3, shape.points.size)
        assertEquals(OGPoint(0.5f, 1f), shape.points[2])
    }

    @Test
    fun polygonRoundTripsThroughJson() {
        val original = OGPolygonShape.of(0.1f to 0.2f, 0.9f to 0.3f, 0.5f to 0.95f)
        val restored = OGAiVector.decodePolygon(OGAiVector.encodePolygon(original))
        assertEquals(original.points, restored.points)
    }

    @Test
    fun decodePolygonOrNullSwallowsGarbage() {
        assertNull(OGAiVector.decodePolygonOrNull("not json at all"))
        assertNull(OGAiVector.decodePolygonOrNull(""))
    }

    // --- SVG patch (describe → SVG patch) -----------------------------------------------------

    @Test
    fun decodesSvgPatchFieldsFaithfully() {
        val json = """
            {"overrides":{
              "needle":{"rotation":120,"fill":"#E53935","scaleX":1.2},
              "bg":{"fill":"#111111"},
              "arc":{"pathData":"M0,0 L10,10","pathDataTo":"M0,0 L20,20","morphProgress":0.5}
            }}
        """.trimIndent()
        val map = OGAiVector.decodeSvgPatch(json)
        assertEquals(3, map.size)

        val needle = map.getValue("needle")
        assertEquals(120f, needle.rotation)
        assertEquals(1.2f, needle.scaleX)
        assertColor(needle.fill, 0xE5 / 255f, 0x39 / 255f, 0x35 / 255f)
        assertTrue(needle.hasPaint && needle.hasTransform)

        val arc = map.getValue("arc")
        assertEquals("M0,0 L10,10", arc.pathData)
        assertEquals("M0,0 L20,20", arc.pathDataTo)
        assertEquals(0.5f, arc.morphProgress)
        assertTrue(arc.hasMorph)

        // Untouched fields stay null so the node keeps its original values.
        assertNull(map.getValue("bg").rotation)
        assertNull(map.getValue("bg").stroke)
    }

    @Test
    fun acceptsNamedAndRgbColors() {
        val map = OGAiVector.decodeSvgPatch(
            """{"overrides":{"a":{"fill":"red"},"b":{"stroke":"rgb(0,128,255)"}}}"""
        )
        assertEquals(Color.Red, map.getValue("a").fill) // named path returns the exact constant
        assertColor(map.getValue("b").stroke, 0f, 128f / 255f, 1f)
    }

    @Test
    fun ignoresUnknownKeys() {
        // A model may emit extra keys ("reason", "confidence"); they must not break decoding.
        val map = OGAiVector.decodeSvgPatch(
            """{"reason":"looks good","overrides":{"x":{"rotation":10,"note":"spin it"}}}"""
        )
        assertEquals(10f, map.getValue("x").rotation)
    }

    @Test
    fun svgPatchRoundTripsThroughHex() {
        val overrides = mapOf(
            "a" to OGSvgNodeOverride(fill = Color(0xFF3366CC), rotation = 45f, strokeWidth = 2f),
            "b" to OGSvgNodeOverride(pathDataTo = "M0,0 L1,1", morphProgress = 0.25f),
        )
        val restored = OGAiVector.decodeSvgPatch(OGAiVector.encodeSvgPatch(overrides))
        assertColor(restored.getValue("a").fill, 0x33 / 255f, 0x66 / 255f, 0xCC / 255f)
        assertEquals(45f, restored.getValue("a").rotation)
        assertEquals(2f, restored.getValue("a").strokeWidth)
        assertEquals("M0,0 L1,1", restored.getValue("b").pathDataTo)
        assertEquals(0.25f, restored.getValue("b").morphProgress)
    }

    @Test
    fun encodesTranslucentColorWithAlpha() {
        // Half-alpha red -> #80FF0000, and it must survive the round trip.
        val overrides = mapOf("a" to OGSvgNodeOverride(fill = Color(red = 1f, green = 0f, blue = 0f, alpha = 0x80 / 255f)))
        val jsonText = OGAiVector.encodeSvgPatch(overrides)
        assertTrue(jsonText.contains("#80ff0000"), "expected #80ff0000 in $jsonText")
    }

    // --- Prompt / schema helpers --------------------------------------------------------------

    @Test
    fun polygonPromptStatesSchemaAndCarriesInstruction() {
        val p = OGAiVector.polygonPrompt("the outline of a five-pointed star")
        assertTrue(p.contains("\"points\""), "prompt should show the points schema")
        assertTrue(p.contains("NORMALIZED"), "prompt should explain normalized coordinates")
        assertTrue(p.contains("the outline of a five-pointed star"), "prompt should embed the instruction")
    }

    @Test
    fun svgPatchPromptListsNodeIdsWhenProvided() {
        val p = OGAiVector.svgPatchPrompt("point the needle to 80%", nodeIds = listOf("needle", "bg"))
        assertTrue(p.contains("\"overrides\""), "prompt should show the overrides schema")
        assertTrue(p.contains("needle, bg"), "prompt should list the allowed node ids")
        assertTrue(p.contains("point the needle to 80%"), "prompt should embed the instruction")
    }

    @Test
    fun svgPatchPromptOmitsIdLineWhenNoneGiven() {
        val p = OGAiVector.svgPatchPrompt("recolor everything")
        assertTrue(!p.contains("Addressable node ids"), "no id list should appear when none provided")
    }
}
