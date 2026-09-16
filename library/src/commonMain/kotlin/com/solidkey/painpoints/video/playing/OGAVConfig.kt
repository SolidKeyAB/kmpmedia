package com.solidkey.painpoints.video.playing

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.solidkey.painpoints.shape.OGShapeType
import com.solidkey.painpoints.shape.toShape

// OGShapeType and the custom Shape classes now live in the shared
// com.solidkey.painpoints.shape package so both OGAVPlayer (video) and
// OGImageView (photos) can clip to the same primitives.

/**
 * How the video fills its (possibly shaped) container.
 * - [FIT]  = letterbox: the whole frame is visible, empty areas are painted with
 *            [OGPlayerConfig.backgroundColor].
 * - [FILL] = crop-to-fill: the video is scaled up until no empty areas remain, overflow is
 *            clipped by the shape. Maps to ExoPlayer RESIZE_MODE_ZOOM / iOS resizeAspectFill.
 */
enum class OGVideoScale {
    FIT, FILL
}


data class OGVideoPlaybackConfig(
    val autoStart: Boolean = true,
    val autoRepeat: Boolean = false,
    val startDelay: Long = 0L,
    val loopCount: Int = 0 // 0 means infinite loop
)

data class OGPlayerConfig(
    val showDefaultControls: Boolean = false, // ✅ Show/hide controls
    val displayMovable: Boolean = false, // ✅ Allow movement
    val displayShape: OGShapeType = OGShapeType.RECTANGLE, // ✅ Shape selection
    val initialOffset: Offset = Offset.Zero, // ✅ Initial position of the player
    val cornerRadius: Dp = 8.dp, // ✅ Rounded corners for rectangle
    val controlPosition: Alignment? = null, // ✅ Allow developers to decide control location
    val backgroundColor: Color = Color.Black, // ✅ Fills the shape behind/around the video (letterbox color)
    val contentScale: OGVideoScale = OGVideoScale.FIT, // ✅ FIT = letterbox, FILL = crop to remove empty areas
    val playbackConfig: OGVideoPlaybackConfig = OGVideoPlaybackConfig() // ✅ Nested playback settings
) {
    // displayShape + displayMovable are now honored on both Android and iOS
    // (see OGAVPlayer.ios.kt), so the previous iOS "ignored" warnings were removed.

    val shape: Shape
        get() = displayShape.toShape(cornerRadius)
}
