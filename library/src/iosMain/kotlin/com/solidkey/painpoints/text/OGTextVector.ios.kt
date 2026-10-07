package com.solidkey.painpoints.text

import com.solidkey.painpoints.shape.OGPoint
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Path
import org.jetbrains.skia.PathVerb
import kotlin.math.max

/** Reference font size: vectorize at 1 em = [EM] units, then divide coordinates to get em units. */
private const val EM = 1000f

actual fun ogVectorizeText(text: String, font: OGTextFont, quality: Int): OGTextOutline {
    if (text.isEmpty()) return OGTextOutline.Empty

    val fontStyle = when {
        font.bold && font.italic -> FontStyle.BOLD_ITALIC
        font.bold -> FontStyle.BOLD
        font.italic -> FontStyle.ITALIC
        else -> FontStyle.NORMAL
    }
    val mgr = FontMgr.default
    val candidates = listOfNotNull(font.family, null, "Helvetica", "Helvetica Neue", "Arial", ".SF UI Text")
    val typeface = candidates.firstNotNullOfOrNull { mgr.matchFamilyStyle(it, fontStyle) }
        ?: (if (mgr.familiesCount > 0) runCatching { mgr.getFamilyName(0) }.getOrNull()?.let { mgr.matchFamilyStyle(it, fontStyle) } else null)
        ?: return OGTextOutline.Empty
    val skFont = Font(typeface, EM)

    val glyphs = skFont.getStringGlyphs(text)
    if (glyphs.isEmpty()) return OGTextOutline.Empty
    val widths = skFont.getWidths(glyphs)

    val contours = ArrayList<List<OGPoint>>()
    var penX = 0f
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    val sub = max(2, quality / 8) // bezier subdivisions per segment

    for (gi in glyphs.indices) {
        val gpath = skFont.getPath(glyphs[gi])
        if (gpath != null) {
            for (contour in flattenSkiaPath(gpath, penX, sub)) {
                contours.add(contour.map { OGPoint(it[0] / EM, it[1] / EM) })
                for (p in contour) {
                    if (p[1] < minY) minY = p[1]
                    if (p[1] > maxY) maxY = p[1]
                }
            }
        }
        penX += widths.getOrElse(gi) { 0f }
    }

    val widthEm = penX / EM
    val topEm = if (minY.isFinite()) minY / EM else 0f
    val bottomEm = if (maxY.isFinite()) maxY / EM else 0f
    return OGTextOutline(contours, widthEm, topEm, bottomEm)
}

/**
 * Flatten a Skia glyph [path] into closed polylines (each a list of `[x, y]` pairs), offset in x by
 * [offsetX], tessellating quad / conic / cubic segments into [sub] straight pieces. Skia's
 * `PathSegment` exposes the segment's start as `p0`, so no running-point state is needed.
 */
private fun flattenSkiaPath(path: Path, offsetX: Float, sub: Int): List<List<FloatArray>> {
    val out = ArrayList<MutableList<FloatArray>>()
    var cur: MutableList<FloatArray>? = null

    for (segN in path) {
        val seg = segN ?: continue
        when (seg.verb) {
            PathVerb.MOVE -> {
                cur = ArrayList()
                out.add(cur)
                val p = seg.p0!!
                cur.add(floatArrayOf(p.x + offsetX, p.y))
            }
            PathVerb.LINE -> {
                val p = seg.p1!!
                cur?.add(floatArrayOf(p.x + offsetX, p.y))
            }
            PathVerb.QUAD, PathVerb.CONIC -> {
                val a = seg.p0!!; val c = seg.p1!!; val e = seg.p2!!
                for (i in 1..sub) {
                    val t = i / sub.toFloat()
                    val u = 1f - t
                    val x = u * u * a.x + 2f * u * t * c.x + t * t * e.x
                    val y = u * u * a.y + 2f * u * t * c.y + t * t * e.y
                    cur?.add(floatArrayOf(x + offsetX, y))
                }
            }
            PathVerb.CUBIC -> {
                val a = seg.p0!!; val c1 = seg.p1!!; val c2 = seg.p2!!; val e = seg.p3!!
                for (i in 1..sub) {
                    val t = i / sub.toFloat()
                    val u = 1f - t
                    val x = u * u * u * a.x + 3f * u * u * t * c1.x + 3f * u * t * t * c2.x + t * t * t * e.x
                    val y = u * u * u * a.y + 3f * u * u * t * c1.y + 3f * u * t * t * c2.y + t * t * t * e.y
                    cur?.add(floatArrayOf(x + offsetX, y))
                }
            }
            PathVerb.CLOSE, PathVerb.DONE -> { /* contour ends; the draw path closes it */ }
        }
    }
    return out.filter { it.size >= 2 }
}
