# Animated GIF — `com.solidkey.painpoints.image.gif` (KMPMedia 1.6.0)

KMPMedia plays **animated GIFs on both Android and iOS**. Before 1.6.0 the image path
decoded only the *first frame* of a GIF (a static picture); now the frames actually animate,
with the same one-liner API as any other image.

> Animated **WebP** and other multi-frame formats the platform decoder understands ride the
> same path — the code is not GIF-specific, it just plays whatever the platform reports as
> having more than one frame.

## The easy way — `OGImageView`

Point [`OGImageView`](../library/src/commonMain/kotlin/com/solidkey/painpoints/image/OGImageView.kt)
at a `.gif` source and it plays automatically. Detection is by file extension, so a `.gif`
**URL** or **file path** animates on both platforms (an iOS resource GIF works too — its location
resolves to a full `.gif` path).

```kotlin
import com.solidkey.painpoints.image.OGImageView
import com.solidkey.painpoints.image.loading.OGImageUrlType

OGImageView(
    source = OGImageUrlType("https://example.com/loading.gif"),
    onEventTriggered = { _, _ -> },
    onError = { /* ... */ },
)
```

Everything else about `OGImageView` still applies to the moving image:

- `contentScale` (`Fit` / `Crop` / …) and `alignment`,
- shape-crop (`displayShape` / `cornerRadius`) and the free-form `clipShape` lasso —
  the GIF plays *inside* the clipped shape.

Static pixel **transformations** (resize/crop via `OGImageProcessor`) do **not** apply to a GIF;
use `contentScale` + the shape clip to fit it.

## Direct control — `rememberOGAnimatedPainter`

For explicit `loop` / `speed`, or to feed the animation into your own composable, use the
painter directly. It returns `null` while decoding (off the main thread) and on failure, so
show a placeholder until it is ready.

```kotlin
import androidx.compose.foundation.Image
import com.solidkey.painpoints.image.gif.rememberOGAnimatedPainter
import com.solidkey.painpoints.source.OGSource

@Composable
fun Spinner() {
    val painter = rememberOGAnimatedPainter(
        source = OGSource.Url("https://example.com/loading.gif"),
        loop = true,     // false = play once and stop on the last frame
        speed = 1.5f,    // 1.0 = native rate; 2.0 = twice as fast (iOS)
        onError = { message -> /* log / fallback */ },
    )
    painter?.let { Image(painter = it, contentDescription = null) }
}
```

`OGSource` accepts `Url`, `FilePath`, or `Resource`. On Android a `Resource` is looked up in
`res/raw` first (the recommended home for a `.gif`), then `res/drawable`.

## How it works

The public API is one shared `expect` composable; each platform decodes with its native path:

| | Decoder | `loop` | `speed` |
|---|---|:---:|:---:|
| **Android (API 28+)** | `ImageDecoder` → self-animating `AnimatedImageDrawable` | ✅ (repeat count) | ⚠️ native rate only |
| **Android (API 24–27)** | `BitmapFactory` first frame (static fallback) | — | — |
| **iOS** | Skia `Codec` — every frame decoded + composited | ✅ | ✅ |

- **Android** wraps the drawable in a Compose `Painter` (an Accompanist-style
  `DrawablePainter`) that starts/stops the animation with the composition and redraws when the
  drawable invalidates itself. `AnimatedImageDrawable` has no speed control, so `speed` is
  ignored there.
- **iOS** decodes every frame into an `ImageBitmap` and cycles them with
  [`OGGifClock`](../library/src/commonMain/kotlin/com/solidkey/painpoints/image/gif/OGGif.kt),
  a pure, unit-tested timing engine that honours each frame's on-screen duration (0-delay
  frames fall back to 100 ms, matching browsers) and the `speed` multiplier. The frame index is
  Compose snapshot state, so advancing it repaints with no manual invalidation.

Decoding always runs off the main thread. The animation stops automatically when the composable
leaves the composition.
