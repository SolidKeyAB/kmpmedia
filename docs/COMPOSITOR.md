# On-device compositor + export — KMPMedia 1.13.0 (GIF) · 1.14.0 (MP4)

KMPMedia could already put *one* piece of media in *one* shape. 1.13.0 adds the flagship **Bet 3**
feature: compose **several** layers on **one timeline**, animate them with **keyframes**, preview the
result live at 60fps, and **export it to a shareable animated GIF** — all authored on device. 1.14.0
adds a second export target: the same composition renders to a **real H.264 MP4** (`exportMp4()`).

- **`OGComposition`** — a fixed `width` × `height` canvas, a `durationMs` timeline at `fps`, a
  background colour, and a back-to-front stack of `OGCompositionLayer`s. Each layer is a still image or
  a solid fill, placed in a box whose **x / y / scale / rotation / opacity** are each an animatable
  keyframe track, optionally clipped to **any KMPMedia shape** (built-in, [lasso](./POLYGON_SHAPE.md),
  [multi-region](./SOFT_MASKS.md), `CircleShape`, …). Layers can enter and leave over `[startMs, endMs]`.
- **`OGCompositionView`** — a Compose composable that plays the composition on the frame clock (holds
  60fps), fit-scales it into the available space, and draws straight to the screen (no intermediate
  bitmap). Pass a `positionMs` to turn it into a scrubber.
- **`OGComposition.exportGif()`** — render every frame offscreen and encode a single looping animated
  GIF with the built-in **pure-Kotlin `OGGifEncoder`** (median-cut palette + LZW). No dependency, no
  platform encoder — the **exact same bytes** come out on Android and iOS. It returns the encoded
  `ByteArray`; where to save or share it is the app's call.
- **`OGComposition.exportMp4()`** *(1.14.0)* — render every frame offscreen and encode a **real H.264
  MP4** with the **OS video encoder** (Android `MediaCodec` + `MediaMuxer`, iOS `AVAssetWriter`), still
  with **no third-party dependency**. The encoded bytes are not identical across platforms (each OS has
  its own encoder), but the *input* is: compositing and the RGB→YUV / RGB→BGRA colour maths are shared,
  tested `commonMain`, so both encoders compress the same rendered frames. Returns the `ByteArray` too.

The model, the drawing, the GIF encoder and the MP4 colour conversions are pure `commonMain`; only the
MP4's final video encode is a thin platform layer over the OS encoder. A composition looks the same on
both platforms.

## Holds 60fps — the perf + simplicity gate

- **Keyframe tracks are parse-nothing / lerp-per-frame.** An `OGKeyframedFloat` is pure data; evaluating
  it at a time is a binary walk over its stops plus one eased lerp — a couple of multiplies. Layers are
  resolved to plain values once per frame, then drawn with ordinary `DrawScope` transforms and clips.
- **The preview never allocates a bitmap.** `OGCompositionView` draws through `drawComposition` directly
  to the screen. The offscreen `renderFrame` / `exportGif` path (one bitmap per frame) is **export-only**
  and never runs during preview.
- **The GIF encoder is one-shot export code**, not a render loop: it samples a shared palette across all
  frames, caches nearest-colour lookups (reduced to 5 bits/channel), and LZW-compresses. A short clip
  encodes in well under a second and touches no platform API.

## API — `com.solidkey.painpoints.compositor`

