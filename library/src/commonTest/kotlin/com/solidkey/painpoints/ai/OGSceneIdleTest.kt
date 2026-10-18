package com.solidkey.painpoints.ai

import com.solidkey.painpoints.stage.OGStageSvg
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Idle motion is attached by region LABEL (a body breathes / eyes blink / a tail sways) and, for the
 * eyes, also by POSITION: a small catchlight drawn on the eyeball blinks with the eye no matter what
 * the model named it — otherwise the white dot floats over a shut eye. Eyebrows, parts that merely
 * sit elsewhere, and a region's own motion are all left alone.
 */
class OGSceneIdleTest {

    /** A small triangle with bbox `[x, y, x+s, y+s]`, so tests can place parts at distinct locations. */
    private fun triAt(x: Float, y: Float, s: Float = 0.08f) =
        listOf(OGPointSpec(x, y), OGPointSpec(x + s, y), OGPointSpec(x + s / 2f, y + s))

    /** An axis-aligned rectangle ring, so a test can check a motion's pivot against a known bbox. */
    private fun rect(x: Float, y: Float, w: Float, h: Float) =
        listOf(OGPointSpec(x, y), OGPointSpec(x + w, y), OGPointSpec(x + w, y + h), OGPointSpec(x, y + h))

    @Test
    fun attachesIdleMotionByLabel() {
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = triAt(0.40f, 0.50f, 0.20f), label = "body", fill = "#aaaaaa"),
                OGPlacedShapeSpec(points = triAt(0.42f, 0.30f), label = "left_eye", fill = "#111111"),
                OGPlacedShapeSpec(points = triAt(0.44f, 0.32f, 0.03f), label = "pupil_r", fill = "#000000"),
                OGPlacedShapeSpec(points = triAt(0.10f, 0.80f), label = "tail", fill = "#7a4a28"),
                OGPlacedShapeSpec(points = triAt(0.42f, 0.20f), label = "eyebrow", fill = "#000000"),
                OGPlacedShapeSpec(points = triAt(0.00f, 0.90f, 0.30f), label = "ground", fill = "#66aa66"),
            ),
            version = 3,
        )
        val out = OGSceneIdle.animate(scene).shapes.associateBy { it.label }

        assertNotNull(out["body"]?.motion?.scale, "body should breathe (scale)")
        // An honest blink is an eyelid squash (scaleY → ~0 about the eye mid-line), NOT a whole-eye opacity fade.
        assertNotNull(out["left_eye"]?.motion?.scaleY, "eye should blink (eyelid squash)")
        assertNull(out["left_eye"]?.motion?.opacity, "a blink no longer fades the whole eye out")
        assertNotNull(out["pupil_r"]?.motion?.scaleY, "pupil should blink")
        assertNotNull(out["tail"]?.motion?.rotate, "tail should sway (rotate)")
        assertNull(out["eyebrow"]?.motion, "eyebrow should NOT blink")
        assertNull(out["ground"]?.motion, "unmatched label elsewhere stays still")
        assertTrue(OGSceneIdle.animate(scene).isLive, "an animated scene is live")
    }

    @Test
    fun catchlightOnTheEyeBlinksWhateverItsCalled() {
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = triAt(0.42f, 0.30f), label = "left_eye", fill = "#111111"),
                // A tiny white dot sitting inside the eye's bounds, labelled with a NON-eye word.
                OGPlacedShapeSpec(points = triAt(0.45f, 0.32f, 0.015f), label = "highlight", fill = "#ffffff"),
                // A same-kind dot far from any eye must stay fully opaque (no false positive).
                OGPlacedShapeSpec(points = triAt(0.90f, 0.10f, 0.015f), label = "sparkle", fill = "#ffffff"),
            ),
            version = 3,
        )
        val out = OGSceneIdle.animate(scene).shapes.associateBy { it.label }

        assertNotNull(out["highlight"]?.motion?.scaleY, "a catchlight ON the eye must blink with it")
        assertNull(out["sparkle"]?.motion, "a bright dot away from the eyes must NOT blink")
    }

    @Test
    fun catchlightSynonymBlinksByLabelAnywhere() {
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = triAt(0.90f, 0.90f, 0.02f), label = "eye glint", fill = "#ffffff"),
            ),
            version = 3,
        )
        assertNotNull(
            OGSceneIdle.animate(scene).shapes[0].motion?.scaleY,
            "a catchlight synonym blinks by name even when drawn off the eyeball",
        )
    }

    @Test
    fun callerRulesDriveNonEnglishLabels() {
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = triAt(0.42f, 0.30f), label = "göz", fill = "#111111"),     // "eye" (tr)
                OGPlacedShapeSpec(points = triAt(0.10f, 0.80f), label = "kuyruk", fill = "#7a4a28"),   // "tail" (tr)
            ),
            version = 3,
        )
        val out = OGSceneIdle.animate(
            scene,
            rules = mapOf("göz" to OGSceneIdle.Blink, "kuyruk" to OGSceneIdle.TailSway),
        ).shapes.associateBy { it.label }

        assertNotNull(out["göz"]?.motion?.scaleY, "a caller rule blinks a non-English eye label")
        assertNotNull(out["kuyruk"]?.motion?.rotate, "a caller rule sways a non-English tail label")
    }

    // --- Pass 1: the motions are HONEST (they pivot where the thing really hinges) ------------------

    @Test
    fun blinkSquashesToTheEyeMidLineNotFadeOut() {
        val eye = OGPlacedShapeSpec(points = rect(0.40f, 0.30f, 0.10f, 0.06f), label = "eye", fill = "#111111")
        val animated = OGSceneIdle.animate(OGSceneSpec(shapes = listOf(eye), version = 3)).shapes[0]
        val m = assertNotNull(animated.motion, "eye blinks")
        assertNull(m.opacity, "a blink must not fade the whole eye")
        assertEquals(0.5f, m.anchor?.y, "the blink pivots at the eye's MID-line so every eye part shuts onto it")

        // At the shut point the eye collapses vertically to a thin line about its CENTRE (top drops, bottom
        // rises) — so a pupil whose own top edge differs from the white's still shuts onto the same line.
        val closed = animated.animatedAt(1.5f)
        val topBefore = eye.points.minOf { it.y }; val botBefore = eye.points.maxOf { it.y }
        val topAfter = closed.points.minOf { it.y }; val botAfter = closed.points.maxOf { it.y }
        val hBefore = botBefore - topBefore
        val hAfter = botAfter - topAfter
        assertTrue(hAfter < hBefore * 0.3f, "the shut eye is a thin line, not a faded oval")
        assertTrue(
            kotlin.math.abs((topAfter + botAfter) / 2f - (topBefore + botBefore) / 2f) < 1e-3f,
            "the eye's mid-line holds (it collapses inward, not pinned to the top)",
        )
        assertTrue(topAfter > topBefore, "the top edge drops inward (not pinned at the socket top)")
        assertEquals(eye.opacity, closed.opacity, "opacity is untouched by the blink")
    }

    @Test
    fun blinkCollapsesEveryEyePartOntoTheSharedEyeLine() {
        // The eyeball WHITE (centre y=0.36) and a PUPIL drawn on it (centre y=0.37) have DIFFERENT
        // centres. If each shut about its own mid-line they would land on different lines and the pupil
        // would poke out below the closed white. They must instead collapse onto ONE shared line.
        val white = OGPlacedShapeSpec(points = rect(0.35f, 0.29f, 0.12f, 0.14f), label = "eye_l", fill = "#ffffff")
        val pupil = OGPlacedShapeSpec(points = rect(0.38f, 0.34f, 0.06f, 0.06f), label = "pupil_l", fill = "#2a2a2a")
        val out = OGSceneIdle.animate(OGSceneSpec(shapes = listOf(white, pupil), version = 3)).shapes

        // Both parts carry a pure vertical squash (blink), re-rooted so their pivots share one line.
        val whiteA = out.first { it.label == "eye_l" }
        val pupilA = out.first { it.label == "pupil_l" }
        assertNotNull(whiteA.motion?.scaleY, "the white blinks")
        assertNotNull(pupilA.motion?.scaleY, "the pupil blinks")

        // At full close both collapse to a thin line whose centres coincide (the pupil no longer drifts
        // below the white) and whose y-ranges OVERLAP — i.e. the pupil stays inside the shut white.
        val wc = whiteA.animatedAt(1.5f); val pc = pupilA.animatedAt(1.5f)
        val wMid = (wc.points.minOf { it.y } + wc.points.maxOf { it.y }) / 2f
        val pMid = (pc.points.minOf { it.y } + pc.points.maxOf { it.y }) / 2f
        assertEquals(wMid, pMid, 1e-3f, "white and pupil shut onto the SAME line (concentric, no drift)")
        assertTrue(
            pc.points.minOf { it.y } <= wc.points.maxOf { it.y } &&
                pc.points.maxOf { it.y } >= wc.points.minOf { it.y },
            "the shut pupil stays within the shut white (no dark speck below the lid)",
        )
    }

    @Test
    fun breatheRisesFromItsSeatedBase() {
        val body = OGPlacedShapeSpec(points = rect(0.40f, 0.40f, 0.20f, 0.30f), label = "body", fill = "#aaaaaa")
        val m = assertNotNull(OGSceneIdle.animate(OGSceneSpec(shapes = listOf(body), version = 3)).shapes[0].motion)
        assertNotNull(m.scaleY, "breathe swells vertically, not just uniformly")
        assertEquals(1f, m.anchor?.y, "breathe is anchored at the seated BASE (bottom), not the centroid")
    }

    @Test
    fun tailSwayHingesAtTheBodyNearEnd() {
        // Body mass on the RIGHT, tail reaching LEFT and OVERLAPPING the body (so it is attached) → the
        // wag must pivot at the tail's RIGHT (body-near) edge.
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = rect(0.60f, 0.40f, 0.30f, 0.30f), label = "body", fill = "#aaaaaa"),
                OGPlacedShapeSpec(points = rect(0.42f, 0.50f, 0.20f, 0.05f), label = "tail", fill = "#7a4a28"),
            ),
            version = 3,
        )
        val tail = OGSceneIdle.animate(scene).shapes.first { it.label == "tail" }
        assertEquals(1f, tail.motion?.anchor?.x ?: 0f, 1e-3f, "the tail hinges at its body-near (right) edge")
        assertEquals(0.5f, tail.motion?.anchor?.y ?: 0f, 1e-3f, "mid-height on that edge (a hinge, not a corner)")
    }

    @Test
    fun declaredTailSwayIsAlsoRootedAtTheBodyNearEnd() {
        // A model-DECLARED sway (data-motion="sway") lands with the preset's generic top anchor. An ATTACHED
        // tail must still be re-rooted at its body-near edge — the pivot is the whole point of a sway, and a
        // model shouldn't have to compute it. Body on the RIGHT, tail overlapping it → hinges at its RIGHT.
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = rect(0.60f, 0.40f, 0.30f, 0.30f), label = "body", fill = "#aaaaaa"),
                OGPlacedShapeSpec(
                    points = rect(0.42f, 0.50f, 0.20f, 0.05f), label = "tail", fill = "#7a4a28",
                    motion = OGSceneIdle.Sway, // declared; carries the default top-centre anchor
                ),
            ),
            version = 3,
        )
        val tail = OGSceneIdle.animate(scene).shapes.first { it.label == "tail" }
        assertEquals(1f, tail.motion?.anchor?.x ?: 0f, 1e-3f, "a declared tail sway is re-rooted at its body-near (right) edge")
        assertEquals(0.5f, tail.motion?.anchor?.y ?: 0f, 1e-3f, "mid-height on that edge (a hinge, not the top corner)")
    }

    @Test
    fun detachedWagFloatsInsteadOfHingingOnAir() {
        // A swaying part that touches NOTHING has no joint, so hinging it would lie. It must be demoted to
        // a gentle float: pivot about its own centre (no faked body hinge), a softened rotation, and drift.
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = rect(0.70f, 0.70f, 0.25f, 0.25f), label = "body", fill = "#aaaaaa"),
                OGPlacedShapeSpec(
                    points = rect(0.05f, 0.05f, 0.12f, 0.05f), label = "tail", fill = "#7a4a28",
                    motion = OGSceneIdle.Sway, // declared sway, but drawn far off the body
                ),
            ),
            version = 3,
        )
        val tail = OGSceneIdle.animate(scene).shapes.first { it.label == "tail" }
        val m = assertNotNull(tail.motion, "a detached wag still moves")
        assertNull(m.anchor, "a floating part pivots about its own centre, not a faked body hinge")
        assertNotNull(m.translate, "it drifts gently instead of hinging")
        assertTrue(
            kotlin.math.abs(m.rotate!!.to) < kotlin.math.abs(OGSceneIdle.Sway.rotate!!.to),
            "the rotation is softened to a faint float",
        )
    }

    @Test
    fun aFullBleedBackdropDoesNotCountAsAttachment() {
        // A full-bleed background touches every part. It must be excluded, or nothing would ever read as
        // detached: the tail near the sky's corner but far from the real body must still float, not hinge.
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = rect(0f, 0f, 1f, 1f), label = "sky", fill = "#bfe3ff"),
                OGPlacedShapeSpec(points = rect(0.70f, 0.70f, 0.25f, 0.25f), label = "body", fill = "#aaaaaa"),
                OGPlacedShapeSpec(
                    points = rect(0.01f, 0.01f, 0.12f, 0.05f), label = "tail", fill = "#7a4a28",
                    motion = OGSceneIdle.Sway, // hugs the sky's corner (within eps of the backdrop edge)
                ),
            ),
            version = 3,
        )
        val tail = OGSceneIdle.animate(scene).shapes.first { it.label == "tail" }
        assertNull(tail.motion?.anchor, "the full-bleed sky is excluded, so the far tail is still detached → float")
        assertNotNull(tail.motion?.translate, "and it drifts")
    }

    @Test
    fun aWagTouchingOnlyAlongAnEdgeStillHinges() {
        // The join crosses an EDGE of the wag, not any corner: the fin's apex pokes ABOVE the body and its
        // two base corners sit BELOW the body's top edge, so NO vertex is within eps of the outline — only
        // the two side-edge midpoints (at y=0.40, on the body's top edge) are. A vertex-only test demoted
        // this to a float (the fish-dorsal bug); sampling edge midpoints must now hinge it at its root.
        val fin = listOf(
            OGPointSpec(0.60f, 0.33f), // apex, 0.07 ABOVE the body top (outside) → >eps
            OGPointSpec(0.70f, 0.47f), // base-right, 0.07 BELOW the body top (inside) → >eps
            OGPointSpec(0.50f, 0.47f), // base-left,  0.07 BELOW the body top (inside) → >eps
        )
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = rect(0.40f, 0.40f, 0.40f, 0.40f), label = "body", fill = "#aaaaaa"),
                OGPlacedShapeSpec(points = fin, label = "fin", fill = "#3f9cbc", motion = OGSceneIdle.Sway),
            ),
            version = 3,
        )
        val m = assertNotNull(OGSceneIdle.animate(scene).shapes.first { it.label == "fin" }.motion)
        assertNotNull(m.anchor, "a part meeting the body along an edge hinges at that join")
        assertNull(m.translate, "it must NOT demote to a float — that was the dorsal-fin bug")
    }

    @Test
    fun anExplicitTailPivotIsRespected() {
        // If the author pinned a specific (non-default) pivot, honour it — don't override with geometry.
        val pinned = OGSceneIdle.Sway.copy(anchor = OGPointSpec(0.25f, 0.5f))
        val scene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(points = rect(0.60f, 0.40f, 0.30f, 0.30f), label = "body", fill = "#aaaaaa"),
                OGPlacedShapeSpec(points = rect(0.10f, 0.50f, 0.20f, 0.05f), label = "tail", fill = "#7a4a28", motion = pinned),
            ),
            version = 3,
        )
        val tail = OGSceneIdle.animate(scene).shapes.first { it.label == "tail" }
        assertEquals(0.25f, tail.motion?.anchor?.x, "an explicit, non-default tail pivot is left untouched")
    }

    @Test
    fun motionNamedResolvesALanguageIndependentVocabulary() {
        assertNotNull(OGSceneIdle.motionNamed("blink"), "blink is a known motion keyword")
        assertNotNull(OGSceneIdle.motionNamed("BREATHE"), "resolution is case-insensitive")
        assertNotNull(OGSceneIdle.motionNamed(" wave "), "and trims whitespace")
        assertNotNull(OGSceneIdle.motionNamed("pulse"))
        assertNull(OGSceneIdle.motionNamed("galumph"), "an unknown keyword names nothing")
        assertNull(OGSceneIdle.motionNamed(null), "null names nothing")
        val wave = assertNotNull(OGSceneIdle.motionNamed("wave")?.rotate)
        assertTrue(
            kotlin.math.abs(wave.to) > kotlin.math.abs(OGSceneIdle.Sway.rotate!!.to),
            "a wave is a bigger swing than an idle sway",
        )
    }

    @Test
    fun idleLoopDividesCleanlySoTheFigureNeverSnapsBackAtTheWrap() {
        // The player loops the idle stage by resetting its clock to 0 at OGStageSvg.IDLE_LOOP_MS. A
        // `reverse` motion is only back at its rest pose at whole multiples of its FULL period (2×duration).
        // If a preset's period does not divide the loop, the wrap lands mid-motion and the figure visibly
        // jerks once per loop — the breathe/sway snap Özge caught. Guard every reverse preset here.
        val loop = OGStageSvg.IDLE_LOOP_MS
        val presets = mapOf(
            "Breathe" to OGSceneIdle.Breathe, "Sway" to OGSceneIdle.Sway, "Blink" to OGSceneIdle.Blink,
            "Wave" to OGSceneIdle.Wave, "Bob" to OGSceneIdle.Bob, "Nod" to OGSceneIdle.Nod, "Pulse" to OGSceneIdle.Pulse,
        )
        for ((name, m) in presets) {
            assertEquals("reverse", m.mode, "$name is a ping-pong idle preset")
            val periodMs = (m.duration * 2f * 1000f).roundToLong()
            assertEquals(
                0L, loop % periodMs,
                "$name full period ${periodMs}ms must divide the ${loop}ms idle loop, else it snaps at the wrap",
            )
        }
    }

    @Test
    fun preservesExistingMotion() {
        // A deliberately authored motion (here with an explicit pivot) is kept verbatim: it is not replaced
        // by the "body" idle heuristic, and the explicit anchor exempts it from the wag safety net too.
        val own = OGRegionMotion(rotate = OGMotionScalar(0f, 90f), anchor = OGPointSpec(0.5f, 0.5f))
        val scene = OGSceneSpec(
            shapes = listOf(OGPlacedShapeSpec(points = triAt(0.4f, 0.4f), label = "body", fill = "#aaaaaa", motion = own)),
            version = 3,
        )
        val out = OGSceneIdle.animate(scene).shapes[0].motion
        assertEquals(90f, out?.rotate?.to, "a region's own motion wins over the auto-heuristic")
        assertEquals(0.5f, out?.anchor?.y, "and an explicitly pinned pivot is left untouched")
    }
}
