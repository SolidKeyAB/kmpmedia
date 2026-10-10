package com.solidkey.painpoints.ai

import androidx.compose.ui.graphics.Color
import com.solidkey.painpoints.image.svg.OGSvgNodeOverride
import com.solidkey.painpoints.shape.OGPoint
import com.solidkey.painpoints.shape.OGPolygonShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    // --- v2 "painted vector" format -------------------------------------------------------------

    @Test
    fun v1SceneStillDecodes() {
        val json = """{"shapes":[{"label":"sky","fill":"#8C90C2","points":[{"x":0,"y":0},{"x":1,"y":0},{"x":1,"y":0.5}]}]}"""
        val scene = OGAiVector.decodeScene(json)
        assertEquals(1, scene.shapes.size)
        assertEquals("sky", scene.shapes[0].label)
        assertEquals("#8C90C2", scene.shapes[0].fill)
        assertEquals(1, scene.version)
        assertNull(scene.trace)
    }

    @Test
    fun leanArrayPointsDecode() {
        val scene = OGAiVector.decodeScene("""{"shapes":[{"fill":"#fff","points":[[0.0,0.0],[1.0,0.0],[0.5,1.0]]}]}""")
        val pts = scene.shapes[0].points
        assertEquals(3, pts.size)
        assertEquals(0.5f, pts[2].x)
        assertEquals(1.0f, pts[2].y)
    }

    @Test
    fun gradientFillDecodesLeanAndObjectStops() {
        val json = """{"shapes":[{"points":[[0,0],[1,0],[1,1]],"fill":"#808080",
          "gradient":{"type":"linear","stops":[[0,"#3B3F7A"],{"at":1,"color":"#FFC97A"}]}}]}"""
        val g = OGAiVector.decodeScene(json).shapes[0].gradient
        assertNotNull(g)
        assertEquals(2, g.stops.size)
        assertEquals(0f, g.stops[0].at)
        assertEquals("#3B3F7A", g.stops[0].color)
        assertEquals("#FFC97A", g.stops[1].color)
        assertTrue(g.isRenderable)
    }

    @Test
    fun perShapePaintFieldsDecodeAndSmoothingWins() {
        val json = """{"shapes":[{"points":[[0,0],[1,0],[1,1]],"opacity":0.5,"smoothing":0.8,"feather":0.02,"blend":"plus"}]}"""
        val s = OGAiVector.decodeScene(json).shapes[0]
        assertEquals(0.5f, s.opacity)
        assertEquals(0.8f, s.smoothing)
        assertEquals(0.02f, s.feather)
        assertEquals("plus", s.blend)
        assertEquals(0.8f, s.toShape(default = 0f).smoothing)  // per-shape smoothing overrides the default
        val plain = OGPlacedShapeSpec(points = listOf(OGPointSpec(0f, 0f), OGPointSpec(1f, 0f), OGPointSpec(1f, 1f)))
        assertEquals(0.3f, plain.toShape(default = 0.3f).smoothing) // no per-shape value → default applies
    }

    @Test
    fun sceneLevelBackgroundLookTraceDecode() {
        val json = """{"background":"#1A1230","look":{"saturation":1.2,"temperature":0.2},
          "trace":{"method":"lowpoly","targetShapes":6000,"seed":42,"realism":0.85},
          "shapes":[{"points":[[0,0],[1,0],[1,1]]}]}"""
        val scene = OGAiVector.decodeScene(json)
        assertEquals("#1A1230", scene.background)
        assertNotNull(scene.look)
        assertEquals(1.2f, scene.look!!.saturation)
        val tr = scene.trace
        assertNotNull(tr)
        assertEquals(OGTraceMethod.LOWPOLY, tr.methodOrNull)
        assertEquals(6000, tr.targetShapes)
        assertEquals(42, tr.seed)
    }

    @Test
    fun traceMethodAliasesAndUnknownNeverFailsDecode() {
        assertEquals(OGTraceMethod.LOWPOLY, OGTraceMethod.from("edge-aware"))
        assertEquals(OGTraceMethod.VORONOI, OGTraceMethod.from("Stained-Glass"))
        assertNull(OGTraceMethod.from("spaghetti"))
        val scene = OGAiVector.decodeScene("""{"trace":{"method":"spaghetti"},"shapes":[]}""")
        assertEquals("spaghetti", scene.trace!!.method)
        assertNull(scene.trace!!.methodOrNull)
    }

    @Test
    fun sanitizedPreservesSceneMetadataClampsAndDropsBadGradient() {
        val json = """{"version":2,"background":"#111","trace":{"method":"lowpoly"},
          "shapes":[
            {"points":[[0,0],[1,0],[1,1]],"opacity":2.0,"smoothing":5.0,"feather":1.0,"gradient":{"stops":[[0,"#fff"]]}},
            {"points":[[0,0]]}
          ]}"""
        val scene = OGAiVector.decodeScene(json).sanitized()
        assertEquals(1, scene.shapes.size)           // degenerate 1-point region dropped
        assertEquals("#111", scene.background)       // scene metadata preserved through sanitize
        assertNotNull(scene.trace)
        val s = scene.shapes[0]
        assertEquals(1f, s.opacity)                  // 2.0 clamped → 1
        assertEquals(1f, s.smoothing)                // 5.0 clamped → 1
        assertEquals(0.25f, s.feather)               // 1.0 clamped → 0.25
        assertNull(s.gradient)                       // single-stop gradient dropped
    }

    @Test
    fun sceneRoundTripWritesCompatibleForms() {
        val original = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(
                    points = listOf(OGPointSpec(0f, 0f), OGPointSpec(1f, 0f), OGPointSpec(0.5f, 1f)),
                    fill = "#aabbcc",
                    gradient = OGFillSpec(stops = listOf(OGStopSpec(0f, "#000000"), OGStopSpec(1f, "#ffffff"))),
                    opacity = 0.7f,
                ),
            ),
            version = 2,
            background = "#101010",
        )
        val encoded = OGAiVector.encodeScene(original)
        assertTrue(encoded.contains("\"x\""), "points should serialize as {x,y} for back-compat")
        val back = OGAiVector.decodeScene(encoded)
        assertEquals(original.shapes[0].points, back.shapes[0].points)
        assertEquals(original.shapes[0].gradient!!.stops, back.shapes[0].gradient!!.stops)
        assertEquals(0.7f, back.shapes[0].opacity)
        assertEquals("#101010", back.background)
    }

    @Test
    fun gradientBuildsABrush() {
        val g = OGFillSpec(stops = listOf(OGStopSpec(0f, "#3B3F7A"), OGStopSpec(1f, "#FFC97A")))
        assertNotNull(g.toBrush(0f, 0f, 100f, 100f)) // builds (oklab resample + linear gradient) without throwing
    }
}
