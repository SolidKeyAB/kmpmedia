package com.solidkey.painpoints.fx

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OGCrackTest {

    @Test
    fun presets_exist_and_are_case_insensitive() {
        for (n in listOf("ground", "ice", "tile", "glass")) assertNotNull(OGCrack.preset(n), "preset $n")
        assertNotNull(OGCrack.preset("GROUND"))
        assertNotNull(OGCrack.preset("Ice"))
        assertEquals(4, OGCrack.presets.size)
    }

    @Test
    fun codec_round_trips() {
        val back = OGCrack.decodeSpec(OGCrack.encode(OGCrack.ICE))
        assertEquals(OGCrack.ICE, back)
    }

    @Test
    fun decode_bad_input_is_null_not_crash() {
        assertEquals(null, OGCrack.decodeSpecOrNull("no json here at all"))
    }

    @Test
    fun arms_grow_from_origin_for_every_preset() {
        for ((name, spec) in OGCrack.presets) {
            val arms = crackArms(spec, 0.5f, 0.5f)
            assertTrue(arms.size >= spec.arms, "$name: ${arms.size} arms, expected >= ${spec.arms}")
            for (arm in arms) assertTrue(arm.points.size >= 2, "$name: arm with ${arm.points.size} points")
            // Main arms start at the origin (sub-branches start partway along a parent, so check
            // that at least spec.arms of them sit on the origin itself).
            val atOrigin = arms.count { abs(it.points.first().x - 0.5f) < 1e-4f && abs(it.points.first().y - 0.5f) < 1e-4f }
            assertTrue(atOrigin >= spec.arms, "$name: only $atOrigin arms anchored at the origin")
        }
    }

    @Test
    fun arms_are_deterministic() {
        val spec = OGCrack.GLASS
        val a = crackArms(spec, 0.3f, 0.7f)
        val b = crackArms(spec, 0.3f, 0.7f)
        assertEquals(a, b) // same (spec, origin) → identical fracture (cross-platform guarantee)
    }
}
