# Data-defined drawing styles

*New in 1.23.0 — package `com.solidkey.painpoints.style`, zero dependencies.*

A **drawing style is just data.** An `OGStyleSpec` is a JSON **pipeline of `{op, params}`** that
the library compiles into a live `OGStyle` and applies to any outline every frame. So a designer, or
a language model, can author, tweak and share a `.style` pack as plain text with **no code and no
rebuild** — exactly the way [`OGAiVector`](AI_HOOKS.md) turns a model's JSON into a clip shape.

```json
{"name":"pixel sprite",
 "ops":[{"op":"boil","amplitude":0.03,"boilFps":10},
        {"op":"pixelate","resolution":20}]}
```

Hand that string to `OGStyles.decode(...)` and you get a running style.

---

## The pipeline

A spec is an ordered list of ops. Each op names a procedural primitive and carries its (flat,
all-optional) parameters; applying the style runs the outline through the ops **in order**. Params
an op doesn't understand are ignored, and an **unknown op is dropped** (so a pack authored against a
newer library degrades gracefully on an older one).

### Built-in ops

| `op` | aliases | what it does | params (defaults) |
|------|---------|--------------|-------------------|
| `boil` | — | the living, hand-drawn "wiggly line": jitters every vertex to a fresh pseudo-random offset `boilFps` times a second | `amplitude` (`0.02`), `boilFps` (`8`), `smooth` (`true`), `seed` (`0`) |
| `quantize` | `step`, `stepped` | snaps vertices to a `grid`×`grid` lattice — a stepped, stop-motion line | `grid` **or** `resolution` (`16`) |
| `subdivide` | `resample` | inserts `detail` points along each edge so a low-vertex outline has enough to move — put it **before** a displacement op | `detail` (`4`) |
| `smooth` | `round` | resamples the outline through a centripetal Catmull-Rom spline — a clean, rounded edge that still passes through every vertex | `strength` (`1`, `0..1`), `detail` (`6`) |
| `roughen` | `rough`, `sketch` | a **static** hand-drawn / sketch edge: subdivides, then offsets each vertex by fixed hashed noise (pair with `boil` to also wobble) | `amplitude` (`0.03`), `detail` (`3`), `seed` (`0`) |
| `wave` | `ripple` | a travelling sinusoidal ripple along the outline normal — smooth + directional (unlike boil's jitter) | `amplitude` (`0.02`), `waves` (`3`), `speed` (`1`) |
| `pixelate` | `pixel` | **terminal** fill op: rasterizes the outline into a `resolution`×`resolution` grid and returns the filled cells — a low-res pixel / mosaic sprite | `resolution` **or** `grid` (`20`) |

Notes:

- **`amplitude` is in the outline's own units.** On a normalized `0..1` outline, `0.03` means "up to
  3% of the box". `~0.01–0.06` is a good range; `boilFps` `~6–12` reads as a lively boil.
- **`pixelate` is terminal** — any op placed after it is ignored, so "pixelate last" is enforced, not
  just advised. The classic combo is `boil` → `pixelate` (a shimmering 8-bit sprite).
- Op names are **case-insensitive**; an op with an out-of-range `grid`/`resolution` (`< 1`) is dropped.

---

## Applying a style

Decode **once** (per edit, not per frame), then `apply` every frame with a monotonically rising
`timeMs`:

```kotlin
import com.solidkey.painpoints.style.OGStyles

val style = OGStyles.decode(
    """{"name":"pixel sprite","ops":[
         {"op":"boil","amplitude":0.03,"boilFps":10},
         {"op":"pixelate","resolution":20}
       ]}""",
)   // OGStyles.decodeOrNull(...) returns null instead of throwing on bad JSON

val frame = style.apply(outline, timeMs)   // outline: List<OGPoint> (0..1 space); timeMs: Long
```

`apply` returns an **`OGStyleFrame`**. Check `isPixelated` to know which half to draw:

```kotlin
if (frame.isPixelated) {
    // draw a `frame.pixelSize`-sided square at each centre in `frame.pixels`
    for (c in frame.pixels) drawRect(centre = c, side = frame.pixelSize)
} else {
    // stroke or fill `frame.outline` (same vertex count/order as the input)
}
```

### A living edge on a media clip

The `boil` op has a one-liner that keeps the result an `OGPolygonShape`, so it drops straight into any
existing `clipShape` slot — a photo, GIF, video or live-camera clip gets a living, hand-cut edge with
**no other change**. Recreate it each frame from a rising time (exactly like `OGMorphShape`):

```kotlin
import com.solidkey.painpoints.style.OGBoil
import com.solidkey.painpoints.style.boiled
import com.solidkey.painpoints.shape.OGPolygonShape

OGImageView(
    source = OGImageUrlType("https://example.com/photo.jpg"),
    clipShape = OGPolygonShape(outline).boiled(OGBoil(amplitude = 0.02f), timeMs),
    onEventTriggered = { _, _ -> },
    onError = { /* ... */ },
)
```

---

## Styled vector text

The style ops transform an *outline* — and **text can be an outline too**. `OGStyledText`
(`com.solidkey.painpoints.text`) vectorizes a string into glyph contours and runs your `OGStyle`
over each one, so the **letters themselves** are hand-inked and, for a time-varying style like `boil`
or `wave`, alive. Ideal for a hand-drawn word-game tile or an animated title.

```kotlin
import com.solidkey.painpoints.text.OGStyledText
import com.solidkey.painpoints.style.OGStyles

val handDrawn = OGStyles.decode(
    """{"ops":[{"op":"roughen","amplitude":0.02,"detail":4},{"op":"boil","amplitude":0.012,"boilFps":7}]}""",
)

OGStyledText(text = "WordStorm", style = handDrawn, fontSize = 40.sp, color = Color(0xFF7B2FF7))
```

How it stays fast and correct:

- **Vectorized once, styled per frame.** The expensive glyph→outline step (`ogVectorizeText`, backed by
  Android `Paint.getTextPath` + `PathMeasure` and iOS Skia `Font.getPath`) is cached per
  `text` / `font` / `quality`; each frame only re-runs the cheap per-contour styling — it holds 60fps.
- **Em units.** Contours come back in em space (`1.0` = one em), so a style `amplitude` of `0.02` means
  the same "2% of an em" at any `fontSize`.
- **Cross-platform caveat.** Android and iOS ship *different* default fonts, so the glyphs are
  *look-equivalent*, not pixel-identical (as with any native text). Pass `OGTextFont(family = …)` for a
  closer match, and `bold` / `italic` as needed.
- `ogVectorizeText(text, font, quality)` is also public if you want the raw contours (an `OGTextOutline`)
  to style or draw yourself; a terminal `pixelate` op is ignored by `OGStyledText` (it fills the outline).

---

## How do I add a new style?

There are two levels, depending on whether the ops you need already exist.

### 1. Author a new pack from existing ops — **no code, no rebuild**

This is the everyday case and the whole point of the layer. Write a JSON object and compose the
built-in ops in the order you want (put `pixelate` last if you use it):

```json
{"name":"nervous sketch",
 "ops":[{"op":"boil","amplitude":0.015,"boilFps":6,"smooth":false},
        {"op":"quantize","grid":28}]}
```

A **hand-drawn / handwriting** look is `roughen` (a fixed sketch edge) optionally kept alive with a
gentle `boil` — great for a word-game tile edge or any outline you want to feel hand-inked:

```json
{"name":"hand-drawn",
 "ops":[{"op":"roughen","amplitude":0.02,"detail":4},
        {"op":"boil","amplitude":0.012,"boilFps":7}]}
```

Feed it to `OGStyles.decode(json)` and apply it as above. That's it — a `.style` pack is just this
string, so you can store it, ship it in a resource, let a user paste it into a text field (the demo
does), or round-trip it with `OGStyles.encode(spec)`.

**Let a model write it for you.** `OGStyles.stylePrompt(instruction)` returns a ready-to-send prompt
that constrains any LLM to emit exactly this schema; pass the reply back to `decode`:

```kotlin
val prompt = OGStyles.stylePrompt("a trembling, hand-inked comic outline")
val style  = OGStyles.decodeOrNull(myLlm.complete(prompt))   // your model, your call
```

No AI SDK or networking is pulled into the library — you own the model and the call, same as
`OGAiVector`.

### 2. Add a brand-new op — a small **library** change

The op *vocabulary* lives in the library, so adding a genuinely new transform (say a `dash` or a
`halftone`) is a code contribution, not a data one. It's deliberately small and local:

1. Write the pure transform as a function (model it on the geometry ops in
   [`OGStyleOps.kt`](../library/src/commonMain/kotlin/com/solidkey/painpoints/style/OGStyleOps.kt), or
   `quantizeVertices` / `pixelateFill` / `OGBoil` in
   [`OGBoil.kt`](../library/src/commonMain/kotlin/com/solidkey/painpoints/style/OGBoil.kt)):
   `List<OGPoint> -> List<OGPoint>` (or a terminal fill like `pixelate`).
2. Add any new parameter field to `OGStyleOp` (keep it nullable with a default so old packs still
   decode).
3. Register the op name in `OGStyle`'s compile step — add a `Step` and a branch to the `when (o.op…)`
   in [`OGStyleSpec.kt`](../library/src/commonMain/kotlin/com/solidkey/painpoints/style/OGStyleSpec.kt),
   so it's resolved once up front (not per frame).
4. Mention it in `OGStyles.stylePrompt` so models can emit it.

Because unknown ops are dropped, a pack that uses your new op simply no-ops on a library that predates
it — forward- and backward-compatible by construction.

---

## Performance

The spec is **compiled once** — op names resolved and `OGBoil` instances built up front — so
`apply` does no per-frame name dispatch or parameter boxing. Each frame is pure integer-hash
offset / round / lerp per vertex, allocating only each op's output list: it holds **60fps**, the same
gate the rest of the library clears. `boil` uses an **integer hash, not an RNG**, so it's fully
deterministic — a live preview, an export, and Android vs iOS all agree frame-for-frame.

## See it running

The demo's **🖊️ Boiling lines** screen dogfoods all of this. Scroll to the **"Style from JSON"**
section: tap a preset chip (Boil / Stepped / Pixel sprite) or edit the JSON field directly and the
preview re-decodes live (invalid JSON shows a warning instead of crashing). The same screen also shows
`OGBoil` as a stroked wiggly line, as a `boiled()` `clipShape` fill, and a `boil → pixelate` sprite
with a resolution slider. Prebuilt APKs are on the demo's
[Releases page](https://github.com/SolidKeyAB/kmpmedia-demo/releases).

## Verification

28 style unit tests green on **JVM and iOS** (decode/encode round-trips, op compilation, boil
determinism, the pixelate/quantize maths, graceful handling of unknown ops and bad JSON).
