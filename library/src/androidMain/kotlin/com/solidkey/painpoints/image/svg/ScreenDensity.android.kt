package com.solidkey.painpoints.image.svg

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

@Composable
actual fun getScreenDensity(): Float {
    return LocalDensity.current.density
}