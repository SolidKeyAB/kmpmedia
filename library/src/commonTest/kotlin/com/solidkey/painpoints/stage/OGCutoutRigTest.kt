package com.solidkey.painpoints.stage

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Tests for the cut-out puppet rig → [OGStage] bake (the nexum "cutoutRig" capability + its offline floor). */
class OGCutoutRigTest {

    private fun superxRig() = OGCutoutRig(
        width = 1024, height = 572, durationMs = 2600L, fps = 60,
        layers = listOf(
            OGCutoutLayer(id = "fig", role = "base"),
            OGCutoutLayer(id = "arm_r", role = "mover", pivotX = 0.43f, pivotY = 0.26f, swingDeg = 12f),
            OGCutoutLayer(id = "leg_l", role = "mover", pivotX = 0.47f, pivotY = 0.70f, swingDeg = 8f),
        ),
    )

    @Test
    fun rig_round_trips_through_codec() {
        val rig = superxRig()
        val decoded = OGCutoutRigs.decode(OGCutoutRigs.encode(rig))
        assertEquals(rig, decoded)
    }

    @Test
    fun codec_is_lenient_about_prose_and_role_case() {
        // extractJson strips the prose + fences; isBase ignores the role's letter-case.
        val wrapped = "Here is the rig:\n```json\n" +
            "{\"width\":100,\"height\":100,\"layers\":[{\"id\":\"b\",\"role\":\"BASE\"}]}\n```"
        val rig = OGCutoutRigs.decodeOrNull(wrapped)
        assertNotNull(rig)
        assertEquals(1, rig.layers.size)
        assertTrue(rig.layers[0].isBase)
    }

    @Test
    fun bake_produces_a_layer_per_part_with_matching_canvas() {
        val stage = OGOfflineCutoutRigAuthor().bake(superxRig())
        assertEquals(3, stage.layers.size)
        assertEquals(1024, stage.width)
        assertEquals(572, stage.height)
        assertEquals(2600L, stage.durationMs)
        assertEquals(listOf("fig", "arm_r", "leg_l"), stage.layers.map { it.id })
    }

    @Test
    fun base_stays_still_and_movers_swing_about_their_seated_joint() {
        val stage = OGOfflineCutoutRigAuthor().bake(superxRig())
        val base = stage.layers.first { it.id == "fig" }
        val arm = stage.layers.first { it.id == "arm_r" }

        // Base: no motion, seated at the centre (full-bleed).
        assertTrue(base.rotationDeg.keyframes.isEmpty(), "base must not rotate")
        assertEquals(0f, base.x.valueAt(0L))
        assertEquals(0f, base.y.valueAt(0L))

        // Mover: the centred joint is translated to (pivot − 0.5), and it rotates.
        assertEquals(0.43f - 0.5f, arm.x.valueAt(0L), 1e-4f)
        assertEquals(0.26f - 0.5f, arm.y.valueAt(0L), 1e-4f)
        assertTrue(arm.rotationDeg.keyframes.isNotEmpty(), "a mover must swing")
        // rest at the loop ends, off-rest somewhere in the middle
        assertEquals(0f, arm.rotationDeg.valueAt(0L), 1e-3f)
        assertEquals(0f, arm.rotationDeg.valueAt(2600L), 1e-3f)
        val mid = (0L..2600L step 130L).maxOf { abs(arm.rotationDeg.valueAt(it)) }
        assertTrue(mid > 1f, "the arm should visibly swing (peak was $mid deg)")
    }

    @Test
    fun baked_stage_is_live_and_movers_are_tappable() {
        val stage = OGOfflineCutoutRigAuthor().bake(superxRig())
        assertTrue(stage.isLive, "a puppet with swinging limbs must be live")
        assertNotNull(stage.layers.first { it.id == "arm_r" }.onTap, "movers should be pokeable")
    }

    @Test
    fun capability_prompt_carries_the_rig_and_decodes_a_stage() {
        val rig = superxRig()
        assertEquals("cutoutRig", OGCutoutRigCapability.id)
        val prompt = OGCutoutRigCapability.promptFor(rig)
        assertTrue(prompt.contains("arm_r"), "prompt should embed the rig")
        // a real OGStage reply decodes…
        val baked = OGOfflineCutoutRigAuthor().bake(rig)
        val reply = "Sure, here you go:\n" + OGStages.encode(baked)
        val decoded = OGCutoutRigCapability.decode(reply)
        assertNotNull(decoded)
        assertEquals(baked.layers.size, decoded.layers.size)
    }

    @Test
    fun resilient_helper_prefers_agent_then_floor() {
        val rig = superxRig()
        // agent gives junk → floor bakes
        val viaFloor = bakeCutoutRig(rig, agentReply = "sorry, no idea")
        assertEquals(3, viaFloor.layers.size)
        // agent gives a valid stage → used as-is
        val agentStage = OGStage.of(OGStages.solid("bg", "#000000"), width = 10, height = 10)
        val viaAgent = bakeCutoutRig(rig, agentReply = OGStages.encode(agentStage))
        assertEquals(1, viaAgent.layers.size)
        assertEquals(10, viaAgent.width)
    }
}
