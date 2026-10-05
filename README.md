<p align="center">
  <img src="docs/branding/kmpmedia-logo.svg" alt="KMPMedia — media for Kotlin Multiplatform + Compose" width="480">
</p>

# KMPMedia

<p align="center">
  <a href="https://central.sonatype.com/artifact/se.solidkey/kmpmedia-lib"><img src="https://img.shields.io/maven-central/v/se.solidkey/kmpmedia-lib?label=Maven%20Central&color=blue" alt="KMPMedia on Maven Central — always the latest published version"></a>
</p>

**A Kotlin Multiplatform media library for Compose** — with a focus on **animated, interactive, and runtime-editable vector SVG**, plus image processing, **animated GIFs**, audio, and video, on **Android and iOS**.

Everything renders into native Compose primitives (`Canvas`/`drawScope`), so SVGs are live vectors you can animate, drag, layer, and edit at runtime — not rasterized bitmaps.

---

## Why KMPMedia?

Basic "show an SVG on both platforms" is now a solved problem (Coil 3, Kamel, Compose Multiplatform resources). KMPMedia targets what those still don't do:

| Capability | KMPMedia | Coil 3 (`coil-svg`) | Compose MP resources |
|---|:---:|:---:|:---:|
| Cross-platform SVG (Android + iOS) | ✅ | ✅ | ⚠️ (not Android) |
| Rendered as **live vectors** (not a bitmap) | ✅ | ❌ (rasterized) | ❌ |
| **Runtime SVG animation** (SMIL `<animate>`) | ✅ | ❌ | ❌ |
| **Wrap any static image/SVG → animated** (`OGAnimatedImage`) | ✅ | ❌ | ❌ |
| **Shape-crop** image & video to any shape (circle/triangle/…) | ✅ | ❌ | ❌ |
| **Free-form polygon lasso** — clip to any AI-/hand-drawn outline (`OGPolygonShape`) | ✅ | ❌ | ❌ |
| **Auto-cutout** — turn a segmentation mask into a live lasso (`autoCutoutPolygon`) | ✅ | ❌ | ❌ |
| **Live camera clipped to any shape** (`OGCameraPreview`) | ✅ | ❌ | ❌ |
| **Depth / layer management** — fly over/under + depth-of-field blur + parallax (`Modifier.ogDepth` / `ogParallax`) | ✅ | ❌ | ❌ |
| **Data-defined drawing styles** — JSON `{op}` pipeline → live boil / quantize / pixelate (`OGStyleSpec`) | ✅ | ❌ | ❌ |
| **Interactive / draggable / gesture** layers | ✅ | ❌ | ❌ |
| **Any graphic → a tappable button** — shape-aware tap + press effects (`Modifier.ogButton`) | ✅ | ❌ | ❌ |
| Bundled image + audio + video suite | ✅ | ❌ | ❌ |

If all you need is a static SVG loaded from the network, a general image loader is the simpler choice. Reach for KMPMedia when you need the SVG to **move, respond, or change at runtime**.

---

## AI-friendly by design

KMPMedia is built to be **generated correctly by AI coding assistants**, not just written by hand:

- **It speaks SVG** — the one graphics format LLMs produce natively as text. An assistant can emit an `<svg>` (including the animated SMIL subset) or a URL, and KMPMedia renders *and animates* it live on both platforms.
- **Declarative, consistent API** — every entry point is `OG…`, and you describe *what* (a shape, an animation set, a cue at a timestamp, a depth) as data. Generated code compiles more often and hallucinates less surface.
- **Agent-ready docs in the repo** — a machine-readable [`llms.txt`](llms.txt) API index and an [AI coding guide](docs/AI_GUIDE.md) with prompt→snippet examples.
- **Runtime prompt-driven editing** *(new in 1.11.0)* — [`OGAiVector`](docs/AI_HOOKS.md) hands a model a stable JSON contract and turns its reply into a live polygon lasso or SVG node patch (*"describe → shape / patch"*), so an app can let its *own users* reshape and restyle vectors with natural language — no AI SDK or networking pulled into the library, and it works with any model.

Today that makes KMPMedia **AI-generatable** *and* **AI-drivable at runtime**. A full serializable scene-spec + an MCP server (an LLM emits validated *data*, not Kotlin, and previews it before writing code) remain on the roadmap to make it fully **AI-ready**.

---

## See it running — the demo app

KMPMedia ships with a full **Compose Multiplatform demo app** (Android + iOS) built directly against this library's source, so every screen is real, readable library code you can lift into your own app. It's the fastest way to see what "animated, interactive, runtime-editable" actually looks like.

> **⏱️ A note on the GIFs' speed.** GIF is a low-frame-rate, palette-limited format, and the shape/path **morph** clips here are *additionally* slowed (~2–3×) on purpose, so the shape-shift is easy to follow in a static browser — **those morph captures are not the real speed.** (The gameplay/feature clips play at real speed.) Running live, every screen animates at your display's refresh rate (**~60fps**) and is smooth, not steppy: each morph resamples its two endpoint outlines *once* (cached) and then only interpolates points per frame — no re-parse, no per-frame allocation. So the slowed morph captures read clear-and-slow on purpose; the actual app is fluid.

<p align="center">
  <img src="demo-screenshots/feature-demo.gif" width="32%" alt="The library's core moves — a photo cropped live into circle, triangle and diamond; a running video re-framed into those same shapes; and a fully-static SVG brought to life with Scale, Rotate, Fade and Slide" />
