package com.solidkey.painpoints.audio.reactive

import kotlin.math.max
import kotlin.math.sqrt

/** The reactive bands you can bind a visual to. [LEVEL] is overall loudness; the rest are frequency bands. */
enum class OGAudioBand { LEVEL, BASS, MID, TREBLE }

/**
 * One frame of analysed audio: four normalized `0..1` signals plus a [beat] flag. Produced by
 * [OGAudioAnalyzer.process] and the value you bind visuals to (see `Modifier.ogAudioReactive`).
 *
 * @property level overall loudness (RMS), 0..1.
 * @property bass low-frequency energy, 0..1.
 * @property mid mid-frequency energy, 0..1.
 * @property treble high-frequency energy, 0..1.
 * @property beat `true` on the frame an onset (a transient / "hit") was detected.
 */
data class OGAudioBands(
    val level: Float = 0f,
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val beat: Boolean = false,
) {
    /** The value of a named [band] — handy for data-driven bindings. */
    fun band(band: OGAudioBand): Float = when (band) {
        OGAudioBand.LEVEL -> level
        OGAudioBand.BASS -> bass
        OGAudioBand.MID -> mid
        OGAudioBand.TREBLE -> treble
    }

    companion object {
        /** All-silent bands. */
        val Silent = OGAudioBands()
    }
}

/**
 * Turns buffers of PCM samples into smoothed, normalized [OGAudioBands]. Call [process] once per frame
 * with the latest samples; it windows them, runs an [OGFft], folds the spectrum into bass / mid /
 * treble, measures overall level, detects beats via spectral flux, and applies an attack/release
 * envelope so the output moves musically instead of flickering.
 *
 * It is a small stateful object (it remembers the previous spectrum and the smoothed values) but all
 * maths is pure and deterministic: the same sequence of input buffers always yields the same bands.
 * Buffers are preallocated from [OGAudioReactiveSpec.fftSize], so steady-state [process] does not
 * allocate. Build one and reuse it; call [reset] to clear the smoothing/beat history.
 *
 * The real PCM is supplied by an [OGAudioSource] (your mic/music tap, or the built-in
 * [OGSyntheticAudioSource]); the analyzer itself knows nothing about platform audio.
 */
class OGAudioAnalyzer(val spec: OGAudioReactiveSpec = OGAudioReactiveSpec()) {

    private val n: Int = spec.fftSize.also {
        require(OGFft.isPow2(it)) { "fftSize must be a power of two (was $it)" }
    }
    private val binHz: Float = spec.sampleRate.toFloat() / n
    private val refMag: Float = n * 0.25f // ~magnitude of a full-scale single-bin sine through the Hann window

    private val window = OGFft.hannWindow(n)
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val mag = FloatArray(n / 2)
    private val prevMag = FloatArray(n / 2)

    private var sLevel = 0f
    private var sBass = 0f
    private var sMid = 0f
    private var sTreble = 0f
    private var fluxAvg = 0f
    private var primed = false

    /** Clear all smoothing and beat-detection history back to silence. */
    fun reset() {
        re.fill(0f); im.fill(0f); mag.fill(0f); prevMag.fill(0f)
        sLevel = 0f; sBass = 0f; sMid = 0f; sTreble = 0f; fluxAvg = 0f; primed = false
    }

    /**
     * Analyse the latest [samples] (mono, roughly `-1..1`). If more than [OGAudioReactiveSpec.fftSize]
     * are given only the most recent window is used; if fewer, it is zero-padded. Returns the smoothed
     * [OGAudioBands] for this frame.
     */
    fun process(samples: FloatArray): OGAudioBands {
        val count = samples.size
        // RMS loudness on the raw (unwindowed) tail, and fill the FFT input with the windowed tail.
        var sumSq = 0f
        for (i in 0 until n) {
            val s = when {
                count >= n -> samples[count - n + i]
                i < count -> samples[i]
                else -> 0f
            }
            sumSq += s * s
            re[i] = s * window[i]
            im[i] = 0f
        }
        OGFft.fft(re, im)

        val half = n / 2
        var bassPeak = 0f
        var midPeak = 0f
        var treblePeak = 0f
        var flux = 0f
        val nyquist = spec.sampleRate * 0.5f
        val trebleTop = minOf(spec.trebleMaxHz, nyquist)
        for (k in 1 until half) {
            val m = sqrt(re[k] * re[k] + im[k] * im[k])
            val hz = k * binHz
            when {
                hz < spec.bassMaxHz -> bassPeak = max(bassPeak, m)
                hz < spec.midMaxHz -> midPeak = max(midPeak, m)
                hz < trebleTop -> treblePeak = max(treblePeak, m)
            }
            val d = m - prevMag[k]
            if (d > 0f) flux += d
            prevMag[k] = m
        }

        val g = spec.gain
        val tLevel = (sqrt(sumSq / n) * g).coerceIn(0f, 1f)
        val tBass = (bassPeak / refMag * g).coerceIn(0f, 1f)
        val tMid = (midPeak / refMag * g).coerceIn(0f, 1f)
        val tTreble = (treblePeak / refMag * g).coerceIn(0f, 1f)

        sLevel = envelope(sLevel, tLevel)
        sBass = envelope(sBass, tBass)
        sMid = envelope(sMid, tMid)
        sTreble = envelope(sTreble, tTreble)

        // Beat = a spectral-flux spike well above the running average flux.
        val fluxNorm = flux / half
        val beat = primed && fluxNorm > fluxAvg * spec.beatSensitivity && fluxNorm > 1e-4f
        fluxAvg = if (!primed) fluxNorm else fluxAvg * 0.92f + fluxNorm * 0.08f
        primed = true

        return OGAudioBands(sLevel, sBass, sMid, sTreble, beat)
    }

    private fun envelope(current: Float, target: Float): Float {
        val k = if (target > current) spec.attack else spec.release
        return (current + (target - current) * k.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }
}
