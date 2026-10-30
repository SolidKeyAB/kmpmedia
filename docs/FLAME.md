# Fire — flickering flame (`OGFlame`)

A body of living, upward-licking **fire**, described as plain data. It is the fire sibling of
[`OGFog`](../library/src/commonMain/kotlin/com/solidkey/painpoints/fx/OGFogSpec.kt) (smoke) and the
particle `EMBERS` preset (sparks) in the `com.solidkey.painpoints.fx` family: a designer or a language
model authors a `.flame` pack and the library brings it to life — a candle, a campfire, a torch, an
inferno — no code and no rebuild.

- **Package:** `com.solidkey.painpoints.fx`
- **Spec + pure geometry:** `OGFlameSpec`, `flameTongues`
- **View:** `OGFlameView`
- **Codec + presets:** `OGFlame`

## How it stays 60fps (the perf gate)

No fluid sim, no per-pixel shader (the library floors runtime shaders out). A fire is a handful of
**tongues** — tapered flame shapes rooted along a baseline — whose centrelines **wave** with fractal
[`OGFxNoise`] noise and whose heights **flicker** over time. Each tongue is one GPU-filled path with a
vertical hot → cool gradient; the noise is sampled a few times per tongue, never per pixel. It is pure
and deterministic from `seed`, so it is frame-identical on Android & iOS and bakes into an export
byte-for-byte, exactly like the rest of the FX layer.

## Coordinates

Everything is normalized `0..1` of the view: `baseY` is where the flames are rooted (`1` = the bottom
edge), `height` / `width` / `sway` are fractions of the view. `y` grows downward (screen convention),
so a tongue rises from `baseY` up toward smaller `y`.

## Quick start — a ready-made preset

```kotlin
import com.solidkey.painpoints.fx.OGFlameView
import com.solidkey.painpoints.fx.OGFlame

// A warm hearth fire at the bottom of the box. Transparent between and above the tongues.
OGFlameView(OGFlame.CAMPFIRE, Modifier.fillMaxSize())
```

Built-in presets (on `OGFlame`): **`CANDLE`** (one slim calm tongue), **`CAMPFIRE`** (a spread of
lively orange tongues), **`TORCH`** (fewer, taller tongues with a steady lean) and **`INFERNO`** (many
tall, ragged, fast-flickering tongues) — or `OGFlame.preset("torch")` by name. For a candle, use the
one-tongue `CANDLE` preset in a narrow box.

## The view

```kotlin
@Composable
fun OGFlameView(
    spec: OGFlameSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    windOverride: Float? = null,   // bend the whole fire with a scene's live breeze
)
```

Set `playing = false` to freeze it. Pass a live `windOverride` to bend the whole fire with a scene's
breeze (overrides `OGFlameSpec.wind` when non-null) — drive it from the same breeze that pushes your
snow / leaves so the fire reacts to the weather. Overlay it in a `Box`.

## The spec

```kotlin
import com.solidkey.painpoints.fx.OGFlameSpec

val torch = OGFlameSpec(
    coreColor = "#FFE08A", tipColor = "#FF4312",
    tongues = 4, height = 0.78f, width = 0.13f,
    sway = 0.05f, wind = 0.08f, flicker = 0.28f,
)
OGFlameView(torch, Modifier.fillMaxSize())
```

| field | default | meaning |
|---|---|---|
| `name` | `null` | optional label |
| `coreColor` | `"#FFD24D"` | the hot colour at the **base** of each tongue (any SVG colour string) |
| `tipColor` | `"#FF4A1C"` | the cooler colour at the **tip** of each tongue |
| `tongues` | `9` | number of flame tongues across the base; more = wider / fuller. Clamped `1..60` |
| `height` | `0.62f` | average tongue height as a fraction of the view |
| `heightJitter` | `0.45f` | per-tongue height variation (`0..1`): `0` = all equal, higher = raggeder crown |
| `width` | `0.16f` | base width of a single tongue (fraction of the view); tongues overlap into one mass |
| `sway` | `0.06f` | how far a tongue's tip swings sideways as it licks (fraction of the view) |
| `wind` | `0f` | a **prevailing wind** that bends the whole flame sideways (`+` = right, `-` = left); grows with height so the base stays put. Clamped `-1..1` |
| `swaySpeed` | `1.1f` | how fast the sideways licking evolves (cycles/second-ish) |
| `flicker` | `0.3f` | height-flicker amount (`0..1`): the fast up-down pulsing of the crown |
| `flickerSpeed` | `2.4f` | how fast the height flicker pulses |
| `octaves` | `3` | fractal-noise octaves for the waving edges. Clamped `1..8` |
| `opacity` | `0.92f` | overall opacity ceiling (`0..1`) |
| `baseY` | `1f` | where the fire is rooted, `0..1` of the view height (`1` = the bottom edge) |
| `seed` | `0` | seed for the deterministic tongue layout, wave and flicker |

`spec.sanitized()` clamps every field into its sane range, so lenient model / designer input never
breaks a frame.

## From JSON / from a model

```kotlin
import com.solidkey.painpoints.fx.OGFlame

val spec = OGFlame.decodeSpec("""{"name":"campfire","coreColor":"#FFD24D","tipColor":"#FF3B10","tongues":10}""")

val prompt = OGFlame.flamePrompt("a single calm candle flame bending gently in a draught")
val fromModel = OGFlame.decodeSpecOrNull(myLlm.complete(prompt)) ?: OGFlame.CANDLE
```

`OGFlame.encode(spec)` goes the other way; the codec is lenient (code fences / stray prose survive)
and `decodeSpecOrNull` returns `null` instead of throwing — no network, no AI SDK.

## Driving the geometry yourself

`flameTongues(spec, timeSec)` is the pure, deterministic geometry — each tongue's `spine` centreline
(base → flickering tip), base `halfWidth` and peak `alpha` — shared by the view and any off-screen
baker, so an export matches the preview. Build a tapered path around each spine and fill it with the
core → tip gradient (the one shared per-frame function is `drawOGFlameFrame`).

## Honest limits

- One `core → tip` vertical gradient per tongue (no multi-stop ramp).
- A stylised flame shape, not a volumetric / lit fire — no smoke plume above (pair it with `OGFog`),
  no embers (pair it with the `EMBERS` particle preset).
