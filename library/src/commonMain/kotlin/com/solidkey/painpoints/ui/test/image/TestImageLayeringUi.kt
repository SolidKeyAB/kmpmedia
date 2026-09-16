package com.solidkey.painpoints.ui.test.image

import com.solidkey.painpoints.utils.OGUUIDGenerator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.OGImageView
import com.solidkey.painpoints.image.history.DefaultOGHistoryHelper
import com.solidkey.painpoints.image.processing.OGColorFilter
import com.solidkey.painpoints.image.processing.OGImageTransformation
import com.solidkey.painpoints.image.loading.OGSvgUrlType
import com.solidkey.painpoints.image.transform.DefaultOGTransformationDialog
import com.solidkey.painpoints.layer.*

@Composable
fun TestImageLayeringUI() {
    val backgroundUrl = "https://upload.wikimedia.org/wikipedia/commons/4/4b/Tullgarns_slott_S%C3%B6dert%C3%A4lje.png"
    val treeUrl = "https://upload.wikimedia.org/wikipedia/commons/b/bf/Tree_Transparent_Background.png"

    val selectedLayer = remember { mutableStateOf<OGLayer?>(null) }
    val imageUrl = remember { mutableStateOf("https://upload.wikimedia.org/wikipedia/commons/4/4e/Single_apple.png") }
    val selectedItemId = remember { mutableStateOf<String?>(null) }
    val showTransformDialog = remember { mutableStateOf(false) }

    val rootLayer = remember {
        OGLayer(
            id = "background",
            onEvent = { event, id ->
                Logger.i("OG>> Event from $id: $event")
            },
        ).apply {
            val treeLayer = OGLayer(
                id = "tree",
                onEvent = { event, id ->
                    Logger.i("OG>> Tree Layer Event from $id: $event")
                },
            ).apply {
                addItem(
                    OGImageLayerItem(
                        id = "treeImage",
                        x = mutableStateOf(100f),
                        y = mutableStateOf(100f),
                        source = OGSvgUrlType(treeUrl),
                        historyHelper = DefaultOGHistoryHelper(),
                        transformations = mutableStateListOf(
                            OGImageTransformation.Resize(width = 120, height = 120, maintainAspectRatio = true)
                        ),
                        displayContent = { tx, onEvent, gestureHandler ->
                            OGImageView(
                                source = OGSvgUrlType(treeUrl),
                                modifier = Modifier.padding(4.dp),
                                draggable = true,
                                transformations = tx,
                                onEventTriggered = onEvent,
                                gestureHandler = gestureHandler
                            )
                        },
                        onEvent = { event, id ->
                            if (event.name == "DOUBLE_TAP") {
                                selectedItemId.value = id
                                showTransformDialog.value = true
                            }
                        }
                    )
                )
            }

            addItem(
                OGImageLayerItem(
                    id = "bgImage",
                    x = mutableStateOf(0f),
                    y = mutableStateOf(0f),
                    source = OGSvgUrlType(backgroundUrl),
                    historyHelper = DefaultOGHistoryHelper(),
                    transformations = mutableStateListOf(
                        OGImageTransformation.Resize(width = 800, height = 800, maintainAspectRatio = true)
                    ),
                    displayContent = { tx, onEvent, gestureHandler ->
                        OGImageView(
                            source = OGSvgUrlType(backgroundUrl),
                            modifier = Modifier.padding(4.dp),
                            draggable = true,
                            transformations = tx,
                            onEventTriggered = onEvent,
                            gestureHandler = gestureHandler
                        )
                    },
                    onEvent = { event, id ->
                        if (event.name == "DOUBLE_TAP") {
                            selectedItemId.value = id
                            showTransformDialog.value = true
                        }
                    }
                )
            )

            addItem(treeLayer)
        }
    }

    val collectedData = remember { derivedStateOf { collectLayerItems(rootLayer) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color(0xFFFFF59D)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎯 Layer Selection")

        LazyColumn(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            items(collectedData.value.layers) { layer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clickable { selectedLayer.value = layer }
                        .background(
                            if (selectedLayer.value == layer) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else Color.Transparent
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedLayer.value == layer),
                        onClick = { selectedLayer.value = layer }
                    )
                    Text("Layer: ${layer.id}", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        OutlinedTextField(
            value = imageUrl.value,
            onValueChange = { imageUrl.value = it },
            label = { Text("Image URL") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Row {
            Button(onClick = {
                val targetLayer = selectedLayer.value
                if (imageUrl.value.isNotBlank() && targetLayer != null) {
                    val newItem = OGImageLayerItem(
                        id = OGUUIDGenerator.generate(),
                        x = mutableStateOf(20f),
                        y = mutableStateOf(20f),
                        source = OGSvgUrlType(imageUrl.value),
                        historyHelper = DefaultOGHistoryHelper(),
                        transformations = mutableStateListOf(
                            OGImageTransformation.Resize(50, 50, maintainAspectRatio = true)
                        ),
                        onEvent = { event, id ->
                            if (event.name == "DOUBLE_TAP") {
                                selectedItemId.value = id
                                showTransformDialog.value = true
                            }
                        },
                        displayContent = { tx, onEvent, gestureHandler ->
                            OGImageView(
                                source = OGSvgUrlType(imageUrl.value),
                                modifier = Modifier.padding(4.dp),
                                draggable = true,
                                transformations = tx,
                                onEventTriggered = onEvent,
                                gestureHandler = gestureHandler
                            )
                        }
                    )
                    targetLayer.addItem(newItem)
                }
            }) {
                Text("➕ Add Image")
            }

            Spacer(Modifier.width(8.dp))

            Button(onClick = {
                selectedLayer.value?.let {
                    val newLayer = OGLayer(
                        id = OGUUIDGenerator.generate(),
                        onEvent = { e, id -> Logger.i("OG>> Sublayer Event: $e $id") }
                    )
                    it.addItem(newLayer)
                }
            }) {
                Text("📂 Add Sublayer")
            }
        }

        OGLayerRenderer(rootLayer)
    }

    if (showTransformDialog.value && selectedItemId.value != null) {
        val item = rootLayer.findItemById(selectedItemId.value!!) as? OGImageLayerItem
        if (item != null) {
            val resize = item.transformations.filterIsInstance<OGImageTransformation.Resize>().firstOrNull()
            val rotate = item.transformations.filterIsInstance<OGImageTransformation.Rotate>().firstOrNull()
            val crop = item.transformations.filterIsInstance<OGImageTransformation.Crop>().firstOrNull()
            val grayscale = item.transformations.filterIsInstance<OGImageTransformation.ColorFilter>()
                .firstOrNull()?.filter == OGColorFilter.GRAYSCALE

            DefaultOGTransformationDialog(
                item = item,
                onClose = { showTransformDialog.value = false },
                enableUndoRedo = true,
                existingResize = resize,
                existingRotation = rotate,
                existingCrop = crop,
                existingGrayscale = grayscale
            )
        }
    }
}


data class CollectedLayerData(
    val layers: List<OGLayer>,
    val items: List<OGLayerItem>
)

fun collectLayerItems(rootLayer: OGLayer): CollectedLayerData {
    val collectedLayers = mutableListOf<OGLayer>()
    val collectedItems = mutableListOf<OGLayerItem>()

    fun traverse(layer: OGLayer) {
        collectedLayers.add(layer)
        layer.layerItems.forEach { item ->
            if (item is OGLayer) {
                traverse(item)
            } else {
                collectedItems.add(item)
            }
        }
    }

    traverse(rootLayer)
    return CollectedLayerData(collectedLayers, collectedItems)
}
