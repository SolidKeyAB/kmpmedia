package com.solidkey.painpoints.image.gif

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.source.OGSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Android implementation of [rememberOGAnimatedPainter]. Decodes the GIF bytes off the main thread
 * into a [Drawable] (a self-animating [AnimatedImageDrawable] on API 28+, otherwise a static
 * first-frame bitmap), then wraps it in a [DrawablePainter] created inside [remember] so the
 * drawable's animation is started/stopped with the composition lifecycle.
 */
@Composable
actual fun rememberOGAnimatedPainter(
    source: OGSource,
    loop: Boolean,
    speed: Float,
    onError: ((String) -> Unit)?
): Painter? {
    val context = LocalContext.current

    val decoded by produceState<Drawable?>(null, source, loop) {
        value = withContext(Dispatchers.IO) {
            try {
                val bytes = readGifBytes(source, context)
                if (bytes == null) {
                    onError?.invoke("Failed to load GIF: $source")
                    null
                } else {
                    decodeAnimatedDrawable(bytes, loop)
                }
            } catch (e: Throwable) {
                Logger.e("OG>> GIF decode failed: ${e.message}")
                onError?.invoke("GIF decode failed: ${e.message}")
                null
            }
        }
    }

    val drawable = decoded
    // Created inside remember(drawable) so DrawablePainter (a RememberObserver) receives
    // onRemembered/onForgotten → it starts the animation when shown and stops it when gone.
    return remember(drawable) { drawable?.let { DrawablePainter(it) } }
}

private fun decodeAnimatedDrawable(bytes: ByteArray, loop: Boolean): Drawable =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeWithImageDecoder(bytes, loop)
    } else {
        // API 24–27: no ImageDecoder / AnimatedImageDrawable — show the static first frame.
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Could not decode image first frame")
        BitmapDrawable(null, bitmap)
    }

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeWithImageDecoder(bytes: ByteArray, loop: Boolean): Drawable {
    val src = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
    val drawable = ImageDecoder.decodeDrawable(src)
    if (drawable is AnimatedImageDrawable) {
        drawable.repeatCount =
            if (loop) AnimatedImageDrawable.REPEAT_INFINITE else 0
    }
    return drawable
}

/** Fetch the raw bytes of [source] (URL / file / `res/raw` or `res/drawable`). */
private fun readGifBytes(source: OGSource, context: Context): ByteArray? = when (source) {
    is OGSource.Url -> readUrlBytes(source.url)
    is OGSource.FilePath -> File(source.path).takeIf { it.exists() }?.readBytes()
    is OGSource.Resource -> {
        val resources = context.resources
        // Prefer res/raw (the recommended home for a .gif); fall back to drawable.
        val rawId = resources.getIdentifier(source.resource, "raw", context.packageName)
        val id = if (rawId != 0) rawId
        else resources.getIdentifier(source.resource, "drawable", context.packageName)
        if (id != 0) resources.openRawResource(id).use { it.readBytes() } else null
    }
}

private fun readUrlBytes(url: String): ByteArray? = try {
    (URL(url).openConnection() as HttpURLConnection).apply {
        doInput = true
        connect()
    }.inputStream.use { it.readBytes() }
} catch (e: Exception) {
    Logger.e("OG>> GIF url fetch failed: ${e.message}")
    null
}

/**
 * Renders an animating [Drawable] as a Compose [Painter]. Mirrors the well-known Accompanist
 * `DrawablePainter`: it registers a [Drawable.Callback] that bumps a snapshot counter every time
 * the drawable invalidates itself (i.e. every animation frame), and reads that counter inside
 * [onDraw], so the frame is redrawn without any external animation clock.
 */
private class DrawablePainter(val drawable: Drawable) : Painter(), RememberObserver {
    private var invalidateTick by mutableIntStateOf(0)
    private var drawableSize by mutableStateOf(drawable.intrinsicSizeOrUnspecified())

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val callback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) {
            invalidateTick++
            drawableSize = who.intrinsicSizeOrUnspecified()
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            mainHandler.postAtTime(what, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            mainHandler.removeCallbacks(what)
        }
    }

    override val intrinsicSize: Size get() = drawableSize

    override fun applyAlpha(alpha: Float): Boolean {
        drawable.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        drawable.colorFilter = colorFilter?.asAndroidColorFilter()
        return true
    }

    override fun onRemembered() {
        drawable.callback = callback
        drawable.setVisible(true, true)
        (drawable as? Animatable)?.start()
    }

    override fun onAbandoned() = cleanup()
    override fun onForgotten() = cleanup()

    private fun cleanup() {
        (drawable as? Animatable)?.stop()
        drawable.setVisible(false, false)
        drawable.callback = null
    }

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            // Reading the tick here subscribes this draw to invalidations → next frame redraws.
            @Suppress("UNUSED_EXPRESSION")
            invalidateTick
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }
}

private fun Drawable.intrinsicSizeOrUnspecified(): Size =
    if (intrinsicWidth > 0 && intrinsicHeight > 0)
        Size(intrinsicWidth.toFloat(), intrinsicHeight.toFloat())
    else Size.Unspecified
