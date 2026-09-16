# KMPMedia for AI coding agents

KMPMedia is designed to be **easy for an LLM to generate correctly**. This page explains why, and shows natural-language prompts next to the KMPMedia code an assistant should produce. For a terse, machine-readable API index, see [`llms.txt`](../llms.txt) at the repo root.

## Why KMPMedia is AI-friendly

1. **It speaks SVG — the one graphics format LLMs generate natively as text.** An assistant can emit an `<svg>` string (including the animated SMIL subset) or point at a generated URL, and KMPMedia renders *and animates* it as a live cross-platform vector — no design tool, no asset pipeline, no rasterization.
2. **The API is declarative, not imperative.** You describe *what* — `displayShape = CIRCLE`, `animations = setOf(SCALE, ROTATE)`, a cue `at(3_000)`, `depth = 0.8f` — via consistently-named `OG*` / `Modifier.ogX` config objects and enums. LLMs are far more reliable emitting a spec than hand-writing frame loops or Canvas math, so generated code compiles and behaves more often, and hallucinates less surface.
3. **One small, consistent, discoverable surface.** Every entry point is `OG…`; one `commonMain` API drives both Android and iOS. An assistant grounds on one predictable namespace and writes once instead of twice.

> Honest framing: today KMPMedia is **AI-generatable / codegen-friendly** — an agent writes Kotlin that uses the library. It is not yet **AI-integrated** (no MCP server / agent tool-schema / serializable scene spec). Those are on the roadmap.

## Prompt → snippet

Each block is a user request and the KMPMedia code an assistant should generate. All compile as written (imports omitted for brevity — they're in `llms.txt`).

**"Show this photo cropped into a circle."**
```kotlin
OGImageView(
    source = OGImageUrlType("https://…/photo.jpg"),
    modifier = Modifier.size(200.dp),
    displayShape = OGShapeType.CIRCLE,
    contentScale = ContentScale.Crop,   // Compose ContentScale for images
    onEventTriggered = { _, _ -> },     // required param
)
```

**"Make our logo SVG gently pulse and spin."**
```kotlin
OGAnimatedImage(
    source = OGSvgUrlType("https://…/logo.svg"),
    animations = setOf(OGAnimationType.SCALE, OGAnimationType.ROTATE),
    durationMillis = 1500,
)
```

**"Play this clip looping inside a triangle, filling the shape."**
```kotlin
OGAVPlayer(
    action = OGAVPlayerAction.PLAY,
    source = OGVideoUrlType("https://…/clip.mp4"),
    modifier = Modifier.size(300.dp),
    config = OGPlayerConfig(
        displayShape = OGShapeType.TRIANGLE_UP,
        contentScale = OGVideoScale.FILL,   // OGVideoScale for video, NOT Compose ContentScale
        playbackConfig = OGVideoPlaybackConfig(autoStart = true, autoRepeat = true),
    ),
    onError = { },
)
```

**"Pop a caption at 3 seconds and keep a live progress bar."**
```kotlin
val controller = rememberOGAVPlayerController()
var caption by remember { mutableStateOf<String?>(null) }

OGAVPlayer(
    action = OGAVPlayerAction.PLAY,
    source = OGVideoUrlType("https://…/talk.mp4"),
    controller = controller,
    cues = listOf(OGCue.at(3_000) { caption = "Chapter 1" }),
    onProgress = { status -> /* status.progress: 0f..1f */ },
    onError = { },
)
// controller.status.value.{positionMs,durationMs,progress}; controller.seekTo(ms)
```

**"Give me a parallax scene where the ship flies over the far planets."**
```kotlin
OGDepthField(focalDepth = 0.5f, modifier = Modifier.fillMaxSize()) {
    OGDepthObject(depth = 0.2f) { /* far planets — dimmed, blurred, drawn under */ }
    OGDepthObject(depth = 0.9f) { /* ship — sharp, drawn over */ }
}
```

## Common mistakes to avoid (agent guardrails)

- Don't pass a raw `String` as a `source`; wrap it: `OGImageUrlType(...)`, `OGVideoUrlType(...)`, `OGSvgUrlType(...)`.
- Don't use `ContentScale.Crop` on a video's `OGPlayerConfig` — video scale is `OGVideoScale.FILL/FIT`.
- Don't write `OGShapeType.TRIANGLE` — it's `TRIANGLE_UP` / `TRIANGLE_DOWN`.
- Don't forget `OGImageView`'s required `onEventTriggered`, or `OGAVPlayer`'s required `action` and `onError`.
- Prefer these primitives over hand-rolled `Canvas`/`drawScope` — that's the whole point of the library.

## What would make it fully "AI-ready"

A serializable **scene spec** (LLM emits validated JSON/DSL, the library renders it via `OGScene(spec)`), an **MCP server** (generate / validate / preview a scene, search the API), and a published **JSON Schema** so an agent can self-correct before a single line of Kotlin is written. That turns "AI-generatable" into "AI-integrated."
