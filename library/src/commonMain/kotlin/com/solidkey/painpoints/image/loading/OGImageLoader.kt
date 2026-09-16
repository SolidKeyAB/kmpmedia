package com.solidkey.painpoints.image.loading

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.solidkey.painpoints.source.OGSource

object OGImageLoader {
    @Composable
    fun loadImage(source: OGSource, onError: ((String) -> Unit)? = null, onResult: (Painter?) -> Unit) {
        return when (source) {
            is OGSource.Url -> loadImageFromUrl(source.url, onError){
                onResult(it)
            }
            is OGSource.FilePath -> loadImageFromPath(source.path, onError){
                onResult(it)
            }
            is OGSource.Resource -> loadImageFromResource(source.resource, onError){
                onResult(it)
            }
        }
    }
}

