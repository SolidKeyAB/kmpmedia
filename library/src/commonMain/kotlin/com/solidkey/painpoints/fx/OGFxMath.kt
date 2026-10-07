package com.solidkey.painpoints.fx

/** Tiny shared math for the FX family — a deterministic integer hash and a lerp, reused across the
 *  action-FX primitives (lightning, speed-lines, afterimage) so their randomness is RNG-free and
 *  frame-identical on every platform. */
internal object OGFxMath {

    /** Linear interpolate. */
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Deterministic integer hash → `[0, 1)`. */
    fun hash01(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 1274126177 + 0x9E3779B1.toInt()
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return ((h ushr 8) and 0xFFFFFF) / 16_777_216f
    }

    /** Deterministic signed hash → `[-1, 1)`. */
    fun hashSigned(a: Int, b: Int): Float = hash01(a, b) * 2f - 1f
}
