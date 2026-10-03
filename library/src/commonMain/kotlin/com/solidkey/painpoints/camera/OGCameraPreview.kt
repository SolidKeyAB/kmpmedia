package com.solidkey.painpoints.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/** Which camera to show. */
enum class OGCameraFacing { BACK, FRONT }

/**
 * A **live camera feed clipped to any shape** — the AR-sticker primitive (Bet 4). Point it at a
 * [Shape] (a built-in `OGShapeType.toShape()`, an `OGPolygonShape` lasso, a `CircleShape`, …) and the
 * camera preview is masked to that outline, so you can drop a circular / triangular / free-form "window"
 * onto the world. The same `OGImageView` / `OGAVPlayer` shape-clip idea, now on the live camera.
 *
 * Built on the **platform camera APIs with no third-party dependency** — Camera2 on Android,
 * AVFoundation on iOS — matching the library's zero-dependency promise.
 *
 * ## Permission is the app's job
 * This composable only *opens* the camera; it does not request the runtime permission (a library can't
 * own that UX). Declare it (`android.permission.CAMERA`, iOS `NSCameraUsageDescription`) and request it
 * in your app **before** showing this. If the permission isn't granted (or no camera is available),
 * [onError] fires and nothing is drawn.
 *
 * @param shape the clip outline the preview is masked to (default: the full rectangle).
 * @param facing [OGCameraFacing.BACK] or [OGCameraFacing.FRONT].
 * @param mirror horizontally mirror the preview (defaults on for the front camera, the expected selfie look).
 * @param rotationOverride force the preview rotation, in degrees clockwise (0/90/180/270). `null` (default)
 *   uses the automatic sensor-orientation math. An escape hatch for the handful of devices whose reported
 *   `SENSOR_ORIENTATION` doesn't match reality, where the auto feed comes out sideways. Android only;
 *   ignored on iOS (AVFoundation orients the preview layer itself).
 * @param onError called with a message if the camera can't be opened (no permission, no device, busy).
 */
@Composable
expect fun OGCameraPreview(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    facing: OGCameraFacing = OGCameraFacing.BACK,
    mirror: Boolean = facing == OGCameraFacing.FRONT,
    rotationOverride: Int? = null,
    onError: ((String) -> Unit)? = null,
)
