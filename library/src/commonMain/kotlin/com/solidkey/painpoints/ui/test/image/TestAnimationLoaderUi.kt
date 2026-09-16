package com.solidkey.painpoints.ui.test.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.loading.OGSvgFileType
import com.solidkey.painpoints.image.loading.OGSvgResourceFileType
import com.solidkey.painpoints.image.loading.OGSvgSourceType
import com.solidkey.painpoints.image.loading.OGSvgUrlType
import com.solidkey.painpoints.image.loading.SAMPLE_ANIMATED_SVG
import com.solidkey.painpoints.image.loading.SAMPLE_ANIMATED_SVG_FILE_NAME
import com.solidkey.painpoints.image.loading.seedSvgFile
import com.solidkey.painpoints.image.svg.animation.OGSVGAnimationPlayer

@Composable
fun TestSvgAnimationLoaderUI() {
    // Default to a genuinely ANIMATED remote SVG so "Load from URL" visibly moves
    // out of the box. The old default (W3C atom.svg) is a *static* logo — no
    // <animate>/<animateTransform> elements — so it loaded fine but never moved,
    // which read as "URL animation is broken". This sample (spinning orbits +
    // orbiting electrons) uses only supported animateTransform rotate/translate.
    var svgUrl by remember { mutableStateOf("https://gist.githubusercontent.com/ozgesolidkey/a61cecbd72ff25ff229e3de5157d9409/raw/animated-atom.svg") }
    var fileName by remember { mutableStateOf("animated_svg") }
    var resourceName by remember { mutableStateOf("anim1") }
    var selectedSvgSource by remember { mutableStateOf<OGSvgSourceType?>(null) }
    // Auto-play once a sample is loaded; the ⏸ Pause button still stops it.
    var isPlaying by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var enableLooping by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()

    // Seed a genuine animated SVG onto disk so "Load from File" reads a real,
    // animated file. The old default "animated_svg" pointed at File("animated_svg"),
    // a bare relative path that never exists on device — so the file source always
    // failed to load while resources (bundled assets) worked. Once written, default
    // the File field to the seeded absolute path.
    seedSvgFile(SAMPLE_ANIMATED_SVG_FILE_NAME, SAMPLE_ANIMATED_SVG) { seededPath ->
        if (seededPath != null && fileName == "animated_svg") fileName = seededPath
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "SVG Animation Loader Test",
            style = TextStyle(color = Color.Black, fontSize = MaterialTheme.typography.titleLarge.fontSize)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = enableLooping,
                onCheckedChange = { enableLooping = it }
            )
            Text("Enable Looping", color = Color.Black)
        }

        /** 🖼 Load Animated SVG from URL */
        Text("Load Animated SVG from URL:", color = Color.Black)
        BasicTextField(
            value = svgUrl,
            onValueChange = { svgUrl = it },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            textStyle = TextStyle(color = Color.Black)
        )
        Button(onClick = { selectedSvgSource = OGSvgUrlType(svgUrl) }) {
            Text("Load SVG from URL", color = Color.Black)
        }

        /** 📁 Load Animated SVG from File */
        Text("Load Animated SVG from File:", color = Color.Black)
        BasicTextField(
            value = fileName,
            onValueChange = { fileName = it },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            textStyle = TextStyle(color = Color.Black)
        )
        Button(onClick = { selectedSvgSource = OGSvgFileType(fileName) }) {
            Text("Load SVG from File", color = Color.Black)
        }

        /** 🎨 Load Animated SVG from Resources */
        Text("Load Animated SVG from Resources:", color = Color.Black)
        BasicTextField(
            value = resourceName,
            onValueChange = { resourceName = it },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            textStyle = TextStyle(color = Color.Black)
        )
        Button(onClick = { selectedSvgSource = OGSvgResourceFileType(resourceName) }) {
            Text("Load SVG from Resources", color = Color.Black)
        }

        Spacer(modifier = Modifier.height(30.dp))

        /** 🎬 Play/Pause Controls */
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { isPlaying = true }) {
                Text("▶ Play", color = Color.Black)
            }
            Button(onClick = { isPlaying = false }) {
                Text("⏸ Pause", color = Color.Black)
            }
            Button(onClick = { selectedSvgSource = null }) {
                Text("❌ Stop", color = Color.Black)
            }
        }

        /** 🖼 Display Loaded Animated SVG */
        selectedSvgSource?.let {
            Logger.e("OG>> TestSvgAnimationLoaderUi... Calling SVGAnimationPlayer...")

            OGSVGAnimationPlayer(
                source = it,
                width = 500f,
                height = 500f,
                isPlaying = isPlaying,
                loop = enableLooping
            ) {
                errorMessage = "Error Loading SVG Animation"
            }
        } ?: Text("No SVG Loaded", color = Color.Black)

        errorMessage?.let {
            Text("⚠️ $it", color = Color.Red)
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
