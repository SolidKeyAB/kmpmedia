package com.solidkey.painpoints.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the **image → vector** half of the AI-interop layer: the multi-region [OGSceneSpec] codec,
 * the [imageToVectorPrompt] contract, [OGImageInfo], and the dependency-free base64 encoder behind
 * [toBase64Png]. The platform `toPngBytes` (Android/iOS) is a thin encoder call verified by
 * compilation; its risky partner — base64 — is pinned here against the RFC 4648 test vectors.
 */
class OGImageVectorTest {

    // --- Scene (image → vector scene) ---------------------------------------------------------

    @Test
    fun decodesSceneFromSchemaJson() {
        val json = """
            {"shapes":[
              {"label":"head","fill":"#E0B080","points":[{"x":0.4,"y":0.1},{"x":0.6,"y":0.1},{"x":0.5,"y":0.4}]},
              {"label":"torso","points":[{"x":0.3,"y":0.4},{"x":0.7,"y":0.4},{"x":0.7,"y":0.9},{"x":0.3,"y":0.9}]}
            ]}
        """.trimIndent()
        val scene = OGAiVector.decodeScene(json)
        assertEquals(2, scene.shapes.size)
        assertEquals("head", scene.shapes[0].label)
        assertEquals("#E0B080", scene.shapes[0].fill)
        assertEquals(3, scene.shapes[0].points.size)
        assertNull(scene.shapes[1].fill)
        assertEquals(listOf("head", "torso"), scene.labels())

        val shapes = scene.toShapes()
        assertEquals(2, shapes.size)
        assertEquals(4, shapes[1].points.size)
    }

    @Test
    fun decodesSceneWrappedInCodeFenceAndProse() {
        val reply = """
            Here are the regions I traced:
            ```json
            {"shapes":[{"label":"ufo","points":[{"x":0.0,"y":0.0},{"x":1.0,"y":0.0},{"x":0.5,"y":1.0}]}]}
            ```
            Let me know if you want more detail.
        """.trimIndent()
        val scene = OGAiVector.decodeScene(reply)
        assertEquals(1, scene.shapes.size)
        assertEquals("ufo", scene.shapes[0].label)
    }

    @Test
    fun sceneRoundTripsThroughJson() {
        val scene = OGSceneSpec.of(
            OGPlacedShapeSpec(listOf(OGPointSpec(0.1f, 0.2f), OGPointSpec(0.9f, 0.3f), OGPointSpec(0.5f, 0.95f)), label = "a"),
            OGPlacedShapeSpec(listOf(OGPointSpec(0f, 0f), OGPointSpec(1f, 1f), OGPointSpec(0f, 1f)), fill = "#112233"),
        )
        val restored = OGAiVector.decodeScene(OGAiVector.encodeScene(scene))
        assertEquals(scene, restored)
    }

    @Test
    fun decodeSceneOrNullSwallowsGarbage() {
        assertNull(OGAiVector.decodeSceneOrNull("not json at all"))
        assertNull(OGAiVector.decodeSceneOrNull(""))
    }

    @Test
    fun decodeSceneShapesGoesStraightToClipShapes() {
        val shapes = OGAiVector.decodeSceneShapes(
            """{"shapes":[{"points":[{"x":0.1,"y":0.1},{"x":0.9,"y":0.1},{"x":0.5,"y":0.9}]}]}"""
        )
        assertEquals(1, shapes.size)
        assertEquals(3, shapes[0].points.size)
    }

    // --- imageToVectorPrompt ------------------------------------------------------------------

    @Test
    fun imagePromptPolygonStatesSchemaAndHint() {
        val p = OGAiVector.imageToVectorPrompt(hint = "trace the cat", target = OGVectorTarget.POLYGON)
        assertTrue(p.contains("\"points\""), "polygon prompt should show the points schema")
        assertTrue(p.contains("NORMALIZED"), "prompt should explain normalized coordinates")
        assertTrue(p.contains("image provided"), "prompt should reference the attached image")
        assertTrue(p.contains("trace the cat"), "prompt should embed the hint")
        assertTrue(!p.contains("\"shapes\""), "polygon prompt should not use the scene schema")
    }

    @Test
    fun imagePromptSceneStatesSchemaAndCap() {
        val p = OGAiVector.imageToVectorPrompt(target = OGVectorTarget.SCENE, maxShapes = 5)
        assertTrue(p.contains("\"shapes\""), "scene prompt should show the shapes schema")
        assertTrue(p.contains("\"label\"") && p.contains("\"fill\""), "scene prompt should mention label + fill")
        assertTrue(p.contains("up to 5"), "scene prompt should state the region cap")
    }

    @Test
    fun imagePromptIncludesAspectWhenImageInfoGiven() {
        val p = OGAiVector.imageToVectorPrompt(imageInfo = OGImageInfo(1600, 900))
        assertTrue(p.contains("1600x900px"), "prompt should state the pixel size")
        assertTrue(p.contains("1.78"), "prompt should state the aspect ratio (16:9 ≈ 1.78): $p")
    }

    @Test
    fun imagePromptOmitsAspectWhenNoImageInfo() {
        val p = OGAiVector.imageToVectorPrompt()
        assertTrue(!p.contains("aspect"), "no aspect line should appear without image info")
    }

    // --- OGImageInfo --------------------------------------------------------------------------

    @Test
    fun imageInfoAspectIsWidthOverHeight() {
        assertEquals(16f / 9f, OGImageInfo(1600, 900).aspect, 0.001f)
        assertEquals(1f, OGImageInfo(0, 0).aspect, 0.001f) // degenerate height -> 1, not NaN/Inf
    }

    // --- base64 (RFC 4648 vectors) ------------------------------------------------------------

    @Test
    fun base64MatchesRfcVectors() {
        assertEquals("", ByteArray(0).encodeBase64())
        assertEquals("TWFu", "Man".encodeToByteArray().encodeBase64())      // no padding
        assertEquals("TWE=", "Ma".encodeToByteArray().encodeBase64())       // one pad
        assertEquals("TQ==", "M".encodeToByteArray().encodeBase64())        // two pads
        assertEquals("Zm9vYmFy", "foobar".encodeToByteArray().encodeBase64())
    }

    @Test
    fun base64HandlesHighBytesAndAllThreeByteGroupBoundaries() {
        // 0x00,0xFF,0x10 exercises the full 0..255 range and the sign-extension trap.
        assertEquals("AP8Q", byteArrayOf(0x00, 0xFF.toByte(), 0x10).encodeBase64())
    }
}
