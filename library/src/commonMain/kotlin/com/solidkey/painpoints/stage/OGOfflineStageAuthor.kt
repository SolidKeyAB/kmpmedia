package com.solidkey.painpoints.stage

import com.solidkey.painpoints.ai.OGColorMotion
import com.solidkey.painpoints.ai.OGFillSpec
import com.solidkey.painpoints.ai.OGMotionOffset
import com.solidkey.painpoints.ai.OGMotionScalar
import com.solidkey.painpoints.ai.OGPlacedShapeSpec
import com.solidkey.painpoints.ai.OGPointSpec
import com.solidkey.painpoints.ai.OGRegionMotion
import com.solidkey.painpoints.ai.OGSceneIdle
import com.solidkey.painpoints.ai.OGSceneMotion
import com.solidkey.painpoints.ai.OGSceneSpec
import com.solidkey.painpoints.ai.OGStopSpec
import com.solidkey.painpoints.compositor.OGEasing
import com.solidkey.painpoints.fx.OGLightningSpec
import com.solidkey.painpoints.fx.OGSpeedLinesSpec
import com.solidkey.painpoints.look.OGBlendMode
import com.solidkey.painpoints.particle.OGParticleSpec
import com.solidkey.painpoints.particle.OGParticles
import com.solidkey.painpoints.style.OGStyleOp
import com.solidkey.painpoints.style.OGStyleSpec
import kotlin.math.cos
import kotlin.math.sin

/**
 * A deterministic, **network-free** [OGStageAuthor] that turns a plain-English description into a real,
 * playable [OGStage] — with no language model, no key, and no call. It reads the instruction for
 * mood / time-of-day words (night, sunset, neon, storm…) and subjects (sea, hills, city, fire, petals…)
 * and assembles a backdrop scene (sky gradient + layered hills + an optional drifting sun/moon),
 * atmosphere particles, an optional action-FX beat (lightning / speed-lines), and a styled title card,
 * all through the ordinary [OGStages] builders. The same words always produce the same movie (it is
 * seeded by the text).
 *
 * This is the **honest floor** of KMPMedia's describe→movie loop: it works offline, in tests, and
 * whenever no model is configured. A real model — wired via [OGStageAuthoring.modelAuthor] — simply
 * produces richer, more surprising movies through the exact same [OGStage] format and player. Pair the
 * two with [OGStageAuthoring.resilient] (model first, this as the safety net).
 *
 * It is pure data assembly done once at author time (never per frame), so the movie it returns clears
 * the same 60fps gate as a hand-authored one.
 */
