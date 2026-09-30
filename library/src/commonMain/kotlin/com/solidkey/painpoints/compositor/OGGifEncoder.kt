package com.solidkey.painpoints.compositor

import kotlin.math.max

/**
 * One frame handed to [OGGifEncoder]: a row-major [argb] pixel array (length `width * height`, each
 * entry `0xAARRGGBB`) shown for [delayCs] centiseconds. Pixels whose alpha is below the encoder's
 * threshold become the single transparent GIF colour.
 */
class OGGifFrame(val argb: IntArray, val delayCs: Int)

/**
 * A tiny, **pure-Kotlin GIF89a encoder** — no platform APIs, no dependencies, so it produces the exact
 * same bytes on Android and iOS. It quantizes the frames to a shared ≤256-colour palette (median cut),
 * maps each pixel to the nearest palette entry (cached), LZW-compresses, and writes a looping animated
 * GIF. This is the "export" half of the roadmap's on-device compositor (Bet 3): render a composition to
 * frames, then [encode] them into one shareable file.
 *
 * It is deliberately export-time code (one allocation-heavy pass), not a per-frame render loop — a live
 * 60fps preview draws straight to the screen via `OGCompositionView`, never through here.
 */
object OGGifEncoder {

    /** Below this alpha (0..255) a pixel is treated as fully transparent. */
    const val DEFAULT_ALPHA_THRESHOLD: Int = 128

    /**
     * Encode [frames] (all `width` × `height`) into a single animated GIF89a byte array.
     *
     * @param loopCount `0` = loop forever (the usual choice), `n` = play `n` times then stop.
     * @param alphaThreshold pixels with alpha below this collapse to one transparent colour; pass a
     *   value `> 255` to force a fully opaque GIF (no transparent index) even if the source has alpha.
     */
    fun encode(
        width: Int,
        height: Int,
        frames: List<OGGifFrame>,
        loopCount: Int = 0,
        alphaThreshold: Int = DEFAULT_ALPHA_THRESHOLD,
    ): ByteArray {
        require(width > 0 && height > 0) { "GIF size must be positive, was ${width}x$height" }
        require(frames.isNotEmpty()) { "GIF needs at least one frame" }

        val pxPerFrame = width * height
        frames.forEachIndexed { i, f ->
            require(f.argb.size == pxPerFrame) {
                "frame $i has ${f.argb.size} pixels, expected $pxPerFrame ($width x $height)"
            }
        }

        val hasTransparency = alphaThreshold in 0..255 &&
            frames.any { f -> f.argb.any { (it ushr 24 and 0xFF) < alphaThreshold } }

        // Build one shared palette from the opaque pixels of every frame.
        val maxColors = if (hasTransparency) 255 else 256
        val samples = sampleOpaqueColors(frames, alphaThreshold, hasTransparency)
        val paletteRgb = medianCutPalette(samples, maxColors)
        val transparentIndex = if (hasTransparency) paletteRgb.size else -1
        val colorCount = paletteRgb.size + if (hasTransparency) 1 else 0
        val gctSize = gctSizeFor(colorCount) // power of two, 4..256
        val minCodeSize = max(2, log2(gctSize))

        val nearest = NearestColorCache(paletteRgb)

        val out = ByteBuf()
        writeHeader(out, width, height, gctSize, backgroundIndex = if (hasTransparency) transparentIndex else 0)
        writeGlobalColorTable(out, paletteRgb, gctSize)
        writeLoopExtension(out, loopCount)

        for (frame in frames) {
            writeGraphicControl(out, frame.delayCs, transparentIndex, hasTransparency)
            writeImageDescriptor(out, width, height)
            val indices = ByteArray(pxPerFrame)
            for (p in 0 until pxPerFrame) {
                val argb = frame.argb[p]
                indices[p] = if (hasTransparency && (argb ushr 24 and 0xFF) < alphaThreshold) {
                    transparentIndex.toByte()
                } else {
                    nearest.indexOf(argb and 0xFFFFFF).toByte()
                }
            }
            out.byte(minCodeSize)
            writeSubBlocks(out, lzwEncode(indices, minCodeSize))
        }

        out.byte(0x3B) // trailer
        return out.toByteArray()
    }

    // ---- palette (median cut) ----------------------------------------------------------------------

    /** Collect a bounded set of opaque RGB samples across all frames (stride-sampled for speed). */
    private fun sampleOpaqueColors(
        frames: List<OGGifFrame>,
        alphaThreshold: Int,
        hasTransparency: Boolean,
    ): IntArray {
        val cap = 16384
        val total = frames.sumOf { it.argb.size }
        val stride = max(1, total / cap)
        val acc = ArrayList<Int>(minOf(cap, total) + 1)
        var counter = 0
        for (f in frames) {
            for (argb in f.argb) {
                if (counter++ % stride != 0) continue
                if (hasTransparency && (argb ushr 24 and 0xFF) < alphaThreshold) continue
                acc.add(argb and 0xFFFFFF)
            }
        }
        if (acc.isEmpty()) acc.add(0) // all-transparent frames still need one palette colour
        return acc.toIntArray()
    }

