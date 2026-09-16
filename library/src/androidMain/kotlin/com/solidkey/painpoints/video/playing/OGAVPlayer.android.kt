package com.solidkey.painpoints.video.playing

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.TextureView
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.solidkey.painpoints.source.OGSource
import com.solidkey.painpoints.source.OGSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
actual fun OGAVPlayer(
    action: OGAVPlayerAction,
    source: OGSourceType,
    config: OGPlayerConfig,
    flipHorizontally: Boolean,
    modifier: Modifier,
    onResetConfirm: (() -> Boolean)?,
    onCustomizeControls: (@Composable (OGAVPlayerState, (OGAVPlayerAction) -> Unit) -> Unit)?,
    controller: OGAVPlayerController?,
    onError: (DAVPlayerError) -> Unit, // ✅ Callback for immediate error handling
    onProgress: ((OGPlaybackStatus) -> Unit)?, // ✅ Live playback status
    cues: List<OGCue> // ✅ Timed triggers fired off the playback position
) : OGAVPlayerResult {

    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    var showControls by remember { mutableStateOf(true) }
    var currentState by remember { mutableStateOf(OGAVPlayerState.STOPPED) }
    var offset by remember { mutableStateOf(config.initialOffset) }
    // Video's pixel aspect ratio (w/h), reported by ExoPlayer once the media is ready. Drives the
    // FIT/FILL scaling math below.
    var videoAspect by remember { mutableStateOf(0f) }

    val location = source.getLocation()
    if (location == null) {
        return OGAVPlayerResult.Error(DAVPlayerError.SourceNotFound(source = source))
    }
    
    // Build a real playable Uri. NOTE: do NOT use OGSource.*.toString() here — those
    // toString()s are human-readable ("URL: https://…", "Resource: …"), so feeding them to
    // ExoPlayer produced `MalformedURLException: no protocol` and URL playback never worked.
    val videoUri: Uri = when (source.getType()) {
        OGSourceType.SourceType.URL -> Uri.parse(location)
        OGSourceType.SourceType.FILE -> Uri.parse(location)
        OGSourceType.SourceType.RESOURCE -> {
            val resId = context.resources.getIdentifier(location, "raw", context.packageName)
            Uri.parse("android.resource://${context.packageName}/$resId")
        }
    }

    // ✅ Observe `source` changes and reload video when `source` updates
    LaunchedEffect(source) {
        try {
//            val videoUri = when (source) {
//                is OGSource.FilePath -> Uri.parse(source.path)
//                is OGSource.Url -> Uri.parse(source.url)
//                is OGSource.Resource -> {
//                    val resId = context.resources.getIdentifier(source.resource, "raw", context.packageName)
//                    if (resId == 0) throw IllegalArgumentException("Resource not found: ${source.resource}")
//                    Uri.parse("android.resource://${context.packageName}/$resId")
//                }
//            } ?: throw IllegalArgumentException("Invalid video source")

            // ✅ Ensure safe media switching
            withContext(Dispatchers.Main) {
                exoPlayer.run {
                    stop()
                    clearMediaItems()
                    setMediaItem(MediaItem.fromUri(videoUri))
                    prepare()
                    playWhenReady = config.playbackConfig.autoStart
                }
            }
        } catch (e: Exception) {
            Log.e("DAVPlayer", "Error loading video: ${e.message}")
            onError(DAVPlayerError.PlaybackError(e))
        }
    }
    // ✅ Honor the caller's `action` (PLAY/PAUSE/STOP/REWIND) so it actually drives the
    // player — mirrors the iOS actual (LaunchedEffect(action)). Previously Android ignored
    // `action` entirely, so passing OGAVPlayerAction.PLAY did nothing and the video sat on a
    // frozen first frame unless autoStart was set. Keyed on `source` too so the action
    // re-applies after a media reload.
    LaunchedEffect(action, source) {
        withContext(Dispatchers.Main) {
            handlePlayerAction(exoPlayer, action) { newState -> currentState = newState }
        }
    }
    // ✅ Actually honor autoRepeat. Previously repeatMode was never set on the player, so the clip
    // played once and stopped (Loop was a no-op, and Play couldn't restart an ended clip).
    LaunchedEffect(config.playbackConfig.autoRepeat) {
        exoPlayer.repeatMode =
            if (config.playbackConfig.autoRepeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }
    // ✅ Forward asynchronous ExoPlayer playback errors (network/decode/etc.) to onError.
    // Setup-time failures are already handled in the LaunchedEffect above; these fire later.
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("DAVPlayer", "ExoPlayer playback error: ${error.message}")
                onError(DAVPlayerError.PlaybackError(error))
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }
    // ✅ Bind the detached controller so external buttons (rendered anywhere, even outside this
    // component) drive the real ExoPlayer directly — robust for repeats (e.g. rewind twice) and
    // independent of recomposition, unlike the declarative `action` param.
    DisposableEffect(controller) {
        controller?.handler = { act ->
            handlePlayerAction(exoPlayer, act) { newState -> currentState = newState }
        }
        // ✅ Absolute seek in ms — powers controller.seekTo/seekBy (scrubber drag, ±10s buttons,
        // double-tap seek). ExoPlayer.seekTo takes ms and must run on the player's (main) thread,
        // which is where the controller's callbacks fire.
        controller?.seekHandler = { positionMs -> exoPlayer.seekTo(positionMs) }
        onDispose {
            controller?.handler = null
            controller?.seekHandler = null
            controller?.updateStatus(OGPlaybackStatus()) // reset to zero-state on detach
        }
    }
    // ✅ Live playback status: poll ExoPlayer ~5 Hz and push position/duration/isPlaying/buffered to
    // the controller (reactive `status`) and/or the onProgress callback. ExoPlayer has no position
    // callback, so a light poll is the standard approach. It runs ONLY while a consumer is attached
    // (controller or onProgress) — otherwise there is zero overhead. The loop body runs on the
    // Compose main dispatcher (LaunchedEffect), the thread ExoPlayer must be read from.
    val currentOnProgress by rememberUpdatedState(onProgress)
    // ✅ Cue/timer engine — fires timed triggers (chapter markers, hotspots) off the SAME position
    // sample below. Pure common code, no platform work; kept in sync with the live `cues` list.
    val cueEngine = remember { OGCueEngine() }
    LaunchedEffect(cues) { cueEngine.setCues(cues) }
    if (controller != null || onProgress != null || cues.isNotEmpty()) {
        LaunchedEffect(exoPlayer, controller) {
            while (true) {
                val rawDuration = exoPlayer.duration
                val status = OGPlaybackStatus(
                    isPlaying = exoPlayer.isPlaying,
                    positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    durationMs = if (rawDuration == C.TIME_UNSET || rawDuration < 0L) 0L else rawDuration,
                    bufferedMs = exoPlayer.bufferedPosition.coerceAtLeast(0L),
                )
                controller?.updateStatus(status)
                currentOnProgress?.invoke(status)
                cueEngine.onTick(status.positionMs)
                delay(OG_POSITION_POLL_MS)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer.release()
            } catch (e: Exception) {
                Log.e("DAVPlayer", "Error releasing ExoPlayer: ${e.message}")
                onError(DAVPlayerError.PlaybackError(e))
            }
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .clip(config.shape)
            .background(config.backgroundColor)
            .pointerInput(config.displayMovable) {
                if (config.displayMovable) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset += dragAmount
                    }
                }
            }
            .clickable { showControls = !showControls },
        contentAlignment = Alignment.Center
    ) {
        // Render the video through a TextureView (not PlayerView's default SurfaceView).
        // A SurfaceView lives in its own hardware layer: it ignores the Compose .clip(shape)
        // (rounded corners / triangle / diamond had no effect), renders as an opaque black
        // punch-through under a software GPU, and z-fights overlays. A TextureView draws INSIDE
        // the Compose hierarchy, so it honors the clip, the flip transform, and composites normally.
        //
        // FIT vs FILL is done with View-level scaleX/scaleY on the TextureView (the same, proven-safe
        // mechanism as the horizontal flip). We deliberately do NOT use AspectRatioFrameLayout's
        // RESIZE_MODE_ZOOM — it stalls the TextureView to black under a software GPU. The container's
        // clipChildren=false lets a FILL-scaled video overflow; the outer Compose .clip(shape) makes
        // the final (possibly non-rectangular) crop.
        AndroidView(
            factory = { ctx ->
                val container = FrameLayout(ctx).apply {
                    clipChildren = false
                    clipToPadding = false
                    setBackgroundColor(config.backgroundColor.toArgb())
                }
                // isOpaque=false is REQUIRED for the backdrop to show. When FIT letterboxes,
                // we scale the TextureView DOWN (scaleX/scaleY) so the FrameLayout's
                // backgroundColor shows in the vacated bands. But a TextureView is opaque by
                // default, which tells the hardware compositor it fully covers its (MATCH_PARENT)
                // bounds — so on a real GPU the parent's background is NOT redrawn behind it and
                // the letterbox stays stale/black no matter what backgroundColor is set to. (The
                // emulator's swiftshader software GPU redraws every frame, which masked this — it
                // looked fine there but never updated on device.) Transparent = parent bg blends
                // through the empty areas.
                val textureView = TextureView(ctx).apply { isOpaque = false }
                container.addView(
                    textureView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                exoPlayer.setVideoTextureView(textureView)
                exoPlayer.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        videoAspect =
                            if (videoSize.width == 0 || videoSize.height == 0) 0f
                            else videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                    }
                })
                container
            },
            update = { container ->
                container.setBackgroundColor(config.backgroundColor.toArgb())
                val tv = container.getChildAt(0) as? TextureView
                if (tv != null) {
                    // The TextureView is MATCH_PARENT, so ExoPlayer stretches the frame to the box.
                    // Correct the aspect (and letterbox/crop) purely with a uniform-per-axis scale.
                    val bw = container.width.toFloat()
                    val bh = container.height.toFloat()
                    var sx = 1f
                    var sy = 1f
                    if (videoAspect > 0f && bw > 0f && bh > 0f) {
                        val boxAspect = bw / bh
                        if (config.contentScale == OGVideoScale.FILL) {
                            // Cover the box (crop the overflow).
                            if (videoAspect > boxAspect) sx = videoAspect / boxAspect
                            else sy = boxAspect / videoAspect
                        } else {
                            // Fit inside the box (letterbox the remainder).
                            if (videoAspect > boxAspect) sy = boxAspect / videoAspect
                            else sx = videoAspect / boxAspect
                        }
                    }
                    tv.scaleX = sx * (if (flipHorizontally) -1f else 1f)
                    tv.scaleY = sy
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Resolve the overlay: a custom overlay wins; otherwise DefaultControls ONLY if the caller
        // opted in via config.showDefaultControls. Neither → the player is fully headless (drive it
        // with the `action` param or an OGAVPlayerController). Previously DefaultControls was always
        // used as a fallback, so a chrome-less player was impossible.
        val overlay: (@Composable (OGAVPlayerState, (OGAVPlayerAction) -> Unit) -> Unit)? =
            onCustomizeControls
                ?: if (config.showDefaultControls) { state, onAction -> DefaultControls(state, onAction) } else null

        if (showControls && overlay != null) {
            Box(
                modifier = Modifier
                    .align(config.controlPosition ?: Alignment.BottomCenter)
                    .zIndex(3f), // ✅ Ensure above player
                contentAlignment = config.controlPosition ?: Alignment.BottomCenter
            ) {
                overlay.invoke(currentState) { action ->
                    handlePlayerAction(exoPlayer, action) { newState ->
                        currentState = newState
                    }
                }
            }
        }
    }

    return OGAVPlayerResult.Success
}

