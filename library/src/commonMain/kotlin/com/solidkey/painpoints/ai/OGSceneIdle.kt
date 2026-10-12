package com.solidkey.painpoints.ai

/**
 * Gentle "idle" life for a still, model-authored scene: match region LABELS and attach the
 * breathing / blink / tail-sway that makes a figure feel alive — with no hand-keyframing. This is
 * the payoff of labelled parts: because the SVG front-end ([OGSvgScene]) names every region from its
 * `id`, we can find the body, the eyes and the tail by name and animate exactly those.
 *
 * Pure data: it only sets each region's [OGRegionMotion]. The player ([com.solidkey.painpoints.stage.OGStageView])
 * and the exporter already animate a live scene via [OGPlacedShapeSpec.animatedAt], so a scene that
 * passes through here simply starts moving — at 60fps, re-evaluating only the handful of moved regions.
 */
object OGSceneIdle {

    /**
     * Return [scene] with idle motion attached to the regions it recognises: a body breathes (a slow
     * scale about its centroid), eyes blink (a periodic fade-shut), a tail sways (a gentle rotation).
     * Regions that already declare their own [OGRegionMotion] are left untouched; labels that match
     * nothing stay still.
     */
    fun animate(scene: OGSceneSpec): OGSceneSpec =
        scene.copy(
            shapes = scene.shapes.map { s ->
                if (s.motion != null) s else idleFor((s.label ?: "").lowercase())?.let { s.copy(motion = it) } ?: s
            },
        )

    private fun idleFor(label: String): OGRegionMotion? = when {
        label.isEmpty() -> null
        // Eyes (but not eyebrows) blink; whites + pupils share the same timeline so they close together.
        ("eye" in label && "brow" !in label) || "pupil" in label || "iris" in label -> BLINK
        "tail" in label -> TAIL_SWAY
        "body" in label || "belly" in label || "chest" in label || "torso" in label ||
            "tummy" in label || "breast" in label -> BREATHE
        else -> null
    }

    /** A slow chest rise-and-fall about the region centroid (subtle, so attached parts don't drift). */
    private val BREATHE = OGRegionMotion(duration = 2.6f, mode = "reverse", easing = "easeInOut", scale = OGMotionScalar(1f, 1.045f))

    /** A gentle side-to-side tail wag about the tail centroid. */
    private val TAIL_SWAY = OGRegionMotion(duration = 1.8f, mode = "reverse", easing = "easeInOut", rotate = OGMotionScalar(-7f, 7f))

    /** A periodic blink: the eye fades fully shut and back open (ping-pong), a clear beat every ~3s so
     *  the face shows through — a visible blink, not an imperceptible flash. */
    private val BLINK = OGRegionMotion(duration = 1.5f, mode = "reverse", easing = "easeInOut", opacity = OGMotionScalar(1f, 0f))
}
