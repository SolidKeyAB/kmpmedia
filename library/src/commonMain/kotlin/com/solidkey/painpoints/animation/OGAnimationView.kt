package com.solidkey.painpoints.animation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier



//import com.airbnb.lottie.compose.*
//import com.airbnb.lottie.compose.LottieAnimation
//import com.airbnb.lottie.compose.LottieCompositionSpec
//import com.airbnb.lottie.compose.LottieConstants
//import com.airbnb.lottie.compose.animateLottieCompositionAsState
//import com.airbnb.lottie.compose.rememberLottieComposition


sealed class OGAnimationSource {
    data class Lottie(val asset: String) : OGAnimationSource()
    data class GIF(val asset: String) : OGAnimationSource()
    data class SpriteSheet(val asset: String, val frameCount: Int, val frameDurationMs: Int) : OGAnimationSource()
    data class AIAnimation(val model: String, val parameters: Map<String, Any>) : OGAnimationSource()
}

@Composable
expect fun OGAnimationView(
    source: OGAnimationSource,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    speed: Float = 1.0f
)



@Composable
fun BLAHBLAH(
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
