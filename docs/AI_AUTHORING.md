# AI authoring — describe → movie

KMPMedia turns a plain sentence into a real, playable [`OGStage`](../library/src/commonMain/kotlin/com/solidkey/painpoints/stage/OGStage.kt)
movie. This is the one thing no other Compose-Multiplatform animation library does: the movie
*format* is a model-writable JSON document, so a language model can author the whole thing.

The library stays **network-free**. It gives you:

- `OGStages.stagePrompt(instruction)` — the exact prompt that constrains a model to emit a valid stage.
- `OGStages.decode(text)` — a tolerant decoder (strips markdown fences / prose, lower-cases enums,
  reads a bare number as a constant track) that turns a raw model reply into a live `OGStage`.
- `OGStageAuthor` / `OGStageModel` / `OGStageAuthoring` — the seam that ties it together.
- `OGOfflineStageAuthor` — a deterministic, no-key director so the loop always works offline.

```
instruction ──▶ OGStages.stagePrompt ──▶ [your model call] ──▶ OGStages.decode ──▶ OGStage ──▶ OGStageView / exportGif
                         │                                            ▲
                         └──────────── OGStageAuthoring.modelAuthor ──┘
```

## No key, right now — the offline director

```kotlin
val author = OGOfflineStageAuthor()
val stage = author.direct("a stormy night over the sea")   // synchronous, deterministic
// OGStageView(stage) to play it, or stage.exportGif() to share it.
```

`OGOfflineStageAuthor` reads the words for mood + subject and composes a backdrop scene, atmosphere
particles, an optional action-FX beat and a styled title — no model, no network. Same words → same
movie. It is the honest floor of the loop.

## Bring your own key — a real model writes the movie

The library never makes a network call; **your app does**. Implement the one-method `OGStageModel`
seam with your LLM, then wrap it with `OGStageAuthoring`. Because the app owns the call, you own the
key and the bill.

`OGStageAuthoring.resilient(model)` tries your model first and falls back to the offline director if
the call fails or the reply doesn't decode — so the user always gets a movie:

```kotlin
val author = OGStageAuthoring.resilient(myClaudeModel)  // falls back to OGOfflineStageAuthor()
val stage = author.author("a neon city speed rush")      // suspend
```

### Reference `OGStageModel` — Anthropic Messages API (ktor, multiplatform)

This is a cross-platform (Android + iOS) implementation using a raw HTTP call, so it compiles
everywhere Compose Multiplatform does. Default model: `claude-opus-4-8`. **Never hard-code the key —
read it from a secret store / env / build config.**

```kotlin
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.*
import com.solidkey.painpoints.stage.OGStageModel

class ClaudeStageModel(
    private val apiKey: String,                 // injected, never hard-coded
    private val model: String = "claude-opus-4-8",
    private val http: HttpClient = HttpClient(),
) : OGStageModel {

    override suspend fun complete(prompt: String): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 16000)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
        }
        val raw = http.post("https://api.anthropic.com/v1/messages") {
            headers {
                append("x-api-key", apiKey)
                append("anthropic-version", "2023-06-01")
            }
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }.bodyAsText()

        // Pull the model's text out of the response; OGStages.decode tolerates any prose around the JSON.
        val json = Json.parseToJsonElement(raw).jsonObject
        return json["content"]?.jsonArray
            ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
            ?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: error("no text block in model reply")
    }
}
```

Wire it up:

```kotlin
val model = ClaudeStageModel(apiKey = BuildConfig.ANTHROPIC_API_KEY)
val author = OGStageAuthoring.resilient(model)      // model first, offline fallback
val stage = author.author("cherry blossoms over pink hills at sunset")
```

Notes:
- The demo app's **Describe → movie** screen shows the whole loop running on the offline director
  (no key), and exposes the exact `OGStages.stagePrompt(...)` string you would send a model.
- For very long replies you can switch to the streaming endpoint; a single stage is small, so a
  non-streaming call with `max_tokens = 16000` is fine.
