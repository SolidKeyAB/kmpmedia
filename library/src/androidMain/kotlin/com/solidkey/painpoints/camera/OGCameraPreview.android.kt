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
    rotationOverride: Int?,
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
        // One long-lived session for the whole composable. It switches camera *in place* when
        // `facing` changes — the TextureView's SurfaceTexture is created only once, so
        // onSurfaceTextureAvailable never fires again on a toggle; the facing change has to be
        // driven from the recomposition (`update`) instead, or the front camera would never start.
        val session = remember { OGCamera2Session(context, onError) }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) =
                            session.onSurfaceAvailable(this@apply)
                        override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) =
                            session.applyTransform(this@apply)
                        override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean {
                            session.onSurfaceDestroyed(); return true
                        }
                        // Re-apply the fill/rotate transform as soon as real frames flow — by then
                        // the view is laid out, so this is immune to the onConfigured post() racing
                        // an unmeasured view (which would leave the raw, sideways preview on screen).
                        override fun onSurfaceTextureUpdated(s: SurfaceTexture) =
                            session.onFrame(this@apply)
                    }
                }
            },
            // Runs on every recomposition: pick up a facing / mirror / rotation change and restart
            // (or just re-transform) on the existing surface.
            update = { view -> session.setFacing(facing, mirror, rotationOverride, view) },
        )
        DisposableEffect(Unit) { onDispose { session.close() } }
    }
}

/** Self-contained Camera2 preview session — opens a camera, runs a repeating preview into a TextureView. */
private class OGCamera2Session(
    private val context: Context,
    private val onError: ((String) -> Unit)?,
) {
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSize = Size(1280, 720)
    private var sensorOrientation = 0

    private var facing: OGCameraFacing = OGCameraFacing.BACK
    private var mirror: Boolean = false
    /** When non-null, forces the preview rotation (deg CW) instead of the auto sensor-orientation math. */
    private var rotationOverride: Int? = null
    private var textureView: TextureView? = null
    /** Cleared whenever the camera (re)starts; the first frame re-applies the transform once. */
    private var transformApplied = false

    /** The preview surface is ready: remember it and open the current camera. */
    fun onSurfaceAvailable(view: TextureView) {
        textureView = view
        open()
    }

    fun onSurfaceDestroyed() {
        closeCamera()
        textureView = null
    }

    /** Drive the live camera/mirror/rotation from recomposition; restart in place when it changes. */
    fun setFacing(newFacing: OGCameraFacing, newMirror: Boolean, newRotationOverride: Int?, view: TextureView) {
        textureView = view
        val restart = newFacing != facing || newMirror != mirror
        val reTransform = newRotationOverride != rotationOverride
        facing = newFacing
        mirror = newMirror
        rotationOverride = newRotationOverride
        if (!view.isAvailable) return // onSurfaceAvailable will open() with these values
        when {
            restart -> { closeCamera(); open() }
            device == null -> open()
            // Just the rotation override changed: re-apply the transform live, no camera restart.
            reTransform && view.width > 0 && view.height > 0 -> applyTransform(view)
        }
    }

    /** Re-apply the fill/rotate transform once real frames arrive (the view is laid out by then). */
    fun onFrame(view: TextureView) {
        if (!transformApplied) applyTransform(view)
    }

    @SuppressLint("MissingPermission") // permission is checked by the composable before open()
    private fun open() {
        val view = textureView ?: return
        if (!view.isAvailable) return
        if (device != null) return
        transformApplied = false
        if (thread == null) {
            thread = HandlerThread("OGCamera").also { it.start() }
            handler = Handler(thread!!.looper)
        }
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
                    createPreview(camera, view)
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
                    if (device == null) { session.close(); return } // camera was torn down mid-config
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
        transformApplied = true
    }

    private fun totalRotationDegrees(): Int {
        rotationOverride?.let { return ((it % 360) + 360) % 360 }
        // Only compensate for DEVICE rotation away from natural. The sensor orientation is already
        // applied to the preview Surface by the platform, so folding it in here double-rotates (it
        // put a portrait-natural device's feed 90° sideways for both cameras). This mirrors Google's
        // Camera2Basic `configureTransform`: no rotation at ROTATION_0, display-delta otherwise.
        // Verified on-device: both front and back read upright at 0° in portrait.
        val display = when (currentDisplayRotation()) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return (360 - display) % 360
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

    /** Tear down just the camera + session (kept when switching facing). */
    private fun closeCamera() {
        try {
            captureSession?.close()
            device?.close()
        } catch (e: Exception) {
            Logger.w("OG>> camera close: ${e.message}")
        } finally {
            captureSession = null
            device = null
            transformApplied = false
        }
    }

    /** Full dispose: camera + background thread (when the composable leaves). */
    fun close() {
        closeCamera()
        try {
            thread?.quitSafely()
        } catch (e: Exception) {
            Logger.w("OG>> camera thread stop: ${e.message}")
        } finally {
            thread = null
            handler = null
            textureView = null
        }
    }
}