```kotlin
// ---- the model ----
data class OGComposition(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val layers: List<OGCompositionLayer>,
    val fps: Int = 24,
    val background: Color = Color.Transparent,
)

sealed interface OGLayerContent {
    data class Image(val bitmap: ImageBitmap) : OGLayerContent   // any decoded still (incl. a video grab)
    data class Solid(val color: Color) : OGLayerContent
}

data class OGCompositionLayer(
    val id: String,
    val content: OGLayerContent,
    val width: Float,
    val height: Float,
    val x: OGKeyframedFloat = OGKeyframedFloat.const(0f),
    val y: OGKeyframedFloat = OGKeyframedFloat.const(0f),
    val scale: OGKeyframedFloat = OGKeyframedFloat.const(1f),
    val rotationDeg: OGKeyframedFloat = OGKeyframedFloat.const(0f),
    val opacity: OGKeyframedFloat = OGKeyframedFloat.const(1f),
    val clip: Shape? = null,
    val contentScale: ContentScale = ContentScale.Crop,
    val startMs: Long = 0L,
    val endMs: Long? = null,          // null → runs to the composition end
)

// ---- keyframes ----
enum class OGEasing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }
data class OGKeyframe(val timeMs: Long, val value: Float, val easing: OGEasing = OGEasing.LINEAR)
data class OGKeyframedFloat(val default: Float, val keyframes: List<OGKeyframe> = emptyList()) {
    fun valueAt(timeMs: Long): Float
    companion object {
        fun const(value: Float): OGKeyframedFloat
        fun of(vararg stops: Pair<Long, Float>): OGKeyframedFloat   // time -> value, LINEAR
    }
}

// ---- preview ----
@Composable
fun OGCompositionView(
    composition: OGComposition,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    loop: Boolean = true,
    positionMs: Long? = null,               // set → controlled/scrubber; null → plays itself
    contentScale: ContentScale = ContentScale.Fit,
    onProgress: ((timeMs: Long) -> Unit)? = null,
)

// ---- export ----
fun OGComposition.exportGif(loopCount: Int = 0, alphaThreshold: Int = 128): ByteArray
fun OGComposition.exportMp4(                                      // H.264 MP4 via the OS encoder (1.14.0)
    bitRate: Int = defaultMp4BitRate(width, height, fps),
    background: Color = Color.Black,                             // transparent pixels resolve to this (no alpha in H.264)
): ByteArray
fun defaultMp4BitRate(width: Int, height: Int, fps: Int): Int    // resolution/fps-scaled, 0.75–16 Mbps
fun OGComposition.renderFrame(timeMs: Long): ImageBitmap          // grab any single frame
fun OGComposition.renderFrameAt(index: Int): ImageBitmap          // grab frame by index (1.20.0)
fun OGComposition.exportFrames(): List<ImageBitmap>               // the whole frame sequence (1.20.0)
fun OGComposition.exportFramesArgb(): List<IntArray>             // frames as raw 0xAARRGGBB pixels (1.20.0)
fun OGComposition.forEachFrame(action: (i: Int, timeMs: Long, frame: ImageBitmap) -> Unit) // stream frames, low memory (1.20.0)
fun OGComposition.frameTimesMs(): List<Long>                      // the frame plan — pure, no rendering (1.20.0)
fun ImageBitmap.toArgbPixels(): IntArray                          // row-major 0xAARRGGBB

// ---- the encoder (reusable on its own) ----
object OGGifEncoder {
    fun encode(width: Int, height: Int, frames: List<OGGifFrame>, loopCount: Int = 0,
               alphaThreshold: Int = 128): ByteArray
}
class OGGifFrame(val argb: IntArray, val delayCs: Int)            // argb = width*height, 0xAARRGGBB
```

## Example — a title card that fades and slides in, exported to GIF

