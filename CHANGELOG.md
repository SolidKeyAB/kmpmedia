# Changelog

All notable changes to **KMPMedia** are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and the project aims for
[Semantic Versioning](https://semver.org/).

## [1.3.0] — 2026-09-16

> Adds a **free-form polygon lasso** clip shape — clip an image (or any composable) to an arbitrary outline of line segments, not just the built-in circle/triangle/…. Purely additive: `OGImageView` gains one optional trailing param, every existing call compiles unchanged.

### Added
- `OGPolygonShape(points: List<OGPoint>)` (+ `OGPolygonShape.of(vararg pairs)`) in `com.solidkey.painpoints.shape` — a Compose `Shape` built from an ordered list of vertices in **normalized `0..1`** space joined by straight line segments (auto-closed). It's the **AI-friendly** clip primitive: the outline is just data, so a segmentation model or an on-image finger-draw can produce it with no external editor. `<3` points → empty outline (never throws); out-of-range coords are clamped into the box.
- `OGPoint(x, y)` — a normalized vertex (top-left origin).
- `scalePolygonPoints(points, width, height)` — the pure, unit-tested helper the shape wraps (maps + clamps points to a box).
- `OGImageView(..., clipShape: Shape? = null)` — when non-null, clips to that arbitrary outline (e.g. an `OGPolygonShape`), taking precedence over `displayShape`/`cornerRadius`. Same GPU `Modifier.clip` mask = no runtime cost for a still image.

### Notes
- Backward-compatible: `clipShape` is an optional trailing param defaulting to `null`; `displayShape`/`cornerRadius` behave exactly as before. Because `OGPolygonShape` is an ordinary `Shape`, it also works with `Modifier.clip(...)` on any composable (including a video surface). See `docs/POLYGON_SHAPE.md`.
- The demo's **"Add Your Head to a Body"** screen uses it (the "Head lasso" chip) to cut out just the head from a photo.

## [1.2.0] — 2026-09-15

> Adds **interactive video** — a live, reactive playback timeline plus timed cue points, all in plain Compose. Purely additive: every existing `OGAVPlayer` call compiles unchanged (the two new params are optional and trailing).

### Added
- `OGPlaybackStatus(isPlaying, positionMs, durationMs, bufferedMs)` + `.progress` (`0f..1f`) — a live, reactive snapshot of the playback timeline, pushed continuously (~5 Hz) on **both** Android (ExoPlayer) and iOS (AVPlayer). Everything a scrubber, progress bar or time display needs, without touching the platform players.
- `OGAVPlayerController.status: State<OGPlaybackStatus>` — read the live status from any `@Composable` and it recomposes as the video plays. Plus `controller.seekTo(positionMs)` and `controller.seekBy(deltaMs)` (both clamped to `[0, durationMs]`) for scrub bars and skip/seek buttons.
- `OGAVPlayer(..., onProgress = { status -> … })` — an optional callback that receives the same `OGPlaybackStatus` stream, for callers not using a controller.
- `OGCue` + `OGCueEngine` — timed triggers fired off the playback position via `OGAVPlayer(..., cues = …)`. **Point cues** fire once as playback crosses a time (including a seek that jumps over it); **range cues** fire `onEnter`/`onExit` as playback enters/leaves `[atMs, untilMs)`. Chapter markers, captions, shoppable tags, "skip intro", analytics beacons — all plain callbacks, no manual position math.

### Notes
- Backward-compatible (new package symbols only): `onProgress` and `cues` are optional, defaulted, trailing params — no existing call site changes.
- The ~5 Hz sampling loop runs **only** while a controller is attached or `onProgress`/`cues` are supplied — negligible battery cost otherwise.
- `bufferedMs` is reported on Android; on iOS it is currently `0` (position/duration/isPlaying — everything a scrubber needs — are populated on both platforms). See `docs/INTERACTIVE_VIDEO.md`.

## [1.1.0] — 2026-09-14

> Adds **depth / layer management** — a small, purely additive `depth` package. No existing API changes; a drop-in for any composable.

### Added
- `Modifier.ogDepth(depth, focalDepth, config)` — place any composable on a `0f..1f` front-to-back axis relative to a focal plane. Derives three effects from that one value: **z-order** (`zIndex`, fly over/under), **depth-of-field** (`blur` + dim that grow with focal distance), and optional **parallax scale**.
- `OGDepthObject { }` (wrapper) and `OGDepthField { }` (+ `LocalOGFocalDepth`) — declare a field of layered content that shares one focal plane; children only state their own depth.
- `OGDepthConfig` (tuning: `maxBlur`, `minAlpha`, `dimFalloff`, `blurContent`, `depthScale`) with an `OGDepthConfig.Video` preset (dim + z-order, **no blur** — safe over `TextureView`/`AVPlayerLayer`) and `OGDepthScale` for parallax endpoints.

### Notes
- Backward-compatible (new package, new symbols only). Composes with `OGImageView`, `OGAVPlayer`, `OGAnimatedImage` and `OGAnimatedContainer` (validated in the demo). An in-focus object with the default config adds **only** `zIndex` — no extra compositing layer. See `docs/DEPTH_LAYER.md` for the perf + compatibility analysis.
- On Android < 31 `Modifier.blur` is a no-op (no crash) → depth-of-field degrades to z-order + dim; iOS blurs on every version.

## [1.0.3] — 2026-09-13

> First public release — live on Maven Central as `se.solidkey:kmpmedia-lib:1.0.3` (Android AAR + iOS `iosArm64`/`iosSimulatorArm64` klibs + KMP metadata).

### Added
- `OGAnimatedImage` — wrap any static image, or a plain SVG with no `<animate>` tags, and animate it at runtime (`SCALE` / `ROTATE` / `FADE` / `TRANSLATE`) without changing the source.
- Shape-crop for images (`OGImageView` gains `displayShape`, `cornerRadius`, `contentScale`, `alignment`) and for video (`OGPlayerConfig.displayShape`) — clip content to circle / triangle / diamond / … with a GPU clip.
- `onError` wired end-to-end through image, SVG, audio and Android video loaders.
- iOS video now honors `displayShape` and `displayMovable`.

### Fixed
- SVG path math: smooth-curve (`S`/`T`) reflected control points, arc start point, zero-radius arc crash, empty-polygon crash, and the offset rect stroke.
- SVG inline-style parsing no longer drops values containing `:` (e.g. `url(...)`).
- Animation player now renders each element's real fill/stroke instead of hardcoded values.

### Changed
- Publishing wired for Maven Central (vanniktech) and GitHub Packages; artifact signing is applied only when a signing key is present.
- Removed debug logging noise from the library's happy path.