</p>
<sub><b>The core moves, live.</b> Crop a photo into any shape · re-frame a <i>running</i> video into any shape · bring a 100%-static SVG to life with Scale / Rotate / Fade / Slide. Each is one <code>OGImageView</code> / <code>OGAVPlayer</code> / animation-primitive call — the same Compose Multiplatform code on Android &amp; iOS.</sub>

And the flagship **runtime-editable SVG** — one `.svg` parsed *once*, then any node changed by its `id` at runtime, bound to Compose state:

<p align="center">
  <img src="demo-screenshots/runtime-svg-demo.gif" width="30%" alt="Runtime-editable SVG on Android — a live gauge whose needle rotates and whose arc and status dot recolour green→amber→red as a slider drives an overrides map keyed by node id, with no re-parse" />
  <img src="demo-screenshots/ios-runtime-svg-demo.gif" width="30%" alt="The same runtime-editable SVG gauge on an iOS simulator — identical Compose Multiplatform code, the needle and zones following the same overrides map" />
</p>
<sub><b>Runtime-editable SVG, live — Android (left) &amp; iOS (right).</b> One <code>.svg</code> is parsed <i>once</i>; a slider only mutates an <code>overrides</code> map keyed by node <code>id</code> and the same drawing redraws live — the needle rotates and the arc + status dot recolour green→amber→red. No re-parse, identical on both platforms. See <a href="docs/RUNTIME_SVG.md">docs/RUNTIME_SVG.md</a>.</sub>

…and the same mechanism **morphs a path between two shapes** — set a `<path>`'s `pathDataTo` and drive `morphProgress` 0→1 from any Compose animation:

<p align="center">
  <img src="demo-screenshots/morph-svg-demo.gif" width="30%" alt="SVG path morphing on Android — one node's path tweens smoothly between a star and a ring and back, forever, driven by a Compose infinite transition through morphProgress" />
  <img src="demo-screenshots/ios-morph-svg-demo.gif" width="30%" alt="The same SVG path morph on an iOS simulator — identical Compose Multiplatform code, one node's path tweening between a star and a ring and back" />
</p>
<sub><b>Path morphing, live — Android (left) &amp; iOS (right).</b> A single node's <code>&lt;path&gt;</code> tweens between a star and a ring and back, forever. Both endpoint <code>d</code> strings are parsed <i>once</i> and cached, so each frame only interpolates points — no re-parse, no allocation churn, built for the "runs in a game at 60fps" bar. Same <code>OGSVGView</code> + <code>overrides</code> path as the gauge above, identical on both platforms. See <a href="docs/RUNTIME_SVG.md">docs/RUNTIME_SVG.md</a>.</sub>

…and the newest — **morph the *clip mask* itself** (v1.10.0). The same idea applied to clipping: the outline that masks a *running* video (or GIF) animates circle → diamond → triangle → lasso while the media plays on:

<p align="center">
  <img src="demo-screenshots/morph-clip-demo.gif" width="30%" alt="Shape-morph clips on Android — a running video clipped by a mask that animates between a circle, a diamond, a triangle and a star lasso, the dark stage revealing the morphing silhouette at the corners" />
</p>
<sub><b>Morph the clip itself, live — Android.</b> One <code>OGMorphShape</code> drives the mask; the video keeps playing underneath as the outline tweens circle → diamond → triangle → lasso (the dark stage shows the silhouette at the corners). The <i>same</i> <code>Shape</code> feeds <code>OGImageView.clipShape</code> (photos/GIFs), <code>OGPlayerConfig.clipShape</code> (video) and any <code>Modifier.clip</code> — no new surface API. Each endpoint outline is resampled <i>once</i> (cached by shape + size) and only the points are lerped per frame, so it holds 60fps. See <a href="docs/SHAPE_MORPH_CLIPS.md">docs/SHAPE_MORPH_CLIPS.md</a>.</sub>

…and the mask can now also be **soft** and **multi-region** (v1.12.0). A clip no longer has to be one shape with a hard edge: `Modifier.ogSoftClip(shape, feather)` fades a photo/GIF out over a feathered band (a vignette) and `Modifier.ogGradientMask(brush)` fades it along any gradient (edge fade, spotlight) — both surfaced on `OGImageView` as `softEdge` / `maskBrush`; and `OGMultiRegionShape` combines several placed sub-shapes with a path op (union / intersect / difference / xor) so one clip can show two portholes, or a diamond with a circular bite. It's an ordinary `Shape`, so it drops into the same `clipShape` slot for photos, GIFs and video. Soft masks are a single offscreen `BlendMode.DstIn` pass (no blur, no API floor); multi-region combines outlines once per size — both hold 60fps. See [**docs/SOFT_MASKS.md**](docs/SOFT_MASKS.md).

<p align="center">
  <img src="demo-screenshots/soft-mask-demo.gif" width="30%" alt="Soft & multi-region masks on Android — a photo whose circular clip edge feathers into a vignette as the feather grows, then a feathered triangle / diamond / rounded rect, then a gradient bottom-fade, then the photo shown through two circular portholes and a diamond with a circular bite removed" />
  <img src="demo-screenshots/ios-soft-mask-demo.gif" width="30%" alt="The same soft & multi-region masks screen on an iOS simulator — a photo's circular edge breathing into a feathered vignette, a gradient fade, and two circular portholes, identical Compose Multiplatform code" />
</p>
<sub><b>Soft &amp; multi-region masks, live — Android (left) &amp; iOS (right).</b> One <code>OGImageView</code>: <code>softEdge</code> feathers the clip edge into a vignette, <code>maskBrush</code> fades the photo along a gradient, and <code>OGMultiRegionShape</code> clips it to more than one region (two portholes, or a diamond with a circular bite). Soft masks are one offscreen <code>BlendMode.DstIn</code> pass — no blur, no API floor; multi-region is a path op computed once per size. The <i>same</i> code on both platforms. See <a href="docs/SOFT_MASKS.md">docs/SOFT_MASKS.md</a>.</sub>

