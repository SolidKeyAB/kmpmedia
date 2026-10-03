# Live camera in any shape (KMPMedia 1.22.0)

`OGImageView` clips a photo to any shape, `OGAVPlayer` clips a video — 1.22.0 completes the set with the
**live camera**, clipped to any shape: the AR-sticker primitive (Bet 4). Point `OGCameraPreview` at a
`Shape` (a built-in `OGShapeType.toShape()`, an `OGPolygonShape` lasso, `CircleShape`, …) and the feed is
masked to that outline — a circular / triangular / free-form window on the world.

Built on the **platform camera APIs with no third-party dependency** — **Camera2** on Android,
**AVFoundation** on iOS — keeping the library's zero-dependency promise.

```kotlin
import com.solidkey.painpoints.camera.OGCameraPreview
import com.solidkey.painpoints.camera.OGCameraFacing
import com.solidkey.painpoints.shape.OGShapeType
import com.solidkey.painpoints.shape.toShape

OGCameraPreview(
    modifier = Modifier.size(240.dp),
    shape = OGShapeType.TRIANGLE_UP.toShape(),   // or CircleShape, an OGPolygonShape, …
    facing = OGCameraFacing.BACK,                 // or FRONT (front mirrors by default)
    onError = { msg -> /* no permission / no device / busy */ },
)
```

## Permission is the app's job

Like any camera library, `OGCameraPreview` only **opens** the camera — it does not request the runtime
permission (a library can't own that UX). Your app must:

- **Android** — declare `<uses-permission android:name="android.permission.CAMERA"/>` and request it at
  runtime (e.g. `ActivityResultContracts.RequestPermission`) **before** showing the preview.
- **iOS** — add `NSCameraUsageDescription` to `Info.plist` and call `AVCaptureDevice.requestAccess(...)`.

If the permission isn't granted (or no camera is available), [onError] fires and nothing is drawn. The
demo's **Live camera in any shape** screen shows the full request-then-preview flow on both platforms.

## How it works

- **Android** — a `TextureView` fed by a Camera2 `CameraCaptureSession` repeating-preview request. The
  session runs on its own `HandlerThread`; the preview is rotated upright (from the sensor orientation +
  display rotation) and **center-crop-filled** into the shape via a `TextureView` transform matrix; the
  front camera is mirrored. The camera is closed when the composable leaves the composition.
- **iOS** — an `AVCaptureSession` with the device's video input, shown through an
  `AVCaptureVideoPreviewLayer` (`resizeAspectFill`) hosted in a `UIKitView`; the layer frame tracks the
  view via `onResize`. The session starts/stops off the main queue with the composable's lifecycle.

Both wrap the feed in `Modifier.clip(shape)`, the same GPU mask `OGImageView` / `OGAVPlayer` use — so the
shape clipping is free.

## Verification & caveats

- **Compiles on Android + iOS**; the new `com.solidkey.painpoints.camera` package is additive and
  isolated (nothing else in the library changed).
- **Android pipeline verified on the emulator via logcat**: the camera device opens and the capture
  session configures a 1280×960 preview stream into the `TextureView` surface, and the permission flow +
  shape/facing controls work.
- **Live frames don't render in a *headless* emulator** (the `ranchu` camera HAL doesn't feed a GPU
  preview surface under software rendering) — the preview shows live on a **real device**, where the
  standard Camera2 → `TextureView` path renders normally.
- **iOS is compile-verified only** — the simulator has no camera; run on a device to see the feed.
