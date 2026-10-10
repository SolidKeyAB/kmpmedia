# Motion design — `com.solidkey.painpoints.motion` (KMPMedia 1.34.0)

Three motion-graphics primitives that turn a handful of numbers into the moves that make an interface
feel alive: **draw-on** (a line or logo that draws itself on), **motion paths** (send any element
gliding along a curve, optionally turning to face its travel), and **stagger** (orchestrate a group
with offset timing so it cascades, waves or pops instead of moving in lockstep).

Like the rest of KMPMedia it is **just data + maths**: the path and the stagger are plain,
serializable specs ([`OGMotionPathSpec`], [`OGStaggerSpec`]) a designer or a language model can author
(see [`OGMotions`]), and the live core ([`OGMotionPath`], [`OGStagger`]) is pure, platform-independent
and directly unit-testable. The Compose layer is two thin pieces: [`Modifier.ogMotionPath`] and
[`OGDrawOnStroke`].

## Perf

The hot path is built for 60fps, the gate the rest of the library clears:

- [`OGMotionPath`] precomputes cumulative arc length **once**; every `pointAt` / `tangentDegAt` is an
  O(log n) binary search, so animating many elements along paths per frame is cheap. Smooth curves
  ([`smoothOpen`], [`loop`]) are sampled **once** at build time, never per frame.
- [`Modifier.ogMotionPath`] reads `progress()` **inside a `graphicsLayer`**, so an animated value
  re-positions the element each frame **without recomposing** it.
- [`OGDrawOnStroke`] reads `progress()` **inside the `Canvas` draw**, so it redraws only that layer.
- [`OGStagger`] is stateless pure maths with no allocation.

## Coordinates & conventions

- Paths are **normalized `0..1`** over the target box (same convention as `OGPoint` / `OGPolygonShape`
  everywhere else). `ogMotionPath` scales them by a `containerSize: Size` you pass (the container's px
  size); `OGDrawOnStroke` scales them by the `Canvas` size. Stroke `width` is in pixels.
- `progress` is `0..1` **by distance travelled**, not by vertex index, so an evenly-advancing progress
  moves at constant speed even on unevenly-spaced points.
- Angles from `tangentDegAt` are degrees clockwise from +x with **y down** (the screen convention).
  `ogMotionPath` passes the container size so `orient` is **exact even in a non-square box**; the bare
  `OGMotionPath.tangentDegAt(t)` defaults to normalized space (pass `scaleX`/`scaleY` for screen-exact).
- Smooth paths use the library's **centripetal** Catmull-Rom (cusp-free on unevenly-spaced points),
  the same spline that smooths a lasso. Draw-on rebuilds only the *revealed* sub-path each frame, so
  keep very dense paths (high `samplesPerSegment` × many points) reasonable.

## Draw-on — a line that draws itself on

```kotlin
import androidx.compose.animation.core.*
import com.solidkey.painpoints.motion.OGMotionPath
import com.solidkey.painpoints.motion.OGDrawOnStroke

// A path in normalized 0..1 (hand-authored, from a shape, or OGMotions.decodePath(...)).
val logo = remember { OGMotionPath.smoothOpen(listOf(/* OGPoint(...) ... */)) }
val t by rememberInfiniteTransition().animateFloat(
    0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Restart)
)

OGDrawOnStroke(
    path = logo,
    progress = { t },                 // 0 -> 1 reveals the stroke
    color = Color(0xFFFFC107),
    strokeWidth = 6f,
    modifier = Modifier.size(180.dp),
)
```

Prefer to draw it yourself? Use the `DrawScope` extension inside any `Canvas`:

```kotlin
Canvas(Modifier.fillMaxSize()) {
    drawOGStroke(logo, progress = t, color = Color.White, width = 6f, start = 0f)
}
```

Animate `start` as well as `progress` for a travelling dash.

## Motion path — glide an element along a curve

