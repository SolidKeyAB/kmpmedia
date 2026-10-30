package com.solidkey.painpoints.stage

import com.solidkey.painpoints.ai.OGAiVector
import com.solidkey.painpoints.ai.OGSceneSpec
import com.solidkey.painpoints.compositor.OGEasing
import com.solidkey.painpoints.compositor.OGKeyframe
import com.solidkey.painpoints.compositor.OGKeyframedFloat
import com.solidkey.painpoints.fx.OGLightningSpec
import com.solidkey.painpoints.fx.OGSlashSpec
import com.solidkey.painpoints.fx.OGSpeedLinesSpec
import com.solidkey.painpoints.look.OGBlendMode
import com.solidkey.painpoints.look.OGLookSpec
import com.solidkey.painpoints.motion.OGClothSpec
import com.solidkey.painpoints.particle.OGParticleSpec
import com.solidkey.painpoints.style.OGStyleSpec
import kotlinx.serialization.json.Json

/**
 * The single entry point for the **OGStage** document — encode / decode a whole animated movie as
 * JSON, hand a language model the contract that produces one, and a few builders for assembling a
 * stage in code.
 *
 * This is the generator half of "describe → play": a model emits one JSON object and [decode] turns
 * it into a live [OGStage] that [OGStageView] plays. It reuses [OGAiVector]'s lenient, unknown-key
 * tolerant [Json] verbatim (model output is rarely pristine), so a stage decodes alongside the
 * scene / polygon / patch payloads with the same rules, and runs at generate time — never per frame.
 */
object OGStages {

    /** Reuse the AI codec's lenient config (ignoreUnknownKeys + isLenient + encodeDefaults=false). */
    val json: Json = OGAiVector.json

    // --- Decode / encode -----------------------------------------------------------------------

    /** Serialize a live [OGStage] to its JSON (to persist a movie or seed a prompt with the current one). */
    fun encode(stage: OGStage): String = json.encodeToString(OGStage.serializer(), stage)

    /**
     * Parse a model reply (or a saved file) describing a whole movie into a live [OGStage]. Tolerates
     * surrounding prose / markdown fences (via [OGAiVector.extractJson]). Throws if no valid JSON is
     * present; use [decodeOrNull] for `null` instead.
     */
    fun decode(text: String): OGStage =
        json.decodeFromString(OGStage.serializer(), OGAiVector.extractJson(text))

    /** [decode] but returns `null` instead of throwing on malformed input. */
    fun decodeOrNull(text: String): OGStage? = runCatching { decode(text) }.getOrNull()

    // --- Code builders -------------------------------------------------------------------------

    /** A transform track from `timeMs -> value` stops (all linear); a single stop is a constant. */
    fun track(vararg stops: Pair<Long, Float>): OGKeyframedFloat = OGKeyframedFloat.of(*stops)

    /** A transform track with an explicit [easing] on every segment. */
    fun eased(easing: OGEasing, vararg stops: Pair<Long, Float>): OGKeyframedFloat =
        OGKeyframedFloat(
            default = stops.firstOrNull()?.second ?: 0f,
            keyframes = stops.map { OGKeyframe(it.first, it.second, easing) },
        )

    /** A flat colour-fill layer. */
    fun solid(
        id: String,
        color: String,
        startMs: Long = 0L,
        endMs: Long? = null,
        opacity: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        onTap: OGStageTrigger? = null,
    ): OGStageLayer = OGStageLayer(
        id = id, content = OGStageContent.Solid(color), startMs = startMs, endMs = endMs, opacity = opacity,
        onTap = onTap,
    )

    /** A vector-scene layer (its own `motion` animates on the layer's local clock). */
    fun scene(
        id: String,
        scene: OGSceneSpec,
        startMs: Long = 0L,
        endMs: Long? = null,
        x: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        y: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        scale: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        rotationDeg: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        opacity: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        look: OGLookSpec? = null,
        blend: OGBlendMode = OGBlendMode.NORMAL,
        onTap: OGStageTrigger? = null,
    ): OGStageLayer = OGStageLayer(
        id = id, content = OGStageContent.Scene(scene), startMs = startMs, endMs = endMs,
        x = x, y = y, scale = scale, rotationDeg = rotationDeg, opacity = opacity, look = look, blend = blend,
        onTap = onTap,
    )