…and the newest, the flagship — **compose layered scenes and export them** (v1.13.0). KMPMedia could put one piece of media in one shape; now it composes *several* layers on *one* timeline, animates them with keyframes, previews the result live at 60fps, and exports it to a single **shareable animated GIF** — all authored on device. `OGComposition` stacks image / solid layers, each with animatable x / y / scale / rotation / opacity tracks and any shape clip over a `[startMs, endMs]` window; `OGCompositionView` plays it on the Compose frame clock (or scrubs to any `positionMs`); and `OGComposition.exportGif()` renders every frame offscreen and encodes one looping GIF with a pure-Kotlin encoder — zero dependencies, identical bytes on Android and iOS. **New in v1.14.0:** the same composition also exports to a real **H.264 MP4** with `OGComposition.exportMp4()`, driving the device's own video encoder (Android `MediaCodec` + `MediaMuxer`, iOS `AVAssetWriter`) — still with no third-party dependency, since the compositing and colour maths stay in shared, tested `commonMain`. **No other KMP library does compose-and-export.** See [**docs/COMPOSITOR.md**](docs/COMPOSITOR.md).

<p align="center">
  <img src="demo-screenshots/compositor-demo.gif" width="30%" alt="On-device compositor on Android — a teal circle, a pink triangle, an amber diamond and a rounded badge composed on one timeline, each keyframe-animated (bobbing, spinning, sliding across, pulsing) and clipped to its shape, previewed live" />
  <img src="demo-screenshots/ios-compositor-demo.gif" width="30%" alt="The same on-device compositor on an iOS simulator — identical Compose Multiplatform code, the same keyframed multi-layer scene playing on one timeline" />
</p>
<sub><b>On-device compositor + export, live — Android (left) &amp; iOS (right).</b> Several solid-colour layers on one <code>OGComposition</code> timeline, each with keyframed x / y / scale / rotation / opacity and a shape clip, previewed at 60fps by <code>OGCompositionView</code>. In the app, <b>Export</b> renders the timeline and encodes it to a single looping animated GIF with the pure-Kotlin <code>OGGifEncoder</code> (median-cut palette + Floyd–Steinberg dithering + LZW, zero deps, so gradients stay band-free) — then plays that exact GIF back through the platform's own decoder, proving it's a real, shareable file. The <i>same</i> code on both platforms. See <a href="docs/COMPOSITOR.md">docs/COMPOSITOR.md</a>.</sub>

<p align="center">
  <img src="demo-screenshots/compositor-mp4-demo.gif" width="30%" alt="The same compositor exporting an H.264 MP4 on Android — the keyframed multi-layer scene rendered to a real .mp4 through MediaCodec, then played straight back in the platform video player" />
  <img src="demo-screenshots/ios-compositor-mp4-demo.gif" width="30%" alt="The same compositor exporting an H.264 MP4 on an iOS simulator — identical Compose Multiplatform code, the multi-layer scene encoded with AVAssetWriter, reporting Exported 40 frames · 96 KB MP4 (H.264)" />
</p>
<sub><b>Same composition, exported to a real H.264 MP4 — Android (left) &amp; iOS (right).</b> The identical keyframed scene, this time rendered by <code>OGComposition.exportMp4()</code> through the device's own video encoder (Android <code>MediaCodec</code> + <code>MediaMuxer</code>, iOS <code>AVAssetWriter</code>) — no third-party dependency. On Android the exported file is played straight back in <code>OGAVPlayer</code>, proving it's a real, shareable <code>.mp4</code>; the iOS capture shows the same export producing a valid H.264 <code>.mp4</code> (standard High profile, 288×288 @ 20fps). The compositing and colour maths (RGB→YUV / BGRA) live in shared, tested <code>commonMain</code>; only the thin encoder is per-platform. <b>New in v1.14.0.</b> See <a href="docs/COMPOSITOR.md">docs/COMPOSITOR.md</a>.</sub>

…and that clipped media is now **interactive** — **grab, drag, pinch-zoom, twist-rotate, fling and spring back** (v1.15.0). `Modifier.ogInteractive(state, hitArea)` makes any composable gesture-driven: it applies the pan / zoom / rotation through a single `graphicsLayer` (a GPU-layer transform, no recomposition, 60fps) and drives it from a custom `awaitEachGesture` loop, with a momentum fling on release and a spring settle back inside its bounds. Pass an `OGHitArea` and the touch is **shape-aware** — a drag only starts inside the clip's silhouette, so the transparent corners of a triangle / circle / lasso fall through instead of grabbing it. Pan limits are a *fraction* of the content size, so one config works at any resolution; the hit-test and clamp maths are unit-tested on JVM + iOS. **No other KMP media library makes a shape-clipped layer physically interactive.** See [**docs/INTERACTIVE.md**](docs/INTERACTIVE.md).

<p align="center">
  <img src="demo-screenshots/interactive-demo.gif" width="30%" alt="A photo clipped to a shape being dragged, pinch-zoomed, twist-rotated and sprung back on Android via Modifier.ogInteractive — the live offset / scale / rotation HUD updating as it moves" />
  <img src="demo-screenshots/ios-interactive-demo.gif" width="30%" alt="The same interactive shape on an iOS simulator — identical Compose Multiplatform code, the shape-clipped photo panning, zooming, rotating and springing back" />
