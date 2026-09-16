package com.solidkey.painpoints.image.svg

import com.solidkey.painpoints.image.svg.animation.OGSVGAnimation
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSXMLParser
import platform.Foundation.NSXMLParserDelegateProtocol
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.darwin.NSObject

actual class OGSVGDocument actual constructor(svgContent: String) {
    actual val root: OGSVGElement

    init {
        val parserDelegate = SvgParserDelegate()
        val parser = NSXMLParser(svgContent.toNSData())

        parser.delegate = parserDelegate
        parser.parse()

        root = parserDelegate.rootElement ?: throw IllegalArgumentException("Failed to parse SVG content")
    }
}

actual class OGSVGElement(
    private val elementName: String,
    private val attributes: MutableMap<String, String>,
    private val childrenList: List<OGSVGElement>
) {
    actual val tagName: String = elementName
    actual val children: List<OGSVGElement> = childrenList
    actual var animations: MutableList<OGSVGAnimation>? = null

    actual fun getAttribute(name: String): String? = attributes[name]

    actual fun getAllAttributes(): MutableMap<String, String> = attributes
    actual fun setAttribute(name: String, value: String) {
    }
}

class SvgParserDelegate : NSObject(), NSXMLParserDelegateProtocol {
    var rootElement: OGSVGElement? = null
    private var elementStack: MutableList<SvgElementBuilder> = mutableListOf()

    override fun parser(
        parser: NSXMLParser,
        didStartElement: String,
        namespaceURI: String?,
        qualifiedName: String?,
        attributes: Map<Any?, Any?>
    ) {
        val attributesMap = attributes.mapNotNull { (key, value) ->
            val keyString = key?.toString()
            val valueString = value?.toString()
            if (keyString != null && valueString != null) {
                keyString to valueString
            } else null
        }.toMap().toMutableMap()

        // Push a new element to the stack
        elementStack.add(SvgElementBuilder(didStartElement, attributesMap))
    }

    override fun parser(
        parser: NSXMLParser,
        didEndElement: String,
        namespaceURI: String?,
        qualifiedName: String?
    ) {
        if (elementStack.isNotEmpty()) {
            val completedElement = elementStack.removeLast().build()
            if (elementStack.isEmpty()) {
                rootElement = completedElement
            } else {
                // Add the completed element as a child of the previous element
                elementStack.last().addChild(completedElement)
            }
        }
    }
}

class SvgElementBuilder(
    private val tagName: String,
    private val attributes: MutableMap<String, String>
) {
    private val children: MutableList<OGSVGElement> = mutableListOf()

    fun addChild(child: OGSVGElement) {
        children.add(child)
    }

    fun build(): OGSVGElement {
        return OGSVGElement(tagName, attributes, children)
    }
}

@OptIn(BetaInteropApi::class)
fun String.toNSData(): NSData {
    return NSString.create(string = this).dataUsingEncoding(NSUTF8StringEncoding)!!
}

actual fun parseSVGDocument(svgContent: String): OGSVGDocument {
    return OGSVGDocument(svgContent)
}
