package com.solidkey.painpoints.puppet

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The **data-defined cut-out puppet**: a hand-drawn figure cut into transparent part-layers (a still
 * **base** plus jointed **movers**) and reassembled as a live raster puppet — each mover rotating about
 * the joint it was pinned at, breathing, leaning and *watching* a look target. It is the raster sibling
 * of the library's generative data packs ([com.solidkey.painpoints.particle.OGParticleSpec] particles,
 * [com.solidkey.painpoints.shape.OGParametricSpec] shapes): a designer (or the Layer Cutter tool) authors
 * a `.puppet` pack of joints + motion, and the lib brings it to life — no per-app rigging code.
 *
 * Decode/encode + a ready-made preset live on [OGPuppet]; drop it on screen with
 * [com.solidkey.painpoints.puppet.OGPuppetView]. The pure transform math ([OGPuppetRig]) is split out so it
 * is deterministic and testable without Compose (same `tSec` + `look` → same pose on Android and iOS).
 *
 * **Coordinates.** Every part sprite shares the base's frame (each cut part baked/placed on the same canvas,
 * so a mover needs only its joint). [OGPuppetPart.pivotX]/[OGPuppetPart.pivotY] are the joint as a `0..1`
 * fraction of that frame; [OGPuppetPart.tuckX]/[tuckY] and [OGPuppetPart.gaze] are fractions of the frame
 * width/height. A **look** value is `-1..1` horizontally (−1 = hard left, +1 = hard right, 0 = centre).
 */
@kotlinx.serialization.Serializable
data class OGPuppetPart(
    /** Resource name of this part's transparent sprite (resolved by the platform image loader). */
    val res: String,
    /** The joint this mover rotates about, as a `0..1` fraction of the shared frame. */
    val pivotX: Float = 0.5f,
    val pivotY: Float = 0.5f,
    /** Idle rotation amplitude (degrees) about the pivot — a faint life-swing (`0` = still). */
    val swingDeg: Float = 0f,
    /** Idle swing rate + phase, so sibling parts don't move in lockstep. */
    val speed: Float = 1f,
    val phase: Float = 0f,
    /** A static offset of this part within the frame (fraction of width/height). */
    val tuckX: Float = 0f,
    val tuckY: Float = 0f,
    /** This part WATCHES the look target: it slides toward it by `gaze·look` (fraction of width) — e.g. coal
     *  eyes/a mouth with no head to turn. */
    val gaze: Float = 0f,
    /** This part is the HEAD: it turns toward the look target (a tilt + a mirror-flip in place about
     *  [OGPuppetSpec.headCenterX]/[headCenterY]) instead of a plain idle swing. At most one part should set this. */
    val head: Boolean = false,
)

/**
 * A whole cut-out puppet: the still [base] sprite plus its [parts] (movers, back→front), the frame [aspect],
 * and the feel of its idle life. [grounded] = it stands/sits on a surface (bends gently about its feet,
 * never lifts); otherwise it hovers in place (a slow vertical float + airborne tilt), like a flying figure.
 */
@kotlinx.serialization.Serializable
data class OGPuppetSpec(
    val base: String,
    val parts: List<OGPuppetPart> = emptyList(),
    /** The base frame's width / height. */
    val aspect: Float = 1f,
    val grounded: Boolean = false,
    /** Breathing: a gentle whole-figure scale pulse. */
    val breatheAmp: Float = 0.02f,
    val breatheSpeed: Float = 0.5f,
    /** Whole-figure horizontal lean toward the look target (fraction of width). */
    val leanAmp: Float = 0.06f,
    /** Head-turn tilt toward the look target (degrees), for a part with [OGPuppetPart.head]. */
    val lookTurnDeg: Float = 12f,
    /** The head's own centre (frame fraction) — a head mirror-flips about this to turn in place. */
    val headCenterX: Float = 0.5f,
    val headCenterY: Float = 0.5f,
    /** Whether a head part mirror-FLIPS to turn (a profile/one-sided face reads as turning when flipped).
     *  `false` for a symmetric forward face (e.g. a bee) — it just tilts toward the look, no flip. */
    val headMirror: Boolean = true,
    /** Grounded idle bend amplitude (degrees) + how much MORE it leans toward the look target. */
    val bendDeg: Float = 4f,
    val bendLookDeg: Float = 5f,
    /** Flying hover: vertical float (fraction of height) + airborne tilt sway (degrees). */
    val hoverAmp: Float = 0.05f,
    val tiltDeg: Float = 1.5f,
    val name: String? = null,
) {
    /** The head part, if any (the one that turns toward the look target). */
    val headPart: OGPuppetPart? get() = parts.firstOrNull { it.head }
}

