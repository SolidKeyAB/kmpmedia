package com.solidkey.painpoints.motion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OGDynamicsTest {

    @Test
    fun spec_roundTrips_withNestedSpring() {
        val spec = OGDynamicsSpec(
            name = "walk",
            spring = OGSpringSpec(stiffness = 90f, dampingRatio = 0.7f),
            followLinks = 3,
            squash = 0.2f,
            sway = 0.1f,
            easing = "anticipate",
        )
        val decoded = OGDynamics.decodeSpec(OGDynamics.encode(spec))
        assertEquals(spec, decoded)
    }

    @Test
    fun decode_tolerantAndNullSafe() {
        assertNull(OGDynamics.decodeSpecOrNull("not json"))
        val def = OGDynamics.decodeSpecOrNull("{}")
        assertNotNull(def)
        assertEquals(OGDynamicsSpec(), def)
    }

    @Test
    fun decode_tolerantOfFencesAndProse() {
        val reply = """
            Sure, here's a bouncy feel:
            ```json
            {"spring":{"stiffness":240,"dampingRatio":0.35},"squash":0.3,"easing":"overshoot"}
            ```
        """.trimIndent()
        val spec = OGDynamics.decodeSpec(reply)
        assertEquals(240f, spec.spring.stiffness)
        assertEquals(0.35f, spec.spring.dampingRatio)
        assertEquals(OGEasing.OVERSHOOT, spec.easingMode())
    }

    @Test
    fun presets_lookupIsCaseInsensitive() {
        assertNotNull(OGDynamics.preset("bouncy"))
        assertNotNull(OGDynamics.preset("BOUNCY"))
        assertNull(OGDynamics.preset("nope"))
        assertTrue(OGDynamics.presets.containsKey("heavy"))
    }

    @Test
    fun prompt_describesTheSchema() {
        val p = OGDynamics.dynamicsPrompt("a heavy bouncy walk that settles slowly")
        assertTrue(p.contains("stiffness") && p.contains("dampingRatio"))
        assertTrue(p.contains("a heavy bouncy walk that settles slowly"))
    }

    @Test
    fun easingMode_resolvesName() {
        assertEquals(OGEasing.ANTICIPATE, OGDynamicsSpec(easing = "anticipate").easingMode())
        assertEquals(OGEasing.EASE_OUT, OGDynamicsSpec().easingMode())
    }
}
