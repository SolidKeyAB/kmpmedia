package com.solidkey.painpoints.motion

import com.solidkey.painpoints.ai.OGAiVector
import com.solidkey.painpoints.fx.OGFxNoise
import com.solidkey.painpoints.shape.OGPoint
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * The **data form of a cloth strip's feel** — the knobs that make a hanging, fluttering piece of fabric,
 * as plain serializable JSON so the whole primitive is **AI-authorable** like `OGDynamics` / `OGLooks` /
 * `OGMotions` (describe it in words, a model returns this JSON — see [OGCloths.clothPrompt]).
 *
 * All positions are **normalized `0..1`** of the target box (the house convention, same as [OGMotionPath]);
 * forces are in those units per second². Node count is fixed when the [OGClothStrip] is built.
 *
 * @param nodes how many point-masses along the strip (≥2; 12–20 is a natural scarf).
 * @param length total rest length of the strip (normalized); the strip cannot stretch past this.
 * @param gravity downward pull (normalized units/s²); higher drapes harder.
 * @param damping how fast motion bleeds off (per second); higher settles sooner, lower keeps swaying.
 * @param wind prevailing horizontal wind (signed: `+` blows right, `−` blows left); gusts ride on top. `0` = calm.
 * @param windAmplitude peak turbulent gust force on top of [wind]; `0` = steady wind only.
 * @param windFrequency how fast the gusts wander (cycles/s of the fractal-noise field).
 * @param windSeed pick a different-but-repeatable gust pattern.
 * @param pinTail also pin the far end (a flag/banner strung between two points); otherwise only the head is pinned.
 */
@Serializable
data class OGClothSpec(
    val name: String? = null,
    val nodes: Int = 14,
    val length: Float = 0.45f,
    val gravity: Float = 0.9f,
    val damping: Float = 0.9f,
    val wind: Float = 0f,
    val windAmplitude: Float = 0.6f,
    val windFrequency: Float = 0.7f,
    val windSeed: Int = 7,
    val pinTail: Boolean = false,
)

/**
 * A lightweight **1-D Verlet cloth strip** — the "real fabric" member of the motion-dynamics family: a
 * scarf, a cape edge, a flag, a banner or a hair strand. Unlike [OGFollowChain] (N springs in series,
 * which *stretch* and trail like a comet tail), this strip is **inextensible** — neighbouring nodes hold a
 * rest distance via position-based **distance constraints** — and it has **gravity** and ambient **wind**,
 * so it *hangs* at rest and *flutters* in motion. That is the difference between a trailing streamer and a
 * piece of cloth. (Rule of thumb: stretches & trails → [OGFollowChain]; hangs at a fixed length & flutters
 * → [OGClothStrip].)
 *
 * It advances on a **fixed internal substep** (not the raw frame `dt`): position Verlet is only stable and
 * *deterministic* at a constant step, so [step] clamps `dt`, accumulates it, and runs whole substeps. That
 * is also what makes it **frame-identical on Android & iOS** and **exact for fixed-step GIF/MP4 export**.
 * Note it is a **live stepper, not closed-form** — unlike [OGSpring] it has no `valueAt(t)`; to *scrub* it,
 * re-simulate from `0` (deterministic, see [OGCloths.sampleAt]). Pure maths, zero dependency, no per-frame
 * allocation (state lives in preallocated `FloatArray`s). This strip is deliberately **1-D** — for a 2-D sheet
 * that drapes over and collides with poles, reach for [OGCloth]; a public constraint/solver API, self-collision
 * and tearing remain out of scope for both (that would make them a physics engine, which this is not).
 */
class OGClothStrip(spec: OGClothSpec) {
    /** The live feel. Node count is fixed at construction; other fields take effect on the next [step]. */
    var spec: OGClothSpec = spec

    /** Number of nodes along the strip. */
    val count: Int = spec.nodes.coerceAtLeast(2)

    private val px = FloatArray(count)
    private val py = FloatArray(count)
    private val ox = FloatArray(count) // previous positions (Verlet's implicit velocity)
    private val oy = FloatArray(count)
    private var seeded = false
    private var accum = 0f
    private var simTime = 0f // total simulated seconds (drives the deterministic wind)

