package com.solidkey.painpoints.video.playing

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ButtonStyle {
    TEXT, ICON, IMAGE
}

data class OGAVPlayerControlConfig(
    val buttonStyle: ButtonStyle = ButtonStyle.ICON, // ✅ Text, Icon, or Image buttons
    val buttonSize: Dp = 48.dp, // ✅ Custom button size
    val buttonSpacing: Dp = 8.dp, // ✅ Spacing between buttons
    val backgroundColor: Color = Color.Black.copy(alpha = 0.5f), // ✅ Semi-transparent background
    val buttonTint: Color = Color.White, // ✅ Tint for icons/text
//    val buttonImages: Map<DAVPlayerAction, Painter?> = emptyMap(), // ✅ Images for buttons
    val controlAlignment: Alignment = Alignment.BottomCenter // ✅ Change button layout position
)
