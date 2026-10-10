package com.solidkey.painpoints.motion

/** A pair of axis scales to drop on a `graphicsLayer` (`scaleX` / `scaleY`). */
data class OGScale(val scaleX: Float, val scaleY: Float)

/**
 * **Squash & stretch** — the most recognizable animation principle: a thing stretches along its
 * motion when fast and squashes when it stops / hits, while keeping its apparent volume. [fromSpeed]
 * turns a signed speed (e.g. a spring's [OGSpringValue.velocity]) into an [OGScale] where
 * `scaleX * scaleY == 1` (volume-preserving), so applying it on a `graphicsLayer` never changes the
 * footprint. Pure maths, no allocation beyond the small result.
 *
 * ```
 * val s = OGSquash.fromSpeed(spring.velocity, intensity = 0.25f)
 * Modifier.graphicsLayer { scaleX = s.scaleX; scaleY = s.scaleY }
 * ```
 */
object OGSquash {
    /**
     * Stretch along the motion axis for a positive [speed], squash for negative, scaled by [intensity]
     * and clamped to ±[max]. The cross axis takes the reciprocal so area is preserved. `speed = 0`
     * returns `(1, 1)`. By default the vertical axis stretches (good for a jump / bounce); pass
     * `vertical = false` to stretch horizontally instead.
     */
    fun fromSpeed(speed: Float, intensity: Float = 0.3f, max: Float = 0.6f, vertical: Boolean = true): OGScale {
        val s = (intensity * speed).coerceIn(-max, max)
        val along = 1f + s
        val cross = 1f / along
        return if (vertical) OGScale(cross, along) else OGScale(along, cross)
    }
}
