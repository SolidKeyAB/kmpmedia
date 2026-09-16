package com.solidkey.painpoints.image.loading

import androidx.compose.runtime.Composable
import com.solidkey.painpoints.source.OGSource

data class ViewBox(
    val minX: Float, // X-coordinate of the top-left corner
    val minY: Float, // Y-coordinate of the top-left corner
    val width: Float, // Width of the SVG viewport
    val height: Float // Height of the SVG viewport
){
    fun scaleX(value: Float, targetWidth: Float): Float = (value - minX) / width * targetWidth
    fun scaleY(value: Float, targetHeight: Float): Float = (value - minY) / height * targetHeight
}

object OGSvgLoader {
    @Composable
    fun loadSvg(source: OGSource, targetWidth: Float, targetHeight: Float, onResult: (
        OGParsedSVGResult?) -> Unit) {

        return when (source) {
            is OGSource.Url -> loadSVGFromUrl(source.url,0f,0f) { onResult(it) }
            is OGSource.FilePath -> loadSVGFromPath(source.path, 0f, 0f) { onResult(it) }
            is OGSource.Resource -> loadSVGFromResource(source.resource, 0f, 0f) { onResult(it) }
        }
    }
}

