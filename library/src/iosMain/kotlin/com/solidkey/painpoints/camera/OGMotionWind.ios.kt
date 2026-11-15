package com.solidkey.painpoints.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.readBytes
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset352x288
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.authorizationStatusForMediaType
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreVideo.CVImageBufferRef
import platform.CoreVideo.CVPixelBufferGetBaseAddressOfPlane
import platform.CoreVideo.CVPixelBufferGetBytesPerRowOfPlane
import platform.CoreVideo.CVPixelBufferGetHeightOfPlane
import platform.CoreVideo.CVPixelBufferGetWidthOfPlane
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange
import platform.Foundation.NSNumber
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun OGMotionWindCameraEffect(
    spec: OGMotionWindSpec,
    enabled: Boolean,
    onLumaFrame: (ByteArray, Long) -> Unit,
    onError: ((String) -> Unit)?,
) {
    DisposableEffect(spec, enabled) {
        var session: AVCaptureSession? = null
        var delegate: OGMotionWindDelegate? = null

        if (enabled) {
            val authorized = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) ==
                AVAuthorizationStatusAuthorized
            if (!authorized) {
                onError?.invoke("Camera permission not granted")
            } else {
                val queue = dispatch_queue_create("OGMotionWind", null)
                val d = OGMotionWindDelegate(spec, queue, onLumaFrame)
                delegate = d
                session = buildMotionWindSession(spec, d, queue, onError)
                session?.let { s ->
                    dispatch_async(dispatch_get_global_queue(0L, 0uL)) { s.startRunning() }
                }
            }
        }

        onDispose {
            val s = session
            dispatch_async(dispatch_get_global_queue(0L, 0uL)) { s?.stopRunning() }
            delegate?.invalidate()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun buildMotionWindSession(
    spec: OGMotionWindSpec,
    delegate: OGMotionWindDelegate,
    queue: dispatch_queue_t?,
    onError: ((String) -> Unit)?,
): AVCaptureSession? {
    val session = AVCaptureSession()
    session.beginConfiguration()
    session.sessionPreset = AVCaptureSessionPreset352x288

    val position = if (spec.facing == OGCameraFacing.FRONT) AVCaptureDevicePositionFront
                   else AVCaptureDevicePositionBack
    val discovery = AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
        deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
        mediaType = AVMediaTypeVideo,
        position = position,
    )
    val device = (discovery.devices.firstOrNull() as? AVCaptureDevice)
        ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)

    if (device == null) {
        onError?.invoke("OGMotionWind: no ${spec.facing} camera")
        session.commitConfiguration()
        return null
    }

    // Lock device to target FPS
    try {
        device.lockForConfiguration(null)
        val duration = platform.CoreMedia.CMTimeMake(1L, spec.targetFps)
        device.activeVideoMinFrameDuration = duration
        device.unlockForConfiguration()
    } catch (_: Exception) { /* unsupported — timestamp throttle handles it */ }

    val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
    if (input != null && session.canAddInput(input)) session.addInput(input)

    @Suppress("UNCHECKED_CAST")
    val output = AVCaptureVideoDataOutput().apply {
        videoSettings = mapOf(
            kCVPixelBufferPixelFormatTypeKey to
                NSNumber(unsignedInt = kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange),
        ) as Map<Any?, *>
        alwaysDiscardsLateVideoFrames = true
        setSampleBufferDelegate(delegate, queue)
    }
    if (session.canAddOutput(output)) {
        session.addOutput(output)
        // Upright orientation so the extraction loop needs no rotation remap
        @Suppress("DEPRECATION")
        output.connectionWithMediaType(AVMediaTypeVideo)?.videoOrientation =
            AVCaptureVideoOrientationPortrait
    }

    session.commitConfiguration()
    return session
}

/** Sample-buffer delegate — extracts luma, downsamples, forwards to [OGMotionWindProcessor]. */
@OptIn(ExperimentalForeignApi::class)
private class OGMotionWindDelegate(
    private val spec: OGMotionWindSpec,
    private val queue: dispatch_queue_t?,
    private val onLumaFrame: (ByteArray, Long) -> Unit,
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {

    private var valid = true
    private var lastEmitNanos = 0L
    private val minFrameIntervalNanos = 1_000_000_000L / spec.targetFps

    fun invalidate() { valid = false }

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        if (!valid) return
        val sampleBuffer = didOutputSampleBuffer ?: return
        val pts = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        val tNanos = (CMTimeGetSeconds(pts) * 1_000_000_000.0).toLong()
        if (tNanos - lastEmitNanos < minFrameIntervalNanos) return
        lastEmitNanos = tNanos

        val imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) ?: return
        processImageBuffer(imageBuffer, tNanos)
    }

    private fun processImageBuffer(imageBuffer: CVImageBufferRef, tNanos: Long) {
        CVPixelBufferLockBaseAddress(imageBuffer, 1uL)  // 1 = read-only
        try {
            val srcW = CVPixelBufferGetWidthOfPlane(imageBuffer, 0uL).toInt()
            val srcH = CVPixelBufferGetHeightOfPlane(imageBuffer, 0uL).toInt()
            val bytesPerRow = CVPixelBufferGetBytesPerRowOfPlane(imageBuffer, 0uL).toInt()
            val baseAddr = CVPixelBufferGetBaseAddressOfPlane(imageBuffer, 0uL) ?: return

            // Read the full luma plane (stride-aware), then nearest-neighbour downsample
            val rawBytes = baseAddr.reinterpret<ByteVar>().readBytes(srcH * bytesPerRow)
            val luma = ByteArray(spec.width * spec.height)
            for (dy in 0 until spec.height) {
                val sy = ((dy + 0.5f) / spec.height * srcH).toInt().coerceIn(0, srcH - 1)
                val rowBase = sy * bytesPerRow
                for (dx in 0 until spec.width) {
                    val sx = ((dx + 0.5f) / spec.width * srcW).toInt().coerceIn(0, srcW - 1)
                    luma[dy * spec.width + dx] = rawBytes[rowBase + sx]
                }
            }
            onLumaFrame(luma, tNanos)
        } finally {
            CVPixelBufferUnlockBaseAddress(imageBuffer, 1uL)
        }
    }
}