    /** Seed every node hanging straight down from ([anchorX], [anchorY]) with zero velocity (call on placement/teleport). */
    fun reset(anchorX: Float, anchorY: Float) {
        val rest = restLen()
        for (i in 0 until count) {
            px[i] = anchorX; py[i] = anchorY + i * rest
            ox[i] = px[i]; oy[i] = py[i]
        }
        seeded = true
        accum = 0f
    }

    /**
     * Advance the strip toward wall-clock [dt], pinning the head node to ([anchorX], [anchorY]) (and, if
     * [OGClothSpec.pinTail], the tail to ([tailX], [tailY])). Runs whole fixed substeps; a stalled frame is
     * clamped so it can't explode. No allocation.
     */
    fun step(dt: Float, anchorX: Float, anchorY: Float, tailX: Float = anchorX, tailY: Float = anchorY) {
        if (!seeded) { reset(anchorX, anchorY); return }
        if (dt <= 0f) return
        accum += dt.coerceIn(0f, MAX_FRAME)
        var guard = 0
        while (accum >= SUBSTEP && guard < MAX_SUBSTEPS) {
            substep(SUBSTEP, anchorX, anchorY, tailX, tailY)
            accum -= SUBSTEP
            simTime += SUBSTEP
            guard++
        }
        if (guard >= MAX_SUBSTEPS) accum = 0f // drop any backlog a very long stall produced
    }

    private fun substep(h: Float, ax: Float, ay: Float, tx: Float, ty: Float) {
        val s = spec
        val drag = 1f - (s.damping * h).coerceIn(0f, 1f)
        val h2 = h * h
        val amp = s.windAmplitude
        val freq = s.windFrequency
        val seed = s.windSeed
        // Integrate every node (pinned ones are overwritten right after).
        for (i in 0 until count) {
            val vx = (px[i] - ox[i]) * drag
            val vy = (py[i] - oy[i]) * drag
            // Wind = a steady prevailing push + deterministic fractal-noise gusts (zero AI-random; same
            // field on Android & iOS).
            val gust = if (amp != 0f) OGFxNoise.fbm(simTime * freq, i * 0.25f, 2, seed) * amp else 0f
            val windForce = s.wind + gust
            val nx = px[i] + vx + windForce * h2
            val ny = py[i] + vy + s.gravity * h2
            ox[i] = px[i]; oy[i] = py[i]
            px[i] = nx; py[i] = ny
        }
        pin(ax, ay, tx, ty)
        // Satisfy the distance constraints (position-based) a few passes; pinned nodes stay put.
        val rest = restLen()
        repeat(ITER) {
            for (i in 0 until count - 1) {
                val dx = px[i + 1] - px[i]
                val dy = py[i + 1] - py[i]
                val d = sqrt(dx * dx + dy * dy)
                if (d < 1e-6f) continue
                val diff = (d - rest) / d
                val m0 = if (isPinned(i, tx = tx)) 0f else 1f
                val m1 = if (isPinned(i + 1, tx = tx)) 0f else 1f
                val sum = m0 + m1
                if (sum == 0f) continue
                val cx = dx * diff
                val cy = dy * diff
                px[i] += cx * (m0 / sum); py[i] += cy * (m0 / sum)
                px[i + 1] -= cx * (m1 / sum); py[i + 1] -= cy * (m1 / sum)
            }
            pin(ax, ay, tx, ty) // re-pin so the ends never drift from their anchors
        }
    }

    private fun pin(ax: Float, ay: Float, tx: Float, ty: Float) {
        px[0] = ax; py[0] = ay
        if (spec.pinTail) { px[count - 1] = tx; py[count - 1] = ty }
    }

    private fun isPinned(i: Int, tx: Float): Boolean = i == 0 || (spec.pinTail && i == count - 1)

    private fun restLen(): Float = spec.length / (count - 1).coerceAtLeast(1)

    /** X of node [i] (0 = the pinned head). */
    fun x(i: Int): Float = px[i]

    /** Y of node [i]. */
    fun y(i: Int): Float = py[i]

