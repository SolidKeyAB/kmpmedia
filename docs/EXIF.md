# EXIF orientation — automatic upright photos (KMPMedia 1.17.0)

Phone cameras store the raw sensor pixels and record *how the phone was held* in EXIF tag `0x0112`
(`Orientation`). A photo shot in portrait, or upside down, therefore decodes **sideways** unless the
viewer reads that tag and rotates. Platform bitmap decoders don't do it for you: Android's
`BitmapFactory` ignores the tag entirely, and on iOS the `UIImage → PNG → Skia` round-trip the
library uses drops `UIImage.imageOrientation`.

1.17.0 fixes both. Any image `OGImageView` loads — from a file, a URL, or bytes — now decodes
**upright automatically**. There is no new API and nothing to opt into; it just renders correctly.

```kotlin
// A portrait photo with EXIF orientation 6 (rotate 90° CW). Before 1.17 it showed sideways;
// now it shows upright. Same call as always:
OGImageView(source = OGImageUrlType("https://example.com/portrait.jpg"))
```

## What it covers

All eight EXIF orientation values — the four rotations and the four mirrored variants:

| Value | Meaning | Correction applied |
|------:|---------|--------------------|
| 1 | Normal | none |
| 2 | Flip horizontal | mirror |
| 3 | Rotate 180° | rotate 180° |
| 4 | Flip vertical | mirror + rotate 180° |
| 5 | Transpose | mirror + rotate 270° CW |
| 6 | Rotate 90° CW | rotate 90° CW |
| 7 | Transverse | mirror + rotate 90° CW |
| 8 | Rotate 270° CW | rotate 270° CW |

## How it works

- **Shared, tested mapping.** [`OGExifOrientation.transformFor(orientation)`](../library/src/commonMain/kotlin/com/solidkey/painpoints/image/loading/OGExifOrientation.kt)
  (in `commonMain`) maps an orientation value to an `OGExifTransform(rotationDegrees, mirrored)` —
  *mirror horizontally, then rotate clockwise*. The classic transpose/transverse (5/7) bugs are
  pinned by a geometry test that runs the four image corners through the transform and compares them
  to the canonical per-orientation pixel mapping, on **JVM and iOS**.
- **Android** reads the tag from the same source it decoded with the SDK's built-in
  `android.media.ExifInterface` (no new dependency) and applies the transform with a `Matrix`. The
  correction runs **after** downsampling, so it works on the already-cheap bitmap; an upright photo
  (the common case) is returned untouched with no extra allocation.
- **iOS** lets UIKit do the rotation: the decode already redraws the `UIImage` into a graphics context
  (for downsampling), and UIKit draws every image upright, so normalising orientation is free in that
  same pass. Images that don't need downsampling are now redrawn too **when** their orientation isn't
  already upright, so the PNG round-trip can't drop it.

## Notes

- **Zero new dependency, zero API change** — purely a correctness fix. Existing calls behave
  identically for already-upright images.
- **Degrades safely** — a missing or corrupt EXIF header falls back to `NORMAL` (the pre-1.17
  behaviour), never a crash.
- **Scope** — applies to decoded photos (file / URL / bytes). Bundled drawable **resources** are
  re-encoded by the build tools and carry no EXIF, so there's nothing to correct there.
- The demo's **EXIF auto-rotate** screen renders the standard eight-orientation test set upright on
  both platforms.
