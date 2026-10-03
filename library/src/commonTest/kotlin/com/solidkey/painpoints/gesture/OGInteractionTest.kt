package com.solidkey.painpoints.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OGInteractionTest {

    @Test
    fun clampScaleHoldsTheRange() {
        assertEquals(1f, clampScale(0.3f, 1f, 4f))
        assertEquals(4f, clampScale(9f, 1f, 4f))
        assertEquals(2.5f, clampScale(2.5f, 1f, 4f))
    }

    @Test
    fun applyZoomMultipliesThenClamps() {
        assertEquals(2f, applyZoom(1f, 2f, 1f, 4f), 0.0001f)
        assertEquals(4f, applyZoom(3f, 2f, 1f, 4f), 0.0001f)   // 6 → clamped to 4
        assertEquals(1f, applyZoom(1f, 0.5f, 1f, 4f), 0.0001f) // 0.5 → clamped to 1
    }

    @Test
    fun clampOffsetHoldsSymmetricBounds() {
        val bounds = OGPanBounds(100f, 50f)
        assertEquals(Offset(100f, 50f), clampOffset(Offset(200f, 80f), bounds))
        assertEquals(Offset(-100f, -50f), clampOffset(Offset(-200f, -80f), bounds))
        assertEquals(Offset(30f, -20f), clampOffset(Offset(30f, -20f), bounds))
    }

    @Test
    fun clampOffsetWithInfiniteBoundsIsIdentity() {
        val o = Offset(9999f, -9999f)
        assertEquals(o, clampOffset(o, OGPanBounds.Unbounded))
    }

    @Test
    fun panBoundsPxScalesFractionBySize() {
        val cfg = OGInteractionConfig(maxPanFractionX = 0.5f, maxPanFractionY = 0.25f)
        val b = cfg.panBoundsPx(IntSize(200, 400))
        assertEquals(100f, b.maxX, 0.0001f)
        assertEquals(100f, b.maxY, 0.0001f)
    }

    @Test
    fun panBoundsPxStaysInfiniteWhenUnbounded() {
        val b = OGInteractionConfig().panBoundsPx(IntSize(200, 400))
        assertTrue(b.maxX.isInfinite())
        assertTrue(b.maxY.isInfinite())
    }

    @Test
    fun localPanToParentIsIdentityWithNoRotationOrScale() {
        val v = Offset(12f, -7f)
        val mapped = localPanToParent(v, rotationDeg = 0f, scale = 1f)
        assertEquals(12f, mapped.x, 0.0001f)
        assertEquals(-7f, mapped.y, 0.0001f)
    }

    @Test
    fun localPanToParentRotates90Degrees() {
        // At +90° the layer's local +x points along screen +y and local +y along screen -x,
        // so a local drag of (1,0) must land in parent space as (0,1).
        val mapped = localPanToParent(Offset(1f, 0f), rotationDeg = 90f, scale = 1f)
        assertEquals(0f, mapped.x, 0.0001f)
        assertEquals(1f, mapped.y, 0.0001f)
    }

    @Test
    fun localPanToParentRotates180DegreesFlipsDirection() {
        // The reported bug: once rotated, dragging went the wrong way. At 180° a local (1,1)
        // must become (-1,-1) in parent space.
        val mapped = localPanToParent(Offset(1f, 1f), rotationDeg = 180f, scale = 1f)
        assertEquals(-1f, mapped.x, 0.0001f)
        assertEquals(-1f, mapped.y, 0.0001f)
    }

    @Test
    fun localPanToParentAppliesScaleSoDragTracksTheFinger() {
        // Zoomed 2×, a local delta covers twice the screen distance, so the parent delta doubles.
        val mapped = localPanToParent(Offset(3f, -4f), rotationDeg = 0f, scale = 2f)
        assertEquals(6f, mapped.x, 0.0001f)
        assertEquals(-8f, mapped.y, 0.0001f)
    }

    @Test
    fun presetsToggleTheRightGestures() {
        assertTrue(OGInteractionConfig.DragOnly.enablePan)
        assertTrue(!OGInteractionConfig.DragOnly.enableZoom)
        assertTrue(!OGInteractionConfig.DragOnly.enableRotate)

        assertTrue(OGInteractionConfig.PanZoom.enableZoom)
        assertTrue(!OGInteractionConfig.PanZoom.enableRotate)

        assertTrue(OGInteractionConfig.All.enableZoom)
        assertTrue(OGInteractionConfig.All.enableRotate)
    }
}
