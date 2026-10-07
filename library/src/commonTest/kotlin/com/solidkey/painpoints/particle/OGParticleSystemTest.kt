package com.solidkey.painpoints.particle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers the pure particle simulation ([OGParticleSystem]), its emission/lifetime/physics and the codec. */
class OGParticleSystemTest {

    @Test
    fun burst_spawnsExactlyBurstCount_onFirstUpdate() {
        val sys = OGParticleSpec(burst = 10, emissionRate = 0f, maxParticles = 100, lifetimeMs = 10_000f).toSystem()
        sys.update(16f)
        assertEquals(10, sys.count)
        sys.update(16f) // burst is one-shot — no more spawns
        assertEquals(10, sys.count)
    }

    @Test
    fun maxParticles_capsTheBurst() {
        val sys = OGParticleSpec(burst = 1000, emissionRate = 0f, maxParticles = 50, lifetimeMs = 10_000f).toSystem()
        sys.update(16f)
        assertEquals(50, sys.count)
    }

    @Test
    fun continuousEmission_spawnsAtTheGivenRate() {
        val sys = OGParticleSpec(burst = 0, emissionRate = 100f, maxParticles = 1000, lifetimeMs = 100_000f).toSystem()
        sys.update(100f) // 0.1s * 100/s = 10 particles
        assertEquals(10, sys.count)
    }

    @Test
    fun particlesDieAfterTheirLifetime() {
        val sys = OGParticleSpec(
            burst = 5, emissionRate = 0f, maxParticles = 100, lifetimeMs = 100f, lifetimeJitter = 0f,
        ).toSystem()
        sys.update(10f)
        assertEquals(5, sys.count)
        sys.update(200f) // every particle is now older than 100ms
        assertEquals(0, sys.count)
    }

    @Test
    fun simulation_isDeterministicFromSeed() {
        val spec = OGParticleSpec(burst = 20, emissionRate = 30f, maxParticles = 500, seed = 42)
        val a = spec.toSystem()
        val b = spec.toSystem()
        repeat(5) { a.update(16f); b.update(16f) }
        assertEquals(a.count, b.count)
        for (i in 0 until a.count) {
            assertEquals(a.x[i], b.x[i], "x[$i]")
            assertEquals(a.y[i], b.y[i], "y[$i]")
        }
    }

    @Test
    fun gravityPullsParticlesDownOverTime() {
        val sys = OGParticleSpec(
            burst = 1, emissionRate = 0f, maxParticles = 10, speed = 0f, spreadDeg = 0f,
            gravityY = 1f, lifetimeMs = 100_000f, lifetimeJitter = 0f,
        ).toSystem()
        sys.update(16f)              // spawn at y = 0.5 with zero initial velocity
        val y0 = sys.y[0]
        repeat(10) { sys.update(100f) }
        assertTrue(sys.y[0] > y0, "gravity should increase y (downward): ${sys.y[0]} !> $y0")
    }

    @Test
    fun reset_clearsAndRearmsTheBurst() {
        val sys = OGParticleSpec(burst = 8, emissionRate = 0f, maxParticles = 100, lifetimeMs = 10_000f).toSystem()
        sys.update(16f)
        assertEquals(8, sys.count)
        sys.reset()
        assertEquals(0, sys.count)
        sys.update(16f)
        assertEquals(8, sys.count) // burst fires again after reset
    }

    @Test
    fun birthColour_reflectsStartColourAndAlpha() {
        val sys = OGParticleSpec(
            burst = 1, emissionRate = 0f, maxParticles = 10,
            startColor = "#FF0000", startAlpha = 1f, endAlpha = 0f,
            lifetimeMs = 100_000f, lifetimeJitter = 0f,
        ).toSystem()
        sys.update(16f) // age ≈ 0 → colour ≈ start
        val argb = sys.argb[0]
        assertEquals(0xFF, (argb ushr 24) and 0xFF, "alpha") // opaque at birth
        assertTrue(((argb ushr 16) and 0xFF) > 250, "red channel high")
        assertTrue(((argb ushr 8) and 0xFF) < 5, "green channel ~0")
    }

    @Test
    fun codec_roundTrips_presetsDecode_andToleratesGarbage() {
        val spec = OGParticleSpec(name = "x", burst = 30, shape = "star", startColor = "#00FF00")
        assertEquals(spec, OGParticles.decodeSpec(OGParticles.encode(spec)))
        assertEquals(OGParticles.CONFETTI, OGParticles.preset("Confetti")) // case-insensitive lookup
        assertNull(OGParticles.decodeSpecOrNull("not a spec"))
        // tolerant of markdown fences
        val fenced = "```json\n{\"burst\":5,\"emissionRate\":0}\n```"
        val sys = OGParticles.decodeOrNull(fenced)!!
        sys.update(16f)
        assertEquals(5, sys.count)
    }

    @Test
    fun confettiPreset_runsAndFillsToItsBurst() {
        val sys = OGParticles.CONFETTI.toSystem()
        sys.update(16f)
        assertEquals(150, sys.count)
        assertTrue(sys.shape == OGParticleShape.SQUARE)
    }
}