</p>
<sub><b>Interactive shapes, live — Android (left) &amp; iOS (right).</b> The <i>same</i> Compose Multiplatform code: a photo clipped to a shape, made draggable / pinch-zoomable / twist-rotatable by one <code>Modifier.ogInteractive</code>, with a momentum fling and a bouncy spring settle. <code>OGHitArea</code> makes the touch shape-aware (grab the silhouette, not its bounding box). The motion here is driven by a scripted loop so both platforms show the full pan / zoom / rotate / spring identically (the capture tooling can't inject multi-touch); in the app it's your fingers. The transform rides one GPU <code>graphicsLayer</code>, so it holds 60fps; the hit-test and clamp maths are unit-tested on JVM + iOS. <b>New in v1.15.0.</b> See <a href="docs/INTERACTIVE.md">docs/INTERACTIVE.md</a>.</sub>

…and the newest additions — **auto-cutout**, **live camera in any shape**, **data-defined drawing styles** and **turning any graphic into a button** — are already live in the demo; their paired Android/iOS GIFs are still being captured, so here they're described rather than shown:

- **🪄 Auto-cutout → live lasso** *(v1.19.0)* — drop a photo and the subject is clipped out: a pluggable `OGSegmenter` seam (plug in ML Kit / Vision / a cloud model) plus a cross-platform mask → lasso tracer (flood-fill → Moore-neighbour trace → Douglas–Peucker simplify) whose output drops straight into the same `clipShape` slot. No ML model is bundled. See [**docs/AUTO_CUTOUT.md**](docs/AUTO_CUTOUT.md).
- **📷 Live camera in any shape** *(v1.22.0)* — `OGCameraPreview(shape, facing)` masks the live camera feed to any shape (a built-in `OGShapeType`, an `OGPolygonShape` lasso, `CircleShape`, …): the AR-sticker primitive, built on the platform camera APIs (Camera2 / AVFoundation) with no third-party dependency. See [**docs/CAMERA.md**](docs/CAMERA.md).
- **🖊️ Data-defined drawing styles** *(v1.23.0)* — a drawing style is now just data: an `OGStyleSpec` is a JSON pipeline of `{op, params}` that `OGStyles.decode()` compiles into a live `OGStyle` applied every frame — **boil** (a living, hand-drawn line), **quantize** (a stepped, stop-motion line) and **pixelate** (a low-res mosaic fill). A designer or a language model can author and share a `.style` pack with no code and no rebuild, mirroring how `OGAiVector` turns a model's JSON into shapes. Zero new dependency; the demo's 🖊️ *Boiling lines* screen (with a live "Style from JSON" editor) dogfoods it. See [**docs/STYLES.md**](docs/STYLES.md).
- **🔘 Any graphic → a button** *(v1.24.0)* — KMPMedia ships no components or theme system, so instead it ships the *bridge*: one modifier, `Modifier.ogButton(hitArea, pressEffect) { onClick() }`, makes any graphic (a photo, an SVG, a lasso-cut cut-out, a `Canvas` drawing) a real, accessible button. It's the **tap twin** of `Modifier.ogInteractive` — both reuse `OGHitArea`, so a tap only counts **inside the silhouette** and the transparent corners fall through. Pick a press effect — `Scale`, `Dim`, `Brutalist` (a pop-art hard-shadow push-in it draws itself) or `None` — add an optional long-press, and it's done. Zero dependency; the demo's 🔘 *Any graphic → a button* screen dogfoods it. See [**docs/BUTTON.md**](docs/BUTTON.md).

*(These join the GIF tour above once their captures land — run the [demo](https://github.com/SolidKeyAB/kmpmedia-demo/releases) to see them live now.)*

And a whole mini-game built from those same primitives:

<p align="center">
  <img src="demo-screenshots/ufo-dodge-demo.gif" width="30%" alt="UFO Dodge in motion on Android — a real looping video clipped into the space background, SVG sprites animated by the library, and a live meteor-storm cue" />
  <img src="demo-screenshots/ios-ufo-dodge-demo.gif" width="30%" alt="The same UFO Dodge running on an iOS simulator — identical Compose Multiplatform code, same clipped-video backdrop and animated sprites" />
</p>
<sub><b>UFO Dodge, live — Android (left) &amp; iOS (right).</b> The <i>same</i> Compose Multiplatform code on both: a real looping video clipped into the backdrop, SVG sprites animated by the library, and the video's own timeline firing an in-game meteor-storm cue.</sub>

And **animated GIFs** — one `OGImageView` pointed at a `.gif`, playing on both platforms (and clipped into shapes just like a still photo):

<p align="center">
  <img src="demo-screenshots/gif-demo.gif" width="30%" alt="Animated GIFs playing on Android — a looping Newton's cradle, a galloping horse clipped into a circle and a diamond, and a loading spinner, all from .gif URLs through one OGImageView" />
  <img src="demo-screenshots/ios-gif-demo.gif" width="30%" alt="The same animated-GIF screen on an iOS simulator — identical Compose Multiplatform code, the horse animating inside the circle and diamond clips" />
</p>
<sub><b>Animated GIF, live — Android (left) &amp; iOS (right).</b> Each GIF is one <code>OGImageView</code> pointed at a <code>.gif</code> URL — it auto-detects and loops the frames (Android <code>AnimatedImageDrawable</code>, iOS Skia <code>Codec</code>). The <i>same</i> shape clip that crops a still photo animates the moving frames inside a circle / diamond too. See <a href="docs/GIF.md">docs/GIF.md</a>.</sub>

<p>
  <img src="demo-screenshots/demo_body_rig.png" width="30%" alt="Add your own photo as the head of an already-rigged body" />
  <img src="demo-screenshots/demo_body_rig_action.png" width="30%" alt="The rig mid jumping-jack, the photo head bobbing along" />
  <img src="demo-screenshots/demo_game_play.png" width="30%" alt="UFO Dodge — the mini-game built entirely from library primitives" />
</p>
<sub><b>Android</b> — “Add Your Head to a Body”, then UFO Dodge.</sub>

<p>
  <img src="demo-screenshots/demo_ios_body_rig.png" width="24%" alt="Add Your Head to a Body running on an iOS simulator — the same head lasso" />
  <img src="demo-screenshots/demo_ios_home.png" width="24%" alt="The demo home menu running on an iOS simulator" />
  <img src="demo-screenshots/demo_ios_warp.png" width="24%" alt="UFO Dodge on iOS — flying through the black-hole warp" />
  <img src="demo-screenshots/demo_ios_storm.png" width="24%" alt="UFO Dodge on iOS — a meteor-storm cue firing" />
</p>
<sub><b>iOS</b> — the same Compose Multiplatform code on an iPhone simulator: <b>“Add Your Head to a Body”</b> (your photo cut out by the <code>OGPolygonShape</code> head-lasso, exactly as on Android), the home menu, then UFO Dodge (black-hole warp + a meteor-storm cue).</sub>

**What's inside**

- **✨ Animate a Static Image** — take a fully-static SVG (zero `<animate>` tags) and bring it alive with the animation primitives: Scale / Rotate / Fade / Slide, combined live.
- **🎨 Runtime-editable SVG** — load an SVG once, then change any node by `id` at runtime — recolour, rotate, move, reshape its path, or **morph** it smoothly between two shapes — bound to Compose state ([`OGSvgNodeOverride`](docs/RUNTIME_SVG.md)). A live gauge whose needle and zones follow a slider, and a star that tweens into a ring at 60fps, no re-parse. "SVG as a live template", identical on Android & iOS.
- **✂️ Crop a Photo into a Shape** — crop any photo into a circle / triangle / diamond with pinch-to-zoom, drag-to-pan and a 3×3 focal grid — one GPU clip, drawn once, so it's free.
- **🧍 Add Your Head to a Body** — an *already-rigged* body (torso + two arms + two legs, every segment a jointed chain). Drop in a photo and the **✂️ head lasso** ([`OGPolygonShape`](docs/POLYGON_SHAPE.md)) clips out *just the head* — no external editor — pinned at the neck joint you set (size + tilt); or fall back to a circle / triangle / diamond. Then tap **Wave / Walk / Jumping jacks / Dance** and the whole body animates, your head riding along. The head is one `OGImageView` clip; the rig and every dynamic are plain Compose, identical on Android & iOS.
- **🦾 Jointed Shapes** — the building block behind it: shape-clipped photos pinned at one pixel with a movable angular limit, chained into a draggable two-link arm and a clamped pendulum.
- **🎞️ Animated GIF** — point one `OGImageView` at a `.gif` and it plays: looping frames on Android (`AnimatedImageDrawable`) and iOS (Skia `Codec`), the same code. The shape clip that crops a photo animates the moving frames inside a circle / diamond too.
- **🎬 Video Playback** — one cross-platform `OGAVPlayer` (ExoPlayer on Android, AVPlayer on iOS), re-framed live into any shape, with transport controls, loop and load-any-URL.
- **🫧 Morph the clip itself** — the clip *mask* animates circle → diamond → triangle → lasso while a video and a GIF keep playing, all driven by one [`OGMorphShape`](docs/SHAPE_MORPH_CLIPS.md). Clipping moving media to a morphing outline at 60fps, identical on Android & iOS.
- **🪶 Soft & multi-region masks** — feather a photo's edge into a vignette (`softEdge`), fade it along a gradient (`maskBrush`), or clip it to more than one region at once — two portholes, a diamond with a circular bite — with [`OGMultiRegionShape`](docs/SOFT_MASKS.md). Soft edges via one offscreen `DstIn` pass; multi-region via a path op computed once. Same code on Android & iOS.
- **🤹 Interactive shapes** — grab a shape-clipped photo and drag it, pinch to zoom, twist to rotate — then fling it and watch it spring back, with a live offset / scale / rotation HUD. One [`Modifier.ogInteractive`](docs/INTERACTIVE.md) adds the transform + momentum + spring; `OGHitArea` means only touches inside the actual silhouette grab it, not its bounding box. Zero-dep, 60fps, same code on Android & iOS.
- **🪄 Auto-cutout** — drop a photo and the subject is clipped out automatically: a pluggable [`OGSegmenter`](docs/AUTO_CUTOUT.md) seam (ML Kit / Vision / cloud) plus a zero-dependency mask → lasso tracer that feeds the same `clipShape` slot. No bundled ML model.
- **📷 Live camera in any shape** — [`OGCameraPreview(shape, facing)`](docs/CAMERA.md) masks the live camera feed to any shape (built-in / lasso / `CircleShape`), front or back: the AR-sticker primitive, on Camera2 / AVFoundation with no third-party dependency.
- **🖊️ Data-defined drawing styles** — a style is just data: an [`OGStyleSpec`](docs/STYLES.md) JSON pipeline of `{op, params}` (`boil` / `quantize` / `pixelate`) that `OGStyles.decode()` compiles into a live `OGStyle` applied every frame. Author and share `.style` packs with no code; the 🖊️ *Boiling lines* screen has a live "Style from JSON" editor.
- **🔘 Any graphic → a button** — make a graphic tappable with one [`Modifier.ogButton`](docs/BUTTON.md): an SVG star, a lasso-cut photo head and a diamond, each with a different press effect (`Scale` / `Dim` / `Brutalist`) and shape-aware taps — tap the head and it fires; tap a transparent corner of the same photo and it falls through. The tap twin of `ogInteractive`, zero-dep, same code on Android & iOS.
- **🛸 UFO Dodge (mini-game)** — every sprite is a static SVG animated by the library; crop your own photos into shapes and drop them into the field as live game objects.
- **🎛️ Playground · 🧪 Edge Cases · ⚡ Performance** — load anything from any URL/resource and tune every config live; deliberately broken inputs that prove `onError` fires cleanly; and load-timing / many-layer stress benchmarks with live numbers.

**Run it** — the demo lives in its own [companion repo](https://github.com/SolidKeyAB/kmpmedia-demo) and consumes this library via [`includeBuild`](#use-it-today-from-source):

- **Android** — `./gradlew :androidApp:assembleDebug`, install the APK, or open it in Android Studio. Prebuilt APKs are on the demo repo's [**Releases** page](https://github.com/SolidKeyAB/kmpmedia-demo/releases).
- **iOS** — open the Xcode project and run; a run-script phase recompiles the shared KMP framework on every build, so library changes flow straight through.

## Supported platforms

- ✅ **Android** (`minSdk` per catalog, compiled against JDK 17)
- ✅ **iOS** (`iosArm64`, `iosSimulatorArm64`)

> ℹ️ There are no JVM-desktop, web, or Linux targets in this build.

---

## Installation

> ✅ **Available on Maven Central.** Add the coordinate below and you're set. If you'd rather build against the library source (or need an unreleased change), see [`includeBuild`](#use-it-today-from-source) or GitHub Packages below.

KMPMedia is a Compose Multiplatform library. In your **`settings.gradle.kts`**, make sure the Compose dev repo is available alongside the usual repositories:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
```

Then add the dependency to your shared module's **`commonMain`**:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("se.solidkey:kmpmedia-lib:1.26.0")
        }
    }
}
```

<details>
<summary>Alternative: GitHub Packages</summary>

Add the GitHub Packages repository (requires a GitHub token with `read:packages`):

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://maven.pkg.github.com/SolidKeyAB/kmpmedia") {
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GPR_USER")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GPR_TOKEN")
            }
        }
    }
}
```
</details>

<details id="use-it-today-from-source">
<summary>Alternative: build from source (<code>includeBuild</code>)</summary>

To hack on the library itself or consume an unreleased change, depend on a local checkout instead of Maven Central — this is exactly how the demo app uses it:

```kotlin
// settings.gradle.kts of the consuming project
includeBuild("../KMPMedia") // path to your local clone
```
```kotlin
// consuming module's build.gradle.kts
commonMain.dependencies {
    implementation("se.solidkey:kmpmedia-lib")
}
```
</details>

---

## Quick start

### Render an SVG

```kotlin
import com.solidkey.painpoints.image.svg.OGSVGView
import com.solidkey.painpoints.image.loading.OGSvgUrlType

