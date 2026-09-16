package com.solidkey.painpoints.video.loading

import androidx.compose.runtime.Composable
import com.solidkey.painpoints.source.OGSourceFormat
import com.solidkey.painpoints.source.OGSourceType
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual class OGVideoFileType actual constructor(
    source: String,
    onUnsupportedFormat: ((OGSourceFormat) -> Unit)?
) : OGSourceType(source, OGSourceType.SourceType.FILE) {

    @Composable
    override fun getLocation(): String? {
        val resolvedPath = resolveFilePath(super.getLocation())

        val fileExists = NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)
        if (!fileExists) {
            println("❌ Video File Not Found: $resolvedPath")
        }
        return resolvedPath
    }

    private fun resolveFilePath(fileName: String?): String {
        val directory = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull()
        return directory?.let { "$it/$fileName" } ?: ""
    }
}

actual class OGIVideoResourceFileType actual constructor(
    source: String,
    onUnsupportedFormat: ((OGSourceFormat) -> Unit)?
) : OGSourceType(source, OGSourceType.SourceType.RESOURCE) {

    @Composable
    override fun getLocation(): String? {
        return resolveResourcePath(super.getLocation())
    }

    private fun resolveResourcePath(resourceName: String?): String {
        val resolvedPath = NSBundle.mainBundle.pathForResource(resourceName, "mp4")

        return resolvedPath ?: ""
    }
}