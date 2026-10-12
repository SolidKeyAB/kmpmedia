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
- Any model works — the format is provider-agnostic. `OGStageModel` is just "prompt in → text out".
```
