package com.solidkey.painpoints.video.loading

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.solidkey.painpoints.source.OGSourceFormat
import com.solidkey.painpoints.source.OGSourceType
import java.io.File

actual class OGVideoFileType actual constructor(
    source: String,
    onUnsupportedFormat: ((OGSourceFormat) -> Unit)?
) : OGSourceType(source, OGSourceType.SourceType.FILE) {

    @Composable
    override fun getLocation(): String? {
        val context = LocalContext.current
        val resolvedPath = resolveFilePath(context, super.getLocation())

        val fileExists = File(resolvedPath).exists()
        if (!fileExists) {
            println("❌ Video File Not Found: $resolvedPath")
        }
        return resolvedPath
    }

    private fun resolveFilePath(context: Context, fileName: String?): String {
        return "${context.filesDir.absolutePath}/$fileName"
    }
}

actual class OGIVideoResourceFileType actual constructor(
    source: String,
    onUnsupportedFormat: ((OGSourceFormat) -> Unit)?
) : OGSourceType(source, OGSourceType.SourceType.RESOURCE)  {

    @Composable
    override fun getLocation(): String? {
        val context = LocalContext.current
        return resolveResourcePath(context, super.getLocation())
    }

    private fun resolveResourcePath(context: Context, resourceName: String?): String? {
        val resId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
        val resolvedPath = "android.resource://${context.packageName}/$resId"

        return if (resId != 0) resolvedPath else null
    }
}