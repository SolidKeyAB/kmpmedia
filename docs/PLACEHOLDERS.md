# Placeholder, loading & error slots + accessibility — KMPMedia 1.16.0

`OGImageView` used to show a neutral grey box while an image loaded and a solid colour box when it
failed, and it reported failures only through the `onError` **callback**. 1.16.0 adds the three
things adopters expect from an image view, all as **additive, backward-compatible** parameters:

- **A placeholder slot** — any composable shown while the image is still loading (a spinner, a
  blurred thumbnail, a brand colour) instead of the grey box.
- **An error slot** — any composable shown when the image fails to load (bad URL, decode error,
  missing/unsupported file, or an unresolvable source), the *visual* partner to `onError`, which
  still fires.
- **An accessibility description** — `contentDescription`, read aloud by TalkBack / VoiceOver.

Everything defaults to the previous behavior, so existing calls compile and render exactly as before.

## The new parameters

```kotlin
OGImageView(
    source = OGImageUrlType("https://example.com/photo.jpg"),
    modifier = Modifier.size(220.dp),

    // ♿ Read aloud by screen readers. null (default) = decorative → the reader skips it.
    contentDescription = "Profile photo of Ada Lovelace",

    // ⏳ Shown until the first bitmap/frame arrives. null (default) keeps the old grey box.
    placeholder = { CircularProgressIndicator() },

    // ⚠️ Shown on failure. null (default) keeps the old solid-colour fallback. onError still fires.
    error = { Text("⚠️ Couldn't load") },

    onError = { msg -> log(msg) },
    onEventTriggered = { _, _ -> },
)
```

| Param | Type | Default | Behavior when default |
|-------|------|---------|-----------------------|
| `contentDescription` | `String?` | `null` | Image is decorative (skipped by screen readers) |
| `placeholder` | `(@Composable () -> Unit)?` | `null` | Neutral light-grey box while loading |
| `error` | `(@Composable () -> Unit)?` | `null` | Solid-colour fallback box on failure |

The placeholder and error composables are drawn **inside the same shape clip / soft mask** as the
image, so a placeholder fills a circular/triangular/lasso `OGImageView` just like the photo does.

## How it decides what to draw

The view tracks two facts — whether a painter has arrived, and whether the load has `failed` — and
resolves them into one of three phases. That resolution is a pure function, unit-tested on JVM + iOS
(no Compose runtime), mirroring the rest of the library's pure-logic helpers:

```kotlin
internal fun ogImagePhase(hasPainter: Boolean, failed: Boolean): OGImagePhase = when {
    failed     -> OGImagePhase.Error     // a failure wins over a stale/fallback painter
    hasPainter -> OGImagePhase.Success
    else       -> OGImagePhase.Loading
}
```

`failed` is driven by wrapping the caller's `onError`, so the **error slot and the `onError`
callback always fire from the same single signal** — they can't disagree. The flag resets whenever
the `source` changes, so re-pointing an `OGImageView` at a new URL starts cleanly in the loading
phase.

A failure wins over any painter because the loaders hand back a solid-colour fallback painter *and*
fire `onError` on failure — so without the precedence, the error slot would never get a chance to
show.

## Works for GIFs too

The same three phases drive the animated-GIF path: the placeholder shows while the GIF decodes, the
error slot shows if it can't, and the animation plays on success. No extra wiring.

## Accessibility & RTL notes

- `contentDescription` is the one piece you should set for any image that conveys information; leave
  it `null` for purely decorative images so screen readers don't announce noise.
- `OGImageView` is already **RTL-safe**: `alignment` is a Compose `Alignment`, which resolves against
  the ambient `LayoutDirection`, so `CenterStart` / `CenterEnd` follow the locale with no extra code.

## Perf + simplicity gate

Zero new cost on the happy path: the phase is a two-boolean `when`, there's no extra layout or draw
when no slot is supplied, and the slots are only composed in their own phase. No new dependency,
identical on Android & iOS.
