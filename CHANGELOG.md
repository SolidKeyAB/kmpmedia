# Changelog

All notable changes to **KMPMedia** are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and the project aims for
[Semantic Versioning](https://semver.org/).

## [1.19.0] — 2026-10-03

> **Auto-cutout — subject → live lasso.** "Drop a photo, get the subject clipped out." KMPMedia could already clip media to a free-form `OGPolygonShape`; 1.19.0 adds the piece that **generates** that lasso from a segmentation mask. True to the library's no-SDK philosophy, it does **not** bundle an ML model: it ships a pluggable `OGSegmenter` seam (hand it a mask from ML Kit / Vision / a cloud model for cluttered scenes), two zero-dependency built-in segmenters for plain backgrounds, and — the genuinely reusable, cross-platform part — the **mask → lasso tracer** (largest-component flood fill → Moore-neighbor boundary trace → Douglas-Peucker simplify → normalised polygon). The polygon drops straight into the existing `clipShape` slot. This completes **Bet 2 — Living shapes** on the roadmap. Purely additive, zero new dependency.

### Added
- `com.solidkey.painpoints.cutout` package:
  - `autoCutoutPolygon(image, segmenter, threshold, simplifyTolerance, maxVertices): OGPolygonShape?` — one-call segment → trace.
  - `OGMaskContour.maskToPolygon(mask, …)` — the mask → lasso tracer.
  - `OGSegmenter` (fun interface) + `OGSegmentationMask` — the pluggable seam (plug in ML Kit / Vision / cloud).
  - `OGChromaKeySegmenter(background, tolerance)` and `OGLumaKeySegmenter(minLuma, maxLuma)` — zero-dependency segmenters for plain-background / brightness-keyed photos (read pixels via `ImageBitmap.toPixelMap()`, pure `commonMain`).

### Notes
- **Compute once, clip forever.** The tracer runs once off the main thread (reads every pixel); the resulting lasso is then the same GPU clip as any shape, so there's no per-frame cost. Tracer unit-tested on **JVM and iOS** (6 tests: empty → null, square → bounding box, picks the larger of two blobs, vertex cap). Verified on-device: a synthetic subject on white is chroma-keyed and traced into a ~17-point lasso that removes the background. For real cluttered photos, supply an ML mask via `OGSegmenter` — the library's value is the mask → live-lasso tracing. See `docs/AUTO_CUTOUT.md`; the demo's **Auto-cutout** screen shows it.

## [1.18.0] — 2026-10-03

> **Persistent disk cache + GIF frame-memory cap.** Two transparent parity additions (no new API, nothing to opt into). (1) Remote images and GIFs were cached only **in memory** (dies with the process), so every cold start re-downloaded them; 1.18 adds a **disk cache** under the OS cache directory — lookup is in-memory → disk → network, a network fetch is written through to disk, and the next launch reads from disk. Keyed by a stable 128-bit content hash, bounded to 128 MB with LRU eviction, best-effort (any IO failure is a cache miss, writes are atomic). (2) A large/long animated GIF could blow up memory, especially on iOS where **every** frame is decoded into RAM; 1.18 caps GIF decode — a per-frame longest-edge cap on both platforms, plus a total-frames budget (≤ 64 MB) on iOS. Finishes the **memory/disk cache + large-GIF** line of table-stakes parity. Zero new dependency, purely additive.

### Added
- Internal: a persistent disk cache (`cacheDir/og_image_cache` on Android, `Caches/og_image_cache` on iOS) for remote image/GIF bytes, keyed by `OGStableHash` (128-bit FNV-1a hex). No public API — `OGImageView` and the GIF loader use it automatically.
- Internal: `OGGifDecodeBudget.frameScale(width, height, frameCount, allFramesInMemory)` — the shared, tested decision for how far to downscale GIF frames.

### Changed
- Remote images/GIFs now survive app restarts via the disk cache instead of re-downloading. Lookup order is in-memory → disk → network.
- GIF decode is memory-capped: Android caps each frame's edge via `ImageDecoder.setTargetSize` (frames already decode on demand); iOS caps edge **and** a 64 MB total-frames budget (all frames live in RAM there), downscaling at decode time.

### Notes
- Pure maths (`OGStableHash`, `OGGifDecodeBudget`) unit-tested on **JVM and iOS** (9 tests). Verified on Android: three remote GIFs populate the disk cache, and with **airplane mode + a cold restart** all three still render (loaded from disk); all GIFs still animate, so the frame cap doesn't break decoding. See `docs/CACHING.md`.

## [1.17.0] — 2026-10-03

> **EXIF orientation — automatic upright photos.** Phone cameras store raw sensor pixels and record how the phone was held in EXIF tag `0x0112`; platform decoders ignore it, so a portrait photo decodes **sideways**. 1.17.0 reads the tag and rotates, so any image `OGImageView` loads — from a file, URL, or bytes — now shows **upright automatically**. All eight orientation values are covered (the four rotations and four mirrored variants). No new API and nothing to opt into — it's a pure correctness fix, and already-upright images are untouched. Finishes the "large-image downsampling + EXIF" line of **table-stakes parity** on the roadmap (downsampling already shipped). Zero new dependency, purely additive.

### Added
- `com.solidkey.painpoints.image.loading.OGExifOrientation.transformFor(orientation): OGExifTransform` — the shared, tested mapping from an EXIF orientation value (1–8) to the *mirror-then-rotate-clockwise* correction that restores upright, plus the eight `OGExifOrientation` constants.

### Fixed
- Photos with a non-default EXIF orientation now decode upright on both platforms. Android reads the tag with the SDK's built-in `android.media.ExifInterface` (no new dependency) and applies a `Matrix` after downsampling; iOS normalises orientation inside the existing redraw pass, so even images that skip downsampling no longer lose orientation through the PNG → Skia round-trip.

### Notes
- **Zero new dependency, zero API change.** The transpose/transverse (5/7) cases most libraries get wrong are pinned by a geometry test (four corners vs. the canonical per-orientation mapping) green on **JVM and iOS**; verified visually on Android with the standard eight-orientation test set, all rendering upright. A missing/corrupt EXIF header degrades to `NORMAL`, never a crash. Bundled drawable resources carry no EXIF (re-encoded by the build tools), so only decoded photos (file/URL/bytes) are affected. See `docs/EXIF.md`; the demo's **EXIF auto-rotate** screen shows the test set upright.

## [1.16.0] — 2026-10-02

> **Placeholder / loading / error slots + accessibility on `OGImageView`.** The table-stakes parity an image view is expected to have: a **placeholder** composable shown while loading (a spinner, a brand colour) in place of the old grey box, an **error** composable shown on failure (the visual partner to `onError`, which still fires) in place of the old solid-colour box, and a **`contentDescription`** read aloud by TalkBack / VoiceOver (null = decorative). Both slots are drawn **inside the same shape clip / soft mask** as the image, so a placeholder fills the shape too. RTL is inherent — `alignment` resolves against `LayoutDirection`. Everything defaults to the previous behavior, so existing calls compile and render exactly as before.

### Added
- `OGImageView(placeholder: (@Composable () -> Unit)? = null, error: (@Composable () -> Unit)? = null, contentDescription: String? = null, …)` — custom loading / error composables (clipped to the image's shape) and an accessibility description. All null by default (old behavior).

### Notes
- The load state is a pure `ogImagePhase(hasPainter, failed)` state machine (4 commonTest green JVM + iOS); the error slot and `onError` are driven by one signal, so they can't disagree. Dogfooded in the demo's **Edge Cases** screen (spinner placeholder + ⚠️ error slot). See `docs/PLACEHOLDERS.md`.

## [1.15.0] — 2026-10-01

> **Gesture + physics interactivity primitives.** KMPMedia could clip media to any shape; now that shape can be **grabbed, dragged, pinch-zoomed and twist-rotated**, with a momentum fling and a spring settle — the interactivity half of **Bet 4** on the roadmap, productized out of the demo. One modifier, `Modifier.ogInteractive(state, hitArea)`, does both halves: it **applies** the pan / zoom / rotation through a single `graphicsLayer` (a GPU-layer transform, so motion never triggers recomposition and holds 60fps) and **drives** that transform from a custom `awaitEachGesture` loop — one- or two-finger drag pans, pinch zooms (clamped), twist rotates, and on release the pan carries on with an exponential-decay fling then springs back inside its bounds. Pass an `OGHitArea` to make the gesture **shape-aware**: a drag only starts if the finger lands inside the clip's silhouette, so the transparent corners of a triangle / circle / lasso no longer grab it (and touches there fall through to whatever is behind). Everything size-independent (pan limits are a *fraction* of the content size) and purely additive — a new `com.solidkey.painpoints.gesture` package, nothing existing changed. Zero new dependency; identical on Android & iOS.

### Added
- `com.solidkey.painpoints.gesture.ogInteractive(state: OGInteractionState, hitArea: OGHitArea? = null): Modifier` — make any composable grab-drag / pinch-zoom / twist-rotate interactive, with momentum fling + spring settle, on one `graphicsLayer`. A convenience overload `ogInteractive(config: OGInteractionConfig = OGInteractionConfig(), hitArea: OGHitArea? = null)` remembers its own state.
- `OGInteractionState` + `rememberOGInteractionState(config, initialOffset, initialScale, initialRotation)` — the live `offset` / `scale` / `rotation` transform, each an `Animatable` you can read (for a HUD, or to drive other effects) and `reset()` back to identity.
- `OGInteractionConfig(enablePan, enableZoom, enableRotate, minScale, maxScale, fling, flingFriction, maxPanFractionX, maxPanFractionY, settleDampingRatio, settleStiffness)` with presets `DragOnly` / `PanZoom` / `All`, plus `OGPanBounds`. Pan limits are a fraction of content size, so the same config works at any resolution.
- `OGHitArea` — a normalized `0..1` hit region (`RECT`, `CIRCLE`, `TRIANGLE_UP`, `TRIANGLE_DOWN`, `DIAMOND`, `polygon(points)` / `polygon(OGPolygonShape)`, and `of(OGShapeType)`) that ray-casts a touch against the clip silhouette so transparent corners are ignored.

### Notes
- **60fps, zero-dep, additive** — the transform rides one `graphicsLayer` (the same primitive the depth / animation code uses); the gesture loop is event-driven (nothing per frame); the hit-test is one pure ray-cast on the down event only; fling / settle are plain Compose `Animatable` animations. The pan / zoom / rotation accumulate **synchronously** inside the gesture loop and snap to **absolute** values, so concurrent snap coroutines under a fast fling can't lose deltas (a plain `snapTo(value + delta)` would). The hit-test and clamp maths are pure and **unit-tested on JVM and iOS** (16 tests); live-verified on Android (drag in all directions, pan bounds + spring settle, shape-aware grab/ignore). See `docs/INTERACTIVE.md`; the demo's **Interactive shapes** screen clips a photo to a shape and lets you drag / pinch / rotate / fling it with shape-aware touch.

## [1.14.0] — 2026-10-01

> **Compositor export → H.264 MP4.** The on-device compositor could already export a shareable animated GIF (1.13.0); now the same `OGComposition` renders to a **real H.264 MP4** with `OGComposition.exportMp4()`. Every frame is rendered offscreen at the composition's `fps`, flattened onto an opaque background (MP4/H.264 has no alpha), and handed to the **OS video encoder** — Android `MediaCodec` + `MediaMuxer`, iOS `AVAssetWriter` — so there is **no third-party dependency**: it uses the encoder that already ships on the device. The byte stream isn't identical across platforms (each OS has its own encoder), but the *input* is: compositing and the RGB→YUV / RGB→BGRA colour maths live in shared, tested `commonMain`, so both encoders compress the same rendered frames. Like `exportGif()`, it returns the encoded `ByteArray` and leaves saving/sharing to the app. H.264 needs **even** dimensions, so an odd canvas is cropped by its last row/column; pick even sizes to keep that edge. This completes **Bet 3 — On-device mini-compositor + export** on the roadmap beyond GIF ("author on device, get a shareable file"). Purely additive — one new extension, nothing existing changed.

### Added
- `com.solidkey.painpoints.compositor.OGComposition.exportMp4(bitRate = defaultMp4BitRate(width, height, fps), background = Color.Black): ByteArray` — render the whole timeline and encode a shareable H.264 MP4 with the platform video encoder. `background` is the opaque colour transparent pixels resolve to (its alpha is ignored).
- `com.solidkey.painpoints.compositor.defaultMp4BitRate(width, height, fps): Int` — a resolution- and fps-scaled default average bit rate, clamped to a 0.75–16 Mbps band.

### Notes
- **Uses the OS encoder, still zero third-party deps** — Android drives `MediaCodec` in flexible-YUV420 buffer mode (exact per-frame presentation timestamps, plane strides handled via the codec's input `Image`) muxed by `MediaMuxer`; iOS drives `AVAssetWriter` through an `AVAssetWriterInputPixelBufferAdaptor` fed 32-BGRA `CVPixelBuffer`s. Each writes to a temp file that the call reads back and deletes, so the library still returns bytes. **No alpha** — H.264 is opaque, so transparent pixels are composited onto `background` (default black). **Even dimensions required** — an odd width/height is cropped down by one pixel. `exportMp4()` blocks on the encoder; call it off the main thread (the demo runs it on `Dispatchers.Default`). Verified on Android (real `MediaCodec` → a valid 288×288 @ 20fps, 40-frame H.264 MP4, played back through `OGAVPlayer`); shared colour maths covered by unit tests on JVM and iOS. See `docs/COMPOSITOR.md`; the demo's **Compositor + export** screen now exports an MP4 and plays it back through the platform video player. Frame-sequence export remains the planned follow-up (a single frame is already available via `renderFrame`).

## [1.13.0] — 2026-09-30

> **On-device compositor + GIF export — the flagship "2.0."** KMPMedia could put one piece of media in one shape; now it can compose **several** layers on **one timeline**, animate them with **keyframes**, preview the result live at 60fps, and **export it to a shareable animated GIF** — all authored on device. `OGComposition` is a fixed-size canvas + a `durationMs`/`fps` timeline + a back-to-front stack of `OGCompositionLayer`s; each layer is a still image or a solid fill placed in a box whose **x / y / scale / rotation / opacity** are each an animatable `OGKeyframedFloat` track (LINEAR / EASE_IN / EASE_OUT / EASE_IN_OUT), optionally clipped to **any KMPMedia shape** (built-in, lasso, multi-region, `CircleShape`, …), and visible over `[startMs, endMs]`. `OGCompositionView` plays it on the Compose frame clock (holds 60fps, no intermediate bitmap) or acts as a scrubber via `positionMs`. `OGComposition.exportGif()` renders every frame offscreen and encodes one looping GIF with the built-in **pure-Kotlin `OGGifEncoder`** (median-cut palette + LZW) — **no dependency, no platform encoder, identical bytes on Android and iOS** — returning the `ByteArray` for the app to save or share. The whole feature is pure `commonMain` and purely additive: a new `com.solidkey.painpoints.compositor` package, nothing existing changed. This is **Bet 3 — On-device mini-compositor + export** on the roadmap ("author on device, get a shareable file — no KMP library does compose-and-export").

### Added
- `com.solidkey.painpoints.compositor.OGComposition(width, height, durationMs, layers, fps = 24, background = Transparent)` — the composition model, with `frameCount`, `frameTimeMs(i)`, `frameDelayCs` and `resolveAt(timeMs)`.
- `OGCompositionLayer(id, content, width, height, x, y, scale, rotationDeg, opacity, clip = null, contentScale = Crop, startMs = 0, endMs = null)` — one keyframe-animated layer; `endMs = null` runs to the composition end. `OGLayerContent.Image(bitmap)` / `OGLayerContent.Solid(color)`.
- `OGKeyframedFloat(default, keyframes)` + `OGKeyframe(timeMs, value, easing)` + `OGEasing` — an animatable float track, piecewise-eased, clamped-flat outside its stops. Factories `OGKeyframedFloat.const(v)` and `.of(time to value, …)`. Pure math (`ogEase`), evaluated once per frame.
- `OGCompositionView(composition, modifier, isPlaying = true, loop = true, positionMs = null, contentScale = Fit, onProgress = null)` — live 60fps preview / scrubber, drawn straight to the screen.
- `OGComposition.exportGif(loopCount = 0, alphaThreshold = 128): ByteArray` — render + encode a shareable animated GIF. `OGComposition.renderFrame(timeMs): ImageBitmap` grabs any single frame; `ImageBitmap.toArgbPixels(): IntArray` reads it to row-major `0xAARRGGBB`.
- `OGGifEncoder.encode(width, height, frames, loopCount = 0, alphaThreshold = 128): ByteArray` + `OGGifFrame(argb, delayCs)` — a standalone, dependency-free GIF89a encoder (shared median-cut palette, cached nearest-colour mapping, LZW with dictionary clear, single transparent index, disposal method 2), usable on its own for any frame list.

### Notes
- **v1 layers are still content** — a decoded `ImageBitmap` (including a frame grabbed from a video) or a colour fill, animated by the layer's transform; playing a *running* GIF/video *inside* a composition is out of scope for the first cut. **The library returns bytes, not a file** — saving/sharing is app- and platform-specific, so `exportGif()` stays platform-free. **GIF transparency is one colour** (no partial alpha): pixels below `alphaThreshold` become transparent (disposal method 2, so holes never accumulate); pass `alphaThreshold > 255` to force an opaque GIF. The shared ≤256-colour palette bands smooth gradients as any GIF does. Preview never allocates a bitmap; export is one-shot. See `docs/COMPOSITOR.md`; the demo's **Compositor + export** screen previews a keyframed multi-layer scene and exports a real GIF that is then played back through the platform decoder. MP4 / frame-sequence export are the planned follow-ups.

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
