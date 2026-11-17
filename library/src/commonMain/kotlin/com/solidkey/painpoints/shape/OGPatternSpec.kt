package com.solidkey.painpoints.shape

import kotlinx.serialization.Serializable

/** The kind of seeded procedural pattern rendered by [OGPatternView] / `drawOGPattern`. */
enum class OGPatternType {
    /** Fractal ice crystal — unique dendrite per seed. */
    SNOWFLAKE,
    /** Radial spoke web with concentric rings — unique spoke spacing per seed. */
    SPIDER_WEB,
    /** Regular hexagonal honeycomb grid with minor per-cell size variation. */
    HONEYCOMB,
    /** Branching leaf — central midrib + alternating side veins, unique per seed. */
    LEAF_VEINS,
}

/**
 * Data-defined spec for a seeded procedural pattern rendered by [OGPatternView] / `drawOGPattern`.
 * Every [seed] produces a visually distinct instance of the same [type] — same seed → same geometry,
 * new seed → new geometry. All types are pure Canvas 2D maths, no shader, no external asset.
 *
 * @param type           Pattern family.
 * @param seed           Deterministic seed — change to get a unique instance.
 * @param color          Stroke/line colour (`#RRGGBB` or `#AARRGGBB`).
 * @param strokeFraction Stroke width as a fraction of the render diameter (0.012 = thin; 0.04 = bold).
 * @param complexity     Detail level. Per type:
 *   SNOWFLAKE = fractal recursion depth (0–4; default 2);
 *   SPIDER_WEB = ring count (2–12; default 5);
 *   HONEYCOMB = ignored;
 *   LEAF_VEINS = sub-vein depth (0–3; default 2).
 * @param count          Primary structural count. Per type:
 *   SNOWFLAKE = arm count (3–12; default 6);
 *   SPIDER_WEB = spoke count (4–16; default 8);
 *   HONEYCOMB = approximate cells across width (2–20; default 5);
 *   LEAF_VEINS = side-vein pairs per side (1–8; default 4).
 */
@Serializable
data class OGPatternSpec(
    val type: OGPatternType = OGPatternType.SNOWFLAKE,
    val seed: Int = 0,
    val color: String = "#FFFFFFFF",
    val strokeFraction: Float = 0.018f,
    val complexity: Int = 2,
    val count: Int = 6,
)
