# KMPMedia Roadmap

KMPMedia's niche is the one thing no other Kotlin Multiplatform media library
combines: **any media — image, GIF, video, SVG — clipped to any shape (including a
hand- or AI-drawn lasso), animated and interactive, behind one API that is identical
on Android and iOS.** Image loaders (Coil, Kamel) load pictures; platform players
(Media3, AVKit) play video. None unify *media × shape × motion × interactivity*.

This roadmap is about **widening that gap** — not chasing loaders on caching. It's
ordered so each item ships on its own as a small, backward-compatible release.

## How releases work (so features arrive "automatically")

Every item below ships as a new **Maven Central** version of `se.solidkey:kmpmedia-lib`.
Releases are **purely additive and backward-compatible** — existing calls keep compiling —
so adopting a new feature is just bumping the coordinate:

```kotlin
implementation("se.solidkey:kmpmedia-lib:<latest>")
```

No migration, no code changes required. More shipped features also means more surface
for discovery (klibs.io, awesome-lists) to pull new users in.

Status: ✅ shipped · 🔜 next · 🧭 planned

---

## Bet 1 — Runtime & AI-editable vector (the biggest open niche)

Competitors render an SVG as a static picture. KMPMedia already has a **live SVG scene
graph + a SMIL animation engine** — so make the vector *editable and data-bound at runtime*.

- ✅ **Runtime-mutable SVG** — address any node by `id` and set `fill` / `stroke` /
  `transform` / path at runtime, bound to Compose state. "SVG as a live template"
  (gauges, charts, badges, progress rings). *(1.7.0 attrs + transform · 1.8.0 path `d`)*
- ✅ **Path morphing** — tween a shape's `d` between two paths, driven by any Compose
  animation, parse-once/lerp-per-frame so it holds 60fps. Cross-platform vector morphing
  exists nowhere else. *(1.9.0 — `OGSvgNodeOverride.pathDataTo` + `morphProgress`)*
- ✅ **AI hooks** — "describe → SVG / clip region." A provider-agnostic JSON interop layer turns
  a language model's reply into a live polygon lasso or SVG node patch (and back), with prompt
  builders that hand the model the exact contract — no network or AI SDK pulled into the library.
  *(1.11.0 — `OGAiVector` + `OGPolygonSpec`/`OGSvgPatchSpec`, see `docs/AI_HOOKS.md`)*

## Bet 2 — Living shapes (deepen the signature)

- ✅ **Morph the clip itself** — animate a video/GIF mask circle → diamond → lasso.
  Clipping *moving* media to a *morphing* shape is unique to KMPMedia.
  *(1.10.0 — `OGMorphShape` + `ogMorphSequence`, drops into the existing `clipShape` slot)*
- ✅ **Auto-cutout** — turn a segmentation mask into a live `OGPolygonShape` lasso (contour trace +
  simplify, pure `commonMain`), with zero-dependency chroma/luma-key segmenters for plain backgrounds
  and a pluggable `OGSegmenter` seam for ML Kit / Vision / cloud models on cluttered scenes. "Drop a
  photo, get the subject clipped out" — no ML dependency pulled into the library.
  *(1.19.0 — see `docs/AUTO_CUTOUT.md`)*
- ✅ **Soft & gradient masks, multi-region clips** — feathered edges and more than one region. Soft
  masks (`Modifier.ogSoftClip` / `ogGradientMask`, and `OGImageView(softEdge=…, maskBrush=…)`) fade a
  photo/GIF at the boundary or along a gradient via one offscreen `DstIn` pass; `OGMultiRegionShape`
  combines placed sub-shapes with a path op (union/intersect/difference/xor) into one clip.
  *(1.12.0 — see `docs/SOFT_MASKS.md`)*

## Bet 3 — On-device mini-compositor + export (the flagship)

KMPMedia already layers image + video + SVG on a shared clock (`OGLayerRenderer` +
`OGCueEngine` timeline cues) and does on-device crop / resize / rotate / color-filters
(`OGImageProcessor`). Combine into:

