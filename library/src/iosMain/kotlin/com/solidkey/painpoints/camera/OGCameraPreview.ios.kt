package com.solidkey.painpoints.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun OGCameraPreview(
    modifier: Modifier,
    shape: Shape,
    facing: OGCameraFacing,
    mirror: Boolean,
    @Suppress("UNUSED_PARAMETER") rotationOverride: Int?, // Android-only; AVFoundation orients the layer itself.
    onError: ((String) -> Unit)?,
) {
    val authorized = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) ==
        AVAuthorizationStatusAuthorized

    Box(modifier = modifier.clip(shape)) {
        if (!authorized) {
            LaunchedEffect(Unit) { onError?.invoke("Camera permission not granted") }
            return@Box
        }

        // One long-lived session + preview layer. The camera is switched by swapping the input in
        // place — recreating them would orphan the layer, since UIKitView's factory runs only once
        // (so a facing toggle would leave the old camera / a blank view on screen).
        val session = remember { AVCaptureSession().apply { sessionPreset = AVCaptureSessionPresetHigh } }
        val previewLayer = remember {
            AVCaptureVideoPreviewLayer(session = session).apply {
                videoGravity = AVLayerVideoGravityResizeAspectFill
            }
        }

        LaunchedEffect(facing, mirror) {
            configureCameraInput(session, facing, onError)
            previewLayer.connection?.let {
                if (it.isVideoMirroringSupported()) {
                    it.automaticallyAdjustsVideoMirroring = false
                    it.setVideoMirrored(mirror)
                }
            }
        }

        UIKitView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                val view = UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))
                view.layer.addSublayer(previewLayer)
                dispatch_async(dispatch_get_global_queue(0L, 0uL)) { session.startRunning() }
                view
            },
            onResize = { view, rect ->
                // Keep the preview layer filling the (shape-clipped) view without an implicit animation.
                CATransaction.begin()
                CATransaction.setDisableActions(true)
                previewLayer.setFrame(rect)
                CATransaction.commit()
            },
        )

        DisposableEffect(session) {
            onDispose { dispatch_async(dispatch_get_global_queue(0L, 0uL)) { session.stopRunning() } }
        }
    }
}

/** Swap the capture session's camera input in place (remove any current input, add [facing]). */
@OptIn(ExperimentalForeignApi::class)
private fun configureCameraInput(
    session: AVCaptureSession,
    facing: OGCameraFacing,
    onError: ((String) -> Unit)?,
) {
    session.beginConfiguration()
    (session.inputs as? List<*>)?.forEach { input ->
        (input as? AVCaptureInput)?.let { session.removeInput(it) }
    }
    val position = if (facing == OGCameraFacing.FRONT) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack
    val discovery = AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
        deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
        mediaType = AVMediaTypeVideo,
        position = position,
    )
    val device = (discovery.devices.firstOrNull() as? AVCaptureDevice)
        ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)

    if (device == null) {
        onError?.invoke("No $facing camera device")
        session.commitConfiguration()
        return
    }
    val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
    if (input != null && session.canAddInput(input)) {
        session.addInput(input)
    } else {
        onError?.invoke("Cannot add camera input")
    }
    session.commitConfiguration()
}
