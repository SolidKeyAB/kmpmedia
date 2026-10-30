package com.solidkey.painpoints.stage

import com.solidkey.painpoints.ai.OGLiveFrame
import com.solidkey.painpoints.ai.OGSceneSpec
import com.solidkey.painpoints.ai.isLive
import com.solidkey.painpoints.ai.liveAt
import com.solidkey.painpoints.compositor.OGKeyframedFloat
import com.solidkey.painpoints.fx.OGLightningSpec
import com.solidkey.painpoints.fx.OGSlashSpec
import com.solidkey.painpoints.fx.OGSpeedLinesSpec
import com.solidkey.painpoints.look.OGBlendMode
import com.solidkey.painpoints.look.OGLookSpec
import com.solidkey.painpoints.particle.OGParticleSpec
import com.solidkey.painpoints.style.OGStyleSpec
import com.solidkey.painpoints.motion.OGClothSpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.roundToInt

// ============================================================================================
// OGStage — the UNIFYING document: one serializable "movie" a generator can emit and one player
// ([OGStageView]) can play end-to-end.
//
// KMPMedia already had both halves of an animation engine, but spread across two document models
// that did not know about each other — the compositor's keyframed layer timeline ([OGComposition],
// bitmap/solid content only) and the model-authorable, t-addressable AI scene ([OGSceneSpec] +
// [com.solidkey.painpoints.ai.OGSceneLive]) — plus a dozen stand-alone packs (particles, fx, looks,
// dynamics, rig) that an app composed by hand. OGStage ties them together: a timeline of [layers],
// each a [content] track bound to one existing pack, transformed by the SAME keyframe tracks the
// compositor uses ([OGKeyframedFloat]), graded by the SAME [OGLookSpec] and composited with the SAME
// [OGBlendMode]. One codec ([OGStages]) round-trips the whole thing as JSON, so a language model (or
// a person) authors a complete animated, multi-pack movie as data.
//
// It is deliberately a THIN orchestration layer, not a new engine: it reuses the shipped primitives
// verbatim and adds no new per-frame cost of its own. Everything stays on the parse-once /
// lerp-per-frame, 60fps budget the rest of the library holds to — the scene/transform channels are
// closed-form t-addressable so the player can scrub and (later) a compositor can export frame by
// frame; the particle pack stays its own stateful simulation (live, deterministic from its seed).
// ============================================================================================

/**
 * The visual content of one [OGStageLayer] — a thin wrapper that binds a layer to one existing
 * KMPMedia pack. Serialized polymorphically with a `"type"` discriminator so a model can author any
 * layer kind in the same JSON. Everything here is already serializable data, so a whole stage round
 * trips losslessly.
 *
 * The kinds cover the two halves of the engine (a generated scene + a generative-motion pack), the
 * action-FX packs, styled vector [Text] for titles/captions, a flat fill, a wind-blown [Cloth] strip,
 * and a raster [Image] cutout — all behind the same interface with no change to [OGStage] or [OGStageView].
 */
@Serializable
sealed interface OGStageContent {
    /** A flat colour fill of the whole layer box (an SVG colour string). The simplest backdrop. */
    @Serializable
    @SerialName("solid")
    data class Solid(val color: String) : OGStageContent

    /** A model-authorable, t-addressable vector [scene] (shapes + gradients + its own `motion`). */
    @Serializable
    @SerialName("scene")
    data class Scene(val scene: OGSceneSpec) : OGStageContent

    /** A data-defined particle emitter ([particles]); runs its own stateful, seeded simulation. */
    @Serializable
    @SerialName("particles")
    data class Particles(val particles: OGParticleSpec) : OGStageContent

    /** An action-FX breathing-slash ribbon ([slash]) — a water/flame/thunder sweep. */
    @Serializable
    @SerialName("slash")
    data class Slash(val slash: OGSlashSpec) : OGStageContent

    /** An action-FX forked lightning bolt ([lightning]). */
    @Serializable
    @SerialName("lightning")
    data class Lightning(val lightning: OGLightningSpec) : OGStageContent

    /** Action-FX manga speed / impact lines ([speedLines]). */
    @Serializable
    @SerialName("speedlines")
    data class SpeedLines(val speedLines: OGSpeedLinesSpec) : OGStageContent

