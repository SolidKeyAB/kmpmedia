package com.solidkey.painpoints.text

import kotlin.test.Test
import kotlin.test.assertTrue

/** Exercises the real Skia-backed [ogVectorizeText] on the iOS simulator (it can't run in JVM unit tests). */
class OGTextVectorIosTest {

    @Test
    fun vectorize_producesFiniteContours_forALetter() {
        val outline = ogVectorizeText("A")
        assertTrue(outline.contours.isNotEmpty(), "a capital A should vectorize to at least one contour")
        assertTrue(outline.widthEm > 0f, "advance width should be positive")
        assertTrue(outline.heightEm > 0f, "inked height should be positive")
        assertTrue(
            outline.contours.all { c -> c.size >= 2 && c.all { it.x.isFinite() && it.y.isFinite() } },
            "every contour must have ≥2 finite points",
        )
    }

    @Test
    fun vectorize_emptyString_isEmpty() {
        assertTrue(ogVectorizeText("").contours.isEmpty())
    }
}
