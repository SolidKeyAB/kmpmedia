package com.solidkey.painpoints.image.loading

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
expect fun loadImageFromUrl(url: String, onError: ((String) -> Unit)? = null, onLoaded: (Painter) -> Unit)

@Composable
expect fun loadImageFromPath(path: String, onError: ((String) -> Unit)? = null, onLoaded: (Painter) -> Unit)

@Composable
expect fun loadImageFromResource(resource: String, onError: ((String) -> Unit)? = null, onLoaded: (Painter) -> Unit)

@Composable
expect fun getDefaultImagePath(imageName: String): String

/**
 * 🔍 Check if the given format is supported on the current platform.
 */
expect fun isSupported(format: OGImageFormat): Boolean
