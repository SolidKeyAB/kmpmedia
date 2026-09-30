# Soft & gradient masks, multi-region clips — KMPMedia 1.12.0

Clip masks used to be a single shape with a **hard edge**: a photo or video sat inside one crisp
circle / triangle / diamond / [lasso](./POLYGON_SHAPE.md). 1.12.0 adds the other two ways to shape a
mask:

- **Soft (feathered) & gradient masks** — the edge can *fade* instead of cut. A feathered vignette,
  a spotlight, an edge fade. `Modifier.ogSoftClip` / `Modifier.ogGradientMask`, or the new
  `OGImageView(softEdge = …, maskBrush = …)` params.
- **Multi-region clips** — the mask can be *more than one region* at once: two circular portholes, a
  diamond with a circular bite taken out, a lasso plus a spotlight. `OGMultiRegionShape` — an ordinary
  Compose `Shape`, so it drops into the **exact same `clipShape` slot** a single shape already uses.

Both are purely additive and backward-compatible: existing calls are unchanged.

## Holds 60fps — the perf + simplicity gate

- **Multi-region** places its sub-shapes and combines their outlines with `Path.op` **once per size**
  (inside `createOutline`, keyed off the shape's value equality), never per frame — the same budget as
  any other static clip.
- **Soft masks** are a single offscreen compositing layer plus one or two `BlendMode.DstIn` draws —
  **no blur, no `RenderEffect`, no Android API-level floor** — so they cost one extra GPU pass and are
  identical on Android and iOS. The feather's radial brush is built once per size (in `drawWithCache`),
  so animating the content underneath doesn't rebuild the mask.

## API

### Soft & gradient masks — `com.solidkey.painpoints.mask`

```kotlin
// Feather (soften) the edge of a shape clip: opaque through the interior, fading to transparent over
// the last `feather` near the boundary. feather = 0.dp is a plain hard clip.
fun Modifier.ogSoftClip(shape: Shape, feather: Dp): Modifier

// Multiply the content's alpha by a Brush gradient. Run it to Color.Transparent for an edge fade,
// spotlight or vignette. Only the brush's alpha matters (BlendMode.DstIn).
fun Modifier.ogGradientMask(brush: Brush): Modifier
```

`OGImageView` exposes both directly, combined with its shape clip in one pass:

```kotlin
@Composable
fun OGImageView(
    // …
    clipShape: Shape? = null,
    softEdge: Dp = 0.dp,        // > 0 → feather the clip edge
    maskBrush: Brush? = null,   // gradient alpha mask
    // …
)
```

### Multi-region clips — `com.solidkey.painpoints.shape`

```kotlin
enum class OGClipOp { UNION, INTERSECT, DIFFERENCE, XOR }

// One sub-shape placed in a normalized (0..1) sub-rectangle of the box — the same space as OGPoint.
data class OGClipRegion(
    val shape: Shape,
    val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f,
)

// A clip Shape made of several regions combined with a path op.
class OGMultiRegionShape(val regions: List<OGClipRegion>, val op: OGClipOp = OGClipOp.UNION) : Shape {
    companion object {
        fun union(vararg shapes: Shape): OGMultiRegionShape                       // full-box overlay
        fun of(vararg regions: OGClipRegion, op: OGClipOp = OGClipOp.UNION): OGMultiRegionShape
    }
}
```

## Examples

### A photo with a feathered (vignette) edge

```kotlin
OGImageView(
    source = OGImageResourceType("portrait.jpg"),
    displayShape = OGShapeType.CIRCLE,
    softEdge = 24.dp,                    // ← the circle's edge fades out instead of a hard cut
    contentScale = ContentScale.Crop,
    modifier = Modifier.size(240.dp),
    onEventTriggered = { _, _ -> },
)
```

### A gradient (edge-fade) mask

```kotlin
OGImageView(
    source = OGImageResourceType("banner.jpg"),
    maskBrush = Brush.verticalGradient(
        0f to Color.Black, 0.7f to Color.Black, 1f to Color.Transparent, // fade the bottom out
    ),
    contentScale = ContentScale.Crop,
    modifier = Modifier.size(320.dp, 180.dp),
    onEventTriggered = { _, _ -> },
)
```

`ogGradientMask` / `ogSoftClip` are plain modifiers, so they also mask any composable:

```kotlin
Box(Modifier.size(200.dp).ogGradientMask(Brush.radialGradient(/* spotlight */))) { /* content */ }
```

### Two circular portholes (multi-region)

```kotlin
val twoWindows = remember {
    OGMultiRegionShape.of(
        OGClipRegion(CircleShape, left = 0.02f, right = 0.48f),   // left porthole
        OGClipRegion(CircleShape, left = 0.52f, right = 0.98f),   // right porthole
        op = OGClipOp.UNION,
    )
}
OGImageView(source = photo, clipShape = twoWindows, contentScale = ContentScale.Crop,
    modifier = Modifier.size(320.dp, 160.dp), onEventTriggered = { _, _ -> })
```

### A diamond with a circular bite (difference)

```kotlin
val bitten = remember {
    OGMultiRegionShape.of(
        OGClipRegion(DiamondShape()),                                        // base
        OGClipRegion(CircleShape, left = 0.55f, top = 0.55f),                // subtracted
        op = OGClipOp.DIFFERENCE,
    )
}
```

Because `OGMultiRegionShape` is just a `Shape`, it works everywhere a shape clip does — `OGImageView`,
`OGPlayerConfig(clipShape = …)` for video, or any `Modifier.clip(…)`.

## Notes & guarantees

- **Backward compatible.** `softEdge = 0.dp` + `maskBrush = null` is the exact hard-clip behavior as
  before; a single-region `OGMultiRegionShape` is just that shape.
- **Soft masks are for Compose-drawn content** — photos and GIFs in `OGImageView`, or any composable.
  They do **not** apply to `OGAVPlayer` video: it renders through a native surface (Android
  `TextureView` / iOS `AVPlayerLayer`) that sits outside the Compose compositing layer, so a DstIn mask
  over it is unreliable (the same reason blur over a video surface doesn't repaint). Clip video with a
  hard shape or an `OGMultiRegionShape`, which are GPU clips it does honor.
- **Feather falloff is radial** about the shape's bounds, so it reads best on roughly centered / round
  shapes (circle, oval, diamond, rounded-rect, a compact lasso). A long thin shape feathers more at its
  ends than its long sides.
- **Value equality.** `OGMultiRegionShape` compares by `regions + op`, so a re-created clip (with
  value-equal inner shapes) is a Compose-skip / cache hit — hold it in `remember { … }` for stability.

## Where it fits

This is the "soft & gradient masks, multi-region clips" item of **Bet 2 — Living shapes** on the
[roadmap](../ROADMAP.md), following the 1.10.0 [shape-morph clips](./SHAPE_MORPH_CLIPS.md). It rounds
out the shape system: a mask can now morph *(1.10.0)*, feather / fade *(1.12.0)*, and cover *(1.12.0)*
more than one region. Run it in the demo app: **Soft & multi-region masks** (`SoftMaskScreen`).
