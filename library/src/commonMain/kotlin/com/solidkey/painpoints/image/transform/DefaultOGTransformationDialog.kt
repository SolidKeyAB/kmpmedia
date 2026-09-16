package com.solidkey.painpoints.image.transform

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solidkey.painpoints.image.processing.OGColorFilter
import com.solidkey.painpoints.image.processing.OGImageTransformation
import com.solidkey.painpoints.layer.OGLayerItem
import com.solidkey.painpoints.layer.OGPositionedLayerItem

@Composable
fun DefaultOGTransformationDialog(
    item: OGPositionedLayerItem,
    onClose: () -> Unit,
    enableUndoRedo: Boolean = true,
    existingResize: OGImageTransformation.Resize? = null,
    existingRotation: OGImageTransformation.Rotate? = null,
    existingCrop: OGImageTransformation.Crop? = null,
    existingGrayscale: Boolean = false
) {
    var enableResize by remember { mutableStateOf(existingResize != null) }
    var enableRotate by remember { mutableStateOf(existingRotation != null) }
    var enableCrop by remember { mutableStateOf(existingCrop != null) }
    var enableGrayscale by remember { mutableStateOf(existingGrayscale) }

    val posX = item.x
    val posY = item.y

    var width by remember { mutableStateOf(existingResize?.width ?: 100) }
    var height by remember { mutableStateOf(existingResize?.height ?: 100) }
    var maintainAspect by remember { mutableStateOf(existingResize?.maintainAspectRatio ?: true) }

    var rotation by remember { mutableStateOf(existingRotation?.degrees ?: 0f) }

    var cropX by remember { mutableStateOf(existingCrop?.x ?: 0) }
    var cropY by remember { mutableStateOf(existingCrop?.y ?: 0) }
    var cropWidth by remember { mutableStateOf(existingCrop?.width ?: 50) }
    var cropHeight by remember { mutableStateOf(existingCrop?.height ?: 50) }

    var userPosX by remember { mutableStateOf(item.x) }
    var userPosY by remember { mutableStateOf(item.y) }

    val history = item.historyHelper
    val canUndo = history?.canUndo() == true
    val canRedo = history?.canRedo() == true

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            Button(onClick = {
                val newTransformations = buildList {
                    if (enableResize) add(OGImageTransformation.Resize(width, height, maintainAspect))
                    if (enableRotate && rotation != 0f) add(OGImageTransformation.Rotate(rotation))
                    if (enableCrop) add(OGImageTransformation.Crop(cropX, cropY, cropWidth, cropHeight))
                    if (enableGrayscale) add(OGImageTransformation.ColorFilter(OGColorFilter.GRAYSCALE))
                }

                item.applyTransformations(newTransformations)
                onClose()
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            Button(onClick = onClose) { Text("Cancel") }
        },
        title = { Text("Transform Image") },
        text = {
            Column {
                Text("🔁 Position")
                Row {
                    OutlinedTextField(
                        value = posX.value.toString(),
                        onValueChange = { posX.value = it.toFloatOrNull() ?: 0f },
                        label = { Text("X") },
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    )
                    OutlinedTextField(
                        value = posY.value.toString(),
                        onValueChange = { posY.value = it.toFloatOrNull() ?: 0f },
                        label = { Text("Y") },
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enableResize, onCheckedChange = { enableResize = it })
                    Text("Resize")
                }
                if (enableResize) {
                    Row {
                        OutlinedTextField(
                            value = width.toString(),
                            onValueChange = { width = it.toIntOrNull() ?: 100 },
                            label = { Text("Width") },
                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                        )
                        OutlinedTextField(
                            value = height.toString(),
                            onValueChange = { height = it.toIntOrNull() ?: 100 },
                            label = { Text("Height") },
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = maintainAspect, onCheckedChange = { maintainAspect = it })
                        Text("Maintain Aspect Ratio")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enableRotate, onCheckedChange = { enableRotate = it })
                    Text("Rotate")
                }
                if (enableRotate) {
                    OutlinedTextField(
                        value = rotation.toString(),
                        onValueChange = { rotation = it.toFloatOrNull() ?: 0f },
                        label = { Text("Degrees") }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enableCrop, onCheckedChange = { enableCrop = it })
                    Text("Crop")
                }
                if (enableCrop) {
                    Row {
                        OutlinedTextField(
                            value = cropX.toString(),
                            onValueChange = { cropX = it.toIntOrNull() ?: 0 },
                            label = { Text("X") },
                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                        )
                        OutlinedTextField(
                            value = cropY.toString(),
                            onValueChange = { cropY = it.toIntOrNull() ?: 0 },
                            label = { Text("Y") },
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        )
                    }
                    Row {
                        OutlinedTextField(
                            value = cropWidth.toString(),
                            onValueChange = { cropWidth = it.toIntOrNull() ?: 50 },
                            label = { Text("Width") },
                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                        )
                        OutlinedTextField(
                            value = cropHeight.toString(),
                            onValueChange = { cropHeight = it.toIntOrNull() ?: 50 },
                            label = { Text("Height") },
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enableGrayscale, onCheckedChange = { enableGrayscale = it })
                    Text("Grayscale")
                }

                if (enableUndoRedo && history != null) {
                    Spacer(Modifier.height(16.dp))
                    Row {
                        Button(
                            onClick = {
                                history.undo()?.let { snapshot ->
                                    item.applyTransformations(snapshot)
                                }
                            },
                            enabled = canUndo
                        ) {
                            Text("Undo")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                history.redo()?.let { snapshot ->
                                    item.applyTransformations(snapshot)
                                }
                            },
                            enabled = canRedo
                        ) {
                            Text("Redo")
                        }
                    }
                }
            }
        }
    )
}
