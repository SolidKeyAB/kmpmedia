package com.solidkey.painpoints.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.solidkey.painpoints.motion.OGSpringSpec
import com.solidkey.painpoints.motion.OGSpringValue

// ── Public types ───────────────────────────────────────────────────────────────

/**
 * One smoothed motion reading — "how much is moving, and where."
 * [directionX] is the motion centroid in screen space (after mirroring):
 * the user's right hand waving right reads positive.
 */
data class OGMotionWindSample(
    /** Left→right position of the motion: −1 (far left) … 0 (centered/still) … +1 (far right). */
    val directionX: Float = 0f,
    /** How much of the frame is moving: 0 (still) … 1 (≥ [OGMotionWindSpec.maxMotionFraction]). */
    val magnitude: Float = 0f,
) {
    companion object {
        val STILL = OGMotionWindSample()
    }
}

/**
 * Config for [rememberOGMotionWind]. Defaults are tuned for a hand waved at arm's length
 * in front of the front camera under indoor light.
 *
 * @param width analysis grid width in pixels (64×48 = 3 072 bytes/frame; don't exceed 128×96).
 * @param height analysis grid height in pixels.
 * @param threshold per-pixel luma delta (0–255) below which a pixel is noise, not motion.
 * @param maxMotionFraction fraction of grid pixels in motion that equals magnitude=1.0.
 *   0.25 ⇒ a quarter of the frame moving = "maximum wind".
 * @param facing which camera to use (front by default for gesture input).
 * @param mirror negate directionX so the user's right hand reads +1 (front camera is un-mirrored
 *   in the buffer — your right hand appears at low X without this).
 * @param targetFps frames to analyze per second; extras are dropped.
 * @param directionSpring smoothing spring for [OGMotionWindSample.directionX].
 * @param magnitudeSpring smoothing spring for [OGMotionWindSample.magnitude] — slightly stiffer
 *   so gusts land fast but decay smoothly.
 */
data class OGMotionWindSpec(
    val name: String? = null,
    val width: Int = 64,
    val height: Int = 48,
    val threshold: Int = 18,
    val maxMotionFraction: Float = 0.25f,
    val facing: OGCameraFacing = OGCameraFacing.FRONT,
    val mirror: Boolean = true,
    val targetFps: Int = 15,
    val directionSpring: OGSpringSpec = OGSpringSpec(stiffness = 120f, dampingRatio = 1f),
    val magnitudeSpring: OGSpringSpec = OGSpringSpec(stiffness = 170f, dampingRatio = 1f),
)

// ── Public composable ──────────────────────────────────────────────────────────

/**
 * Streams camera motion as a smoothed [OGMotionWindSample] — wave a hand, get wind.
 * Opens the camera **headless** (no preview is shown); the camera releases when this
 * leaves composition or [enabled] flips false.
 *
 * **Permission**: app must hold `android.permission.CAMERA` / `NSCameraUsageDescription`
 * before calling; without it [onError] fires and the state stays [OGMotionWindSample.STILL].
 *
 * Usage in a game loop:
 * ```kotlin
 * val wind by rememberOGMotionWind()
 * // wind.directionX → −1..+1 horizontal force
 * // wind.magnitude  → 0..1 effort
 * ```
 */
@Composable
fun rememberOGMotionWind(
    spec: OGMotionWindSpec = OGMotionWindSpec(),
    enabled: Boolean = true,
    onError: ((String) -> Unit)? = null,
): State<OGMotionWindSample> {
    val state = remember { mutableStateOf(OGMotionWindSample.STILL) }
    val processor = remember(spec) {
        OGMotionWindProcessor(spec) { sample -> state.value = sample }
    }
    OGMotionWindCameraEffect(
        spec = spec,
        enabled = enabled,
        onLumaFrame = processor::onLumaFrame,
        onError = onError,
    )
    return state
}

// ── Internal expect — platform delivers upright luma frames ───────────────────

/**
 * Platform camera feeder (internal implementation detail; not part of the public API).
 *
 * Contract:
 * - While [enabled] and in composition: open the [spec.facing] camera headless and call
 *   [onLumaFrame] at ≤ [spec.targetFps] with a fresh ByteArray of exactly `spec.width*spec.height`
 *   bytes already rotated to upright display orientation, plus a monotonic timestamp in nanos.
 *   Ownership of the array transfers — never reuse it.
 * - [onLumaFrame] is always called on a background thread, never on main.
 * - On dispose or enabled=false: release camera + threads fully.
 */
@Composable
internal expect fun OGMotionWindCameraEffect(
    spec: OGMotionWindSpec,
    enabled: Boolean,
    onLumaFrame: (luma: ByteArray, timestampNanos: Long) -> Unit,
    onError: ((String) -> Unit)?,
)

// ── Algorithm — pure arithmetic, platform-free ────────────────────────────────

/**
 * Frame-difference motion estimate from two equally-sized luma grids.
 * Uses excess-weighted centroid: pixels further above [threshold] pull the centroid harder.
 * This is pure Kotlin arithmetic — JVM and Native identical, unit-testable without a camera.
 */
internal fun computeMotionWind(
    prev: ByteArray,
    curr: ByteArray,
    width: Int,
    height: Int,
    threshold: Int,
    maxMotionFraction: Float = 0.25f,
): OGMotionWindSample {
    val n = width * height
    if (prev.size != n || curr.size != n) return OGMotionWindSample.STILL

    var movedCount = 0
    var weightSum = 0L
    var weightedX = 0L
    var i = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val a = kotlin.math.abs(
                (curr[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF)
            )
            if (a > threshold) {
                val excess = a - threshold
                movedCount++
                weightSum += excess
                weightedX += excess.toLong() * x
            }
            i++
        }
    }

    if (weightSum == 0L) return OGMotionWindSample.STILL

    val cx = weightedX.toFloat() / weightSum
    val directionX = ((cx / (width - 1)) * 2f - 1f).coerceIn(-1f, 1f)
    val maxMoved = (n * maxMotionFraction).coerceAtLeast(1f)
    val magnitude = (movedCount / maxMoved).coerceIn(0f, 1f)

    return OGMotionWindSample(directionX, magnitude)
}

// ── Processor — diffs frames, smooths via OGSpring, emits ────────────────────

/** Owns prev-frame state and the two smoothing springs. Must be called from ONE background thread. */
internal class OGMotionWindProcessor(
    private val spec: OGMotionWindSpec,
    private val emit: (OGMotionWindSample) -> Unit,
) {
    private var prev: ByteArray? = null
    private var prevNanos = 0L
    private val dir = OGSpringValue(0f)
    private val mag = OGSpringValue(0f)

    fun onLumaFrame(curr: ByteArray, timestampNanos: Long) {
        val p = prev
        prev = curr  // take ownership; platform never reuses the array
        if (p == null) { prevNanos = timestampNanos; return }

        val raw = computeMotionWind(p, curr, spec.width, spec.height,
            spec.threshold, spec.maxMotionFraction)
        val rawDir = if (spec.mirror) -raw.directionX else raw.directionX

        val dt = ((timestampNanos - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.25f)
        prevNanos = timestampNanos

        dir.update(target = rawDir, dt = dt, spec = spec.directionSpring)
        mag.update(target = raw.magnitude, dt = dt, spec = spec.magnitudeSpring)
        emit(OGMotionWindSample(dir.value.coerceIn(-1f, 1f), mag.value.coerceIn(0f, 1f)))
    }
}
