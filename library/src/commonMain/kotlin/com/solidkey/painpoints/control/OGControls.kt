package com.solidkey.painpoints.control

import com.solidkey.painpoints.ai.OGAiVector
import com.solidkey.painpoints.style.OGStyleSpec

/**
 * The **codec** for [OGControlSpec] — decode a JSON `.control` pack (designer- or model-authored) into
 * a live [OGControlSpec], encode one back, and build a prompt that constrains a model to emit a valid
 * control skin. Mirrors [com.solidkey.painpoints.look.OGLooks] / [com.solidkey.painpoints.style.OGStyles]:
 * tolerant of code fences and stray prose (reuses [OGAiVector.extractJson]) and shares the same lenient
 * [OGAiVector.json] config so every data layer behaves identically. No network, no AI SDK.
 *
 * A few named [presets] (plain / sketch / slime / snow) and reusable [garnishes] are ready-made.
 */
object OGControls {
    /** The shared lenient JSON config (reused from `OGAiVector` so all data layers agree). */
    val json get() = OGAiVector.json

    /** Decode a JSON control pack into its [OGControlSpec]. Throws on malformed input. */
    fun decodeSpec(text: String): OGControlSpec =
        json.decodeFromString(OGControlSpec.serializer(), OGAiVector.extractJson(text))

    /** [decodeSpec] but returns `null` instead of throwing on malformed input. */
    fun decodeSpecOrNull(text: String): OGControlSpec? = runCatching { decodeSpec(text) }.getOrNull()

    /** Serialize a control pack to JSON (compact; defaults omitted). */
    fun encode(spec: OGControlSpec): String = json.encodeToString(OGControlSpec.serializer(), spec)

    /** Reusable seasonal garnishes, keyed by name. Declared before [presets] so they can reference these. */
    val garnishes: Map<String, OGGarnishSpec> = linkedMapOf(
        // Halloween goo oozing off the top edge (the defaults).
        "slime" to OGGarnishSpec(),
        // A soft snow cap clinging to the top, gentle and slow.
        "snow" to OGGarnishSpec(
            edge = "top",
            color = "#FFFFFF",
            deepColor = "#DCE7F2",
            shineColor = "#FFFFFF",
            coverage = 0.38f,
            drips = 3,
            waves = 2,
            amplitude = 0.03f,
            speed = 0.18f,
        ),
    )

    /** Look up a [garnishes] entry by name, or `null` if there is none. */
    fun garnish(name: String): OGGarnishSpec? = garnishes[name.lowercase()]

    /** Ready-made control skins, keyed by name. Each is a plain [OGControlSpec] you can `copy()`. */
    val presets: Map<String, OGControlSpec> = linkedMapOf(
        // Clean button: crisp letters, no sketch edge.
        "plain" to OGControlSpec(outline = null, labelStyle = OGStyleSpec()),
        // The default hand-drawn look (sketched ink edge + a subtle roughened label).
        "sketch" to OGControlSpec(),
        // Hand-drawn + Halloween slime.
        "slime" to OGControlSpec(garnish = garnishes.getValue("slime")),
        // Hand-drawn + winter snow.
        "snow" to OGControlSpec(garnish = garnishes.getValue("snow")),
    )

    /** Look up a [presets] entry by name, or `null` if there is none. */
    fun preset(name: String): OGControlSpec? = presets[name.lowercase()]

    /**
     * Build a ready-to-send instruction constraining a model to output exactly the control-pack JSON
     * [decodeSpec] expects. Wire the returned string into your model; pass the reply to [decodeSpec].
     *
     * @param instruction the natural-language ask, e.g. "a tall slimy Halloween action button".
     */
    fun controlPrompt(instruction: String): String = """
        You output ONLY a JSON object describing a button "control" skin for the KMPMedia library.
        Every field is optional; omit a field to accept its default. Live colours (the fill and text)
        and the click are set in code, NOT here — this is purely the look.

        Schema (defaults in parentheses):
        {
          "cornerRadius": <dp (11)>,
          "height":       <dp (46)>,
          "fontSize":     <sp (13)>,
          "pressEffect":  "scale" | "dim" | "brutalist" | "none"  ("scale"),
          "disabledAlpha":<0..1 (0.4): fill opacity when disabled>,
          "outline": {                      // the hand-drawn ink edge; null for a clean button
            "width": <dp (2.2)>, "inset": <dp (3.5)>,
            "amplitude": <0..0.1 (0.03): sketch wobble, fraction of height>,
            "detail": <int (7)>, "inkAlpha": <0..1 (0.45)>
          },
          "labelStyle": { "ops": [ {"op":"roughen","amplitude":0.02,"detail":3}, ... ] },  // optional
          "fill": {                          // optional gradient; omit for a flat colour
            "type":"linear"|"radial", "stops":[[0,"#RRGGBB"],[1,"#RRGGBB"]]
          },
          "garnish": {                       // optional seasonal goo on one edge
            "edge":"top"|"bottom", "color":"#RRGGBB", "deepColor":"#RRGGBB", "shineColor":"#RRGGBB",
            "coverage":<0..1 (0.5)>, "drips":<int (2)>, "waves":<int (3)>,
            "amplitude":<0..0.2 (0.055)>, "speed":<cps (0.5)>
          }
        }

        Examples:
        {"cornerRadius":14,"pressEffect":"brutalist"}
        {"garnish":{"edge":"top","color":"#7CB342","deepColor":"#558B2F","drips":2}}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()
}