- Any model works, the format is provider-agnostic. `OGStageModel` is just "prompt in, text out".

## On a local / on-device model, too

Nothing in the loop assumes the cloud. `OGStageModel.complete(prompt): String` doesn't care whether
the text came from Anthropic, a model running on the phone's NPU, or a local server on your laptop, so
an on-device LLM plugs in exactly like the Claude example above. `OGStageAuthoring.resilient(...)`
matters even more here: small quantised models emit malformed JSON more often, and a reply that
doesn't decode into a real movie falls back to `OGOfflineStageAuthor`, so the user still gets a movie.

The clean Kotlin Multiplatform shape is a per-platform factory, since the actual inference call is
platform code:

```kotlin
// commonMain
expect fun localStageModel(): OGStageModel

// use it the same way as any other model:
val author = OGStageAuthoring.resilient(localStageModel())   // on-device first, offline fallback
val stage  = author.author("a stormy night over the sea")
```

### Android — MediaPipe LLM Inference (Gemma, on-device)

Runs a Gemma / Phi / StableLM checkpoint fully on the device's GPU/NPU, no network. Add
`com.google.mediapipe:tasks-genai` and push a model file to the device. `generateResponse` blocks, so
hop off the main thread:

```kotlin
// androidMain
import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.solidkey.painpoints.stage.OGStageModel

class MediaPipeStageModel(context: Context, modelPath: String) : OGStageModel {
    private val llm = LlmInference.createFromOptions(
        context,
        LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)        // e.g. a Gemma .task/.bin on the device
            .setMaxTokens(4096)
            .setTemperature(0.7f)
            .setTopK(40)
            .build(),
    )

    override suspend fun complete(prompt: String): String =
        withContext(Dispatchers.Default) { llm.generateResponse(prompt) }  // blocking call, off-main
}
```

### Any platform — a local server (Ollama) over HTTP

The quickest way to prototype with a real local model: run Ollama on your machine and point the same
cross-platform ktor client at it. Ask it for strict JSON with `format: "json"`.

```kotlin
// commonMain (ktor, so it compiles on Android + iOS)
class OllamaStageModel(
    private val baseUrl: String = "http://10.0.2.2:11434", // host's Ollama from the Android emulator
    private val model: String = "llama3.1",
    private val http: HttpClient = HttpClient(),
) : OGStageModel {
    override suspend fun complete(prompt: String): String {
        val body = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("stream", false)
            put("format", "json")                       // constrain the reply to valid JSON
            putJsonObject("options") { put("temperature", 0.7) }
        }
        val raw = http.post("$baseUrl/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }.bodyAsText()
        return Json.parseToJsonElement(raw).jsonObject["response"]
            ?.jsonPrimitive?.content ?: error("no response field from Ollama")
    }
}
```

Host address per target: the Android **emulator** reaches the host's Ollama at `10.0.2.2`; the iOS
**simulator** shares the host network so `127.0.0.1` / `localhost` works; a real device needs the
host's LAN IP (and `OLLAMA_HOST=0.0.0.0` so the server listens beyond loopback).

### iOS — Apple's on-device model

On iOS you can back the `expect` factory with Apple's on-device **Foundation Models**
(`LanguageModelSession`) through a small Swift shim exposed to Kotlin/Native via cinterop, or with an
**MLC-LLM** / **llama.cpp** build. Either way it is the same `OGStageModel.complete` contract, and the
library stays free of the dependency.

### Expect the fallback to earn its keep

The stage prompt is a full JSON schema (layers, keyframe tracks, enums), so a tiny 1–3B model will
stumble often and `resilient(...)` will drop to the offline director a lot. A ~7B+ instruct model that
follows JSON is the realistic floor for authoring a genuine stage. Either way the loop never breaks:
worst case it is the deterministic offline movie, best case a model-written one, in the identical
`OGStage` format and player.