OGSVGView(
    source = OGSvgUrlType("https://example.com/logo.svg"),
    width = 240f,
    height = 240f,
    onError = { message -> /* handle load/parse errors */ },
)
```

Sources can also be local files or bundled resources:

```kotlin
OGSvgFileType("/path/to/icon.svg")
OGSvgResourceFileType("icon.svg")
```

### Animate an SVG (SMIL `<animate>`)

```kotlin
import com.solidkey.painpoints.image.svg.animation.OGSVGAnimationPlayer

OGSVGAnimationPlayer(
    source = OGSvgResourceFileType("spinner.svg"),
    width = 120f,
    height = 120f,
    isPlaying = true,
    loop = true,
    onError = { /* ... */ },
)
```

### Turn any static image into an animated one

`OGAnimatedImage` wraps any source — a photo, or even a plain SVG with **no** `<animate>` tags — and animates it at runtime. The source file is never modified; the motion comes entirely from the wrapper.

```kotlin
import com.solidkey.painpoints.image.animating.OGAnimatedImage
import com.solidkey.painpoints.image.animating.OGAnimationType
import com.solidkey.painpoints.image.loading.OGImageUrlType

OGAnimatedImage(
    source = OGImageUrlType("https://example.com/logo.png"),
    animations = setOf(OGAnimationType.SCALE, OGAnimationType.ROTATE), // SCALE / ROTATE / FADE / TRANSLATE
    durationMillis = 1200,
    intensity = 1f,
    onError = { /* ... */ },
)
```

### Display and transform an image

```kotlin
import com.solidkey.painpoints.image.OGImageView
import com.solidkey.painpoints.image.loading.OGImageUrlType

