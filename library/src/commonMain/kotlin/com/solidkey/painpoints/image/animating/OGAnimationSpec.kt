package com.solidkey.painpoints.image.animating

data class OGAnimationSpec(
    val type: OGAnimationType,
    val duration: Int = 500, // Default duration in milliseconds
    val delay: Int = 0, // Delay before animation starts
)

enum class OGAnimationType {
    SCALE, ROTATE, FADE, TRANSLATE
}