    /**
     * A line of **styled vector text** — a title, caption or lower-third. The glyphs are vectorized to
     * outlines and filled, centred in the layer box (position / scale / rotate it with the layer's own
     * transform tracks). An optional [style] pipeline (`roughen` / `boil` / `wave` / …) inks the letters
     * hand-drawn and — for a time-varying op — alive on the layer's local clock.
     *
     * @param size the em height as a **fraction of the stage height**, so the text stays
     *   resolution-independent (a single stage renders the same on any output size). `0.14` ≈ a caption.
     * @param color the glyph fill (an SVG colour string).
     * @param bold / [italic] / [family] the font selection ([family] = a platform family like
     *   `"serif"` / `"monospace"`, or `null` for the default sans).
     * @param style an optional [OGStyleSpec] run over every glyph outline; `null` = a clean fill.
     */
    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
        val size: Float = 0.14f,
        val color: String = "#FFFFFF",
        val bold: Boolean = false,
        val italic: Boolean = false,
        val family: String? = null,
        val style: OGStyleSpec? = null,
    ) : OGStageContent

    /**
     * A **raster cutout** — real drawn pixels (a bundled PNG / WebP), not a redrawn vector. This is the
     * image-bitmap slot the engine reserved: it lets a hand-drawn character part live on the stage as
     * itself. One or more [frames] are shown in order on the layer's local clock (looping), so a cutout
     * animates by **frame-swap** (a blink, a mouth shape, a wave) while the layer's own transform tracks
     * move it and an [OGStageLayer.onTap] makes it poke-reactive — a paper-doll / Spine-style puppet,
     * authored as data.
     *
     * The image is centred in the layer box and scaled to **fit** within [widthFrac] × [heightFrac] of
     * it (aspect preserved), so you place and size it with the layer transform, not by pre-scaling the
     * art. Each frame names a resource the **host app bundles** (Android `res/drawable`, iOS bundle),
     * resolved by the shared image loader — so unlike the other content kinds a blind describe→movie
     * model can't author this one, but an app assembling a puppet from its own assets can.
     *
     * @param frames the cutout's frames in play order; one frame = a still, several = a frame-swap sprite.
     * @param widthFrac / [heightFrac] the fit box as a fraction of the stage (`1` = the whole stage box).
     */
    @Serializable
    @SerialName("image")
    data class Image(
        val frames: List<OGImageFrame>,
        val widthFrac: Float = 1f,
        val heightFrac: Float = 1f,
    ) : OGStageContent {
        /** True when there is more than one frame — the cutout animates by frame-swap. */
        val isAnimated: Boolean get() = frames.size > 1

        /** The index of the frame shown at layer-local [timeMs], looping over the total hold span. Safe
         *  for an empty / single-frame list (returns 0). */
        fun frameIndexAt(timeMs: Long): Int {
            if (frames.size <= 1) return 0
            val total = frames.sumOf { it.holdMs.coerceAtLeast(1L) }
            if (total <= 0L) return 0
            var t = (timeMs % total).coerceAtLeast(0L)
            for (i in frames.indices) {
                t -= frames[i].holdMs.coerceAtLeast(1L)
                if (t < 0L) return i
            }
            return frames.lastIndex
        }
    }

    /**
     * A **wind-blown cloth strip** — a scarf, cape edge, banner, or a lock of **long hair** that hangs
     * and *flutters*. Wraps the motion family's 1-D Verlet [OGClothSpec] (gravity + prevailing wind +
     * turbulent gusts) as a first-class stage layer: the strip's head is pinned at ([anchorX], [anchorY])
     * inside the layer box and streams out on the breeze, deterministic and frame-identical on both
     * platforms (it re-simulates from `0` to the layer-local time, so a live frame and an exported frame
     * match exactly). Pair it with a particle layer blowing the SAME direction and the whole scene reads
     * as one wind — snow sweeps, hair streams, a dress ripples, together.
     *
     * Pin it onto a character cutout by placing this layer at the hairline / hem / sleeve with the layer
     * transform. The anchor is fixed within the box (the layer transform moves it), so the strip responds
     * to **wind**, not to the character's own acceleration — inertial drag from motion is out of scope.
     *
     * @param cloth the fabric's feel (nodes / length / gravity / damping / wind…); reuse an [OGClothSpec]
     *   preset like `"hair"` or `"scarf"` and `copy()` the wind to taste.
     * @param anchorX / [anchorY] the pinned head, normalized `0..1` of the layer box.
     * @param tailX / [tailY] the far-end pin, used only when [OGClothSpec.pinTail] (a banner strung taut).
     * @param color the ribbon fill (an SVG colour string).
     * @param width the ribbon's half-thickness as a fraction of the box's shorter side.
     * @param taper taper the ribbon to a point at the free end (a hair lock / pennant) vs a constant
     *   width (a strap / banner).
     */
    @Serializable
    @SerialName("cloth")
    data class Cloth(
        val cloth: OGClothSpec,
        val anchorX: Float = 0.5f,
        val anchorY: Float = 0.12f,
        val tailX: Float = 0.5f,
        val tailY: Float = 0.12f,
        val color: String = "#FFFFFF",
        val width: Float = 0.05f,
        val taper: Boolean = true,
    ) : OGStageContent
}

