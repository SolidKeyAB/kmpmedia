package com.solidkey.painpoints.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.solidkey.painpoints.shape.OGShapeType
import com.solidkey.painpoints.shape.toShape
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.layering.OGLayerItemEvent
import com.solidkey.painpoints.image.loading.OGImageLoader
import com.solidkey.painpoints.image.processing.OGImageProcessor
import com.solidkey.painpoints.image.processing.OGImageTransformation
import com.solidkey.painpoints.image.processing.OGImageTransformation.Resize
import com.solidkey.painpoints.layer.*
import com.solidkey.painpoints.source.OGSource
import com.solidkey.painpoints.source.OGSourceType

@Composable
fun OGImageView(
    source: OGSourceType,
    modifier: Modifier = Modifier,
    // 🔷 Crop the photo into one of the shared shape primitives (circle / triangle / diamond / …).
    // RECTANGLE + 0dp corner = no clip, so existing callers are unaffected. Pair with a sized
    // [modifier] (e.g. Modifier.size(220.dp)) and contentScale = ContentScale.Crop to fill the shape.
    displayShape: OGShapeType = OGShapeType.RECTANGLE,
    cornerRadius: Dp = 0.dp,
    contentScale: ContentScale = ContentScale.Fit,
    // 🧭 When the photo is scaled larger than the shape (e.g. contentScale = Crop), this picks WHICH
    // region stays visible — Alignment.TopCenter keeps the top (a face), BottomCenter the bottom,
    // CenterStart the left edge, etc. Center = default (unchanged behavior). It also positions a
    // letterboxed image when contentScale = Fit.
    alignment: Alignment = Alignment.Center,
    transformations: SnapshotStateList<OGImageTransformation> = mutableStateListOf(),
    draggable: Boolean = false,
    x: MutableState<Float>? = null,
    y: MutableState<Float>? = null,
    zIndex: Float = 0f,
    label: OGLayerItemLabel = OGLayerItemLabel(),
    gestureHandler: OGItemGestureHandler? = null,
    onError: ((String) -> Unit)? = null,
    onEventTriggered: (OGLayerItemEvent, String) -> Unit,
    debugVisual: Boolean = false // 🧪 NEW: enables colored box background for debug
) {
    val densityObj = LocalDensity.current            // full object for image processing
    val densityVal = densityObj.density              // just the float value for gesture delta fix

    val imageProcessor = remember { OGImageProcessor.create() }

    val location = source.getLocation()
    val imageId = location ?: "unknown"

    var imagePainter by remember { mutableStateOf<Painter?>(null) }
    var transformedPainter by remember { mutableStateOf<Painter?>(null) }

    if (location == null) {
        onError?.invoke("❌ Failed to resolve image location.")
        return
    }

    val imageSource = when (source.getType()) {
        OGSourceType.SourceType.URL -> OGSource.Url(location)
        OGSourceType.SourceType.FILE -> OGSource.FilePath(location)
        OGSourceType.SourceType.RESOURCE -> OGSource.Resource(location)
    }

    // Load image
    OGImageLoader.loadImage(imageSource, onError) { painter ->
        imagePainter = painter
        transformedPainter = painter
    }

    // Trigger transformation if any changes
    val snapshotHash = remember(transformations) {
        derivedStateOf { transformations.map { it.hashCode() }.hashCode() }
    }

    LaunchedEffect(snapshotHash.value, imagePainter) {
        transformedPainter = imagePainter?.let {
            imageProcessor.applyTransformations(it, densityObj, transformations)
        }
    }

    // Modifier for resizing based on Resize transform
    val sizeModifier by remember {
        derivedStateOf {
            transformations
                .filterIsInstance<Resize>()
                .firstOrNull()
                ?.let { Modifier.size(it.width?.dp ?: 0.dp, it.height?.dp ?: 0.dp) }
                ?: Modifier
        }
    }

    Logger.i("OG>> OGImageView[$imageId] label=${label.itemLabel} x=${x?.value}, y=${y?.value}")

    // ✅ Provide default gesture handler with density
    val effectiveGestureHandler = remember(draggable, gestureHandler, transformations, x, y, densityVal) {
        gestureHandler ?: if (draggable && x != null && y != null) {
            OGItemGestureHandler.default(
                transformations = transformations,
                x = x,
                y = y,
                density = densityVal,
                onEvent = onEventTriggered // ✅ this one
            )
        } else null
    }

    Logger.i("OG>> OGImageView[$imageId] label=${label.itemLabel}, gestures=$effectiveGestureHandler")

    // 🔷 Shape crop. RECTANGLE + 0dp corner is a no-op (default), so behavior is unchanged for
    // existing callers; any other shape masks the photo via a GPU clip (drawn once = no perf loss).
    val isShaped = displayShape != OGShapeType.RECTANGLE || cornerRadius > 0.dp
    val clipShape = remember(displayShape, cornerRadius) { displayShape.toShape(cornerRadius) }

    Box(
        modifier = modifier
            .zIndex(zIndex) // ✅ apply stacking order
            .then(if (isShaped) Modifier.clip(clipShape) else Modifier) // 🔷 crop photo into shape
            .then(
                if (effectiveGestureHandler != null)
                    Modifier.ogPointerGestureWrapper(
                        id = imageId,
                        label = label,
                        gestureHandler = effectiveGestureHandler
                    )
                else Modifier
            )
            .then(if (debugVisual) Modifier.background(Color.Red.copy(alpha = 0.3f)) else Modifier)
    ) {
        Image(
            painter = transformedPainter ?: imagePainter ?: ColorPainter(Color.LightGray),
            contentDescription = "Processed Image",
            contentScale = contentScale,
            alignment = alignment,
            // When shaped, fill the (sized) shaped box so contentScale = Crop can pick the piece.
            modifier = if (isShaped) Modifier.fillMaxSize() else sizeModifier
        )
    }
}
