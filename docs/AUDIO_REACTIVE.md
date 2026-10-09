# Audio-reactive vectors

Make any vector **react to sound**: bind live audio (bass / mid / treble / overall level, plus a beat
flag) to any visual parameter, so your UI pulses to music, a loader breathes with a voice, or a game
reacts to its own soundtrack. It is the same "render at 60fps, parse once" philosophy as the rest of
KMPMedia, applied to audio: a pure-Kotlin FFT turns PCM into frequency bands, and you map those bands
onto transforms or spec values.

Package: `com.solidkey.painpoints.audio.reactive`. New in **1.33.0**.

## How it fits the ground rules

- **Pure + deterministic.** The FFT ([`OGFft`]), the band analysis ([`OGAudioAnalyzer`]) and the
  built-in [`OGSyntheticAudioSource`] are plain Kotlin maths in `commonMain`, unit-tested on JVM + iOS.
  The same input samples always give the same bands.
- **Zero new dependency, no forced permission.** The library does **not** capture audio. It defines a
  tiny [`OGAudioSource`] seam and you feed it PCM from wherever you like (an Android `Visualizer` /
  `AudioRecord`, an iOS `AVAudioEngine` tap, your own player's buffers). This is the same
  "library provides the maths, the app provides the capture" pattern as the pluggable segmenter for
  auto-cutout. For previews, demos and tests there is a built-in synthetic source so nothing extra is
  needed to see it work.
- **60fps.** Analysis buffers are preallocated and reused; the reactive modifier maps a band through a
  `graphicsLayer`, so the content re-renders without recomposing.

## Quick start

```kotlin
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.solidkey.painpoints.audio.reactive.*

// 1. A source. Use the built-in synthetic one, or implement OGAudioSource over your mic/music.
val source = remember { OGSyntheticAudioSource(bpm = 120f) }

// 2. Analyse it on the frame clock -> live bands.
val bands by rememberOGAudioReactive(source)

// 3a. Make ANY composable pulse. The lambda picks the band; it is read inside a graphicsLayer,
//     so this is cheap on an SVG, a particle view, a photo, text, anything.
OGSVGView(
    source = logo,
    modifier = Modifier.ogAudioReactive({ bands.bass }, scale = 1f..1.3f, alpha = 0.7f..1f),
)

// 3b. Or drive a spec value directly on a cheap-to-rebuild view.
OGSlashView(OGSlashes.WATER.copy(width = 0.12f * (1f + bands.treble)))

// 3c. Or fire something on the beat.
if (bands.beat) { confetti.burst() }
```

## Feeding real audio

Implement [`OGAudioSource.read`] to copy your latest mono samples (roughly `-1..1`) into the buffer:

```kotlin
class MicSource(/* platform recorder */) : OGAudioSource {
    override fun read(out: FloatArray): Int {
        // non-blocking: write the newest samples, return how many you wrote (0 if none yet)
        return recorder.drainInto(out)
    }
}
```

On Android a `Visualizer` attached to your playing `MediaPlayer`/`ExoPlayer` session gives you PCM
without the microphone permission; `AudioRecord` captures the mic. On iOS an `AVAudioEngine` input-node
tap gives you the mic buffers. These live in your app (or an `expect`/`actual` in your shared code),
exactly like the demo's gyro/tilt provider, so the library stays dependency-free.

## Tuning the response

[`OGAudioReactiveSpec`] is a plain `@Serializable` config. The two knobs that shape the feel:

- `attack` (0..1) — how fast a band rises toward a louder value. Higher = snappier.
- `release` (0..1) — how fast it falls back. Lower = longer, smoother tails.

Ready-made profiles via [`OGAudioReactives`]: `snappy` (punchy on hits), `smooth` (flowing), `beat`
(tuned so `bands.beat` fires cleanly). A profile is just data, so a designer or a model can author a
`.reactive` pack:

```kotlin
val spec = OGAudioReactives.decodeSpec("""{"name":"punchy","attack":0.9,"release":0.25}""")
val bands by rememberOGAudioReactive(source, spec)
// OGAudioReactives.reactivePrompt("snappy on every drum hit") hands a model the exact contract.
```

## What the bands mean

`OGAudioBands` is four normalized `0..1` signals plus a flag:

| field    | meaning |
|----------|---------|
| `level`  | overall loudness (RMS) |
| `bass`   | low-frequency energy (below `bassMaxHz`, default 250 Hz) |
| `mid`    | mid energy (up to `midMaxHz`, default 4 kHz) |
| `treble` | high energy (up to `trebleMaxHz`, default 16 kHz) |
| `beat`   | `true` on the frame an onset is detected (spectral flux) |

## Honest limits

- You bring the audio capture. The library ships the analysis + binding + a synthetic source, not a
  recorder, so there is no bundled platform audio and no permission imposed on consumers.
- Beat detection is a lightweight spectral-flux onset detector, not a full tempo tracker. It is great
  for "pulse/fire on hits"; it does not estimate BPM.
- The FFT is deterministic per platform, but unlike the byte-identical GIF/MP4 export path this feature
  reacts to live input, so there is no cross-platform byte guarantee (there is nothing to export).

## References

- Cooley & Tukey, "An Algorithm for the Machine Calculation of Complex Fourier Series" (1965) — the FFT.
- Hann window, and spectral-flux onset detection (Dixon, "Onset Detection Revisited", 2006) for `beat`.