/**
 * One raster frame of an [OGStageContent.Image] cutout: a bundled image [resource] held on screen for
 * [holdMs] before the next frame. A single frame is a static cutout; a list is a frame-swap sprite — a
 * blink, a mouth shape, a wave — advanced on the layer's local clock. Hold the resting frame long and
 * the transient frames briefly to read as a natural blink (e.g. `open 3000ms, half 40ms, shut 90ms`).
 */
@Serializable
data class OGImageFrame(
    val resource: String,
    val holdMs: Long = 120L,
)

/** Style ops whose output changes with time — a [OGStageContent.Text] carrying one of these animates. */
private val TEXT_LIVE_OPS = setOf("boil", "wave", "ripple")

/**
 * One layer of an [OGStage]: some [content] placed over the whole stage box, transformed by five
 * keyframe tracks and graded by an optional [look] / [blend]. The layer is only drawn while the
 * stage clock is within `[startMs, endMs]` (`endMs = null` runs to the stage end), so layers can
 * enter and leave over the timeline.
 *
 * **Two clocks, deliberately.** The transform tracks ([x]/[y]/[scale]/[rotationDeg]/[opacity]) read
 * **absolute stage time** (exactly like an [com.solidkey.painpoints.compositor.OGCompositionLayer]),
 * so authoring a pan/zoom is identical to authoring a composition. The content's *own* internal
 * motion (a scene's day→night, a region's drift) reads **local time** (`stageTime - startMs`), so a
 * layer that enters at 2s starts its own life at 0. [resolveAt] returns both.
 *
 * Coordinates are normalized: [x]/[y] are a fraction of the stage width/height added as a translate
 * (so `0` = no offset), [scale] is about the layer centre, [rotationDeg] is degrees about the centre,
 * [opacity] is `0..1`.
 */
@Serializable
data class OGStageLayer(
    val id: String,
    val content: OGStageContent,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val x: OGKeyframedFloat = OGKeyframedFloat.const(0f),
    val y: OGKeyframedFloat = OGKeyframedFloat.const(0f),
    val scale: OGKeyframedFloat = OGKeyframedFloat.const(1f),
    val rotationDeg: OGKeyframedFloat = OGKeyframedFloat.const(0f),
    val opacity: OGKeyframedFloat = OGKeyframedFloat.const(1f),
    /** An optional colour grade for just this layer (a scene's own live grade wins over it). */
    val look: OGLookSpec? = null,
    /** How this layer composites over the layers beneath it. [OGBlendMode.NORMAL] = source-over. */
    val blend: OGBlendMode = OGBlendMode.NORMAL,
    /**
     * An optional **tap handler** that makes this layer interactive: a finger on the layer fires a
     * reaction (a spring bounce/shake/spin, or a seek / play-pause). Pure data, so it round-trips
     * through [OGStages] and a model can author it; `null` = an inert, watch-only layer. The player
     * ([OGStageView]) drives the reaction; the off-screen exporter ignores it (it bakes the timeline).
     */
    val onTap: OGStageTrigger? = null,
)

/**
 * A **declarative, model-authorable animation** — a fixed [width] × [height] canvas, a [durationMs]
 * timeline at [fps], an optional [background] base coat, and a stack of [layers] drawn back-to-front
 * (first = behind). Preview it live at 60fps with [OGStageView], or scrub any instant with its
 * `positionMs`.
 *
 * Pure data: nothing platform-specific lives here, and [resolveAt] flattens the whole stage to plain
 * values at any time t (closed-form for the scene/transform channels), so it is directly unit-testable
 * and behaves identically on Android and iOS.
 */