    /** A particle-emitter layer. */
    fun particles(
        id: String,
        spec: OGParticleSpec,
        startMs: Long = 0L,
        endMs: Long? = null,
        opacity: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        blend: OGBlendMode = OGBlendMode.NORMAL,
        onTap: OGStageTrigger? = null,
    ): OGStageLayer = OGStageLayer(
        id = id, content = OGStageContent.Particles(spec), startMs = startMs, endMs = endMs,
        opacity = opacity, blend = blend, onTap = onTap,
    )

    /** A breathing-slash ribbon layer (water / flame / thunder sweep); additive by default. */
    fun slash(
        id: String,
        spec: OGSlashSpec,
        startMs: Long = 0L,
        endMs: Long? = null,
        blend: OGBlendMode = OGBlendMode.PLUS,
    ): OGStageLayer = OGStageLayer(id = id, content = OGStageContent.Slash(spec), startMs = startMs, endMs = endMs, blend = blend)

    /** A forked-lightning layer; additive by default. */
    fun lightning(
        id: String,
        spec: OGLightningSpec,
        startMs: Long = 0L,
        endMs: Long? = null,
        blend: OGBlendMode = OGBlendMode.PLUS,
    ): OGStageLayer = OGStageLayer(id = id, content = OGStageContent.Lightning(spec), startMs = startMs, endMs = endMs, blend = blend)

    /** A manga speed / impact-lines layer. */
    fun speedLines(
        id: String,
        spec: OGSpeedLinesSpec,
        startMs: Long = 0L,
        endMs: Long? = null,
        blend: OGBlendMode = OGBlendMode.NORMAL,
    ): OGStageLayer = OGStageLayer(id = id, content = OGStageContent.SpeedLines(spec), startMs = startMs, endMs = endMs, blend = blend)

    /**
     * A **styled-vector-text** layer — a title / caption / lower-third, centred in the box (position it
     * with [x] / [y], a fraction of the box). [size] is the em height as a fraction of the stage height;
     * an optional [style] pack inks the glyphs hand-drawn (`roughen`) or alive (`boil` / `wave`).
     */
    fun text(
        id: String,
        text: String,
        size: Float = 0.14f,
        color: String = "#FFFFFF",
        bold: Boolean = false,
        italic: Boolean = false,
        family: String? = null,
        style: OGStyleSpec? = null,
        startMs: Long = 0L,
        endMs: Long? = null,
        x: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        y: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        scale: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        rotationDeg: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        opacity: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        look: OGLookSpec? = null,
        blend: OGBlendMode = OGBlendMode.NORMAL,
        onTap: OGStageTrigger? = null,
    ): OGStageLayer = OGStageLayer(
        id = id,
        content = OGStageContent.Text(text, size, color, bold, italic, family, style),
        startMs = startMs, endMs = endMs,
        x = x, y = y, scale = scale, rotationDeg = rotationDeg, opacity = opacity, look = look, blend = blend,
        onTap = onTap,
    )

    /**
     * A **raster-cutout** layer — a bundled image shown as real drawn pixels (not a redrawn vector),
     * optionally a frame-swap sprite ([frames] held in order on the local clock: blink / mouth / wave).
     * The image fits within [widthFrac] × [heightFrac] of the box (aspect preserved); place and size it
     * with [x] / [y] / [scale], and make it poke-reactive with [onTap]. The frame resources must be
     * bundled by the host app (Android `res/drawable`, iOS bundle).
     */
    fun image(
        id: String,
        frames: List<OGImageFrame>,
        widthFrac: Float = 1f,
        heightFrac: Float = 1f,
        startMs: Long = 0L,
        endMs: Long? = null,
        x: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        y: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        scale: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        rotationDeg: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        opacity: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        look: OGLookSpec? = null,
        blend: OGBlendMode = OGBlendMode.NORMAL,
        onTap: OGStageTrigger? = null,
    ): OGStageLayer = OGStageLayer(
        id = id,
        content = OGStageContent.Image(frames, widthFrac, heightFrac),
        startMs = startMs, endMs = endMs,
        x = x, y = y, scale = scale, rotationDeg = rotationDeg, opacity = opacity, look = look, blend = blend,
        onTap = onTap,
    )

