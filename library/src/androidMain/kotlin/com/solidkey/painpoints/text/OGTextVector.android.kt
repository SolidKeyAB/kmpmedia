package com.solidkey.painpoints.text

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import com.solidkey.painpoints.shape.OGPoint
import kotlin.math.max

/** Reference text size: vectorize at 1 em = [EM] px, then divide coordinates to get em units. */
private const val EM = 1000f

actual fun ogVectorizeText(text: String, font: OGTextFont, quality: Int): OGTextOutline {
    if (text.isEmpty()) return OGTextOutline.Empty

    val style = when {
        font.bold && font.italic -> Typeface.BOLD_ITALIC
        font.bold -> Typeface.BOLD
        font.italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    val base = if (font.family != null) Typeface.create(font.family, style) else Typeface.create(Typeface.DEFAULT, style)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = base
        textSize = EM
    }

    val path = Path()
    paint.getTextPath(text, 0, text.length, 0f, 0f, path)

    val bounds = RectF()
    path.computeBounds(bounds, true)
    val widthEm = paint.measureText(text) / EM
    val topEm = bounds.top / EM
    val bottomEm = bounds.bottom / EM

    val contours = ArrayList<List<OGPoint>>()
    val pm = PathMeasure(path, false)
    val pos = FloatArray(2)
    do {
        val len = pm.length
        if (len > 0f) {
            val steps = max(8, (len / EM * quality).toInt())
            val pts = ArrayList<OGPoint>(steps)
            for (i in 0 until steps) {
                val d = len * i / steps
                if (pm.getPosTan(d, pos, null)) pts.add(OGPoint(pos[0] / EM, pos[1] / EM))
            }
            if (pts.size >= 2) contours.add(pts)
        }
    } while (pm.nextContour())

    return OGTextOutline(contours, widthEm, topEm, bottomEm)
}
