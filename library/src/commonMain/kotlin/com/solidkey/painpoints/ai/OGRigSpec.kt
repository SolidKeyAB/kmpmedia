package com.solidkey.painpoints.ai

import kotlinx.serialization.Serializable

// ============================================================================================
// OGRigSpec — the SERIALIZABLE "scaffold" half of the clickable-joints rig. [OGRig] (OGSceneRig.kt)
// already owns the skinning MATHS (bind a mesh once, deform per frame); what it lacked was a way to
// AUTHOR, SAVE, or model-DESCRIBE a rig's control points — every other pack in the lib has a JSON
// codec, the rig did not. This is that codec: a tiny list of NAMED joints plus the bind/feel knobs,
// nothing more. No new skinning math, no per-frame cost — a spec resolves to a plain `List<OGJoint>`
// ONCE and then the shipped [OGRig] does the work.
//
// Named joints are the load-bearing choice: an `id` is what a code-driven pose addresses TODAY (poke
// "jaw", not index 4) and what a future `parent = "shoulder"` hierarchy would reference TOMORROW.
// Because the whole lib follows the tolerant-wire rule (unknown keys ignored, every field defaulted),
// a later hierarchy upgrade — adding `parent: String? = null` + `angleDeg: Float = 0f` to a joint — is
// purely ADDITIVE and cannot break a rig authored today. That is why deferring the full skeleton costs
// nothing: this format is already the stepping stone to it.
// ============================================================================================

/**
 * One named control point of an [OGRigSpec] — the serializable, authorable twin of [OGJoint]. Its [id]
 * is a stable handle (so code poses it by name and a future hierarchy can reference it as a parent); its
 * rest position is either the explicit ([x], [y]) in normalized `0..1` scene space, OR — when [region]
 * names a labelled scene part — that part's centroid, so a rig can pin "the jaw" without hard-coding a
 * coordinate that drifts when the drawing changes.
 *
 * Every field but [id] is defaulted so a partial / model-authored joint still decodes (the lib's
 * forward-compatible wire rule); an unknown future field (e.g. a hierarchy `parent`) is ignored today.
 */
@Serializable
public data class OGRigJointSpec(
    /** Stable name — the handle a code-driven pose and a future `parent` reference address. */
    public val id: String,
    /** Explicit rest X (normalized `0..1`), used when [region] is null or unmatched. */
    public val x: Float = 0.5f,
    /** Explicit rest Y (normalized `0..1`), used when [region] is null or unmatched. */
    public val y: Float = 0.5f,
    /** Optional: snap this joint's rest to the centroid of the scene part with this label
     *  (case-insensitive). Falls back to ([x], [y]) when null or no such part exists. */
    public val region: String? = null,
)

/**
 * A SERIALIZABLE rig scaffold: a list of named [joints] plus the knobs [OGRig.bind] and the poke view
 * ([com.solidkey.painpoints.stage.OGSceneRigView]) need to make a mesh pokeable/poseable. It carries no
 * mesh and no maths of its own — [restJoints] resolves it to plain [OGJoint]s and [bind] hands those to
 * the shipped skinning engine.
 *
 * Author one by hand, decode one from JSON ([OGRigs.decode], tolerant of fences/prose), or capture the
 * auto-derived pins of an existing scene with [fromJoints] + [OGRig.sceneJoints] and persist them.
 */
