package com.solidkey.painpoints.skin

import com.solidkey.painpoints.ai.OGAiVector

/**
 * The **codec + presets** for [OGSkinSpec] — decode a JSON pack, encode one back, build a model prompt,
 * and reach for a ready-made material ([REPTILE], [SNAKE], [HIDE], [DAPPLE_GREY], [DENIM], [LINEN]).
 * Mirrors the rest of the data layers (OGFog / OGControls / OGLooks): lenient JSON, tolerant extraction,
 * no network, no AI SDK.
 */
object OGSkins {
    val json get() = OGAiVector.json

    fun decodeSpec(text: String): OGSkinSpec =
        json.decodeFromString(OGSkinSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeSpecOrNull(text: String): OGSkinSpec? = runCatching { decodeSpec(text) }.getOrNull()

    fun encode(spec: OGSkinSpec): String = json.encodeToString(OGSkinSpec.serializer(), spec)

    /** Decode a raised-edge ([OGSkinEdgeSpec]) pack from (possibly prose-wrapped) JSON. */
    fun decodeEdge(text: String): OGSkinEdgeSpec =
        json.decodeFromString(OGSkinEdgeSpec.serializer(), OGAiVector.extractJson(text))

    fun decodeEdgeOrNull(text: String): OGSkinEdgeSpec? = runCatching { decodeEdge(text) }.getOrNull()

    fun encodeEdge(spec: OGSkinEdgeSpec): String = json.encodeToString(OGSkinEdgeSpec.serializer(), spec)

    /** Look up a built-in skin preset by name (case-insensitive), or `null`. */
    fun preset(name: String): OGSkinSpec? = presets[name.lowercase()]

    /** Look up a built-in raised-edge preset by name (case-insensitive), or `null`. */
    fun edgePreset(name: String): OGSkinEdgeSpec? = edgePresets[name.lowercase()]

    fun skinPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a procedural, repeating SKIN / material for the KMPMedia
        library. It tiles seamlessly across a shape (a creature's hide, a surface).

        kind: "reptile" (scales) | "hide" (animal/horse coat, fine hairs, optional dapple) | "fabric"
        (woven cloth: warp+weft threads). Colours are "#RRGGBB" or a css name. Sizes are fractions.

        Fields (all optional): kind, base (main colour), pattern (scale rim / coat shade / recessed
        thread), highlight (sheen; null = base lightened), accent (hide dapple/spots; null = none),
        cell (one scale/weave-cell/mottle size, fraction of the shorter side, 0.02..0.5),
        roughness (0..1 organic variation), contrast (0..1 pattern strength), relief (0..1
        scale-overlap / thread-rounding / hair-length),
        density (0.05..1 coverage; 1 = packed material, lower SCATTERS the pattern sparsely so the base
        shows through — a speckled / loose-weave field), sizeJitter (0..1 random element size within a
        limited range), colorJitter (0..1 random element colour within a limited in-palette range),
        seed (int).

        Schema: {"kind":"reptile","base":"#4E9A4E","pattern":"#2F6B30","highlight":"#86C77F","cell":0.14}
        Example: {"kind":"hide","base":"#7A4A2B","pattern":"#5A3420","highlight":"#9E6A44","accent":"#E8D9C4","cell":0.2,"contrast":0.5}
        Sparse example: {"kind":"reptile","base":"#2E2A3A","pattern":"#6E5AA8","highlight":"#C9B8F0","cell":0.1,"density":0.35,"sizeJitter":0.6,"colorJitter":0.5}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()

    /** Classic green reptile scales — lizard / dragon hide. */
    val REPTILE = OGSkinSpec(
        kind = "reptile", base = "#4E9A4E", pattern = "#285C2A", highlight = "#8FD08A",
        cell = 0.14f, roughness = 0.55f, contrast = 0.65f, relief = 0.6f, seed = 7,
    )

    /** Tighter, cooler snake scales with a diamond-ish sheen. */
    val SNAKE = OGSkinSpec(
        kind = "reptile", base = "#6E7F5A", pattern = "#2E3A22", highlight = "#C7CFA6",
        cell = 0.1f, roughness = 0.7f, contrast = 0.75f, relief = 0.75f, seed = 19,
    )

    /** A bay horse coat — warm brown, fine hairs, soft mottle. */
    val HIDE = OGSkinSpec(
        kind = "hide", base = "#7A4A2B", pattern = "#52301B", highlight = "#A2714A",
        cell = 0.22f, roughness = 0.6f, contrast = 0.5f, relief = 0.6f, seed = 11,
    )

    /** A dapple-grey coat — cool grey with pale dapple spots. */
    val DAPPLE_GREY = OGSkinSpec(
        kind = "hide", base = "#8C8A91", pattern = "#5E5C64", highlight = "#C6C4CB", accent = "#EDECF0",
        cell = 0.24f, roughness = 0.55f, contrast = 0.5f, relief = 0.55f, seed = 23,
    )

    /** Indigo denim — dark warp, pale weft, a tight weave. */
    val DENIM = OGSkinSpec(
        kind = "fabric", base = "#2E4A78", pattern = "#1B2E4C", highlight = "#D7DEEA",
        cell = 0.08f, roughness = 0.4f, contrast = 0.7f, relief = 0.5f, seed = 5,
    )

    /** Natural linen — warm oatmeal threads, a looser, softer weave. */
    val LINEN = OGSkinSpec(
        kind = "fabric", base = "#CDBFA3", pattern = "#A4916F", highlight = "#EDE3D0",
        cell = 0.11f, roughness = 0.6f, contrast = 0.45f, relief = 0.7f, seed = 29,
    )

    /** Sparse amethyst scales scattered over a dark field — scales of random size & shade, base showing
     *  through. Shows off [OGSkinSpec.density] + [OGSkinSpec.sizeJitter] + [OGSkinSpec.colorJitter]. */
    val SPECKLE = OGSkinSpec(
        kind = "reptile", base = "#2E2A3A", pattern = "#6E5AA8", highlight = "#C9B8F0",
        cell = 0.1f, roughness = 0.5f, contrast = 0.7f, relief = 0.6f,
        density = 0.35f, sizeJitter = 0.6f, colorJitter = 0.5f, seed = 17,
    )

    /** An open, loose gauze — a sparse weave with gaps and uneven threads, over a warm backing. */
    val GAUZE = OGSkinSpec(
        kind = "fabric", base = "#E7DFCE", pattern = "#B8A984", highlight = "#FBF6EA",
        cell = 0.14f, roughness = 0.5f, contrast = 0.4f, relief = 0.6f,
        density = 0.5f, sizeJitter = 0.5f, colorJitter = 0.35f, seed = 37,
    )

    /** All built-in skin presets, keyed by lowercase name. */
    val presets: Map<String, OGSkinSpec> = mapOf(
        "reptile" to REPTILE,
        "snake" to SNAKE,
        "hide" to HIDE,
        "dapple_grey" to DAPPLE_GREY,
        "denim" to DENIM,
        "linen" to LINEN,
        "speckle" to SPECKLE,
        "gauze" to GAUZE,
    )

    // ── Raised-edge ([OGSkinEdgeSpec]) presets ─────────────────────────────────────────────────────────

    /** A continuous beaded rim — scales bulging shoulder-to-shoulder around the whole silhouette. */
    val BEADED = OGSkinEdgeSpec(protrude = 0.55f, coverage = 1f, bumpScale = 1f, lightAngleDeg = -120f)

    /** A tall, pronounced crest — bigger, prouder bumps for a dragon-ridge / spiny look. */
    val CREST = OGSkinEdgeSpec(protrude = 1.1f, coverage = 1f, bumpScale = 1.4f, lightAngleDeg = -120f)

    /** Sparse round studs dotted along the edge, base showing between them. */
    val STUDS = OGSkinEdgeSpec(protrude = 0.7f, coverage = 0.5f, bumpScale = 1.2f, lightAngleDeg = -120f)

    /** All built-in raised-edge presets, keyed by lowercase name. */
    val edgePresets: Map<String, OGSkinEdgeSpec> = mapOf(
        "beaded" to BEADED,
        "crest" to CREST,
        "studs" to STUDS,
    )
}
