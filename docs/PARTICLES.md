# Particles — `com.solidkey.painpoints.particle` (KMPMedia 1.27.0)

A **data-defined particle system**: an emitter described as plain, serializable JSON that the
library compiles once and simulates each frame. It is the generative-motion member of KMPMedia's
"just data" family, alongside [`OGStyleSpec`](./STYLES.md) (drawing styles),
[`OGParametricSpec`](./PARAMETRIC_SHAPES.md) (shapes) and [`OGAiVector`](./AI_HOOKS.md) (describe →
vector): a designer or a language model authors a `.particles` pack and the lib brings it to life —
no code, no rebuild.

> Confetti on a win, sparks on a tap, snow behind a title, bokeh under a card, a firework burst.
> Drop [`OGParticleView`] in a `Box` above your content and you have a live effect layer.

## Perf

The simulation ([`OGParticleSystem`]) is a fixed-capacity **struct-of-arrays**: positions, velocities
and render outputs live in `FloatArray`/`IntArray`, so a frame is a tight numeric loop with **no
per-frame allocation** and **no RNG object churn** (a tiny deterministic xorshift). A hard
`maxParticles` cap is the perf ceiling. From the same `seed` it runs **frame-identically on Android
and iOS**. Circles (the default) draw with a direct `drawCircle`; other shapes fill a single reused
`Path`. This is the real-app / 60fps gate the rest of the library clears.

## Coordinates & conventions

- **Position** (`x`, `y`) and `spawnRadius` are normalized `0..1` over the view box.
- **Size** (`startSize` / `endSize`) is a fraction of the box's **shorter side** (a radius).
- **Speed** is normalized units per second; **angles** are degrees clockwise from +x with **y down**,
  so `0` = right, `90` = down, `270` (or `-90`) = up. `gravityY > 0` pulls particles **down**.

## Quick start — a ready-made preset

```kotlin
import androidx.compose.foundation.layout.Box
import com.solidkey.painpoints.particle.OGParticleView
import com.solidkey.painpoints.particle.OGParticles

Box(Modifier.fillMaxSize()) {
    YourContent()
    // A celebratory burst on top of everything. The empty areas are fully transparent.
    OGParticleView(OGParticles.CONFETTI, Modifier.fillMaxSize())
}
```

Built-in presets (on `OGParticles`): **`CONFETTI`**, **`SPARKS`**, **`SNOW`**, **`BOKEH`**,
**`RAIN`**, **`FIREWORKS`** — or `OGParticles.preset("snow")` by name.

## Author your own

```kotlin
import com.solidkey.painpoints.particle.OGParticleSpec
import com.solidkey.painpoints.particle.OGParticleView

val embers = OGParticleSpec(
    emissionRate = 40f,            // particles/sec (steady); use `burst` for a one-shot
    angleDeg = 270f, spreadDeg = 40f,
    speed = 0.2f, gravityY = -0.05f,   // drift up
    startColor = "#FFE082", endColor = "#FF6D00", endAlpha = 0f,
    startSize = 0.01f, endSize = 0.003f,
    maxParticles = 150,
)
OGParticleView(embers, Modifier.fillMaxSize())
```

Every field is optional with a sensible default (see the KDoc on [`OGParticleSpec`]). Colours are any
SVG colour string (`#RGB` / `#RRGGBB` / `#AARRGGBB` / `rgb()` / a name), parsed with the **same**
parser the SVG renderer uses. Size and colour interpolate from `start*` to `end*` over each
particle's life.

## From JSON / from a model

```kotlin
import com.solidkey.painpoints.particle.OGParticles

// A designer- or model-authored pack (tolerant of code fences / stray prose):
val system = OGParticles.decode("""{"name":"snow","emissionRate":70,"angleDeg":90,"speed":0.12,"startColor":"#FFFFFF","endAlpha":0}""")

// Hand a model the exact contract, then decode its reply:
val prompt = OGParticles.particlePrompt("a cosy fireplace ember glow rising from the bottom")
val fromModel = OGParticles.decodeOrNull(myLlm.complete(prompt)) ?: OGParticles.SPARKS.toSystem()
```

`OGParticles.encode(spec)` goes the other way (save a pack, seed a prompt with the current one).

## Driving the simulation yourself

[`OGParticleView`] runs the frame clock for you. If you need to render particles into your own
`Canvas` / compositor, drive the system directly — it is pure and allocation-free:

```kotlin
val system = OGParticles.CONFETTI.toSystem()
system.update(dtMs)                       // advance one frame (clamp big dt upstream)
for (i in 0 until system.count) {         // read the live prefix of the arrays
    val cx = system.x[i]; val cy = system.y[i]     // normalized 0..1
    val r = system.size[i]                          // fraction of the shorter side
    val color = system.argb[i]                      // packed ARGB (alpha folded in)
    val rot = system.rotationDeg[i]
}
system.reset()                            // clear + re-arm the opening burst
```

## Shapes

`shape` is `"circle"` (default), `"square"`, `"triangle"` or `"star"`. For richer silhouettes, pair
particles with a [parametric shape](./PARAMETRIC_SHAPES.md).

## Honest limits

- One `start → end` colour ramp per emitter (no per-particle hue yet) — multi-colour confetti is a
  natural follow-up (`colorJitter`).
- Alpha is monotonic `start → end`; a fade-**in**-then-out isn't expressible in one emitter.
- The spawn area is a disk (`spawnRadius`); a line/edge emitter is a future addition.

## Where this sits on the roadmap

The first shipped item of the new **Bet — Generative & living content**: turning motion itself into
shareable data, the same way [styles](./STYLES.md) and [parametric shapes](./PARAMETRIC_SHAPES.md)
turn looks and forms into data.
