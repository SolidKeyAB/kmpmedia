package com.solidkey.painpoints.audio.reactive

import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.PI

/**
 * Where the reactive layer gets its PCM. Implement this to feed real audio from the platform (an
 * Android `Visualizer` / `AudioRecord`, an iOS `AVAudioEngine` tap, your own player's buffers, …) —
 * exactly the "library provides the maths, the app provides the capture" seam used elsewhere (e.g. the
 * pluggable segmenter for auto-cutout, or the demo's gyro provider). No platform audio is pulled into
 * the library, so there is no new dependency and no microphone permission forced on consumers.
 *
 * [read] is polled once per frame by `rememberOGAudioReactive`.
 */
interface OGAudioSource {
    /**
     * Write the most recent mono samples (roughly `-1..1`) into [out] and return how many were
     * written (`0` if none are available this frame). Implementations should be non-blocking.
     */
    fun read(out: FloatArray): Int
}

/**
 * A built-in, **deterministic** [OGAudioSource] that synthesizes a musical-ish signal: a kick-drum
 * thump on every beat (at [bpm]), a steady mid tone, and bursts of hi-hat noise. It produces real
 * spectral content, so the full analysis path works with **no microphone and no platform code** —
 * ideal for previews, the demo, reproducible marketing captures, and tests.
 *
 * Being deterministic (seeded, clock-driven) means the same [bpm]/[seed] always yields the same
 * samples, so a recorded GIF of a synthetic visualizer is byte-reproducible.
 *
 * @param bpm beats per minute of the synthesized kick.
 * @param sampleRate sample rate to generate at (match your [OGAudioReactiveSpec.sampleRate]).
 * @param seed varies the hi-hat noise.
 */
class OGSyntheticAudioSource(
    val bpm: Float = 120f,
    val sampleRate: Int = 44100,
    val seed: Int = 1,
) : OGAudioSource {

    private var pos = 0L

    /** Restart the internal clock (so playback/capture is reproducible from the top). */
    fun reset() { pos = 0L }

    override fun read(out: FloatArray): Int {
        val beatSamples = (60f / bpm.coerceAtLeast(1f)) * sampleRate
        for (i in out.indices) {
            val sample = pos + i
            val t = sample.toDouble() / sampleRate
            val beatPhase = (sample % beatSamples.toLong()).toFloat() / beatSamples // 0..1 within a beat

            val kickEnv = exp(-beatPhase * 11.0).toFloat()                 // thump decays after each beat
            val bass = kickEnv * sin(2.0 * PI * 55.0 * t).toFloat()        // low kick ~55 Hz
            val mid = 0.3f * sin(2.0 * PI * 660.0 * t).toFloat() *
                (0.5f + 0.5f * sin(2.0 * PI * 0.5 * t).toFloat())          // slowly swelling mid tone
            val hatPhase = (beatPhase * 2f) % 1f
            val hatEnv = exp(-hatPhase * 18.0).toFloat()                   // hi-hat on the half-beat
            val treble = 0.22f * hatEnv * noise(sample)

            out[i] = (0.8f * bass + mid + treble).coerceIn(-1f, 1f)
        }
        pos += out.size
        return out.size
    }

    /** Deterministic white-ish noise in `-1..1` from a sample index. */
    private fun noise(sample: Long): Float {
        var x = (sample * 2654435761L + seed * 40503L) and 0x7fffffffL
        x = x xor (x shr 13); x = (x * 1274126177L) and 0x7fffffffL
        return (x.toFloat() / 0x3fffffffL) - 1f
    }
}