OGImageView(
    source = OGImageUrlType("https://example.com/photo.jpg"),
    draggable = true,
    onEventTriggered = { event, id -> /* layer interaction events */ },
    onError = { /* ... */ },
)
```

Crop the photo into a shape (circle, triangle, diamond, …) with a GPU clip — drawn once, no bitmap cost:

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.solidkey.painpoints.shape.OGShapeType

OGImageView(
    source = OGImageUrlType("https://example.com/portrait.jpg"),
    modifier = Modifier.size(220.dp),
    displayShape = OGShapeType.CIRCLE,   // CIRCLE / TRIANGLE_UP / TRIANGLE_DOWN / DIAMOND / SQUARE / RECTANGLE
    contentScale = ContentScale.Crop,
    alignment = Alignment.TopCenter,     // which region stays visible when the photo is over-scaled
    onEventTriggered = { _, _ -> },
    onError = { /* ... */ },
)
```

> The same shape-crop applies to video — a built-in shape via `OGAVPlayer(config = OGPlayerConfig(displayShape = OGShapeType.CIRCLE))`, or a **free-form lasso** via `OGPlayerConfig(clipShape = OGPolygonShape.of(...))` (the same `clipShape` override as images).

Need to keep only an *arbitrary* region — a head, a logo, a hand-drawn area? Clip to a **free-form polygon lasso** instead of a built-in shape. The outline is just a list of normalized `0..1` vertices joined by line segments, so an AI/segmentation model or an on-image finger-draw can produce it directly — no external editor:

