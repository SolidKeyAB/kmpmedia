package com.solidkey.painpoints.stage

import com.solidkey.painpoints.ai.OGSceneIdle
import com.solidkey.painpoints.ai.OGSvgScene
import com.solidkey.painpoints.ai.isLive
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

    /** A longer loop when idle motion is on, so the breathing/blink/sway play continuously (and the
     *  stage's own loop boundary is rare) rather than freezing after a short still-length clip. */
    public const val IDLE_LOOP_MS: Long = 12000L

    /** Compile a full SVG document into a single-layer [OGStage], or `null` if it holds no fillable
     *  shapes. The canvas is 1080 wide with the SVG's own aspect ratio (from its `viewBox`). When
     *  [idle] is true (default), gentle idle motion (breathing/blink/tail-sway) is attached to the
     *  recognised labelled regions via [OGSceneIdle] so the figure is alive, not a still. */
    public fun fromSvg(
        svg: String,
        durationMs: Long = DEFAULT_DURATION_MS,
        fps: Int = DEFAULT_FPS,
        background: String? = null,
        idle: Boolean = true,
    ): OGStage? {
        val compiled = OGSvgScene.toScene(svg)
        if (compiled.shapes.isEmpty()) return null
        val scene = if (idle) OGSceneIdle.animate(compiled) else compiled
        val vb = runCatching { parseViewBox(svg) }.getOrNull()
        val w = 1080
        val h = if (vb != null && vb.width > 0f && vb.height > 0f) {
            (1080f * vb.height / vb.width).roundToInt().coerceIn(240, 4096)
        } else {
            1080
        }
        val duration = if (scene.isLive) durationMs.coerceAtLeast(IDLE_LOOP_MS) else durationMs
        return OGStage(
            width = w,
            height = h,
            durationMs = duration.coerceAtLeast(1L),
            fps = fps.coerceIn(1, 120),
            background = background,
            layers = listOf(OGStageLayer(id = "svg-scene", content = OGStageContent.Scene(scene))),
        )
    }

    /** Lift a single `<svg>…</svg>` out of a model reply (prose / markdown fences tolerated) and compile
     *  it, or `null` if there's no SVG in the text. Idle motion is on by default (see [fromSvg]). */
    public fun fromReply(
        reply: String,
        durationMs: Long = DEFAULT_DURATION_MS,
        fps: Int = DEFAULT_FPS,
        idle: Boolean = true,
    ): OGStage? = extractSvg(reply)?.let { fromSvg(it, durationMs, fps, idle = idle) }

    private fun extractSvg(text: String): String? {
        val start = text.indexOf("<svg", ignoreCase = true)
        if (start < 0) return null
        val end = text.indexOf("</svg>", startIndex = start, ignoreCase = true)
        if (end < 0) return null
        return text.substring(start, end + "</svg>".length)
    }
}