/**
 * A transient whole-figure pose laid **on top** of the idle life — the host app folds its own game beats
 * (a score pop, a tap-spotlight, an emotional reaction) into one of these. Identity = [Idle].
 * [dy] is a fraction of height, [scale] a multiplier, [rot] degrees.
 */
data class OGPuppetPose(val dy: Float = 0f, val scale: Float = 1f, val rot: Float = 0f) {
    companion object { val Idle = OGPuppetPose() }
}

/**
 * The puppet's pure, deterministic pose math — no Compose, no platform. Given a clock [tSec] and a look
 * target, it yields the whole-figure transform and each part's local transform. [OGPuppetView] renders
 * these; tests pin them. Same inputs → identical output on every platform.
 */
object OGPuppetRig {
    private const val TAU = (2.0 * PI).toFloat()

    /** The resolved look `-1..1`: the host's [look] when it is watching something, else a slow idle glance. */
    fun effectiveLook(look: Float?, tSec: Float): Float =
        look?.coerceIn(-1f, 1f) ?: (sin(tSec * 0.7f) * 0.6f)

    /** Whole-figure vertical offset (fraction of height): a hover float for a flying figure, `0` if grounded. */
    fun figureFloat(spec: OGPuppetSpec, tSec: Float): Float =
        if (spec.grounded) 0f else sin(tSec * 2.5f) * spec.hoverAmp

    /** Whole-figure breathing scale multiplier. */
    fun figureBreathe(spec: OGPuppetSpec, tSec: Float): Float =
        1f + sin(TAU * spec.breatheSpeed * tSec) * spec.breatheAmp

    /** Whole-figure horizontal lean (fraction of width) toward the look target. */
    fun figureLean(spec: OGPuppetSpec, look: Float): Float = look * spec.leanAmp

    /** Whole-figure rotation (degrees): a grounded side-to-side bend about the feet, or an airborne tilt. */
    fun figureRotation(spec: OGPuppetSpec, tSec: Float, look: Float): Float =
        if (spec.grounded) sin(tSec * 1.05f) * spec.bendDeg + look * spec.bendLookDeg
        else sin(tSec * 1.3f) * spec.tiltDeg

    /** A mover's local rotation (degrees) about its pivot: a head adds a damped look-turn, others idle-swing. */
    fun partAngle(spec: OGPuppetSpec, part: OGPuppetPart, tSec: Float, look: Float): Float {
        val idle = part.swingDeg * sin(2.2f * part.speed * tSec + part.phase)
        return if (part.head) idle * 0.25f + look * spec.lookTurnDeg else idle
    }

    /** A head mirror-flips toward the look direction to read as turning in place (kept near full width so the
     *  flip never looks squished) — only when [OGPuppetSpec.headMirror] is set (a one-sided face). A symmetric
     *  face (headMirror = false) and every non-head part stay at width 1. */
    fun partScaleX(spec: OGPuppetSpec, part: OGPuppetPart, look: Float): Float =
        if (part.head && spec.headMirror) (if (look >= 0f) 1f else -1f) * (0.9f + 0.1f * abs(look)) else 1f

    /** A mover's horizontal offset (fraction of width): its static tuck plus a gaze slide toward the look. */
    fun partOffsetX(part: OGPuppetPart, look: Float): Float = part.tuckX + part.gaze * look

    /** A mover's vertical offset (fraction of height): its static tuck. */
    fun partOffsetY(part: OGPuppetPart): Float = part.tuckY
}
