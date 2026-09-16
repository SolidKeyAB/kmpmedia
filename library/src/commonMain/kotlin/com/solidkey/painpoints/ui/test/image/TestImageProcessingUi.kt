package com.solidkey.painpoints.ui.test.image

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.OGImageView
import com.solidkey.painpoints.image.layering.OGLayerItemEvent
import com.solidkey.painpoints.image.loading.*
import com.solidkey.painpoints.image.processing.OGColorFilter
import com.solidkey.painpoints.image.processing.OGImageTransformation
import com.solidkey.painpoints.source.OGSourceType

@Composable
fun TestImageProcessingUI() {
    var imageUrl by remember { mutableStateOf("https://bellard.org/bpg/3.png") }
    var fileName by remember { mutableStateOf("happy_icon") }
    var resourceName by remember { mutableStateOf("happy_icon") }
    var source by remember { mutableStateOf<OGSourceType>(OGImageFileType(fileName, OGImageFormat.PNG)) }
    val transformations = remember { mutableStateListOf<OGImageTransformation>() }
    var lastEvent by remember { mutableStateOf<OGLayerItemEvent?>(null) }
    var isDraggable by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Enter File Name, URL, or Resource Name")

        var inputText by remember { mutableStateOf("") }
        OutlinedTextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.padding(8.dp))

        Row {
            Button(onClick = { source = OGImageFileType(fileName, OGImageFormat.PNG) }) { Text("Load File") }
            Button(onClick = { source = OGImageUrlType(imageUrl) }) { Text("Load URL") }
            Button(onClick = { source = OGImageResourceFileType(resourceName, OGImageFormat.PNG) }) { Text("Load Resource") }
        }

        OGImageView(
            source = source,
            modifier = Modifier.wrapContentSize(),
            transformations = transformations,
            draggable = isDraggable,
            onEventTriggered = { event, id ->
                lastEvent = event
                Logger.i("OG>> TestImageProcessingUI... Event from $id: $event")
            }
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text("Last Event: ${lastEvent ?: "None"}")

        Row {
            Button(onClick = {
                transformations.add(OGImageTransformation.Resize(100, 100))
            }) {
                Text("Resize to 100x100")
            }

            Button(onClick = {
                transformations.add(OGImageTransformation.Crop(50, 50, 200, 100))
            }) {
                Text("Crop")
            }

            Button(onClick = {
                val currentRotation = transformations.filterIsInstance<OGImageTransformation.Rotate>().firstOrNull()?.degrees ?: 0f
                transformations.removeAll { it is OGImageTransformation.Rotate }
                transformations.add(OGImageTransformation.Rotate(currentRotation + 90f))
            }) {
                Text("Rotate")
            }

            Button(onClick = {
                transformations.add(OGImageTransformation.ColorFilter(OGColorFilter.GRAYSCALE))
            }) {
                Text("Grayscale")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {
            transformations.clear()
        }) {
            Text("Reset Transformations")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {
            isDraggable = !isDraggable
        }) {
            Text(if (isDraggable) "Disable Dragging" else "Enable Dragging")
        }
    }
}
