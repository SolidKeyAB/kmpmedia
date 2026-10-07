package com.solidkey.painpoints.fx

import kotlin.math.floor

/**
 * **Organic procedural noise** for the FX family — 2D **simplex noise** (Perlin 2002; after Stefan
 * Gustavson's "Simplex Noise Demystified") plus fractal Brownian motion ([fbm]). Simplex has no
 * axis-aligned directional artefacts and a smooth, continuous gradient everywhere, unlike the plain
 * integer-hash *value* noise the first cut of the slash edge used — so a flame edge licks and a water
 * edge ripples organically instead of just wobbling.
 *
 * It is pure integer/float maths over a permutation table built once from a fixed seed, so it is
 * allocation-free on the hot path, deterministic, and **identical on Android & iOS** — the same perf
 * gate the rest of the library clears. Sampling a position along the ribbon against time gives a field
 * that flows.
 *
 * See `docs/SLASH.md` (References) — Perlin, "Improving Noise" (2002); Gustavson, "Simplex Noise
 * Demystified" (2005).
 */
internal object OGFxNoise {

    private const val F2 = 0.3660254037844386f   // 0.5 * (sqrt(3) - 1)
    private const val G2 = 0.21132486540518713f  // (3 - sqrt(3)) / 6

    // 12 gradient directions (grad3 projected to 2D), the standard simplex set.
    private val GX = floatArrayOf(1f, -1f, 1f, -1f, 1f, -1f, 1f, -1f, 0f, 0f, 0f, 0f)
    private val GY = floatArrayOf(1f, 1f, -1f, -1f, 0f, 0f, 0f, 0f, 1f, -1f, 1f, -1f)

    // Permutation table (duplicated to 512), shuffled once from a fixed seed → identical everywhere.
    private val perm = IntArray(512)

    init {
        val p = IntArray(256) { it }
        var s = 1234567
        for (i in 255 downTo 1) {
            s = (s * 1103515245 + 12345) and 0x7fffffff // deterministic LCG
            val j = s % (i + 1)
            val t = p[i]; p[i] = p[j]; p[j] = t
        }
        for (i in 0 until 512) perm[i] = p[i and 255]
    }

    /** 2D simplex noise in roughly `[-1, 1]`. Pure + deterministic. */
    fun noise2(xin: Float, yin: Float): Float {
        val s = (xin + yin) * F2
        val i = floor(xin + s).toInt()
        val j = floor(yin + s).toInt()
        val t = (i + j) * G2
        val x0 = xin - (i - t)
        val y0 = yin - (j - t)
        val i1: Int; val j1: Int
        if (x0 > y0) { i1 = 1; j1 = 0 } else { i1 = 0; j1 = 1 }
        val x1 = x0 - i1 + G2
        val y1 = y0 - j1 + G2
        val x2 = x0 - 1f + 2f * G2
        val y2 = y0 - 1f + 2f * G2
        val ii = i and 255
        val jj = j and 255
        val gi0 = perm[ii + perm[jj]] % 12
        val gi1 = perm[ii + i1 + perm[jj + j1]] % 12
        val gi2 = perm[ii + 1 + perm[jj + 1]] % 12
        return 70f * (corner(x0, y0, gi0) + corner(x1, y1, gi1) + corner(x2, y2, gi2))
    }

    private fun corner(x: Float, y: Float, gi: Int): Float {
        var t = 0.5f - x * x - y * y
        if (t < 0f) return 0f
        t *= t
        return t * t * (GX[gi] * x + GY[gi] * y)
    }

    /**
     * Fractal Brownian motion: sum [octaves] of [noise2] at doubling frequency / halving amplitude,
     * offset by [seed] for a different-but-repeatable field. Normalized to roughly `[-1, 1]`.
     */
    fun fbm(x: Float, y: Float, octaves: Int = 4, seed: Int = 0): Float {
        val ox = seed * 0.1234f
        var amp = 1f; var freq = 1f; var sum = 0f; var norm = 0f
        repeat(octaves.coerceIn(1, 8)) {
            sum += amp * noise2(x * freq + ox, y * freq + ox)
            norm += amp
            amp *= 0.5f; freq *= 2f
        }
        return if (norm > 0f) sum / norm else 0f
    }
}
