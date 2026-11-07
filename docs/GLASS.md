# Glass — a look-behind-glass lens (`OGGlass`)

A round, **draggable glass lens** laid over any content: a **magnifier** (`zoom > 1`), a **minifier**
(`zoom < 1`) and/or **coloured glass** (`tint`). Like the rest of the library's packs it is plain,
serializable data a designer or a language model can author — decode + presets on [`OGGlass`], draw
with [`OGGlass`] (the composable).

It is as much an **accessibility win** as an effect: drop it over a board, a photo or a word and older
eyes get a movable magnifier; flip `zoom` under `1` for a fit-more minifier, or `tint` it for a
colour-filter viewing glass.

- **Package:** `com.solidkey.painpoints.glass`
- **Spec:** `OGGlassSpec`
- **Composable:** `OGGlass(spec, …) { content }`
- **Codec + presets:** `OGGlass`

## How it works (60fps, no shader)

The content under the lens is recorded **once** into a `GraphicsLayer` (the same trick as
`Modifier.ogGlow`); the lens then re-draws that layer **scaled about its centre**, clipped to a
circle, so whatever sits beneath appears magnified (or shrunk). A translucent `tint`, a soft rim and a
specular highlight sell the "piece of glass" look. No per-pixel work and no runtime shader (the library
floors those out), so it's cheap and cross-platform.

## Quick start — a ready-made preset

```kotlin
import com.solidkey.painpoints.glass.OGGlass

// Wrap whatever should be magnifiable; the lens starts in the centre.
OGGlass(OGGlass.MAGNIFIER, Modifier.fillMaxSize(), onRemove = { showLens = false }) {
    YourContent()
}
```

Built-in presets (on `OGGlass`): **`MAGNIFIER`**, **`READER`**, **`MINIFIER`**, **`AMBER`**, **`ROSE`**
— or `OGGlass.preset("reader")` by name.

## The composable

`OGGlass` is the **outermost wrapper** around whatever should be magnifiable. The lens is drawn **on
top** of the content but its body is **pointer-transparent**, so you keep interacting with the content
underneath (place a word, tap a cell) while the glass is parked over an area. You move it only by
dragging its **grip handle** (the four-arrow glyph on the lower-right of the rim); the optional **✕**
(upper-right, wired to `onRemove`) dismisses it. `enabled = false` hides the glass entirely.

```kotlin
@Composable
fun OGGlass(
    spec: OGGlassSpec,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onRemove: (() -> Unit)? = null,
    content: @Composable () -> Unit,
)
```

The lens is clamped to stay fully on screen, and starts centred until the first drag.

## The spec

```kotlin
import com.solidkey.painpoints.glass.OGGlassSpec

val amber = OGGlassSpec(
    zoom = 1.8f,                 // magnify 1.8×
    radius = 0.24f,              // lens radius = 24% of the shorter side
    tint = "#FFB300",            // warm amber glass
    tintStrength = 0.35f,
    rimColor = "#FFE08A",
    highlight = 0.3f,
)
```

| field | default | meaning |
|---|---|---|
| `name` | `null` | optional label |
| `zoom` | `2f` | magnification under the glass: `> 1` magnifies, `< 1` minifies, `1` = a plain (tinted) pane. Clamped `0.2..6` |
| `radius` | `0.22f` | lens radius as a fraction of the **shorter side** of the view. Clamped `0.03..1` |
| `tint` | `"#00000000"` | glass tint, any SVG colour string; fully transparent = a clear magnifier |
| `tintStrength` | `0.25f` | how strongly `tint` is laid over the magnified content (`0..1`) |
| `rimColor` | `"#FFFFFF"` | the glass edge / frame colour |
| `rimWidth` | `0.05f` | rim thickness as a fraction of the lens radius (`0..0.5`) |
| `rimAlpha` | `0.8f` | rim opacity (`0..1`) |
| `highlight` | `0.3f` | specular sheen strength across the top-left of the glass (`0..1`, `0` = none) |

`spec.sanitized()` clamps every field into its sane range, so lenient model / designer input never
breaks a frame.

## From JSON / from a model

```kotlin
import com.solidkey.painpoints.glass.OGGlass

// A designer- or model-authored pack (tolerant of code fences / stray prose):
val spec = OGGlass.decodeSpec("""{"name":"magnifier","zoom":2.2,"radius":0.22,"highlight":0.3}""")

// Hand a model the exact contract, then decode its reply:
val prompt = OGGlass.glassPrompt("a warm amber reading magnifier that cuts glare")
val fromModel = OGGlass.decodeSpecOrNull(myLlm.complete(prompt)) ?: OGGlass.MAGNIFIER
```

`OGGlass.encode(spec)` goes the other way; `decodeSpecOrNull` returns `null` instead of throwing on
malformed input (the same lenient JSON as the rest of the AI layer — no network, no AI SDK).

## Honest limits

- One circular lens; no barrel / fisheye distortion inside the glass (a plain uniform scale).
- The content is captured into one `GraphicsLayer`, so the lens magnifies composed graphics, shapes
  and `Canvas` — not a live camera / video surface (the same limit the glow and soft masks have).
