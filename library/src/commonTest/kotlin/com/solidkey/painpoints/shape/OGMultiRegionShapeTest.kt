package com.solidkey.painpoints.shape

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Covers the pure geometry + value semantics behind [OGMultiRegionShape] — normalized region
 * placement, the `union` / `of` factories, and by-value equality (so a re-created multi-region clip
 * is a cache / Compose-skip hit). The actual [androidx.compose.ui.graphics.Path.op] combination is
 * platform-backed and validated by the demo; pinning this math pins the placement.
 */
class OGMultiRegionShapeTest {

    // ---- regionRect: normalized (0..1) placement onto a pixel box ------------------------------

    @Test
    fun placesNormalizedRegionOntoBox() {
        val region = OGClipRegion(CircleShape, left = 0f, top = 0f, right = 0.5f, bottom = 1f)
        assertEquals(Rect(0f, 0f, 100f, 200f), regionRect(region, width = 200f, height = 200f))
    }

    @Test
    fun fullBoxIsTheDefault() {
        assertEquals(Rect(0f, 0f, 200f, 120f), regionRect(OGClipRegion(CircleShape), 200f, 120f))
    }

    @Test
    fun clampsOutOfRangePlacement() {
        val region = OGClipRegion(CircleShape, left = -0.5f, top = 0f, right = 2f, bottom = 1.5f)
        assertEquals(Rect(0f, 0f, 100f, 100f), regionRect(region, 100f, 100f))
    }

    @Test
    fun normalizesEdgeOrder() {
        // right < left / bottom < top is normalized so width/height stay positive.
        val region = OGClipRegion(CircleShape, left = 0.75f, top = 0.75f, right = 0.25f, bottom = 0.25f)
        assertEquals(Rect(25f, 25f, 75f, 75f), regionRect(region, 100f, 100f))
    }

    // ---- factories -----------------------------------------------------------------------------

    @Test
    fun unionMakesOneFullBoxRegionPerShape() {
        val shape = OGMultiRegionShape.union(CircleShape, DiamondShape())
        assertEquals(2, shape.regions.size)
        assertEquals(OGClipOp.UNION, shape.op)
        assertTrue(shape.regions.all { it.left == 0f && it.top == 0f && it.right == 1f && it.bottom == 1f })
    }

    @Test
    fun ofKeepsRegionsAndOp() {
        val a = OGClipRegion(CircleShape, right = 0.5f)
        val b = OGClipRegion(CircleShape, left = 0.5f)
        val shape = OGMultiRegionShape.of(a, b, op = OGClipOp.INTERSECT)
        assertEquals(listOf(a, b), shape.regions)
        assertEquals(OGClipOp.INTERSECT, shape.op)
    }

    // ---- value equality (cache / skip hits) ----------------------------------------------------

    @Test
    fun equalByValueForCacheHits() {
        val one = OGMultiRegionShape.union(CircleShape, CircleShape)
        val two = OGMultiRegionShape.union(CircleShape, CircleShape)
        assertEquals(one, two)
        assertEquals(one.hashCode(), two.hashCode())
    }

    @Test
    fun differentOpIsNotEqual() {
        val regions = listOf(OGClipRegion(CircleShape), OGClipRegion(CircleShape))
        assertNotEquals(
            OGMultiRegionShape(regions, OGClipOp.UNION),
            OGMultiRegionShape(regions, OGClipOp.DIFFERENCE),
        )
    }
}
