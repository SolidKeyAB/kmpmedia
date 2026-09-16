package com.solidkey.painpoints.image.loading

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.solidkey.painpoints.source.OGSourceFormat
import com.solidkey.painpoints.source.OGSourceType


actual class OGImageFileType actual constructor(
    source: String,
    private val format: OGSourceFormat,
    private val onUnsupportedFormat: ((OGSourceFormat) -> Unit)? // ✅ Callback for unsupported formats
) : OGSourceType(source, OGSourceType.SourceType.FILE) {

    @Composable
    override fun getLocation(): String? {
        return if (isSupported(format as OGImageFormat)) {
            resolveFilePath(super.getLocation())
        } else {
            onUnsupportedFormat?.invoke(format) // ❌ Trigger callback if unsupported
            null // 🚨 Prevent crash
        }
    }

    @Composable
    private fun resolveFilePath(fileName: String?): String {
        val context: Context = LocalContext.current
        val filePath = context.filesDir.absolutePath + "/$fileName.${format.extension}"
        return filePath
    }
}

actual class OGImageResourceFileType actual constructor(
    source: String,
    private val format: OGSourceFormat,
    private val onUnsupportedFormat: ((OGSourceFormat) -> Unit)?
) : OGSourceType(
    source,
    OGSourceType.SourceType.RESOURCE
) {

    @Composable
    override fun getLocation(): String? {
        return if (isSupported(format as OGImageFormat)) {
            resolveResourcePath(super.getLocation())
        } else {
            onUnsupportedFormat?.invoke(format) // ❌ Trigger callback if unsupported
            null
        }
    }

    @Composable
    private fun resolveResourcePath(resourceName: String?): String {
        val context: Context = LocalContext.current
        val resourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        return if (resourceId != 0) {
            resourceName!!
        } else {
            ""
        }
    }
}
