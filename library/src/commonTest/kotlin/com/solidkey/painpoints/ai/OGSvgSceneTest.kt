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

    // --- Pass 2: the MODEL declares motion with data-motion (kills the English-keyword ceiling) -----

    @Test
    fun dataMotionDeclaresMotionInAnyLanguage() {
        // Turkish ids ("göz" = eye, "gövde" = body, "kuyruk" = tail) that the English heuristic would
        // never match — but data-motion makes them move anyway.
        val svg = """
            <svg viewBox="0 0 100 100">
              <rect id="gövde" x="30" y="40" width="40" height="40" fill="#7a4a28" data-motion="breathe"/>
              <circle id="göz" cx="50" cy="30" r="8" fill="#111111" data-motion="blink"/>
              <rect id="kuyruk" x="5" y="60" width="20" height="6" fill="#7a4a28" data-motion="sway"/>
              <rect id="arka" x="80" y="80" width="10" height="10" fill="#555555"/>
            </svg>
        """.trimIndent()
        val byLabel = OGSvgScene.toScene(svg).shapes.associateBy { it.label }

        assertNotNull(byLabel["göz"]?.motion?.scaleY, "a blink-tagged eye squashes (scaleY) in any language")
        assertNotNull(byLabel["gövde"]?.motion?.scale, "a breathe-tagged body breathes")
        assertNotNull(byLabel["kuyruk"]?.motion?.rotate, "a sway-tagged tail wags")
        assertEquals(null, byLabel["arka"]?.motion, "an untagged part stays still")
    }

    @Test
    fun dataAnchorOverridesThePivotAndDataDurTheBeat() {
        val svg = """
            <svg viewBox="0 0 100 100">
              <rect id="tail" x="5" y="50" width="20" height="6" fill="#7a4a28"
                    data-motion="sway" data-anchor="right" data-dur="3"/>
            </svg>
        """.trimIndent()
        val m = assertNotNull(OGSvgScene.toScene(svg).shapes.first { it.label == "tail" }.motion)
        assertEquals(1f, m.anchor?.x, "data-anchor=right pivots on the right edge")
        assertEquals(0.5f, m.anchor?.y)
        assertEquals(3f, m.duration, "data-dur sets the beat")
    }

    @Test
    fun dataMotionOnAGroupCascadesAndAChildOverrides() {
        val svg = """
            <svg viewBox="0 0 100 100">
              <g id="face" data-motion="blink">
                <circle id="eye_l" cx="35" cy="40" r="6" fill="#111"/>
                <circle id="eye_r" cx="65" cy="40" r="6" fill="#111"/>
                <rect id="mouth" x="40" y="60" width="20" height="6" fill="#900" data-motion="pulse"/>
              </g>
            </svg>
        """.trimIndent()
        val byLabel = OGSvgScene.toScene(svg).shapes.associateBy { it.label }
        assertNotNull(byLabel["eye_l"]?.motion?.scaleY, "a child inherits the group's blink")
        assertNotNull(byLabel["eye_r"]?.motion?.scaleY)
        // the mouth overrides with its own pulse (uniform scale, no scaleY)
        assertNotNull(byLabel["mouth"]?.motion?.scale, "a child's own data-motion wins over the group's")
        assertEquals(null, byLabel["mouth"]?.motion?.scaleY, "pulse is a uniform scale, not a squash")
    }

    @Test
    fun unknownDataMotionIsIgnored() {
        val svg = """
            <svg viewBox="0 0 100 100">
              <rect id="x" x="10" y="10" width="20" height="20" fill="#111" data-motion="explode"/>
            </svg>
        """.trimIndent()
        assertEquals(null, OGSvgScene.toScene(svg).shapes.first().motion, "an unknown keyword adds no motion")
    }

    @Test
    fun idleKeepsModelDeclaredMotionAndStillAutoAnimatesTheRest() {
        // A model declared a blink on a non-English eye; the body has no tag but an English label.
        val svg = """
            <svg viewBox="0 0 100 100">
              <rect id="body" x="30" y="40" width="40" height="40" fill="#7a4a28"/>
              <circle id="göz" cx="50" cy="30" r="8" fill="#111" data-motion="blink"/>
            </svg>
        """.trimIndent()
        val scene = OGSvgScene.toScene(svg)
        val idled = OGSceneIdle.animate(scene).shapes.associateBy { it.label }
        assertNotNull(idled["göz"]?.motion?.scaleY, "idle must preserve the model-declared blink")
        assertNotNull(idled["body"]?.motion?.scale, "and still auto-breathe an untagged English body")
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

    @Test
    fun groupShapesShareOneRigidPivot() {
        // A multi-shape limb wrapped in a <g data-motion=…> must rotate about ONE shared pivot (the
        // group's combined-bbox anchor, in scene space), so the pieces swing rigidly instead of each
        // spinning about its own centre and shearing. upper+fore span x[24..46] y[24..60]; a "bottom"
        // anchor = combined bottom-centre (35,60) -> normalized (0.35, 0.60) at viewBox 0..100.
        val svg = """
            <svg viewBox="0 0 100 100">
              <g id="arm" data-motion="wave" data-anchor="bottom">
                <polygon id="upper" points="40,60 46,60 44,40 38,40" fill="#aa0000"/>
                <polygon id="fore"  points="38,40 44,40 30,24 24,28" fill="#bb0000"/>
              </g>
              <polygon id="solo" points="10,60 16,60 14,40 8,40" fill="#00aa00" data-motion="wave" data-anchor="bottom"/>
            </svg>
        """.trimIndent()
        val byLabel = OGSvgScene.toScene(svg).shapes.associateBy { it.label }

        val upper = assertNotNull(byLabel["upper"]?.motion?.pivotAbs, "grouped piece must get a shared pivot")
        val fore = assertNotNull(byLabel["fore"]?.motion?.pivotAbs, "grouped piece must get a shared pivot")
        assertEquals(upper.x, fore.x, 1e-5f, "both grouped pieces rotate about the SAME x")
        assertEquals(upper.y, fore.y, 1e-5f, "both grouped pieces rotate about the SAME y")
        assertEquals(0.35f, upper.x, 1e-4f, "shared pivot = combined-bbox bottom-centre x")
        assertEquals(0.60f, upper.y, 1e-4f, "shared pivot = combined-bbox bottom-centre y")
        assertNotNull(byLabel["upper"]?.motion?.rotate, "a wave still rotates")

        // A lone shape with its OWN data-motion keeps per-shape pivoting (no shared pivot forced on it).
        assertEquals(null, byLabel["solo"]?.motion?.pivotAbs, "a single shape pivots about its own box")

        // The shared pivot must SURVIVE the idle pass (its wag re-root is skipped once a pivot is baked).
        val idled = OGSceneIdle.animate(OGSvgScene.toScene(svg)).shapes.associateBy { it.label }
        assertNotNull(idled["upper"]?.motion?.pivotAbs, "idle must preserve a baked group pivot")
    }
}
