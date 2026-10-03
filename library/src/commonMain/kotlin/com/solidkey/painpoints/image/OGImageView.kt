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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import com.solidkey.painpoints.image.gif.rememberOGAnimatedPainter
import com.solidkey.painpoints.image.layering.OGLayerItemEvent
import com.solidkey.painpoints.image.loading.OGImageLoader
import com.solidkey.painpoints.image.processing.OGImageProcessor
import com.solidkey.painpoints.image.processing.OGImageTransformation
import com.solidkey.painpoints.image.processing.OGImageTransformation.Resize
import com.solidkey.painpoints.mask.ogSoftMask
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
    // ✂️ Free-form clip override. When non-null (e.g. an OGPolygonShape lasso) it takes precedence
    // over displayShape/cornerRadius and masks the photo to that arbitrary outline — the same GPU
    // clip, just any Path. Use it to keep only a hand-/AI-outlined region (e.g. lasso a head).
    clipShape: Shape? = null,
    // 🪶 Soft (feathered) edge, in dp. > 0 replaces the hard clip with a mask that fades the photo
    // out over this band near the shape's boundary (a feathered vignette) instead of a crisp cut.
    // 0.dp = unchanged hard clip. Works on photos + GIFs; see com.solidkey.painpoints.mask.
    softEdge: Dp = 0.dp,
    // 🌈 Arbitrary gradient alpha mask. A Brush (linear/radial/vertical) running to Color.Transparent
    // fades the photo along it — an edge fade, a spotlight, a vignette. Only the brush's alpha matters.
    // Combined with the shape clip (and softEdge) in one offscreen pass. null = no gradient mask.
    maskBrush: Brush? = null,
    transformations: SnapshotStateList<OGImageTransformation> = mutableStateListOf(),
    draggable: Boolean = false,
    x: MutableState<Float>? = null,
    y: MutableState<Float>? = null,
    zIndex: Float = 0f,
    label: OGLayerItemLabel = OGLayerItemLabel(),
    gestureHandler: OGItemGestureHandler? = null,
    onError: ((String) -> Unit)? = null,
    // ♿ Accessibility label read aloud by screen readers (TalkBack / VoiceOver). null = decorative,
    // so the reader skips it — the right default for a purely visual image. Set it to the photo's
    // meaning ("Profile photo of Ada") whenever the image conveys information.
    contentDescription: String? = null,
    // ⏳ Shown while the image is still loading, before the first bitmap/frame arrives. null keeps the
    // previous behavior (a neutral light-grey box). Use it for a spinner or a blurred thumbnail. It is
    // drawn inside the same shape clip / soft mask as the image, so a placeholder fills the shape too.
    placeholder: (@Composable () -> Unit)? = null,
    // ⚠️ Shown when the image fails to load (bad URL, decode error, missing/unsupported file) — the
    // visual partner to [onError], which still fires. null keeps the previous behavior (a solid
    // fallback colour). Also shown if the source location can't be resolved at all.
    error: (@Composable () -> Unit)? = null,
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

    // ⚠️ Did this source fail to load? Reset whenever the source changes. Drives the [error] slot and
    // keeps [onError] firing as before. We wrap the caller's [onError] so the slot and the callback
    // stay in lock-step from a single failure signal.
    var failed by remember(imageId) { mutableStateOf(false) }
    val effectiveOnError: (String) -> Unit = { message ->
        if (!failed) failed = true
        onError?.invoke(message)
    }

    if (location == null) {
        effectiveOnError("❌ Failed to resolve image location.")
        if (error != null) {
            Box(modifier, contentAlignment = Alignment.Center) { error() }
        }
        return
    }

    val imageSource = when (source.getType()) {
        OGSourceType.SourceType.URL -> OGSource.Url(location)
        OGSourceType.SourceType.FILE -> OGSource.FilePath(location)
        OGSourceType.SourceType.RESOURCE -> OGSource.Resource(location)
    }

    // 🎞️ Animated GIF path. A `.gif` source is played as a looping animation instead of a static
    // decode (which only ever shows the first frame). Detection is by extension, so URL/file GIFs
    // animate on both platforms and iOS resource GIFs too (their location resolves to a full path);
    // static transformations don't apply to GIFs, but the shape clip / contentScale / alignment
    // below still do. Everything else is unchanged for non-GIF sources.
    val isGif = remember(location) {
        location.substringBefore('?').substringAfterLast('.', "").equals("gif", ignoreCase = true)
    }
    val gifPainter = if (isGif) rememberOGAnimatedPainter(imageSource, onError = effectiveOnError) else null

    // Load image (static path only — GIFs are handled by [gifPainter] above).
    if (!isGif) {
        OGImageLoader.loadImage(imageSource, effectiveOnError) { painter ->
            imagePainter = painter
            transformedPainter = painter
        }
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
    val isShaped = clipShape != null || displayShape != OGShapeType.RECTANGLE || cornerRadius > 0.dp
    val builtInShape = remember(displayShape, cornerRadius) { displayShape.toShape(cornerRadius) }
    val effectiveShape = clipShape ?: builtInShape

    // 🪶🌈 Soft mask path: a feathered edge and/or a gradient brush replaces the hard GPU clip with a
    // single offscreen DstIn pass (drawn once for a still photo = no per-frame cost). When neither is
    // set this is the exact hard-clip behavior as before, so existing callers are unaffected.
    val isSoftMasked = softEdge > 0.dp || maskBrush != null

    Box(
        modifier = modifier
            .zIndex(zIndex) // ✅ apply stacking order
            .then(
                when {
                    isSoftMasked -> Modifier.ogSoftMask(shape = effectiveShape, feather = softEdge, brush = maskBrush)
                    isShaped -> Modifier.clip(effectiveShape) // 🔷 crop photo into shape (hard edge)
                    else -> Modifier
                }
            )
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
        // The painter we'd draw if loaded: the GIF painter, or the (optionally transformed) still.
        val displayPainter = if (isGif) gifPainter else (transformedPainter ?: imagePainter)
        // When shaped or soft-masked, fill the (sized) box so contentScale = Crop can pick the piece.
        val imageModifier = if (isShaped || isSoftMasked) Modifier.fillMaxSize() else sizeModifier

        when (ogImagePhase(hasPainter = displayPainter != null, failed = failed)) {
            // ⚠️ Failed: prefer the caller's error slot; otherwise keep the old solid-colour fallback.
            OGImagePhase.Error ->
                if (error != null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { error() }
                } else {
                    Image(
                        painter = displayPainter ?: ColorPainter(Color.LightGray),
                        contentDescription = contentDescription,
                        contentScale = contentScale,
                        alignment = alignment,
                        modifier = imageModifier,
                    )
                }

            // ⏳ Loading: prefer the caller's placeholder; otherwise the old neutral light-grey box.
            OGImagePhase.Loading ->
                if (placeholder != null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() }
                } else {
                    Image(
                        painter = ColorPainter(Color.LightGray),
                        contentDescription = contentDescription,
                        contentScale = contentScale,
                        alignment = alignment,
                        modifier = imageModifier,
                    )
                }

            // ✅ Loaded: draw the image with the caller's accessibility description.
            OGImagePhase.Success ->
                Image(
                    painter = displayPainter!!,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    alignment = alignment,
                    modifier = imageModifier,
                )
        }
    }
}

/**
 * The three visual states [OGImageView] can be in. Pure data so the state machine is unit-testable
 * with no Compose runtime (mirrors the library's other pure-logic helpers).
 */
internal enum class OGImagePhase { Loading, Success, Error }

/**
 * Resolve the current [OGImagePhase] from the two inputs the view tracks: whether a painter has
 * arrived ([hasPainter]) and whether the load has [failed]. A failure wins over a stale/fallback
 * painter so the error slot shows; otherwise a painter means success, and its absence means we're
 * still loading.
 */
internal fun ogImagePhase(hasPainter: Boolean, failed: Boolean): OGImagePhase = when {
    failed -> OGImagePhase.Error
    hasPainter -> OGImagePhase.Success
    else -> OGImagePhase.Loading
}
