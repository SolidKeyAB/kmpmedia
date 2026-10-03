package com.solidkey.painpoints.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger

@Composable
actual fun OGCameraPreview(
    modifier: Modifier,
    shape: Shape,
    facing: OGCameraFacing,
    mirror: Boolean,
    onError: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    Box(modifier = modifier.clip(shape)) {
        if (!granted) {
            LaunchedEffect(Unit) { onError?.invoke("Camera permission not granted") }
            return@Box
        }
        // Re-create the session when the camera to show changes.
        val session = remember(facing) { OGCamera2Session(context, facing, mirror, onError) }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) =
                            session.start(this@apply)
                        override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) =
                            session.applyTransform(this@apply)
                        override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean {
                            session.close(); return true
                        }
                        override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
                    }
                }
            },
        )
        DisposableEffect(facing) { onDispose { session.close() } }
    }
}

/** Self-contained Camera2 preview session — opens a camera, runs a repeating preview into a TextureView. */
private class OGCamera2Session(
    private val context: Context,
    private val facing: OGCameraFacing,
    private val mirror: Boolean,
    private val onError: ((String) -> Unit)?,
) {
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSize = Size(1280, 720)
    private var sensorOrientation = 0

    @SuppressLint("MissingPermission") // permission is checked by the composable before start()
    fun start(textureView: TextureView) {
        if (device != null) return
        thread = HandlerThread("OGCamera").also { it.start() }
        handler = Handler(thread!!.looper)
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val id = pickCameraId(manager) ?: run { onError?.invoke("No $facing camera found"); return }
            val chars = manager.getCameraCharacteristics(id)
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)
            previewSize = choosePreviewSize(sizes)
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device = camera
                    createPreview(camera, textureView)
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); device = null }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close(); device = null; onError?.invoke("Camera device error $error")
                }
            }, handler)
        } catch (e: Exception) {
            Logger.e("OG>> camera open failed: ${e.message}")
            onError?.invoke("Camera open failed: ${e.message}")
        }
    }

    private fun createPreview(camera: CameraDevice, textureView: TextureView) {
        try {
            val texture = textureView.surfaceTexture ?: return
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val surface = Surface(texture)
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            @Suppress("DEPRECATION")
            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        session.setRepeatingRequest(builder.build(), null, handler)
                        textureView.post { applyTransform(textureView) }
                    } catch (e: Exception) {
                        onError?.invoke("Camera preview failed: ${e.message}")
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    onError?.invoke("Camera session configuration failed")
                }
            }, handler)
        } catch (e: Exception) {
            onError?.invoke("Camera preview failed: ${e.message}")
        }
    }

    /** Rotate to upright + center-crop fill the view, mirroring the front camera. */
    fun applyTransform(textureView: TextureView) {
        val vw = textureView.width.toFloat()
        val vh = textureView.height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        val rotation = totalRotationDegrees()
        val swap = rotation % 180 != 0
        val cx = vw / 2f
        val cy = vh / 2f
        val previewW = previewSize.width.toFloat()
        val previewH = previewSize.height.toFloat()
        val contentW = if (swap) previewH else previewW
        val contentH = if (swap) previewW else previewH
        val fill = maxOf(vw / contentW, vh / contentH)

        val matrix = Matrix().apply {
            // 1) undo TextureView's default buffer→view stretch so the content is at its native aspect
            setScale(previewW / vw, previewH / vh, cx, cy)
            // 2) rotate the sensor buffer upright
            postRotate(rotation.toFloat(), cx, cy)
            // 3) center-crop fill the view
            postScale(fill, fill, cx, cy)
            // 4) front camera: mirror horizontally
            if (mirror) postScale(-1f, 1f, cx, cy)
        }
        textureView.setTransform(matrix)
    }

    private fun totalRotationDegrees(): Int {
        val display = when (currentDisplayRotation()) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (facing == OGCameraFacing.FRONT) {
            (sensorOrientation + display) % 360
        } else {
            (sensorOrientation - display + 360) % 360
        }
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int {
        val wm = (context as? Activity)?.windowManager
            ?: context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return wm.defaultDisplay.rotation
    }

    private fun pickCameraId(manager: CameraManager): String? {
        val want = if (facing == OGCameraFacing.FRONT) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        return manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == want
        } ?: manager.cameraIdList.firstOrNull()
    }

    /** Prefer a ~720p-ish size to keep the preview cheap; fall back to the first available. */
    private fun choosePreviewSize(sizes: Array<Size>?): Size {
        if (sizes.isNullOrEmpty()) return Size(1280, 720)
        return sizes.filter { it.width <= 1920 && it.height <= 1080 }
            .maxByOrNull { it.width.toLong() * it.height } ?: sizes.first()
    }

    fun close() {
        try {
            captureSession?.close()
            device?.close()
            thread?.quitSafely()
        } catch (e: Exception) {
            Logger.w("OG>> camera close: ${e.message}")
        } finally {
            captureSession = null
            device = null
            thread = null
            handler = null
        }
    }
}
