package com.solidkey.painpoints.cutout

import androidx.compose.ui.graphics.ImageBitmap
import com.solidkey.painpoints.shape.OGPolygonShape

/**
 * One-call auto-cutout: segment [image] with [segmenter], trace the subject, and return a live
 * [OGPolygonShape] lasso you can hand to [com.solidkey.painpoints.image.OGImageView]'s `clipShape`
 * (or any `Modifier.clip`). "Drop a photo, get the subject clipped out."
 *
 * The whole pipeline is pure/CPU and should run **off the main thread** (it reads every pixel once);
 * the returned polygon is then clipped by the GPU at zero per-frame cost. Returns `null` when nothing
 * is found (empty mask, subject too small).
 *
 * For plain-background photos pass a built-in [OGChromaKeySegmenter] / [OGLumaKeySegmenter]; for
 * cluttered scenes plug an ML segmenter (ML Kit / Vision / cloud) into [OGSegmenter]. No ML dependency
 * is pulled into the library.
 */
fun autoCutoutPolygon(
    image: ImageBitmap,
    segmenter: OGSegmenter,
    threshold: Float = 0.5f,
    simplifyTolerance: Float = 0.01f,
    maxVertices: Int = 120,
): OGPolygonShape? =
    OGMaskContour.maskToPolygon(
        mask = segmenter.segment(image),
        threshold = threshold,
        simplifyTolerance = simplifyTolerance,
        maxVertices = maxVertices,
    )
