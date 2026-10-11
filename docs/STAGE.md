# OGStage — one JSON, a whole movie (`OGStage`)

The **capstone**: a single serializable document that stacks every KMPMedia pack onto one timeline — a
**movie** a language model (or a person) authors as one JSON object, a single player plays at 60fps and
scrubs, and the library renders off-screen to a shareable **GIF / MP4** on device. One generator emits
it; one player plays it; the same code runs on Android & iOS.

It is a **thin orchestration** over the shipped primitives — the compositor's keyframe tracks
([COMPOSITOR.md](COMPOSITOR.md)), the model-authorable scene ([AI_GUIDE.md](AI_GUIDE.md)), particles
([PARTICLES.md](PARTICLES.md)), the action-FX packs ([SLASH.md](SLASH.md)), looks
([LOOKS.md](LOOKS.md)) and styled text ([STYLES.md](STYLES.md)) — so it adds no new per-frame cost and
holds the parse-once / 60fps budget.

- **Package:** `com.solidkey.painpoints.stage`
- **Document:** `OGStage` → `OGStageLayer` → `OGStageContent`
- **Player:** `OGStageView`
- **Codec + prompt + builders:** `OGStages`
- **Export:** `OGStage.exportGif()` / `OGStage.exportMp4()` / `OGStage.renderFrame(t)`

## Quick start

```kotlin
import com.solidkey.painpoints.stage.*
import com.solidkey.painpoints.particle.OGParticles
import com.solidkey.painpoints.look.OGBlendMode

val movie = OGStage.of(
    OGStages.scene("backdrop", mySceneSpec),                    // a day→night vector scene
    OGStages.particles("petals", OGParticles.PETALS, blend = OGBlendMode.PLUS),
    OGStages.text("title", "HANAMI", size = 0.2f, color = "#FFF1F4"),  // a styled vector title
    width = 1600, height = 900, durationMs = 6500L,
)

OGStageView(movie, modifier = Modifier.fillMaxSize())           // play it live, 60fps
```

Pass `positionMs` to `OGStageView` to turn it into a scrubber (the scene + transform channels are
closed-form t-addressable).

## Layer kinds (`OGStageContent`)

Each `OGStageLayer` carries one `OGStageContent`, is transformed by five keyframe tracks
(`x` / `y` / `scale` / `rotationDeg` / `opacity`), graded by an optional `OGLookSpec`, composited with
an `OGBlendMode`, and gated by a `[startMs, endMs]` window.

| `"type"` | content | pack |
|----------|---------|------|
| `solid` | a flat colour fill | — |
| `scene` | a model-authorable vector scene (shapes, gradients, its own day→night motion) | [AI_GUIDE.md](AI_GUIDE.md) |
| `particles` | a data-defined emitter (petals, embers, rain, sparks …) | [PARTICLES.md](PARTICLES.md) |
| `slash` | a breathing-slash ribbon (water / flame / thunder) | [SLASH.md](SLASH.md) |
| `lightning` | a forked lightning bolt | [SLASH.md](SLASH.md) |
| `speedlines` | manga speed / impact lines | [SLASH.md](SLASH.md) |
| `text` | **styled vector text** — a title / caption / lower-third | [STYLES.md](STYLES.md) |

### The text layer

