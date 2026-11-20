package com.solidkey.painpoints.fx

/**
 * **Digital rain** — the iconic waterfall of falling glyphs made famous by The Matrix. Each column
 * of characters scrolls downward at an independent speed; a bright head glyph leads a fading tail.
 * Characters flicker via deterministic per-cell hashing — no RNG, frame-identical on every platform.
 *
 * Decode + presets on [OGDigitalRain]; render with [OGDigitalRainView].
 *
 * **Coordinates.** The view is divided into [columnCount] equal columns; cell height equals cell width
 * (square monospace cells). [speed] is in rows/second at the nominal column width; [speedJitter]
 * spreads per-column rates around that value. [tailLength] is in character rows.
 */
@kotlinx.serialization.Serializable
data class OGDigitalRainSpec(
    val name: String? = null,
    /** Number of character columns across the view. Clamped `1..256`. */
    val columnCount: Int = 20,
    /**
     * Characters to cycle through. Half-width katakana + digits by default (the classic Matrix look).
     * Any non-empty string works — Latin letters, symbols, emoji (single-codepoint only).
     */
    val glyphSet: String = "ｦｧｨｩｪｫｬｭｮｯｰｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789",
    /** Scroll speed in character rows per second (nominal). */
    val speed: Float = 8f,
    /** Length of the fading tail, in character rows. Clamped `1..128`. */
    val tailLength: Int = 16,
    /** Rate at which individual glyphs flicker and change, in changes per second per cell. */
    val glyphChangeRate: Float = 12f,
    /** Colour of the leading head glyph — any SVG colour string. */
    val headColor: String = "#FFFFFF",
    /** Colour of the trail body — any SVG colour string. */
    val trailColor: String = "#00FF41",
    /** Black background fill alpha `0..1`; `0` = transparent overlay, `1` = opaque black fill. */
    val bgAlpha: Float = 0f,
    /** Overall opacity `0..1`. */
    val alpha: Float = 1f,
    /** Per-column speed spread `0..1`; `0` = all columns identical speed. */
    val speedJitter: Float = 0.4f,
    /** Seed for deterministic per-column phase and speed offsets. */
    val seed: Int = 0,
)
