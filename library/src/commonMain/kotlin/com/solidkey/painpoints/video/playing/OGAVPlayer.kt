package com.solidkey.painpoints.video.playing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.shape.OGShapeType
import com.solidkey.painpoints.source.OGSource
import com.solidkey.painpoints.source.OGSourceType

expect enum class OGAVPlayerState {
    STOPPED,
    PLAYING,
    PAUSED,
    RESET
}

expect enum class OGAVPlayerAction {
    STOP,
    PLAY,
    PAUSE,
    REWIND
}

internal enum class OGAVPlayerInternalAction {
    STOP,
    PLAY,
    PAUSE,
    REWIND,
    RESET  // Internal only
}

@Composable
fun DefaultControls(
    state: OGAVPlayerState,
    onAction: (OGAVPlayerAction) -> Unit,
    config: OGAVPlayerControlConfig = OGAVPlayerControlConfig()
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(config.backgroundColor)
            .padding(8.dp),
        contentAlignment = config.controlAlignment
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(config.buttonSpacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentSize()
        ) {
            OGAVPlayerAction.entries.forEach { action ->
                IconButton(
                    onClick = { onAction(action) },
                    modifier = Modifier.size(config.buttonSize)
                ) {
                    Icon(
                        imageVector = when (action) {
                            OGAVPlayerAction.PLAY -> Icons.Filled.PlayArrow
                            OGAVPlayerAction.PAUSE -> Icons.Filled.KeyboardArrowDown
                            OGAVPlayerAction.STOP -> Icons.Filled.KeyboardArrowUp
                            OGAVPlayerAction.REWIND -> Icons.Filled.Refresh
                            else -> {
                                Logger.w("Unknown player action: $action")
                                Icons.Default.Warning
                            }
                        },
                        contentDescription = action.name,
                        tint = config.buttonTint
                    )
                }
            }
        }
    }
}


/**
 * A hoistable, detached controller for [OGAVPlayer]. Create one with [rememberOGAVPlayerController],
 * hand it to the player, and drive playback from ANY button in your UI — even from views rendered
 * completely outside the player's borders (a HUD, a game overlay, a bottom bar). This lets the
 * video component stay chrome-less by default (no built-in controls) while still being fully
 * controllable. Calls are no-ops until the player has bound itself.
 */
class OGAVPlayerController {
    // Set by the platform actual once the player is composed; cleared on dispose.
    internal var handler: ((OGAVPlayerAction) -> Unit)? = null

    // Set by the platform actual to perform an absolute seek in milliseconds; cleared on dispose.
    internal var seekHandler: ((Long) -> Unit)? = null

    // Live playback status, pushed by the platform actual on every position tick. Backed by a
    // Compose MutableState so reads from a @Composable are reactive (recompose on each update).
    private val _status = mutableStateOf(OGPlaybackStatus())

    /**
     * Live, reactive playback status — read it from ANY `@Composable` (a scrubber, a progress bar,
     * a time-synced overlay) and it recomposes as the video plays. Updates ~5 Hz while attached; it
     * holds the default zero-state before a player binds or after it disposes.
     */
    val status: State<OGPlaybackStatus> get() = _status

    // Called by the platform actual on each poll tick.
    internal fun updateStatus(newStatus: OGPlaybackStatus) { _status.value = newStatus }

    val isAttached: Boolean get() = handler != null

    fun play() { handler?.invoke(OGAVPlayerAction.PLAY) }
    fun pause() { handler?.invoke(OGAVPlayerAction.PAUSE) }
    fun stop() { handler?.invoke(OGAVPlayerAction.STOP) }
    fun rewind() { handler?.invoke(OGAVPlayerAction.REWIND) }
    fun dispatch(action: OGAVPlayerAction) { handler?.invoke(action) }

    /**
     * Seek to an absolute position in milliseconds, clamped to `[0, durationMs]` (the duration is
     * taken from the latest [status]). No-op until the player has bound itself.
     */
    fun seekTo(positionMs: Long) { seekHandler?.invoke(clampSeek(positionMs)) }

    /**
     * Seek by a relative delta in milliseconds — negative rewinds, positive fast-forwards (e.g.
     * ±10 000 for skip buttons or double-tap seek). Computed off the latest [status] position and
     * clamped to `[0, durationMs]`. No-op until the player has bound itself.
     */
    fun seekBy(deltaMs: Long) { seekHandler?.invoke(clampSeek(_status.value.positionMs + deltaMs)) }

    private fun clampSeek(target: Long): Long {
        val dur = _status.value.durationMs
        val lo = if (target < 0L) 0L else target
        return if (dur > 0L && lo > dur) dur else lo
    }
}

/** Remembers an [OGAVPlayerController] across recompositions. */
@Composable
fun rememberOGAVPlayerController(): OGAVPlayerController = remember { OGAVPlayerController() }

@Composable
expect fun OGAVPlayer(
    action: OGAVPlayerAction,
    source: OGSourceType,
    config: OGPlayerConfig = OGPlayerConfig(),
    flipHorizontally: Boolean = false,
    modifier: Modifier = Modifier,
    onResetConfirm: (() -> Boolean)? = null,
    onCustomizeControls: (@Composable (OGAVPlayerState, (OGAVPlayerAction) -> Unit) -> Unit)? = null, // ✅ New customization callback
    controller: OGAVPlayerController? = null, // ✅ Detached/attachable external controls (headless-friendly)
    onError: (DAVPlayerError) -> Unit, // ✅ Callback for immediate error handling
    onProgress: ((OGPlaybackStatus) -> Unit)? = null, // ✅ Live playback status (position/duration/isPlaying/buffered), ~5 Hz while attached
    cues: List<OGCue> = emptyList() // ✅ Timed triggers fired off the playback position (chapter markers, hotspots, timers)
): OGAVPlayerResult

fun getDefaultControlAlignment(shape: OGShapeType): Alignment {
    return when (shape) {
        OGShapeType.CIRCLE -> Alignment.Center
        OGShapeType.TRIANGLE_UP -> Alignment.BottomCenter
        OGShapeType.TRIANGLE_DOWN -> Alignment.TopCenter
        OGShapeType.DIAMOND -> Alignment.Center
        else -> Alignment.BottomCenter
    }
}

sealed class DAVPlayerError(val message: String) {
    data class SourceNotFound(val source: OGSourceType?) : DAVPlayerError("Video source not found: ${source.toString()}")
    data class PlaybackError(val cause: Throwable?) : DAVPlayerError("Playback error: ${cause?.message}")
    data object UnknownError : DAVPlayerError("An unknown error occurred")
}

sealed class OGAVPlayerResult {
    data object Success : OGAVPlayerResult()
    data class Error(val error: DAVPlayerError) : OGAVPlayerResult()
}
