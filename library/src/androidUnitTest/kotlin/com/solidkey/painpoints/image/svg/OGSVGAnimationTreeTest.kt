package com.solidkey.painpoints.image.svg

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end parse test (runs on the JVM via the Android `javax.xml` parser
 * actual). Locks in the core animated-SVG regression: `<animate>` /
 * `<animateTransform>` nodes are children of the shape they target, but shape
 * elements never recursed into their children, so every animation node was
 * silently dropped and nothing ever animated. Now they must be attached to the
 * shape element itself.
 */
class OGSVGAnimationTreeTest {

    private fun animatedElements(root: OGSVGTreeElement): List<OGSVGTreeElement> {
        val out = mutableListOf<OGSVGTreeElement>()
        fun walk(e: OGSVGTreeElement) {
            if (e.animations.isNotEmpty()) out.add(e)
            e.children.forEach { walk(it) }
        }
        walk(root)
        return out
    }

    @Test
    fun animateTransformChildrenAttachToTheirCircle() {
        val svg = """
            <svg width="400" height="400" viewBox="0 0 400 400" xmlns="http://www.w3.org/2000/svg">
                <circle cx="50" cy="0" r="5" fill="red">
                    <animateTransform attributeName="transform" type="translate" from="0,0" to="0,400" dur="2s" repeatCount="indefinite"/>
                    <animateTransform attributeName="transform" type="rotate" from="0" to="360" dur="2s" repeatCount="indefinite"/>
                </circle>
            </svg>
        """.trimIndent()

        val tree = assertNotNull(parseSVGPaths(svg).svgTree, "tree should parse")
        val animated = animatedElements(tree)

        assertEquals(1, animated.size, "exactly the circle should carry animations")
        val circle = animated.first()
        assertTrue(circle.shapes.firstOrNull() is OGSVGCircle, "animations must land on the circle shape")
        assertEquals(2, circle.animations.size, "both <animateTransform>s attach")

        val types = circle.animations.map { it.type }
        assertTrue(types.contains("translate"), "translate transform parsed")
        assertTrue(types.contains("rotate"), "rotate transform parsed")

        val translate = circle.animations.first { it.type == "translate" }
        assertEquals(listOf(0f, 400f), translate.toVec)
    }

    @Test
    fun animateXAttachesToNestedRect() {
        val svg = """
            <svg width="480" height="360" viewBox="0 0 480 360" xmlns="http://www.w3.org/2000/svg">
                <g transform="translate(0,0)">
                    <g>
                        <rect x="0" y="0" width="50" height="50" fill="#0f5">
                            <animate attributeName="x" from="0" to="50" dur="2s" repeatCount="indefinite"/>
                        </rect>
                    </g>
                </g>
            </svg>
        """.trimIndent()

        val tree = assertNotNull(parseSVGPaths(svg).svgTree, "tree should parse")
        val animated = animatedElements(tree)

        assertEquals(1, animated.size, "the deeply-nested rect should still be found")
        val rect = animated.first()
        assertTrue(rect.shapes.firstOrNull() is OGSVGRect, "animation must land on the rect")
        assertEquals(1, rect.animations.size)
        assertEquals("x", rect.animations.first().attributeName)
        assertEquals(50f, rect.animations.first().to)
    }

    @Test
    fun staticSvgWithoutAnimationsHasNoAnimatedElements() {
        val svg = """
            <svg width="100" height="100" viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
                <circle cx="50" cy="50" r="40" fill="blue"/>
            </svg>
        """.trimIndent()
        val tree = assertNotNull(parseSVGPaths(svg).svgTree)
        assertTrue(animatedElements(tree).isEmpty(), "no <animate> => nothing animated")
    }
}
