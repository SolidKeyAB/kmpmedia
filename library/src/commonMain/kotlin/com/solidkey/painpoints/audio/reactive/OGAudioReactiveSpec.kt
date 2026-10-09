package com.solidkey.painpoints.audio.reactive

import kotlinx.serialization.Serializable

/**
 * Data-defined configuration for the [OGAudioAnalyzer] — how PCM is turned into reactive bands. Like
 * every other KMPMedia pack ([com.solidkey.painpoints.look.OGLookSpec],
 * [com.solidkey.painpoints.style.OGStyleSpec], …) it is a plain `@Serializable` value you can tweak,
 * `copy()`, ship as a `.reactive` JSON pack, or have a model author via [OGAudioReactives.reactivePrompt].
 *
 * The defaults are a good general-purpose "visualizer" response. The two smoothing knobs are the ones
 * worth tuning for feel:
 *  - [attack] — how fast a band rises toward a louder value (0..1 per update; higher = snappier).
 *  - [release] — how fast it falls back toward quiet (0..1; lower = longer, smoother tails).
 * A snappy, percussive look uses a high [attack] and low [release]; a smooth, flowing look lowers both.
 *
 * @property fftSize analysis window size in samples (a power of two; larger = finer frequency bins).
 * @property sampleRate PCM sample rate the source delivers, in Hz.
 * @property attack rise smoothing 0..1 (fraction of the gap closed per update when getting louder).
 * @property release fall smoothing 0..1 (fraction closed per update when getting quieter).
 * @property gain output multiplier applied before the 0..1 clamp (lift a quiet source into range).
 * @property beatSensitivity spectral-flux threshold multiplier for [OGAudioBands.beat] (higher = fewer beats).
 * @property bassMaxHz upper edge of the bass band.
 * @property midMaxHz upper edge of the mid band.
 * @property trebleMaxHz upper edge of the treble band (clamped to Nyquist).
 */
@Serializable
data class OGAudioReactiveSpec(
    val name: String? = null,
    val fftSize: Int = 1024,
    val sampleRate: Int = 44100,
    val attack: Float = 0.6f,
    val release: Float = 0.14f,
    val gain: Float = 1.5f,
    val beatSensitivity: Float = 1.5f,
    val bassMaxHz: Float = 250f,
    val midMaxHz: Float = 4000f,
    val trebleMaxHz: Float = 16000f,
)
