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
    onError: ((String) -> Unit)?,
) {
    val authorized = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) ==
        AVAuthorizationStatusAuthorized

    Box(modifier = modifier.clip(shape)) {
        if (!authorized) {
            LaunchedEffect(Unit) { onError?.invoke("Camera permission not granted") }
            return@Box
        }

        val session = remember(facing) { buildCaptureSession(facing, onError) }
        val previewLayer = remember(session) {
            AVCaptureVideoPreviewLayer(session = session).apply {
                videoGravity = AVLayerVideoGravityResizeAspectFill
                connection?.let {
                    if (it.isVideoMirroringSupported()) {
                        it.automaticallyAdjustsVideoMirroring = false
                        it.setVideoMirrored(mirror)
                    }
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

@OptIn(ExperimentalForeignApi::class)
private fun buildCaptureSession(facing: OGCameraFacing, onError: ((String) -> Unit)?): AVCaptureSession {
    val session = AVCaptureSession()
    session.beginConfiguration()
    session.sessionPreset = AVCaptureSessionPresetHigh
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
        return session
    }
    val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
    if (input != null && session.canAddInput(input)) {
        session.addInput(input)
    } else {
        onError?.invoke("Cannot add camera input")
    }
    session.commitConfiguration()
    return session
}