- ✅ **Composition API** — layers + keyframes + one timeline. `OGComposition` stacks image / solid
  layers, each with animatable x / y / scale / rotation / opacity tracks and any shape clip, previewed
  live at 60fps by `OGCompositionView`. *(1.13.0 — see `docs/COMPOSITOR.md`)*
- ✅ **Export → GIF** — `OGComposition.exportGif()` renders the timeline and encodes a shareable animated
  GIF with a pure-Kotlin on-device encoder (median-cut palette + LZW, zero deps, identical bytes on both
  platforms). Author on-device, get a shareable file. No KMP library does compose-and-export. *(1.13.0)*
- ✅ **Export → MP4** — `OGComposition.exportMp4()` renders the timeline and encodes an H.264 MP4 with the
  **OS video encoder** (Android `MediaCodec` + `MediaMuxer`, iOS `AVAssetWriter`), still with no third-party
  dependency; the shared compositing / colour maths mean both encoders compress the same rendered frames.
  *(1.14.0)*
- ✅ **Export → frame sequence** — `exportFrames()` / `exportFramesArgb()` / `forEachFrame { … }` hand you
  the individual frames (bitmaps or raw pixels) to save a PNG sequence, feed a custom encoder, or build a
  filmstrip; `renderFrameAt` / `frameTimesMs` round it out. *(1.20.0 — see `docs/COMPOSITOR.md`)*

## Bet 4 — Interactivity primitives (productize the demo)

Pinch-to-depth, tilt, jointed rigs and draggable layers already run in the demo app —
promote them into the library:

- ✅ **Gesture modifiers + spring/physics** — `Modifier.ogInteractive` makes any composable
  grab-drag / pinch-zoom / twist-rotate interactive, with a momentum fling and a spring settle, all on
  one `graphicsLayer` (no recomposition, 60fps). `OGHitArea` ray-casts the touch against the clip
  silhouette so transparent corners fall through — you grab the *shape*, not its bounding box. Pure
  hit-test / clamp maths unit-tested on JVM + iOS; zero new dependency. *(1.15.0)*
- 🧭 **Depth/parallax** primitive (builds on the existing `Modifier.ogDepth`).
- 🧭 **Live camera into any shape** — an AR-sticker primitive (a `camera` package is scaffolded).

## Table-stakes parity (so we don't lose on the basics)

Not differentiators, but things adopters expect — cheap to add and they remove objections:

- ✅ **Memory/disk cache** for remote images & GIFs + frame-memory control for large GIFs. In-memory
  cache (LRU bitmaps on Android, a bounded cache on iOS) plus a **persistent disk cache** so a URL
  fetched once isn't re-downloaded across app launches, and a **GIF frame-memory cap** (per-frame edge
  cap on both platforms; a total-frames budget on iOS, where every frame is held in RAM).
  *(1.18.0 — see `docs/CACHING.md`)*
- ✅ **Large-image downsampling**, EXIF rotation. Downsampling caps the decode to a max edge on both
  platforms; EXIF orientation is read and applied so photos shot in portrait / upside down decode
  upright automatically (all 8 orientation values, no new dependency, no API change).
  *(1.17.0 — see `docs/EXIF.md`)*
- ✅ **Placeholder / loading / error** slots. `OGImageView(placeholder = …, error = …)` — any composable
  shown while loading / on failure, drawn inside the shape clip, with the error slot and `onError` driven
  by one signal. *(1.16.0 — see `docs/PLACEHOLDERS.md`)*
- ✅ **Accessibility** — `OGImageView(contentDescription = …)` read by TalkBack / VoiceOver (null =
  decorative); RTL is inherent (alignment resolves against `LayoutDirection`). *(1.16.0)*

---

## Suggested order

1. **Parity: cache + placeholders** — cheap, removes the most common adoption objections.
2. **Runtime-mutable SVG + path morph** — highest differentiation, builds directly on what exists.
3. **Shape-morph clips** — extends the shape system into moving media.
4. **Compositor + export** — the headline "2.0."

Have a feature request or an idea? Open an issue — this roadmap is shaped by what people ask for.