public class OGOfflineStageAuthor(
    private val width: Int = 1600,
    private val height: Int = 900,
    private val durationMs: Long = 5000L,
    private val fps: Int = 30,
    /** The rig-recipe catalog used to draw a recognised subject. Pass your own (e.g.
     *  `OGRigRecipes.builtIns + myRecipe`) to teach the offline floor new kinds — the extendable seam. */
    private val recipes: List<OGRigRecipe> = OGRigRecipes.builtIns,
) : OGStageAuthor {

    override suspend fun author(instruction: String): OGStage = direct(instruction)

    /** The pure, synchronous core (no coroutines) — the same result [author] returns, handy for tests
     *  and non-suspend call sites. */
    public fun direct(instruction: String): OGStage {
        val text = instruction.lowercase()
        val seed = (instruction.hashCode() and 0x7fffffff)
        val p = paletteFor(text)
        val durSec = durationMs / 1000f

        val layers = ArrayList<OGStageLayer>(5)

        // 1) Backdrop scene — a slow push-in gives even a still subject life.
        layers += OGStages.scene(
            id = "backdrop",
            scene = backdrop(text, p, durSec),
            scale = OGStages.eased(OGEasing.EASE_OUT, 0L to 1f, durationMs to 1.06f),
        )

        // 2) Atmosphere particles — always present (drifting motes at worst), so the frame breathes.
        layers += OGStages.particles(
            id = "atmosphere",
            spec = particlesFor(text, p, seed),
            blend = p.particleBlend,
            opacity = OGStages.eased(OGEasing.EASE_OUT, 0L to 0f, 700L to 1f),
        )

        // 3) Optional action-FX beat (a storm's bolt, a rush's speed-lines).
        fxFor(text, p, seed)?.let { layers += it }

        // 3b) A recognisable SUBJECT (a cat, a person, a flower, a pawn…) → assemble a living figure
        //     from its rig recipe and stand it in front of the backdrop. Deterministic + network-free;
        //     when nothing matches we simply fall through to mood + title as before.
        subjectLayer(text)?.let { layers += it }

        // 4) Title card — fades + settles in, holds, eases out at the end.
        layers += titleLayer(instruction, text, p)

        return OGStage.of(
            *layers.toTypedArray(),
            width = width,
            height = height,
            durationMs = durationMs,
            fps = fps,
            background = p.background,
        )
    }

    // ── Palette ───────────────────────────────────────────────────────────────────────────────────

    /** The resolved colour world for a movie: sky ramp, layered ground, accent (sun/glow), particle
     *  tint + blend, title ink, and an optional base-coat the scene swings toward over time. */
    private data class Palette(
        val background: String,
        val skyTop: String,
        val skyBottom: String,
        val groundFar: String,
        val groundNear: String,
        val groundDeep: String,
        val accent: String,
        val particleTint: String,
        val particleBlend: OGBlendMode,
        val title: String,
        /** When non-null the backdrop scene swings its base coat toward this (a day→dusk cycle). */
        val swingTo: String?,
        /** When true place a drifting celestial disc (sun / moon) in the sky. */
        val celestial: Boolean,
    )

    private fun paletteFor(t: String): Palette = when {
        t.hasAny("neon", "cyber", "synthwave", "arcade", "glitch") -> Palette(
            background = "#05060a", skyTop = "#0a0620", skyBottom = "#1a0b3a",
            groundFar = "#1c0b40", groundNear = "#0c0722", groundDeep = "#05040f",
            accent = "#19f6ff", particleTint = "#ff2bd6", particleBlend = OGBlendMode.PLUS,
            title = "#eafcff", swingTo = null, celestial = false,
        )
        t.hasAny("storm", "thunder", "lightning", "tempest") -> Palette(
            background = "#05070f", skyTop = "#02040c", skyBottom = "#16284f",
            groundFar = "#122641", groundNear = "#0b1b34", groundDeep = "#030710",
            accent = "#8fb8ff", particleTint = "#9fc4ff", particleBlend = OGBlendMode.SCREEN,
            title = "#dce8ff", swingTo = null, celestial = false,
        )
        t.hasAny("night", "midnight", "starry", "space", "galaxy", "cosmos", "moon") -> Palette(
            background = "#060a16", skyTop = "#03060f", skyBottom = "#132a52",
            groundFar = "#122038", groundNear = "#0a1526", groundDeep = "#04070f",
            accent = "#cfe0ff", particleTint = "#dce8ff", particleBlend = OGBlendMode.PLUS,
            title = "#eef4ff", swingTo = null, celestial = true,
        )
        t.hasAny("sunset", "dusk", "golden", "evening", "twilight") -> Palette(
            background = "#2a1636", skyTop = "#4b2e63", skyBottom = "#ff9e5e",
            groundFar = "#7a3a5a", groundNear = "#3a1c38", groundDeep = "#1c0e26",
            accent = "#ffd79a", particleTint = "#ffd0b0", particleBlend = OGBlendMode.NORMAL,
            title = "#fff1e6", swingTo = "#201033", celestial = true,
        )
        t.hasAny("dawn", "sunrise", "morning") -> Palette(
            background = "#ffd9cf", skyTop = "#ffd1dc", skyBottom = "#fff2cf",
            groundFar = "#f0b89e", groundNear = "#d98f82", groundDeep = "#9c5a58",
            accent = "#fff0c0", particleTint = "#ffe6d0", particleBlend = OGBlendMode.NORMAL,
            title = "#5a3a44", swingTo = "#ffbfae", celestial = true,
        )
        t.hasAny("ocean", "sea", "underwater", "wave", "lagoon", "reef") -> Palette(
            background = "#042a3a", skyTop = "#063a4d", skyBottom = "#0d8aa0",
            groundFar = "#0a6274", groundNear = "#063a4a", groundDeep = "#022029",
            accent = "#aef0ff", particleTint = "#cdf7ff", particleBlend = OGBlendMode.SCREEN,
            title = "#e6ffff", swingTo = null, celestial = false,
        )
        t.hasAny("forest", "jungle", "meadow", "woods", "garden", "grass") -> Palette(
            background = "#eaf3c8", skyTop = "#cfe9b0", skyBottom = "#eef7d2",
            groundFar = "#6fa84a", groundNear = "#3a6b35", groundDeep = "#223f20",
            accent = "#fff1b0", particleTint = "#fff6cf", particleBlend = OGBlendMode.NORMAL,
            title = "#223f20", swingTo = "#d7ebc0", celestial = true,
        )
        t.hasAny("fire", "flame", "lava", "volcano", "ember", "inferno") -> Palette(
            background = "#160402", skyTop = "#2a0a06", skyBottom = "#ff6a2b",
            groundFar = "#5a1808", groundNear = "#2a0a04", groundDeep = "#120302",
            accent = "#ffd080", particleTint = "#ffb060", particleBlend = OGBlendMode.PLUS,
            title = "#ffe9d6", swingTo = null, celestial = false,
        )
        t.hasAny("snow", "winter", "ice", "frost", "blizzard", "arctic") -> Palette(
            background = "#eef6ff", skyTop = "#cfe8ff", skyBottom = "#f4faff",
            groundFar = "#cfe0ef", groundNear = "#b6cde0", groundDeep = "#8fb0c8",
            accent = "#ffffff", particleTint = "#ffffff", particleBlend = OGBlendMode.NORMAL,
            title = "#35506b", swingTo = null, celestial = true,
        )
        else -> Palette( // calm daylight
            background = "#dff1ff", skyTop = "#7fb3e6", skyBottom = "#dff1ff",
            groundFar = "#6fa0c8", groundNear = "#3f7fb0", groundDeep = "#275a82",
            accent = "#fff3c0", particleTint = "#ffffff", particleBlend = OGBlendMode.NORMAL,
            title = "#12324a", swingTo = "#cfe6fb", celestial = true,
        )
    }

    // ── Backdrop scene ──────────────────────────────────────────────────────────────────────────

    private fun backdrop(t: String, p: Palette, durSec: Float): OGSceneSpec {
        val shapes = ArrayList<OGPlacedShapeSpec>(4)

        // Sky: a full-width band with a vertical gradient from skyTop → skyBottom.
        shapes += OGPlacedShapeSpec(
            points = rect(0f, 0f, 1f, 0.68f),
            label = "sky",
            gradient = linear(p.skyTop, p.skyBottom),
        )

        // Celestial disc (sun / moon): round in pixels, drifts down and fades on its own clock.
        if (p.celestial) {
            shapes += OGPlacedShapeSpec(
                points = disc(0.76f, 0.24f, 0.09f),
                label = "sun",
                fill = p.accent,
                opacity = 0.95f,
                smoothing = 1f,
                motion = OGRegionMotion(
                    duration = durSec,
                    mode = "once",
                    easing = "easeIn",
                    translate = OGMotionOffset(OGPointSpec(0f, 0f), OGPointSpec(-0.04f, 0.22f)),
                    opacity = OGMotionScalar(0.95f, 0.5f),
                ),
            )
        }

        // Far hills — lighter, higher ridge.
        shapes += OGPlacedShapeSpec(
            points = ridge(0.64f, 0.06f, seedOf(t, 1)),
            label = "hills_far",
            fill = p.groundFar,
            opacity = 0.9f,
            smoothing = 0.8f,
        )
        // Near hills — darker, lower ridge, vertical gradient for depth.
        shapes += OGPlacedShapeSpec(
            points = ridge(0.8f, 0.08f, seedOf(t, 2)),
            label = "hills_near",
            gradient = linear(p.groundNear, p.groundDeep),
            smoothing = 0.9f,
        )

        return OGSceneSpec(
            shapes = shapes,
            version = 4,
            background = p.background,
            motion = p.swingTo?.let {
                OGSceneMotion(
                    duration = durSec,
                    mode = "once",
                    easing = "easeInOut",
                    background = OGColorMotion(from = p.background, to = it),
                )
            },
        )
    }

    // ── Particles ─────────────────────────────────────────────────────────────────────────────────

    private fun particlesFor(t: String, p: Palette, seed: Int): OGParticleSpec = when {
        t.hasAny("rain", "storm", "thunder", "shower", "drizzle") ->
            OGParticles.RAIN.copy(
                maxParticles = 700, emissionRate = 420f, speed = 1.6f, gravityY = 1.0f,
                startColor = p.particleTint, startAlpha = 0.7f, endAlpha = 0.2f, seed = seed,
            )
        t.hasAny("snow", "winter", "blizzard", "frost") ->
            OGParticles.SNOW.copy(startColor = p.particleTint, seed = seed)
        t.hasAny("petal", "blossom", "cherry", "sakura", "flower", "bloom") ->
            OGParticles.PETALS.copy(startColor = p.particleTint, seed = seed)
        t.hasAny("leaf", "leav", "autumn") ->
            OGParticles.LEAVES.copy(seed = seed)
        t.hasAny("ember", "fire", "flame", "lava", "spark", "volcano") ->
            OGParticles.EMBERS.copy(startColor = p.particleTint, seed = seed)
        t.hasAny("confetti", "party", "celebrat", "birthday", "festival") ->
            OGParticles.CONFETTI.copy(seed = seed)
        t.hasAny("firework", "fireworks") ->
            OGParticles.FIREWORKS.copy(seed = seed)
        t.hasAny("star", "space", "galaxy", "cosmos", "stars", "cosmic") ->
            OGParticleSpec(
                name = "stardust", shape = "star", maxParticles = 90, emissionRate = 18f,
                lifetimeMs = 6000f, x = 0.5f, y = 0.5f, spawnRadius = 0.6f, angleDeg = 270f,
                spreadDeg = 360f, speed = 0.02f, speedJitter = 1f, drag = 0.2f,
                startSize = 0.006f, endSize = 0.012f, startColor = p.particleTint,
                startAlpha = 0.9f, endAlpha = 0f, spinDeg = 40f, seed = seed,
            )
        t.hasAny("rush", "speed", "neon", "cyber", "race") ->
            OGParticles.SPARKS.copy(
                startColor = p.accent, endColor = p.particleTint, emissionRate = 30f, burst = 20, seed = seed,
            )
        else -> // drifting dust motes — subtle, always-on atmosphere
            OGParticles.BOKEH.copy(
                maxParticles = 48, emissionRate = 9f, startColor = p.particleTint, endColor = null,
                startAlpha = 0.45f, endAlpha = 0f, startSize = 0.02f, endSize = 0.03f, seed = seed,
            )
    }

    // ── Action-FX beat ──────────────────────────────────────────────────────────────────────────

    private fun fxFor(t: String, p: Palette, seed: Int): OGStageLayer? = when {
        t.hasAny("storm", "thunder", "lightning") -> OGStages.lightning(
            id = "bolt",
            spec = OGLightningSpec(
                x1 = 0.62f, y1 = 0.02f, x2 = 0.5f, y2 = 0.62f,
                color = p.accent, coreColor = "#FFFFFF", glow = 0.95f,
                strikeMs = 120f, decayMs = 520f, gapMs = 1600f, loop = true, seed = seed,
            ),
            blend = OGBlendMode.PLUS,
        ).copy(opacity = OGStages.eased(OGEasing.EASE_OUT, 0L to 0f, 900L to 0f, 1100L to 1f))
        t.hasAny("rush", "speed", "fast", "race", "zoom", "neon", "cyber") -> OGStages.speedLines(
            id = "lines",
            spec = OGSpeedLinesSpec(
                mode = "radial", cx = 0.5f, cy = 0.5f, count = 52, color = p.accent,
                innerRadius = 0.3f, outerRadius = 1.08f, thickness = 0.01f,
                speed = 1.6f, pulse = 0.3f, alpha = 0.8f, seed = seed,
            ),
            blend = OGBlendMode.PLUS,
        ).copy(
            opacity = OGStages.eased(OGEasing.EASE_OUT, 0L to 0f, 500L to 0.9f),
            scale = OGStages.eased(OGEasing.EASE_OUT, 0L to 1.2f, 900L to 1f),
        )
        else -> null
    }

    // ── Subject figure (rig recipe) ───────────────────────────────────────────────────────────────

    /** If the words name a kind we have a recipe for, assemble a living figure (idle-animated by
     *  [OGSceneIdle]) and stand it centre-frame in front of the backdrop; else null (→ pure atmosphere). */
    private fun subjectLayer(t: String): OGStageLayer? {
        val recipe = OGRigRecipes.select(t, recipes) ?: return null
        val figure = OGSceneIdle.animate(recipe.assemble(aspect = width.toFloat() / height))
        if (figure.shapes.isEmpty()) return null
        return OGStages.scene(
            id = "subject",
            scene = figure,
            opacity = OGStages.eased(OGEasing.EASE_OUT, 0L to 0f, 450L to 1f),
            scale = OGStages.eased(OGEasing.EASE_OUT, 0L to 0.96f, 700L to 1f),
        )
    }

    // ── Title card ────────────────────────────────────────────────────────────────────────────────

    private fun titleLayer(instruction: String, t: String, p: Palette): OGStageLayer {
        val boil = t.hasAny("neon", "cyber", "rush", "speed", "fire", "flame", "lava", "glitch")
        val style = if (boil) {
            OGStyleSpec(name = "vibe", ops = listOf(OGStyleOp(op = "boil", amplitude = 0.018f, boilFps = 9f)))
        } else {
            OGStyleSpec(name = "handdrawn", ops = listOf(OGStyleOp(op = "roughen", amplitude = 0.02f, detail = 4)))
        }
        val holdOut = (durationMs - 500L).coerceAtLeast(900L)
        return OGStages.text(
            id = "title",
            text = titleText(instruction),
            size = 0.2f,
            color = p.title,
            bold = true,
            style = style,
            opacity = OGStages.eased(
                OGEasing.EASE_OUT,
                0L to 0f, 850L to 1f, holdOut to 1f, durationMs to 0.9f,
            ),
            scale = OGStages.eased(OGEasing.EASE_OUT, 0L to 1.15f, 900L to 1f),
        )
    }

    /** Derive a short, screen-worthy title: the one or two longest meaningful words of the ask,
     *  upper-cased (no regex, so it is identical on every platform). */
    private fun titleText(instruction: String): String {
        val stop = setOf(
            "the", "a", "an", "of", "over", "with", "and", "in", "on", "at", "to", "into",
            "under", "above", "from", "by", "for", "is", "are", "as", "its", "my", "your",
        )
        val words = instruction
            .split(' ', '\n', '\t', ',', '.', ';', ':', '!', '?', '"', '\'', '(', ')', '-', '/', '_')
            .map { tok -> tok.filter { it.isLetter() } }
            .filter { it.length >= 3 && it.lowercase() !in stop }
        if (words.isEmpty()) {
            val trimmed = instruction.trim()
            return if (trimmed.isEmpty()) "MOVIE" else trimmed.filter { it.isLetterOrDigit() || it == ' ' }.trim().take(16).uppercase().ifEmpty { "MOVIE" }
        }
        val picked = words.withIndex()
            .sortedByDescending { it.value.length }
            .take(2)
            .sortedBy { it.index }
            .map { it.value }
        return picked.joinToString(" ").uppercase().take(18)
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────────────────────────

    private fun rect(x0: Float, y0: Float, x1: Float, y1: Float): List<OGPointSpec> =
        listOf(OGPointSpec(x0, y0), OGPointSpec(x1, y0), OGPointSpec(x1, y1), OGPointSpec(x0, y1))

    /** A polygon that looks round in pixels (x radius corrected by the box aspect), closed to [n] verts. */
    private fun disc(cx: Float, cy: Float, r: Float, n: Int = 18): List<OGPointSpec> {
        val ry = r * (width.toFloat() / height) // taller in normalized space → round in pixels (16:9)
        return (0 until n).map { i ->
            val a = (i / n.toFloat()) * 6.2831855f
            OGPointSpec(cx + r * cos(a), cy + ry * sin(a))
        }
    }

    /** A hill silhouette: a wavy top edge around [baseY] (± [amp]) closed down to the bottom. */
    private fun ridge(baseY: Float, amp: Float, seed: Int): List<OGPointSpec> {
        val cols = 6
        val top = (0..cols).map { i ->
            val x = i / cols.toFloat()
            val h = hashSigned(i, seed) // -1..1
            OGPointSpec(x, (baseY + h * amp).coerceIn(0.4f, 0.98f))
        }
        return top + listOf(OGPointSpec(1f, 1f), OGPointSpec(0f, 1f))
    }

    private fun linear(top: String, bottom: String): OGFillSpec = OGFillSpec(
        type = "linear",
        from = OGPointSpec(0.5f, 0f),
        to = OGPointSpec(0.5f, 1f),
        stops = listOf(OGStopSpec(0f, top), OGStopSpec(1f, bottom)),
    )

    private fun seedOf(t: String, salt: Int): Int = ((t.hashCode() * 31 + salt) and 0x7fffffff)

    /** Deterministic `-1..1` from an integer index + seed (same hash family the FX packs use). */
    private fun hashSigned(i: Int, seed: Int): Float {
        var h = (i * 374761393) xor (seed * 668265263)
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return ((h and 0xffff) / 32768f) - 1f
    }

    private fun String.hasAny(vararg needles: String): Boolean = needles.any { this.contains(it) }
}
