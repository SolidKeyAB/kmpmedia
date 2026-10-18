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
     * rise from its seated base), eyes blink (an eyelid squashing shut), a part sways (a wag about the
     * end where it meets the figure). A region's OWN declared [OGRegionMotion] WINS (a model that set
     * `data-motion` in its SVG picks the motion); labels that match nothing stay still.
     *
     * Every wag (sway/wave) is then made HONEST by pure geometry, with no labels: a part whose outline
     * touches the rest of the figure hinges at that join; a part floating in empty space has no joint, so
     * instead of faking one it is demoted to a gentle float. The drawing itself is never moved or edited —
     * only the author can safely redraw a detached part connected. An explicit `data-anchor` is respected.
     */
    fun animate(
        scene: OGSceneSpec,
        rules: Map<String, OGRegionMotion> = emptyMap(),
    ): OGSceneSpec {
        // Eye regions (matched by label) also tell us WHERE catchlights live. A catchlight / glint is a
        // small bright shape drawn ON the eyeball, and a model rarely labels it "eye" — so a pure label
        // match leaves that white dot floating, fully opaque, while the eye blinks shut (the monkey bug).
        // So we also blink any small shape that sits inside an eye's bounds, whatever it's called.
        val eyeBoxes = scene.shapes.mapNotNull { s ->
            if (isEyeLabel((s.label ?: "").lowercase())) boxOf(s.points) else null
        }
        // Caller rules are matched by case-insensitive substring and WIN over the built-in English
        // heuristics, so an app can drive idle motion for non-English labels or a custom rig (e.g.
        // mapOf("göz" to OGSceneIdle.Blink, "kuyruk" to OGSceneIdle.TailSway)) without forking this table.
        val loweredRules = rules.mapKeys { it.key.lowercase() }
        return scene.copy(
            shapes = scene.shapes.map { s ->
                val label = (s.label ?: "").lowercase()
                // A region's OWN motion (declared via SVG data-motion) WINS; otherwise a caller rule, then
                // the English idle heuristics, then a catchlight sitting on an eye. Null → leave it still.
                var motion = s.motion
                    ?: ruleFor(label, loweredRules)
                    ?: idleFor(label)
                    ?: (if (sitsOnAnEye(s.points, eyeBoxes)) Blink else null)
                    ?: return@map s
                // A pure-rotation wag (sway / wave) only reads right if it hinges where the part MEETS the
                // figure — and nothing should hinge on empty air. Handle it GENERICALLY, with no label and
                // no "body" classifier (an explicit `data-anchor` is always respected, so this only touches
                // a still-default pivot):
                //  - ATTACHED (part of the wag's outline actually touches the rest of the figure): hinge at the
                //    real JOINT — the midpoint of the contact zone (the outline points that touch) — so it wags
                //    from exactly where it meets the body instead of a buried bbox-edge pivot that opens a gap.
                //  - DETACHED (floating in empty space — a stray tail a model drew off the body, or a
                //    genuinely free balloon / leaf): there is NO joint, so don't fake one. Demote the wag to
                //    an honest gentle float. We never move or edit the drawing; only the model can safely
                //    redraw a detached part connected (the describe prompt now steers it to — see stagePrompt).
                if (isWag(motion) && motion.pivotAbs == null && anchorIsUnsetOrTop(motion.anchor)) {
                    val joint = contactAnchor(s, scene.shapes)
                    motion = if (joint != null) motion.copy(anchor = joint) else demoteToFloat(motion)
                }
                // A blink squashes an eye vertically — but a model draws one eye as SEVERAL shapes (white +
                // pupil + iris + catchlight) with DIFFERENT centres, and shutting each about its OWN mid-line
                // lands them on different lines: at full close the pupil pokes out below the shut white (the
                // "dark speck under the lid" bug). Re-root every eye part's blink onto the SHARED eye line —
                // the eyeball white's centre — so the whole eye collapses onto one line and stays concentric.
                // Label-free geometry; runs once at compile; a part on no eye keeps its own mid-line.
                if (isBlink(motion) && motion.pivotAbs == null && anchorIsUnsetOrMid(motion.anchor)) {
                    eyeLineFor(boxOf(s.points), eyeBoxes)?.let { line ->
                        motion = motion.copy(anchor = eyeAnchorOnLine(s.points, line))
                    }
                }
                s.copy(motion = motion)
            },
        )
    }

    /** A pure-rotation idle (a sway / wag / wave): it rotates about a pivot and does nothing else, so the
     *  ONLY thing that decides how it reads is where that pivot sits — which is why a tail needs re-rooting. */
    private fun isWag(m: OGRegionMotion): Boolean =
        m.rotate != null && m.scale == null && m.scaleY == null && m.translate == null

    /** True when a pivot is still the generic default — unset (centroid) or the preset's top-centre — i.e.
     *  nobody deliberately chose it, so a wag is free to re-root (or be demoted) by geometry. */
    private fun anchorIsUnsetOrTop(a: OGPointSpec?): Boolean =
        a == null || (kotlin.math.abs(a.x - 0.5f) < 1e-4f && kotlin.math.abs(a.y) < 1e-4f)

    /** A pure vertical squash (scaleY only, no scale / rotate / translate) — the blink shape: it shuts an
     *  eye to a thin line and does nothing else, so re-rooting its pivot onto the shared eye line is safe.
     *  Breathe also drives scaleY but carries a uniform `scale` too, so it is correctly excluded here. */
    private fun isBlink(m: OGRegionMotion): Boolean =
        m.scaleY != null && m.scale == null && m.rotate == null && m.translate == null

    /** True when a blink pivot is still the generic default — unset or the preset's dead-centre (0.5,0.5) —
     *  i.e. nobody deliberately chose it, so it's free to re-root onto the shared eye line. */
    private fun anchorIsUnsetOrMid(a: OGPointSpec?): Boolean =
        a == null || (kotlin.math.abs(a.x - 0.5f) < 1e-4f && kotlin.math.abs(a.y - 0.5f) < 1e-4f)

    /** The shared vertical mid-line (scene y) that all parts of ONE eye must blink onto: the centre of the
     *  LARGEST eye region whose bounds contain [box]'s centre. Picking the largest containing box selects the
     *  eyeball WHITE over a small pupil / catchlight drawn on it, so the white and everything on it collapse
     *  onto the same line and stay concentric. Null when the shape sits on no eye (→ it keeps its own line). */
    private fun eyeLineFor(box: FloatArray?, eyeBoxes: List<FloatArray>): Float? {
        if (box == null) return null
        val cx = (box[0] + box[2]) * 0.5f; val cy = (box[1] + box[3]) * 0.5f
        var best: FloatArray? = null; var bestArea = Float.NEGATIVE_INFINITY
        for (e in eyeBoxes) {
            if (cx in e[0]..e[2] && cy in e[1]..e[3]) {
                val a = areaOf(e)
                if (a > bestArea) { best = e; bestArea = a }
            }
        }
        return best?.let { (it[1] + it[3]) * 0.5f }
    }

    /** The bbox-relative anchor for [points] whose pivot lands on scene-y [lineY] (x stays centred — a pure
     *  vertical squash ignores anchor.x). Clamped so a part straddling the line edge still blinks cleanly. */
    private fun eyeAnchorOnLine(points: List<OGPointSpec>, lineY: Float): OGPointSpec {
        val b = boxOf(points) ?: return OGPointSpec(0.5f, 0.5f)
        val h = b[3] - b[1]
        val ay = if (h > 1e-6f) ((lineY - b[1]) / h).coerceIn(0f, 1f) else 0.5f
        return OGPointSpec(0.5f, ay)
    }

    // --- "attached vs floating": the generic, label-free safety net for a wag (Fable's detect+demote) -----
    // A filled-vector part is "attached" to the figure iff part of its outline touches another part (gap ≈ 0).
    // That is pure geometry — no English labels, no guessing which blob is "the body". When it touches, we hinge
    // the wag at the MIDPOINT of the contact zone (the outline points that touch) — the real joint — so the base
    // stays pinned to the body and no gap opens. A part that touches nothing is floating, so hinging it would
    // lie; we demote it to a gentle drift instead. We never move the drawing (auto-snapping corrupts correct
    // art — a balloon, a leaf); only the model can redraw a part connected.

    /** Max outline gap (fraction of the normalized scene) at which a wag part still counts as ATTACHED —
     *  small enough to catch a real detachment, loose enough to absorb curve-sampling slop. */
    private const val ATTACH_EPS = 0.02f

    /** Fraction of a demoted wag's rotation that survives (the rest of the motion becomes a gentle drift). */
    private const val FLOAT_ROTATE_KEEP = 0.33f

    /** Vertical drift (fraction of scene height) a demoted float gets, so a detached part bobs, not hinges. */
    private const val FLOAT_DRIFT = 0.01f

    /** The hinge pivot (as a bbox-relative `0..1` anchor) at which [region] joins the rest of the figure, or
     *  null when it touches nothing (→ it is detached and should float, not hinge). We sample every outline
     *  VERTEX of [region] AND every edge MIDPOINT, and keep the ones within [ATTACH_EPS] of some NON-backdrop
     *  other region; their mean is the midpoint of the contact zone, i.e. the real joint. Sampling edge
     *  midpoints (not only corners) catches a part that meets the figure along an EDGE — e.g. a fin or wing
     *  whose corners are buried inside / poke outside the body while the join crosses between them — which a
     *  vertex-only test would miss and wrongly demote to a float. Pivoting at the contact mean keeps the base
     *  pinned to the body through the whole wag (a buried bbox-edge pivot instead lets the part swing away →
     *  the "tail floats next to the body" gap). Backdrop-ish regions (over half the scene, or fully enclosing
     *  [region]) are skipped so a full-bleed sky / ground / card can't make everything "attached". Pure
     *  geometry; runs once at scene-compile time, never per frame. */
    private fun contactAnchor(region: OGPlacedShapeSpec, all: List<OGPlacedShapeSpec>): OGPointSpec? {
        val rb = boxOf(region.points) ?: return null
        val others = all.filter { o ->
            o !== region && o.points.size >= 2 && boxOf(o.points)?.let { ob ->
                !isBackdrop(ob, rb) && bboxGap(rb, ob) <= ATTACH_EPS // fast reject: boxes too far to touch
            } == true
        }
        if (others.isEmpty()) return null
        var sx = 0.0; var sy = 0.0; var n = 0
        val pts = region.points
        val m = pts.size
        var i = 0
        while (i < m) {
            val a = pts[i]
            val b = pts[(i + 1) % m]
            val mid = OGPointSpec((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
            // each vertex is visited exactly once (as `a`); each edge contributes its midpoint once
            for (p in arrayOf(a, mid)) {
                if (others.any { pointToPolyDistance(p, it.points) <= ATTACH_EPS }) { sx += p.x; sy += p.y; n++ }
            }
            i++
        }
        if (n == 0) return null
        val cx = (sx / n).toFloat(); val cy = (sy / n).toFloat()
        val w = rb[2] - rb[0]; val h = rb[3] - rb[1]
        return OGPointSpec(
            if (w > 1e-6f) (cx - rb[0]) / w else 0.5f,
            if (h > 1e-6f) (cy - rb[1]) / h else 0.5f,
        )
    }

    /** A region is backdrop-ish when its bounds cover more than half the scene or fully contain [inner] —
     *  a sky / ground / card that must not anchor a limb just by enclosing it. */
    private fun isBackdrop(b: FloatArray, inner: FloatArray): Boolean =
        areaOf(b) > 0.5f || (b[0] <= inner[0] && b[1] <= inner[1] && b[2] >= inner[2] && b[3] >= inner[3])

    /** Shortest gap between two axis-aligned boxes (0 if they overlap) — a cheap lower bound on the true
     *  outline distance, used to skip far-apart pairs before the O(n·m) outline test. */
    private fun bboxGap(a: FloatArray, b: FloatArray): Float {
        val dx = maxOf(0f, a[0] - b[2], b[0] - a[2])
        val dy = maxOf(0f, a[1] - b[3], b[1] - a[3])
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** Smallest distance from point [p] to any edge of the closed polyline [poly] (closing edge included). */
    private fun pointToPolyDistance(p: OGPointSpec, poly: List<OGPointSpec>): Float {
        val n = poly.size
        if (n < 2) return Float.POSITIVE_INFINITY
        var best = Float.POSITIVE_INFINITY
        var i = 0
        while (i < n) {
            val a = poly[i]
            val c = poly[(i + 1) % n]
            val d = pointSegmentDistance(p.x, p.y, a.x, a.y, c.x, c.y)
            if (d < best) best = d
            i++
        }
        return best
    }

    /** Euclidean distance from point `(px,py)` to the segment `(ax,ay)-(bx,by)`. */
    private fun pointSegmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val vx = bx - ax; val vy = by - ay
        val len2 = vx * vx + vy * vy
        val t = if (len2 <= 1e-12f) 0f else (((px - ax) * vx + (py - ay) * vy) / len2).coerceIn(0f, 1f)
        val dx = px - (ax + t * vx); val dy = py - (ay + t * vy)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** Turn a wag with no real joint (a DETACHED part) into an honest gentle FLOAT: keep only a faint
     *  rotation and add a small vertical drift, pivoting about the part's own centroid (no fake body hinge).
     *  Correct for a genuinely free balloon / leaf, and merely calmer for a limb a model drew off the body. */
    private fun demoteToFloat(m: OGRegionMotion): OGRegionMotion {
        val soft = m.rotate?.let { OGMotionScalar(it.from * FLOAT_ROTATE_KEEP, it.to * FLOAT_ROTATE_KEEP) }
        val drift = m.translate ?: OGMotionOffset(OGPointSpec(0f, -FLOAT_DRIFT), OGPointSpec(0f, FLOAT_DRIFT))
        return m.copy(rotate = soft, translate = drift, anchor = null)
    }

    /** First caller [rules] entry whose (already-lowercased) key appears in [label], or null. */
    private fun ruleFor(label: String, rules: Map<String, OGRegionMotion>): OGRegionMotion? {
        if (rules.isEmpty() || label.isEmpty()) return null
        return rules.entries.firstOrNull { it.key.isNotEmpty() && it.key in label }?.value
    }

    /** Eyes (but not eyebrows); whites + pupils + irises share the one blink timeline so they close
     *  together. The catchlight synonyms are here so a glint labelled by name blinks even when it's
     *  drawn just off the eyeball (the positional pass in [animate] catches the rest). */
    private fun isEyeLabel(label: String): Boolean =
        (("eye" in label && "brow" !in label) || "pupil" in label || "iris" in label ||
            "catchlight" in label || "glint" in label || "twinkle" in label)

    private fun idleFor(label: String): OGRegionMotion? = when {
        label.isEmpty() -> null
        isEyeLabel(label) -> Blink
        "tail" in label -> TailSway
        "body" in label || "belly" in label || "chest" in label || "torso" in label ||
            "tummy" in label || "breast" in label -> Breathe
        else -> null
    }

    /**
     * Resolve a motion KEYWORD (language-independent) to a ready-made idle [OGRegionMotion], or null if
     * it names nothing. This is the vocabulary a model declares with `data-motion="…"` on an SVG element
     * ([OGSvgScene]) — so motion is chosen by an explicit tag, not by guessing from an English label,
     * which lifts the "only English ids animate" ceiling. The same presets back the English auto-match
     * above, so a tagged part and a recognised label move identically.
     */
    fun motionNamed(name: String?): OGRegionMotion? = when (name?.trim()?.lowercase()) {
        "blink", "wink" -> Blink
        "breathe", "breath", "breathing" -> Breathe
        "sway", "tail", "tailsway", "wag", "hang", "flutter" -> Sway
        "wave", "waving" -> Wave
        "bob", "float", "bobbing", "drift" -> Bob
        "nod", "nodding" -> Nod
        "pulse", "pulsing", "glow", "heartbeat", "throb" -> Pulse
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

    // The ready-made idle motions, public so a caller can map them to its own labels via [animate]'s
    // rules. Each pivots about a HONEST anchor (see [OGRegionMotion.anchor]) rather than the centroid, so
    // the motion matches the thing it represents instead of ballooning / shearing about the middle.
    //
    // SEAMLESS-LOOP INVARIANT: the player loops the idle stage at
    // [com.solidkey.painpoints.stage.OGStageSvg.IDLE_LOOP_MS] (12 000 ms) by RESETTING the clock to 0.
    // A `reverse` motion returns to its rest pose only at whole multiples of its FULL period (2×duration).
    // So every preset's full period MUST divide 12 000 ms exactly, or the wrap lands mid-motion and the
    // figure visibly SNAPS back to rest once per loop (the breathe/sway "jerk" Özge caught — blink never
    // jerked because 3.0 s divides 12 s, but breathe 5.2 s / sway 3.6 s / wave 1.8 s did not). Full periods
    // below are all 2.0 s, 3.0 s or 4.0 s → each divides 12 s → the loop is seamless. `idleLoopDividesCleanly`
    // in the tests guards this; keep new presets on the {2,3,4,6} s period grid.

    /** A slow chest rise-and-fall: the body expands a touch, mostly UPWARD from its seated base
     *  (`anchor` bottom-centre), so a standing/sitting figure swells at the chest instead of inflating
     *  symmetrically. Deliberately subtle — attached parts (head/arms) don't yet ride the chest, that is
     *  the rig's job ([OGRig] / poke-to-deform), so a bigger breathe would read as drift. 4 s full period
     *  (2 s in, 2 s out) = a calm resting breath that divides the 12 s idle loop cleanly (no wrap snap). */
    val Breathe = OGRegionMotion(
        duration = 2.0f, mode = "reverse", easing = "easeInOut",
        scale = OGMotionScalar(1f, 1.02f), scaleY = OGMotionScalar(1f, 1.05f), anchor = OGPointSpec(0.5f, 1f),
    )

    /** A gentle side-to-side wag about the BASE of the shape (`anchor` top-centre by default — a tail /
     *  hanging leaf / ponytail swings from where it attaches). [animate] re-roots an ATTACHED wag at the
     *  real joint (the midpoint of where its outline touches the figure) via geometry, so a horizontal tail
     *  hinges at the body and never swings a gap open. 4 s full period (divides the 12 s idle loop → no snap). */
    val Sway = OGRegionMotion(
        duration = 2.0f, mode = "reverse", easing = "easeInOut",
        rotate = OGMotionScalar(-7f, 7f), anchor = OGPointSpec(0.5f, 0f),
    )

    /** Back-compat alias for [Sway] (the tail preset's original name). */
    val TailSway = Sway

    /** A periodic blink: the eye squashes shut to a thin line (`scaleY → ~0`) and springs back open, a clear
     *  beat so the face reads — not a whole-eye opacity fade that leaves the socket showing through. The
     *  preset pivots at the shape's own centre (`anchor` 0.5,0.5); [animate] then re-roots every eye part
     *  onto the SHARED eye line (the eyeball white's centre) so the white and the pupil / iris / catchlight
     *  drawn on it — whose own centres DIFFER — all collapse onto the SAME line and stay concentric. Without
     *  that, each part shut about its own mid-line and the pupil poked out below the closed white. 3 s full
     *  period (divides the 12 s loop). */
    val Blink = OGRegionMotion(
        duration = 1.5f, mode = "reverse", easing = "easeInOut",
        scaleY = OGMotionScalar(1f, 0.06f), anchor = OGPointSpec(0.5f, 0.5f),
    )

    /** A bigger swing for a raised hand/arm — a wave hinged at the shoulder (`anchor` bottom-centre, the
     *  arm's rooted end). Faster and wider than [Sway]. 2 s full period (divides the 12 s idle loop). */
    val Wave = OGRegionMotion(
        duration = 1.0f, mode = "reverse", easing = "easeInOut",
        rotate = OGMotionScalar(-16f, 16f), anchor = OGPointSpec(0.5f, 1f),
    )

    /** A gentle vertical float (a balloon, a firefly, a drifting leaf) — pure translate, no pivot.
     *  4 s full period (divides the 12 s idle loop). */
    val Bob = OGRegionMotion(
        duration = 2.0f, mode = "reverse", easing = "easeInOut",
        translate = OGMotionOffset(OGPointSpec(0f, -0.012f), OGPointSpec(0f, 0.012f)),
    )

    /** A small head nod, hinged at the neck (`anchor` bottom-centre). 4 s full period (divides the loop). */
    val Nod = OGRegionMotion(
        duration = 2.0f, mode = "reverse", easing = "easeInOut",
        rotate = OGMotionScalar(-6f, 6f), anchor = OGPointSpec(0.5f, 1f),
    )

    /** A soft size pulse about the centroid — a glow, a heartbeat, a throb (uniform, no anchor).
     *  2 s full period (divides the 12 s idle loop). */
    val Pulse = OGRegionMotion(
        duration = 1.0f, mode = "reverse", easing = "easeInOut",
        scale = OGMotionScalar(1f, 1.08f),
    )
}