    /**
     * Median-cut quantization: start with one box of all sample colours, repeatedly split the box with
     * the widest channel spread at its median along that channel, until [maxColors] boxes exist; each
     * box's average colour is a palette entry. Pure integer math, identical on every platform.
     */
    private fun medianCutPalette(samples: IntArray, maxColors: Int): IntArray {
        if (samples.isEmpty()) return intArrayOf(0)
        val boxes = ArrayList<IntArray>()
        boxes.add(samples)
        while (boxes.size < maxColors) {
            var bestIdx = -1
            var bestRange = -1
            for (i in boxes.indices) {
                val b = boxes[i]
                if (b.size < 2) continue
                val r = widestChannelRange(b)
                if (r > bestRange) { bestRange = r; bestIdx = i }
            }
            if (bestIdx < 0) break // every box is a single colour
            val box = boxes.removeAt(bestIdx)
            val channel = dominantChannel(box)
            val boxed = box.toTypedArray()
            boxed.sortBy { channelValue(it, channel) }
            val prim = IntArray(boxed.size) { boxed[it] }
            val mid = prim.size / 2
            boxes.add(prim.copyOfRange(0, mid))
            boxes.add(prim.copyOfRange(mid, prim.size))
        }
        return IntArray(boxes.size) { averageColor(boxes[it]) }
    }

    private fun channelValue(rgb: Int, channel: Int): Int = when (channel) {
        0 -> rgb ushr 16 and 0xFF
        1 -> rgb ushr 8 and 0xFF
        else -> rgb and 0xFF
    }

    private fun widestChannelRange(box: IntArray): Int {
        var rMin = 255; var rMax = 0; var gMin = 255; var gMax = 0; var bMin = 255; var bMax = 0
        for (c in box) {
            val r = c ushr 16 and 0xFF; val g = c ushr 8 and 0xFF; val b = c and 0xFF
            if (r < rMin) rMin = r; if (r > rMax) rMax = r
            if (g < gMin) gMin = g; if (g > gMax) gMax = g
            if (b < bMin) bMin = b; if (b > bMax) bMax = b
        }
        return maxOf(rMax - rMin, gMax - gMin, bMax - bMin)
    }

    private fun dominantChannel(box: IntArray): Int {
        var rMin = 255; var rMax = 0; var gMin = 255; var gMax = 0; var bMin = 255; var bMax = 0
        for (c in box) {
            val r = c ushr 16 and 0xFF; val g = c ushr 8 and 0xFF; val b = c and 0xFF
            if (r < rMin) rMin = r; if (r > rMax) rMax = r
            if (g < gMin) gMin = g; if (g > gMax) gMax = g
            if (b < bMin) bMin = b; if (b > bMax) bMax = b
        }
        val dr = rMax - rMin; val dg = gMax - gMin; val db = bMax - bMin
        return when {
            dr >= dg && dr >= db -> 0
            dg >= db -> 1
            else -> 2
        }
    }

    private fun averageColor(box: IntArray): Int {
        if (box.isEmpty()) return 0
        var r = 0L; var g = 0L; var b = 0L
        for (c in box) { r += c ushr 16 and 0xFF; g += c ushr 8 and 0xFF; b += c and 0xFF }
        val n = box.size
        return ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    /** Nearest-palette lookup, cached by RGB reduced to 5 bits/channel so photos stay fast. */
    private class NearestColorCache(private val palette: IntArray) {
        private val cache = HashMap<Int, Int>()
        fun indexOf(rgb: Int): Int {
            val key = rgb and 0xF8F8F8
            cache[key]?.let { return it }
            val r = key ushr 16 and 0xFF; val g = key ushr 8 and 0xFF; val b = key and 0xFF
            var best = 0; var bestD = Int.MAX_VALUE
            for (i in palette.indices) {
                val p = palette[i]
                val dr = r - (p ushr 16 and 0xFF)
                val dg = g - (p ushr 8 and 0xFF)
                val db = b - (p and 0xFF)
                val d = dr * dr + dg * dg + db * db
                if (d < bestD) { bestD = d; best = i; if (d == 0) break }
            }
            cache[key] = best
            return best
        }
    }

    // ---- GIF structure ------------------------------------------------------------------------------

    private fun writeHeader(out: ByteBuf, width: Int, height: Int, gctSize: Int, backgroundIndex: Int) {
        out.ascii("GIF89a")
        out.short(width); out.short(height)
        // packed: GCT flag=1, colour resolution=7, sort=0, GCT size = log2(gctSize)-1
        val packed = 0x80 or (0x7 shl 4) or (log2(gctSize) - 1)
        out.byte(packed)
        out.byte(backgroundIndex.coerceIn(0, gctSize - 1))
        out.byte(0) // pixel aspect ratio
    }

    private fun writeGlobalColorTable(out: ByteBuf, palette: IntArray, gctSize: Int) {
        for (i in 0 until gctSize) {
            val c = if (i < palette.size) palette[i] else 0
            out.byte(c ushr 16 and 0xFF); out.byte(c ushr 8 and 0xFF); out.byte(c and 0xFF)
        }
    }

    private fun writeLoopExtension(out: ByteBuf, loopCount: Int) {
        out.byte(0x21); out.byte(0xFF); out.byte(0x0B)
        out.ascii("NETSCAPE2.0")
        out.byte(0x03); out.byte(0x01)
        out.short(loopCount.coerceIn(0, 0xFFFF))
        out.byte(0x00)
    }

    private fun writeGraphicControl(out: ByteBuf, delayCs: Int, transparentIndex: Int, hasTransparency: Boolean) {
        out.byte(0x21); out.byte(0xF9); out.byte(0x04)
        // disposal method 2 (restore to background) so transparent holes never accumulate.
        val disposal = if (hasTransparency) 2 else 1
        val packed = (disposal shl 2) or (if (hasTransparency) 1 else 0)
        out.byte(packed)
        out.short(delayCs.coerceIn(0, 0xFFFF))
        out.byte(if (hasTransparency) transparentIndex else 0)
        out.byte(0x00)
    }

    private fun writeImageDescriptor(out: ByteBuf, width: Int, height: Int) {
        out.byte(0x2C)
        out.short(0); out.short(0)        // left, top
        out.short(width); out.short(height)
        out.byte(0x00)                    // no local colour table
    }

    /** Chunk LZW output into ≤255-byte sub-blocks, then a zero-length terminator. */
    private fun writeSubBlocks(out: ByteBuf, data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val n = minOf(255, data.size - offset)
            out.byte(n)
            out.bytes(data, offset, n)
            offset += n
        }
        out.byte(0x00)
    }

