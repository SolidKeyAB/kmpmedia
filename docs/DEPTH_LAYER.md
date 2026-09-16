# Depth / Layer Management — `com.solidkey.painpoints.depth` (KMPMedia 1.1.0)

A single `depth` in `0f..1f` places any composable on a front-to-back axis — `0f` = deepest
(far/behind → "under"), `1f` = nearest (front → "over") — relative to a **focal plane**
(`focalDepth`, the depth in sharp focus). From those two numbers the API derives three effects that
together read as 3D depth:

| Effect | Mechanism | Grows with |
|---|---|---|
| **Z-order** — fly over / under | `Modifier.zIndex(depth)` | the depth value itself |
| **Depth-of-field** — blur + dim | `Modifier.blur` + `graphicsLayer{alpha}` | distance from the focal plane |
| **Parallax scale** (optional) | `graphicsLayer{scale}` | the depth value (far→near lerp) |

> This is **not** the editor layer-stack in `com.solidkey.painpoints.layer` (draggable image/SVG/text
> items). This is the *rendering* depth of any composable — hence the separate `depth` package.

## API

```kotlin
// Core primitive — explicit focal plane, fully testable, no composition needed.
fun Modifier.ogDepth(depth: Float, focalDepth: Float, config: OGDepthConfig = OGDepthConfig()): Modifier

// Ergonomic wrappers — a field of objects sharing one focal plane.
@Composable fun OGDepthField(focalDepth: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)
@Composable fun OGDepthObject(depth: Float, modifier: Modifier = Modifier,
                              focalDepth: Float = LocalOGFocalDepth.current,
                              config: OGDepthConfig = OGDepthConfig(), content: @Composable BoxScope.() -> Unit)
val LocalOGFocalDepth: ProvidableCompositionLocal<Float>   // default 0.5f

data class OGDepthConfig(maxBlur: Dp = 12.dp, minAlpha: Float = 0.4f, dimFalloff: Float = 0.5f,
                         blurContent: Boolean = true, depthScale: OGDepthScale? = null) {
    companion object { val Video = OGDepthConfig(blurContent = false) } // dim + z-order, no blur
}
data class OGDepthScale(far: Float, near: Float)
```

```kotlin
OGDepthField(focalDepth = shipDepth) {
    OGDepthObject(depth = 0.2f) { OGImageView(...) }                        // blurred (far)
    OGDepthObject(depth = shipDepth) { PlayerShip() }                      // crisp (in focus)
    OGDepthObject(depth = 0.9f, config = OGDepthConfig.Video) { OGAVPlayer(...) } // dim, no blur
}
```

---

## Performance

### Cost per effect

- **`zIndex` — effectively free.** It is a parent-data hint; it only changes the order draw calls are
  emitted within one parent. No offscreen buffer, no extra layer, no per-frame shader. Changing depth
  re-sorts siblings — an O(n log n) sort over that parent's children, done at draw setup.

- **`alpha` / `scale` — one `graphicsLayer`.** A single GPU compositing layer (the exact primitive
  `OGAnimatedContainer` already uses per frame). Alpha is a blend; scale is a matrix on the layer.
  Cheap and constant-cost per element.

- **`blur` — the only heavy effect.** `Modifier.blur` is a `RenderEffect` on Android (API 31+,
  hardware) and a Skia `ImageFilter` on iOS. It renders the content to an **offscreen layer** and runs
  a separable Gaussian. Cost scales with **blurred area × radius**, and — critically — with **how often
  the content is redrawn**:
  - **Static content** (a still `OGImageView`) is rasterized once and the blur result is cached until
    the layer is invalidated → the per-frame cost after the first frame is ~zero.
  - **Animating content** (anything inside `OGAnimatedContainer`, or a video) invalidates every frame,
    so the blur shader **re-runs every frame**. This is the case to budget for.

### What `ogDepth` does to keep blur cheap

1. **In-focus = no layer at all.** When `depth == focalDepth` and the default config is used, `ogDepth`
   adds **only** `zIndex` — no `graphicsLayer`, no blur. The subject you're looking at pays nothing.
2. **Blur only when off-focus.** Radius = `maxBlur × |depth − focalDepth|`; a sub-pixel radius
   (< 0.5 dp) is skipped entirely, so near-focus objects never allocate a blur layer.
