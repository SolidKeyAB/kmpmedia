package com.solidkey.painpoints.image.svg.animation

data class OGSVGAnimation(
    val attributeName: String,
    val type: String? = null, // <animateTransform>: translate | rotate | scale | skewx | skewy
    val from: Float? = null,
    val to: Float? = null,
    val by: Float? = null,
    val values: List<Float>? = null, // Multi-step animations (scalar keyframes)
    val fromVec: List<Float>? = null, // Multi-component from (e.g. translate "x,y", rotate "angle cx cy")
    val toVec: List<Float>? = null,   // Multi-component to
    // Multi-step vector keyframes, e.g. translate values="0,0; 0,-30; 0,0" for a
    // smooth there-and-back bounce, or scale values="0.7,0.7; 1.25,1.25; 0.7,0.7".
    val valuesVec: List<List<Float>>? = null,
    val duration: Float,
    val repeatCount: String,
    val fromPath: String? = null,
    val toPath: String? = null
) {
    fun getInterpolatedValue(progress: Float): Float? {
        return when {
            values != null -> {
                // Multi-step animation: pick the closest value
                val stepIndex = (progress * (values.size - 1)).toInt().coerceIn(0, values.size - 1)
                values[stepIndex]
            }
            from != null && to != null -> from + (to - from) * progress
            from != null && by != null -> from + by * progress
            else -> null
        }
    }

    /**
     * Component-wise interpolation for multi-value transforms such as
     * `translate(x,y)`, `scale(sx,sy)` or `rotate(angle cx cy)`. Returns null
     * when this animation has no vector form (fall back to [getInterpolatedValue]).
     */
    fun getInterpolatedVec(progress: Float): List<Float>? {
        // Multi-step vector keyframes take precedence: piecewise-linear across the
        // list so translate/scale can animate there-and-back without a snap on loop.
        valuesVec?.let { keys ->
            if (keys.isEmpty()) return null
            if (keys.size == 1) return keys[0]
            val p = progress.coerceIn(0f, 1f)
            val segment = p * (keys.size - 1)
            val i = segment.toInt().coerceIn(0, keys.size - 2)
            val local = segment - i
            val a = keys[i]
            val b = keys[i + 1]
            val n = maxOf(a.size, b.size)
            return List(n) { k ->
                val av = a.getOrElse(k) { 0f }
                val bv = b.getOrElse(k) { av }
                av + (bv - av) * local
            }
        }
        val f = fromVec ?: return null
        val t = toVec ?: return f
        val n = maxOf(f.size, t.size)
        return List(n) { i ->
            val a = f.getOrElse(i) { 0f }
            val b = t.getOrElse(i) { a }
            a + (b - a) * progress
        }
    }

    fun getInterpolatedPath(progress: Float): String? {
        return when {
            fromPath != null && toPath != null -> {
                // TODO: Implement path morphing interpolation
                fromPath // Temporary: Use `fromPath` as a placeholder
            }
            else -> null
        }
    }
}

