package com.solidkey.painpoints.depth

import kotlinx.serialization.Serializable

/**
 * Pinhole camera for a flat-ground perspective scene.
 *
 * Coordinate system: X = horizontal, Z = depth (0 = screen plane, positive = away from viewer).
 * Ground is a flat plane at world Y = 0; the camera sits at [cameraHeight] above it.
 *
 * All distances are in the same unit as the canvas (dp-pixels at draw time).
 */
@Serializable
data class OGPerspectiveCamera(
    /** Eye-to-projection-plane distance. Lower = wider FOV, more dramatic convergence. */
    val focalLength: Float = 300f,
    /** Camera elevation above the ground plane in world units. */
    val cameraHeight: Float = 120f,
    /** Horizon line as a fraction of canvas height (0 = top, 1 = bottom). */
    val horizonFraction: Float = 0.42f,
)

/**
 * Perspective scale at [worldZ]: the fraction of native size world objects appear.
 * Returns 1.0 at the near plane (Z ≈ 0) and approaches 0 as Z → ∞.
 */
fun OGPerspectiveCamera.perspectiveScale(worldZ: Float): Float =
    focalLength / (focalLength + worldZ.coerceAtLeast(0.5f))

/**
 * Projects world X to screen X.
 *
 * @param worldX  Lateral world position (0 = road centre).
 * @param worldZ  Depth from the camera.
 * @param vpX     Screen X coordinate of the vanishing point.
 */
fun OGPerspectiveCamera.projectX(worldX: Float, worldZ: Float, vpX: Float): Float =
    vpX + worldX * perspectiveScale(worldZ)

/**
 * World Z corresponding to screen scanline [screenY] on the ground plane.
 * Returns [Float.MAX_VALUE] for scanlines at or above the horizon (no ground intersection).
 */
fun OGPerspectiveCamera.groundZAt(screenY: Float, horizonY: Float): Float {
    val dy = screenY - horizonY
    return if (dy <= 0f) Float.MAX_VALUE else focalLength * cameraHeight / dy
}

/**
 * Screen Y of a world point at depth [worldZ] on the ground plane.
 * Returns [Float.MAX_VALUE] for Z ≤ 0 (behind the camera).
 */
fun OGPerspectiveCamera.screenYAt(worldZ: Float, horizonY: Float): Float {
    if (worldZ <= 0f) return Float.MAX_VALUE
    return horizonY + focalLength * cameraHeight / worldZ
}
