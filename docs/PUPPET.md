# Cut-out puppet (`OGPuppet`)

A **data-defined cut-out puppet**: a hand-drawn figure cut into transparent part-layers (a still
**base** plus jointed **movers**) and reassembled as a live raster puppet — each mover rotating about
the joint it was pinned at, breathing, leaning and *watching* a look target. It is the raster sibling
of the library's generative data packs ([particles](./PARTICLES.md), [parametric shapes](./PARAMETRIC_SHAPES.md)):
a designer (or the Layer Cutter tool) authors a `.puppet` pack of joints + motion, and the lib brings
it to life — no per-app rigging code.

- **Package:** `com.solidkey.painpoints.puppet`
- **Spec:** `OGPuppetSpec` → `OGPuppetPart`, plus the transient `OGPuppetPose`
- **Pure pose math:** `OGPuppetRig` (no Compose — deterministic, testable)
- **View:** `OGPuppetView`
- **Codec + prompt:** `OGPuppet`

Unlike a vector scene, a cut-out puppet is **real drawn pixels** (bundled PNG / WebP sprites), so a
hand-drawn character part lives on screen as itself. The pure transform math (`OGPuppetRig`) is split
out so the same `tSec` + `look` yields the same pose on Android and iOS.

## Coordinates

Every part sprite shares the base's frame (each cut part baked / placed on the same canvas, so a mover
needs only its joint). `pivotX` / `pivotY` are the joint as a `0..1` fraction of that frame;
`tuckX` / `tuckY` and `gaze` are fractions of the frame width / height. A **look** value is `-1..1`
horizontally (`-1` = hard left, `+1` = hard right, `0` = centre, `null` = a gentle idle glance).

## Quick start

```kotlin
import com.solidkey.painpoints.puppet.OGPuppet
import com.solidkey.painpoints.puppet.OGPuppetView

// A puppet pack (authored by the Layer Cutter tool or by hand). The self-loading overload resolves
// each sprite by resource name via the platform image loader.
val spec = OGPuppet.decode(myPuppetJson)

OGPuppetView(
    spec,
    modifier = Modifier.aspectRatio(spec.aspect),  // match the base frame so pivots line up
    look = watchTarget,                             // -1..1, or null for an idle glance
)
```

Give the box the base frame's `aspect` so the frame isn't letterboxed and every mover's pivot fraction
lines up with the drawn pixels.

## The spec

`OGPuppetSpec` is the whole figure: the still `base` sprite, its `parts` (movers, back → front), the
frame `aspect`, and the feel of its idle life.

```kotlin
import com.solidkey.painpoints.puppet.OGPuppetSpec
import com.solidkey.painpoints.puppet.OGPuppetPart

val bee = OGPuppetSpec(
    base = "bee_body",
    aspect = 1.2f,
    grounded = false,                 // hovers in place (a flying figure)
    headMirror = false,               // symmetric forward face: tilts toward the look, no flip
    parts = listOf(
        OGPuppetPart(res = "bee_head", head = true),
        OGPuppetPart(res = "bee_wing_l", pivotX = 0.4f, pivotY = 0.35f, swingDeg = 14f, speed = 3f),
        OGPuppetPart(res = "bee_wing_r", pivotX = 0.6f, pivotY = 0.35f, swingDeg = 14f, speed = 3f, phase = 3.14f),
    ),
)
```

### `OGPuppetSpec`

| field | default | meaning |
|---|---|---|
| `base` | — | resource name of the still base sprite |
| `parts` | `[]` | the movers, back → front |
| `aspect` | `1f` | the base frame's width / height |
| `grounded` | `false` | `true` = stands / sits and bends about its feet (never lifts); `false` = hovers in place (a slow vertical float + airborne tilt) |
| `breatheAmp` / `breatheSpeed` | `0.02f` / `0.5f` | a gentle whole-figure scale pulse |
| `leanAmp` | `0.06f` | whole-figure horizontal lean toward the look target (fraction of width) |
| `lookTurnDeg` | `12f` | head-turn tilt toward the look target (degrees) for the `head` part |
| `headCenterX` / `headCenterY` | `0.5f` / `0.5f` | the head's own centre (frame fraction) — it mirror-flips about this to turn in place |
| `headMirror` | `true` | whether a head part mirror-**flips** to turn (a profile / one-sided face reads as turning when flipped); `false` for a symmetric forward face — it just tilts, no flip |
| `headLookInvert` | `false` | invert the head's look **direction** (tilt + mirror); set `true` if the drawn head turns *away* from the look target instead of toward it |
| `bendDeg` / `bendLookDeg` | `4f` / `5f` | grounded idle bend amplitude + how much more it leans toward the look |
| `hoverAmp` / `tiltDeg` | `0.05f` / `1.5f` | flying hover: vertical float (fraction of height) + airborne tilt sway (degrees) |
| `name` | `null` | optional label |