@Serializable
data class OGStage(
    val width: Int = 1080,
    val height: Int = 1080,
    val durationMs: Long = 4000L,
    val fps: Int = 60,
    /** A solid base coat painted before layer 0 (SVG colour string); `null` leaves it transparent. */
    val background: String? = null,
    val layers: List<OGStageLayer> = emptyList(),
) {
    /** Number of frames a frame-by-frame export would walk: `round(durationMs/1000 * fps)`, at least one. */
    val frameCount: Int
        get() = max(1, (durationMs.toDouble() / 1000.0 * fps).roundToInt())

    /** Absolute time (ms) of frame [index], evenly spaced from 0. */
    fun frameTimeMs(index: Int): Long = index.toLong() * 1000L / fps.toLong().coerceAtLeast(1L)

    /** Per-frame GIF delay in centiseconds (GIF's timing unit), clamped `>= 2cs` like the compositor. */
    val frameDelayCs: Int
        get() = max(2, (100.0 / fps.coerceAtLeast(1)).roundToInt())

    /** The stage's width:height ratio (for fitting it into a view); `1` when height is zero. */
    val aspect: Float
        get() = if (height != 0) width.toFloat() / height.toFloat() else 1f

    /** True when anything moves: any scene layer declares motion, any particle layer exists, or any
     *  layer carries a keyframed transform track. A still stage renders one unchanging frame. */
    val isLive: Boolean
        get() = layers.any { l ->
            val contentLive = when (val c = l.content) {
                is OGStageContent.Scene -> c.scene.isLive
                is OGStageContent.Particles -> true
                is OGStageContent.Slash -> true
                is OGStageContent.Lightning -> true
                is OGStageContent.SpeedLines -> true
                is OGStageContent.Text -> c.style?.ops?.any { it.op.lowercase() in TEXT_LIVE_OPS } == true
                is OGStageContent.Image -> c.isAnimated
                is OGStageContent.Cloth -> true
                is OGStageContent.Solid -> false
            }
            contentLive ||
                l.x.keyframes.isNotEmpty() || l.y.keyframes.isNotEmpty() ||
                l.scale.keyframes.isNotEmpty() || l.rotationDeg.keyframes.isNotEmpty() ||
                l.opacity.keyframes.isNotEmpty()
        }

    /** Every layer visible at [timeMs], flattened to plain values, in back-to-front draw order. */
    fun resolveAt(timeMs: Long): List<OGResolvedStageLayer> =
        layers.filter { it.isVisibleAt(timeMs, durationMs) }.map { it.resolveAt(timeMs) }

    companion object {
        /** Build a stage from layers (first = back), with the common knobs. */
        fun of(
            vararg layers: OGStageLayer,
            width: Int = 1080,
            height: Int = 1080,
            durationMs: Long = 4000L,
            fps: Int = 60,
            background: String? = null,
        ): OGStage = OGStage(width, height, durationMs, fps, background, layers.toList())
    }
}

/**
 * One [OGStageLayer] flattened at one instant — every transform track evaluated to a plain value,
 * the content and grade carried through, plus the content's [localTimeMs]. Pure; ready to draw.
 */
data class OGResolvedStageLayer(
    val content: OGStageContent,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotationDeg: Float,
    val opacity: Float,
    val look: OGLookSpec?,
    val blend: OGBlendMode,
    /** Content-local time: `stageTime - layer.startMs`, never negative. Drives scene/region motion. */
    val localTimeMs: Long,
) {
    /** When this layer is a [OGStageContent.Scene], the scene's live frame (motion applied, scatter
     *  expanded, grade/base-coat resolved) at [localTimeMs]; `null` for other content kinds. */
    fun sceneFrameAt(): OGLiveFrame? =
        (content as? OGStageContent.Scene)?.scene?.liveAt(localTimeMs / 1000f)
}

/** True while [timeMs] is within the layer's `[startMs, endMs]` window (`endMs == null` → [durationMs]). */
internal fun OGStageLayer.isVisibleAt(timeMs: Long, durationMs: Long): Boolean {
    val end = endMs ?: durationMs
    return timeMs >= startMs && timeMs <= end
}

/** Flatten one layer's transform tracks at absolute [timeMs]. Pure — just value evaluation. */
internal fun OGStageLayer.resolveAt(timeMs: Long): OGResolvedStageLayer = OGResolvedStageLayer(
    content = content,
    x = x.valueAt(timeMs),
    y = y.valueAt(timeMs),
    scale = scale.valueAt(timeMs),
    rotationDeg = rotationDeg.valueAt(timeMs),
    opacity = opacity.valueAt(timeMs).coerceIn(0f, 1f),
    look = look,
    blend = blend,
    localTimeMs = (timeMs - startMs).coerceAtLeast(0L),
)