    /** Node [i] as an [OGPoint]. */
    fun point(i: Int): OGPoint = OGPoint(px[i], py[i])

    private companion object {
        const val SUBSTEP = 1f / 120f    // fixed step — Verlet is only stable/deterministic at a constant dt
        const val MAX_FRAME = 0.064f     // clamp a stalled frame (mirrors OGSpringValue.update)
        const val ITER = 4               // constraint relaxation passes (internal; not a spec knob)
        const val MAX_SUBSTEPS = 16      // backstop so a huge stall can't spiral
    }
}

/**
 * The **codec + presets** for [OGClothSpec] — decode / encode a `.cloth` pack (designer- or model-authored),
 * reach for a ready-made feel, or build a [clothPrompt] that constrains a model to emit a valid one. Mirrors
 * `OGDynamics` / `OGMotions` / `OGLooks`: tolerant of code fences and stray prose, reusing the same
 * [OGAiVector.json] / [OGAiVector.extractJson]. No network, no AI SDK.
 *
 * (Named `OGCloths`, the plural: `OGCloth` is the 2-D cloth-mesh *sheet* and [OGClothStrip] the 1-D *strip*;
 * this one codec serves both — see the mesh-suffixed members [decodeMeshSpec] / [meshPresets] / [clothMeshPrompt].)
 */
object OGCloths {
    /** The shared lenient JSON config (reused from `OGAiVector` so all data layers agree). */
    val json get() = OGAiVector.json

    /** Decode a JSON cloth pack into its [OGClothSpec]. Throws on malformed input. */
    fun decodeSpec(text: String): OGClothSpec =
        json.decodeFromString(OGClothSpec.serializer(), OGAiVector.extractJson(text))

    /** [decodeSpec] but returns `null` instead of throwing on malformed input. */
    fun decodeSpecOrNull(text: String): OGClothSpec? = runCatching { decodeSpec(text) }.getOrNull()

    /** Serialize a cloth pack to JSON (compact; defaults omitted). */
    fun encode(spec: OGClothSpec): String = json.encodeToString(OGClothSpec.serializer(), spec)

    /** Ready-made fabrics, keyed by name. Each is a plain [OGClothSpec] you can `copy()`. */
    val presets: Map<String, OGClothSpec> = linkedMapOf(
        "scarf" to OGClothSpec(name = "scarf", nodes = 14, length = 0.46f, gravity = 0.9f, damping = 0.75f, wind = -0.5f, windAmplitude = 0.6f, windFrequency = 0.8f, windSeed = 7),
        "flag" to OGClothSpec(name = "flag", nodes = 16, length = 0.5f, gravity = 0.4f, damping = 0.6f, wind = 1.1f, windAmplitude = 0.7f, windFrequency = 1.0f, windSeed = 11, pinTail = true),
        "banner" to OGClothSpec(name = "banner", nodes = 18, length = 0.7f, gravity = 0.6f, damping = 0.7f, wind = 0.7f, windAmplitude = 0.5f, windFrequency = 0.55f, windSeed = 13, pinTail = true),
        "hair" to OGClothSpec(name = "hair", nodes = 10, length = 0.3f, gravity = 1.1f, damping = 1.1f, windAmplitude = 0.3f, windFrequency = 0.5f, windSeed = 3),
    )

    /** Look up a [presets] entry by name (case-insensitive), or `null` if there is none. */
    fun preset(name: String): OGClothSpec? = presets[name.lowercase()]

    /**
     * Deterministically **re-simulate a fresh head-pinned strip to time [timeSec]** at the fixed internal
     * substep, with the head anchor given by [anchorAt] (seconds → point). This is the compositor's way to
     * *scrub* a cloth strip (it has no closed form): same inputs → same output, exactly. Cheap at a handful
     * of nodes. (For a `pinTail` flag, drive it yourself with [OGClothStrip.step].)
     */
    fun sampleAt(spec: OGClothSpec, timeSec: Float, anchorAt: (Float) -> OGPoint): OGClothStrip {
        val strip = OGClothStrip(spec)
        val h = 1f / 120f
        val a0 = anchorAt(0f)
        strip.reset(a0.x, a0.y)
        var t = 0f
        while (t < timeSec) {
            val a = anchorAt(t + h)
            strip.step(h, a.x, a.y)
            t += h
        }
        return strip
    }

