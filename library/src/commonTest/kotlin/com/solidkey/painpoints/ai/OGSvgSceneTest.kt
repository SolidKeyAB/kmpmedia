package com.solidkey.painpoints.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The SVG front-end for the scene IR: a model-authored SVG compiles into labeled [OGPlacedShapeSpec]
 * regions in normalized `0..1` space, ready to animate/tap by name. Verifies id->label, fill, viewBox
 * normalization and multi-subpath splitting.
 */
class OGSvgSceneTest {

    private val svg = """
        <svg viewBox="0 0 100 100">
          <rect id="bg" x="0" y="0" width="100" height="100" fill="#cfeecf"/>
          <path id="body" fill="#7a4a28" d="M30 40 L70 40 L70 80 L30 80 Z"/>
          <circle id="eye" cx="50" cy="30" r="10" fill="#111111"/>
          <path id="pair" fill="#ff0000" d="M10 10 L20 10 L20 20 Z M80 80 L90 80 L90 90 Z"/>
        </svg>
    """.trimIndent()

    @Test
    fun compilesSvgIntoLabeledNormalizedRegions() {
        val scene = OGSvgScene.toScene(svg)
        val byLabel = scene.shapes.associateBy { it.label }

        // bg + body + eye + (pair split into two subpaths) = 5 regions
        assertEquals(5, scene.shapes.size, "labels=${scene.shapes.map { it.label }}")
        assertTrue(byLabel.keys.containsAll(setOf("bg", "body", "eye", "pair_1", "pair_2")))

        // Labels carry the SVG id; fills survive as #rrggbb swatches.
        assertEquals("#7a4a28", byLabel["body"]?.fill)
        assertEquals("#111111", byLabel["eye"]?.fill)
        assertEquals("#ff0000", byLabel["pair_1"]?.fill)

        // viewBox 0..100 normalizes to 0..1: the body rect's first corner (30,40) -> (0.3,0.4).
        val body = assertNotNull(byLabel["body"])
        assertEquals(4, body.points.size)
        assertEquals(0.30f, body.points[0].x, 1e-4f)
        assertEquals(0.40f, body.points[0].y, 1e-4f)

        // The circle sampled into a closed ring of points, all inside the box.
        val eye = assertNotNull(byLabel["eye"])
        assertTrue(eye.points.size >= 8)

        // Every coordinate is finite and within the normalized box.
        for (s in scene.shapes) for (p in s.points) {
            assertTrue(p.x in -0.001f..1.001f && p.y in -0.001f..1.001f, "oob ${s.label} $p")
        }
    }

    @Test
    fun emptyOrInvalidSvgIsHandledGracefully() {
        assertTrue(OGSvgScene.toScene("not svg at all").shapes.isEmpty())
    }

    @Test
    fun hardenedGradientOpacityAndArc() {
        val svg = """
            <svg viewBox="0 0 100 100">
              <defs>
                <linearGradient id="sky">
                  <stop offset="0%" stop-color="#ff0000"/>
                  <stop offset="100%" stop-color="#0000ff"/>
                </linearGradient>
              </defs>
              <rect id="bg" x="0" y="0" width="100" height="100" fill="url(#sky)"/>
              <path id="dot" fill="#00ff00" fill-opacity="0.5" d="M40 40 L60 40 L60 60 L40 60 Z"/>
              <path id="dome" fill="#123456" d="M10 60 A40 40 0 0 1 90 60 Z"/>
            </svg>
        """.trimIndent()
        val byLabel = OGSvgScene.toScene(svg).shapes.associateBy { it.label }

        // Gradient fill compiled into an OGFillSpec with its two stops (not just a flat swatch).
        val bg = assertNotNull(byLabel["bg"])
        val grad = assertNotNull(bg.gradient, "expected a gradient on bg")
        assertEquals(2, grad.stops.size)

        // fill-opacity folds into the region opacity.
        assertEquals(0.5f, byLabel["dot"]?.opacity ?: 1f, 1e-3f)

        // The arc (A command) is sampled into many points, not a single straight chord.
        val dome = assertNotNull(byLabel["dome"])
        assertTrue(dome.points.size > 8, "arc should sample to a curve, got ${dome.points.size}")
    }

    @Test
    fun gradientAcceptsDecimalOffsetsStyleStopColorAndAnyOrder() {
        // Real/LLM-authored SVG: decimal (not just %) offsets, stop-color via `style=`, a stop-opacity,
        // and attributes in mixed order. All three stops must survive.
        val svg = """
            <svg viewBox="0 0 100 100">
              <defs>
                <linearGradient id="g">
                  <stop offset="0" style="stop-color:#ff0000"/>
                  <stop stop-color="#00ff00" offset="0.5" stop-opacity="0.5"/>
                  <stop style="stop-color:#0000ff;stop-opacity:1" offset="1"/>
                </linearGradient>
              </defs>
              <rect id="bg" x="0" y="0" width="100" height="100" fill="url(#g)"/>
            </svg>
        """.trimIndent()
        val bg = assertNotNull(OGSvgScene.toScene(svg).shapes.associateBy { it.label }["bg"])
        val grad = assertNotNull(bg.gradient, "decimal-offset / style-stop-color gradient should parse")
        assertEquals(3, grad.stops.size, "stops=${grad.stops}")
    }

    @Test
    fun roundedRectCornersBecomeCurves() {
        // A model's "rounded rectangle" limb (rx) should keep soft corners (many sampled points),
        // while a plain rect stays a 4-corner box.
        val svg = """
            <svg viewBox="0 0 100 100">
              <rect id="arm" x="10" y="10" width="60" height="20" rx="10" fill="#7a4a28"/>
              <rect id="bar" x="10" y="40" width="60" height="20" fill="#7a4a28"/>
            </svg>
        """.trimIndent()
        val byLabel = OGSvgScene.toScene(svg).shapes.associateBy { it.label }
        assertTrue((byLabel["arm"]?.points?.size ?: 0) > 8, "rounded rect should curve, got ${byLabel["arm"]?.points?.size}")
        assertEquals(4, byLabel["bar"]?.points?.size, "plain rect stays a box")
    }
}
