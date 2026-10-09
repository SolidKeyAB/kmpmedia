package com.solidkey.painpoints.audio.reactive

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OGAudioReactiveTest {

    // ---- FFT ----------------------------------------------------------------

    @Test
    fun fft_cosine_peaks_at_its_bin() {
        val n = 64
        val f = 8
        val re = FloatArray(n) { cos(2.0 * PI * f * it / n).toFloat() }
        val im = FloatArray(n)
        OGFft.fft(re, im)
        var best = 1
        var bestMag = -1f
        for (k in 1..n / 2) {
            val m = sqrt(re[k] * re[k] + im[k] * im[k])
            if (m > bestMag) { bestMag = m; best = k }
        }
        assertEquals(f, best, "a pure cosine should peak at its own frequency bin")
    }

    @Test
    fun fft_impulse_is_flat() {
        val n = 16
        val re = FloatArray(n).also { it[0] = 1f }
        val im = FloatArray(n)
        OGFft.fft(re, im)
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (k in 0 until n) {
            val m = sqrt(re[k] * re[k] + im[k] * im[k])
            lo = minOf(lo, m); hi = maxOf(hi, m)
        }
        assertTrue(hi - lo < 1e-3f, "an impulse has a flat magnitude spectrum (got $lo..$hi)")
    }

    @Test
    fun fft_rejects_non_power_of_two() {
        val re = FloatArray(6)
        val im = FloatArray(6)
        var threw = false
        try { OGFft.fft(re, im) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw, "non power-of-two length must be rejected")
    }

    // ---- Analyzer bands -----------------------------------------------------

    private fun sine(freq: Double, n: Int, sr: Int, amp: Float = 1f): FloatArray =
        FloatArray(n) { amp * sin(2.0 * PI * freq * it / sr).toFloat() }

    private fun run(analyzer: OGAudioAnalyzer, frame: FloatArray, times: Int): OGAudioBands {
        var out = OGAudioBands.Silent
        repeat(times) { out = analyzer.process(frame) }
        return out
    }

    @Test
    fun analyzer_silence_is_zero() {
        val a = OGAudioAnalyzer()
        val b = run(a, FloatArray(1024), 4)
        assertTrue(b.level < 0.02f && b.bass < 0.02f && b.mid < 0.02f && b.treble < 0.02f,
            "silence should read ~0 (got $b)")
    }

    @Test
    fun analyzer_low_tone_is_bass_heavy() {
        val a = OGAudioAnalyzer()
        val b = run(a, sine(80.0, 1024, 44100), 4)
        assertTrue(b.bass > b.treble, "an 80 Hz tone should have more bass than treble (got $b)")
        assertTrue(b.bass > b.mid, "an 80 Hz tone should have more bass than mid (got $b)")
    }

    @Test
    fun analyzer_high_tone_is_treble_heavy() {
        val a = OGAudioAnalyzer()
        val b = run(a, sine(10000.0, 1024, 44100), 4)
        assertTrue(b.treble > b.bass, "a 10 kHz tone should have more treble than bass (got $b)")
    }

    @Test
    fun analyzer_bands_stay_in_unit_range() {
        val a = OGAudioAnalyzer(OGAudioReactiveSpec(gain = 50f)) // absurd gain must still clamp
        val b = run(a, sine(120.0, 1024, 44100), 4)
        for (v in listOf(b.level, b.bass, b.mid, b.treble)) {
            assertTrue(v in 0f..1f, "bands must be clamped to 0..1 (got $v)")
        }
    }

    @Test
    fun analyzer_release_decays_after_silence() {
        val a = OGAudioAnalyzer()
        val loud = run(a, sine(80.0, 1024, 44100), 4).bass
        val silence = FloatArray(1024)
        val d1 = a.process(silence).bass
        val d2 = a.process(silence).bass
        val d3 = a.process(silence).bass
        assertTrue(loud > d1 && d1 >= d2 && d2 >= d3, "bass should decay monotonically through silence ($loud→$d1→$d2→$d3)")
        assertTrue(d3 < loud * 0.8f, "bass should be clearly lower after silence")
    }

    @Test
    fun analyzer_reset_clears_state() {
        val a = OGAudioAnalyzer()
        run(a, sine(80.0, 1024, 44100), 4)
        a.reset()
        val b = a.process(FloatArray(1024))
        assertTrue(b.bass < 0.02f, "after reset the first silent frame should read ~0 (got ${b.bass})")
    }

    // ---- Beat detection -----------------------------------------------------

    @Test
    fun beat_fires_on_onset_not_on_silence() {
        val a = OGAudioAnalyzer(OGAudioReactives.preset("beat")!!)
        // Prime with a few steady quiet frames → low running flux, no beats.
        val quiet = sine(80.0, 1024, 44100, amp = 0.03f)
        var beatsInQuiet = 0
        repeat(5) { if (a.process(quiet).beat) beatsInQuiet++ }
        // A sudden loud broadband frame = an onset.
        val onset = FloatArray(1024) { noise(it.toLong()) }
        val beat = a.process(onset).beat
        assertTrue(beat, "a sudden loud onset should register a beat")
        assertEquals(0, beatsInQuiet, "steady quiet audio should not spuriously beat")
    }

    @Test
    fun pure_silence_never_beats() {
        val a = OGAudioAnalyzer(OGAudioReactives.preset("beat")!!)
        val z = FloatArray(1024)
        var beats = 0
        repeat(6) { if (a.process(z).beat) beats++ }
        assertEquals(0, beats, "silence must never beat")
    }

    // ---- Synthetic source ---------------------------------------------------

    @Test
    fun synthetic_source_is_deterministic() {
        val a = OGSyntheticAudioSource(bpm = 120f, seed = 1)
        val b = OGSyntheticAudioSource(bpm = 120f, seed = 1)
        repeat(3) {
            val bufA = FloatArray(256); val bufB = FloatArray(256)
            a.read(bufA); b.read(bufB)
            assertTrue(bufA.contentEquals(bufB), "same bpm/seed must yield identical samples")
        }
    }

    @Test
    fun synthetic_source_produces_energy() {
        val src = OGSyntheticAudioSource(bpm = 120f)
        val a = OGAudioAnalyzer()
        val buf = FloatArray(1024)
        var level = 0f
        repeat(8) { src.read(buf); level = a.process(buf).level }
        assertTrue(level > 0.01f, "the synthetic source should drive a non-zero level (got $level)")
    }

    @Test
    fun synthetic_source_reset_replays() {
        val src = OGSyntheticAudioSource(bpm = 120f)
        val first = FloatArray(256).also { src.read(it) }
        src.read(FloatArray(256)) // advance
        src.reset()
        val again = FloatArray(256).also { src.read(it) }
        assertTrue(first.contentEquals(again), "reset should replay from the top")
    }

    // ---- Bands helper + codec ----------------------------------------------

    @Test
    fun bands_band_selector() {
        val b = OGAudioBands(level = 0.1f, bass = 0.2f, mid = 0.3f, treble = 0.4f)
        assertEquals(0.1f, b.band(OGAudioBand.LEVEL))
        assertEquals(0.2f, b.band(OGAudioBand.BASS))
        assertEquals(0.3f, b.band(OGAudioBand.MID))
        assertEquals(0.4f, b.band(OGAudioBand.TREBLE))
    }

    @Test
    fun codec_decodes_partial_json() {
        val spec = OGAudioReactives.decodeSpec("""{"name":"x","attack":0.9}""")
        assertEquals("x", spec.name)
        assertEquals(0.9f, spec.attack)
        assertEquals(0.14f, spec.release, "omitted fields keep their default")
    }

    @Test
    fun codec_tolerates_fences_and_prose() {
        val reply = "Sure!\n```json\n{\"attack\":0.8}\n```"
        val spec = OGAudioReactives.decodeSpecOrNull(reply)
        assertNotNull(spec)
        assertEquals(0.8f, spec.attack)
    }

    @Test
    fun codec_round_trips() {
        val spec = OGAudioReactiveSpec(name = "r", attack = 0.7f, release = 0.2f, gain = 1.2f)
        val back = OGAudioReactives.decodeSpec(OGAudioReactives.encode(spec))
        assertEquals(spec, back)
    }

    @Test
    fun codec_bad_input_is_null() {
        assertNull(OGAudioReactives.decodeSpecOrNull("not json at all"))
    }

    @Test
    fun presets_exist() {
        assertNotNull(OGAudioReactives.preset("snappy"))
        assertNotNull(OGAudioReactives.preset("smooth"))
        assertNotNull(OGAudioReactives.preset("beat"))
        assertNull(OGAudioReactives.preset("nope"))
    }

    @Test
    fun prompt_describes_the_schema() {
        val p = OGAudioReactives.reactivePrompt("snappy on drum hits")
        assertTrue(p.contains("attack") && p.contains("beatSensitivity"), "prompt should name the fields")
        assertTrue(p.contains("snappy on drum hits"), "prompt should carry the instruction")
    }

    private fun noise(sample: Long): Float {
        var x = (sample * 2654435761L + 40503L) and 0x7fffffffL
        x = x xor (x shr 13); x = (x * 1274126177L) and 0x7fffffffL
        return (x.toFloat() / 0x3fffffffL) - 1f
    }
}