### `OGPuppetPart`

| field | default | meaning |
|---|---|---|
| `res` | — | resource name of this part's transparent sprite |
| `pivotX` / `pivotY` | `0.5f` / `0.5f` | the joint this mover rotates about, `0..1` of the shared frame |
| `swingDeg` | `0f` | idle rotation amplitude (degrees) about the pivot — a faint life-swing (`0` = still) |
| `speed` / `phase` | `1f` / `0f` | idle swing rate + phase, so sibling parts don't move in lockstep |
| `tuckX` / `tuckY` | `0f` / `0f` | a static offset of this part within the frame (fraction of width / height) |
| `gaze` | `0f` | this part **watches** the look target: it slides toward it by `gaze·look` (e.g. coal eyes / a mouth with no head to turn) |
| `head` | `false` | this part is the **head**: it turns toward the look target (a tilt + mirror-flip about `headCenterX` / `headCenterY`) instead of a plain idle swing. At most one part should set this |

`headMirror` and `headLookInvert` solve the two gotchas of a cut-out head: a **symmetric** forward face
(a bee) should tilt but **not** flip (`headMirror = false`), and when the drawn head faces the "wrong"
way so it appears to turn *away* from the look, flip the direction with `headLookInvert = true`.

### Host beats — `OGPuppetPose`

A transient whole-figure pose laid **on top** of the idle life — the host folds its own game beats (a
score pop, a tap-spotlight, a reaction) into one: `OGPuppetPose(dy, scale, rot)` (`dy` a fraction of
height, `scale` a multiplier, `rot` degrees). Identity = `OGPuppetPose.Idle`.

## The view

Two overloads:

```kotlin
// Self-loading: resolves base + part sprites by resource name (the pure-lib path, no sprite swaps).
@Composable
fun OGPuppetView(
    spec: OGPuppetSpec,
    modifier: Modifier = Modifier,
    look: Float? = null,
    pose: OGPuppetPose = OGPuppetPose.Idle,
    playing: Boolean = true,
)

// Painter overload: you pass the already-decoded base + part painters (and an optional headPainter to
// swap the head's sprite, e.g. an expression frame), keeping control of loading / sprite-swapping.
@Composable
fun OGPuppetView(
    base: Painter?,
    parts: List<Painter?>,
    spec: OGPuppetSpec,
    modifier: Modifier = Modifier,
    look: Float? = null,
    pose: OGPuppetPose = OGPuppetPose.Idle,
    headPainter: Painter? = null,
    playing: Boolean = true,
)
```

Grounded puppets pivot about their feet and never lift; flying ones float in place. Set
`playing = false` to freeze the rig.

## From JSON / from a model

```kotlin
import com.solidkey.painpoints.puppet.OGPuppet

val spec = OGPuppet.decode(myPuppetJson)          // throws on malformed
val safe = OGPuppet.decodeOrNull(myPuppetJson)    // null instead

val prompt = OGPuppet.puppetPrompt("a flying bee with two beating wings and a head that watches")
```

Same lenient JSON config as the rest of the data layers (code fences / stray prose survive);
`OGPuppet.encode(spec)` serialises a pack back. The pack names the base + part sprites by resource —
the **host app bundles** them (Android `res/drawable`, iOS bundle), resolved by the shared image loader.

## The pure rig — `OGPuppetRig`

All pose math lives in `OGPuppetRig` with no Compose: `effectiveLook`, `figureFloat`, `figureBreathe`,
`figureLean`, `figureRotation`, `partAngle`, `partScaleX`, `partOffsetX` / `partOffsetY`. Same inputs →
identical output on every platform, so it is directly unit-testable and the view is a thin renderer.

## Honest limits

- Each part is a **flat raster** rotated about one joint — not a mesh deform (for that, see `OGRig`).
- Movers rotate about a single pivot (no multi-bone chains); `OGPuppetPose` is one whole-figure beat.
- A puppet's sprites are host-bundled assets, so unlike the vector packs a blind describe → movie model
  can't author one from scratch — but a tool (the Layer Cutter) or an app assembling its own art can.
