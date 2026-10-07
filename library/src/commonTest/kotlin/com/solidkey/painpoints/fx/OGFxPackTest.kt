package com.solidkey.painpoints.fx

import com.solidkey.painpoints.particle.OGParticleShape
import com.solidkey.painpoints.particle.OGParticleSpec
import com.solidkey.painpoints.particle.OGParticles
import com.solidkey.painpoints.shape.OGPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers the pure cores of the action-FX pack: lightning, speed-lines, elemental particle presets. */
class OGFxPackTest {

    // ── lightning ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun lightningChannel_spansEndpoints_andSubdivides() {
        val a = OGPoint(0f, 0f); val b = OGPoint(100f, 300f)
        val ch = lightningChannel(a, b, jaggedness = 20f, detail = 6, seed = 0)
        assertEquals((1 shl 6) + 1, ch.size) // 2^detail + 1
        assertEquals(a.x, ch.first().x, 1e-4f); assertEquals(a.y, ch.first().y, 1e-4f)
        assertEquals(b.x, ch.last().x, 1e-4f); assertEquals(b.y, ch.last().y, 1e-4f)
    }

    @Test
    fun lightningChannel_isDeterministic() {
        val a = OGPoint(0f, 0f); val b = OGPoint(80f, 260f)
        val x = lightningChannel(a, b, 20f, 6, 3)
        val y = lightningChannel(a, b, 20f, 6, 3)
        for (i in x.indices) { assertEquals(x[i].x, y[i].x, 0f); assertEquals(x[i].y, y[i].y, 0f) }
    }

    @Test
    fun lightningStrike_lifecycleAndLoop() {
        val spec = OGLightningSpec(strikeMs = 90f, decayMs = 240f, gapMs = 420f, loop = true) // cycle 750
        assertEquals(1f, lightningStrikeAt(spec, 0f).alpha, 1e-3f)
        assertEquals(0.5f, lightningStrikeAt(spec, 210f).alpha, 1e-2f) // mid-decay
        assertEquals(0f, lightningStrikeAt(spec, 500f).alpha, 1e-3f) // dark gap
        assertEquals(1, lightningStrikeAt(spec, 800f).index) // next strike (new bolt)
    }

    @Test
    fun lightningBranches_countTaperAndDeterminism() {
        val spec = OGLightnings.BOLT // branches = 5
        val main = lightningChannel(spec.endpointA(), spec.endpointB(), 0.16f, spec.detail, spec.seed)
        val a = lightningBranches(main, spec, strikeIndex = 0, widthScale = 300f)
        val b = lightningBranches(main, spec, strikeIndex = 0, widthScale = 300f)
        assertTrue(a.isNotEmpty() && a.size <= spec.branches)
        assertTrue(a.all { it.size >= 2 })
        assertEquals(a.size, b.size)
    }

    @Test
    fun lightningCodec_roundTripsAndTolerates() {
        for (p in OGLightnings.presets.values) assertEquals(p, OGLightnings.decodeSpec(OGLightnings.encode(p)))
        assertNotNull(OGLightnings.decodeSpecOrNull("```json\n{\"name\":\"z\",\"branches\":3}\n```"))
        assertNull(OGLightnings.decodeSpecOrNull("nope"))
        assertEquals(OGLightnings.BOLT, OGLightnings.preset("BoLt"))
        assertTrue(OGLightnings.lightningPrompt("x").contains("JSON"))
    }

    // ── speed lines ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun speedLines_countRangesAndDeterminism() {
        val spec = OGSpeedLines.IMPACT // count 64
        val a = speedLineParams(spec)
        val b = speedLineParams(spec)
        assertEquals(64, a.size)
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals(a[i].innerFrac, b[i].innerFrac, 0f)
            assertTrue(a[i].innerFrac < a[i].outerFrac)
            assertTrue(a[i].halfWidthFrac >= 0f)
        }
    }

    @Test
    fun speedLinesCodec_roundTripsAndPresets() {
        for (p in OGSpeedLines.presets.values) assertEquals(p, OGSpeedLines.decodeSpec(OGSpeedLines.encode(p)))
        assertNull(OGSpeedLines.decodeSpecOrNull("garbage"))
        assertEquals(3, OGSpeedLines.presets.size)
        assertEquals(OGSpeedLines.MOTION, OGSpeedLines.preset("motion"))
        assertTrue(!OGSpeedLines.MOTION.isRadial)
        assertTrue(OGSpeedLines.IMPACT.isRadial)
    }

    // ── elemental particle presets + new shapes ───────────────────────────────────────────────────

    @Test
    fun particleShapes_resolvePetalAndTeardrop() {
        assertEquals(OGParticleShape.PETAL, OGParticleSpec(shape = "petal").toSystem().shape)
        assertEquals(OGParticleShape.PETAL, OGParticleSpec(shape = "leaf").toSystem().shape)
        assertEquals(OGParticleShape.TEARDROP, OGParticleSpec(shape = "teardrop").toSystem().shape)
        assertEquals(OGParticleShape.TEARDROP, OGParticleSpec(shape = "droplet").toSystem().shape)
        assertEquals(OGParticleShape.CIRCLE, OGParticleSpec(shape = "circle").toSystem().shape)
    }

    @Test
    fun elementalPresets_arePresent() {
        for (n in listOf("petals", "embers", "droplets", "leaves")) assertNotNull(OGParticles.preset(n))
        assertEquals(10, OGParticles.presets.size) // 6 original + 4 elemental
    }
}
