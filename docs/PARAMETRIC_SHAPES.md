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

## Snowflakes — a recursive-fractal crystal generator (`OGSnowflake`)

`OGSnowflake` is the line-figure cousin of the closed parametric outlines: a deterministic `seed` → a
unique, n-fold **symmetric ice crystal** as plain line **segments** (`OGSnowflakeSegment(a, b)` in a
unit space centred at `(0,0)`). Unlike the filled parametric shapes, a snowflake is a **stroked line
figure**, so the caller turns the segments into a `Path` and strokes it (no two seeds alike).

```kotlin
import com.solidkey.painpoints.shape.OGSnowflake

val segments = OGSnowflake.generate(seed = 42)      // arms = 6, branches = 3, depth = 2 by default

// Turn them into a stroked Path, scaled + translated to place the crystal:
val path = Path().apply {
    for (s in segments) {
        moveTo(cx + s.a.x * r, cy + s.a.y * r)
        lineTo(cx + s.b.x * r, cy + s.b.y * r)
    }
}
```

How it is built: one **arm** is grown along +Y — a central spine plus a few side-twigs — then
mirrored left ↔ right for bilateral symmetry, and that whole arm is copied `arms` times around the
centre (`6` = a real snowflake's hexagonal symmetry). Each twig is a tiny **recursive fractal**: at
`depth > 0` every twig sprouts its own smaller side-twigs, and those sprout again, the way a real
stellar-dendrite crystal grows.

```kotlin
fun generate(
    seed: Int,
    arms: Int = 6,        // radial symmetry; clamped 3..12 (6 is natural)
    branches: Int = 3,    // primary side-twigs per arm; clamped 0..6
    depth: Int = 2,       // fractal recursion; clamped 0..4
): List<OGSnowflakeSegment>
```

`depth` is the new knob: `0` = the old plain-prong look (straight prongs), `2` (the default) reads as a
convincing dendrite, capped at `4`. It is pure maths with a seeded xorshift — no Compose, zero
dependency — so a given seed yields the same crystal on Android, iOS, preview and export.

## Where this sits on the roadmap

Part of the new **Bet — Generative & living content**, with [particles](./PARTICLES.md): describing
forms and motion as shareable data instead of baked assets.
