package com.solidkey.painpoints.animation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
//import com.airbnb.lottie.compose.*

@Composable
actual fun OGAnimationView(
    source: OGAnimationSource,
    modifier: Modifier,
    loop: Boolean,
    speed: Float
) {
//    when (source) {
//        is OGAnimationSource.Lottie -> {
//            val composition by rememberLottieComposition(LottieCompositionSpec.Asset(source.asset))
//            val progress by animateLottieCompositionAsState(
//                composition,
//                iterations = if (loop) LottieConstants.IterateForever else 1,
//                speed = speed
//            )
//            LottieAnimation(composition = composition, progress = progress, modifier = modifier)
//        }
//        else -> throw IllegalArgumentException("Unsupported animation type on this platform.")
//    }
}
