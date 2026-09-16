package com.solidkey.painpoints.image.loading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.svg.parseSVGPaths
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToFile


@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun loadSVGFromUrl(
    url: String,
    targetWidth: Float,
    targetHeight: Float,
    onResult: (OGParsedSVGResult?) -> Unit
) {
    val svgState = remember { mutableStateOf<OGParsedSVGResult?>(null) }

    LaunchedEffect(url) {
        svgState.value = withContext(Dispatchers.IO) {
            try {
                val svgContent = NSString.stringWithContentsOfURL(
                    NSURL(string = url), NSUTF8StringEncoding, null
                )?.toString()
                svgContent?.let { parseSVGPaths(it) }
            } catch (e: Exception) {
                Logger.e("SVG>> Failed to load/parse from URL $url: ${e.message}")
                null
            }
        }
        // Always surface the result (including null) so the caller's onError fires
        // on failure instead of the load silently hanging.
        onResult(svgState.value)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun loadSVGFromPath(
    path: String,
    targetWidth: Float,
    targetHeight: Float,
    onResult: (OGParsedSVGResult?) -> Unit
) {
    val svgState = remember { mutableStateOf<OGParsedSVGResult?>(null) }

    LaunchedEffect(path) {
        svgState.value = withContext(Dispatchers.IO) {
            try {
                val svgContent = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)?.toString()
                svgContent?.let { parseSVGPaths(it) }
            } catch (e: Exception) {
                Logger.e("SVG>> Failed to load/parse from path $path: ${e.message}")
                null
            }
        }
        onResult(svgState.value)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun loadSVGFromResource(
    resource: String,
    targetWidth: Float,
    targetHeight: Float,
    onResult: (OGParsedSVGResult?) -> Unit
) {

    val svgState = remember { mutableStateOf<OGParsedSVGResult?>(null) }

    LaunchedEffect(resource) {
        svgState.value = withContext(Dispatchers.IO) {
            try {
                val bundlePath = NSBundle.mainBundle.pathForResource(resource, "svg")
                val svgContent = bundlePath?.let {
                    NSString.stringWithContentsOfFile(it, NSUTF8StringEncoding, null)?.toString()
                }
                svgContent?.let { parseSVGPaths(it) }
            } catch (e: Exception) {
                Logger.e("SVG>> Failed to load/parse resource $resource: ${e.message}")
                null
            }
        }
        onResult(svgState.value)
    }
}

@Composable
actual fun getDefaultSVGPath(svgName: String): String {
    return NSHomeDirectory() + "/Documents/$svgName"
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun seedSvgFile(fileName: String, content: String, onReady: (String?) -> Unit) {
    LaunchedEffect(fileName) {
        val path = withContext(Dispatchers.IO) {
            try {
                val documents = NSHomeDirectory() + "/Documents"
                NSFileManager.defaultManager.createDirectoryAtPath(documents, true, null, null)
                val filePath = "$documents/$fileName"
                if (!NSFileManager.defaultManager.fileExistsAtPath(filePath)) {
                    (content as NSString).writeToFile(filePath, true, NSUTF8StringEncoding, null)
                }
                filePath
            } catch (e: Exception) {
                Logger.e("SVG>> Failed to seed $fileName: ${e.message}")
                null
            }
        }
        onReady(path)
    }
}

//fun SvgDocument.toParsedSvgResult(viewBox: ViewBox, gradients: Map<String, SvgGradient>): ParsedSvgResult {
//    Logger.i("Converting SvgDocument to ParsedSvgResult...")
//
//    // Parse the root element as an SvgTreeElement
//    val rootElement = buildSvgTree(
//        element = root,
//        parentTreeElement = null,
//        rootTreeElement = null,
//        definitions = emptyMap(),
//        symbolMap = emptyMap(),
//        viewBox = viewBox,
//        gradients = gradients,
//        inheritedStyle = SvgStyle()
//    )
//
//    return ParsedSvgResult(
//        viewBox = viewBox,
//        svgTree = rootElement,
//        hasClickAction = false,
//        gradients = gradients
//    )
//}