@Serializable
public data class OGRigSpec(
    /** The named control points, in a stable order (code may pose by [indexOf] or by list index). */
    public val joints: List<OGRigJointSpec> = emptyList(),
    /** How close (normalized) a touch must land to grab a pin — passed to the poke view. */
    public val grabRadius: Float = 0.12f,
    /** Influences per vertex for the skinning bind (higher = smoother, softer falloff). */
    public val nearest: Int = 4,
    /** Distance falloff for the bind weights (higher = tighter, more local bulge). */
    public val falloff: Float = 2f,
    /** Settle-home spring stiffness for the poke view (a released pin springs back with this). */
    public val stiffness: Float = 220f,
    /** Settle-home spring damping ratio (`< 1` overshoots on release). */
    public val dampingRatio: Float = 0.55f,
    /** Advisory version; decoding never branches on it. */
    public val version: Int = 1,
) {
    /**
     * Resolve this spec to the rest [OGJoint]s the engine poses (offsets all `(0,0)` = home), in [joints]
     * order. A joint with a [OGRigJointSpec.region] that matches a labelled part of [scene] snaps to that
     * part's centroid; otherwise it uses its explicit ([OGRigJointSpec.x], [OGRigJointSpec.y]). Pass the
     * same [scene] you intend to [bind] / draw so region-snapped pins land on the right parts.
     */
    public fun restJoints(scene: OGSceneSpec? = null): List<OGJoint> = joints.map { j ->
        val snapped = if (j.region != null && scene != null) regionCentroid(scene, j.region) else null
        OGJoint(snapped ?: OGPointSpec(j.x, j.y))
    }

    /**
     * Bind an [OGRig] over [scene]'s vertices using this spec's resolved [restJoints] and its [nearest] /
     * [falloff]. The heavy step (done once); pose the result with [OGRig.deform] / [OGRig.deformInto].
     */
    public fun bind(scene: OGSceneSpec): OGRig =
        OGRig.bind(scene, restJoints(scene), nearest = nearest, falloff = falloff)

    /** The index of the joint named [id] in [joints], or `-1` — for addressing a code-driven pose by name. */
    public fun indexOf(id: String): Int = joints.indexOfFirst { it.id == id }

    public companion object {
        /**
         * Capture a plain `List<OGJoint>` (e.g. from [OGRig.sceneJoints] or a hand-tuned pose's rest) as a
         * serializable spec, so an auto-derived or interactively-placed rig can be saved and replayed. Ids
         * are "[idPrefix]0", "[idPrefix]1", … unless [ids] supplies names; the bind/feel knobs default to
         * [OGRigSpec]'s own defaults (override to persist a tuned feel).
         */
        public fun fromJoints(
            joints: List<OGJoint>,
            idPrefix: String = "j",
            ids: List<String>? = null,
            grabRadius: Float = 0.12f,
            nearest: Int = 4,
            falloff: Float = 2f,
            stiffness: Float = 220f,
            dampingRatio: Float = 0.55f,
        ): OGRigSpec = OGRigSpec(
            joints = joints.mapIndexed { i, j ->
                OGRigJointSpec(id = ids?.getOrNull(i) ?: "$idPrefix$i", x = j.rest.x, y = j.rest.y)
            },
            grabRadius = grabRadius, nearest = nearest, falloff = falloff,
            stiffness = stiffness, dampingRatio = dampingRatio,
        )

        /** Mean of the finite outline points of the first [scene] part whose label equals [label]
         *  (case-insensitive), or null if there is no such part (→ the joint keeps its explicit x/y). */
        private fun regionCentroid(scene: OGSceneSpec, label: String): OGPointSpec? {
            val want = label.trim().lowercase()
            val shape = scene.shapes.firstOrNull { (it.label ?: "").trim().lowercase() == want } ?: return null
            var sx = 0f; var sy = 0f; var n = 0
            for (p in shape.points) {
                if (!p.x.isFinite() || !p.y.isFinite()) continue
                sx += p.x; sy += p.y; n++
            }
            return if (n == 0) null else OGPointSpec(sx / n, sy / n)
        }
    }
}

/**
 * Codec + glue for [OGRigSpec], mirroring every other pack's wire helpers ([OGRigRecipes],
 * `OGStages`, …): encode to compact JSON, decode tolerantly (surrounding prose / code fences are
 * stripped), or decode-or-null. Reuses [OGAiVector.json], so the exact same lenient, unknown-key-tolerant
 * configuration as the rest of the describe→movie pipeline.
 */
public object OGRigs {
    /** Serialize a rig scaffold to compact JSON (defaults omitted) — to persist or seed a prompt. */
    public fun encode(spec: OGRigSpec): String =
        OGAiVector.json.encodeToString(OGRigSpec.serializer(), spec)

    /** Parse a rig scaffold from JSON; tolerates surrounding prose / fences. Throws on garbage. */
    public fun decode(text: String): OGRigSpec =
        OGAiVector.json.decodeFromString(OGRigSpec.serializer(), OGAiVector.extractJson(text))

    /** [decode] but returns null instead of throwing. */
    public fun decodeOrNull(text: String): OGRigSpec? = runCatching { decode(text) }.getOrNull()
}
