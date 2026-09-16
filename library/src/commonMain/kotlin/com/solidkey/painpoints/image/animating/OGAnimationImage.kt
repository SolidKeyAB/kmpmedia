package com.solidkey.painpoints.image.animating

sealed class OGAnimationImage(val source: String) {
    abstract val width: Float
    abstract val height: Float
}

class OGVectorImage(source: String) : OGAnimationImage(source) {
    override val width = 0f // SVG can be scaled dynamically
    override val height = 0f
}

class OGRasterImage(source: String, override val width: Float, override val height: Float) : OGAnimationImage(source)