3. **Radius scales with distance.** Far objects get a larger (but bounded by `maxBlur`, default 12 dp)
   radius; mid-field objects get a small, cheap one.
4. **`blurContent = false` for surfaces.** The `Video` preset drops blur for video/native surfaces
   (see Compatibility), removing the most expensive and least reliable case.

### Practical budget

Blur is the same primitive as any `Modifier.blur`, so the same rules apply. On a modern device a
handful of blurred, animating sprites at ≤ 16 dp holds 60 fps comfortably (the demo runs the UFO-Dodge
field — a dozen+ animating SVG sprites, several off-focus and blurred — plus a full-screen video
backdrop). The thing to watch is **many large, animating, blurred surfaces at once**: each is a
full-frame offscreen render. Guidance:

- Keep the count of *simultaneously blurred **animating*** surfaces small; static blurred content is
  effectively free after the first frame.
- Prefer smaller `maxBlur`; blur cost rises with radius.
- Never blur video — use `OGDepthConfig.Video`.

### Allocation

`Modifier.ogDepth` is a plain (non-`@Composable`) factory: it builds a chained `Modifier` on each call,
so it allocates on each recomposition of the caller. Depth normally changes on user interaction (a
pinch), not every frame, so this is negligible. If you drive depth *per frame* across *many* nodes and
see churn, hoist the derived values (`alphaFor` / `blurRadiusFor` / `scaleFor` are public and pure) or
apply `ogDepth` higher up the tree.

---

## Compatibility with existing features

Purely **additive** — a new package (`depth`) with new symbols. No existing signature changed, so
1.0.x consumers are unaffected (semver **minor**: 1.0.3 → 1.1.0). `ogDepth` composes on the *outside*
of any content, so it stacks cleanly with every current primitive:

| Feature | Interaction | Verdict |
|---|---|---|
| **`OGImageView`** (shape crop) | The shape `clip` is applied inside; `ogDepth`'s z/alpha/blur wrap the already-shaped result → the whole shaped photo blurs/dims as a unit. | ✅ Full effect |
| **`OGAVPlayer`** (video, any shape) | Blurring a `TextureView`/`AVPlayerLayer` via `RenderEffect` is costly and unreliable across devices. Use `OGDepthConfig.Video` → `alpha` (works on the surface) + `zIndex` only. | ✅ z-order + dim (no blur, by design) |
| **`OGAnimatedImage` / `OGAnimatedContainer`** | Each already runs its own `graphicsLayer` animation; `ogDepth`'s layer nests outside it. A `FADE` animation's alpha multiplies with `ogDepth`'s dim (both apply). Blur re-runs per animated frame — see the budget note. | ✅ Composes; mind blur-on-animation cost |
| **Editor layer-stack** (`com.solidkey.painpoints.layer`, `OGLayerRenderer`) | Different concept (a Photoshop-style item stack that already uses `zIndex` for its own order). `ogDepth`'s `zIndex` is scoped to *its* parent, so there's no cross-talk. The `depth` package name avoids the "layer" overload. | ✅ No interference |

### Platform notes

- **Android ≥ 31:** hardware `RenderEffect` blur. **Android < 31:** `Modifier.blur` is a documented
  no-op (no crash) — depth-of-field degrades gracefully to z-order + dim; alpha and scale still apply.
- **iOS:** Skia `ImageFilter` blur works on every supported version.
- Targets shipped: `androidTarget`, `iosArm64`, `iosSimulatorArm64` (same as the rest of the library).

---

## Validation

- **12 unit tests** (`OGDepthTest`, commonTest → runs on Android + iOS) pin the pure math: in-focus =
  no blur/dim, effects grow with focal distance, out-of-range inputs clamped, `Video` preset disables
  blur, parallax lerps and clamps.
- **Full library suite: 56 tests green, 0 failures** (44 pre-existing + 12 new) — no regressions.
- **Demo dogfoods it end-to-end:** UFO-Dodge's per-object depth-of-field now calls `Modifier.ogDepth`
  across `OGImageView` (cropped photos), `OGAVPlayer` (video anomalies, `Video` preset),
  `OGAnimatedImage` (SVG hazards) and `OGAnimatedContainer`. The demo compiles on Android (APK) and
  iOS against 1.1.0.
