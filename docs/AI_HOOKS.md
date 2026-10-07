# AI hooks — `com.solidkey.painpoints.ai.OGAiVector` (KMPMedia 1.11.0)

KMPMedia's differentiator is *editable, data-bound vector*: a live SVG scene graph you patch by node
`id` ([`OGSvgNodeOverride`](./RUNTIME_SVG.md)) and a free-form polygon [lasso](./POLYGON_SHAPE.md) that
clips any media to a hand- or AI-drawn outline. Both are **just data** — which makes them a natural
target for a language model. `OGAiVector` closes that loop: a **stable, provider-agnostic JSON schema**
for those primitives, with parse/serialize both directions and a helper that hands a model the exact
contract to fill in.

> **"describe → shape / patch."** Ask a model for *"a five-pointed star"* or *"point the gauge needle to
> 80% and make it red"*, and its reply drops straight into a `clipShape` or an `overrides` map — no glue.

> **It's a wire format, not an AI SDK.** The **JSON schema** ([below](#json-schema)) is the stable,
> versioned contract; `decode*` / `encode*` are built around it. The prompt-builder *text*
> (`polygonPrompt`, `svgPatchPrompt`, `imageToVectorPrompt`) is a **convenience, not part of the
> contract** — reword it, translate it, or write your own; only the JSON your model returns has to match
> the schema. So this layer is really just a codec for the library's own vector primitives, with helpers
> to hand a model the shape of the data.

## The library stays zero-dependency and provider-agnostic

`OGAiVector` makes **no network calls** and bundles **no AI SDK**. It defines the contract and does the
JSON both ways; *you* own the model and the call. That keeps the library simple and free of any provider
lock-in, and it works with any model (Claude, a local model, a segmentation network that emits vertices).
The three moving parts:

1. **Ask** — `polygonPrompt(instruction)` / `svgPatchPrompt(instruction, nodeIds)` return a ready-to-send
   prompt that states the JSON schema and embeds your instruction. Send it however you talk to your model.
2. **Apply** — `decodePolygon(reply)` → an `OGPolygonShape` for any `clipShape`; `decodeSvgPatch(reply)` →
   the `Map<String, OGSvgNodeOverride>` that `OGSVGView(overrides = …)` consumes. Decoding is **tolerant**
   of the markdown code fences and stray prose models routinely add.
3. **Persist / seed** — `encodePolygon(shape)` / `encodeSvgPatch(overrides)` go the other way, to save a
   lasso, show the payload, or seed a prompt with the current state.

All of this runs at generate/patch time, not per frame, so it never touches the 60fps hot path that
clipping and [morphing](./SHAPE_MORPH_CLIPS.md) live on.

## API

```kotlin
object OGAiVector {
    val json: Json                                   // the lenient, unknown-key-tolerant codec used below

    // model reply -> live primitive (tolerant of ```json fences + surrounding prose)
    // `smoothing` (0..1, default 0 = straight edges) rounds the outline — see "Smoothing & robustness".
    fun decodePolygon(text: String, smoothing: Float = 0f): OGPolygonShape
    fun decodePolygonOrNull(text: String, smoothing: Float = 0f): OGPolygonShape?
    fun decodeSvgPatch(text: String): Map<String, OGSvgNodeOverride>
    fun decodeSvgPatchOrNull(text: String): Map<String, OGSvgNodeOverride>?
    fun decodeScene(text: String): OGSceneSpec                 // multi-region (image -> vector scene)
    fun decodeSceneOrNull(text: String): OGSceneSpec?
    fun decodeSceneShapes(text: String, smoothing: Float = 0f): List<OGPolygonShape>  // sanitized -> clip shapes

    // live primitive -> JSON
    fun encodePolygon(shape: OGPolygonShape): String
    fun encodeSvgPatch(overrides: Map<String, OGSvgNodeOverride>): String
    fun encodeScene(scene: OGSceneSpec): String

    // hand a model the contract
    fun polygonPrompt(instruction: String): String
    fun svgPatchPrompt(instruction: String, nodeIds: List<String> = emptyList()): String
    fun imageToVectorPrompt(                                   // vision: an image -> editable vectors
        hint: String? = null,
        target: OGVectorTarget = OGVectorTarget.POLYGON,       // POLYGON (one silhouette) | SCENE (regions)
        maxShapes: Int = 1,
        imageInfo: OGImageInfo? = null,
    ): String

    // pull the JSON payload out of a raw reply (strips fences / prose)
    fun extractJson(text: String): String
}

