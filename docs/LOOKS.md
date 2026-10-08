# Live looks — data-defined colour grades

`com.solidkey.painpoints.look` adds a **colour grade as data**. A look is a handful of perceptual
knobs that compile **once** into a single Compose `ColorFilter` (a 4×5 `ColorMatrix`) and apply to any
graphic every frame. Like [`OGAiVector`](AI_HOOKS.md) for shapes or [`OGStyleSpec`](STYLES.md) for
outlines, a look is plain JSON — a designer or a language model can author, tweak and share a `.look`
pack with no code and no rebuild.

The whole grade is **one GPU colour op**: the matrix/filter are cached per spec (zero per-frame
allocation), it runs at 60fps, and because it is pure maths (no platform blur or shader) it is
**pixel-identical on Android & iOS**.

## The spec

```kotlin
val look = OGLookSpec(
    brightness = 0f,   // -1..1  additive exposure            (0 = unchanged)
    contrast   = 1f,   //  0..2  scale around mid-grey        (1 = unchanged)
    saturation = 1f,   //  0..2  0 = greyscale, >1 = richer    (1 = unchanged)
    temperature= 0f,   // -1..1  + warmer / - cooler           (0 = unchanged)
    tint       = 0f,   // -1..1  + magenta / - green           (0 = unchanged)
    hue        = 0f,   // -180..180  degrees of hue rotation   (0 = unchanged)
)
```

Every field is neutral by default, so the default `OGLookSpec()` is a no-op pass-through.

## Grade any graphic — `Modifier.ogLook`

```kotlin
Image(painter, null, Modifier.ogLook(OGLooks.presets.getValue("warm")))

// or build your own, and optionally pick a blend mode for how it sits over what's behind it:
Box(Modifier.ogLook(OGLookSpec(saturation = 0f, contrast = 1.4f)))        // noir
Box(Modifier.ogLook(look, blend = BlendMode.Screen))
```

`Modifier.ogLook` captures the content into a `GraphicsLayer` and redraws it through the look's cached
`ColorFilter`, so it works on photos, GIFs, SVGs, lasso-cut cut-outs and `Canvas` drawings with no
per-content code. An identity look short-circuits to a plain draw.

## Looks on the compositor

An `OGCompositionLayer` can carry a `look` and a `blend`, applied in the shared render path — so a
layer is graded and composited identically in the live `OGCompositionView` preview and the GIF/MP4
export:

```kotlin
OGCompositionLayer(
    id = "hero", content = OGLayerContent.Image(photo), width = 1080f, height = 1080f,
    look = OGLooks.presets.getValue("faded"),
    blend = OGBlendMode.SCREEN,
)
```

`OGBlendMode` is the serializable set `NORMAL / MULTIPLY / SCREEN / OVERLAY / LIGHTEN / DARKEN / PLUS`.
`NORMAL` keeps the compositor's byte-identical export guarantee; the others composite through the
platform's own Skia (visually consistent across platforms, but not part of the byte-identical
guarantee).

## Looks as data — codec, presets, prompt

```kotlin
val json = OGLooks.encode(look)                 // -> {"name":"...","temperature":0.3,...}
val spec = OGLooks.decodeSpec(modelReply)       // fence/prose-tolerant, like OGAiVector
val filter = OGLooks.decode(modelReply)          // straight to a ColorFilter

OGLooks.presets                                  // warm / cool / noir / faded / vivid
OGLooks.lookPrompt("a warm, faded 70s film look") // constrain a model to emit a valid .look
```

## Scope & honest limits

- Grades **drawn graphics** (photos / GIFs / shapes / `Canvas`). The live **video** surface is a
  separate platform view, so a `ColorFilter` over it is not reliable — the same limit as the soft-mask
  and blur modifiers.
- True per-pixel **vibrance** (saturation weighted by how saturated a pixel already is) and **3-D
  LUTs** are intentionally out of this cut: neither is expressible as one linear colour matrix, so
  including them would break the single-op / frame-identical guarantee. Use `saturation` for the
  linear version.
