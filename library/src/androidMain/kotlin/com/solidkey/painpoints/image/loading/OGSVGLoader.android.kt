package com.solidkey.painpoints.image.loading

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import com.solidkey.painpoints.image.svg.parseSVGPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

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
                val svgContent = URL(url).readText()
                parseSVGPaths(svgContent)
            } catch (e: Exception) {
                Logger.e("SVG>> Failed to load/parse from URL $url: ${e.message}")
                null
            }
        }
        onResult(svgState.value)
    }
}

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
                val svgContent = File(path).readText()
                parseSVGPaths(svgContent)
            } catch (e: Exception) {
                Logger.e("SVG>> Failed to load/parse from path $path: ${e.message}")
                null
            }
        }
        onResult(svgState.value)
    }
}

@Composable
actual fun loadSVGFromResource(
    resource: String,
    targetWidth: Float,
    targetHeight: Float,
    onResult: (OGParsedSVGResult?) -> Unit
) {
    val context: Context = LocalContext.current

    LaunchedEffect(resource) {
        val svgContent = withContext(Dispatchers.IO) {
            try {
                context.assets.open("$resource.svg").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Logger.e("SVG>> Failed to read resource $resource: ${e.message}")
                null
            }
        }
        if (svgContent != null) {
            val result = parseSVGPaths(svgContent)
            onResult(result)
        } else {
            onResult(null)
        }
    }
}

@Composable
actual fun getDefaultSVGPath(svgName: String): String {
    val context = LocalContext.current
    return context.filesDir.absolutePath + "/$svgName"
}

@Composable
actual fun seedSvgFile(fileName: String, content: String, onReady: (String?) -> Unit) {
    val context: Context = LocalContext.current
    LaunchedEffect(fileName) {
        val path = withContext(Dispatchers.IO) {
            try {
                val outFile = File(context.filesDir, fileName)
                if (!outFile.exists()) outFile.writeText(content)
                outFile.absolutePath
            } catch (e: Exception) {
                Logger.e("SVG>> Failed to seed $fileName: ${e.message}")
                null
            }
        }
        onReady(path)
    }
}
