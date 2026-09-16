package com.solidkey.painpoints.ui.test

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solidkey.painpoints.ui.test.audio.TestAudioPlaybackUI
import com.solidkey.painpoints.ui.test.image.TestImageLayeringUI
import com.solidkey.painpoints.ui.test.image.TestImageLoaderUI
import com.solidkey.painpoints.ui.test.image.TestImageProcessingUI
import com.solidkey.painpoints.ui.test.image.TestSvgAnimationLoaderUI
import com.solidkey.painpoints.ui.test.video.TestVideoPlaybackUI

@Composable
fun TestMainUI() {
    var currentTest by remember { mutableStateOf<TestScreen?>(null) }

    when (currentTest) {
        null -> TestMenuScreen { selectedTest -> currentTest = selectedTest }
        TestScreen.ImageLoading -> TestImageLoaderUI()
        TestScreen.ImageProcessing -> TestImageProcessingUI()
        TestScreen.ImageLayering -> TestImageLayeringUI()
        TestScreen.AudioPlayback -> TestAudioPlaybackUI()
        TestScreen.VideoPlayback -> TestVideoPlaybackUI()
        TestScreen.SvgAnimation -> TestSvgAnimationLoaderUI() // ✅ Add SVG Animation Test
    }

    // ✅ "Back" Button to return to the menu
    currentTest?.let {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Button(onClick = { currentTest = null }, modifier = Modifier.padding(16.dp)) {
                Text("Back to Main Menu")
            }
        }
    }
}

@Composable
fun TestMenuScreen(onTestSelected: (TestScreen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📌 Select a Test Case", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(TestScreen.entries.size) { index ->
                val test = TestScreen.entries[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable { onTestSelected(test) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(test.title)
                    }
                }
            }
        }
    }
}

enum class TestScreen(val title: String) {
    ImageLoading("🖼️ Image Loading Test"),
    ImageProcessing("🖼️ Image Processing Test"),
    ImageLayering("📌 Image Layering Test"),
    AudioPlayback("🔊 Audio Playback Test"),
    VideoPlayback("🎥 Video Playback Test"),
    SvgAnimation("🎬 SVG Animation Test") // ✅ Add SVG Animation Test
}