    /**
     * A **wind-blown cloth / hair strip** layer — a scarf, cape edge, banner or a lock of long hair that
     * hangs and flutters on the breeze (the motion family's Verlet [OGClothSpec]: gravity + prevailing
     * wind + turbulent gusts). The strip's head pins at ([anchorX], [anchorY]) in the box and streams out;
     * [taper] narrows it to a point at the free end (hair / pennant). Pin it onto a character by placing
     * this layer at the hairline / hem with [x] / [y]. Deterministic → preview and export match.
     */
    fun cloth(
        id: String,
        cloth: OGClothSpec,
        anchorX: Float = 0.5f,
        anchorY: Float = 0.12f,
        color: String = "#FFFFFF",
        width: Float = 0.05f,
        taper: Boolean = true,
        tailX: Float = 0.5f,
        tailY: Float = 0.12f,
        startMs: Long = 0L,
        endMs: Long? = null,
        x: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        y: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        scale: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        rotationDeg: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        opacity: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        look: OGLookSpec? = null,
        blend: OGBlendMode = OGBlendMode.NORMAL,
        onTap: OGStageTrigger? = null,
    ): OGStageLayer = OGStageLayer(
        id = id,
        content = OGStageContent.Cloth(cloth, anchorX, anchorY, tailX, tailY, color, width, taper),
        startMs = startMs, endMs = endMs,
        x = x, y = y, scale = scale, rotationDeg = rotationDeg, opacity = opacity, look = look, blend = blend,
        onTap = onTap,
    )

    /** Shorthand for a single-frame still cutout (one bundled image, no frame-swap). */
    fun imageStill(
        id: String,
        resource: String,
        widthFrac: Float = 1f,
        heightFrac: Float = 1f,
        startMs: Long = 0L,
        endMs: Long? = null,
        x: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        y: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        scale: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        rotationDeg: OGKeyframedFloat = OGKeyframedFloat.const(0f),
        opacity: OGKeyframedFloat = OGKeyframedFloat.const(1f),
        look: OGLookSpec? = null,
        blend: OGBlendMode = OGBlendMode.NORMAL,
        onTap: OGStageTrigger? = null,
    ): OGStageLayer = image(
        id = id, frames = listOf(OGImageFrame(resource)), widthFrac = widthFrac, heightFrac = heightFrac,
        startMs = startMs, endMs = endMs, x = x, y = y, scale = scale, rotationDeg = rotationDeg,
        opacity = opacity, look = look, blend = blend, onTap = onTap,
    )

    // --- Interactivity shorthands --------------------------------------------------------------

    /** Attach a tap [trigger] to any [layer] (returns a copy) — the generic path for layer kinds whose
     *  builder has no `onTap` parameter (slash / lightning / speed-lines), or to add one after the fact. */
    fun tappable(layer: OGStageLayer, trigger: OGStageTrigger): OGStageLayer = layer.copy(onTap = trigger)

    /** A tap that plays a visual reaction ([OGTapAction.BOUNCE] / [OGTapAction.SHAKE] / [OGTapAction.SPIN]). */
    fun tap(action: OGTapAction, strength: Float = 1f, durationMs: Long = 600L): OGStageTrigger =
        OGStageTrigger(action = action, strength = strength, durationMs = durationMs)

    /** A tap that jumps the stage clock to absolute [seekMs] (a chapter jump). */
    fun seekTo(seekMs: Long): OGStageTrigger = OGStageTrigger(action = OGTapAction.SEEK, seekMs = seekMs)

    /** A tap that toggles the player between playing and paused. */
    fun playPause(): OGStageTrigger = OGStageTrigger(action = OGTapAction.PLAY_PAUSE)

    // --- Prompt: hand a model the contract -----------------------------------------------------