// Attach an image to a vision call (library does NO network I/O — these only serialize pixels):
fun ImageBitmap.toBase64Png(): String                          // ready for an image content block
fun ImageBitmap.toPngBytes(): ByteArray                        // Android Bitmap.compress / iOS Skia
data class OGImageInfo(val width: Int, val height: Int)        // .of(bitmap); .aspect for the prompt
```

The serializable schema types (`com.solidkey.painpoints.ai`) mirror the Compose-facing primitives but use
model-friendly plain values (colors as strings, coordinates as floats):

```kotlin
@Serializable data class OGPointSpec(val x: Float, val y: Float)
@Serializable data class OGPolygonSpec(val points: List<OGPointSpec>)          // .toShape(smoothing=0f); .sanitized(); .isRenderable
@Serializable data class OGNodeOverrideSpec(                                     // .toOverride() -> OGSvgNodeOverride
    val fill: String? = null, val stroke: String? = null, val strokeWidth: Float? = null,
    val translateX: Float? = null, val translateY: Float? = null,
    val rotation: Float? = null, val rotationCx: Float? = null, val rotationCy: Float? = null,
    val scaleX: Float? = null, val scaleY: Float? = null,
    val pathData: String? = null, val pathDataTo: String? = null, val morphProgress: Float = 0f,
)
@Serializable data class OGSvgPatchSpec(val overrides: Map<String, OGNodeOverrideSpec> = emptyMap()) // .toOverrides()
@Serializable data class OGPlacedShapeSpec(                                      // .toShape(smoothing=0f); .sanitized(); .isRenderable
    val points: List<OGPointSpec>, val label: String? = null, val fill: String? = null)
@Serializable data class OGSceneSpec(val shapes: List<OGPlacedShapeSpec> = emptyList()) // .toShapes(smoothing=0f); .labels(); .sanitized()
```

Colors are parsed with the **same** parser the SVG renderer uses, so the model may emit `#RGB`,
`#RRGGBB`, `#AARRGGBB`, `rgb(r,g,b)` / `rgba(...)`, or a name like `red` — exactly as in SVG.

## JSON schema

**Polygon lasso** — vertices in normalized `0..1` space (top-left origin), in order; the outline closes
automatically. `≥ 3` points.

```json
{ "points": [ {"x":0.5,"y":0.0}, {"x":1.0,"y":0.5}, {"x":0.5,"y":1.0}, {"x":0.0,"y":0.5} ] }
```

**SVG patch** — `id → override`. Every field is optional; omitting one leaves that aspect of the node
unchanged.

```json
{ "overrides": { "needle": {"rotation":120,"fill":"#E53935"}, "bg": {"fill":"#111111"} } }
```

**Scene** (image → vector, `SCENE` target) — an ordered set of regions, each a closed polygon in the
same normalized `0..1` space, with an optional `label` and representative `fill`.

```json
{ "shapes": [
  { "label":"head",  "fill":"#E0B080", "points":[ {"x":0.42,"y":0.08}, {"x":0.58,"y":0.08}, {"x":0.5,"y":0.34} ] },
  { "label":"torso", "points":[ {"x":0.3,"y":0.35}, {"x":0.7,"y":0.35}, {"x":0.7,"y":0.92}, {"x":0.3,"y":0.92} ] }
] }
```

## Example — describe → clip region

```kotlin
import com.solidkey.painpoints.ai.OGAiVector
import com.solidkey.painpoints.image.OGImageView
import com.solidkey.painpoints.image.loading.OGImageUrlType

// 1) Ask your model (any model — this is your code / your API call).
val prompt = OGAiVector.polygonPrompt("the outline of a five-pointed star")
val reply: String = myLlm.complete(prompt)          // whatever you use to reach a model

// 2) Apply — the reply becomes a live clip shape.
val star = OGAiVector.decodePolygonOrNull(reply) ?: OGPolygonShape.of(/* fallback */)

OGImageView(
    source = OGImageUrlType("https://example.com/portrait.jpg"),
    clipShape = star,                                // clips the photo to the model's outline
    onEventTriggered = { _, _ -> },
)
```

## Example — describe → SVG patch

```kotlin
import com.solidkey.painpoints.ai.OGAiVector
import com.solidkey.painpoints.image.svg.OGSVGView
import com.solidkey.painpoints.image.loading.OGSvgResourceFileType

// Constrain the model to the ids that actually exist in your SVG.
val prompt = OGAiVector.svgPatchPrompt(
    instruction = "point the gauge needle to about 80% and turn the arc amber",
    nodeIds = listOf("needle", "arc", "dot"),
)
val overrides = OGAiVector.decodeSvgPatchOrNull(myLlm.complete(prompt)) ?: emptyMap()

OGSVGView(
    source = OGSvgResourceFileType("gauge.svg"),
    width = 240f, height = 240f,
    overrides = overrides,                           // the model's patch, applied live — no re-parse
)
```

