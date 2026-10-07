# Parametric shapes — `com.solidkey.painpoints.shape.OGParametric` (KMPMedia 1.28.0)

**Parametric shapes** turn a handful of numbers into a closed outline: a 7-point star, a 12-tooth
gear, a 6-petal flower, a squircle, an organic blob. They are the *generative* sibling of
[`OGPolygonShape`](./POLYGON_SHAPE.md)'s hand-/AI-drawn lasso — instead of supplying vertices, you
describe a **family** and get the vertices back (a `List<OGPoint>` in normalized `0..1` space,
centred in the box with radius up to `0.5`).

The result drops into anything that takes a shape: wrap it in an [`OGPolygonShape`] for a `clipShape`
(optionally rounded with `smoothing`), feed it to a [style pipeline](./STYLES.md), morph it, or use
it as a [particle](./PARTICLES.md) silhouette.

## Perf & determinism

Everything is **pure maths** — no RNG at call time; the blob's lumpiness is a deterministic hash of
its `seed` — so a given spec yields the **same** outline on Android, iOS, preview and export, and
every generator is directly unit-testable. Generation happens **once** when you build the shape,
never per frame, so it's free on the 60fps clip path.

## Generators

```kotlin
import com.solidkey.painpoints.shape.OGParametric

OGParametric.regularPolygon(sides = 6)                       // hexagon (points up)
OGParametric.star(points = 5, innerRatio = 0.45f)            // 5-point star; small ratio = spiky
OGParametric.gear(teeth = 12, depth = 0.3f)                  // cog with square teeth
OGParametric.flower(petals = 6, depth = 0.4f)                // rose / flower
OGParametric.superellipse(exponent = 8f)                     // squircle (2 = circle, <1 = 4-point star)
OGParametric.blob(lobes = 6, irregularity = 0.5f, seed = 3)  // organic lump; seed varies it
```

Use one directly as a clip:

```kotlin
import com.solidkey.painpoints.image.OGImageView
import com.solidkey.painpoints.shape.OGPolygonShape

OGImageView(
    source = OGImageUrlType("https://…/photo.jpg"),
    clipShape = OGPolygonShape(OGParametric.star(points = 6), smoothing = 0.2f),
    onEventTriggered = { _, _ -> },
)
```

## From JSON / from a model

Like every KMPMedia data layer, a parametric shape is serializable, so a designer or a model can
author one as plain JSON. [`OGParametricSpec`] names a `kind` and its parameters; `count` is the
primary integer for the family (polygon sides / star points / gear teeth / flower petals / blob
lobes).

```kotlin
import com.solidkey.painpoints.shape.OGParametricSpec
import com.solidkey.painpoints.shape.OGParametrics

val gear = OGParametricSpec(kind = "gear", count = 12, depth = 0.3f).toShape()   // -> OGPolygonShape

// Decode a pack (tolerant of code fences), or hand a model the contract:
val shape = OGParametrics.decodeOrNull("""{"kind":"blob","count":6,"irregularity":0.5,"seed":3}""")
val prompt = OGParametrics.shapePrompt("a chunky 10-tooth cog")
val fromModel = OGParametrics.decodeOrNull(myLlm.complete(prompt))
```

`OGParametrics.encode(spec)` serializes one back out.

### Kinds

| `kind` | reads | shape |
|---|---|---|
| `polygon` (`ngon`) | `count`, `rotationDeg` | regular n-gon |
| `star` | `count`, `innerRatio`, `rotationDeg` | n-point star |
| `gear` (`cog`) | `count`, `depth`, `rotationDeg` | cog with square teeth |
| `flower` (`rose`) | `count`, `depth`, `samples`, `rotationDeg` | petalled rose |
| `superellipse` (`squircle`) | `exponent`, `samples`, `rotationDeg` | circle ⇄ square ⇄ 4-star |
| `blob` | `count`, `irregularity`, `seed`, `samples` | organic lump |

An unknown `kind` yields an empty outline (nothing shown) rather than throwing.

## Where this sits on the roadmap

Part of the new **Bet — Generative & living content**, with [particles](./PARTICLES.md): describing
forms and motion as shareable data instead of baked assets.
