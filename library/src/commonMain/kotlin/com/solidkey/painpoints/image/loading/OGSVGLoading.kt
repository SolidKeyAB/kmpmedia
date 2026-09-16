package com.solidkey.painpoints.image.loading

import androidx.compose.runtime.Composable
import com.solidkey.painpoints.image.svg.OGSVGGradient
import com.solidkey.painpoints.image.svg.OGSVGPattern
import com.solidkey.painpoints.image.svg.OGSVGTreeElement

data class OGParsedSVGResult(
    val viewBox: ViewBox,
    val svgTree: OGSVGTreeElement? = null,
    val hasClickAction: Boolean = false,
    val gradients: Map<String, OGSVGGradient> = emptyMap(),
    val patterns: Map<String, OGSVGPattern> = emptyMap() // Add this line
)

@Composable
expect fun loadSVGFromUrl(url: String, targetWidth: Float, targetHeight: Float, onResult: (OGParsedSVGResult?) -> Unit)

@Composable
expect fun loadSVGFromPath(path: String, targetWidth: Float, targetHeight: Float, onResult: (OGParsedSVGResult?) -> Unit)

@Composable
expect fun loadSVGFromResource(resource: String, targetWidth: Float, targetHeight: Float, onResult: (OGParsedSVGResult?) -> Unit)

@Composable
expect fun getDefaultSVGPath(svgName: String): String

/**
 * File name (with extension) the animated sample is seeded to on disk so the
 * "Load from File" flow has a real, animated file to read.
 */
const val SAMPLE_ANIMATED_SVG_FILE_NAME = "animated_svg.svg"

/**
 * A self-contained animated SVG (identical in spirit to the bundled `anim1`
 * resource: coloured circles falling + spinning). Written to disk by
 * [seedSvgFile] so loading "from file" exercises the real file path and animates
 * exactly like the resource path.
 */
val SAMPLE_ANIMATED_SVG: String = """
<svg width="400" height="400" viewBox="0 0 400 400" xmlns="http://www.w3.org/2000/svg">
    <rect width="100%" height="100%" fill="white"/>
    <circle cx="50" cy="0" r="5" fill="red">
        <animateTransform attributeName="transform" type="translate" from="0,0" to="0,400" dur="2s" repeatCount="indefinite"/>
        <animateTransform attributeName="transform" type="rotate" from="0" to="360" dur="2s" repeatCount="indefinite" additive="sum"/>
    </circle>
    <circle cx="120" cy="0" r="6" fill="blue">
        <animateTransform attributeName="transform" type="translate" from="0,0" to="0,400" dur="2.5s" repeatCount="indefinite"/>
        <animateTransform attributeName="transform" type="rotate" from="0" to="-360" dur="2s" repeatCount="indefinite" additive="sum"/>
    </circle>
    <circle cx="200" cy="0" r="7" fill="green">
        <animateTransform attributeName="transform" type="translate" from="0,0" to="0,400" dur="2.8s" repeatCount="indefinite"/>
        <animateTransform attributeName="transform" type="rotate" from="0" to="180" dur="2.2s" repeatCount="indefinite" additive="sum"/>
    </circle>
    <circle cx="280" cy="0" r="5" fill="yellow">
        <animateTransform attributeName="transform" type="translate" from="0,0" to="0,400" dur="3s" repeatCount="indefinite"/>
        <animateTransform attributeName="transform" type="rotate" from="0" to="360" dur="1.8s" repeatCount="indefinite" additive="sum"/>
    </circle>
    <circle cx="360" cy="0" r="6" fill="purple">
        <animateTransform attributeName="transform" type="translate" from="0,0" to="0,400" dur="2.7s" repeatCount="indefinite"/>
        <animateTransform attributeName="transform" type="rotate" from="0" to="-180" dur="2.5s" repeatCount="indefinite" additive="sum"/>
    </circle>
</svg>
""".trimIndent()

/**
 * Ensures [content] exists on disk under [fileName] at the platform's default SVG
 * location (writing it once if absent) and delivers the resulting absolute path
 * via [onReady] (null on failure). This lets the file-loading demo read a real
 * animated SVG file without the user having to sideload one.
 */
@Composable
expect fun seedSvgFile(fileName: String, content: String, onReady: (String?) -> Unit)