    /**
     * Build a ready-to-send instruction constraining a model to output exactly the cloth-pack JSON
     * [decodeSpec] expects. The developer describes the FABRIC ("a light silk scarf that flutters", "a heavy
     * banner in a stiff breeze") and the model returns the numbers.
     */
    fun clothPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a piece of fabric (a 1-D cloth strip) for the KMPMedia
        library — a scarf, cape edge, flag, banner or hair strand. Every field is optional; omit a field to
        keep its default. All positions are normalized 0..1 of the box.

        Schema (default in parentheses):
        {
          "name":          "<short name>",
          "nodes":         <int >=2 (14): points along the strip; more = smoother, heavier>,
          "length":        <0..1 (0.45): total rest length; it cannot stretch past this>,
          "gravity":       <>=0 (0.9): downward pull; higher drapes harder>,
          "damping":       <>=0 (0.9): how fast it settles; higher is stiffer/calmer, lower sways longer>,
          "wind":          <signed (0): prevailing horizontal wind; + blows right, − blows left; gusts ride on top>,
          "windAmplitude": <>=0 (0.6): peak turbulent gust force on top of wind; 0 = steady wind only>,
          "windFrequency": <>=0 (0.7): how fast the gusts wander>,
          "windSeed":      <int (7): pick a different gust pattern>,
          "pinTail":       <bool (false): also pin the far end — a flag/banner between two points>
        }

        Examples:
        {"name":"silk scarf","nodes":16,"length":0.5,"gravity":0.7,"damping":0.7,"windAmplitude":0.8}
        {"name":"heavy flag","nodes":16,"length":0.5,"gravity":0.4,"wind":1.1,"pinTail":true}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    // ---- 2-D cloth mesh (OGCloth) — same codec family, mesh-suffixed members ----

    /** Decode a JSON cloth-mesh pack into its [OGClothMeshSpec]. Throws on malformed input. */
    fun decodeMeshSpec(text: String): OGClothMeshSpec =
        json.decodeFromString(OGClothMeshSpec.serializer(), OGAiVector.extractJson(text))

    /** [decodeMeshSpec] but returns `null` instead of throwing on malformed input. */
    fun decodeMeshSpecOrNull(text: String): OGClothMeshSpec? = runCatching { decodeMeshSpec(text) }.getOrNull()

    /** Serialize a cloth-mesh pack to JSON (compact; defaults omitted). */
    fun encode(spec: OGClothMeshSpec): String = json.encodeToString(OGClothMeshSpec.serializer(), spec)

    /** Ready-made 2-D sheets, keyed by name. Each is a plain [OGClothMeshSpec] you can `copy()`. */
    val meshPresets: Map<String, OGClothMeshSpec> = linkedMapOf(
        "tarp" to OGClothMeshSpec(name = "tarp", cols = 14, rows = 10, width = 0.55f, height = 0.42f, gravity = 1.1f, damping = 0.85f, wind = 0.2f, windAmplitude = 0.3f, windFrequency = 0.6f, windSeed = 5, friction = 0.6f, pinMode = OGClothPinMode.NONE),
        "curtain" to OGClothMeshSpec(name = "curtain", cols = 14, rows = 11, width = 0.5f, height = 0.55f, gravity = 0.8f, damping = 0.9f, wind = 0.1f, windAmplitude = 0.25f, windFrequency = 0.5f, windSeed = 7, friction = 0.4f, pinMode = OGClothPinMode.TOP_EDGE),
        "banner-wall" to OGClothMeshSpec(name = "banner-wall", cols = 16, rows = 10, width = 0.7f, height = 0.4f, gravity = 0.6f, damping = 0.75f, wind = 0.8f, windAmplitude = 0.6f, windFrequency = 0.8f, windSeed = 13, friction = 0.3f, pinMode = OGClothPinMode.TOP_CORNERS),
        "sail" to OGClothMeshSpec(name = "sail", cols = 12, rows = 12, width = 0.45f, height = 0.5f, gravity = 0.35f, damping = 0.6f, wind = 1.1f, windAmplitude = 0.7f, windFrequency = 0.9f, windSeed = 11, friction = 0.2f, pinMode = OGClothPinMode.LEFT_EDGE),
    )

