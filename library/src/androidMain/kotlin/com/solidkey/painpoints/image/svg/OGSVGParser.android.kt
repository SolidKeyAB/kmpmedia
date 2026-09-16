package com.solidkey.painpoints.image.svg

import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.svg.animation.OGSVGAnimation
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory


actual class OGSVGDocument actual constructor(svgContent: String) {
    actual val root: OGSVGElement

    init {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(InputSource(StringReader(svgContent)))
        root = OGSVGElement(document.documentElement)

        logDocument(document.documentElement)
    }

    private fun logDocument(node: Node, depth: Int = 0) {
        val indent = "  ".repeat(depth)
        val nodeName = node.nodeName
        val nodeId = (node as? Element)?.getAttribute("id").orEmpty()

        val attributes = node.attributes?.let { attributesMap ->
            (0 until attributesMap.length)
                .mapNotNull { attributesMap.item(it) }
                .joinToString(", ") { "${it.nodeName}=\"${it.nodeValue}\"" }
        } ?: "No attributes"

        Logger.e("${indent}- $nodeName (ID: $nodeId) Attributes: [$attributes]")

        // Recursively log all child nodes
        val children = node.childNodes
        for (i in 0 until children.length) {
            logDocument(children.item(i), depth + 1)
        }
    }

}

actual class OGSVGElement(private val element: org.w3c.dom.Element) {
    actual val tagName: String = element.tagName

    actual val children: List<OGSVGElement>
        get() = (0 until element.childNodes.length)
            .mapNotNull { element.childNodes.item(it) as? org.w3c.dom.Element }
            .map { OGSVGElement(it) }

    actual fun getAttribute(name: String): String? = element.getAttribute(name)

    actual fun getAllAttributes(): MutableMap<String, String> {
        val attributesMap = mutableMapOf<String, String>()
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if (attr != null) {
                attributesMap[attr.nodeName] = attr.nodeValue
            }
        }
        return attributesMap
    }

    actual fun setAttribute(name: String, value: String) {
    }

    actual var animations: MutableList<OGSVGAnimation>? = null
}

actual fun parseSVGDocument(svgContent: String): OGSVGDocument {
    return OGSVGDocument(svgContent)
}


