package com.solidkey.painpoints.gesture

import com.solidkey.painpoints.shape.OGPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pure-logic tests for the [Modifier.ogButton] press math + the [OGHitArea] drawing outline. */
class OGButtonTest {

    private val eps = 1e-4f

    @Test
    fun scaleEffect_lerpsFromRestToPressed() {
        val e = OGPressEffect.Scale(scale = 0.9f)
        assertEquals(1f, e.transformAt(0f).scale, eps)       // rest = identity
        assertEquals(0.9f, e.transformAt(1f).scale, eps)     // fully pressed = target
        assertEquals(0.95f, e.transformAt(0.5f).scale, eps)  // halfway
        // Scale never touches alpha / translation.
        assertEquals(1f, e.transformAt(1f).alpha, eps)
        assertEquals(0f, e.transformAt(1f).translateFraction, eps)
    }

    @Test
    fun dimEffect_lerpsAlphaOnly() {
        val e = OGPressEffect.Dim(alpha = 0.6f)
        assertEquals(1f, e.transformAt(0f).alpha, eps)
        assertEquals(0.6f, e.transformAt(1f).alpha, eps)
        assertEquals(1f, e.transformAt(1f).scale, eps)
        assertEquals(0f, e.transformAt(1f).translateFraction, eps)
    }

    @Test
    fun brutalistEffect_drivesTranslationFractionOnly() {
        val e = OGPressEffect.Brutalist()
        val t = e.transformAt(0.5f)
        assertEquals(0.5f, t.translateFraction, eps)
        assertEquals(1f, t.scale, eps)
        assertEquals(1f, t.alpha, eps)
    }

    @Test
    fun noneEffect_isIdentityAtAnyProgress() {
        val t = OGPressEffect.None.transformAt(1f)
        assertEquals(1f, t.scale, eps)
        assertEquals(1f, t.alpha, eps)
        assertEquals(0f, t.translateFraction, eps)
    }

    @Test
    fun progressIsClampedToUnitRange() {
        val e = OGPressEffect.Scale(scale = 0.8f)
        assertEquals(1f, e.transformAt(-5f).scale, eps)  // below 0 clamps to rest
        assertEquals(0.8f, e.transformAt(9f).scale, eps) // above 1 clamps to pressed
    }

    @Test
    fun rectOutline_isTheFourCorners() {
        val o = OGHitArea.RECT.outlineNormalized()
        assertEquals(4, o.size)
        assertTrue(o.contains(OGPoint(0f, 0f)))
        assertTrue(o.contains(OGPoint(1f, 1f)))
    }

    @Test
    fun circleOutline_isSampledOnTheUnitCircle() {
        val o = OGHitArea.CIRCLE.outlineNormalized(samples = 24)
        assertEquals(24, o.size)
        // Every sampled vertex lies on the inscribed circle (centre 0.5, radius 0.5).
        for (p in o) {
            val dx = p.x - 0.5f
            val dy = p.y - 0.5f
            assertEquals(0.25f, dx * dx + dy * dy, 1e-3f)
        }
        // containsNormalized itself: the centre is inside, a corner is outside (exact-boundary
        // points are ambiguous under float rounding, so they're not asserted here).
        assertTrue(OGHitArea.CIRCLE.containsNormalized(0.5f, 0.5f))
        assertFalse(OGHitArea.CIRCLE.containsNormalized(0.98f, 0.98f))
    }

    @Test
    fun polygonOutline_returnsTheExactVertices() {
        val pts = listOf(OGPoint(0.5f, 0f), OGPoint(1f, 1f), OGPoint(0f, 1f))
        assertEquals(pts, OGHitArea.polygon(pts).outlineNormalized())
    }
}
