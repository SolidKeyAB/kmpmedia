# Live camera in any shape (KMPMedia 1.22.1)

> **1.22.1** — real-device fixes: front/back toggle switches the camera in place (no blank front
> preview), the fill/rotate transform re-applies on the first real frame, and the Android preview no
> longer double-rotates (both cameras read upright in portrait). Adds `rotationOverride` as an escape
> hatch for devices whose reported sensor orientation doesn't match reality.


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
    rotationOverride = null,                      // Android escape hatch: force 0/90/180/270 if a
                                                  // device's auto preview comes out sideways (null = auto)
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
  session runs on its own `HandlerThread`, is **kept alive across a front/back toggle** (the camera input
  is swapped in place), and the fill/rotate transform is re-applied on the first real frame (so a view
  that wasn't yet laid out can't leave the raw, sideways preview on screen). The preview is
  **center-crop-filled** into the shape via a transform matrix and the front camera is mirrored. Rotation
  compensates for **device rotation only** (the platform already orients the preview Surface to the
  sensor; folding the sensor orientation in again double-rotates). `rotationOverride` forces the angle on
  the rare device where the auto path is still wrong. The camera is closed when the composable leaves.
- **iOS** — an `AVCaptureSession` with the device's video input, shown through an
  `AVCaptureVideoPreviewLayer` (`resizeAspectFill`) hosted in a `UIKitView`; the layer frame tracks the
  view via `onResize`. The session starts/stops off the main queue with the composable's lifecycle.

Both wrap the feed in `Modifier.clip(shape)`, the same GPU mask `OGImageView` / `OGAVPlayer` use — so the
shape clipping is free.

## Verification & caveats

- **Compiles on Android + iOS**; the `com.solidkey.painpoints.camera` package is additive and isolated.
- **Live frames verified on a real Android device** (1.22.1): front and back both render inside the shape
  and read upright in portrait; the front/back toggle switches cleanly. Live frames do **not** render in a
  *headless* emulator (the `ranchu` camera HAL doesn't feed a GPU preview surface under software
  rendering), so use a real device.
- **iOS is compile-verified only** — the simulator has no camera; run on a device to see the feed.
  `rotationOverride` is Android-only (AVFoundation orients the preview layer itself).
