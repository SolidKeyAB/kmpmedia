package com.solidkey.painpoints.stage

import com.solidkey.painpoints.motion.OGSpring
import com.solidkey.painpoints.motion.OGSpringSpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================================
// OGStage INTERACTIVITY — the data-authored "poke it and it responds" layer that turns the movie
// from a thing you watch into a thing you touch. This is the one capability the CMP animation
// players (Lottie/Rive-CMP) lead on and KMPMedia did not: a tap on a layer triggers a reaction.
//
// It stays true to the whole library's ethos: the trigger is plain serializable data on the layer
// ([OGStageLayer.onTap]), so it round-trips through the [OGStages] codec and a model can author it
// alongside everything else. The REACTION itself is a pure, closed-form, t-addressable function
// ([reactionTransformAt]) — a transient modulation applied ON TOP of the layer's resolved transform —
// so it is deterministic, unit-testable, and costs nothing until a finger lands on the layer. The
// bounce/shake feel reuses the shipped dynamics spring ([OGSpring.valueAt]) verbatim, so a tap reacts
// with the same analytic second-order motion the dynamics pack already ships (no new physics here).
//
// Reactions are a LIVE-PLAYER concern only: [OGStageView] drives them off the frame clock while a
// reaction is settling. The off-screen exporter bakes the base timeline unchanged — an exported GIF
// is the movie without taps — so nothing about interactivity touches [OGStageExport].
// ============================================================================================

/**
 * What a tap on an [OGStageLayer] does. The visual reactions ([BOUNCE] / [SHAKE] / [SPIN]) are
 * transient transform modulations that play on top of the layer wherever the stage clock is (so they
 * work even while the stage is paused or scrubbed); the navigation actions ([SEEK] / [PLAY_PAUSE])
 * drive the player's own clock and so only apply when [OGStageView] owns the clock (not when it is
 * controlled by an external `positionMs`). Lower-case [SerialName]s so a model authors them as the
 * prompt promises; the codec decodes enum names case-insensitively regardless.
 */
@Serializable
enum class OGTapAction {
    /** A spring "poke": the layer scales up and wobbles back to rest ([OGSpring]-driven overshoot). */
    @SerialName("bounce") BOUNCE,

    /** A damped horizontal shake that oscillates and settles ([OGSpring]-driven). */
    @SerialName("shake") SHAKE,

    /** One full eased 360° turn that settles back to the layer's resting rotation. */
    @SerialName("spin") SPIN,

    /** Jump the stage clock to [OGStageTrigger.seekMs] (a chapter jump); live-clock mode only. */
    @SerialName("seek") SEEK,

    /** Toggle the player between playing and paused in place; live-clock mode only. */
    @SerialName("playpause") PLAY_PAUSE,
}

/**
 * A declarative tap handler on one [OGStageLayer]. Pure serializable data, so it is authored in JSON
 * with the rest of the movie and round-trips through [OGStages].
 *
 * @param action what the tap does (see [OGTapAction]).
 * @param strength how big the visual reaction is (≈ a fraction; `1` is the natural default, `>1`
 *   exaggerates, `0` is inert). Ignored by [OGTapAction.SEEK] / [OGTapAction.PLAY_PAUSE].
 * @param durationMs how long the reaction animates before it fully settles back to rest.
 * @param seekMs for [OGTapAction.SEEK], the absolute stage time (ms) to jump to; ignored otherwise.
 */
@Serializable
data class OGStageTrigger(
    val action: OGTapAction = OGTapAction.BOUNCE,
    val strength: Float = 1f,
    val durationMs: Long = 600L,
    val seekMs: Long? = null,
)

/**
 * The transient modulation a tap reaction applies on TOP of a layer's resolved transform: a scale
 * multiplier, an additive rotation (deg) and an additive translate (as a fraction of the layer box).
 * [IDENTITY] is a no-op (nothing applied). Produced by [reactionTransformAt]; consumed by the player.
 */
data class OGReactionTransform(
    val scaleMul: Float = 1f,
    val rotationAdd: Float = 0f,
    val txAdd: Float = 0f,
    val tyAdd: Float = 0f,
) {
    companion object {
        val IDENTITY = OGReactionTransform()
    }
}

// Feel of the spring-backed reactions — a lively, overshooting second-order spring (ζ < 1).
private val BOUNCE_SPEC = OGSpringSpec(stiffness = 260f, dampingRatio = 0.35f)
private val SHAKE_SPEC = OGSpringSpec(stiffness = 420f, dampingRatio = 0.30f)
private const val BOUNCE_IMPULSE = 3.4f // initial scale velocity per unit strength → a ~13% pop at 1
private const val SHAKE_IMPULSE = 1.3f // initial horizontal velocity per unit strength → a ~5% swing at 1

/**
 * The reaction transform for [action] at [elapsedMs] since the tap, closed-form and pure. Returns
 * [OGReactionTransform.IDENTITY] once the reaction has fully settled (`elapsedMs >= durationMs`) or for
 * the non-visual navigation actions, so the player can apply it unconditionally and drop it when it
 * reaches identity. The bounce/shake curves are evaluated with the shipped analytic [OGSpring], so the
 * reaction has the exact same deterministic, platform-identical feel as the dynamics pack.
 *
 * @param strength scales the reaction amplitude (`1` = natural).
 * @param durationMs the reaction's lifetime; after it, the layer is back at rest.
 * @param elapsedMs time since the tap, in ms.
 */
fun reactionTransformAt(
    action: OGTapAction,
    strength: Float,
    durationMs: Long,
    elapsedMs: Long,
): OGReactionTransform {
    if (durationMs <= 0L || elapsedMs >= durationMs) return OGReactionTransform.IDENTITY
    val s = strength.coerceAtLeast(0f)
    val t = elapsedMs.coerceAtLeast(0L) / 1000f
    return when (action) {
        // A poke: start at rest (displacement 0) with an upward scale velocity, let the spring overshoot
        // and settle back to 1. valueAt(from=to=1) returns 1 + the decaying oscillation of that impulse.
        OGTapAction.BOUNCE ->
            OGReactionTransform(scaleMul = OGSpring.valueAt(BOUNCE_SPEC, t, from = 1f, to = 1f, velocity = s * BOUNCE_IMPULSE))

        // Start at rest with a sideways velocity kick; the spring oscillates it back to 0 — a damped shake.
        OGTapAction.SHAKE ->
            OGReactionTransform(txAdd = OGSpring.valueAt(SHAKE_SPEC, t, from = 0f, to = 0f, velocity = s * SHAKE_IMPULSE))

        // One full turn, eased out, ending exactly on a multiple of 360° (≡ the resting rotation).
        OGTapAction.SPIN -> {
            val p = (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
            OGReactionTransform(rotationAdd = 360f * s * easeOutCubic(p))
        }

        // Navigation — no per-frame transform; handled by the player at tap time.
        OGTapAction.SEEK, OGTapAction.PLAY_PAUSE -> OGReactionTransform.IDENTITY
    }
}

/** `1 - (1-p)³` — a fast-start / gentle-settle ease for the one-shot [OGTapAction.SPIN]. */
private fun easeOutCubic(p: Float): Float {
    val u = 1f - p.coerceIn(0f, 1f)
    return 1f - u * u * u
}