// ✅ ExoPlayer with TextureView, NO SurfaceView
private fun createExoPlayerWithTextureView(context: Context, source: OGSource, config: OGVideoPlaybackConfig): ExoPlayer {
    val exoPlayer = ExoPlayer.Builder(context).build()
    val videoUri = when (source) {
        is OGSource.FilePath -> Uri.parse(source.path)
        is OGSource.Url -> Uri.parse(source.url)
        is OGSource.Resource -> {
            val resId = context.resources.getIdentifier(source.resource, "raw", context.packageName)
            Uri.parse("android.resource://${context.packageName}/$resId")
        }
    }
    exoPlayer.setMediaItem(MediaItem.Builder().setUri(videoUri).build())
    exoPlayer.playWhenReady = config.autoStart
    exoPlayer.clearVideoSurface()
    exoPlayer.prepare()
    return exoPlayer
}

// ✅ Handles video player actions
private fun handlePlayerAction(
    player: ExoPlayer,
    action: OGAVPlayerAction,
    updateState: (OGAVPlayerState) -> Unit
) {
    val internalAction = when (action) {
        OGAVPlayerAction.PLAY -> OGAVPlayerInternalAction.PLAY
        OGAVPlayerAction.PAUSE -> OGAVPlayerInternalAction.PAUSE
        OGAVPlayerAction.STOP -> OGAVPlayerInternalAction.STOP
        OGAVPlayerAction.REWIND -> OGAVPlayerInternalAction.REWIND // ✅ Map REWIND
    }

    executePlayerAction(player, internalAction, updateState)
}

