package com.solidkey.painpoints.image.svg

import androidx.compose.runtime.Composable
import platform.UIKit.UIScreen

@Composable
actual fun getScreenDensity(): Float {
    return UIScreen.mainScreen.scale.toFloat()
}