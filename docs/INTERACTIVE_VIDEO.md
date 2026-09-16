# Interactive video — playback-aware interactions on a running `OGAVPlayer` (design note)

Goal: let apps put **interactivity on a video while it's running** — scrub, tap-to-pause,
double-tap-seek, and interactive elements (hotspots, annotations, shoppable tags, quiz prompts) that
track the playback timeline — with the same "easy path + full control" shape as the rest of KMPMedia.

Status: **Phase 1 (foundation) built** — targeting **1.2.0**. Shipped: `OGPlaybackStatus`,
`OGAVPlayerController.status` (live, reactive), `controller.seekTo`/`seekBy`, and an additive
`onProgress` callback on `OGAVPlayer`, wired via `expect/actual` on Android (ExoPlayer position poll +
`seekTo`) and iOS (AVPlayer position poll + `seek`). Additive — every existing `OGAVPlayer` call
compiles unchanged. Phases 2–3 (gesture flags + `videoOverlay` slot + hotspot helper) remain proposed.

## What `OGAVPlayer` already gives you

- **`OGAVPlayerController`** (detached) — `play() / pause() / stop() / rewind() / dispatch(action)`
  from any button anywhere, even outside the player. Chrome-less by default.
- **`onCustomizeControls: @Composable (OGAVPlayerState, dispatch) -> Unit`** — render your own control
  UI as an overlay; you get the state and a dispatch function.
- **`showDefaultControls`**, **`controlPosition`**, **`displayMovable`** (drag the whole player),
  `flipHorizontally`, plus shape / scale / background / loop config.

So *controlling transport* and *drawing custom chrome* are covered. What's missing is everything that
needs the **live playback position**.

## The gap

1. **No live position / duration / buffered.** `OGAVPlayerState` is a coarse snapshot; there's no
   continuously-updating `positionMs` / `durationMs`. Without it: no scrubber, no progress bar, no
   time-synced anything.
2. **No seek.** The controller can play/pause/stop/rewind but not `seekTo(ms)` / `seekBy(±ms)`.
3. **No tap-on-video with context.** No callback giving the tap location + current time, so tap-to-
   pause, double-tap-seek, and clickable regions must be hand-wired and can't know "where in time."
4. **No time-synced overlay slot** that receives the live position to place elements that track the
   timeline.

## Proposed API (additive)

```kotlin
/** Live, reactive playback status. */
data class OGPlaybackStatus(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,   // 0 until known
    val bufferedMs: Long = 0L,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f
}
```

**1. Expose + control position on the controller** (the hoistable object callers already hold):

```kotlin
class OGAVPlayerController {
    // existing: play/pause/stop/rewind/dispatch
    val status: State<OGPlaybackStatus>     // read live position/duration/isPlaying anywhere
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)               // ±10s buttons, double-tap seek
}
```

Plus an optional param for the callback style: `onProgress: (OGPlaybackStatus) -> Unit`.

**2. A time-synced interactive overlay slot** — Compose content drawn over the video that receives the
live status, so hotspots/annotations/scrubbers are plain Compose (tap handling stays native Compose):

```kotlin
videoOverlay: (@Composable BoxScope.(OGPlaybackStatus) -> Unit)? = null
```

**3. Gesture conveniences in `OGPlayerConfig`** for the common cases (one flag each):

```kotlin
val tapToToggle: Boolean = false,     // tap the video → play/pause
val doubleTapSeekMs: Long = 0L,       // double-tap left/right → seek ∓/± this (0 = off)
```

### Platform mapping

- **Android (ExoPlayer):** `player.currentPosition` / `duration` / `bufferedPosition`; a
  `Player.Listener` for `isPlaying`; `player.seekTo(ms)`. Poll position ~5–10 Hz on the main handler
  while playing (ExoPlayer has no position callback).
- **iOS (AVPlayer):** `addPeriodicTimeObserver(forInterval:)` for position; `currentItem.duration`;
  `rate != 0` for isPlaying; `player.seek(to:)`. The observer already runs at whatever interval we ask.

Both feed one `OGPlaybackStatus` → identical common API.

## What you can then build (all in app Compose, on top of the above)

- **Scrubber / progress bar** — read `controller.status`, draw a slider, `seekTo` on drag.
- **Tap-to-pause / double-tap ±10s** — one config flag each, or your own gestures via the overlay.
- **Time-synced hotspots** — in `videoOverlay`, show a tappable badge only while
  `status.positionMs in start..end`; tap → your action (open link, pause, jump).
- **Chapter markers / captions / shoppable tags / quiz prompts** — same pattern, driven by position.

## Phasing

- **Phase 1 — foundation (recommended first) — ✅ DONE:** `OGPlaybackStatus` + `controller.status` +
  `seekTo`/`seekBy` + `onProgress`. Unlocks scrubbers and *all* programmatic interactivity.
- **Cue points / timers — ✅ DONE:** `OGCue` (point `.at(ms)` + range `.range(from, to)`) fed to
  `OGAVPlayer(cues = …)`, dispatched by the pure `OGCueEngine` off the same position sample. This is
  the "chapter markers / hotspots / timers" primitive — the useful "conditional" (time-range gating)
  falls out of a range cue's enter/exit for free. A general conditionals/rules engine is deliberately
  NOT built (app code + Compose express conditions off the exposed signals).
- **Phase 2 — gestures + overlay (still proposed):** `videoOverlay` slot + `tapToToggle` / `doubleTapSeekMs`.
- **Branching / choose-your-path video (future, optional):** a small state machine over multiple
  sources — a separate opt-in module, not part of this foundation.

## Compatibility & perf

- **Additive:** new optional params + new controller members; every existing `OGAVPlayer` call compiles
  unchanged. Semver minor (→ 1.2.0).
- **Perf:** position tracking is a ~5–10 Hz poll (Android) / periodic observer (iOS) — negligible, and
  only while a controller/overlay/onProgress is actually attached (skip it entirely otherwise).
- Composes with shape-crop, depth (`ogDepth`) and the animated containers exactly as today — the
  overlay draws inside the same shaped/clipped box.
