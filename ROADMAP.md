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
- 🧭 **Auto-cutout** — on-device subject/background segmentation → auto-generate an
  `OGPolygonShape`. "Drop a photo, get the subject clipped out," no manual lasso.
- ✅ **Soft & gradient masks, multi-region clips** — feathered edges and more than one region. Soft
  masks (`Modifier.ogSoftClip` / `ogGradientMask`, and `OGImageView(softEdge=…, maskBrush=…)`) fade a
  photo/GIF at the boundary or along a gradient via one offscreen `DstIn` pass; `OGMultiRegionShape`
  combines placed sub-shapes with a path op (union/intersect/difference/xor) into one clip.
  *(1.12.0 — see `docs/SOFT_MASKS.md`)*

## Bet 3 — On-device mini-compositor + export (the flagship)

KMPMedia already layers image + video + SVG on a shared clock (`OGLayerRenderer` +
`OGCueEngine` timeline cues) and does on-device crop / resize / rotate / color-filters
(`OGImageProcessor`). Combine into:

- 🧭 **Composition API** — layers + keyframes + one timeline.
- 🧭 **Export** — render a composition to **GIF / MP4 / frame sequence**. Author on-device,
  get a shareable file. No KMP library does compose-and-export.

## Bet 4 — Interactivity primitives (productize the demo)

Pinch-to-depth, tilt, jointed rigs and draggable layers already run in the demo app —
promote them into the library:

- 🧭 **Gesture modifiers + spring/physics** for shapes; hit-testing on clipped regions.
- 🧭 **Depth/parallax** primitive (builds on the existing `Modifier.ogDepth`).
- 🧭 **Live camera into any shape** — an AR-sticker primitive (a `camera` package is scaffolded).

## Table-stakes parity (so we don't lose on the basics)

Not differentiators, but things adopters expect — cheap to add and they remove objections:

- 🧭 **Memory/disk cache** for remote images & GIFs + frame-memory control for large GIFs.
- 🧭 **Large-image downsampling**, EXIF rotation.
- 🧭 **Placeholder / loading / error** slots.
- 🧭 **Accessibility** — `contentDescription`, RTL.

---

## Suggested order

1. **Parity: cache + placeholders** — cheap, removes the most common adoption objections.
2. **Runtime-mutable SVG + path morph** — highest differentiation, builds directly on what exists.
3. **Shape-morph clips** — extends the shape system into moving media.
4. **Compositor + export** — the headline "2.0."

Have a feature request or an idea? Open an issue — this roadmap is shaped by what people ask for.
