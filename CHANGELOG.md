# Changelog

All notable changes to **KMPMedia** are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and the project aims for
[Semantic Versioning](https://semver.org/).

## [1.12.0] — 2026-09-30

> **Soft & gradient masks, multi-region clips.** A clip mask no longer has to be one shape with a hard edge. Two additions round out the shape system: (1) **soft masks** — `Modifier.ogSoftClip(shape, feather)` fades a photo/GIF out over a feathered band at the shape's edge (a vignette), and `Modifier.ogGradientMask(brush)` multiplies its alpha by any gradient (edge fade, spotlight); both are surfaced on `OGImageView` as `softEdge` / `maskBrush`. (2) **multi-region clips** — `OGMultiRegionShape` combines several placed sub-shapes with a path op (union / intersect / difference / xor), so one clip can show two portholes, or a diamond with a circular bite. `OGMultiRegionShape` is an ordinary Compose `Shape`, so it drops into the exact `clipShape` slot a single shape already uses — **no media-surface API changed** — and works for a photo, an animated GIF *and* a running video. Built for the 60fps bar: multi-region combines its outlines with `Path.op` **once per size** (never per frame), and soft masks are a single offscreen `BlendMode.DstIn` pass — **no blur, no `RenderEffect`, no Android API-level floor**. Purely additive: two new packages / two new optional `OGImageView` params, nothing existing changed. This is the "soft & gradient masks, multi-region clips" item of **Bet 2 — Living shapes** on the roadmap.

### Added
- `com.solidkey.painpoints.mask.ogSoftClip(shape: Shape, feather: Dp)` — a `Modifier` that clips to `shape` with a **feathered edge**: opaque through the interior, fading to transparent over the last `feather` near the boundary (`feather = 0.dp` = hard clip). The falloff is radial about the shape's bounds.
- `com.solidkey.painpoints.mask.ogGradientMask(brush: Brush)` — a `Modifier` that multiplies content alpha by an arbitrary `Brush` gradient (run it to `Color.Transparent` for a fade / spotlight / vignette).
- `OGImageView(softEdge: Dp = 0.dp, maskBrush: Brush? = null)` — soft-edge feather and/or a gradient mask on a photo or GIF, combined with the shape clip in one offscreen pass.
- `OGMultiRegionShape(regions: List<OGClipRegion>, op: OGClipOp = UNION)` — a clip `Shape` made of several placed regions, combined with a path op. Factories `OGMultiRegionShape.union(vararg shapes)` and `.of(vararg regions, op)`.
- `OGClipRegion(shape, left, top, right, bottom)` — one sub-shape placed in a normalized `0..1` sub-rectangle of the box (defaults to full box). `OGClipOp` = `UNION` / `INTERSECT` / `DIFFERENCE` / `XOR`. Value `equals`/`hashCode` on `OGMultiRegionShape` for cache / Compose-skip hits.

### Notes
- **Soft masks apply to Compose-drawn content** — photos and GIFs in `OGImageView`, or any composable — not to `OGAVPlayer` video, which renders through a native surface (Android `TextureView` / iOS `AVPlayerLayer`) outside the Compose compositing layer, so a DstIn mask over it is unreliable (the same reason blur over a video surface doesn't repaint). Video is clipped by a hard shape or an `OGMultiRegionShape`, both of which are GPU clips it honors. See `docs/SOFT_MASKS.md`; the demo's **Soft & multi-region masks** screen drives a feathered vignette, a gradient fade, and two-porthole / bite multi-region clips.

## [1.11.0] — 2026-09-30

> **AI hooks — "describe → shape / patch."** KMPMedia's vector primitives are just data — a free-form polygon [lasso](docs/POLYGON_SHAPE.md) and a runtime [SVG node patch](docs/RUNTIME_SVG.md) — so a language model can produce them. `OGAiVector` makes that first-class: a **stable, provider-agnostic JSON schema** for those primitives with parse/serialize both directions, plus prompt builders that hand a model the exact contract to fill in. Ask for *"a five-pointed star"* or *"point the gauge needle to 80% and make it red"* and the reply drops straight into a `clipShape` or an `overrides` map. The library makes **no network calls and bundles no AI SDK** — it defines the contract and does the JSON; you own the model and the call — so it stays zero-dependency and works with any model. Purely additive: a new `com.solidkey.painpoints.ai` package, nothing existing changed. This is the first shipped piece of **Bet 1 — Runtime & AI-editable vector**'s "AI hooks" item.

### Added
- `com.solidkey.painpoints.ai.OGAiVector` — the entry point. `polygonPrompt(instruction)` / `svgPatchPrompt(instruction, nodeIds)` build a ready-to-send prompt stating the JSON contract; `decodePolygon` / `decodeSvgPatch` (and `…OrNull` variants) turn a model's reply into a live `OGPolygonShape` / `Map<String, OGSvgNodeOverride>`; `encodePolygon` / `encodeSvgPatch` serialize the other way. Decoding tolerates the markdown code fences and surrounding prose models routinely add (`extractJson`).
- `OGPolygonSpec`, `OGNodeOverrideSpec`, `OGSvgPatchSpec`, `OGPointSpec` — `@Serializable` DTOs mirroring the Compose-facing primitives but with model-friendly plain values (colors as SVG strings, coordinates as floats). Each has `toShape()` / `toOverride()` / `toOverrides()` and a `from(...)` companion for the reverse.
- `kotlinx-serialization-json` is now a declared dependency (the same serialization family already in use) exposing the `Json` codec via `OGAiVector.json`.

### Notes
- Colors are parsed with the **same** parser the SVG renderer uses, so a model may emit `#RGB` / `#RRGGBB` / `#AARRGGBB` / `rgb(...)` / a name like `red`. All conversion runs at generate/patch time, never per frame, so it never touches the 60fps hot path. See `docs/AI_HOOKS.md`; the demo's **AI vector** screen drives a lasso clip and a live SVG patch from model / pasted JSON.

## [1.10.0] — 2026-09-27

> **Shape-morph clips.** The clip mask itself can now animate. `OGMorphShape(from, to, progress)` is a Compose `Shape` whose outline tweens from one shape to another as you drive `progress` `0f`→`1f`, so a **running video or animated GIF** can shift circle → diamond → triangle → [lasso](docs/POLYGON_SHAPE.md) *while it keeps playing*. It drops into the exact slot a static shape already uses — `OGImageView(clipShape = …)`, `OGPlayerConfig(clipShape = …)`, or any `Modifier.clip(…)` — so **no media-surface API changed**. Built for the 60fps bar: each endpoint outline is resampled to N perimeter points **once** (cached by shape + size) and only the point lists are lerped per frame — the same sample-once / lerp-per-frame budget as the 1.9.0 SVG path morph. Clipping *moving* media to a *morphing* outline, identical on Android and iOS, exists nowhere else in KMP. This is the first item of **Bet 2 — Living shapes** on the roadmap.

### Added
- `OGMorphShape(from: Shape, to: Shape, progress: Float, sampleCount: Int = OG_MORPH_SAMPLES)` — a clip `Shape` that morphs between any two shapes. `progress <= 0` / `>= 1` pass the raw endpoint outline straight through (zero resample cost).
- `ogMorphSequence(stops: List<Shape>, progress: Float, …)` — one `0f..1f` progress that walks a whole chain of stops (e.g. `circle → diamond → lasso`), returning the active adjacent-pair morph.
- `OG_MORPH_SAMPLES` — the default perimeter-sample count (96).
- Value `equals`/`hashCode` on `TriangleShape` / `DiamondShape` / `OGPolygonShape`, so a re-created endpoint is a resample-cache (and Compose-skip) hit.

### Notes
- Endpoints can be any `Shape` (built-ins, `RoundedCornerShape`, an `OGPolygonShape` lasso). Different vertex counts still tween — both are resampled to `sampleCount` points. The target samples are cyclically re-aligned (and reversed if it fits better) to the source once per resample, so outlines that start at different corners or wind opposite ways morph without swirling. A degenerate endpoint (empty outline) snaps to the start shape. See `docs/SHAPE_MORPH_CLIPS.md`; the demo's **Morph the clip itself** screen drives a video + GIF + gradient tile through one morphing mask.

## [1.9.0] — 2026-09-24

> **Path morphing.** A `<path>` can now tween smoothly between two shapes at runtime. Set `OGSvgNodeOverride.pathDataTo` to a target `d` and drive `morphProgress` `0f`→`1f` from any Compose animation, and the node interpolates from its current geometry (the original, or `pathData` if also set) to the target. Built for the "must run in a game at 60fps" bar: both endpoint `d` strings are parsed **once** (cached), so each frame only interpolates floats — no per-frame re-parse, no string work, no allocation churn. Purely additive: two new optional fields defaulting to no-morph, so every existing call is unchanged. Same code on Android and iOS.

### Added
- `OGSvgNodeOverride.pathDataTo: String?` — the morph *target* path `d`. When set on a node holding `<path>` geometry, the node tweens toward this shape.
- `OGSvgNodeOverride.morphProgress: Float` — the tween position in `0f..1f` (`0f` = start, `1f` = target). Ignored unless `pathDataTo` is set; clamped to range. Drive it from `animateFloatAsState` / `rememberInfiniteTransition` for a live morph.

### Notes
- Morphing is a coordinate tween, so the two paths must share the same command **structure** (identical count and command types, index for index). A structurally-mismatched pair snaps at the halfway point rather than crashing or drawing a garbled shape — the same constraint every SVG morph tool imposes.
- Internally `OGSVGView` keeps a per-SVG parse cache so the two endpoint `d` strings survive the per-frame recompositions a morph triggers; the cost profile matches the already-shipped animated rotation override. The parsed source tree is never mutated. See `docs/RUNTIME_SVG.md`; the demo's **Runtime-editable SVG** screen adds a live star⇄ring morph driven by an infinite transition.

## [1.8.0] — 2026-09-23

> **Runtime path geometry.** Runtime-editable SVG can now change a node's **shape**, not just its paint and transform: an `OGSvgNodeOverride` may carry a replacement path `d`, and the addressed `<path>` re-parses to the new geometry live — no new node, no re-parse of the whole SVG. This is the building block for path morphing (the next roadmap step). Purely additive: a new optional `pathData` field defaulting to `null`, so every existing call is unchanged. Same code on Android and iOS.

### Added
- `OGSvgNodeOverride.pathData: String?` — a replacement path `d`. When set on a node that holds `<path>` geometry, the node redraws with this geometry instead of its original; `null` (default) keeps the original. Ignored on non-path nodes.

### Notes
- The new `d` is re-parsed against the SVG's viewBox (same user-space as the source), so the viewport transform, any paint override and any transform override on the node still apply on top. Reuses the existing `parsePathCommands`; the resolve rebuilds a fresh shapes list and never mutates the parsed source tree, so switching `pathData` (or clearing it) always re-resolves from the original `d`.
- Because the geometry is fully runtime-supplied, callers can compute a `d` per frame (e.g. interpolate between two same-structure paths) as a stop-gap until the first-class path-morphing tween lands. See `docs/RUNTIME_SVG.md`; the demo's **Runtime-editable SVG** screen adds a live ▶/❚❚/■ icon whose one `<path>` swaps geometry from the override map.

## [1.7.0] — 2026-09-23

> **Runtime-editable SVG.** KMPMedia parses an SVG into a live node tree — now you can address any node by its `id` and change its attributes at runtime, bound to Compose state, turning a static `.svg` into a **live template**: gauges, charts, progress rings, status icons. The source is parsed **once**; only the overrides change and it redraws live. Purely additive — a new optional `overrides` param on `OGSVGView` defaulting to empty, so every existing call is unchanged. Same code on Android and iOS.

### Added
- `OGSvgNodeOverride` in `com.solidkey.painpoints.image.svg` — a per-node override (every field nullable, `null` = keep the original): `fill`, `stroke`, `strokeWidth`, `translateX` / `translateY`, `rotation` (with `rotationCx` / `rotationCy` pivot), `scaleX` / `scaleY`.
- `OGSVGView(overrides: Map<String, OGSvgNodeOverride> = emptyMap())` — map node `id` → override; change the map from Compose state and only the matched nodes re-resolve, **without re-parsing** the source.

### Notes
- Paint (`fill` / `stroke` / `strokeWidth`) folds into the node's style (reusing `OGSVGStyle.combine`); transforms (`translate` / `rotation` / `scale`) are applied uniformly at draw time, so they work on **any** shape type (path / circle / rect / line / polygon / ellipse) regardless of the renderer's per-shape transform handling.
- Reactive and cheap — resolving overrides walks the already-parsed tree and rebuilds the draw list; no re-parse or re-fetch. Backward-compatible (`overrides` defaults to empty). See `docs/RUNTIME_SVG.md`; the demo's **Runtime-editable SVG** screen is a live gauge driven by a slider.
- Roadmap follow-ups: overriding a node's path `d`, and animating between two paths (path morphing).

## [1.6.0] — 2026-09-23

> Makes **animated GIFs actually animate** on both Android and iOS. Until now the image path decoded only a GIF's *first frame* (a still picture); `OGImageView` now plays the frames — looping, with the same one-liner API as any other image, and the same shape clip that crops a still photo works on the moving frames too. Purely additive and dependency-free.

### Added
- `rememberOGAnimatedPainter(source, loop = true, speed = 1f, onError = null)` in `com.solidkey.painpoints.image.gif` (expect/actual `@Composable`) — returns a playing `Painter` that advances a multi-frame image's frames on the Compose clock (`null` while the first frame is still decoding). `loop` repeats forever (default); `speed > 1` plays faster.
- `OGImageView` now auto-detects a `.gif` source (by extension) and animates it — **no API change** to existing calls; a `.gif` URL, file path or iOS resource simply plays instead of showing one frame.

### Notes
- Native platform decoders, **no new dependencies**: Android uses `AnimatedImageDrawable` (API 28+, with a clean static-first-frame fallback on API 24–27, where `speed` is ignored); iOS decodes every frame with Skia's `Codec` and cycles them, driven by a small, pure, unit-tested frame clock (`OGGifClock`). This matches the library's existing `BitmapFactory` / `UIImage` / Skia style.
- **Not GIF-specific**: animated **WebP** and any other multi-frame format the platform decoder understands ride the same path.
- Backward-compatible: still images are unaffected; a GIF just animates where it used to render its first frame. See `docs/GIF.md`.

## [1.5.0] — 2026-09-19

> Adds **audio sprites** — trigger *slices* of a single audio file on demand. Pack many short sounds (a "collect" chime, a "hit" thud, a "powerup" sweep) into one asset and fire any of them by id on an event, with a small voice pool so they can overlap instead of cutting each other off. Purely additive: a brand-new primitive alongside the existing `OGAudioPlayer`, which is unchanged.

### Added
- `OGAudioClip(id, startMs, endMs = END)` in `com.solidkey.painpoints.audio.playing` — a named, time-bounded window inside one audio file (the audio equivalent of a texture atlas). `endMs = OGAudioClip.END` (the default) plays from `startMs` to the end of the file; otherwise `endMs` must be `> startMs`. Exposes `playsToEnd` / `durationMs`; validates its inputs.
- `OGAudioSprite` (expect/actual) — loads one `OGSource` plus a list of `OGAudioClip`s, then `play("hit")` fires a clip on the next free voice. Also `stop(clipId)`, `stopAll()`, `setVolume(0f..1f)`, `release()`, and `clipIds`. Created with `OGAudioSprite.create()` (Composable factory).
- `OGAudioSpriteConfig(voices = 4, volume = 1f)` — sizes the round-robin voice pool (how many clips may sound at once) and sets master volume.

### Notes
- Android plays each window via **media3/ExoPlayer** `MediaItem.ClippingConfiguration` (the same media3 stack the video player already uses); iOS via `AVPlayer` seek + `AVPlayerItem.forwardPlaybackEndTime`, one player per voice. Overlap is handled by a shared, unit-tested round-robin allocator (`OGVoiceRotor`) — the (voices+1)-th simultaneous trigger reuses the oldest voice.
- Short SFX deliberately do **not** grab Android audio focus (a per-trigger request/abandon would add latency and duck the user's music).
- Backward-compatible: `OGAudioPlayer` (whole-file playback) is untouched; `OGAudioSprite` is a separate, opt-in primitive. See `docs/AUDIO_SPRITE.md`. The demo's **UFO Dodge** game uses it for collect/hit SFX.

## [1.4.0] — 2026-09-18

> Brings the **free-form clip shape to video**: `OGAVPlayer` now clips to any `Shape` (e.g. an `OGPolygonShape` lasso), reaching full parity with `OGImageView`. Purely additive — `OGPlayerConfig` gains one optional field defaulting to `null`, so every existing player call compiles unchanged.

### Added
- `OGPlayerConfig(clipShape: Shape? = null)` — when non-null, the video is masked to that arbitrary outline (e.g. an `OGPolygonShape`), taking precedence over `displayShape`/`cornerRadius`. Backed by a new `OGPlayerConfig.effectiveShape` (= `clipShape ?: shape`) that both the Android (ExoPlayer) and iOS (AVPlayer) players clip to — the same GPU `Modifier.clip` mask, so there's no new runtime cost.

### Notes
- Backward-compatible: `clipShape` defaults to `null`, so `displayShape`/`cornerRadius` behave exactly as before. This promotes "clip a video to a hand-/AI-drawn region" from a manual `Modifier.clip` wrap to a first-class config option, matching `OGImageView(clipShape = …)`.

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
