# KMPMedia vs. the KMP / Compose Multiplatform landscape

*Last verified: **2026-10-11**. Versions checked: Coil **3.6.3**, Kamel **1.0.9**, Landscapist **2.13.2**, Compottie **2.3.3**, Telephoto **0.19.0**, Compose Multiplatform **1.12.1**. This page is re-checked periodically (see [Keeping this current](#keeping-this-current)).*

Showing an SVG or a cached image on both platforms is a solved problem. KMPMedia lives one layer up: it's a **creative media + vector toolkit** — media that moves, responds to touch, composes on a timeline, and changes at runtime. Most of what follows is **complementary** to the loaders, not competing.

## What each library actually is

| Library | What it is | Where it stops |
|---|---|---|
| **Coil 3** (`3.6.3`) | The leading image **loader** for Compose Multiplatform (load, decode, cache). | SVG is rasterized to a bitmap; **animated GIF and video are Android-only** (iOS/desktop/web show a still frame). No vector editing, gestures, compositor or FX. |
| **Kamel** (`1.0.9`) | Async image **loader** (raster + static SVG + animated GIF decoders). | Loader only — no creative/media features. |
| **Landscapist** (`2.13.2`) | Pluggable image **loader** (over Coil/Glide/Fresco) with a plugin system. | Plugins cover load transitions, blur, palette and a `ZoomablePlugin` (pinch/pan/double-tap) — no fling/spring/shape-aware hit, and nothing else creative. |
| **Compottie** (`2.3.3`) | A Lottie / dotLottie **player** for Compose Multiplatform. | Plays animations **authored in After Effects**; runtime "dynamic properties" are shallow overrides of existing layers (recolor / transform / text) — not runtime geometry authoring, media clipping, compositing or export. |
| **Telephoto** (`0.19.0`) | A zoom / pan image **viewer** (+ subsampling for huge images). | Gestures stop at pan + pinch + double-tap + fling (no rotate — [open issue](https://github.com/saket/telephoto/issues/130) — no spring-settle, no shape-aware hit). No clipping/FX/compositor. iOS gets the zoom modifier only. |
| **Compose MP** (`1.12.1`) | The platform itself (low-level primitives). | `clip(Shape)` clips static content to an arbitrary polygon, and the animation APIs are low-level. But `ImageVector` is immutable (no runtime node-editing), there's no morph/multi-region/soft mask, no official video or camera, no `drawVertices` in common `DrawScope`, no unified shader API, and no compositor/export. |

## Capability matrix

Legend: ✅ full · ⚠️ partial / limited · ❌ none

| Capability | KMPMedia | Coil 3 | Kamel | Landscapist | Compottie | Telephoto | Compose MP |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| Network image load + cache | ⚠️ basic | ✅ | ✅ | ✅ | ❌ | ⚠️ | ⚠️ |
| SVG static render (cross-platform) | ✅ | ⚠️ raster | ⚠️ raster | ⚠️ raster | ❌ | ❌ | ⚠️ static |
| SVG **rendered as live vectors** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **SVG runtime node-edit** (by id, state-bound) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Shape-crop media to arbitrary **polygon / lasso** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ⚠️ static only |
| **Morph / multi-region / soft-gradient** masks | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Live **video in a shape** (unified cross-platform) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Live **camera in a shape** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Cross-platform animated GIF** | ✅ | ⚠️ Android-only | ⚠️ | ⚠️ | ❌ | ❌ | ❌ |
| Gestures: **twist-rotate + fling + spring-settle + shape-aware hit** | ✅ | ❌ | ❌ | ⚠️ zoom/pan | ❌ | ⚠️ pan/pinch/fling | ❌ |
| **Compositor** (layers + keyframes) → **GIF / MP4 export** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Motion & action FX (slash / lightning / speed-lines / glow) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Particles & parametric shapes | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Live colour **looks + blend** (data-defined grade) | ✅ | ❌ | ❌ | ⚠️ blur only | ❌ | ❌ | ❌ |
| Audio-reactive vectors | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Motion design + dynamics (spring/squash/sway) + cloth | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Photo → editable vector scene** (gradients/texture/glow/scatter + make-it-live motion) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Clickable-joints mesh rig** — bend a dense mesh live (`OGRig`) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Model-authorable JSON** primitive format | ✅ | ❌ | ❌ | ❌ | ⚠️ Lottie=AE export | ❌ | ❌ |
| Cross-platform **pixel parity** (pure-maths cores) | ✅ | — | — | — | ✅ renderer | — | ⚠️ shaders differ |

## Where the alternatives genuinely lead

To keep this honest: reach for those libraries when that's the job.

- **Coil / Kamel / Landscapist** — network image loading, caching and broad format decoding. KMPMedia is not a general image loader.
- **Compottie / Lottie-Compose** — playing complex animations hand-authored by a designer in After Effects.
- **Platform players (Media3 / ExoPlayer, AVPlayer)** — deep video streaming, DRM and codec control.

## Keeping this current

Competitor releases move, so these claims have a shelf life. A scheduled check re-verifies the latest version and feature set of each library against this page and flags any change that would make a row above inaccurate (a new version, a newly-closed gap, or a new competing library). The *Last verified* date at the top reflects the most recent run.
