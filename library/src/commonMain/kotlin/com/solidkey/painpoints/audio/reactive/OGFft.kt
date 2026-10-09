package com.solidkey.painpoints.audio.reactive

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A tiny, dependency-free **radix-2 Cooley-Tukey FFT** (iterative, in-place) for the audio-reactive
 * layer. Pure Kotlin `FloatArray` maths, so it runs identically wherever the library does and is
 * allocation-free on the hot path when the caller reuses its buffers.
 *
 * It is deliberately minimal: a forward transform plus a windowed magnitude helper. Lengths must be a
 * power of two. This is what turns a buffer of PCM samples into a frequency spectrum that
 * [OGAudioAnalyzer] folds into bass / mid / treble bands.
 *
 * Reference: Cooley & Tukey, "An Algorithm for the Machine Calculation of Complex Fourier Series"
 * (1965); the standard iterative bit-reversal formulation.
 */
internal object OGFft {

    /** True when [n] is a power of two (and positive) — the only lengths the transform accepts. */
    fun isPow2(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

    /**
     * In-place forward FFT of the complex signal held in [re] / [im] (same length, a power of two).
     * On return they hold the transform. Pure and deterministic.
     */
    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        require(isPow2(n) && im.size == n) { "fft needs power-of-two arrays of equal length (was ${re.size}/${im.size})" }

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        // Butterflies, stage by stage.
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curR = 1f
                var curI = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val tr = curR * re[b] - curI * im[b]
                    val ti = curR * im[b] + curI * re[b]
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr;        im[a] += ti
                    val nextR = curR * wr - curI * wi
                    curI = curR * wi + curI * wr
                    curR = nextR
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Fill [mag] (length `n/2`) with the magnitude spectrum of real [samples] (length `n`, a power of
     * two) after multiplying by [window]. Uses the scratch [re] / [im] buffers (length `n`) so no
     * allocation happens per call. Bin `k` corresponds to frequency `k * sampleRate / n` Hz.
     */
    fun magnitudes(samples: FloatArray, window: FloatArray, re: FloatArray, im: FloatArray, mag: FloatArray) {
        val n = re.size
        for (i in 0 until n) {
            re[i] = (if (i < samples.size) samples[i] else 0f) * window[i]
            im[i] = 0f
        }
        fft(re, im)
        val half = n / 2
        for (k in 0 until half) {
            mag[k] = sqrt(re[k] * re[k] + im[k] * im[k])
        }
    }

    /** A Hann analysis window of length [n] (reduces spectral leakage). Build once, reuse. */
    fun hannWindow(n: Int): FloatArray =
        FloatArray(n) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (n - 1))).toFloat() }
}
