package com.solidkey.painpoints.image.loading

import androidx.compose.runtime.Composable
import com.solidkey.painpoints.source.OGSourceFormat
import com.solidkey.painpoints.source.OGSourceType
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual class OGImageFileType actual constructor(
    source: String,
    private val format: OGSourceFormat,
    private val onUnsupportedFormat: ((OGSourceFormat) -> Unit)? // ✅ Optional Callback
) : OGSourceType(source, OGSourceType.SourceType.FILE) {

    @Composable
    override fun getLocation(): String? {
        return if (isSupported(format as OGImageFormat)) {
            val resolvedPath = resolveFilePath(super.getLocation())

            val fileExists = NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)
            if (!fileExists) {
                println("❌ File Not Found: $resolvedPath")
                null
            } else {
                resolvedPath
            }
        } else {
            onUnsupportedFormat?.invoke(format) // ❌ Trigger callback if unsupported
            null
        }
    }

    private fun resolveFilePath(fileName: String?): String {
        val directory = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull()
        return directory?.let { "$it/$fileName.${format.extension}" } ?: ""
    }
}

actual class OGImageResourceFileType actual constructor(
    source: String,
    private val format: OGSourceFormat,
    private val onUnsupportedFormat: ((OGSourceFormat) -> Unit)?
) : OGSourceType(source, OGSourceType.SourceType.RESOURCE) {

    @Composable
    override fun getLocation(): String? {
        return if (isSupported(format as OGImageFormat)) {
            resolveResourcePath(super.getLocation())
        } else {
            onUnsupportedFormat?.invoke(format) // ❌ Trigger callback if unsupported
            null
        }
    }

    private fun resolveResourcePath(resourceName: String?): String {
        val bundlePath = NSBundle.mainBundle.bundlePath
        val resolvedPath = NSBundle.mainBundle.pathForResource(resourceName, format.extension)

        return resolvedPath ?: ""
    }
}
