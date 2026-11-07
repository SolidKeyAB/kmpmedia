package com.solidkey.painpoints.stage

import com.solidkey.painpoints.ai.OGAiCapability
import com.solidkey.painpoints.ai.OGAiVector
import com.solidkey.painpoints.compositor.OGEasing
import com.solidkey.painpoints.compositor.OGKeyframedFloat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A **cut-out puppet rig**: a base image plus mover part-images, each carrying a joint [OGCutoutLayer.pivotX]/
 * [OGCutoutLayer.pivotY] (a fraction of the canvas). This is what the *Layer Cutter* tool produces once a
 * drawing is separated into clean transparent part-layers — the data half of "a flat drawing becomes a
 * movable puppet".
 *
 * ### How a limb swings about its real joint with the plain stage transform
 * [OGStageLayer] rotates about the layer **centre** (there is no per-layer transform origin). So the mover
 * frames are authored **pivot-centred** — the part is drawn on a full-canvas frame with its joint at the
 * frame centre. Then [OGOfflineCutoutRigAuthor] rotates the layer about its centre (= the joint) and
 * *afterwards* translates the joint to where it belongs ([OGStageView] applies `rotation` about centre, then
 * `translation`). A rigid post-rotation translate keeps the swing anchored at the joint. No library render
 * change is needed — the whole geometry lives in the frame + the x/y offset here.
 */
@Serializable
public data class OGCutoutRig(
    public val width: Int = 1080,
    public val height: Int = 1080,
    public val durationMs: Long = 2600L,
    public val fps: Int = 60,
    /** Optional base coat behind every layer (SVG colour string); `null` = transparent. */
    public val background: String? = null,
    public val layers: List<OGCutoutLayer> = emptyList(),
)

/** One part of a [OGCutoutRig]: the drawn [resource], its [role], and the joint it turns around. */
@Serializable
public data class OGCutoutLayer(
    public val id: String,
    /** Bundled/registered image name the player resolves (defaults to [id]). */
    public val resource: String = id,
    /** `"base"` (stays joined, still) or `"mover"` (swings about its joint). */
    public val role: String = "mover",
    /** Joint X as a fraction of the canvas width (the mover frame is authored with the joint at centre). */
    public val pivotX: Float = 0.5f,
    /** Joint Y as a fraction of the canvas height. */
    public val pivotY: Float = 0.5f,
    /** Mover swing amplitude in degrees (ignored for a base). */
    public val swingDeg: Float = 9f,
) {
    public val isBase: Boolean get() = role.equals("base", ignoreCase = true)
}

/** JSON codec for [OGCutoutRig], reusing the lenient AI codec (tolerates fences/prose, case-insensitive). */
public object OGCutoutRigs {
    public val json: Json = OGAiVector.json
    public fun encode(rig: OGCutoutRig): String = json.encodeToString(OGCutoutRig.serializer(), rig)
    public fun decode(text: String): OGCutoutRig =
        json.decodeFromString(OGCutoutRig.serializer(), OGAiVector.extractJson(text))
    public fun decodeOrNull(text: String): OGCutoutRig? = runCatching { decode(text) }.getOrNull()
}

/**
 * The **deterministic floor**: a cut-out rig → a looping [OGStage] puppet, with no AI and no network. The
 * base sits still; each mover swings gently about its joint, desynced so the figure reads as alive. This is
 * the honest fallback under [OGCutoutRigCapability] — the plugin always produces *something* movable even
 * when no agent answers. Mirrors `OGOfflineStageAuthor`'s build-from-[OGStages]-builders style.
 */
public class OGOfflineCutoutRigAuthor {
    public fun bake(rig: OGCutoutRig): OGStage {
        val w = rig.width.coerceAtLeast(1)
        val h = rig.height.coerceAtLeast(1)
        val dur = rig.durationMs.coerceAtLeast(400L)
        var moverIndex = 0
        val layers = rig.layers.map { l ->
            if (l.isBase) {
                OGStages.imageStill(id = l.id, resource = l.resource, widthFrac = 1f, heightFrac = 1f)
            } else {
                // Flip the lead direction and nudge amplitude per mover so limbs don't move in lockstep.
                val sign = if (moverIndex % 2 == 0) 1f else -1f
                val amp = l.swingDeg * (0.8f + 0.2f * (moverIndex % 3))
                moverIndex++
                OGStages.imageStill(
                    id = l.id, resource = l.resource, widthFrac = 1f, heightFrac = 1f,
                    x = OGKeyframedFloat.const(l.pivotX - 0.5f),   // move the centred joint to its spot…
                    y = OGKeyframedFloat.const(l.pivotY - 0.5f),
                    rotationDeg = loopSwing(dur, amp * sign),       // …then swing about it (rotation-first)
                    onTap = OGStages.tap(OGTapAction.BOUNCE),       // tappable: poke a limb to react
                )
            }
        }
        return OGStage.of(
            *layers.toTypedArray(),
            width = w, height = h, durationMs = dur, fps = rig.fps, background = rig.background,
        )
    }

    /** rest → +amp → rest → −amp → rest across one loop, eased so it reverses smoothly at the extremes. */
    private fun loopSwing(dur: Long, amp: Float): OGKeyframedFloat {
        val q = dur / 4
        return OGStages.eased(
            OGEasing.EASE_IN_OUT,
            0L to 0f, q to amp, 2 * q to 0f, 3 * q to -amp, dur to 0f,
        )
    }
}

/**
 * The **"cut-out puppet" capability** — a first-class [OGAiCapability] for the manifest, next to
 * `OGStageAuthoring.illustrate`. The app sends an [OGCutoutRig]; a subscribed agent (over your nexum
 * channel) returns a moving [OGStage], or [OGOfflineCutoutRigAuthor] does it on-device. Compose it into a
 * manifest with `OGAiManifest.of(app, listOf(OGStageAuthoring.illustrate, OGCutoutRigCapability))`.
 */
public val OGCutoutRigCapability: OGAiCapability<OGCutoutRig, OGStage> =
    object : OGAiCapability<OGCutoutRig, OGStage> {
        override val id: String = "cutoutRig"
        override val title: String = "Cut-out puppet"
        override val description: String =
            "Turn a cut-out rig (a base image plus mover part-images, each with a joint pivot given as a " +
                "fraction of the canvas) into a looping OGStage puppet whose parts swing about their joints. " +
                "Each mover frame is already pivot-centred, so animate its rotationDeg about the layer centre " +
                "and offset its x/y by (pivot − 0.5) to seat the joint."
        override val outputFormat: String = "one OGStage JSON object"
        override fun promptFor(input: OGCutoutRig): String =
            OGStages.stagePrompt(
                "Author a lively looping puppet from this cut-out rig. The base layer stays still and " +
                    "joined; every mover swings gently about its joint, desynced. Rig:\n" +
                    OGCutoutRigs.encode(input),
            )
        override fun decode(reply: String): OGStage? = OGStages.decodeOrNull(reply)
    }

/** Agent-first helper: use the agent's [agentReply] OGStage if it parses, else the deterministic floor. */
public fun bakeCutoutRig(
    rig: OGCutoutRig,
    agentReply: String? = null,
    floor: OGOfflineCutoutRigAuthor = OGOfflineCutoutRigAuthor(),
): OGStage = agentReply?.let { OGStages.decodeOrNull(it) } ?: floor.bake(rig)
