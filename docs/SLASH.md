# Breathing-slash ribbons (`OGSlash`)

The signature **anime-action** effect: a glowing, tapered arc that **draws on** along a path and
stays alive with a flowing edge and a breathing width. One primitive wears three elements, chosen by
**data, not code** — a **water** sweep, a **flame** lick, a **thunder** bolt.

It is the motion sibling of the data-defined [particle system](PARTICLES.md) and
[parametric shapes](PARAMETRIC_SHAPES.md): a designer or a language model authors a `.slash` pack and
the library brings it to life, no code and no rebuild.

- **Package:** `com.solidkey.painpoints.fx`
- **Spec + pure geometry:** `OGSlashSpec`, `slashCenterline`, `slashRibbonOutline`, `slashFrameAt`
- **View:** `OGSlashView`
- **Codec + presets:** `OGSlashes`

## Quick start

```kotlin
import com.solidkey.painpoints.fx.OGSlashView
import com.solidkey.painpoints.fx.OGSlashes

// A ready-made water slash, looping draw-on → hold → fade.
OGSlashView(OGSlashes.WATER, modifier = Modifier.fillMaxSize())
```

Three signature presets: `OGSlashes.WATER`, `OGSlashes.FLAME`, `OGSlashes.THUNDER`.

## From data (designer- or model-authored)

```kotlin
val spec = OGSlashes.decodeSpec(
    """
    {
      "name": "water",
      "path": [{"x":0.1,"y":0.34},{"x":0.34,"y":0.2},{"x":0.6,"y":0.34},{"x":0.88,"y":0.66}],
      "edge": "wave",
      "colorStart": "#4FC3F7",
      "colorEnd": "#01579B",
      "glow": 0.8
    }
    """
)
OGSlashView(spec)
```

For an LLM, hand it `OGSlashes.slashPrompt("a diagonal blue water sweep")` and pass the reply to
`decodeSpec`. The codec tolerates markdown code fences and stray prose (same lenient JSON as the rest
of the AI layer); `decodeSpecOrNull` returns `null` instead of throwing.

## The spec

| field | meaning |
|---|---|
| `path` | the blade trajectory, `{x,y}` points in `0..1` (head → tail), ≥ 2; a smooth centripetal Catmull-Rom curve is fit through them |
| `width` | max ribbon half-thickness, fraction of the shorter side |
| `taper` | tip sharpness — higher tapers the ends to a finer point |
| `peak` | where along the length the ribbon is widest (`0..1`) |
| `smoothing` | centreline roundness (`0` = straight segments, `1` = full curve) |
| `samples` | centreline resolution / perf cap (clamped `4..512`) |
| `colorStart` / `colorEnd` | flowing gradient along the path (any SVG colour string) |
| `glow` / `glowColor` | additive bloom strength `0..1` and its colour |
| `edge` | `smooth` · `wave` (water) · `rough` (flame) · `bolt` (thunder) |
| `edgeAmp` / `edgeSpeed` / `edgeDetail` | edge displacement amplitude, flow/flicker speed, richness |
| `breatheAmp` / `breatheSpeed` | width pulsation amount and speed |
| `revealMs` / `holdMs` / `fadeMs` / `loop` | the draw-on → hold → fade lifecycle |
| `seed` | deterministic edge noise (`rough` / `bolt`) |

## Composing the full look

Drop an [`OGParticleView`](PARTICLES.md) over the slash in a `Box` for the elemental detail — water
**droplets**, flame **embers** — and keyframe both on the [compositor timeline](COMPOSITOR.md):

```kotlin
Box(Modifier.fillMaxSize()) {
    OGSlashView(OGSlashes.WATER)
    OGParticleView(OGParticles.SPARKS) // or a droplet/ember pack
}
```

## Design notes (the perf gate)

- **Parse-once.** The smooth centreline is sampled once per spec (`remember(spec)` in the view); each
  frame only rebuilds the tapered outline (a cheap sine/noise pass over the samples) and fills it.
- **GPU-drawn.** The ribbon is a single filled `Path` with a gradient `Brush`. The glow is a **layered
  additive bloom** — several `BlendMode.Plus` strokes of the tapered outline (wide+faint → tight+bright)
  plus a white-hot core down the spine — deliberately **not** `RenderEffect` blur, which is flaky over
  surfaces and API-floored on Android.
- **Perceptual colour (OKLab).** The gradient is interpolated in [OKLab](OGFxColor) and sampled into
  stops, so a blue→deep-blue or yellow→orange ramp stays even instead of dipping through a muddy middle.
- **Organic motion (simplex / fbm).** The `wave` / `rough` / `bolt` edges are driven by 2D **simplex
  noise** + fractal Brownian motion (`OGFxNoise`) sampled against time — so water ripples and flame
  licks, with no axis-aligned artefacts. `bolt` additionally grows **forked branches** (`slashBranches`).
- **Deterministic, cross-platform.** All geometry + noise + colour is pure math (no RNG), so from the
  same `seed` it renders frame-identically on Android and iOS. Verified by the test suite on both.
- **Zero new dependencies.** Compose only. It reuses the library's own centripetal Catmull-Rom math,
  SVG colour parser, and lenient AI JSON config.

## Honest limits (easy follow-ups)

- The glow is a layered additive-stroke bloom, not a true gaussian bloom. It reads as neon (the intent),
  but the physically-soft version needs an offscreen blur (Android `RenderEffect` / Skiko SkSL) which is
  API-floored and not frame-identical across platforms — so it would be an **opt-in** that degrades to
  this layered bloom. Like the soft masks, any glow works over shapes/Canvas, not the live video surface.
- One start→end gradient per slash (no multi-stop ramp yet, though it is now perceptually interpolated).

## References

The procedural techniques here are grounded in published work (all applied as pure, cross-platform math):

- **Perceptual colour** — Björn Ottosson, *A perceptual color space for image processing (OKLab)*, 2020. <https://bottosson.github.io/posts/oklab/>
- **Simplex noise** — Ken Perlin, *Improving Noise*, SIGGRAPH 2002; Stefan Gustavson, *Simplex Noise Demystified*, 2005. <https://cgvr.cs.uni-bremen.de/teaching/cg_literatur/simplexnoise.pdf>
- **Flow / fluid noise** — Perlin & Neyret, *Flow Noise*, SIGGRAPH Sketches 2001 (<https://morpho.inrialpes.fr/Publications/2001/PN01/>); Bridson et al., *Curl-Noise for Procedural Fluid Flow*, SIGGRAPH 2007 (<https://www.cs.ubc.ca/~rbridson/docs/bridson-siggraph2007-curlnoise.pdf>); Iñigo Quílez, *Domain warping* (<https://iquilezles.org/articles/warp/>).
- **Fractal lightning** — recursive midpoint displacement + branching (dielectric-breakdown model).
- **Bloom (the Tier-2 opt-in path)** — Jimenez, *Next Generation Post Processing in Call of Duty: AW*, SIGGRAPH 2014 (<https://www.iryoku.com/next-generation-post-processing-in-call-of-duty-advanced-warfare/>); Bjørge, *Bandwidth-Efficient Rendering / Dual Kawase*, SIGGRAPH 2015 (ARM); Green (Valve), *Improved Alpha-Tested Magnification for Vector Textures and Special Effects*, SIGGRAPH 2007 (SDF glow).
