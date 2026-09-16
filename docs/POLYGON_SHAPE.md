# Free-form Polygon Lasso — `com.solidkey.painpoints.shape.OGPolygonShape` (KMPMedia 1.3.0)

The built-in [`OGShapeType`](../library/src/commonMain/kotlin/com/solidkey/painpoints/shape/OGShape.kt)
values (circle / triangle / diamond / …) cover the common cases. `OGPolygonShape` covers the rest:
clip a photo or video to **any** outline — a head, a logo, a hand-drawn region — described as a list
of straight **line segments**.

It is a plain Compose `Shape`, so it drops into the same `Modifier.clip(shape)` GPU mask the built-in
shapes use — for a still image, drawn once = **no runtime cost**.

## AI-friendly by design

The outline is *just data*: an ordered list of vertices in **normalized `0..1`** space
(`(0,0)` = top-left of the box, `(1,1)` = bottom-right), joined by line segments and auto-closed. That
means the exact same list can come from:

- an **AI / segmentation model** that outputs the region boundary of an object, or
- a **user drawing** the outline on the image with a finger,

…and it drops straight in — **no external image editor**. Coordinates outside `0..1` are clamped into
the box, so raw detector output is safe to pass.

## API

```kotlin
// A vertex in normalized 0..1 coordinates (top-left origin).
data class OGPoint(val x: Float, val y: Float)

// A free-form polygon built from an ordered list of points joined by line segments (auto-closed).
// Fewer than 3 points is degenerate → empty outline (nothing shown), never throws.
class OGPolygonShape(val points: List<OGPoint>) : Shape {
    companion object {
        fun of(vararg points: Pair<Float, Float>): OGPolygonShape   // convenience: raw (x, y) pairs
    }
}

// Pure, platform-independent helper the Shape wraps (unit-tested directly): maps + clamps points to a box.
fun scalePolygonPoints(points: List<OGPoint>, width: Float, height: Float): List<OGPoint>
```

`OGImageView` gained one optional param to accept it:

```kotlin
@Composable fun OGImageView(
    source: OGSourceType,
    // …
    clipShape: Shape? = null,   // NEW: when non-null, overrides displayShape/cornerRadius and clips to this outline
    // …
)
```

`clipShape` is fully backward-compatible: leave it `null` (the default) and `displayShape` +
`cornerRadius` behave exactly as before. Because `OGPolygonShape` is an ordinary `Shape`, you can also
apply it to **any** composable with `Modifier.clip(OGPolygonShape.of(...))` — including a video surface.

## Example — cut out a head

```kotlin
import com.solidkey.painpoints.shape.OGPolygonShape

// An outline the AI traced around the head (or the user drew), in 0..1 space.
val head = OGPolygonShape.of(
    0.48f to 0.02f, 0.64f to 0.04f, 0.78f to 0.10f, 0.86f to 0.22f,
    0.88f to 0.38f, 0.84f to 0.54f, 0.73f to 0.69f, 0.59f to 0.79f,
    0.48f to 0.83f, 0.37f to 0.79f, 0.25f to 0.68f, 0.16f to 0.53f,
    0.13f to 0.38f, 0.15f to 0.22f, 0.24f to 0.09f, 0.35f to 0.03f,
)

OGImageView(
    source = OGImageResourceFileType("portrait", OGImageFormat.JPEG),
    modifier = Modifier.size(220.dp),
    contentScale = ContentScale.Crop,   // fill the box so the outline lines up with the photo
    clipShape = head,                   // keep only the region inside the outline
    onEventTriggered = { _, _ -> },
    onError = {},
)
```

> **Coordinate space.** The outline is normalized to the `OGImageView` *box*, and the photo is placed in
> that box by `contentScale` + `alignment`. For the outline to line up with the image, keep the mapping
> 1:1 — e.g. a square box with a square source at `ContentScale.Crop`, center alignment (as the demo
> does). If you zoom/pan the photo, re-derive the outline in the same frame.

This is exactly how the demo's **"🧍 Add Your Head to a Body"** screen
([`BodyRigScreen.kt`](https://github.com/SolidKeyAB/kmpmedia-demo)) cuts out just the head — the "Head
lasso" chip feeds an AI-traced `OGPolygonShape` into the head `OGImageView`, no external tool.

## Notes

- **Winding / order matters.** Points are connected in the order given; a self-crossing list produces a
  self-intersecting polygon (Compose fills by non-zero winding).
- **Concave is fine.** Any simple polygon works — concave outlines (e.g. tracing around ears/jaw) clip
  correctly.
- **Cost.** One GPU clip mask, identical to the built-in shapes — negligible for a still image, and the
  same mechanism the shipped "in any shape" video already runs at 60fps.
