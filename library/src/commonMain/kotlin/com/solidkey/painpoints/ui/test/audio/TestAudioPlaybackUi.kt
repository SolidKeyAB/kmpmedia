package com.solidkey.painpoints.ui.test.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.solidkey.painpoints.audio.loading.OGAudioFormat
import com.solidkey.painpoints.audio.playing.OGAudioPlaybackConfig
import com.solidkey.painpoints.audio.playing.OGAudioPlayer
import com.solidkey.painpoints.source.OGSource

@Composable
fun TestAudioPlaybackUI() {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var audioPlayer by remember { mutableStateOf<OGAudioPlayer?>(null) }

    // ✅ Input Fields for User to Enter Audio Source
    var audioUrl by remember { mutableStateOf("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3") }
    var fileName by remember { mutableStateOf("") }
    var resourceName by remember { mutableStateOf("") }

    var isPlaying by remember { mutableStateOf(false) }
    var selectedAudio by remember { mutableStateOf<OGSource?>(null) }

    // 🔧 Playback Config Options
    var autoStart by remember { mutableStateOf(false) }
    var autoRepeat by remember { mutableStateOf(false) }
    var startDelay by remember { mutableStateOf("0") }

    if (audioPlayer == null) {
        audioPlayer = OGAudioPlayer.create()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔊 **Test Audio Playback**", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // 🎵 **Audio Selection (User Input)**
        Text("Enter Audio Source:")
        OutlinedTextField(
            value = audioUrl,
            onValueChange = { audioUrl = it },
            label = { Text("🌐 Audio URL") },
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )

        OutlinedTextField(
            value = fileName,
            onValueChange = { fileName = it },
            label = { Text("📂 File Name (e.g., my_audio.mp3)") },
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )

        OutlinedTextField(
            value = resourceName,
            onValueChange = { resourceName = it },
            label = { Text("🎵 Resource Name (e.g., sample_audio)") },
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ⚙️ **Playback Configuration**
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = autoStart, onCheckedChange = { autoStart = it })
            Text("Auto-Start")

            Spacer(modifier = Modifier.width(16.dp))

            Checkbox(checked = autoRepeat, onCheckedChange = { autoRepeat = it })
            Text("Auto-Repeat")

            Spacer(modifier = Modifier.width(16.dp))

            OutlinedTextField(
                value = startDelay,
                onValueChange = { startDelay = it.filter { char -> char.isDigit() } },
                label = { Text("Start Delay (ms)") },
                modifier = Modifier.width(120.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ✅ Load Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (audioUrl.isNotEmpty()) {
                    selectedAudio = OGSource.Url(audioUrl)
                    audioPlayer?.load(
                        selectedAudio!!,
                        OGAudioPlaybackConfig(autoStart, autoRepeat, startDelay.toLongOrNull() ?: 0L)
                    )
                    if (autoStart) isPlaying = true
                }
            }) {
                Text("Load from URL")
            }

            Button(onClick = {
                if (fileName.isNotEmpty()) {
                    selectedAudio = OGSource.FilePath(fileName, OGAudioFormat.MP3.extension)
                    audioPlayer?.load(
                        selectedAudio!!,
                        OGAudioPlaybackConfig(autoStart, autoRepeat, startDelay.toLongOrNull() ?: 0L)
                    )
                    if (autoStart) isPlaying = true
                }
            }) {
                Text("Load from File")
            }

            Button(onClick = {
                if (resourceName.isNotEmpty()) {
                    selectedAudio = OGSource.Resource(resourceName, OGAudioFormat.MP3.extension)
                    audioPlayer?.load(
                        selectedAudio!!,
                        OGAudioPlaybackConfig(autoStart, autoRepeat, startDelay.toLongOrNull() ?: 0L)
                    )
                    if (autoStart) isPlaying = true
                }
            }) {
                Text("Load from Resource")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🎧 **Playback Control Buttons**
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    audioPlayer?.play()
                    isPlaying = true
                },
                enabled = selectedAudio != null && audioPlayer != null && !isPlaying
            ) {
                Text("▶️ Play")
            }

            Button(
                onClick = {
                    audioPlayer?.pause()
                    isPlaying = false
                },
                enabled = isPlaying
            ) {
                Text("⏸ Pause")
            }

            Button(
                onClick = {
                    audioPlayer?.stop()
                    isPlaying = false
                },
                enabled = isPlaying // ✅ Enabled when playing (even with auto-start)
            ) {
                Text("⏹ Stop")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 🎶 **Playback Status**
        Text("🎵 Now Playing: ${selectedAudio?.displayName()}", style = MaterialTheme.typography.bodyLarge)

        // 🚨 **Error Message Display**
        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text("⚠️ $it", color = Color.Red)
        }
    }
}

// ✅ Helper function to display audio source name
fun OGSource.displayName(): String {
    return when (this) {
        is OGSource.Url -> "🌍 URL: ${this.url}"
        is OGSource.FilePath -> "📂 File: ${this.path}"
        is OGSource.Resource -> "🎵 Resource: ${this.resource}"
    }
}
