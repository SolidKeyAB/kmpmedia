package com.solidkey.painpoints.skin

import kotlinx.serialization.Serializable

/** The family of procedural skin a spec renders; the advisory [OGSkinSpec.kind] string maps here. */
enum class OGSkinKind { REPTILE, HIDE, FABRIC }

/**
 * A data-defined **procedural skin** — a seamless, repeating material (reptile scales, an animal hide /
 * horse coat, or a woven fabric) that tiles across any fill. It renders to ONE small seamless tile
 * ([ogSkinTile]) that Compose repeats as an `ImageShader` brush, so a whole surface costs one cached
 * bitmap + a GPU tile — no per-pixel work, no shader source, identical on Android & iOS. Fill a shape
 * with [Modifier.ogSkin], or grab the [androidx.compose.ui.graphics.Brush] via [rememberOGSkinBrush].
 *
 * Every field is a plain colour/number so a model (or [OGSkins] preset) can author a creature's hide.
 * Colours are `#RRGGBB` / `#AARRGGBB` / css name; sizes are fractions of the fill's shorter side.
 */
@Serializable
data class OGSkinSpec(
    /** Which material to grow — `reptile` | `hide` (horse/fur/coat) | `fabric` (cloth/weave/denim/linen).
     *  Matched case-insensitively with a few synonyms; anything else falls back to reptile. */
    val kind: String = "reptile",
    /** The main surface colour the pattern sits on (scale body / coat / thread). */
    val base: String = "#4E9A4E",
    /** The pattern colour — scale rims & shadow, coat shade, the weave's recessed thread. */
    val pattern: String = "#2F6B30",
    /** Lighter accent — scale sheen / coat highlight / thread sheen. `null` = base lightened. */
    val highlight: String? = "#86C77F",
    /** A second accent, used for hide dapples / spots. `null` = no spots. */
    val accent: String? = null,
    /** One pattern cell (a scale, a coat-mottle, a weave crossing) as a fraction of the fill's shorter
     *  side; `0.02..0.5`. Smaller = finer, denser pattern. */
    val cell: Float = 0.14f,
    /** Organic variation `0..1`: per-cell colour jitter + edge wobble from `OGFxNoise`. `0` = rigid. */
    val roughness: Float = 0.5f,
    /** Pattern-vs-base contrast `0..1` — how strongly the pattern stands out from [base]. */
    val contrast: Float = 0.65f,
    /** Relief feel `0..1`: scale overlap (reptile), thread rounding/sheen (fabric), hair length (hide). */
    val relief: Float = 0.6f,
    /** Coverage `0.05..1` — how densely the pattern fills the area. `1` (default) = fully packed (the
     *  classic skin). Lower **scatters** the pattern so the [base] shows through between elements — a
     *  sparse field of scales/mottle/threads. Think of it as the inverse of a "sparse level". The tile
     *  grows as coverage drops so the scatter reads as random, not a tight repeat. */
    val density: Float = 1f,
    /** Per-element **size** randomness `0..1`: each drawn element's size is jittered within a limited
     *  range around [cell] (±~60 % at `1`). `0` = every element the same size. */
    val sizeJitter: Float = 0f,
    /** Per-element **colour** randomness `0..1`: each element's colour is nudged within a limited range,
     *  between the material's own [highlight] and [pattern] tones (so it stays in-palette). `0` = every
     *  element the same colour. Independent of [roughness] (which varies edges + contrast). */
    val colorJitter: Float = 0f,
    /** Seed — same seed ⇒ the same skin on every platform. */
    val seed: Int = 7,
) {
    /** The resolved material family. */
    val skinKind: OGSkinKind
        get() = when (kind.trim().lowercase()) {
            "hide", "horse", "fur", "coat", "animal" -> OGSkinKind.HIDE
            "fabric", "cloth", "weave", "woven", "denim", "linen", "canvas" -> OGSkinKind.FABRIC
            else -> OGSkinKind.REPTILE
        }

    /** Clamp the knobs into their valid ranges. */
    fun sanitized(): OGSkinSpec = copy(
        cell = cell.coerceIn(0.02f, 0.5f),
        roughness = roughness.coerceIn(0f, 1f),
        contrast = contrast.coerceIn(0f, 1f),
        relief = relief.coerceIn(0f, 1f),
        density = density.coerceIn(0.05f, 1f),
        sizeJitter = sizeJitter.coerceIn(0f, 1f),
        colorJitter = colorJitter.coerceIn(0f, 1f),
    )
}