    /**
     * Build a ready-to-send instruction that constrains a language model to output exactly the
     * [OGStage] JSON [decode] expects — a whole animated movie: a timeline of layers, each a scene,
     * a particle emitter or a solid fill, transformed by keyframe tracks and graded by a look.
     *
     * The full per-region scene grammar (gradients, textures, glow, scatter, region motion) is the
     * one [OGAiVector.imageToVectorPrompt] already teaches for `OGVectorTarget.SCENE`; this prompt
     * references it rather than repeating it, and focuses on the stage/timeline wrapper.
     *
     * @param instruction the natural-language ask, e.g. "a sunrise over hills with petals drifting".
     */
    fun stagePrompt(instruction: String): String = """
        You output ONLY a JSON object describing a short animated "movie" (an OGStage) for the
        KMPMedia library. One object = a timeline of layers the library plays at 60fps.

        Top level:
        {
          "width": <px, e.g. 1080>, "height": <px>, "durationMs": <total length ms>,
          "fps": <e.g. 60>, "background": "#RRGGBB" (optional base coat),
          "layers": [ <layer>, ... ]   // drawn back-to-front: layers[0] is BEHIND
        }

        Each layer:
        {
          "id": "<unique>",
          "content": <one of the three content objects below>,
          "startMs": <when it appears, default 0>, "endMs": <when it leaves, default = movie end>,
          "x": <track>, "y": <track>,          // translate as a FRACTION of width/height (0 = none)
          "scale": <track>,                     // about the layer centre (1 = unchanged)
          "rotationDeg": <track>,               // degrees about the centre
          "opacity": <track>,                   // 0..1
          "look": { "brightness":0,"contrast":1,"saturation":1,"temperature":0,"tint":0,"hue":0 },
          "blend": "normal"|"multiply"|"screen"|"overlay"|"lighten"|"darken"|"plus",
          "onTap": { "action":"bounce"|"shake"|"spin"|"seek"|"playpause",
                     "strength":1.0, "durationMs":600, "seekMs":<ms> }   // optional — makes it tappable
        }

        A transform <track> is a keyframed float on ABSOLUTE movie time:
          { "default": <v>, "keyframes": [ {"timeMs":<ms>,"value":<v>,"easing":"LINEAR"|"EASE_IN"|
            "EASE_OUT"|"EASE_IN_OUT"}, ... ] }
        Omit a track (or give it one keyframe) to hold it constant. A layer's content has its OWN
        internal clock that starts at the layer's startMs (so a scene's day->night begins when it
        enters), while these transform tracks run on the whole-movie clock.

        The content objects (note the "type" discriminator):
          { "type":"solid", "color":"#RRGGBB" }
          { "type":"scene", "scene": <an OGSceneSpec> }
          { "type":"particles", "particles": <an OGParticleSpec> }
          { "type":"slash", "slash": <an OGSlashSpec> }                 // a water/flame/thunder sweep
          { "type":"lightning", "lightning": <an OGLightningSpec> }     // a forked bolt
          { "type":"speedlines", "speedLines": <an OGSpeedLinesSpec> }  // manga impact lines
          { "type":"text", "text":"TITLE", "size":0.14, "color":"#RRGGBB", "bold":false,
            "italic":false, "family":null, "style": <an OGStyleSpec, optional> }  // styled vector text
        The slash/lightning/speedlines specs are the KMPMedia action-FX packs, authored as their own
        JSON; they play as live, self-animating layers (good on a key beat, usually blend "plus").
        A "text" layer is a title / caption / lower-third: its glyphs are vectorized and filled, centred
        in the layer box — position it with the layer's x/y tracks, size it with "size" (em height as a
        fraction of the movie height, e.g. 0.1 a caption, 0.25 a big title), and fade it in/out with an
        "opacity" track or its startMs/endMs window. Its optional "style" is an OGStyleSpec drawing-style
        pipeline (ops like {"op":"roughen","amplitude":0.02} for hand-drawn edges, or {"op":"boil",...} /
        {"op":"wave",...} to keep the letters alive); omit it for clean filled letters.

        An OGSceneSpec is the SAME model-authored vector scene used elsewhere: labelled polygon
        regions in normalized 0..1 space with fills / gradients / smoothing / opacity / blend, an
        optional scene "background", a scene "look", per-region "motion" (translate/scale/rotate/
        opacity) and a scene-level "motion" that swings the grade + sky (day->night). Author it exactly
        as the KMPMedia image-to-scene contract specifies.

        An OGParticleSpec is a data-defined emitter: fields include "maxParticles", "emissionRate",
        "burst", "lifetimeMs", "x","y" (0..1 origin), "angleDeg" (270 = up), "spreadDeg", "speed",
        "gravityY", "startSize","endSize", "startColor","endColor", "shape"
        ("circle"|"square"|"triangle"|"star"|"petal"|"teardrop"), and "seed".

        Add "onTap" to a layer to make it INTERACTIVE — a finger on that layer fires a reaction while
        the movie plays. "bounce" / "shake" / "spin" are spring-driven visual reactions on the layer
        itself ("strength" ~1 scales how big, "durationMs" how long it settles); "seek" jumps the movie
        clock to "seekMs" (a chapter jump); "playpause" toggles playback. Omit "onTap" for a normal,
        watch-only layer. Taps fall through non-interactive layers to an interactive one behind, and the
        hit area is the layer's box — so to make distinct things tappable, give each its own on-screen
        zone with a sub-1 "scale" and an "x"/"y" offset.

        Compose the movie from these: e.g. a scene layer for the backdrop (with its own day->night
        motion), a particle layer of drifting petals on top with blend "plus", a gentle "scale" track on
        the backdrop for a slow push-in, and a "text" title card that fades in over the first second.
        Keep it resolution-independent (normalized coords).

        Alternative format — STRONGLY PREFERRED whenever the request names a recognizable SUBJECT (an
        animal, person, character, object, vehicle, plant, food): reply with ONE standalone
        <svg ...>...</svg> document and nothing else, and actually DRAW that subject as labelled, filled
        parts (for "a monkey on a branch holding a banana": branch, tail, body, belly, arms, legs, head,
        ears, face, eyes, muzzle, banana — each its own shape). A language model draws FAR more reliably
        in SVG than by hand-placing JSON polygon points, so do NOT fall back to an atmospheric background
        + title when a concrete subject is asked for — compose it part by part.

        Match the way you BUILD and MOVE the subject to WHAT KIND of thing it is:
          - A CREATURE or PERSON (animal, human, character): use the anatomy + joint rules below —
            rooted, tapered limbs, joints that fold the real way, and a few parts that
            breathe / blink / sway.
          - A MECHANICAL OBJECT or VEHICLE (a lamp, a door, a crane, a car, a windmill, a clock): it
            has no muscles but it has MECHANISMS — hinges, lids, wheels, arms, pendulums. Draw each
            moving part as its own shape anchored at its real PIVOT and leave the rigid body still: a
            hinge or lid rotates about its hinge EDGE (data-motion="nod" or "sway" with a data-anchor
            on that edge), a pendulum or hanging sign swings from its TOP (data-motion="sway"
            data-anchor="top"), a wheel / gear / propeller spins about its CENTRE (wrap it in a <g>
            and give that layer a rotate track, or data-motion="pulse" for a throb).
          - A GAME PIECE or TOKEN (a chess knight, a die, a card, a pawn, a marble): draw it as ONE
            rigid shape or <g> group — it has NO internal joints. Give it life through WHOLE-PIECE
            motion on its layer/group instead: a hop (a "y" track up then down), a flip or spin (a
            "rotationDeg" track), a slide (an "x" track), or a soft settle (data-motion="bob"). A
            piece is a prop you MOVE and place, not a body you pose.
        (A BOARD, grid, map or scoreboard is not one figure but a LAYOUT of many cells — build that
        arrangement in your app and drop living pieces onto it; here you draw the single subject.)

        First decide the VIEWPOINT the request implies and keep EVERY part agreeing on it — a figure
        only reads right when the whole of it is drawn at ONE camera angle:
          - FRONT (a portrait, "facing", "front", and the default): the body is symmetric; BOTH of
            each paired part show (two eyes, two ears, both arms, both legs) mirrored about the centre
            line, and the face points at the viewer.
          - THREE-QUARTER ("3/4", "turned", "glancing away"): still both of each paired part, but the
            FAR side is narrower and shifted toward the centre line, the far eye/ear smaller, and the
            nose / muzzle pushed toward the near cheek.
          - PROFILE / SIDE ("side", "profile", "from the side", "left/right facing", "walking past"):
            draw only ONE of each paired part — ONE eye, ONE ear, ONE near arm and ONE near leg; the
            FAR arm/leg, if shown at all, is drawn FIRST (behind the body), smaller and mostly hidden.
            The head is a side silhouette: the nose / muzzle / beak juts out on the facing side, the
            mouth and chin sit under it, the back of the head or hair rounds out behind, and a tail or
            snout points the way the body faces.
          - BACK ("from behind", "back turned"): the face is HIDDEN — show the back of the head / hair,
            the shoulders and back, the heels; no eyes, nose or mouth.
          - SEATED / CROUCHING / LYING: the legs FOLD at hip then knee and OVERLAP the torso; the body
            rests on a seated base, and breathing / idle motion anchors there.
        Paint order follows the angle: the parts NEAREST the camera draw LAST (in front). In profile
        the near arm and near leg sit in front of the body and the far ones behind it; in three-quarter
        the near side overlaps the far side. Choose the angle ONCE and keep every part, overlap and
        pivot consistent with it — never a front-facing face on a side-on body.

        Then decide what the subject is DOING and make the POSE show it — a figure asked to walk, run,
        jump, gallop, fly, swim or reach must NOT be drawn in a neutral stand. A WALK or RUN splits the
        legs into a STRIDE: one leg swings FORWARD and plants, the other trails BEHIND, the thighs open
        at the hip and at least one knee is clearly BENT — never two near-vertical parallel legs side by
        side (that reads as standing, not moving); the arms swing in counterpoint (the arm OPPOSITE the
        lead leg comes forward). A gallop gathers the legs under the body then extends them front-and-
        back; a jump tucks the legs up; a swimmer or flyer bows the SPINE / wings into the direction of
        travel so the whole body curves with the motion. Pick the action pose FIRST, then hang the idle
        breathing / sway on top of it.

        For a creature or figure, give it believable ANATOMY. Draw each whole arm and leg as ONE
        continuous, smooth, slightly TAPERED shape that is ROOTED where it meets the body (at the
        shoulder or hip) and overlaps the torso there, so a limb can NEVER float free; the hand or foot
        is a rounded shape attached at the far end (a few fingers / toes). A TAIL follows the same rule:
        root it at the rump and OVERLAP the body so its base is hidden behind the torso (order it before
        the body, i.e. drawn behind), then let it curve out — never a separate oval floating beside the
        body with a gap. EARS, horns and antlers follow the same rule: seat each one so its base OVERLAPS
        well INSIDE the head outline and order it BEFORE the head (drawn behind), so only the part that
        rises above the head shows — never a triangle perched ON TOP of the head with a flat seam across
        the forehead or its base corners sticking out past the head's edge. Only split a limb into an
        upper + lower piece when it is clearly BENT (a raised waving arm, a kneeling leg) — and then make
        the two pieces OVERLAP heavily at the joint (elbow/knee) so there is no gap, with the joint where
        it really bends, and BEND IT THE WAY A REAL JOINT BENDS: the two segments meet at an ANGLE and the
        far segment FOLDS BACK — it must NEVER continue straight on in the same outward direction past the
        joint (that reads as a backward-bending, broken limb). For a raised waving arm, the upper arm goes
        UP and OUT from the shoulder to the elbow (the elbow is the OUTER corner of the bend), then the
        forearm folds back UP and slightly IN so the hand rests up beside the head — do NOT let the forearm
        keep travelling outward past the elbow. A bent leg is the same: the knee is the forward corner and
        the shin folds back beneath it. A RAISED or WAVING arm (data-motion="wave") is a FOREGROUND gesture: order it
        LAST of all — AFTER the body, the head AND every face detail (cheeks, eyes, nose, whiskers) — so
        it is drawn IN FRONT of everything, and reach the hand OUT past the head's silhouette. That way
        the swing stays visible beside/in front of the head and is NEVER covered: not by the head, and
        not by a cheek or other face shape layered on after it. Keep the swing clear of the eyes. When a
        moving part is made of SEVERAL shapes (an arm = upper + forearm + hand + shading, a tail of
        segments), WRAP those shapes in ONE <g> group and put the data-motion (+ data-anchor) on the <g>,
        not on each piece. A group is a LAYER that moves as one: its shapes paint together at one depth
        (order the whole <g> where the part belongs — a waving arm's <g> goes LAST, in front) and they
        share ONE pivot, so the whole limb swings rigidly from its shoulder/base instead of each piece
        spinning about its own centre and shearing apart. Imply fur / feather / scale TEXTURE by giving the silhouette
        a slightly jagged, tufted OUTLINE (little notches along the back, tail, cheeks, chest) plus a few
        short accent tufts. Part COUNT is NOT a limit — the player renders 100+ shapes at 60fps — so
        make it RICH: keep the STRUCTURAL skeleton small and carefully placed (body, head, rooted limbs,
        roughly 20-30 shapes), then layer on as much DETAIL as you like (fur tufts along edges, shading
        and highlight patches, finer facial features, claws, whiskers, spots). Detail shapes sit ON TOP
        of already-placed parts, so they add richness WITHOUT risking the pose. A rich subject is happily
        50-100 shapes; just don't scatter loose STRUCTURAL pieces — pile the extra shapes into detail.

        The NAMED joints and the way each one really bends — draw and fold every limb to agree
        with the skeleton a pose / anatomy model already knows, so naming them keeps the figure
        honest rather than invented:
          - PERSON / UPRIGHT CHARACTER: the chain is hips → spine → neck → head, each arm
            shoulder → elbow → wrist → hand, each leg hip → knee → ankle → foot. Shoulders and
            hips are BALL joints (they swing any direction). The elbow and knee are one-way
            HINGES that only CLOSE — the far segment folds back toward the limb's root (the elbow
            closes ~0–150° bringing the hand toward the shoulder, the knee closes ~0–140° bringing
            the heel back) and NEVER hyperextends past straight the other way. The neck turns /
            tilts only ~45°; the wrist and ankle flex only a little.
          - FOUR-LEGGED ANIMAL (cat, dog, horse, deer): the front and hind legs bend in OPPOSITE
            directions, and getting that asymmetry right is what makes it read as a real animal.
            FRONT leg: shoulder → elbow (tucked back near the chest) → wrist (the low "front knee",
            which bends slightly BACKWARD) → paw. HIND leg: hip → stifle (a knee that bends FORWARD
            like ours) → HOCK → paw — the HOCK is an ankle partway up the back leg and bends the
            OPPOSITE way (the famous "backward knee"), then the lower leg drops straight to the
            paw. The spine is a soft curve and the tail continues it off the rump.
          - BIRD: the WING folds in a Z against the body (shoulder → elbow → wrist) and opens /
            swings from the SHOULDER; the leg's one visible joint is the ANKLE and it bends
            BACKWARD (the true knee hides up in the feathers), with the foot gripping below it.
          - FISH: no limbs and no hinges — a flexible SPINE that curves in a smooth S; the TAIL fin
            sweeps side to side and the body ripples with it, while the dorsal / pectoral fins flex
            gently from where they root on the body — draw each fin with a few radiating rays and let
            the tail fin's base overlap DEEP into the body so no fin ever looks stuck on.
          - INSECT / BUG: a stiff body in three segments (head, thorax, abdomen) with SIX legs in
            three pairs splaying out and DOWN from the thorax, each leg sharply jointed with its
            "knee" pointing UP above the body; wings beat from the thorax and antennae pivot at the
            head.
          - REPTILE: a SNAKE is pure spine — a smooth lateral S-wave, no limbs, no hinges; a LIZARD
            / crocodile has a SPRAWLING stance — the legs stick OUT TO THE SIDE (elbows and knees
            point outward, not tucked under), and the tail continues the spine.
          - HAND: wrist → palm → five fingers; every finger has three knuckle HINGES (the thumb
            two) that all curl the SAME way toward the palm and only close, never bending backward;
            the thumb folds across to oppose the fingers.
          - PLANT: a stem has no hinge — it is anchored at its base / pot and BOWS smoothly along
            its whole length; each leaf or petal pivots where it joins the stem.
          (A mechanism / vehicle has no joints that bend — its moving parts rotate or slide about a
          fixed pivot; see the MECHANICAL OBJECT rules above.)

        Make the forms feel REAL, not blocky: build the body and limbs from smooth, CURVED outlines — a
        forearm or shin is a rounded, slightly TAPERED capsule, not a hard bar; draw them with curved
        bezier path edges (C / Q commands), or a <rect> with an "rx" corner radius; hands, feet, belly,
        head and cheeks are soft ovals. OVERLAP the ends of connected segments a little so a joint reads
        as joined, not as two floating boxes with a gap. Avoid sharp straight-edged rectangles and hard
        corners unless the thing really is angular.

        For extra REALISM (optional, and still fully movable): SHADE the major shapes with gradients —
        define an SVG <linearGradient> or <radialGradient> in <defs> and reference it with fill="url(#id)"
        — lighter where the light falls, darker in creases and on undersides; add small HIGHLIGHT glints
        (a pale dot on each eye and the nose, a bright rim on a lit edge) and soft SHADOW patches where
        parts overlap. Gradients and highlights read as depth while every part stays its own labelled
        region (a radialGradient on a round body, a vertical linearGradient on fur, etc.).

        Give every shape an "id" (it becomes that part's animatable name, e.g. id="tail" or id="forearm_l")
        and a "fill" (a solid #RRGGBB, or url(#id) for a gradient); use paths / polygons / rect / circle /
        ellipse, ordered back-to-front. We
        compile it into the same movie (each element becomes a labelled region you can later animate or tap).

        To bring the drawing to LIFE, add a data-motion attribute to the few parts that should move — do
        NOT animate everything. The vocabulary (works in ANY language, so you do not need English ids):
          data-motion="blink"   on each eye / eyelid  (it squashes shut like a lid)
          data-motion="breathe" on the chest / body   (a slow swell from the base)
          data-motion="sway"    on a tail / leaf / hanging hair (a gentle wag from where it attaches)
          data-motion="wave"    on a raised hand / arm (a bigger swing from the shoulder)
          data-motion="bob"     on a floating thing (a soft up-and-down drift)
          data-motion="nod"     on a head (a small nod)
          data-motion="pulse"   on a glow / heart (a soft size pulse)
        A rotating/squashing motion hinges at the part's BASE by default; override it with
        data-anchor="top|bottom|left|right|center" (or "x,y" fractions) when the base is elsewhere — e.g.
        a tail that extends to the LEFT should pivot on its right: data-anchor="right". Optionally set
        data-dur="<seconds>" to change the beat. You can put data-motion on a <g> to move a whole group.
        Example: <ellipse id="eye_l" cx=".." cy=".." rx=".." ry=".." fill="#111" data-motion="blink"/>.

        The living motion belongs to the SUBJECT, not its surroundings. The BACKGROUND and SCENERY —
        sky, ground, water, walls, an iceberg, hills, furniture — stays STILL; give it NO breathe, sway
        or bob. Loose AMBIENT bits of the setting (snowflakes, dust, bubbles, falling leaves, floating
        sparks) MAY drift, but only on their OWN slow, independent beat (a gentle "bob" with a longer
        data-dur), never the subject's motion. Above all, never let the backdrop or its flecks rise and
        fall in time with the figure's breathing, or the whole scene looks pinned to the subject's chest
        instead of sitting behind it.

        Before you output the SVG, CHECK the figure against this list and fix anything that fails:
          - the whole figure holds ONE viewpoint — a side-on body carries a side-on face with ONE of
            each paired part (not two front-facing eyes on a profile head), and the nearest parts are
            drawn LAST / in front;
          - every arm, leg and tail OVERLAPS the body where it attaches (nothing floats free with a gap);
          - each ear / horn / antler is seated BEHIND the head with only its tip rising above it (no flat
            seam across the top, no base corners poking past the head's edge);
          - every BENT joint angles the real way and the far segment FOLDS BACK (no backward/broken elbow
            or knee, no forearm continuing outward past the elbow);
          - a four-legged animal's FRONT and HIND legs bend in OPPOSITE ways (the hind leg's hock
            is a backward-bending ankle, not a second forward knee); a bird's leg bends backward at
            the ankle; a stem bows along its length rather than snapping at a hinge; a snake or fish
            has no limbs (it curves, never hinges); an insect has SIX legs, and a sprawling lizard's
            legs splay out to the SIDE;
          - if the subject is DOING something (walking, running, jumping, flying, swimming), the POSE
            shows it — a walk / run SPLITS the legs into a stride (one forward, one trailing, a bent
            knee), NOT two parallel vertical legs that read as a stand, and a swimmer / flyer's body or
            wings curve with the motion;
          - a waving / raised arm is ordered LAST so it is drawn IN FRONT of the head and every face
            detail, with the hand reaching out past the head's silhouette and clear of the eyes;
          - the BACKGROUND / scenery stays STILL and independent — it does NOT breathe, sway or bob in
            time with the subject (ambient flecks may drift, but on their own slower beat);
          - any paired eyes sit on one shared line; no important part is hidden behind another.

        Use the JSON movie format above instead only when the request is an ATMOSPHERE or mood (a stormy
        sea, a neon city, a calm dawn) with no specific subject to draw. Pick EXACTLY ONE format — the
        JSON movie OR one SVG document — never both.

        Output only that one JSON object OR that one <svg> document — no prose, no markdown fences.

        Task: $instruction
    """.trimIndent()
}
