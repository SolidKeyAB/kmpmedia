package com.solidkey.painpoints.depth

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the pure derivations behind [ogDepth] — [depthDistance], [OGDepthConfig.alphaFor],
 * [OGDepthConfig.blurRadiusFor], [OGDepthConfig.scaleFor]. The [Modifier.ogDepth] wiring is a thin
 * layer over exactly these, so pinning the math down here pins the behaviour (in-focus content has
 * no blur/dim; effects grow with focal distance; out-of-range inputs are clamped; video config
 * disables blur).
 */
class OGDepthTest {

    private val default = OGDepthConfig()

    // ---- depthDistance ----

    @Test
    fun distanceIsZeroInFocus() {
        assertEquals(0f, depthDistance(0.5f, 0.5f), 0.0001f)
        assertEquals(0f, depthDistance(0f, 0f), 0.0001f)
        assertEquals(0f, depthDistance(1f, 1f), 0.0001f)
    }

    @Test
    fun distanceIsSymmetricAndAbsolute() {
        assertEquals(0.3f, depthDistance(0.2f, 0.5f), 0.0001f)
        assertEquals(0.3f, depthDistance(0.5f, 0.2f), 0.0001f)
    }

    @Test
    fun distanceClampsOutOfRangeInputs() {
        // depth < 0 clamps to 0, focal > 1 clamps to 1 -> full distance, not > 1.
        assertEquals(1f, depthDistance(-2f, 5f), 0.0001f)
        assertEquals(1f, depthDistance(0f, 1f), 0.0001f)
    }

    // ---- alpha ----

    @Test
    fun alphaIsFullInFocus() {
        assertEquals(1f, default.alphaFor(0.5f, 0.5f), 0.0001f)
    }

    @Test
    fun alphaFallsWithDistanceButNeverBelowFloor() {
        val a = default.alphaFor(0.9f, 0.5f) // dz = 0.4 -> 1 - 0.2 = 0.8
        assertEquals(0.8f, a, 0.0001f)
        // Max distance -> 1 - 0.5 = 0.5, still >= floor 0.4.
        assertEquals(0.5f, default.alphaFor(1f, 0f), 0.0001f)
        // A steep falloff is floored, not allowed to go negative/too low.
        val steep = OGDepthConfig(dimFalloff = 5f, minAlpha = 0.4f)
        assertEquals(0.4f, steep.alphaFor(1f, 0f), 0.0001f)
    }

    @Test
    fun dimFalloffZeroDisablesDimming() {
        val noDim = OGDepthConfig(dimFalloff = 0f)
        assertEquals(1f, noDim.alphaFor(1f, 0f), 0.0001f)
    }

    // ---- blur ----

    @Test
    fun blurIsZeroInFocus() {
        assertEquals(0.dp, default.blurRadiusFor(0.5f, 0.5f))
    }

    @Test
    fun blurReachesMaxAtFullDistanceAndScalesLinearly() {
        assertEquals(default.maxBlur, default.blurRadiusFor(1f, 0f))
        // Half the distance -> half the radius.
        assertEquals((default.maxBlur.value / 2f), default.blurRadiusFor(1f, 0.5f).value, 0.01f)
    }

    @Test
    fun blurContentFalseAndVideoPresetDisableBlur() {
        val noBlur = OGDepthConfig(blurContent = false)
        assertEquals(0.dp, noBlur.blurRadiusFor(1f, 0f))
        assertEquals(0.dp, OGDepthConfig.Video.blurRadiusFor(1f, 0f))
        assertTrue(!OGDepthConfig.Video.blurContent)
    }

    // ---- parallax scale ----

    @Test
    fun scaleIsOneWhenNoDepthScale() {
        assertEquals(1f, default.scaleFor(0f), 0.0001f)
        assertEquals(1f, default.scaleFor(1f), 0.0001f)
    }

    @Test
    fun scaleLerpsFarToNear() {
        val cfg = OGDepthConfig(depthScale = OGDepthScale(far = 0.8f, near = 1.2f))
        assertEquals(0.8f, cfg.scaleFor(0f), 0.0001f)   // far
        assertEquals(1.2f, cfg.scaleFor(1f), 0.0001f)   // near
        assertEquals(1.0f, cfg.scaleFor(0.5f), 0.0001f) // midpoint
    }

    @Test
    fun scaleClampsDepth() {
        val cfg = OGDepthConfig(depthScale = OGDepthScale(far = 0.8f, near = 1.2f))
        assertEquals(0.8f, cfg.scaleFor(-3f), 0.0001f)
        assertEquals(1.2f, cfg.scaleFor(4f), 0.0001f)
    }
}