private fun executePlayerAction(
    player: ExoPlayer,
    action: OGAVPlayerInternalAction,
    updateState: (OGAVPlayerState) -> Unit
) {
    when (action) {
        OGAVPlayerInternalAction.PLAY -> {
            player.play()
            updateState(OGAVPlayerState.PLAYING)
        }
        OGAVPlayerInternalAction.PAUSE -> {
            player.pause()
            updateState(OGAVPlayerState.PAUSED)
        }
        OGAVPlayerInternalAction.STOP -> {
            player.pause()
            player.seekTo(0)
            updateState(OGAVPlayerState.STOPPED)
        }
        OGAVPlayerInternalAction.REWIND -> { // ✅ New REWIND action
            player.seekTo(0) // ✅ Move to the beginning
            updateState(OGAVPlayerState.STOPPED) // ✅ Transition to STOPPED state
        }
        OGAVPlayerInternalAction.RESET -> { // ✅ Internal only
            player.stop()
            player.seekTo(0)
            updateState(OGAVPlayerState.RESET)
        }
    }
}



private fun createExoPlayer(context: Context, source: OGSource, config: OGPlayerConfig): ExoPlayer {
    val exoPlayer = ExoPlayer.Builder(context).build()
    val videoUri = when (source) {
        is OGSource.FilePath -> Uri.parse(source.path)
        is OGSource.Url -> Uri.parse(source.url)
        is OGSource.Resource -> {
            val resId = context.resources.getIdentifier(source.resource, "raw", context.packageName)
            Uri.parse("android.resource://${context.packageName}/$resId")
        }
    }
    exoPlayer.setMediaItem(MediaItem.Builder().setUri(videoUri).build())

    exoPlayer.playWhenReady = config.playbackConfig.autoStart
    exoPlayer.repeatMode = if (config.playbackConfig.autoRepeat) ExoPlayer.REPEAT_MODE_ALL else ExoPlayer.REPEAT_MODE_OFF

    exoPlayer.prepare()
    return exoPlayer
}


actual enum class OGAVPlayerState {
    STOPPED,
    PLAYING,
    PAUSED,
    RESET;
}

actual enum class OGAVPlayerAction {
    STOP,
    PLAY,
    PAUSE,
    REWIND;
}