    // ---- LZW (variable-width, LSB-first — the canonical GIF stream) ----------------------------------

    private fun lzwEncode(indices: ByteArray, minCodeSize: Int): ByteArray {
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        val bits = BitWriter()
        var codeSize = minCodeSize + 1
        var nextCode = endCode + 1
        val dict = HashMap<Int, Int>()

        bits.write(clearCode, codeSize)
        if (indices.isEmpty()) { bits.write(endCode, codeSize); return bits.toByteArray() }

        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val k = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val existing = dict[key]
            if (existing != null) {
                prefix = existing
            } else {
                bits.write(prefix, codeSize)
                if (nextCode == 4096) {
                    bits.write(clearCode, codeSize)
                    dict.clear()
                    codeSize = minCodeSize + 1
                    nextCode = endCode + 1
                } else {
                    if (nextCode >= (1 shl codeSize)) codeSize++
                    dict[key] = nextCode
                    nextCode++
                }
                prefix = k
            }
        }
        bits.write(prefix, codeSize)
        bits.write(endCode, codeSize)
        return bits.toByteArray()
    }

    // ---- little-endian bit / byte writers -----------------------------------------------------------

    /** Packs variable-width codes LSB-first into a byte stream (GIF's bit order). */
    private class BitWriter {
        private val buf = ByteBuf()
        private var cur = 0
        private var bits = 0
        fun write(code: Int, size: Int) {
            cur = cur or (code shl bits)
            bits += size
            while (bits >= 8) {
                buf.byte(cur and 0xFF)
                cur = cur ushr 8
                bits -= 8
            }
        }
        fun toByteArray(): ByteArray {
            if (bits > 0) { buf.byte(cur and 0xFF); cur = 0; bits = 0 }
            return buf.toByteArray()
        }
    }

    /** Growable byte buffer (no boxing), the one place raw bytes are accumulated. */
    private class ByteBuf(initial: Int = 4096) {
        private var arr = ByteArray(initial)
        private var len = 0
        fun byte(v: Int) {
            ensure(1)
            arr[len++] = v.toByte()
        }
        fun bytes(src: ByteArray, offset: Int, count: Int) {
            ensure(count)
            src.copyInto(arr, len, offset, offset + count)
            len += count
        }
        fun short(v: Int) { byte(v and 0xFF); byte(v ushr 8 and 0xFF) } // little-endian
        fun ascii(s: String) { for (c in s) byte(c.code and 0xFF) }
        private fun ensure(extra: Int) {
            if (len + extra <= arr.size) return
            var cap = arr.size * 2
            while (cap < len + extra) cap *= 2
            arr = arr.copyOf(cap)
        }
        fun toByteArray(): ByteArray = arr.copyOf(len)
    }

    /** GIF global-colour-table size: the smallest power of two in 4..256 that holds [colorCount]. */
    private fun gctSizeFor(colorCount: Int): Int {
        var size = 4
        while (size < colorCount && size < 256) size = size shl 1
        return size
    }

    private fun log2(v: Int): Int {
        var n = v; var r = 0
        while (n > 1) { n = n shr 1; r++ }
        return r
    }
}
