package com.solidkey.painpoints.image.animating

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.solidkey.painpoints.image.OGImageView
import com.solidkey.painpoints.image.svg.OGSVGView
import com.solidkey.painpoints.image.svg.SVGScalingBehavior
import com.solidkey.painpoints.image.loading.OGSvgSourceType
import com.solidkey.painpoints.source.OGSourceType

/**
 * ✨ **The wrapping power of KMPMedia.**
 *
 * Wraps ANY static [content] — a plain logo, an SVG with zero `<animate>` tags,
 * a raster image — and brings it to life by continuously applying the library's
 * animation primitives ([OGAnimationType]). The primitives **compose**: enable
 * [OGAnimationType.SCALE] + [OGAnimationType.ROTATE] together and the artwork
 * pulses while it spins. Nothing about the source file has to change — the
 * motion is entirely the library wrapping the content.
 *
 * Pass an empty [animations] set and the content renders perfectly still.
 *
 * The oscillating primitives ([OGAnimationType.SCALE], [OGAnimationType.FADE],
 * [OGAnimationType.TRANSLATE]) scale their amplitude by [intensity]: `0f` is no
 * motion, `1f` is the normal amount, `>1f` exaggerates. This makes the container
 * ideal for *state-driven* motion — e.g. a game sprite whose shake/flicker grows
 * with a damage level — without swapping the animation set. ([OGAnimationType.ROTATE]
 * is a continuous full spin; [intensity] only gates it on/off.)
 *
 * @param animations the primitives to run simultaneously (empty = fully static)
 * @param durationMillis one full cycle of each primitive (lower = more frantic)
 * @param slidePx horizontal travel of [OGAnimationType.TRANSLATE] at intensity 1
 * @param intensity amplitude multiplier for SCALE/FADE/TRANSLATE (0 = still, 1 = normal)
 */
@Composable
fun OGAnimatedContainer(
    animations: Set<OGAnimationType>,
    modifier: Modifier = Modifier,
    durationMillis: Int = 1200,
    slidePx: Float = 40f,
    intensity: Float = 1f,
    content: @Composable () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "og-animated")
    val amp = intensity.coerceAtLeast(0f)

    // Each primitive drives one channel. When its type isn't selected the
    // target equals the initial value, so the channel simply rests — no motion.
    // Amplitudes scale by [amp] so the SAME set can read as gentle or violent.
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (OGAnimationType.SCALE in animations) 1f + 0.4f * amp else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (OGAnimationType.ROTATE in animations) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val fade by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (OGAnimationType.FADE in animations) (1f - 0.75f * amp).coerceAtLeast(0f) else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fade"
    )

    // 0f..1f factor mapped to -slide..+slide so the slide is centred on the origin.
    val slide by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (OGAnimationType.TRANSLATE in animations) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slide"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationZ = rotation
            alpha = fade
            translationX = (slide * 2f - 1f) * slidePx * amp * (if (OGAnimationType.TRANSLATE in animations) 1f else 0f)
            // FADE (alpha < 1) must NOT clip the artwork. With the default compositing
            // strategy, alpha < 1 forces an offscreen layer sized to THIS box's bounds and
            // clips any content that overflows them — so a wrapped image larger than its box
            // would get chopped the instant it starts fading. ModulateAlpha applies the alpha
            // per draw-instruction instead (no bounds-clipped offscreen buffer), so fading
            // dims the whole image without cropping it.
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
    ) {
        content()
    }
}

/**
 * Convenience over [OGAnimatedContainer]: resolves a [source] (SVG → [OGSVGView],
 * anything else → [OGImageView]) and animates it with the selected [animations].
 * This is the one-call "give me a static image, hand me back an animated one" API.
 *
 * @param width/[height] the intrinsic pixel box used when the source is an SVG
 */
@Composable
fun OGAnimatedImage(
    source: OGSourceType,
    animations: Set<OGAnimationType>,
    width: Float = 512f,
    height: Float = 512f,
    modifier: Modifier = Modifier,
    durationMillis: Int = 1200,
    intensity: Float = 1f,
    slidePx: Float = 40f,
    onError: ((String) -> Unit)? = null,
) {
    OGAnimatedContainer(
        animations = animations,
        modifier = modifier,
        durationMillis = durationMillis,
        intensity = intensity,
        slidePx = slidePx
    ) {
        if (source is OGSvgSourceType) {
            OGSVGView(
                source = source,
                width = width,
                height = height,
                scalingBehavior = SVGScalingBehavior.FIT,
                onError = onError
            )
        } else {
            OGImageView(
                source = source,
                modifier = Modifier,
                onError = onError,
                onEventTriggered = { _, _ -> }
            )
        }
    }
}
