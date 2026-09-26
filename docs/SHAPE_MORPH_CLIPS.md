# Shape-morph clips — `com.solidkey.painpoints.shape.OGMorphShape` (KMPMedia 1.10.0)

Clip masks used to be static: a video or photo sat inside a fixed circle / triangle / diamond /
[lasso](./POLYGON_SHAPE.md). `OGMorphShape` makes the **mask itself animate** — the outline tweens
from one shape to another as you drive a `0f → 1f` progress, so a **running video or animated GIF** can
shift from a circle to a diamond to a hand-/AI-drawn lasso *while it keeps playing*.

It is an ordinary Compose `Shape`, so it drops into the **exact same slot** a static shape already uses —
`OGImageView(clipShape = …)`, the video player's `OGPlayerConfig(clipShape = …)`, or any
`Modifier.clip(…)`. **No new surface API**: every KMPMedia media surface already clips through a `Shape`,
so morphing the clip is just a smarter `Shape`.

Clipping *moving* media to a *morphing* outline, identical on Android and iOS, exists nowhere else in the
Kotlin Multiplatform ecosystem.

## Holds 60fps — the perf + simplicity gate

The two endpoints can be *any* shapes (a built-in, a `RoundedCornerShape`, an `OGPolygonShape` lasso), so
they can't be tweened command-for-command like the [SVG path morph](./RUNTIME_SVG.md). Instead each
endpoint's outline is **resampled once** into N equally-spaced perimeter points and cached by
`(from, to, size)`; animating `progress` across frames then only lerps those two point lists — no
re-measure, no allocation storm. This is the same **sample-once / lerp-per-frame** budget the SVG morph
uses. At `progress <= 0` / `>= 1` the raw endpoint outline passes straight through, so a settled clip is
exactly as cheap as it was before this feature existed.

## API

```kotlin
// A clip Shape whose outline morphs from `from` to `to` as `progress` runs 0f..1f.
class OGMorphShape(
    val from: Shape,
    val to: Shape,
    val progress: Float,
    val sampleCount: Int = OG_MORPH_SAMPLES,   // perimeter samples per endpoint (default 96)
) : Shape

// One 0f..1f progress that walks a whole chain of stops (e.g. circle → diamond → lasso). Splits
// progress evenly across the `stops.size - 1` segments and returns the active adjacent-pair morph.
fun ogMorphSequence(stops: List<Shape>, progress: Float, sampleCount: Int = OG_MORPH_SAMPLES): Shape
```

Nothing about the media surfaces changed — `clipShape` has accepted any `Shape` since 1.3.0 (images) /
1.4.0 (video). `OGMorphShape` is simply a `Shape` you can now pass there.

## Examples

### A running video whose mask morphs

```kotlin
val progress by rememberInfiniteTransition().animateFloat(
    0f, 1f, infiniteRepeatable(tween(7000), RepeatMode.Restart)
)
// Stable endpoints (remembered / the CircleShape singleton) → the resample cache hits every frame.
val stops = remember { listOf(CircleShape, DiamondShape(), OGPolygonShape.of(/* lasso */)) }

OGAVPlayer(
    action = OGAVPlayerAction.PLAY,
    source = OGVideoUrlType(url),
    config = OGPlayerConfig(
        contentScale = OGVideoScale.FILL,
        clipShape = ogMorphSequence(stops, progress),   // ← the mask animates; the video plays on
    ),
)
```

### The same morph on an animated GIF or photo

```kotlin
OGImageView(
    source = OGImageUrlType(gifUrl),          // a .gif plays its frames; a photo is drawn once
    clipShape = ogMorphSequence(stops, progress),
    contentScale = ContentScale.Crop,
    onEventTriggered = { _, _ -> },
)
```

### Two-endpoint morph, or any composable

```kotlin
// Just two shapes:
val clip = OGMorphShape(CircleShape, TriangleShape(TriangleDirection.UP), progress)

// It's a plain Shape — clip anything:
Box(Modifier.clip(clip).background(brush))
```

Drive `progress` from anything: an `animateFloatAsState`, an infinite transition, a `Slider`, a
gesture, or a playback position — exactly like `OGSvgNodeOverride.morphProgress` drives SVG morphing.

## Notes & guarantees

- **Any two shapes.** Endpoints with different vertex counts (a 96-gon circle vs a 3-point triangle) are
  both resampled to `sampleCount` perimeter points, so they always tween.
- **No swirl.** The target samples are cyclically re-aligned (and reversed if that fits better) to the
  source once per resample, so shapes whose outlines start at different corners — or wind opposite ways —
  morph without spinning. Winding never affects the filled region, so the realignment is free of visual
  cost.
- **Stable endpoints = zero per-frame resampling.** Hold the endpoint shapes in `remember { … }` (or use
  singletons like `CircleShape`) so the cache — keyed by shape value + size — hits every frame. The
  built-in `TriangleShape` / `DiamondShape` / `OGPolygonShape` compare by value, so even a re-created
  instance is a cache hit.
- **Endpoints pass through.** `progress <= 0` returns exactly the `from` outline and `progress >= 1` the
  `to` outline (no resampling), so start/end states are pixel-identical to using the raw shape.
- **Degenerate input is safe.** An endpoint whose outline has no length (e.g. a `<3`-point polygon) snaps
  to the start shape rather than throwing.

## Where it fits

This is the first item of **Bet 2 — Living shapes** on the [roadmap](../ROADMAP.md): "morph the clip
itself." It builds directly on 1.9.0 SVG path morphing and the 1.3.0 lasso, extending the shape system
into *moving* media. Run it in the demo app: **Morph the clip itself** (`MorphClipScreen`).