`OGStageContent.Text` vectorizes its glyphs to outlines and fills them, centred in the layer box
(position / scale / rotate it with the layer's transform tracks). `size` is the em height as a fraction
of the **stage height**, so it stays resolution-independent. An optional `OGStyleSpec` runs over every
glyph outline — `roughen` for a hand-drawn edge, `boil` / `wave` to keep the letters alive:

```kotlin
import com.solidkey.painpoints.style.OGStyleSpec
import com.solidkey.painpoints.style.OGStyleOp

OGStages.text(
    id = "title",
    text = "STORM",
    size = 0.26f,                 // 26% of the movie height per em
    color = "#DCE8FF",
    bold = true,
    style = OGStyleSpec(ops = listOf(OGStyleOp(op = "roughen", amplitude = 0.02f, detail = 4))),
    opacity = OGStages.track(1000L to 0f, 1200L to 1f),   // fade in
)
```

The live player and the off-screen exporter draw the text through one shared per-frame function, so an
exported title matches the preview — the same single-code-path discipline the action-FX layers use.

## Describe → playable movie

The whole document is model-authorable. `OGStages.stagePrompt(instruction)` returns the exact contract
a language model needs to emit one movie as JSON; hand the reply to `OGStages.decode`:

```kotlin
val prompt = OGStages.stagePrompt("a stormy night over the sea with a forked lightning bolt and a STORM title")
val reply  = myModel.complete(prompt)   // your model call (any provider; the library is network-free)
val movie  = OGStages.decode(reply)     // tolerant of prose / markdown fences
OGStageView(movie, modifier = Modifier.fillMaxSize())
```

The codec is deliberately **lenient about real model output**, so a capable model's first reply tends to
just work:

- **A transform track may be a bare number.** `"opacity": 0.8` decodes as a constant track — a model
  need not write the verbose `{ "default": 0.8 }` object.
- **Enums decode case-insensitively.** The lowercase `blend` (`"plus"`, `"screen"`) and `easing` names
  the prompt asks for decode even though the constants are `PLUS` / `EASE_OUT`.
- **Unknown keys and surrounding prose are ignored** (`OGAiVector`'s shared config).

Encoding always emits the canonical form, so the wire format and round-trips are unchanged. The demo's
**🎞️ OGStage** screen ships three movies (STORM / HANAMI / RUSH) authored this way by a model from a
single one-line prompt, decoded and played unchanged — and the library pins them as regression tests
(`OGStageShowcaseTest`).

## Export (generate → play → export)

```kotlin
val bytes: ByteArray = movie.copy(width = 640, height = 360, fps = 12).exportGif()   // off the main thread
```

`exportGif()` / `exportMp4()` render the movie frame-by-frame off-screen (the same `CanvasDrawScope`
path the player uses) and encode it with the compositor's pure-Kotlin `OGGifEncoder` (identical bytes on
Android & iOS) or the OS `OGMp4Encoder`. **Every layer kind bakes in** — scene, particles in their real
silhouette, the action-FX through the same per-frame functions the views use, and styled text. Export is
a one-shot, allocation-heavy snapshot: run it off the main thread and prefer a down-fps / down-sized
`copy()`. The README's capstone clip was captured this way — the iOS pair rendered **headless** on the
simulator through the very same `exportGif()`.

## Two clocks, deliberately

A layer's **transform** tracks read **absolute movie time** (authoring a pan / zoom is identical to a
composition), while a layer's **content** reads **local time** (`t − startMs`) — so a scene's day→night
begins when its layer enters, not at `t = 0`. `OGStage.resolveAt(t)` flattens the whole stage to plain
values at any instant (closed-form for the scene + transform channels), so it is directly unit-testable
and identical on both platforms. A particle layer runs its own seeded simulation (live), and the
exporter re-simulates it deterministically.

## Interactivity — tap a layer, it reacts *(v1.44.0)*

A movie you watch becomes one you touch. Any `OGStageLayer` can carry an optional **`onTap`** trigger (`OGStageTrigger`) — plain serializable data, so it round-trips through the codec and a model can author it. `null` (the default) = an inert, watch-only layer, so every existing stage is unchanged.

```kotlin
OGStages.text(
    id = "title", text = "TAP ME", bold = true,
    onTap = OGStages.tap(OGTapAction.BOUNCE),   // or .seekTo(2000L) / .playPause()
)
// any layer kind: OGStages.tappable(someLayer, OGStages.tap(OGTapAction.SPIN))
```

`OGTapAction`:

| action | what a tap does |
|---|---|
| `BOUNCE` | a spring scale-pop that overshoots and settles |
| `SHAKE` | a damped horizontal wobble |
| `SPIN` | one eased 360° turn back to rest |
| `SEEK` | jump the stage clock to `seekMs` (a chapter jump) |
| `PLAY_PAUSE` | toggle playback |

The visual reactions (`BOUNCE` / `SHAKE` / `SPIN`) are **closed-form and pure** — `reactionTransformAt(action, strength, durationMs, elapsedMs)` returns the transient transform applied *on top of* the layer's resolved transform, evaluated with the shipped dynamics spring (`OGSpring.valueAt`), so a tap reacts with the same deterministic, platform-identical second-order feel the dynamics pack ships. They work even while the stage is paused or scrubbed (they're independent of the clock); `SEEK` / `PLAY_PAUSE` drive the player's own clock and so apply only when `OGStageView` owns it (not when it is controlled by an external `positionMs`).

**Cost & semantics.** `OGStageView` drives reactions off the frame clock but **only while one is settling** — a still, tappable stage adds no frame loop and reports `isLive = false` (it renders one frame and waits for a finger). Hit-testing is the layer **box**, so taps fall through a non-interactive layer to an interactive one behind it; to make distinct things tappable, give each its own on-screen zone with a sub-1 `scale` and an `x`/`y` offset. The off-screen exporter is untouched — an exported GIF / MP4 bakes the base timeline (taps are a live-player concern).

## See also

- [COMPOSITOR.md](COMPOSITOR.md) — the keyframe tracks + GIF/MP4 encoders OGStage reuses
- [AI_GUIDE.md](AI_GUIDE.md) — the model-authorable scene grammar a `scene` layer carries
- [PARTICLES.md](PARTICLES.md) · [SLASH.md](SLASH.md) · [LOOKS.md](LOOKS.md) · [STYLES.md](STYLES.md) — the packs behind the other layer kinds
