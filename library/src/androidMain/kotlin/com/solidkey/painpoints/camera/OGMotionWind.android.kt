package com.solidkey.painpoints.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import java.nio.ByteBuffer

@Composable
internal actual fun OGMotionWindCameraEffect(
    spec: OGMotionWindSpec,
    enabled: Boolean,
    onLumaFrame: (ByteArray, Long) -> Unit,
    onError: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    DisposableEffect(spec, enabled) {
        val session = if (enabled) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                onError?.invoke("Camera permission not granted")
                null
            } else {
                OGMotionWindCamera2Session(context, spec, onLumaFrame, onError).also { it.start() }
            }
        } else null
        onDispose { session?.close() }
    }
}

/** Headless Camera2 session: opens camera → ImageReader YUV → Y-plane downsample → upright luma. */
private class OGMotionWindCamera2Session(
    private val context: Context,
    private val spec: OGMotionWindSpec,
    private val onLumaFrame: (ByteArray, Long) -> Unit,
    private val onError: ((String) -> Unit)?,
) {
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var sensorOrientation = 0
    private var isFacingFront = false
    private var lastEmitNanos = 0L
    private val minFrameIntervalNanos = 1_000_000_000L / spec.targetFps

    @SuppressLint("MissingPermission")
    fun start() {
        val t = HandlerThread("OGMotionWind").also { it.start() }
        thread = t
        handler = Handler(t.looper)

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = pickCameraId(manager, spec.facing) ?: run {
            onError?.invoke("No ${spec.facing} camera found"); return
        }
        val chars = manager.getCameraCharacteristics(cameraId)
        sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        isFacingFront = chars.get(CameraCharacteristics.LENS_FACING) ==
            CameraCharacteristics.LENS_FACING_FRONT

        // Smallest YUV size with enough pixels to downsample from
        val sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.YUV_420_888)
        val srcSize = sizes?.filter { it.width >= 160 && it.height >= 120 }
            ?.minByOrNull { it.width.toLong() * it.height }
            ?: sizes?.minByOrNull { it.width.toLong() * it.height }
            ?: android.util.Size(320, 240)

        imageReader = ImageReader.newInstance(srcSize.width, srcSize.height,
            ImageFormat.YUV_420_888, 2).apply {
            setOnImageAvailableListener(ImageReader.OnImageAvailableListener { reader ->
                val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
                try {
                    val t = image.timestamp
                    if (t - lastEmitNanos >= minFrameIntervalNanos) {
                        lastEmitNanos = t
                        val plane = image.planes[0]
                        val luma = extractUprightLuma(
                            src = plane.buffer,
                            rowStride = plane.rowStride,
                            pixelStride = plane.pixelStride,
                            srcW = image.width,
                            srcH = image.height,
                            dstW = spec.width,
                            dstH = spec.height,
                            rotationDeg = uprightRotationDegrees(),
                        )
                        onLumaFrame(luma, t)
                    }
                } finally {
                    image.close()
                }
            }, handler)
        }

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(cam: CameraDevice) {
                device = cam
                val surface = imageReader!!.surface
                val builder = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(surface)
                    try {
                        set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            Range(spec.targetFps, spec.targetFps))
                    } catch (_: Exception) { /* unsupported range — throttle by timestamp */ }
                }
                @Suppress("DEPRECATION")
                cam.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        captureSession = s
                        try { s.setRepeatingRequest(builder.build(), null, handler) }
                        catch (e: Exception) { onError?.invoke("OGMotionWind: repeating request failed: ${e.message}") }
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        onError?.invoke("OGMotionWind: session config failed")
                    }
                }, handler)
            }
            override fun onDisconnected(cam: CameraDevice) { cam.close(); device = null }
            override fun onError(cam: CameraDevice, error: Int) {
                cam.close(); device = null
                onError?.invoke("OGMotionWind: camera error $error")
            }
        }, handler)
    }

    /**
     * Nearest-neighbor downsample from sensor-landscape into upright dstW×dstH luma.
     * ImageReader buffers are raw-sensor-orientation: without this remap, horizontal
     * hand waves register as Y-axis motion and directionX stays near 0 in portrait.
     *
     * Index via absolute ByteBuffer.get(index) — avoids rowStride/pixelStride padding traps
     * and doesn't move the buffer position.
     */
    private fun extractUprightLuma(
        src: ByteBuffer, rowStride: Int, pixelStride: Int,
        srcW: Int, srcH: Int, dstW: Int, dstH: Int, rotationDeg: Int,
    ): ByteArray {
        val out = ByteArray(dstW * dstH)
        for (dy in 0 until dstH) {
            for (dx in 0 until dstW) {
                val u = (dx + 0.5f) / dstW
                val v = (dy + 0.5f) / dstH
                // Inverse-rotate from upright display back into sensor space
                val (su, sv) = when (rotationDeg) {
                    90  -> v to (1f - u)
                    180 -> (1f - u) to (1f - v)
                    270 -> (1f - v) to u
                    else -> u to v
                }
                val sx = (su * srcW).toInt().coerceIn(0, srcW - 1)
                val sy = (sv * srcH).toInt().coerceIn(0, srcH - 1)
                out[dy * dstW + dx] = src.get(sy * rowStride + sx * pixelStride)
            }
        }
        return out
    }

    /**
     * Rotation needed to map sensor-landscape pixels to upright display orientation.
     * This folds in SENSOR_ORIENTATION (unlike OGCamera2Session which omits it, because
     * the preview Surface pre-applies it; ImageReader gets no such rotation — raw sensor only).
     */
    private fun uprightRotationDegrees(): Int {
        val display = when (currentDisplayRotation()) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (isFacingFront) (sensorOrientation + display) % 360
               else (sensorOrientation - display + 360) % 360
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int {
        val wm = (context as? Activity)?.windowManager
            ?: context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return wm.defaultDisplay.rotation
    }

    private fun pickCameraId(manager: CameraManager, facing: OGCameraFacing): String? {
        val want = if (facing == OGCameraFacing.FRONT) CameraCharacteristics.LENS_FACING_FRONT
                   else CameraCharacteristics.LENS_FACING_BACK
        return manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == want
        } ?: manager.cameraIdList.firstOrNull()
    }

    fun close() {
        try {
            captureSession?.close()
            device?.close()
            imageReader?.close()
        } catch (e: Exception) {
            Logger.w("OGMotionWind close: ${e.message}")
        } finally {
            captureSession = null
            device = null
            imageReader = null
        }
        thread?.quitSafely()
        thread = null
        handler = null
    }
}
