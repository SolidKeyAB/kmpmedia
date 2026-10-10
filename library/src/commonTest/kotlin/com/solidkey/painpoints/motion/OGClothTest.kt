package com.solidkey.painpoints.motion

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OGClothTest {

    /** Distance from point (x,y) to a capsule's core segment. */
    private fun distToCapsule(x: Float, y: Float, col: OGClothCollider): Float {
        val ex = col.bx - col.ax
        val ey = col.by - col.ay
        val len2 = ex * ex + ey * ey
        val t = if (len2 < 1e-12f) 0f else (((x - col.ax) * ex + (y - col.ay) * ey) / len2).coerceIn(0f, 1f)
        val qx = col.ax + ex * t
        val qy = col.ay + ey * t
        return sqrt((x - qx) * (x - qx) + (y - qy) * (y - qy))
    }

    @Test
    fun reset_seedsFlatWithZeroVelocity() {
        val spec = OGClothMeshSpec(cols = 5, rows = 4, width = 0.4f, height = 0.3f, windAmplitude = 0f, pinMode = OGClothPinMode.TOP_EDGE)
        val c = OGCloth(spec)
        c.reset(0.3f, 0.2f, 0.7f, 0.2f)
        val restX = spec.width / (spec.cols - 1)
        val restY = spec.height / (spec.rows - 1)
        for (col in 0 until c.cols) {
            val tx = 0.3f + (0.7f - 0.3f) * (col.toFloat() / (c.cols - 1))
            for (row in 0 until c.rows) {
                assertTrue(abs(c.x(col, row) - tx) < 1e-5f, "node $col,$row x on its column")
                assertTrue(abs(c.y(col, row) - (0.2f + row * restY)) < 1e-5f, "node $col,$row hangs at rest spacing")
            }
        }
        // Column spacing matches restX.
        assertTrue(abs((c.x(1, 0) - c.x(0, 0)) - restX) < 1e-5f, "columns at rest spacing")
    }

    @Test
    fun settles_inextensible_stillAir() {
        val spec = OGClothMeshSpec(cols = 6, rows = 5, width = 0.4f, height = 0.35f, gravity = 0.25f, damping = 1.2f, windAmplitude = 0f, pinMode = OGClothPinMode.TOP_EDGE)
        val c = OGCloth(spec)
        c.reset(0.3f, 0.15f, 0.7f, 0.15f)
        repeat(360) { c.step(1f / 60f, 0.3f, 0.15f, 0.7f, 0.15f) }
        val restX = spec.width / (spec.cols - 1)
        val restY = spec.height / (spec.rows - 1)
        // Structural edges stay close to rest (position-based constraints = inextensible).
        for (row in 0 until c.rows) for (col in 0 until c.cols - 1) {
            val dx = c.x(col + 1, row) - c.x(col, row); val dy = c.y(col + 1, row) - c.y(col, row)
            val e = sqrt(dx * dx + dy * dy)
            assertTrue(abs(e - restX) < restX * 0.25f, "h-edge $col,$row len $e ~ $restX")
        }
        for (col in 0 until c.cols) for (row in 0 until c.rows - 1) {
            val dx = c.x(col, row + 1) - c.x(col, row); val dy = c.y(col, row + 1) - c.y(col, row)
            val e = sqrt(dx * dx + dy * dy)
            assertTrue(abs(e - restY) < restY * 0.25f, "v-edge $col,$row len $e ~ $restY")
        }
    }

    @Test
    fun fixedStep_isDeterministic() {
        val spec = OGClothMeshSpec(cols = 8, rows = 6, windAmplitude = 0.7f, windFrequency = 0.9f, windSeed = 5, pinMode = OGClothPinMode.TOP_CORNERS)
        fun run(): OGCloth {
            val c = OGCloth(spec)
            c.colliders.add(OGClothCollider(0.4f, 0.5f, 0.6f, 0.5f, 0.04f))
            c.reset(0.2f, 0.2f, 0.8f, 0.2f)
            repeat(200) { val ax = (0.2f + 0.01f * it) % 1f; c.step(1f / 60f, ax, 0.2f, ax + 0.6f, 0.2f) }
            return c
        }
        val a = run(); val b = run()
        for (col in 0 until a.cols) for (row in 0 until a.rows) {
            assertEquals(a.x(col, row), b.x(col, row), "node $col,$row x identical")
            assertEquals(a.y(col, row), b.y(col, row), "node $col,$row y identical")
        }
    }

    @Test
    fun stalledFrame_isClampedNotExploded() {
        val c = OGCloth(OGClothMeshSpec(cols = 6, rows = 5, windAmplitude = 1f, pinMode = OGClothPinMode.TOP_EDGE))
        c.reset(0.3f, 0.2f, 0.7f, 0.2f)
        c.step(10f, 0.3f, 0.2f, 0.7f, 0.2f) // a 10-second "stall" must not blow up
        for (col in 0 until c.cols) for (row in 0 until c.rows) {
            assertTrue(c.x(col, row).isFinite() && c.y(col, row).isFinite(), "node $col,$row finite after stall")
            assertTrue(abs(c.x(col, row)) < 10f && abs(c.y(col, row)) < 10f, "node $col,$row bounded after stall")
        }
    }

    @Test
    fun pinModes_holdTheirAnchors() {
        // TOP_EDGE: whole top row stays on the segment.
        OGCloth(OGClothMeshSpec(cols = 6, rows = 5, windAmplitude = 0f, pinMode = OGClothPinMode.TOP_EDGE)).apply {
            reset(0.2f, 0.3f, 0.8f, 0.3f)
            repeat(180) { step(1f / 60f, 0.2f, 0.3f, 0.8f, 0.3f) }
            for (col in 0 until cols) {
                val tx = 0.2f + 0.6f * (col.toFloat() / (cols - 1))
                assertTrue(abs(x(col, 0) - tx) < 1e-3f && abs(y(col, 0) - 0.3f) < 1e-3f, "top-edge node $col held")
            }
        }
        // TOP_CORNERS: only the two corners are held, so the body of the sheet hangs below them.
        OGCloth(OGClothMeshSpec(cols = 7, rows = 5, width = 0.6f, height = 0.3f, gravity = 1f, windAmplitude = 0f, pinMode = OGClothPinMode.TOP_CORNERS)).apply {
            reset(0.2f, 0.3f, 0.8f, 0.3f)
            repeat(240) { step(1f / 60f, 0.2f, 0.3f, 0.8f, 0.3f) }
            assertTrue(abs(x(0, 0) - 0.2f) < 1e-3f && abs(y(0, 0) - 0.3f) < 1e-3f, "left corner held")
            assertTrue(abs(x(cols - 1, 0) - 0.8f) < 1e-3f && abs(y(cols - 1, 0) - 0.3f) < 1e-3f, "right corner held")
            val bottom = y(cols / 2, rows - 1)
            assertTrue(bottom > 0.3f + 0.1f, "the body hangs well below the two held corners (bottom=$bottom)")
        }
        // LEFT_EDGE: whole left column stays on the (vertical) segment.
        OGCloth(OGClothMeshSpec(cols = 6, rows = 5, windAmplitude = 0f, pinMode = OGClothPinMode.LEFT_EDGE)).apply {
            reset(0.3f, 0.2f, 0.3f, 0.6f)
            repeat(180) { step(1f / 60f, 0.3f, 0.2f, 0.3f, 0.6f) }
            for (row in 0 until rows) {
                val ty = 0.2f + 0.4f * (row.toFloat() / (rows - 1))
                assertTrue(abs(x(0, row) - 0.3f) < 1e-3f && abs(y(0, row) - ty) < 1e-3f, "left-edge node $row held")
            }
        }
        // NONE: nothing held, so the sheet falls.
        OGCloth(OGClothMeshSpec(cols = 5, rows = 4, gravity = 1f, windAmplitude = 0f, pinMode = OGClothPinMode.NONE)).apply {
            reset(0.3f, 0.2f, 0.7f, 0.2f)
            val y0 = y(0, 0)
            repeat(60) { step(1f / 60f, 0.3f, 0.2f, 0.7f, 0.2f) }
            assertTrue(y(0, 0) > y0 + 0.02f, "unpinned sheet falls under gravity")
        }
    }

    @Test
    fun drapesOverCapsule_allNodesStayOutside() {
        val spec = OGClothMeshSpec(cols = 11, rows = 8, width = 0.5f, height = 0.4f, gravity = 1f, damping = 0.9f, windAmplitude = 0f, friction = 0.5f, pinMode = OGClothPinMode.NONE)
        val c = OGCloth(spec)
        val bar = OGClothCollider(0.4f, 0.5f, 0.6f, 0.5f, 0.03f) // a horizontal pole, narrower than the sheet
        c.colliders.add(bar)
        c.reset(0.25f, 0.2f, 0.75f, 0.2f) // sheet seeded flat above the bar, wider than it
        repeat(420) { c.step(1f / 60f, 0.25f, 0.2f, 0.75f, 0.2f) }
        // No node penetrates the capsule.
        for (col in 0 until c.cols) for (row in 0 until c.rows) {
            assertTrue(distToCapsule(c.x(col, row), c.y(col, row), bar) >= bar.radius - 2e-3f, "node $col,$row outside the bar")
            assertTrue(c.y(col, row).isFinite(), "node $col,$row finite")
        }
        // It genuinely draped over: the lowest node hangs well below the bar.
        var maxY = -1f
        for (col in 0 until c.cols) for (row in 0 until c.rows) if (c.y(col, row) > maxY) maxY = c.y(col, row)
        assertTrue(maxY > 0.5f + bar.radius + 0.05f, "the sheet hangs down past the bar (maxY=$maxY)")
    }

    @Test
    fun shear_keepsSheetFromCollapsing() {
        val spec = OGClothMeshSpec(cols = 7, rows = 6, width = 0.6f, height = 0.4f, gravity = 0.7f, damping = 0.9f, windAmplitude = 0f, pinMode = OGClothPinMode.TOP_CORNERS)
        val c = OGCloth(spec)
        c.reset(0.2f, 0.25f, 0.8f, 0.25f)
        repeat(300) { c.step(1f / 60f, 0.2f, 0.25f, 0.8f, 0.25f) }
        var minX = 2f; var maxX = -2f; var minY = 2f; var maxY = -2f
        for (col in 0 until c.cols) for (row in 0 until c.rows) {
            val x = c.x(col, row); val y = c.y(col, row)
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        // A sheared/collapsed sheet would lose its 2-D extent; shear springs keep a healthy bounding box.
        assertTrue((maxX - minX) > spec.width * 0.5f, "keeps horizontal extent (${maxX - minX})")
        assertTrue((maxY - minY) > spec.height * 0.5f, "keeps vertical extent (${maxY - minY})")
    }

    @Test
    fun prevailingWind_deflectsTheFreeEdge() {
        // A tall, light banner hung from two close top corners swings like the strip's hanging line.
        fun settleBottomX(wind: Float): Float {
            val c = OGCloth(OGClothMeshSpec(cols = 4, rows = 10, width = 0.08f, height = 0.55f, gravity = 0.25f, damping = 0.6f, wind = wind, windAmplitude = 0f, pinMode = OGClothPinMode.TOP_CORNERS))
            c.reset(0.46f, 0.12f, 0.54f, 0.12f)
            repeat(480) { c.step(1f / 60f, 0.46f, 0.12f, 0.54f, 0.12f) }
            var sum = 0f
            for (col in 0 until c.cols) sum += c.x(col, c.rows - 1)
            return sum / c.cols
        }
        val z = settleBottomX(0f); val r = settleBottomX(1.4f); val l = settleBottomX(-1.4f)
        assertTrue(r > z + 0.02f, "wind right deflects the free (bottom) edge right (z=$z r=$r)")
        assertTrue(l < z - 0.02f, "wind left deflects the free edge left (z=$z l=$l)")
    }

    @Test
    fun codec_roundTripsAndIsLenient() {
        for (spec in OGCloths.meshPresets.values) {
            val back = OGCloths.decodeMeshSpec(OGCloths.encode(spec))
            assertEquals(spec, back, "mesh preset ${spec.name} round-trips")
        }
        val fenced = "```json\n{\"name\":\"x\",\"cols\":10,\"rows\":7,\"pinMode\":\"TOP_CORNERS\"}\n```"
        assertEquals(10, OGCloths.decodeMeshSpec(fenced).cols)
        assertEquals(OGClothPinMode.TOP_CORNERS, OGCloths.decodeMeshSpec(fenced).pinMode)
        assertNull(OGCloths.decodeMeshSpecOrNull("not json at all"), "garbage decodes to null")
        assertEquals("tarp", OGCloths.meshPreset("TARP")?.name, "mesh preset lookup is case-insensitive")
        assertNull(OGCloths.meshPreset("nope"))
    }

    @Test
    fun grid_isClampedToCeiling() {
        val c = OGCloth(OGClothMeshSpec(cols = 999, rows = 1))
        assertEquals(24, c.cols, "cols clamped to the ceiling")
        assertEquals(2, c.rows, "rows clamped up to the floor")
    }

    @Test
    fun sampleAt_matchesStepByStep_withCollider() {
        val spec = OGClothMeshSpec(cols = 8, rows = 6, windAmplitude = 0.6f, windSeed = 2, pinMode = OGClothPinMode.NONE)
        val bar = OGClothCollider(0.4f, 0.5f, 0.6f, 0.5f, 0.03f)
        val anchor: (Float) -> com.solidkey.painpoints.shape.OGPoint =
            { t -> com.solidkey.painpoints.shape.OGPoint(0.25f + 0.02f * t, 0.2f) }
        val anchor2: (Float) -> com.solidkey.painpoints.shape.OGPoint =
            { t -> com.solidkey.painpoints.shape.OGPoint(0.75f + 0.02f * t, 0.2f) }
        val sampled = OGCloths.sampleAt(spec, timeSec = 1f, colliders = listOf(bar), anchorAt = anchor, anchor2At = anchor2)
        // Reproduce the same fixed-step march by hand.
        val manual = OGCloth(spec)
        manual.colliders.add(bar)
        val h = 1f / 120f
        manual.reset(anchor(0f).x, anchor(0f).y, anchor2(0f).x, anchor2(0f).y)
        var t = 0f
        while (t < 1f) { val a = anchor(t + h); val b = anchor2(t + h); manual.step(h, a.x, a.y, b.x, b.y); t += h }
        for (col in 0 until manual.cols) for (row in 0 until manual.rows) {
            assertTrue(abs(sampled.x(col, row) - manual.x(col, row)) < 1e-5f, "node $col,$row x reproducible")
            assertTrue(abs(sampled.y(col, row) - manual.y(col, row)) < 1e-5f, "node $col,$row y reproducible")
        }
    }
}
