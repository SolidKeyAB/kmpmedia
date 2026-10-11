package com.solidkey.painpoints.stage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the data-authored interactivity layer: the pure, closed-form reaction math
 * ([reactionTransformAt]) and the [OGStageTrigger] codec round-trip. The player wiring (gestures,
 * frame-clock ticker) is Compose UI and verified on-device; everything deterministic is pinned here.
 */
class OGStageInteractionTest {

    // --- Reaction math: BOUNCE ------------------------------------------------------------------

    @Test
    fun bounceStartsAtRestPopsThenSettles() {
        // At the tap instant the layer is unchanged (spring starts from rest).
        assertEquals(1f, reactionTransformAt(OGTapAction.BOUNCE, 1f, 600L, 0L).scaleMul, 1e-4f)
        // Early in the reaction it has visibly popped above 1 (overshoot).
        val pop = reactionTransformAt(OGTapAction.BOUNCE, 1f, 600L, 100L).scaleMul
        assertTrue(pop > 1.05f, "bounce should overshoot early, was $pop")
        // Once the window elapses it is fully back to rest (identity).
        assertEquals(OGReactionTransform.IDENTITY, reactionTransformAt(OGTapAction.BOUNCE, 1f, 600L, 600L))
        assertEquals(OGReactionTransform.IDENTITY, reactionTransformAt(OGTapAction.BOUNCE, 1f, 600L, 999L))
    }

    @Test
    fun bounceStrengthScalesThePop() {
        val weak = reactionTransformAt(OGTapAction.BOUNCE, 0.5f, 600L, 100L).scaleMul - 1f
        val strong = reactionTransformAt(OGTapAction.BOUNCE, 1.5f, 600L, 100L).scaleMul - 1f
        assertTrue(strong > weak, "stronger tap pops more: strong=$strong weak=$weak")
        // Zero strength is inert at every instant.
        assertEquals(1f, reactionTransformAt(OGTapAction.BOUNCE, 0f, 600L, 100L).scaleMul, 1e-4f)
    }

    // --- Reaction math: SHAKE -------------------------------------------------------------------

    @Test
    fun shakeSwingsSidewaysAndSettles() {
        // Starts at rest, kicks sideways, returns to 0 — never touches scale/rotation.
        assertEquals(0f, reactionTransformAt(OGTapAction.SHAKE, 1f, 600L, 0L).txAdd, 1e-4f)
        val swing = reactionTransformAt(OGTapAction.SHAKE, 1f, 600L, 60L).txAdd
        assertTrue(kotlin.math.abs(swing) > 0.01f, "shake should swing, was $swing")
        val mid = reactionTransformAt(OGTapAction.SHAKE, 1f, 600L, 60L)
        assertEquals(1f, mid.scaleMul, 1e-4f)
        assertEquals(0f, mid.rotationAdd, 1e-4f)
        assertEquals(OGReactionTransform.IDENTITY, reactionTransformAt(OGTapAction.SHAKE, 1f, 600L, 600L))
    }

    // --- Reaction math: SPIN --------------------------------------------------------------------

    @Test
    fun spinTurnsOnceEasingOut() {
        assertEquals(0f, reactionTransformAt(OGTapAction.SPIN, 1f, 600L, 0L).rotationAdd, 1e-4f)
        // Eased-out: past half the window it is already well past 180°.
        val half = reactionTransformAt(OGTapAction.SPIN, 1f, 600L, 300L).rotationAdd
        assertTrue(half in 280f..340f, "spin eases out, half-way was $half")
        // Strength multiplies the number of turns.
        val double = reactionTransformAt(OGTapAction.SPIN, 2f, 600L, 300L).rotationAdd
        assertTrue(double > half * 1.9f, "double strength ~doubles the turn: $double vs $half")
        assertEquals(OGReactionTransform.IDENTITY, reactionTransformAt(OGTapAction.SPIN, 1f, 600L, 600L))
    }

    // --- Navigation actions carry no transform --------------------------------------------------

    @Test
    fun navigationActionsAreVisualNoOps() {
        for (ms in listOf(0L, 100L, 300L, 599L)) {
            assertEquals(OGReactionTransform.IDENTITY, reactionTransformAt(OGTapAction.SEEK, 1f, 600L, ms))
            assertEquals(OGReactionTransform.IDENTITY, reactionTransformAt(OGTapAction.PLAY_PAUSE, 1f, 600L, ms))
        }
    }

    @Test
    fun zeroDurationIsInert() {
        assertEquals(OGReactionTransform.IDENTITY, reactionTransformAt(OGTapAction.BOUNCE, 1f, 0L, 10L))
    }

    // --- Data model + codec round-trip ----------------------------------------------------------

    @Test
    fun layerIsNonInteractiveByDefault() {
        assertNull(OGStages.text("t", "hi").onTap)
        assertNull(OGStages.solid("s", "#000000").onTap)
    }

    @Test
    fun triggersRoundTripThroughTheCodec() {
        val stage = OGStage.of(
            OGStages.solid("bg", "#101018"),
            OGStages.text("title", "TAP ME", onTap = OGStages.tap(OGTapAction.SPIN, strength = 1.5f, durationMs = 800L)),
            OGStages.text("chapter", "SKIP", onTap = OGStages.seekTo(1200L)),
            width = 640, height = 360, durationMs = 2000L,
        )
        val round = OGStages.decode(OGStages.encode(stage))

        val spin = round.layers[1].onTap
        assertNotNull(spin)
        assertEquals(OGTapAction.SPIN, spin.action)
        assertEquals(1.5f, spin.strength, 1e-5f)
        assertEquals(800L, spin.durationMs)

        val seek = round.layers[2].onTap
        assertNotNull(seek)
        assertEquals(OGTapAction.SEEK, seek.action)
        assertEquals(1200L, seek.seekMs)

        // A layer with no handler stays null across the round trip.
        assertNull(round.layers[0].onTap)
    }

    @Test
    fun actionDecodesCaseInsensitively() {
        // The prompt promises lower-case; a model may still emit canonical/upper-case. Both decode.
        val json = """
            { "width":320,"height":320,"durationMs":1000,
              "layers":[ { "id":"a","content":{"type":"solid","color":"#222222"},
                           "onTap":{"action":"SHAKE","strength":2.0} } ] }
        """.trimIndent()
        val stage = OGStages.decode(json)
        val t = stage.layers.single().onTap
        assertNotNull(t)
        assertEquals(OGTapAction.SHAKE, t.action)
        assertEquals(2.0f, t.strength, 1e-5f)
    }

    @Test
    fun tappableHelperAttachesToAnyLayerKind() {
        val base = OGStages.lightning("bolt", com.solidkey.painpoints.fx.OGLightningSpec())
        assertNull(base.onTap)
        val interactive = OGStages.tappable(base, OGStages.playPause())
        assertEquals(OGTapAction.PLAY_PAUSE, interactive.onTap?.action)
        // The helper only adds the handler — content/id are untouched.
        assertEquals(base.id, interactive.id)
        assertEquals(base.content, interactive.content)
    }

    @Test
    fun onTapAloneDoesNotMakeAStillStageLive() {
        // Interactivity must not force a per-frame redraw until a finger lands — a tappable but
        // otherwise still stage is NOT "live" (it renders one frame and waits).
        val stage = OGStage.of(
            OGStages.solid("bg", "#333333", onTap = OGStages.tap(OGTapAction.BOUNCE)),
            durationMs = 1000L,
        )
        assertTrue(!stage.isLive, "a still, tappable stage should not report isLive")
    }
}
