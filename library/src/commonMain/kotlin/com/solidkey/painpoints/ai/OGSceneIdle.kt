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
    fun animate(scene: OGSceneSpec): OGSceneSpec {
        // Eye regions (matched by label) also tell us WHERE catchlights live. A catchlight / glint is a
        // small bright shape drawn ON the eyeball, and a model rarely labels it "eye" — so a pure label
        // match leaves that white dot floating, fully opaque, while the eye fades shut (the monkey bug).
        // So we also blink any small shape that sits inside an eye's bounds, whatever it's called.
        val eyeBoxes = scene.shapes.mapNotNull { s ->
            if (isEyeLabel((s.label ?: "").lowercase())) boxOf(s.points) else null
        }
        return scene.copy(
            shapes = scene.shapes.map { s ->
                if (s.motion != null) return@map s
                val motion = idleFor((s.label ?: "").lowercase())
                    ?: if (sitsOnAnEye(s.points, eyeBoxes)) BLINK else null
                motion?.let { s.copy(motion = it) } ?: s
            },
        )
    }

    /** Eyes (but not eyebrows); whites + pupils + irises share the one blink timeline so they close
     *  together. The catchlight synonyms are here so a glint labelled by name blinks even when it's
     *  drawn just off the eyeball (the positional pass in [animate] catches the rest). */
    private fun isEyeLabel(label: String): Boolean =
        (("eye" in label && "brow" !in label) || "pupil" in label || "iris" in label ||
            "catchlight" in label || "glint" in label || "twinkle" in label)

    private fun idleFor(label: String): OGRegionMotion? = when {
        label.isEmpty() -> null
        isEyeLabel(label) -> BLINK
        "tail" in label -> TAIL_SWAY
        "body" in label || "belly" in label || "chest" in label || "torso" in label ||
            "tummy" in label || "breast" in label -> BREATHE
        else -> null
    }

    // --- geometry: a catchlight "sits on" an eye when its centre is inside the eye's bounding box AND
    //     it is no bigger than the eye (so a head/face region that merely overlaps an eye is excluded). --

    /** Axis-aligned bounds of [points] as `[minX, minY, maxX, maxY]`, or null if none are finite. */
    private fun boxOf(points: List<OGPointSpec>): FloatArray? {
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        var n = 0
        for (p in points) {
            if (!p.x.isFinite() || !p.y.isFinite()) continue
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
            n++
        }
        return if (n == 0) null else floatArrayOf(minX, minY, maxX, maxY)
    }

    private fun areaOf(b: FloatArray): Float = (b[2] - b[0]) * (b[3] - b[1])

    private fun sitsOnAnEye(points: List<OGPointSpec>, eyeBoxes: List<FloatArray>): Boolean {
        if (eyeBoxes.isEmpty()) return false
        val b = boxOf(points) ?: return false
        val cx = (b[0] + b[2]) * 0.5f
        val cy = (b[1] + b[3]) * 0.5f
        val a = areaOf(b)
        return eyeBoxes.any { e -> cx in e[0]..e[2] && cy in e[1]..e[3] && a <= areaOf(e) + 1e-6f }
    }

    /** A slow chest rise-and-fall about the region centroid (subtle, so attached parts don't drift). */
    private val BREATHE = OGRegionMotion(duration = 2.6f, mode = "reverse", easing = "easeInOut", scale = OGMotionScalar(1f, 1.045f))

    /** A gentle side-to-side tail wag about the tail centroid. */
    private val TAIL_SWAY = OGRegionMotion(duration = 1.8f, mode = "reverse", easing = "easeInOut", rotate = OGMotionScalar(-7f, 7f))

    /** A periodic blink: the eye fades fully shut and back open (ping-pong), a clear beat every ~3s so
     *  the face shows through — a visible blink, not an imperceptible flash. */
    private val BLINK = OGRegionMotion(duration = 1.5f, mode = "reverse", easing = "easeInOut", opacity = OGMotionScalar(1f, 0f))
}
