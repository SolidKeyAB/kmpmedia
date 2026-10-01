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
