package com.solidkey.painpoints.image.loading

/**
 * A small, **stable** (same input → same output across platforms and launches) hash used to turn an
 * image / GIF URL into a safe, collision-resistant disk-cache filename.
 *
 * `String.hashCode()` is only 32-bit and invites collisions in a cache (a collision would serve the
 * *wrong* image), so this concatenates two 64-bit FNV-1a hashes — the plain string and a salted
 * variant — into a 128-bit hex string. The collision probability for a per-app image cache is then
 * negligible. Kept in `commonMain` so both platforms key the cache identically.
 */
internal object OGStableHash {
    private val FNV_OFFSET = 0xcbf29ce484222325uL.toLong()
    private const val FNV_PRIME = 0x100000001b3L

    private fun fnv1a(s: String): Long {
        var h = FNV_OFFSET
        for (c in s) {
            h = h xor (c.code.toLong() and 0xFFFF)
            h *= FNV_PRIME
        }
        return h
    }

    /** A 32-char lowercase hex (128-bit) digest of [input], safe to use as a filename. */
    fun hex(input: String): String {
        val a = fnv1a(input)
        val b = fnv1a("og-salt$input")
        return a.toULong().toString(16).padStart(16, '0') +
            b.toULong().toString(16).padStart(16, '0')
    }
}
