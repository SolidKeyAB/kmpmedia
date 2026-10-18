# OGControl — the whole hand-drawn button in one call

`com.solidkey.painpoints.control`

Apps kept re-assembling the same button out of library pieces: a rounded fill, a
`drawWithCache` roughened ink edge, an `ogButton` tap + press, a styled vector label, and a
seasonal garnish Canvas. **`OGControl`** bundles all of it behind one composable plus a
serializable **`OGControlSpec`** skin, so a control is *configured*, not hand-built.

Pure `commonMain`, no new dependency, identical on Android & iOS, and it clears the same 60fps
gate as the rest of the library (the vectorized label and the roughened edge are cached — only a
*moving* garnish redraws).

## At a glance

```kotlin
OGControl(
    label = "Play word",
    backgroundColor = Accent,
    contentColor = Color.White,
    enabled = canPlay,
    seed = 1,                       // varies the sketch + garnish per button
    spec = OGControls.preset("slime")!!,   // or OGControlSpec()
    modifier = Modifier.weight(1f),
) { controller.submit() }
```

What it draws, in order:

1. a **fill** — a flat `backgroundColor`, an explicit gradient `fill: Brush`, or a data-defined
   `OGControlSpec.fill` (`OGFillSpec`); brush wins over spec wins over the flat colour;
2. a cached hand-drawn **ink outline** (`OGControlSpec.outline`) — the library's own
   `roughenVertices` over a rounded-rect, sketched just inside the fill;
3. a tap + **pressed-state** via `ogButton`, the effect named by `OGControlSpec.pressEffect`
   (`scale` | `dim` | `brutalist` | `none`);
4. an optional **seasonal garnish** (`OGControlSpec.garnish`) — or your own `garnish` content slot;
5. a centred **styled vector label** (`OGStyledText`), inked by `OGControlSpec.labelStyle` (or the
   `labelStyle` argument; the default is a subtle hand-drawn roughen).

`enabled = false` dims the fill to `disabledAlpha`, halves the label, fades the ink, hides the
garnish, and makes the tap a **consumed no-op** — a disabled control never leaks taps to content
behind it.

## The skin is data

`OGControlSpec` / `OGControlOutline` / `OGGarnishSpec` are `@Serializable`. The *live* colours and
the click stay runtime arguments on the composable; everything else — geometry, the ink edge, an
optional gradient, a garnish — can live in a `.control` JSON pack, round-tripped by `OGControls`
(the same lenient `OGAiVector.json` as `OGLooks` / `OGStyles`, tolerant of code fences and unknown
keys). So a control skin can be authored by a model exactly like a look or a style:

```kotlin
val spec = OGControls.decodeSpec(model.complete(OGControls.controlPrompt("a tall slimy button")))
```

### Garnishes

A garnish is "goo oozing off an edge", generalized into one spec driven by the library's
`waveVertices` — no hand-rolled Canvas per app:

```kotlin
OGGarnishSpec(edge = "top", color = "#7CB342", deepColor = "#558B2F", drips = 2)   // Halloween slime
```

Ready-made: `OGControls.garnishes["slime"]`, `OGControls.garnishes["snow"]`, and the
`"plain"` / `"sketch"` / `"slime"` / `"snow"` entries in `OGControls.presets`.

## Pure geometry (testable without Compose)

`ogControlOutline(...)` (the roughened rounded-rect) and `ogGarnishRing(...)` (the drippy goo ring
at a time `t`) are Compose-free, deterministic from `seed`, and unit-tested directly — the
composable only *draws* them.

## Dogfood

WordStorm's action button was a `Box` + `background` + a `drawWithCache` `roughRectPath` + `ogButton`
+ a bespoke `SlimeTrim` Canvas (~90 lines). It is now a thin adapter over `OGControl` — the library
gap this feature was built to close.
