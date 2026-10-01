# Gesture + physics interactivity — KMPMedia 1.15.0

KMPMedia already clips a photo, GIF or video to any shape. 1.15.0 makes that shape **interactive**:
grab it to drag, pinch to zoom, twist to rotate — then let go and it flings on with momentum and
springs back inside its bounds. It's the interactivity half of **Bet 4 — Interactivity primitives**
on the [roadmap](../ROADMAP.md), promoted out of the demo app into one reusable modifier.

Everything lives in a new `com.solidkey.painpoints.gesture` package and is purely additive — nothing
existing changed, no new dependency, identical on Android & iOS.

## One modifier does both halves

```kotlin
val interaction = rememberOGInteractionState(OGInteractionConfig.PanZoom)

OGImageView(
    source = photo,
    clipShape = TriangleShape(TriangleDirection.UP),
    modifier = Modifier
        .size(240.dp)
        .ogInteractive(interaction, hitArea = OGHitArea.TRIANGLE_UP),
)
```

`Modifier.ogInteractive(state, hitArea)`:

1. **Applies** the state's pan / zoom / rotation through a single `graphicsLayer` — a GPU-layer
   transform, so motion never triggers recomposition.
2. **Drives** that transform from a custom `awaitEachGesture` loop — one- or two-finger drag pans,
   pinch zooms (clamped to `minScale..maxScale`), twist rotates — and on release carries the pan on
   with an exponential-decay **momentum fling**, then **springs back** inside the configured bounds.

If you don't need to read the transform yourself, the convenience overload remembers its own state:

```kotlin
Modifier.ogInteractive(OGInteractionConfig.All, hitArea = OGHitArea.CIRCLE)
```

## Shape-aware touch — grab the shape, not the box

A clipped composable is still a **rectangle**. Without a hit area, a drag started in an empty corner
of a triangle still grabs it. Pass an `OGHitArea` and the gesture only starts if the finger lands
**inside the silhouette**; touches in the transparent corners are ignored and fall through to
whatever is behind.

```kotlin
.ogInteractive(state, hitArea = OGHitArea.of(OGShapeType.TRIANGLE_UP))
// or a free-form lasso, reusing the exact clip outline:
.ogInteractive(state, hitArea = OGHitArea.polygon(myLassoShape))
```

`OGHitArea` is described entirely in **normalized `0..1`** coordinates and is pure data + pure math
(even-odd ray cast / analytic tests), so it is unit-tested with no Compose runtime — exactly like the
shape and depth maths. Built-ins mirror the `OGShapeType` vocabulary: `RECT`, `CIRCLE`,
`TRIANGLE_UP`, `TRIANGLE_DOWN`, `DIAMOND`, plus `polygon(...)` for a lasso and `of(OGShapeType)`.

## Configuring the feel

`OGInteractionConfig` toggles which gestures are live and tunes the physics. Three presets cover the
common cases:

| Preset | Pan | Zoom | Rotate |
|---|:---:|:---:|:---:|
| `DragOnly` | ✅ | ❌ | ❌ |
| `PanZoom`  | ✅ | ✅ | ❌ |
| `All`      | ✅ | ✅ | ✅ |

Notable knobs:

- `minScale` / `maxScale` — pinch-zoom clamp (default `1f..5f`).
- `fling` / `flingFriction` — momentum on release; higher friction stops the fling sooner.
- `maxPanFractionX` / `maxPanFractionY` — pan limit as a **fraction of the content's own size**
  (e.g. `0.75f` = three-quarters of a width each way from centre; `Float.POSITIVE_INFINITY` =
  unbounded). Because it's a fraction, the same config works at any resolution. The content springs
  back inside the limit on release.
- `settleDampingRatio` / `settleStiffness` — the spring that settles the pan back (lower damping =
  bouncier).

## Reading the transform

`OGInteractionState` exposes `offset` (pan px), `scale` and `rotation` as `Animatable`s, so you can
read them for a HUD, drive other effects from them, or animate everything back to the resting
identity transform:

```kotlin
val o = state.offset.value          // Offset, px
val s = state.scale.value           // zoom factor
val r = state.rotation.value        // degrees
scope.launch { state.reset() }      // spring back to identity
```

## Holds 60fps — the perf + simplicity gate

- The transform rides **one `graphicsLayer`** (the same primitive the depth / animation code uses),
  so updating pan / zoom / rotation is a GPU-layer change with **no recomposition**.
- The gesture loop is **event-driven** — nothing runs per frame.
- The hit-test is **one pure ray-cast on the down event only**.
- Fling and settle are plain Compose `Animatable` animations (`exponentialDecay` + `spring`).

One subtlety worth calling out: the pan / zoom / rotation are accumulated **synchronously** inside
the gesture loop and snapped to **absolute** values. A naive per-event `snapTo(value + delta)` races
under a fast fling — concurrent snap coroutines read a stale value and lose deltas (a fast right-fling
came out under-counted during development). Accumulating the running totals in the loop and snapping
absolutes fixes it.

## Tested & verified

- **Pure maths unit-tested on JVM + iOS** (16 tests): the `OGHitArea` ray-cast (inside / on-edge /
  transparent-corner for circle, triangles, diamond and a lasso) and the scale / pan clamps.
- **Live-verified on Android**: drag in all directions, pan bounds + spring settle clamping to the
  configured limit, and shape-aware grab/ignore (swipe an empty corner of a triangle → ignored; swipe
  inside → grabs).

The gesture-driving code is Compose UI (one file), the maths and the state model are shared
`commonMain`.