    /** Look up a [meshPresets] entry by name (case-insensitive), or `null` if there is none. */
    fun meshPreset(name: String): OGClothMeshSpec? = meshPresets[name.lowercase()]

    /**
     * Deterministically **re-simulate a fresh sheet to time [timeSec]** at the fixed internal substep, over the
     * (static) [colliders], with the anchor segment given by [anchorAt] / [anchor2At] (seconds → point). This is
     * the compositor's way to *scrub* a sheet (it has no closed form): same inputs → same output, exactly. Cost
     * is O(timeSec × nodes), so prefer off-main for a long export scrub. If [anchor2At] is omitted the second
     * endpoint defaults to [anchorAt] offset by the spec width (a sensible default top edge).
     */
    fun sampleAt(
        spec: OGClothMeshSpec,
        timeSec: Float,
        colliders: List<OGClothCollider> = emptyList(),
        anchorAt: (Float) -> OGPoint,
        anchor2At: ((Float) -> OGPoint)? = null,
    ): OGCloth {
        val cloth = OGCloth(spec)
        cloth.colliders.addAll(colliders)
        val h = 1f / 120f
        val second: (Float) -> OGPoint = anchor2At ?: { t -> val p = anchorAt(t); OGPoint(p.x + spec.width, p.y) }
        val a0 = anchorAt(0f); val b0 = second(0f)
        cloth.reset(a0.x, a0.y, b0.x, b0.y)
        var t = 0f
        while (t < timeSec) {
            val a = anchorAt(t + h); val b = second(t + h)
            cloth.step(h, a.x, a.y, b.x, b.y)
            t += h
        }
        return cloth
    }

    /**
     * Build a ready-to-send instruction constraining a model to output exactly the cloth-mesh JSON
     * [decodeMeshSpec] expects. The developer describes the SHEET ("a heavy tarp thrown over a bar", "a light
     * sail in a stiff breeze") and the model returns the numbers.
     */
    fun clothMeshPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a 2-D piece of fabric (a planar cloth sheet) for the KMPMedia
        library — a tarp, curtain, banner wall or sail. Every field is optional; omit a field to keep its
        default. All positions are normalized 0..1 of the box.

        Schema (default in parentheses):
        {
          "name":          "<short name>",
          "cols":          <int 2..24 (12): nodes across; more = smoother + heavier>,
          "rows":          <int 2..24 (9): nodes down>,
          "width":         <0..1 (0.5): rest width of the sheet>,
          "height":        <0..1 (0.4): rest height of the sheet>,
          "gravity":       <>=0 (0.9): downward pull; higher drapes harder>,
          "damping":       <>=0 (0.9): how fast it settles; higher is calmer, lower ripples longer>,
          "wind":          <signed (0): prevailing horizontal wind; + blows right, − left; gusts ride on top>,
          "windAmplitude": <>=0 (0.5): peak turbulent gust force on top of wind; 0 = steady wind only>,
          "windFrequency": <>=0 (0.7): how fast the gusts wander>,
          "windSeed":      <int (7): pick a different gust pattern>,
          "friction":      <0..1 (0.3): grip on a pole; 0 slides freely, 1 grips like canvas>,
          "pinMode":       <"NONE"|"TOP_EDGE"|"TOP_CORNERS"|"LEFT_EDGE" (TOP_EDGE): how it is held>
        }

        pinMode: NONE = thrown/draped over poles, TOP_EDGE = curtain on a rod, TOP_CORNERS = banner between two
        poles, LEFT_EDGE = flag on a vertical pole.

        Examples:
        {"name":"tarp","cols":14,"rows":10,"gravity":1.1,"friction":0.6,"pinMode":"NONE"}
        {"name":"sail","cols":12,"rows":12,"gravity":0.35,"wind":1.1,"pinMode":"LEFT_EDGE"}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()
}