```kotlin
val logo: ImageBitmap = /* decoded elsewhere */
val composition = OGComposition(
    width = 480, height = 270, durationMs = 2000, fps = 24,
    background = Color(0xFF101018),
    layers = listOf(
        OGCompositionLayer(
            id = "logo",
            content = OGLayerContent.Image(logo),
            width = 200f, height = 200f,
            x = OGKeyframedFloat.of(0L to 140f, 800L to 140f),
            y = OGKeyframedFloat(                                   // slide up with an ease-out
                default = 60f,
                keyframes = listOf(
                    OGKeyframe(0L, 120f),
                    OGKeyframe(800L, 40f, OGEasing.EASE_OUT),
                ),
            ),
            opacity = OGKeyframedFloat.of(0L to 0f, 600L to 1f),    // fade in
            rotationDeg = OGKeyframedFloat.of(1200L to 0f, 2000L to 8f),
            clip = CircleShape,
            contentScale = ContentScale.Crop,
        ),
    ),
)

// Live preview (loops at 60fps):
OGCompositionView(composition, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))

// Export to a shareable GIF (loops forever), then save/share the bytes yourself:
val gifBytes: ByteArray = composition.exportGif()

// …or to an H.264 MP4 via the OS encoder (run off the main thread — it blocks on the encoder):
val mp4Bytes: ByteArray = withContext(Dispatchers.Default) { composition.exportMp4() }
```

## Notes & guarantees

- **Purely additive.** New package, nothing else changes; every existing call keeps compiling.
- **v1 layers are still content** — a decoded `ImageBitmap` or a colour fill, animated by the layer's
  transform. A frame can hold *any* single decoded bitmap (including one grabbed from a video), but
  playing a *running* GIF/video *inside* a composition is intentionally out of scope for the first cut.
- **The library returns bytes, not a file.** Saving to disk / the photo library / a share sheet is
  platform- and app-specific, so `exportGif()` hands back the encoded `ByteArray` and stays platform-free.
- **Transparency is one colour.** GIF has a single fully-transparent palette index (no partial alpha):
  pixels below `alphaThreshold` become transparent and the encoder uses disposal method 2 so transparent
  areas never accumulate across frames. Pass `alphaThreshold > 255` to force a fully opaque GIF.
- **Palette is shared + quantized.** Every frame maps to one ≤256-colour median-cut palette, so smooth
  gradients band as any GIF does; flat colours and clip edges stay crisp.
- **MP4 uses the OS encoder, not pure Kotlin.** `exportMp4()` is the one place a platform encoder is
  involved (`MediaCodec` / `AVAssetWriter`), so the encoded bytes differ per platform — but no
  third-party dependency is pulled in, and the frames fed in are the same shared render. H.264 is
  **opaque** (no alpha): transparent pixels composite onto the `background` param (default black). It
  needs **even** dimensions, so an odd canvas is cropped by its last row/column. `exportMp4()` **blocks**
  on the encoder — call it off the main thread (e.g. `Dispatchers.Default`).

## Where it fits

This is **Bet 3 — On-device mini-compositor + export** on the [roadmap](../ROADMAP.md): "author on
device, get a shareable file — no KMP library does compose-and-export." It builds directly on the shape
system ([shapes](./POLYGON_SHAPE.md), [morph](./SHAPE_MORPH_CLIPS.md),
[soft/multi-region](./SOFT_MASKS.md)) — any of those shapes is a valid layer clip. Run it in the demo
app: **Compositor + export** (`CompositorScreen`), which exports a GIF, an MP4, **and a frame sequence**
(shown as a filmstrip), playing the GIF/MP4 back through the platform's own decoder.

### Export → frame sequence *(1.20.0)*

Beyond the one-file GIF/MP4 encoders, you can take the **individual frames** — to write a PNG sequence,
feed a custom encoder, run per-frame analysis, or build a filmstrip:

- `exportFrames(): List<ImageBitmap>` — the whole sequence as bitmaps.
- `exportFramesArgb(): List<IntArray>` — the same as raw, row-major `0xAARRGGBB` pixels (most portable).
- `forEachFrame { i, timeMs, frame -> … }` — streams one frame at a time (lowest memory; prefer this for
  long compositions).
- `renderFrameAt(index)` / `frameTimesMs()` — one frame by index, and the pure (render-free) frame plan.

Each frame uses the same `renderFrame` the GIF/MP4 exporters use, so frames are identical across
platforms. They allocate a bitmap per frame — run off the main thread.