```kotlin
import androidx.compose.ui.geometry.Size
import com.solidkey.painpoints.motion.ogMotionPath

var box by remember { mutableStateOf(Size.Zero) }
val path = remember { OGMotionPath.of(0f to 0.8f, 0.5f to 0.2f, 1f to 0.8f) } // an arc L→R

Box(Modifier.fillMaxSize().onSizeChanged { box = it.toSize() }) {
    OGSVGView(
        /* ... */,
        modifier = Modifier.ogMotionPath(path, { t }, box, orient = true) // turns to face travel
    )
}
```

`center = true` (default) rides the element's centre on the path; `orient = true` rotates it to the
travel direction. Use [`OGMotionPath.loop`] for an endless orbit.

## Stagger — orchestrate a group

```kotlin
import com.solidkey.painpoints.motion.OGStagger
import com.solidkey.painpoints.motion.OGEasing

items.forEachIndexed { i, item ->
    // Each element gets its own 0..1 progress from the shared timeline t.
    val p = OGStagger.progressFor(i, items.size, t, stagger = 0.6f, easing = OGEasing.EASE_OUT)
    Row(Modifier.graphicsLayer { alpha = p; translationY = (1f - p) * 40f }) { /* ... */ }
}
```

`stagger` is `0..1`: `0` = everyone together, near `1` = one after another. `reverse = true` makes the
last element lead. Easings: `LINEAR`, `EASE_IN`, `EASE_OUT`, `EASE_IN_OUT`, `OVERSHOOT` (a pop).

## Data form — author / decode packs

```kotlin
import com.solidkey.painpoints.motion.OGMotions

val path = OGMotions.decodePath("""{"points":[0,0.8, 0.5,0.2, 1,0.8], "smooth":true}""")
val stagger = OGMotions.staggerPreset("cascade")!!            // together / cascade / wave / sequential / pop / reverse
val prompt = OGMotions.motionPathPrompt("an S-curve from left to right") // hand to a model → decodePath(reply)
```

Decoding is tolerant of code fences and stray prose (shared [`OGAiVector`](./AI_HOOKS.md) JSON config),
and `decode*OrNull` returns `null` instead of throwing on malformed input.

## Dynamics — making motion feel real (1.35.0)

Paths and stagger say *where* and *when*; **dynamics** say *how it feels*. These are the pure-maths
"principles of animation" that turn a robotic keyframe into motion with weight: lag, overshoot,
settle, follow-through. The library owns the *feel* (reusable filters); your app owns the *intent*
(poses, choreography). A full physics / IK / gait engine is deliberately out of scope.

**Spring — the core.** An exact, analytically-solved damped oscillator (stiffness + damping ratio),
in two forms sharing one maths core: a live mutable stepper, and a **closed-form** `value at t` that
the compositor can scrub and the GIF/MP4 exporter can sample deterministically (the one thing
Compose's own `spring()` can't do).

```kotlin
import com.solidkey.painpoints.motion.*

// Live: one per joint / property, updated each frame (no allocation).
val y = remember { OGSpringValue(0f) }
y.update(target = targetY, dt = dtSeconds, spec = OGSpringSpec(stiffness = 220f, dampingRatio = 0.4f))
Box(Modifier.graphicsLayer { translationY = y.value })

// Closed-form: scrub / export a from→to with a bouncy feel.
val v = OGSpring.valueAt(OGSpringSpec(dampingRatio = 0.3f), t = positionSec, from = 0f, to = 1f)
```

**Follow-through.** `OGFollowChain(n, spec)` is `n` springs in series, each trailing the one before —
hair, a cape, a tail, a lagging forearm. `update(leaderX, leaderY, dt)`; read lagged links with
`point(i)`.

**Squash & stretch.** `OGSquash.fromSpeed(speed, intensity)` → a volume-preserving `OGScale`
(`scaleX * scaleY == 1`) to drop on a `graphicsLayer` — stretch when fast, squash on impact.

**Idle sway.** `OGSway(amplitude, frequency)` wraps the library's fractal noise so an idle element
breathes instead of freezing between animations. **Anticipation easing.** `OGEasing.ANTICIPATE` /
`ANTICIPATE_OVERSHOOT` wind back before launching / overshoot on arrival.

