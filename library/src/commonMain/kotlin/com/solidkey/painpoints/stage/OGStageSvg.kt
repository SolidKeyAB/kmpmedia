package com.solidkey.painpoints.stage

import com.solidkey.painpoints.ai.OGSvgScene
import com.solidkey.painpoints.image.svg.parseViewBox
import kotlin.math.roundToInt

/**
 * SVG as a **describe→movie front-end**. A language model is far more fluent in SVG than in our own
 * stage JSON, so letting the agent reply with a single `<svg>` document is often the most reliable way
 * to get a compact, recognizable drawing. This compiles that SVG into a one-layer [OGStage] whose
 * scene is the model-authored [com.solidkey.painpoints.ai.OGSvgScene] result — the JSON stays the
 * runtime IR, SVG is just another source language into it.
 *
 * Wired into [OGStageAuthoring.fromReply], so every author (offline, bring-your-own-key, or an agent
 * over nexum) transparently accepts an SVG reply with no extra code: if the reply isn't a stage JSON,
 * we try to lift an `<svg>…</svg>` out of it and play that instead.
 */
public object OGStageSvg {

    public const val DEFAULT_DURATION_MS: Long = 4000L
    public const val DEFAULT_FPS: Int = 30

    /** Compile a full SVG document into a single-layer [OGStage], or `null` if it holds no fillable
     *  shapes. The canvas is 1080 wide with the SVG's own aspect ratio (from its `viewBox`). */
    public fun fromSvg(
        svg: String,
        durationMs: Long = DEFAULT_DURATION_MS,
        fps: Int = DEFAULT_FPS,
        background: String? = null,
    ): OGStage? {
        val scene = OGSvgScene.toScene(svg)
        if (scene.shapes.isEmpty()) return null
        val vb = runCatching { parseViewBox(svg) }.getOrNull()
        val w = 1080
        val h = if (vb != null && vb.width > 0f && vb.height > 0f) {
            (1080f * vb.height / vb.width).roundToInt().coerceIn(240, 4096)
        } else {
            1080
        }
        return OGStage(
            width = w,
            height = h,
            durationMs = durationMs.coerceAtLeast(1L),
            fps = fps.coerceIn(1, 120),
            background = background,
            layers = listOf(OGStageLayer(id = "svg-scene", content = OGStageContent.Scene(scene))),
        )
    }

    /** Lift a single `<svg>…</svg>` out of a model reply (prose / markdown fences tolerated) and compile
     *  it, or `null` if there's no SVG in the text. */
    public fun fromReply(
        reply: String,
        durationMs: Long = DEFAULT_DURATION_MS,
        fps: Int = DEFAULT_FPS,
    ): OGStage? = extractSvg(reply)?.let { fromSvg(it, durationMs, fps) }

    private fun extractSvg(text: String): String? {
        val start = text.indexOf("<svg", ignoreCase = true)
        if (start < 0) return null
        val end = text.indexOf("</svg>", startIndex = start, ignoreCase = true)
        if (end < 0) return null
        return text.substring(start, end + "</svg>".length)
    }
}
