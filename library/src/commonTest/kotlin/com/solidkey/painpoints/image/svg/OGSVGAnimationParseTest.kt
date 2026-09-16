package com.solidkey.painpoints.image.svg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [parseAnimationElement] and [com.solidkey.painpoints.image.svg.animation.OGSVGAnimation]
 * interpolation.
 *
 * Regression: animated SVGs never rendered because
 *  1. animation attributes with vector `from`/`to` ("0,0" → "0,400") and the
 *     `<animateTransform type=...>` kind were not parsed at all, and
 *  2. `<animate>` nodes were dropped from the tree (see the tree test).
 * These tests lock in the parsing side.
 */
class OGSVGAnimationParseTest {

    @Test
    fun animateTransformTranslateParsesVectorFromTo() {
        val a = parseAnimationElement(
            "animateTransform",
            mapOf(
                "attributeName" to "transform",
                "type" to "translate",
                "from" to "0,0",
                "to" to "0,400",
                "dur" to "2s",
                "repeatCount" to "indefinite"
            )
        )!!
        assertEquals("transform", a.attributeName)
        assertEquals("translate", a.type)
        assertEquals(listOf(0f, 0f), a.fromVec)
        assertEquals(listOf(0f, 400f), a.toVec)
        assertEquals(2f, a.duration)
        assertEquals("indefinite", a.repeatCount)
    }

    @Test
    fun animateTransformRotateParsesAngle() {
        val a = parseAnimationElement(
            "animateTransform",
            mapOf("attributeName" to "transform", "type" to "rotate", "from" to "0", "to" to "360", "dur" to "1.8s")
        )!!
        assertEquals("rotate", a.type)
        assertEquals(0f, a.from)
        assertEquals(360f, a.to)
        // rotate 0 -> 360, half way = 180 degrees
        assertEquals(180f, a.getInterpolatedValue(0.5f)!!, 0.001f)
    }

    @Test
    fun animateAttributeXParsesScalar() {
        val a = parseAnimationElement(
            "animate",
            mapOf("attributeName" to "x", "from" to "0", "to" to "50", "dur" to "2s")
        )!!
        assertEquals("x", a.attributeName)
        assertNull(a.type)
        assertEquals(0f, a.from)
        assertEquals(50f, a.to)
        assertEquals(12.5f, a.getInterpolatedValue(0.25f)!!, 0.001f)
    }

    @Test
    fun translateVectorInterpolatesComponentWise() {
        val a = parseAnimationElement(
            "animateTransform",
            mapOf("attributeName" to "transform", "type" to "translate", "from" to "0,0", "to" to "0,400", "dur" to "2s")
        )!!
        val mid = a.getInterpolatedVec(0.5f)!!
        assertEquals(0f, mid[0], 0.001f)
        assertEquals(200f, mid[1], 0.001f)
    }

    @Test
    fun multiStepTranslateValuesParseAsVectorKeyframes() {
        // A there-and-back bounce: down then back up. Must NOT snap on loop, so the
        // first and last keyframes match and the midpoint is the far end.
        val a = parseAnimationElement(
            "animateTransform",
            mapOf(
                "attributeName" to "transform",
                "type" to "translate",
                "values" to "0,-30; 0,30; 0,-30",
                "dur" to "1s"
            )
        )!!
        assertEquals(3, a.valuesVec!!.size)
        assertEquals(listOf(0f, -30f), a.valuesVec!![0])
        // Endpoints identical -> seamless loop.
        val start = a.getInterpolatedVec(0f)!!
        val end = a.getInterpolatedVec(1f)!!
        assertEquals(start[1], end[1], 0.001f)
        // Halfway through is the far (down) keyframe.
        val mid = a.getInterpolatedVec(0.5f)!!
        assertEquals(30f, mid[1], 0.001f)
        // Quarter of the way = halfway along the first segment (-30 -> 30).
        assertEquals(0f, a.getInterpolatedVec(0.25f)!![1], 0.001f)
    }

    @Test
    fun multiStepScaleValuesPulseInterpolate() {
        val a = parseAnimationElement(
            "animateTransform",
            mapOf(
                "attributeName" to "transform",
                "type" to "scale",
                "values" to "0.65,0.65; 1.2,1.2; 0.65,0.65",
                "dur" to "1.2s"
            )
        )!!
        val peak = a.getInterpolatedVec(0.5f)!!
        assertEquals(1.2f, peak[0], 0.001f)
        assertEquals(1.2f, peak[1], 0.001f)
        // Scalar `values` view still holds the first component of each keyframe.
        assertEquals(listOf(0.65f, 1.2f, 0.65f), a.values)
    }

    @Test
    fun animateMotionDefaultsToTransformAttribute() {
        val a = parseAnimationElement("animateMotion", mapOf("dur" to "3s"))!!
        assertEquals("transform", a.attributeName)
    }

    @Test
    fun animateWithoutAttributeNameIsNull() {
        assertNull(parseAnimationElement("animate", mapOf("from" to "0", "to" to "1")))
    }

    @Test
    fun defaultsApplyWhenDurAndRepeatMissing() {
        val a = parseAnimationElement("animate", mapOf("attributeName" to "x", "from" to "0", "to" to "1"))!!
        assertEquals(1f, a.duration)          // default dur = 1s
        assertEquals("indefinite", a.repeatCount) // default repeat
    }

    @Test
    fun pathAnimationKeepsRawStringsNotFloats() {
        val a = parseAnimationElement(
            "animate",
            mapOf("attributeName" to "d", "from" to "M0 0 L10 10", "to" to "M0 0 L20 20", "dur" to "1s")
        )!!
        assertEquals("d", a.attributeName)
        assertNull(a.from)
        assertNull(a.to)
        assertTrue(a.fromPath!!.startsWith("M0 0"))
        assertEquals("M0 0 L20 20", a.toPath)
    }
}
