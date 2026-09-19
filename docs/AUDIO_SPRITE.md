# Audio Sprites — `com.solidkey.painpoints.audio.playing.OGAudioSprite` (KMPMedia 1.5.0)

The existing [`OGAudioPlayer`](../library/src/commonMain/kotlin/com/solidkey/painpoints/audio/playing/OGAudioPlayer.kt)
plays a *whole* audio file: load → play / pause / stop. `OGAudioSprite` covers the other common need —
playing **parts** of a single file, **on demand**, for events.

Pack many short sounds into one asset (a "collect" chime, a "hit" thud, a "powerup" sweep laid out back
to back) and trigger any of them by id: `sprite.play("hit")`. It's the audio equivalent of a texture
atlas / sprite sheet — one file, many addressable clips. A small pool of voices lets clips **overlap**
(a collect chime can ring while a hit thud is still playing) instead of cutting each other off the way a
single player would.

## Why one file?

- **One decode, many sounds** — a single small asset instead of a dozen tiny files to ship, load, and
  manage.
- **Low-latency events** — voices are created up front, so a game hit or a UI tap fires without a
  per-sound load.
- **Reuses the stack you already ship** — Android plays each window through **media3/ExoPlayer**
  clipping (the same player the video component uses); iOS through `AVPlayer` seek +
  `AVPlayerItem.forwardPlaybackEndTime`.

## The clip model

An [`OGAudioClip`](../library/src/commonMain/kotlin/com/solidkey/painpoints/audio/playing/OGAudioClip.kt)
is a named, time-bounded window `[startMs, endMs]` inside the file:

```kotlin
OGAudioClip("collect", startMs = 0,    endMs = 400)   // 0.0s – 0.4s
OGAudioClip("hit",     startMs = 400,  endMs = 950)   // 0.4s – 0.95s
OGAudioClip("powerup", startMs = 950)                 // 0.95s – end of file (endMs defaults to END)
```

`endMs` defaults to `OGAudioClip.END` (= play to the end of the file); when given, it must be strictly
greater than `startMs`. Clips validate their inputs (blank id / negative start / `end <= start` throw).

## API

```kotlin
val sprite = OGAudioSprite.create()            // @Composable factory

sprite.load(
    source = OGSource.Resource("sfx"),         // one file: Resource / FilePath / Url
    clips = listOf(
        OGAudioClip("collect", 0, 400),
        OGAudioClip("hit", 400, 950),
        OGAudioClip("powerup", 950),
    ),
    config = OGAudioSpriteConfig(voices = 4, volume = 1f),
    onError = { msg -> /* bad source / decode error / unknown clip id */ },
)

sprite.play("hit")        // fire a clip on the next free voice (overlaps up to `voices`)
sprite.stop("hit")        // silence voices currently playing "hit"
sprite.stopAll()          // silence everything
sprite.setVolume(0.5f)    // master volume, 0f..1f
sprite.clipIds            // Set<String> of loaded clip ids
sprite.release()          // free all voices + platform resources
```

### Lifecycle

Create the sprite in composition and **release it when the screen leaves** — e.g.:

```kotlin
val sprite = OGAudioSprite.create()
DisposableEffect(Unit) {
    sprite.load(OGSource.Resource("sfx"), clips)
    onDispose { sprite.release() }
}
```

## Voices & overlap

`OGAudioSpriteConfig.voices` (default `4`) is how many clips may sound **at the same time**. Each voice
is an independent player instance; triggers are handed out **round-robin**, so the (voices+1)-th
overlapping trigger reuses — and cuts off — the oldest voice. That "steal the oldest voice" policy is
the standard behaviour for sound effects. The allocation logic lives in one platform-free, unit-tested
helper (`OGVoiceRotor`) shared by both actuals, so Android and iOS behave identically.

## Platform notes

| | Android | iOS |
|---|---|---|
| Engine | media3/ExoPlayer, one per voice | `AVPlayer`, one per voice |
| Window | `MediaItem.ClippingConfiguration(startMs, endMs)` | `seekToTime(startMs)` + `forwardPlaybackEndTime(endMs)` |
| Volume | `ExoPlayer.volume` | `AVPlayer.volume` |

- **Android** short SFX deliberately do **not** request audio focus — a per-trigger focus
  request/abandon would add latency and duck the user's music. (The whole-file `OGAudioPlayer` still
  requests focus, which is right for it.)
- **iOS** resource sources default to the `mp3` extension unless `OGSource.Resource(name, extension)`
  gives one, matching `OGAudioPlayer`.

## When to use which

| Need | Use |
|---|---|
| Play a whole track / clip, pause & resume, loop | `OGAudioPlayer` |
| Fire short sounds on events from one packed file, overlapping | `OGAudioSprite` |