```kotlin
import com.solidkey.painpoints.shape.OGPolygonShape

OGImageView(
    source = OGImageResourceFileType("portrait", OGImageFormat.JPEG),
    modifier = Modifier.size(220.dp),
    contentScale = ContentScale.Crop,
    clipShape = OGPolygonShape.of(          // vertices in 0..1 space (top-left origin), any count ≥ 3
        0.48f to 0.02f, 0.86f to 0.22f, 0.84f to 0.54f,
        0.48f to 0.83f, 0.16f to 0.53f, 0.15f to 0.22f,
    ),                                      // ← clips to exactly this outline; see docs/POLYGON_SHAPE.md
    onEventTriggered = { _, _ -> },
    onError = { /* ... */ },
)
```

### Play an animated GIF

Point `OGImageView` at a `.gif` and it plays — no extra API. The frames animate on **both platforms** (previously the image path only ever showed the first frame). Everything else about `OGImageView` still applies: `contentScale`, `alignment`, and shape-crop / lasso clipping all work on the moving image.

```kotlin
import com.solidkey.painpoints.image.OGImageView
import com.solidkey.painpoints.image.loading.OGImageUrlType

// A .gif URL (or file path) auto-detects and loops.
OGImageView(
    source = OGImageUrlType("https://example.com/loading.gif"),
    onEventTriggered = { _, _ -> },
    onError = { /* ... */ },
)
```

Want direct control over looping / speed, or to feed the animation into your own `Image`? Use the underlying painter:

```kotlin
import androidx.compose.foundation.Image
import com.solidkey.painpoints.image.gif.rememberOGAnimatedPainter
import com.solidkey.painpoints.source.OGSource

val painter = rememberOGAnimatedPainter(
    source = OGSource.Url("https://example.com/loading.gif"),
    loop = true,
    speed = 1.5f,          // iOS honours speed; Android plays at native rate
)
painter?.let { Image(painter = it, contentDescription = null) }
```

> **How it works** — Android decodes into the platform's self-animating `AnimatedImageDrawable` (API 28+; the static first frame on API 24–27). iOS decodes every frame with Skia's `Codec` and cycles them honouring per-frame delays and `speed`. See [docs/GIF.md](docs/GIF.md).

See [**docs/POLYGON_SHAPE.md**](docs/POLYGON_SHAPE.md) for the full API and how the demo's "Add Your Head to a Body" uses it to cut out a head.

### Play video / audio

```kotlin
import com.solidkey.painpoints.video.playing.OGAVPlayer
import com.solidkey.painpoints.video.playing.OGAVPlayerAction
import com.solidkey.painpoints.video.loading.OGVideoUrlType

val result = OGAVPlayer(
    action = OGAVPlayerAction.PLAY,   // PLAY / PAUSE / STOP
    source = OGVideoUrlType("https://example.com/clip.mp4"),
    onError = { error -> /* DAVPlayerError */ },
)
```

### Depth / layers — fly over/under + depth-of-field

*New in 1.1.0.* Give any content a `depth` (`0f` = far/behind, `1f` = near/front) relative to a **focal plane**, and KMPMedia derives all three depth effects from that one number: **z-order** (fly over/under), a **depth-of-field** blur + dim that grows with distance from focus, and an optional **parallax scale**.

The easy path — a field of objects that share one focal plane; each child just states its own depth:

```kotlin
import com.solidkey.painpoints.depth.OGDepthField
import com.solidkey.painpoints.depth.OGDepthObject
import com.solidkey.painpoints.depth.OGDepthConfig

OGDepthField(focalDepth = 0.5f) {                    // 0.5 is the plane in sharp focus
    OGDepthObject(depth = 0.1f) { Backdrop() }       // far   → behind + blurred
    OGDepthObject(depth = 0.5f) { Subject() }        // in focus → crisp
    OGDepthObject(depth = 0.9f, config = OGDepthConfig.Video) {
        OGAVPlayer(...)                              // near, in front — Video preset: dim, no blur
    }
}
```

Or drop it onto any single composable:

```kotlin
import com.solidkey.painpoints.depth.ogDepth

OGImageView(..., modifier = Modifier.ogDepth(depth = 0.2f, focalDepth = 0.5f))
```

Tune it with `OGDepthConfig(maxBlur, minAlpha, dimFalloff, blurContent, depthScale)`; use `OGDepthConfig.Video` for video/native surfaces (dim + z-order, no blur — a `RenderEffect` blur over a `TextureView`/`AVPlayerLayer` is unreliable). In focus with defaults it applies **only** `zIndex` (no extra layer). Full perf + compatibility analysis in [`docs/DEPTH_LAYER.md`](docs/DEPTH_LAYER.md).

### Generate or patch a vector from a prompt (AI interop)

*New in 1.11.0.* KMPMedia's vector primitives are just data, so a language model can produce them. `OGAiVector` is a **provider-agnostic** JSON interop layer: it hands a model the exact contract with `polygonPrompt(...)` / `svgPatchPrompt(...)`, and turns the reply back into a live clip shape or SVG patch — tolerant of the code fences and prose models add. **No AI SDK or networking is pulled into the library**; you own the model and the call.

