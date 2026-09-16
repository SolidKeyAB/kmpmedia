package com.solidkey.painpoints.image.svg.animation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun rememberSVGAnimationState(): OGSVGAnimationState {
    return remember { OGSVGAnimationState() }
}

class OGSVGAnimationState {
    var animations = mutableMapOf<String, AnimationSpec<Float>>()
    var currentValues = mutableMapOf<String, MutableState<Float>>()

    fun addAnimation(id: String, from: Float, to: Float, duration: Float, repeatCount: String) {
        val anim = tween<Float>(
            durationMillis = (duration * 1000).toInt(),
            easing = LinearEasing
        )

        val repeatMode = if (repeatCount == "indefinite") InfiniteRepeatableSpec(anim) else anim
        animations[id] = repeatMode
    }

    @Composable
    fun animate(id: String, from: Float, to: Float) {
        val animation = animations[id] ?: return
        val animatedValue = remember { mutableStateOf(from) }

        LaunchedEffect(id) {
            animate(
                initialValue = from,
                targetValue = to,
                animationSpec = animation
            ) { value, _ ->
                animatedValue.value = value
            }
        }

        currentValues[id] = animatedValue
    }
}