Because a patch is plain data, you can also let the model **morph** a path: it emits `pathDataTo` +
`morphProgress`, and the node tweens exactly as the [runtime path morph](./RUNTIME_SVG.md) does.

## Example — image → vector *(new in 1.25.0)*

The same contract, but the input is an **image** instead of a sentence: hand a *vision* model a photo (or
a frame of a running scene) and get the identical normalized vector JSON back. `imageToVectorPrompt` builds
the text half; you attach the image yourself with `ImageBitmap.toBase64Png()` (the library still makes **no**
network call). Decode with `decodePolygon` for one silhouette, or `decodeScene` for several labelled regions.

```kotlin
import com.solidkey.painpoints.ai.OGAiVector
import com.solidkey.painpoints.ai.OGImageInfo
import com.solidkey.painpoints.ai.OGVectorTarget
import com.solidkey.painpoints.ai.toBase64Png

// 1) Ask a vision model to trace the subject (your model, your call).
val photo: ImageBitmap = /* a decoded photo or a captured frame */
val prompt = OGAiVector.imageToVectorPrompt(
    hint = "trace the person's silhouette",
    target = OGVectorTarget.POLYGON,
    imageInfo = OGImageInfo.of(photo),               // lets the prompt state the aspect ratio
)
val reply = myVisionLlm.complete(prompt, imageBase64 = photo.toBase64Png())  // base64 PNG, no network in the lib

// 2) Apply — the reply becomes a live clip shape.
val cutout = OGAiVector.decodePolygonOrNull(reply) ?: OGPolygonShape.of(/* fallback */)
OGImageView(source = /* the same photo */, clipShape = cutout, onEventTriggered = { _, _ -> })
```

For a multi-part cut-out — "cut the head, torso and each arm out of this photo" — use `target = SCENE` and
`decodeScene(reply).toShapes()` (one `OGPolygonShape` per region, with the model's `label`/`fill` as advisory
metadata).

> **Honest limit.** A general vision model returns *approximate / semantic* vectors (a silhouette, rough
> regions), not pixel-accurate tracing. For a precise cut-out, pair it with a segmentation model (see
> [auto-cutout](./AUTO_CUTOUT.md)) and feed those vertices through `OGPolygonSpec` / `OGSceneSpec` directly —
> the division of labour is *LLM = understand + approximate as editable vectors; segmentation = precise mask.*

## Smoothing & robustness

A model emits a *faceted*, few-point outline, and often a slightly messy one. Two things turn that into a
clean, safe clip with no change to the wire format (the JSON stays "just points"):

**Smoothing.** Every decode that yields a shape takes an optional `smoothing: Float` (`0..1`, default `0`).
`0` keeps straight edges; above `0` the outline is rounded with a closed, interpolating **centripetal
Catmull-Rom** spline that still passes through every input vertex, so an 8–40-point silhouette reads as a
smooth curve. Centripetal (not uniform) parameterization is used on purpose: a model's points are unevenly
spaced, and that is exactly where uniform smoothing overshoots or forms self-intersecting loops; centripetal
does not. It is computed once when the shape is built, never per frame, so it is free on the 60fps clip path.

```kotlin
val shape  = OGAiVector.decodePolygon(reply, smoothing = 0.6f)        // one rounded lasso
val shapes = OGAiVector.decodeSceneShapes(reply, smoothing = 0.6f)    // rounded, sanitized regions
// or straight from a spec: OGPolygonSpec(points).toShape(0.6f)
```

**Robustness.** Model output is rarely pristine, so the codec degrades gracefully instead of crashing:

- `decodePolygonOrNull` / `decodeSceneOrNull` return `null` (not throw) on unparseable replies, for a clean fallback.
- `OGPolygonSpec` / `OGPlacedShapeSpec` / `OGSceneSpec` expose `isRenderable` (≥ 3 finite vertices) and
  `sanitized()` (clamps every vertex to a finite `0..1`; for a scene, also **drops** regions that can't render).
- `decodeSceneShapes` applies `sanitized()` for you, so a shaky multi-region reply yields only usable cut-outs
  with the `label ↔ shape` order preserved.
- At raster time a non-finite coordinate (`NaN` / `±∞`) is treated as `0` and everything is clamped to the box,
  so no reply can push the clip outside its bounds or poison the path.

## Where this sits on the roadmap

This is the first shipped piece of **Bet 1 — Runtime & AI-editable vector**'s "AI hooks" item: making
prompt-driven vector generation/patching first-class. The [`llms.txt`](../llms.txt) API index and the
[AI coding guide](./AI_GUIDE.md) already help an assistant *write* KMPMedia code; `OGAiVector` lets an app
let its *own* users drive the vector with natural language at runtime.
