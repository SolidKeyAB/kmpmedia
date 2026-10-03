# Caching & large-GIF memory (KMPMedia 1.18.0)

Two table-stakes parity additions, both transparent — **no new API, nothing to opt into**:

1. a **persistent disk cache** for remote images and GIFs, so a URL fetched once isn't re-downloaded
   on the next app launch;
2. a **GIF frame-memory cap**, so a large or long animated GIF can't blow up memory.

## Disk cache

Before 1.18, remote images/GIFs were kept only in an **in-memory** cache (an LRU bitmap cache on
Android, a bounded cache on iOS) that dies with the process — so every cold start re-downloaded them.
1.18 adds a **disk** layer under the OS cache directory:

- **Lookup order:** in-memory → disk → network. A network fetch is **written through** to disk, so the
  next launch reads it from disk instead of the network.
- **Keyed by content hash.** The URL is hashed with [`OGStableHash`](../library/src/commonMain/kotlin/com/solidkey/painpoints/image/loading/OGStableHash.kt)
  — a 128-bit (two 64-bit FNV-1a) hex digest, stable across platforms and launches — so collisions
  that could serve the *wrong* image are negligible (a 32-bit `hashCode()` wouldn't be safe enough).
- **Bounded.** Android caps the directory at 128 MB and evicts least-recently-used files; iOS does the
  same as a backstop and also benefits from the OS purging the Caches directory under storage pressure.
- **Best-effort.** Any IO error is swallowed and treated as a cache miss — the cache is an optimisation,
  never a hard dependency, and a half-written file can't be read (writes are atomic via a temp file).
- **Location:** `cacheDir/og_image_cache` (Android `Context.cacheDir`) · `Caches/og_image_cache` (iOS
  `NSCachesDirectory`).

Verified on Android: after loading three remote GIFs, the cache directory holds three hashed files;
with **airplane mode on and the app cold-restarted** (in-memory cache gone), all three still render —
proof they loaded from disk.

## GIF frame-memory cap

How a GIF is held in memory differs by platform, so the cap is matched to each:

- **Android** uses the platform `AnimatedImageDrawable`, which decodes frames **on demand** — memory is
  already bounded to roughly one frame. 1.18 additionally caps each frame's **longest edge** via
  `ImageDecoder.setTargetSize`, so a huge-dimension GIF decodes smaller.
- **iOS** decodes **every frame** up front into a Skia bitmap list (needed for Skia's frame compositing),
  so a long GIF of many frames is the real OOM risk. 1.18 caps both the per-frame edge **and** a
  **total-frames budget** (`frameCount × width × height × 4 ≤ 64 MB`), downscaling frames at decode time
  when needed.

The decision is pure maths in
[`OGGifDecodeBudget`](../library/src/commonMain/kotlin/com/solidkey/painpoints/image/gif/OGGifDecodeBudget.kt)
(`frameScale(width, height, frameCount, allFramesInMemory)`), unit-tested on JVM + iOS; each platform
applies the returned scale with its own resampler. Small GIFs are never downscaled.

## Notes

- **Zero new dependency, zero API change.** Existing `OGImageView` / GIF calls behave identically; they
  just avoid re-downloading and can't be OOM'd by a pathological GIF.
