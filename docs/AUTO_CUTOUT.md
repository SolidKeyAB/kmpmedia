# Auto-cutout — subject → live lasso (KMPMedia 1.19.0)

"Drop a photo, get the subject clipped out." KMPMedia already clips media to a free-form
[`OGPolygonShape`](POLYGON_SHAPE.md) lasso; 1.19.0 adds the piece that **generates** that lasso from a
segmentation mask — the reusable, **zero-dependency** half of background removal.

The library deliberately does **not** bundle an ML model (same philosophy as [`OGAiVector`](AI_HOOKS.md):
keep AI/SDKs out of the library). Instead it ships:

- a **pluggable `OGSegmenter`** seam — implement one function to supply a mask from ML Kit Subject
  Segmentation (Android), the Vision framework (iOS), or a cloud model, for cluttered scenes;
- two **zero-dependency built-in segmenters** for the common plain-background case; and
- the **mask → lasso tracer**, which is the genuinely reusable, cross-platform, tested part.

## Quick start

```kotlin
// Plain-background photo (product on white, portrait on a green screen):
val lasso: OGPolygonShape? = autoCutoutPolygon(
    image = photo,                                   // an androidx.compose.ui.graphics.ImageBitmap
    segmenter = OGChromaKeySegmenter(background = Color.White, tolerance = 0.12f),
)

// …then clip the photo to it (the existing clipShape slot) — the white background is gone:
lasso?.let { OGImageView(source = photoSource, clipShape = it) }
```

Run `autoCutoutPolygon` **off the main thread** — it reads every pixel once. The returned polygon is
then clipped by the GPU at **zero per-frame cost** (the same mask any other shape uses), so it respects
the perf gate: compute once, clip forever.

## The pieces

| Type | Role |
|------|------|
| `OGSegmentationMask(width, height, foreground)` | per-pixel foreground confidence (`0..1`) |
| `OGSegmenter` | `fun segment(image): OGSegmentationMask` — the pluggable seam |
| `OGChromaKeySegmenter(background, tolerance)` | zero-dep: keys out a solid colour background |
| `OGLumaKeySegmenter(minLuma, maxLuma)` | zero-dep: keeps a brightness band (dark-on-light etc.) |
| `OGMaskContour.maskToPolygon(mask, …)` | trace the subject into a normalised `OGPolygonShape` |
| `autoCutoutPolygon(image, segmenter, …)` | one call: segment → trace |

## How the tracer works

[`OGMaskContour`](../library/src/commonMain/kotlin/com/solidkey/painpoints/cutout/OGMaskContour.kt),
pure `commonMain`:

1. **Threshold** the mask to foreground/background.
2. **Largest connected component** (8-connected flood fill) — so background speckle and secondary blobs
   are ignored; only the main subject is traced.
3. **Moore-neighbor boundary trace** of that component's outer contour.
4. **Douglas-Peucker simplify** (tolerance as a fraction of the longest edge), with a hard vertex cap so
   a jagged mask can't produce a thousand-point polygon.
5. **Normalise** to `0..1` → an `OGPolygonShape` that drops into `clipShape`.

Unit-tested on **JVM and iOS** (empty mask → null, a square → its bounding box, picks the larger of two
blobs, vertex cap honoured). Verified on-device: a synthetic subject on white is keyed out and traced
into a ~17-point lasso that removes the background.

## Notes

- **Zero new dependency.** Pixels are read via Compose's `ImageBitmap.toPixelMap()` in `commonMain`, so
  the built-in segmenters are pure and cross-platform.
- For real, cluttered photos, a chroma/luma key won't isolate the subject — that's what the `OGSegmenter`
  seam is for (hand it an ML mask). The library's value is the mask → live-lasso tracing, which no other
  KMP media library provides.
