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
import androidx.compose.foundation.layout.size
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
import com.solidkey.painpoints.image.OGImageView
import com.solidkey.painpoints.image.loading.OGImageFileType
import com.solidkey.painpoints.image.loading.OGImageFormat
import com.solidkey.painpoints.image.loading.OGImageResourceFileType
import com.solidkey.painpoints.image.loading.OGImageUrlType
import com.solidkey.painpoints.image.loading.OGSvgFileType
import com.solidkey.painpoints.image.loading.OGSvgResourceFileType
import com.solidkey.painpoints.image.loading.OGSvgSourceType
import com.solidkey.painpoints.image.loading.OGSvgUrlType
import com.solidkey.painpoints.image.svg.OGSVGView
import com.solidkey.painpoints.source.OGSourceType

@Composable
fun TestImageLoaderUI() {
    var imageUrl by remember { mutableStateOf("https://bellard.org/bpg/3.png") }
    var svgUrl by remember { mutableStateOf("https://dev.w3.org/SVG/tools/svgweb/samples/svg-files/atom.svg") }
    var fileName by remember { mutableStateOf("happy_icon") }
    var resourceName by remember { mutableStateOf("c") }
    var selectedImageSource by remember { mutableStateOf<OGSourceType?>(null) }
    var selectedSvgSource by remember { mutableStateOf<OGSvgSourceType?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var allowDrag by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("SVG & Image Loader Test", style = TextStyle(color = Color.Black, fontSize = MaterialTheme.typography.titleLarge.fontSize))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = allowDrag,
                onCheckedChange = { allowDrag = it }
            )
            Text("Enable Dragging", color = Color.Black)
        }

        /** 🌐 Load Image from URL */
        Text("Load Image from URL:", color = Color.Black)
        BasicTextField(
            value = imageUrl,
            onValueChange = { imageUrl = it },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            textStyle = TextStyle(color = Color.Black)
        )
        Button(onClick = { selectedImageSource = OGImageUrlType(imageUrl) }) {
            Text("Load from URL", color = Color.Black)
        }

        /** 🖼 Load SVG from URL */
        Text("Load SVG from URL:", color = Color.Black)
        BasicTextField(
            value = svgUrl,
            onValueChange = { svgUrl = it },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            textStyle = TextStyle(color = Color.Black)
        )
        Button(onClick = { selectedSvgSource = OGSvgUrlType(svgUrl) }) {
            Text("Load SVG from URL", color = Color.Black)
        }

        /** 📁 Load Image from File */
        Text("Load Image from File:", color = Color.Black)
        BasicTextField(
            value = fileName,
            onValueChange = { fileName = it },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            textStyle = TextStyle(color = Color.Black)
        )
        Button(onClick = { selectedImageSource = OGImageFileType(fileName, OGImageFormat.PNG) }) {
            Text("Load from File", color = Color.Black)
        }

        /** 📁 Load SVG from File */
        Text("Load SVG from File:", color = Color.Black)
        BasicTextField(
            value = fileName,
            onValueChange = { fileName = it },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            textStyle = TextStyle(color = Color.Black)
        )
        Button(onClick = { selectedSvgSource = OGSvgFileType(fileName) }) {
            Text("Load SVG from File", color = Color.Black)
        }

        /** 🎨 Load Image from Resources */
        Text("Load Image from Resources:", color = Color.Black)
        BasicTextField(
            value = resourceName,
            onValueChange = { resourceName = it },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            textStyle = TextStyle(color = Color.Black)
        )
        Button(onClick = { selectedImageSource = OGImageResourceFileType(resourceName, OGImageFormat.PNG) }) {
            Text("Load from Resources", color = Color.Black)
        }

        /** 🎨 Load SVG from Resources */
        Text("Load SVG from Resources:", color = Color.Black)
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

        /** 🖼 Display Loaded Image */
        selectedImageSource?.let {
            Logger.e("OG>> TestImageLoaderUi... Calling OGImageView...")
            OGImageView(
                source = it,
                modifier = Modifier.size(200.dp),
                onEventTriggered = { event, id ->
                    Logger.i("OG>> Image Event from $id: $event")
                },
                onError = {
                    errorMessage = "Error Loading Image"
                }
            )
        } ?: Text("No Image Loaded", color = Color.Black)

        /** 🖼 Display Loaded SVG */
        selectedSvgSource?.let {
            Logger.e("OG>> TestImageLoaderUi... Calling OGSvgView...")

            OGSVGView(
                source = it,
                width = 500f,
                height = 500f,
                enableDrag = allowDrag,
            ) {
                errorMessage = "Error Loading SVG"
            }
        } ?: Text("No SVG Loaded", color = Color.Black)

        errorMessage?.let {
            Text("⚠️ $it", color = Color.Red)
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
