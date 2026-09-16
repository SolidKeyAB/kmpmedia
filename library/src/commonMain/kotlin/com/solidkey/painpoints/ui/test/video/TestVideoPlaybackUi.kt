package com.solidkey.painpoints.ui.test.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.source.OGSource
import com.solidkey.painpoints.source.OGSourceFormat
import com.solidkey.painpoints.source.OGSourceType
import com.solidkey.painpoints.video.loading.OGIVideoResourceFileType
import com.solidkey.painpoints.video.loading.OGVideoFileType
import com.solidkey.painpoints.video.loading.OGVideoUrlType
import com.solidkey.painpoints.video.playing.OGAVPlayer
import com.solidkey.painpoints.video.playing.OGAVPlayerAction
import com.solidkey.painpoints.shape.OGShapeType
import com.solidkey.painpoints.video.playing.OGPlayerConfig
import com.solidkey.painpoints.video.playing.OGVideoPlaybackConfig

@Composable
fun TestVideoPlaybackUI() {
    var videoUrl by remember { mutableStateOf("https://media.w3.org/2010/05/sintel/trailer.mp4") }
    var filePath by remember { mutableStateOf("bird_video.mp4") }
    var resourceName by remember { mutableStateOf("sample_resource") }
    var selectedSource by remember { mutableStateOf<OGSourceType?>(null) }

    var autoStart by remember { mutableStateOf(false) }
    var autoRepeat by remember { mutableStateOf(false) }
    var flipVideo by remember { mutableStateOf(false) }
    var isMovable by remember { mutableStateOf(false) }
    var showDefaultControls by remember { mutableStateOf(false) }
    // Default to RECTANGLE: a simple rounded-rect clip is hardware-friendly, whereas
    // non-rectangular clips force an offscreen layer that a video TextureView can't render into.
    var selectedShape by remember { mutableStateOf(OGShapeType.RECTANGLE) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎥 Test DAVPlayer", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // 🔗 Video Source Inputs
        OutlinedTextField(
            value = videoUrl,
            onValueChange = { videoUrl = it },
            label = { Text("🌐 Video URL") },
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )

        OutlinedTextField(
            value = filePath,
            onValueChange = { filePath = it },
            label = { Text("📂 File Name") },
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )

        OutlinedTextField(
            value = resourceName,
            onValueChange = { resourceName = it },
            label = { Text("📦 Resource Name") },
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ⚙️ Playback Config Options
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = autoStart, onCheckedChange = { autoStart = it })
            Text("Auto-Start")

            Checkbox(checked = autoRepeat, onCheckedChange = { autoRepeat = it })
            Text("Auto-Repeat")

            Checkbox(checked = flipVideo, onCheckedChange = { flipVideo = it })
            Text("Flip Video")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isMovable, onCheckedChange = { isMovable = it })
            Text("Movable")

            Checkbox(checked = showDefaultControls, onCheckedChange = { showDefaultControls = it })
            Text("Default Controls")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 🎨 Shape Selection
        Text("📐 Select Shape")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(OGShapeType.RECTANGLE, OGShapeType.CIRCLE, OGShapeType.TRIANGLE_UP, OGShapeType.DIAMOND).forEach { shape ->
                Button(onClick = { selectedShape = shape }) {
                    Text(shape.name)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 🚀 Load Video Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { selectedSource = OGVideoUrlType(videoUrl) }) { Text("Load URL") }
            Button(onClick = { selectedSource = OGVideoFileType(filePath) }) { Text("Load File") }
            Button(onClick = { selectedSource = OGIVideoResourceFileType(resourceName) }) { Text("Load Resource") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🎬 DAVPlayer Preview
        selectedSource?.let { source ->
            Box(Modifier.size(320.dp).background(Color.LightGray)) {
                OGAVPlayer(
                    action = OGAVPlayerAction.PLAY,
                    source = source,
                    config = OGPlayerConfig(
                        showDefaultControls = showDefaultControls,
                        displayMovable = isMovable,
                        displayShape = selectedShape,
                        initialOffset = Offset.Zero,
                        cornerRadius = 12.dp,
                        controlPosition = Alignment.BottomCenter,
                        playbackConfig = OGVideoPlaybackConfig(
                            autoStart = autoStart,
                            autoRepeat = autoRepeat
                        )
                    ),
                    flipHorizontally = flipVideo,
                    modifier = Modifier.fillMaxSize(),
                    onResetConfirm = null,
                    onCustomizeControls = { state, onAction ->
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .background(Color.DarkGray.copy(alpha = 0.8f))
                                .clip(RoundedCornerShape(12.dp))
                                .padding(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(onClick = { onAction(OGAVPlayerAction.PLAY) }) { Text("▶️ Play") }
                                Button(onClick = { onAction(OGAVPlayerAction.PAUSE) }) { Text("⏸ Pause") }
                                Button(onClick = { onAction(OGAVPlayerAction.STOP) }) { Text("⏹ Stop") }
                                Button(onClick = { onAction(OGAVPlayerAction.REWIND) }) { Text("🔄 Rewind") }
                            }
                        }
                    },
                    onError = { error ->
                        Logger.e("❌ Error: $error")
                        errorMessage = error.message
                    }
                )
            }
        } ?: Text("📭 No Video Loaded", modifier = Modifier.padding(16.dp))

        Spacer(modifier = Modifier.height(8.dp))

        // 🚨 Error Handling
        errorMessage?.let {
            Text(text = "⚠️ $it", color = MaterialTheme.colorScheme.error)
        }
    }
}
