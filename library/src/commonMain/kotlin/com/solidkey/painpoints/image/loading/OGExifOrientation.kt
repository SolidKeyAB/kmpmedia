package com.solidkey.painpoints.image.loading

/**
 * The geometric correction an EXIF orientation tag asks a viewer to apply so a photo shows upright.
 *
 * Phone cameras store the raw sensor pixels and record *how the phone was held* in EXIF tag `0x0112`
 * (`Orientation`). Platform bitmap decoders (`BitmapFactory` on Android, the raw data path on iOS)
 * hand back those raw pixels, so a photo shot in portrait comes out sideways unless this correction
 * is applied.
 *
 * The correction is modelled as **mirror horizontally (if [mirrored]), then rotate clockwise by
 * [rotationDegrees]** — that order and direction, applied consistently, reproduces all eight EXIF
 * orientation values. Kept in `commonMain` so the mapping is one tested source of truth; Android
 * applies it with a `Matrix`, iOS lets UIKit normalise while redrawing.
 */
data class OGExifTransform(val rotationDegrees: Int, val mirrored: Boolean) {
    /** No correction needed (upright already) — callers can skip the costly redraw. */
    val isIdentity: Boolean get() = rotationDegrees == 0 && !mirrored
}

/**
 * The eight EXIF `Orientation` values and the [OGExifTransform] that restores each to upright.
 *
 * Values 5 and 7 (transpose / transverse) are the ones most libraries get subtly wrong; the mapping
 * here matches Pillow's authoritative `exif_transpose` table and is pinned by geometry tests in
 * `OGExifOrientationTest`.
 */
object OGExifOrientation {
    const val NORMAL = 1
    const val FLIP_HORIZONTAL = 2
    const val ROTATE_180 = 3
    const val FLIP_VERTICAL = 4
    const val TRANSPOSE = 5
    const val ROTATE_90 = 6
    const val TRANSVERSE = 7
    const val ROTATE_270 = 8

    /**
     * Map an EXIF orientation value to the mirror + clockwise rotation needed to display the stored
     * pixels upright. Unknown / absent values (including [NORMAL]) fall back to the identity transform.
     */
    fun transformFor(orientation: Int): OGExifTransform = when (orientation) {
        FLIP_HORIZONTAL -> OGExifTransform(rotationDegrees = 0, mirrored = true)
        ROTATE_180 -> OGExifTransform(rotationDegrees = 180, mirrored = false)
        FLIP_VERTICAL -> OGExifTransform(rotationDegrees = 180, mirrored = true)
        TRANSPOSE -> OGExifTransform(rotationDegrees = 270, mirrored = true)
        ROTATE_90 -> OGExifTransform(rotationDegrees = 90, mirrored = false)
        TRANSVERSE -> OGExifTransform(rotationDegrees = 90, mirrored = true)
        ROTATE_270 -> OGExifTransform(rotationDegrees = 270, mirrored = false)
        else -> OGExifTransform(rotationDegrees = 0, mirrored = false) // NORMAL / unknown
    }
}