**AI-authorable feel.** The whole feel is data: `OGDynamicsSpec` (spring + followLinks + squash + sway
+ easing) with an `OGDynamics` codec + presets (`bouncy` / `heavy` / `snappy` / `gentle` / `stiff`)
and a `dynamicsPrompt(...)`. A developer describes it in words ("a heavy, bouncy walk that overshoots
and settles slowly") and a model returns the JSON — same codec-not-SDK pattern as the rest of the lib.

```kotlin
val feel = OGDynamics.preset("bouncy")!!            // or OGDynamics.decodeSpec(modelReply)
val spec = feel.spring                              // feed into OGSpringValue / OGSpring
```

**Cloth — real fabric (1.36.0).** `OGClothStrip(spec)` is a lightweight 1-D Verlet cloth strip: a
scarf, cape edge, flag, banner or hair strand. Unlike `OGFollowChain` (springs in series, which
*stretch* and trail like a comet tail), the strip is **inextensible** — distance constraints hold the
rest length — and it has **gravity** and ambient **wind**, so it *hangs* at rest and *flutters* in
motion. **Which one:** stretches & trails → `OGFollowChain`; hangs at a fixed length & flutters →
`OGClothStrip`.

```kotlin
val scarf = remember { OGClothStrip(OGCloths.preset("scarf")!!) }
scarf.step(dt, anchorX = neckX, anchorY = neckY)      // pin the head at the neck each frame
// then draw a tapered ribbon through scarf.point(0 .. count-1)
```

It advances on a **fixed internal substep** (position Verlet is only stable + deterministic at a
constant `dt`), so it is frame-identical on Android & iOS and **exact for fixed-step GIF/MP4 export**.
Unlike `OGSpring` it is a **live stepper, not closed-form** — there is no `valueAt(t)`; to *scrub* it,
re-simulate from `0` deterministically via `OGCloths.sampleAt(spec, t, anchorAt)`. The wind is seeded
fractal noise (no caller randomness), so runs reproduce exactly. Data form `OGClothSpec` + `OGCloths`
codec + presets (`scarf` / `flag` / `banner` / `hair`) + `clothPrompt(...)`, same codec-not-SDK pattern.
**Scope is deliberately a 1-D strip only** — a 2-D cloth mesh, collisions and a public solver API are
rejected-by-default (that would make it a physics engine, which this is not).

## API summary

| Piece | What it is |
|-------|------------|
| [`OGMotionPath`] | Arc-length path: `of` / `smoothOpen` / `loop`; `pointAt` / `tangentDegAt` / `trimmed`. |
| [`OGStagger`] / `OGEasing` | Per-element offset timing + easing curves (incl. anticipation). Pure. |
| [`OGMotionPathSpec`] / [`OGStaggerSpec`] | Serializable data form of each. |
| [`OGMotions`] | Codec + presets + model prompts (path / stagger). |
| [`Modifier.ogMotionPath`] | Move/orient a composable along a path (`graphicsLayer`, no recompose). |
| [`OGDrawOnStroke`] / `drawOGStroke` | Progressive stroke reveal (draw-on). |
| `OGSpringSpec` / `OGSpringValue` / `OGSpring` | Second-order spring: live stepper + closed-form (scrub/export). |
| `OGFollowChain` | Trailing chain for follow-through (hair / cape / limbs). |
| `OGSquash` / `OGSway` | Volume-preserving squash & stretch; noise-driven idle sway. |
| `OGClothStrip` | 1-D Verlet cloth strip (inextensible, gravity + seeded wind): scarf / flag / banner / hair. Live stepper (fixed substep, deterministic). |
| `OGClothSpec` / `OGCloths` | Serializable cloth feel + codec + presets + `clothPrompt`; `sampleAt` for deterministic scrub. |
| `OGDynamicsSpec` / `OGDynamics` | Serializable "feel" + codec + presets + `dynamicsPrompt` (AI-authorable). |

All in `commonMain`, zero new dependencies.
