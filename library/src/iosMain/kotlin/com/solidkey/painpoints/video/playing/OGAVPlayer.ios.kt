// iosMain/kotlin/com/solidkey/painpoints/video/playing/OGAVPlayer.kt
package com.solidkey.painpoints.video.playing

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.solidkey.painpoints.source.OGSource
import com.solidkey.painpoints.source.OGSourceType
import platform.AVFoundation.*
import platform.Foundation.*
import platform.UIKit.*
import platform.CoreGraphics.*
import kotlinx.cinterop.*
import kotlinx.coroutines.delay
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.darwin.NSObject
import kotlin.math.roundToInt

actual enum class OGAVPlayerState {
    STOPPED, PLAYING, PAUSED, RESET
}

actual enum class OGAVPlayerAction {
    STOP, PLAY, PAUSE, REWIND
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun OGAVPlayer(
    action: OGAVPlayerAction,
    source: OGSourceType,
    config: OGPlayerConfig,
    flipHorizontally: Boolean,
    modifier: Modifier,
    onResetConfirm: (() -> Boolean)?,
    onCustomizeControls: @Composable ((OGAVPlayerState, (OGAVPlayerAction) -> Unit) -> Unit)?,
    controller: OGAVPlayerController?,
    onError: (DAVPlayerError) -> Unit,
    onProgress: ((OGPlaybackStatus) -> Unit)?, // ✅ Live playback status
    cues: List<OGCue> // ✅ Timed triggers fired off the playback position
): OGAVPlayerResult {
    var playerState by remember { mutableStateOf(OGAVPlayerState.STOPPED) }
    var offset by remember { mutableStateOf(config.initialOffset) }
    val avPlayer = remember { AVPlayer() }

    // FIT letterboxes (empty areas show backgroundColor); FILL crops to fill the shape.
    val gravity =
        if (config.contentScale == OGVideoScale.FILL) AVLayerVideoGravityResizeAspectFill
        else AVLayerVideoGravityResizeAspect
    val bg = config.backgroundColor
    val bgUiColor = UIColor(
        red = bg.red.toDouble(),
        green = bg.green.toDouble(),
        blue = bg.blue.toDouble(),
        alpha = bg.alpha.toDouble()
    )

    // ✅ Bind the detached controller so external buttons (rendered anywhere, even outside this
    // component) drive the AVPlayer directly — mirrors the Android actual.
    DisposableEffect(controller) {
        controller?.handler = { act ->
            when (act) {
                OGAVPlayerAction.PLAY -> { avPlayer.play(); playerState = OGAVPlayerState.PLAYING }
                OGAVPlayerAction.PAUSE -> { avPlayer.pause(); playerState = OGAVPlayerState.PAUSED }
                OGAVPlayerAction.STOP -> {
                    avPlayer.pause()
                    avPlayer.seekToTime(CMTimeMake(value = 0, timescale = 1))
                    playerState = OGAVPlayerState.STOPPED
                }
                OGAVPlayerAction.REWIND -> avPlayer.seekToTime(CMTimeMake(value = 0, timescale = 1))
            }
        }
        // ✅ Absolute seek in ms — powers controller.seekTo/seekBy. A 1000-timescale CMTime gives
        // millisecond precision, mirroring the Android actual.
        controller?.seekHandler = { positionMs ->
            avPlayer.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 1000))
        }
        onDispose {
            controller?.handler = null
            controller?.seekHandler = null
            controller?.updateStatus(OGPlaybackStatus()) // reset to zero-state on detach
        }
    }

    // ✅ Live playback status: poll AVPlayer ~5 Hz and push position/duration/isPlaying to the
    // controller (reactive `status`) and/or the onProgress callback — same shape as the Android
    // actual so the common API is identical. Runs ONLY while a consumer is attached (controller or
    // onProgress). (bufferedMs is left at 0 on iOS for now.)
    val currentOnProgress by rememberUpdatedState(onProgress)
    // ✅ Cue/timer engine — same pure common engine as the Android actual, fed off this poll.
    val cueEngine = remember { OGCueEngine() }
    LaunchedEffect(cues) { cueEngine.setCues(cues) }
    if (controller != null || onProgress != null || cues.isNotEmpty()) {
        LaunchedEffect(avPlayer, controller) {
            while (true) {
                val posSeconds = CMTimeGetSeconds(avPlayer.currentTime())
                val durSeconds = avPlayer.currentItem?.duration?.let { CMTimeGetSeconds(it) } ?: Double.NaN
                val status = OGPlaybackStatus(
                    isPlaying = avPlayer.rate != 0f,
                    positionMs = if (posSeconds.isNaN() || posSeconds < 0.0) 0L else (posSeconds * 1000).toLong(),
                    durationMs = if (durSeconds.isNaN() || durSeconds.isInfinite() || durSeconds < 0.0) 0L
                                 else (durSeconds * 1000).toLong(),
                    bufferedMs = 0L,
                )
                controller?.updateStatus(status)
                currentOnProgress?.invoke(status)
                cueEngine.onTick(status.positionMs)
                delay(OG_POSITION_POLL_MS)
            }
        }
    }

    val location = source.getLocation()
    if (location == null) {
        return OGAVPlayerResult.Error(DAVPlayerError.SourceNotFound(source = source))
    }


    val videoUri = when (source.getType()) {
        OGSourceType.SourceType.URL -> NSURL.URLWithString(location)
        OGSourceType.SourceType.FILE -> NSURL.fileURLWithPath(location)
        OGSourceType.SourceType.RESOURCE -> NSURL.fileURLWithPath(location)
    }



    // Handle source
    LaunchedEffect(source) {
//        val url = when (source.getType()) {
//            is OGSourceType.SourceType.Url -> NSURL(string = source.url)
//            else -> null // Extend for other types
//        }

        val playerItem = AVPlayerItem(uRL = videoUri!!)
        avPlayer.replaceCurrentItemWithPlayerItem(playerItem)

    }

    // Action handling
    LaunchedEffect(action) {
        when (action) {
            OGAVPlayerAction.PLAY -> {
                avPlayer.play()
                playerState = OGAVPlayerState.PLAYING
            }
            OGAVPlayerAction.PAUSE -> {
                avPlayer.pause()
                playerState = OGAVPlayerState.PAUSED
            }
            OGAVPlayerAction.STOP -> {
                avPlayer.pause()
                avPlayer.seekToTime(CMTimeMake(value = 0, timescale = 1))
                playerState = OGAVPlayerState.STOPPED
            }
            OGAVPlayerAction.REWIND -> {
                avPlayer.seekToTime(CMTimeMake(value = 0, timescale = 1))
            }
        }
    }

    // ✅ Honor displayShape (clip) + displayMovable (drag) via Compose modifiers, mirroring the
    // Android actual so behavior is consistent cross-platform. CMP manages the interop view's
    // layout, so clip/offset operate in Compose's coordinate space.
    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            // Respect the caller's modifier size (was hardcoded .size(250.dp), which ignored the
            // passed modifier — the same bug fixed on Android in v0.1.8).
            .clip(config.shape)
            .pointerInput(config.displayMovable) {
                if (config.displayMovable) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset += dragAmount
                    }
                }
            }
    ) {
        UIKitView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                val view = UIView(frame = CGRectZero.readValue())
                view.setClipsToBounds(true) // ✅ Clip the native player layer to the shaped bounds
                view.backgroundColor = bgUiColor // ✅ Letterbox / empty-area color
                val playerLayer = AVPlayerLayer.playerLayerWithPlayer(avPlayer)
                playerLayer.frame = view.bounds
                playerLayer.videoGravity = gravity // ✅ FIT (aspect) or FILL (aspectFill) per config
                playerLayer.backgroundColor = bgUiColor.CGColor
                if (flipHorizontally) {
                    view.transform = CGAffineTransformMakeScale(-1.0, 1.0)
                }
                view.layer.addSublayer(playerLayer)
                view
            },
            update = { view ->
                view.backgroundColor = bgUiColor
                (view.layer.sublayers?.firstOrNull() as? AVPlayerLayer)?.apply {
                    player = avPlayer
                    frame = view.bounds // ✅ Keep the player layer sized to the container
                    videoGravity = gravity // ✅ Honor live scale-mode changes
                    backgroundColor = bgUiColor.CGColor
                }
            }
        )
    }

    // Optional controls: a custom overlay wins; otherwise DefaultControls ONLY if the caller opted
    // in via config.showDefaultControls. Neither → fully headless (mirrors the Android actual).
    val overlay: (@Composable (OGAVPlayerState, (OGAVPlayerAction) -> Unit) -> Unit)? =
        onCustomizeControls
            ?: if (config.showDefaultControls) { state, onAction -> DefaultControls(state, onAction) } else null
    overlay?.invoke(playerState) { newAction ->
        when (newAction) {
            OGAVPlayerAction.PLAY -> avPlayer.play()
            OGAVPlayerAction.PAUSE -> avPlayer.pause()
            OGAVPlayerAction.STOP -> {
                avPlayer.pause()
                avPlayer.seekToTime(CMTimeMake(0, 1))
            }
            OGAVPlayerAction.REWIND -> {
                avPlayer.seekToTime(CMTimeMake(0, 1))
            }
        }
        playerState = when (newAction) {
            OGAVPlayerAction.PLAY -> OGAVPlayerState.PLAYING
            OGAVPlayerAction.PAUSE -> OGAVPlayerState.PAUSED
            OGAVPlayerAction.STOP -> OGAVPlayerState.STOPPED
            OGAVPlayerAction.REWIND -> OGAVPlayerState.RESET
        }
    }


    return OGAVPlayerResult.Success
}