```kotlin
import com.solidkey.painpoints.ai.OGAiVector

// describe → clip region: the model's reply becomes an OGPolygonShape for any clipShape
val prompt = OGAiVector.polygonPrompt("the outline of a five-pointed star")
val star = OGAiVector.decodePolygonOrNull(myLlm.complete(prompt))   // your model, your call

// describe → SVG patch: constrain the model to the ids that exist, apply the result live
val patch = OGAiVector.svgPatchPrompt("point the needle to 80% and turn the arc amber",
                                      nodeIds = listOf("needle", "arc"))
OGSVGView(source = OGSvgResourceFileType("gauge.svg"), width = 240f, height = 240f,
          overrides = OGAiVector.decodeSvgPatchOrNull(myLlm.complete(patch)) ?: emptyMap())

// image → vector (new in 1.25.0): hand a VISION model a photo/frame, get editable vectors back
val imgPrompt = OGAiVector.imageToVectorPrompt(hint = "trace the person", imageInfo = OGImageInfo.of(photo))
val cutout = OGAiVector.decodePolygonOrNull(myVisionLlm.complete(imgPrompt, photo.toBase64Png()))
```

`encodePolygon` / `encodeSvgPatch` / `encodeScene` go the other way (persist a lasso/scene, seed a prompt with the current state). *New in 1.25.0:* `imageToVectorPrompt(...)` + `decodeScene(...)` turn a photo (or a live frame) into editable vector shapes via any **vision** model — you attach the image with `ImageBitmap.toBase64Png()`, and the library still makes **no** network call. All of it runs at generate/patch time, never per frame. See [`docs/AI_HOOKS.md`](docs/AI_HOOKS.md).

### Apply a data-defined drawing style

*New in 1.23.0.* A drawing style is just data — a JSON pipeline of `{op, params}`. `OGStyles.decode(...)` compiles it **once** into a live `OGStyle`; `apply` it each frame with a rising `timeMs`. The ops are `boil` (the living hand-drawn line), `quantize` (a stepped line) and `pixelate` (a low-res sprite fill); a designer or a model can author and share a `.style` pack with no code.

```kotlin
import com.solidkey.painpoints.style.OGStyles

val style = OGStyles.decode(
    """{"name":"pixel sprite","ops":[
         {"op":"boil","amplitude":0.03,"boilFps":10},
         {"op":"pixelate","resolution":20}
       ]}""",
)

val frame = style.apply(outline, timeMs)   // outline: List<OGPoint> in 0..1 space
if (frame.isPixelated) { /* draw frame.pixelSize squares at frame.pixels */ }
else                   { /* stroke or fill frame.outline */ }
```

Or give a media clip a living, hand-cut edge — a boiled `OGPolygonShape` drops into any `clipShape` slot, recreated each frame from a rising time:

```kotlin
import com.solidkey.painpoints.style.OGBoil
import com.solidkey.painpoints.style.boiled

OGImageView(
    source = OGImageUrlType("https://example.com/photo.jpg"),
    clipShape = OGPolygonShape(outline).boiled(OGBoil(amplitude = 0.02f), timeMs),
    onEventTriggered = { _, _ -> },
    onError = { /* ... */ },
)
```

`OGStyles.stylePrompt("a nervous pencil sketch")` hands any model the exact JSON contract, so a style can be AI-authored too. See [`docs/STYLES.md`](docs/STYLES.md).

### Turn any graphic into a button

*New in 1.24.0.* `Modifier.ogButton` makes any graphic a real, accessible, shape-aware button — no UI component, no theme system. Pass an `OGHitArea` matching the clip and only taps **inside the silhouette** count; the transparent corners fall through. Pick a press effect and you're done.

```kotlin
import com.solidkey.painpoints.gesture.ogButton
import com.solidkey.painpoints.gesture.OGHitArea
import com.solidkey.painpoints.gesture.OGPressEffect

OGImageView(
    source = photo,
    clipShape = headLasso,                           // the visible cut-out
    modifier = Modifier
        .size(160.dp)
        .ogButton(
            hitArea = OGHitArea.polygon(headLasso),  // only the head is tappable
            pressEffect = OGPressEffect.Brutalist(),  // pop-art hard-shadow push-in
            onLongClick = { showOptions() },
        ) { open() },
    onEventTriggered = { _, _ -> },
    onError = { /* ... */ },
)
```

It's the **tap twin** of `Modifier.ogInteractive` (the drag half) — both reuse `OGHitArea`. Other press effects: `OGPressEffect.Scale()` (a squeeze), `OGPressEffect.Dim()` (a fade), `OGPressEffect.None`. See [`docs/BUTTON.md`](docs/BUTTON.md).

---

## SVG feature support

The SVG engine is a pure-Kotlin parser + renderer. Honest status:

**Supported**
- Path commands `M L H V C Q Z` and arcs (`A`, approximated with béziers)
- Shapes: `rect` (incl. `rx`/`ry`), `circle`, `ellipse`, `line`, `polygon`, `polyline`
- `<g>`, `<use>`, `<symbol>`
- Linear & radial gradients, patterns
- Transforms: `translate`, `scale`, `rotate`, `skewX/Y`, `matrix`
- SMIL animation (`<animate>`) and interactive/draggable layers

**Partial / not yet supported**
- Fill/stroke opacity, `<clipPath>`, `<mask>`
- `<text>` / `<tspan>`
- `<image>`

Anything not listed above as working should be treated as unsupported for now.

---

## Status

Pre-1.0 in spirit — the SVG animation/interaction layer is the actively developed core; the audio/video/image pieces are functional wrappers over platform players. See the [CHANGELOG](CHANGELOG.md) for release history.

## License

[MIT](https://opensource.org/licenses/MIT) © SolidKey AB
