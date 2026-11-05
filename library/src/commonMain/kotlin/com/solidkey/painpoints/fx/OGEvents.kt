package com.solidkey.painpoints.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.solidkey.painpoints.ai.OGFillSpec
import com.solidkey.painpoints.ai.OGPlacedShapeSpec
import com.solidkey.painpoints.ai.OGPointSpec
import com.solidkey.painpoints.ai.OGSceneSpec
import com.solidkey.painpoints.ai.OGStopSpec
import com.solidkey.painpoints.compositor.OGEasing
import com.solidkey.painpoints.look.OGBlendMode
import com.solidkey.painpoints.motion.OGSpring
import com.solidkey.painpoints.motion.OGSpringSpec
import com.solidkey.painpoints.particle.OGParticleSpec
import com.solidkey.painpoints.particle.OGParticleView
import com.solidkey.painpoints.particle.OGParticles
import com.solidkey.painpoints.stage.OGStage
import com.solidkey.painpoints.stage.OGStageLayer
import com.solidkey.painpoints.stage.OGStages
import com.solidkey.painpoints.style.OGStyleOp
import com.solidkey.painpoints.style.OGStyleSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * **Cinematic Natural Disaster & Action Event Suite**
 *
 * Provides ready-to-run, anime-grade composable views and multi-layered [OGStage] movies for the
 * three quintessential dramatic natural events:
 * 1. **Thunder (落雷 / 雷雨)** — Screen flash strobe, forked fractal lightning bolt, airburst shockwave,
 *    ground sparks, driving rain, and camera jolt.
 * 2. **Earthquake (地震 / 地割れ)** — Dual-phase seismic tremor (P-wave rumble + S-wave surface jolt),
 *    propagating tectonic ground chasm, 2:1 ground-roll shockwave, and rising rock dust motes.
 * 3. **Flood (激流 / 氾濫)** — Surging multi-tiered Gerstner waves with rising inundation waterline,
 *    wind-whipped foam spray droplets, and surface impact ripples.
 */
object OGEvents {

    /**
     * Map a grid tile coordinate ([col], [row] in a grid of [gridCols] x [gridRows]) to normalized
     * target center ([targetX], [targetY]) for effortless drop-in use in [Thunder], [Earthquake], and [Flood].
     *
     * Example:
     * ```kotlin
     * val (tx, ty) = OGEvents.gridTarget(col = 2, row = 3, gridCols = 5, gridRows = 5)
     * OGEvents.Thunder(targetX = tx, targetY = ty, tileWidth = 0.16f, tileHeight = 0.16f)
     * ```
     */
    fun gridTarget(
        col: Int,
        row: Int,
        gridCols: Int,
        gridRows: Int,
        paddingFraction: Float = 0.05f,
    ): Pair<Float, Float> {
        val usableW = (1f - 2f * paddingFraction).coerceAtLeast(0.1f)
        val usableH = (1f - 2f * paddingFraction).coerceAtLeast(0.1f)
        val cx = paddingFraction + (col + 0.5f) * (usableW / gridCols.coerceAtLeast(1))
        val cy = paddingFraction + (row + 0.5f) * (usableH / gridRows.coerceAtLeast(1))
        return cx to cy
    }

    /**
     * Build an anime-grade **Thunder & Lightning Storm** movie stage.
     */
    fun thunderStage(
        width: Int = 1600,
        height: Int = 900,
        durationMs: Long = 5000L,
        fps: Int = 30,
    ): OGStage {
        val layers = ArrayList<OGStageLayer>(8)

        // Seismic camera trauma track on strike (650ms)
        val thunderShakeX = OGStages.track(
            0L to 0f, 600L to 0f,
            650L to 0.016f, 700L to -0.014f, 750L to 0.011f,
            810L to -0.008f, 880L to 0.005f, 960L to -0.003f, 1050L to 0f,
        )
        val thunderShakeY = OGStages.track(
            0L to 0f, 600L to 0f,
            650L to -0.020f, 710L to 0.016f, 760L to -0.012f,
            820L to 0.007f, 890L to -0.004f, 980L to 0f,
        )

        // 1. Storm sky backdrop
        val skyScene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(
                    points = listOf(OGPointSpec(0f, 0f), OGPointSpec(1f, 0f), OGPointSpec(1f, 1f), OGPointSpec(0f, 1f)),
                    label = "tempest_sky",
                    gradient = OGFillSpec(
                        type = "linear",
                        from = OGPointSpec(0.5f, 0f),
                        to = OGPointSpec(0.5f, 1f),
                        stops = listOf(OGStopSpec(0f, "#020409"), OGStopSpec(0.65f, "#081024"), OGStopSpec(1f, "#122040")),
                    ),
                ),
            ),
            background = "#020409",
            version = 4,
        )
        layers += OGStages.scene(
            id = "sky",
            scene = skyScene,
            x = thunderShakeX,
            y = thunderShakeY,
            scale = OGStages.eased(OGEasing.EASE_OUT, 0L to 1f, durationMs to 1.05f),
        )

        // 2. Ambient Sky Flash (Impact Frame) - strobes bright white-cyan on strike
        layers += OGStages.solid(
            id = "sky_flash",
            color = "#E1F5FE",
            opacity = OGStages.track(
                0L to 0f, 600L to 0f,
                650L to 0.95f, 720L to 0.20f,
                760L to 0.85f, 980L to 0f,
            ),
        )

        // 3. Main Forked Lightning Bolt (strikes at 650ms)
        layers += OGStages.lightning(
            id = "bolt",
            spec = OGLightningSpec(
                x1 = 0.62f, y1 = 0.02f, x2 = 0.50f, y2 = 0.68f,
                width = 0.015f, jaggedness = 0.18f, detail = 7, branches = 6, branchLength = 0.42f,
                color = "#90CAF9", coreColor = "#FFFFFF", glow = 1f,
                strikeMs = 120f, decayMs = 380f, gapMs = 2800f, loop = true, seed = 42,
            ),
            blend = OGBlendMode.PLUS,
        ).copy(
            startMs = 600L,
            x = thunderShakeX,
            y = thunderShakeY,
            opacity = OGStages.track(600L to 0f, 650L to 1f, 1200L to 1f, 1400L to 0f),
        )

        // 4. Ground Contact Sparks
        layers += OGStages.particles(
            id = "sparks",
            spec = OGParticles.SPARKS.copy(
                maxParticles = 120, burst = 80, emissionRate = 0f,
                x = 0.50f, y = 0.68f, speed = 0.65f, speedJitter = 0.6f,
                gravityY = 0.55f, lifetimeMs = 900f,
            ),
            startMs = 650L,
            blend = OGBlendMode.PLUS,
        ).copy(x = thunderShakeX, y = thunderShakeY)

        // 5. Airburst Ground Shockwave
        layers += OGStages.shockwave(
            id = "shockwave",
            spec = OGShockwave.AIRBURST.copy(maxRadius = 0.42f, ringWidth = 0.024f),
            originX = 0.50f, originY = 0.68f,
            progress = OGStages.eased(OGEasing.EASE_OUT, 650L to 0f, 1350L to 1f),
            startMs = 650L,
            x = thunderShakeX,
            y = thunderShakeY,
            opacity = OGStages.track(650L to 0.95f, 1350L to 0f),
        )

        // 6. Torrential Driving Rain
        layers += OGStages.particles(
            id = "rain",
            spec = OGParticles.RAIN.copy(
                maxParticles = 500, emissionRate = 320f, speed = 1.6f, gravityY = 1.1f,
                angleDeg = 105f, spreadDeg = 8f, startColor = "#B0BEC5", startAlpha = 0.8f,
            ),
            opacity = OGStages.eased(OGEasing.EASE_OUT, 0L to 0f, 400L to 0.85f),
        )

        // 7. Styled Title Card: THUNDER
        layers += OGStages.text(
            id = "title",
            text = "THUNDER",
            size = 0.22f,
            color = "#E3F2FD",
            bold = true,
            style = OGStyleSpec(
                ops = listOf(
                    OGStyleOp(op = "subdivide", detail = 4),
                    OGStyleOp(op = "boil", amplitude = 0.02f, boilFps = 10f),
                ),
            ),
            opacity = OGStages.track(
                0L to 0f, 700L to 0f, 850L to 1f,
                (durationMs - 600L) to 1f, durationMs to 0f,
            ),
            x = thunderShakeX,
            y = thunderShakeY,
            scale = OGStages.eased(OGEasing.EASE_OUT, 700L to 1.18f, 950L to 1f),
        )

        return OGStage.of(
            *layers.toTypedArray(),
            width = width,
            height = height,
            durationMs = durationMs,
            fps = fps,
            background = "#020409",
        )
    }

    /**
     * Build an anime-grade **Earthquake Cataclysm** movie stage.
     */
    fun earthquakeStage(
        width: Int = 1600,
        height: Int = 900,
        durationMs: Long = 5000L,
        fps: Int = 30,
    ): OGStage {
        val layers = ArrayList<OGStageLayer>(12)

        // 1. Tectonic Canyon Backdrop
        val earthScene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(
                    points = listOf(OGPointSpec(0f, 0f), OGPointSpec(1f, 0f), OGPointSpec(1f, 0.65f), OGPointSpec(0f, 0.65f)),
                    label = "sky",
                    gradient = OGFillSpec(
                        type = "linear",
                        from = OGPointSpec(0.5f, 0f),
                        to = OGPointSpec(0.5f, 0.65f),
                        stops = listOf(OGStopSpec(0f, "#120B08"), OGStopSpec(1f, "#3E2723")),
                    ),
                ),
                OGPlacedShapeSpec(
                    points = listOf(OGPointSpec(0f, 0.60f), OGPointSpec(0.35f, 0.52f), OGPointSpec(0.7f, 0.58f), OGPointSpec(1f, 0.50f), OGPointSpec(1f, 1f), OGPointSpec(0f, 1f)),
                    label = "far_ridge",
                    fill = "#2B1A14",
                    smoothing = 0.6f,
                ),
                OGPlacedShapeSpec(
                    points = listOf(OGPointSpec(0f, 0.72f), OGPointSpec(0.45f, 0.68f), OGPointSpec(0.8f, 0.74f), OGPointSpec(1f, 0.70f), OGPointSpec(1f, 1f), OGPointSpec(0f, 1f)),
                    label = "near_ground",
                    fill = "#1A0F0A",
                    smoothing = 0.5f,
                ),
            ),
            background = "#120B08",
            version = 4,
        )

        // Two-phase seismic shake: P-wave micro-jitter (100–400ms) then S-wave rupture (400–1800ms).
        // ALL layers use the same shake tracks so the entire frame moves together like a real camera.
        val shakeX = OGStages.track(
            0L to 0f,
            // P-wave: quick high-frequency tremor before the rupture
            100L to 0.004f, 140L to -0.004f, 180L to 0.005f, 220L to -0.004f,
            260L to 0.005f, 300L to -0.003f, 340L to 0.004f, 380L to -0.003f,
            // S-wave: violent low-frequency jolt
            400L to 0.012f, 480L to -0.015f, 560L to 0.018f,
            640L to -0.016f, 740L to 0.014f, 860L to -0.010f, 1050L to 0.007f,
            1350L to -0.004f, 1800L to 0f,
        )
        val shakeY = OGStages.track(
            0L to 0f,
            // P-wave
            110L to -0.003f, 150L to 0.003f, 190L to -0.004f, 230L to 0.003f,
            270L to -0.003f, 310L to 0.004f, 350L to -0.003f, 390L to 0.003f,
            // S-wave
            420L to -0.018f, 500L to 0.022f, 580L to -0.019f,
            660L to 0.015f, 760L to -0.012f, 880L to 0.008f, 1100L to -0.005f,
            1400L to 0f,
        )

        layers += OGStages.scene(
            id = "earth_backdrop",
            scene = earthScene,
            x = shakeX,
            y = shakeY,
            scale = OGStages.eased(OGEasing.EASE_OUT, 0L to 1f, durationMs to 1.06f),
        )

        // 2. Magma fissure thermal underglow — amber glow beneath the crack line
        layers += OGStages.solid(
            id = "magma_glow",
            color = "#BF360C",
            opacity = OGStages.track(0L to 0f, 400L to 0f, 700L to 0.18f, 2500L to 0.22f, durationMs to 0f),
        ).copy(blend = OGBlendMode.PLUS)

        // 3. Propagating Ground Fractures (crack arms grow 400ms→1400ms) — shakes with the camera
        layers += OGStages.crack(
            id = "cracks",
            spec = OGCrack.GROUND,
            originX = 0.50f, originY = 0.72f,
            progress = OGStages.eased(OGEasing.EASE_OUT, 400L to 0f, 1400L to 1f),
            startMs = 400L,
            x = shakeX,
            y = shakeY,
            opacity = OGStages.track(400L to 0f, 550L to 1f, 3500L to 1f, durationMs to 0f),
        )

        // 4. Ground-roll Shockwave Oval (expands 400ms→1200ms) — shakes with the camera
        layers += OGStages.shockwave(
            id = "ground_roll",
            spec = OGShockwave.GROUND,
            originX = 0.50f, originY = 0.72f,
            progress = OGStages.eased(OGEasing.EASE_OUT, 400L to 0f, 1200L to 1f),
            startMs = 400L,
            x = shakeX,
            y = shakeY,
            opacity = OGStages.track(400L to 0.9f, 1200L to 0f),
        )

        // 5. Heavy Stone Debris — large chunks falling down (gravityY positive = downward)
        val debrisSpec = OGParticleSpec(
            name = "stone_debris",
            shape = "square",
            maxParticles = 80,
            burst = 60, emissionRate = 20f,
            lifetimeMs = 1800f,
            x = 0.50f, y = 0.72f, spawnRadius = 0.30f,
            angleDeg = 280f, spreadDeg = 80f,
            speed = 0.55f, speedJitter = 0.7f,
            gravityY = 0.50f, drag = 0.10f,
            startSize = 0.022f, endSize = 0.010f, sizeJitter = 0.7f,
            startColor = "#6D4C41", endColor = "#1A0F0A",
            startAlpha = 0.95f, endAlpha = 0f, seed = 77,
        )
        layers += OGStages.particles(
            id = "debris",
            spec = debrisSpec,
            startMs = 400L,
            opacity = OGStages.track(400L to 0f, 500L to 1f, 2000L to 1f, durationMs to 0f),
        ).copy(x = shakeX, y = shakeY)

        // 6. Fine Rising Rock Dust — lighter motes float upward
        val dustSpec = OGParticleSpec(
            name = "earth_dust",
            shape = "circle",
            maxParticles = 180,
            emissionRate = 65f,
            lifetimeMs = 2800f,
            x = 0.50f, y = 0.82f, spawnRadius = 0.40f,
            angleDeg = 270f, spreadDeg = 75f,
            speed = 0.16f, speedJitter = 0.6f,
            gravityY = -0.035f, drag = 0.25f,
            startSize = 0.018f, endSize = 0.035f, sizeJitter = 0.6f,
            startColor = "#D7CCC8", endColor = "#5D4037",
            startAlpha = 0.75f, endAlpha = 0f, seed = 88,
        )
        layers += OGStages.particles(
            id = "dust",
            spec = dustSpec,
            startMs = 400L,
            opacity = OGStages.track(400L to 0f, 650L to 1f, 3200L to 1f, durationMs to 0f),
        ).copy(x = shakeX, y = shakeY)

        // 7. Styled Title Card: EARTHQUAKE — shakes with the camera
        layers += OGStages.text(
            id = "title",
            text = "EARTHQUAKE",
            size = 0.18f,
            color = "#FFE082",
            bold = true,
            style = OGStyleSpec(
                ops = listOf(
                    OGStyleOp(op = "roughen", amplitude = 0.03f, detail = 4),
                    OGStyleOp(op = "boil", amplitude = 0.02f, boilFps = 12f),
                ),
            ),
            opacity = OGStages.track(
                0L to 0f, 500L to 0f, 700L to 1f,
                (durationMs - 600L) to 1f, durationMs to 0f,
            ),
            x = shakeX,
            y = shakeY,
            scale = OGStages.eased(OGEasing.EASE_OUT, 500L to 1.22f, 850L to 1f),
        )

        return OGStage.of(
            *layers.toTypedArray(),
            width = width,
            height = height,
            durationMs = durationMs,
            fps = fps,
            background = "#120B08",
        )
    }

    /**
     * Build an anime-grade **Flood Inundation & Surging Water** movie stage.
     */
    fun floodStage(
        width: Int = 1600,
        height: Int = 900,
        durationMs: Long = 5000L,
        fps: Int = 30,
    ): OGStage {
        val layers = ArrayList<OGStageLayer>(8)

        // Tidal perspective swell track (gentle vertical undulation of the waterline)
        val swellY = OGStages.track(
            0L to 0f, 1200L to 0.008f, 2500L to -0.006f, 3800L to 0.005f, durationMs to 0f,
        )

        // 1. Dark Gloomy Horizon Backdrop
        val floodSky = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(
                    points = listOf(OGPointSpec(0f, 0f), OGPointSpec(1f, 0f), OGPointSpec(1f, 0.65f), OGPointSpec(0f, 0.65f)),
                    label = "stormy_horizon",
                    gradient = OGFillSpec(
                        type = "linear",
                        from = OGPointSpec(0.5f, 0f),
                        to = OGPointSpec(0.5f, 0.65f),
                        stops = listOf(OGStopSpec(0f, "#06101E"), OGStopSpec(1f, "#102A45")),
                    ),
                ),
            ),
            background = "#06101E",
            version = 4,
        )
        layers += OGStages.scene(id = "horizon", scene = floodSky, y = swellY)

        // 2. Background deep wave — dark navy, slow, higher waterline. Enters from below, rises.
        // y animates from +0.35 (off-screen below) up to -0.15 (high water) over the stage duration.
        layers += OGStages.wave(
            id = "deep_wave",
            spec = OGWave.FLOOD.copy(
                surfaceY = 0.55f, amplitude = 0.07f, speed = 0.55f,
                deepColor = "#0D233A", shallowColor = "#1A4A72",
            ),
            startMs = 0L,
            y = OGStages.eased(OGEasing.EASE_IN_OUT, 0L to 0.35f, durationMs to -0.18f),
            opacity = OGStages.eased(OGEasing.EASE_OUT, 0L to 0f, 600L to 0.80f),
        )

        // 3. Foreground surge wave — brighter teal, faster, lower waterline. Also rises.
        layers += OGStages.wave(
            id = "surge",
            spec = OGWave.FLOOD.copy(surfaceY = 0.68f, amplitude = 0.11f, speed = 1.10f),
            startMs = 200L,
            y = OGStages.eased(OGEasing.EASE_IN_OUT, 200L to 0.20f, durationMs to -0.12f),
            opacity = OGStages.eased(OGEasing.EASE_OUT, 200L to 0f, 700L to 0.88f),
        )

        // 4. Submersion tint — deepens as the water rises (ocean-floor murk)
        layers += OGStages.solid(
            id = "submersion_tint",
            color = "#0D47A1",
            opacity = OGStages.eased(OGEasing.EASE_IN_OUT, 0L to 0f, durationMs to 0.28f),
        ).copy(blend = OGBlendMode.MULTIPLY)

        // 5. Whipped Foam Crest Spray Droplets
        layers += OGStages.particles(
            id = "spray",
            spec = OGParticles.DROPLETS.copy(
                maxParticles = 240, emissionRate = 95f,
                x = 0.45f, y = 0.62f, spawnRadius = 0.50f,
                angleDeg = 60f, spreadDeg = 45f,
                speed = 0.42f, speedJitter = 0.5f, gravityY = 0.40f,
                startColor = "#E1F5FE", endColor = "#81D4FA",
            ),
            opacity = OGStages.eased(OGEasing.EASE_OUT, 0L to 0f, 500L to 0.9f),
        )

        // 6. Driving Heavy Torrential Rain
        layers += OGStages.particles(
            id = "rain",
            spec = OGParticles.RAIN.copy(
                maxParticles = 400, emissionRate = 260f, speed = 1.4f, gravityY = 0.9f,
                angleDeg = 100f, spreadDeg = 10f, startAlpha = 0.75f,
            ),
        )

        // 7. Styled Title Card: FLOOD
        layers += OGStages.text(
            id = "title",
            text = "FLOOD",
            size = 0.22f,
            color = "#80D8FF",
            bold = true,
            style = OGStyleSpec(
                ops = listOf(
                    OGStyleOp(op = "wave", amplitude = 0.03f, waves = 4, speed = 1.8f),
                    OGStyleOp(op = "boil", amplitude = 0.015f, boilFps = 8f),
                ),
            ),
            opacity = OGStages.track(
                0L to 0f, 400L to 0f, 650L to 1f,
                (durationMs - 600L) to 1f, durationMs to 0f,
            ),
            scale = OGStages.eased(OGEasing.EASE_OUT, 400L to 1.15f, 750L to 1f),
        )

        return OGStage.of(
            *layers.toTypedArray(),
            width = width,
            height = height,
            durationMs = durationMs,
            fps = fps,
            background = "#06101E",
        )
    }

    // ── Drop-in Composable Event Views ────────────────────────────────────────────────────────────

    /**
     * Drop-in anime-grade **Thunderstorm Event** view.
     * Features ambient screen flash, forked lightning bolt, airburst shockwave, and rain.
     * When [targetX] and [targetY] are provided, the lightning bolt and anime slash ribbon
     * target that exact cell, exploding radial letters/sparks, scorching the tile, and lingering
     * with an afterimage ghost bolt.
     */
    @Composable
    fun Thunder(
        modifier: Modifier = Modifier,
        targetX: Float = 0.5f,
        targetY: Float = 0.5f,
        tileWidth: Float = 0.125f,
        tileHeight: Float = 0.125f,
        playing: Boolean = true,
        durationMs: Long = 3400L,
        seed: Int = 42,
        onDone: (() -> Unit)? = null,
    ) {
        val progress = remember(seed) { Animatable(0f) }
        var striking by remember(seed) { mutableStateOf(true) }
        var burning by remember(seed) { mutableStateOf(false) }
        var afterBoltPlaying by remember(seed) { mutableStateOf(false) }

        val cx = targetX
        val cy = targetY

        val tImpact = (180f / durationMs).coerceIn(0.03f, 0.20f)
        val tBurn = (980f / durationMs).coerceIn(0.20f, 0.40f)

        // IMPACT — main bolt: massive, blinding, branching down to the cell with needle-point taper and impact flash
        val mainBolt = remember(seed, cx, cy) {
            OGLightningSpec(
                x1 = cx + 0.015f, y1 = 0f, x2 = cx, y2 = cy,
                width = 0.032f, jaggedness = 0.22f, detail = 9, branches = 12, branchLength = 0.48f,
                color = "#81D4FA", coreColor = "#FFFFFF", glow = 3.5f, glowColor = "#00E5FF",
                strikeMs = 140f, decayMs = 560f, gapMs = 10_000f, loop = false, seed = seed,
                taper = true, flashRadius = 0.20f,
            )
        }

        // IMPACT — letters explode radially from the struck tile (on-brand for word game & action FX)
        val letterMeasurer = rememberTextMeasurer()
        val impactLetters = remember(seed) {
            val rng = Random(seed + 77)
            val pool = "AEIOURSTLNBCDFGM"
            List(12) { i ->
                val baseAngle = i * (360f / 12f)
                val angle = (baseAngle + rng.nextFloat() * 22f - 11f) * (PI.toFloat() / 180f)
                Triple(pool[rng.nextInt(pool.length)].toString(), angle, rng.nextFloat())
            }
        }
        val impactSparks = remember(seed, cx, cy) {
            OGParticles.SPARKS.copy(
                burst = 60, emissionRate = 0f, maxParticles = 60,
                x = cx, y = cy, spawnRadius = 0.012f,
                angleDeg = 270f, spreadDeg = 360f, speed = 1.05f, speedJitter = 0.78f,
                lifetimeMs = 780f, gravityY = 0.20f,
                startColor = "#FFF176", endColor = "#FF6F00", endAlpha = 0f, seed = seed,
            )
        }
        val impactSparkles = remember(seed, cx, cy) {
            OGParticles.SPARKLES.copy(
                burst = 22, emissionRate = 0f, maxParticles = 22,
                x = cx, y = cy, spawnRadius = 0.010f,
                speed = 0.38f, speedJitter = 0.72f,
                lifetimeMs = 520f, gravityY = 0.05f,
                startColor = "#FFFFFF", endColor = "#FFD600", endAlpha = 0f,
                spinDeg = 600f, spinJitter = 1f, seed = seed + 9,
            )
        }
        val preChargeSparks = remember(seed, cx) {
            OGParticles.EMBERS.copy(
                burst = 0, emissionRate = 22f, maxParticles = 30,
                x = cx, y = 0.88f, spawnRadius = 0.38f,
                angleDeg = 270f, spreadDeg = 55f,
                speed = 0.18f, speedJitter = 0.6f, gravityY = -0.08f,
                lifetimeMs = 900f, drag = 0.05f,
                startColor = "#FFD600", endColor = "#FFF9C4", endAlpha = 0f,
                startSize = 0.007f, endSize = 0.003f, sizeJitter = 0.5f, seed = seed + 41,
            )
        }
        val burnFlame = remember(seed) {
            OGFlameSpec(
                name = "tile_fire",
                coreColor = "#FFD24D",
                tipColor = "#FF3B10",
                tongues = 1,
                height = 0.72f,
                heightJitter = 0.15f,
                width = 0.44f,
                sway = 0.07f,
                swaySpeed = 1.2f,
                flicker = 0.35f,
                flickerSpeed = 2.5f,
                octaves = 3,
                opacity = 0.95f,
                seed = seed + 13,
            )
        }
        val afterBolt = remember(seed, cx, cy) {
            OGLightningSpec(
                x1 = cx + 0.02f, y1 = 0f, x2 = cx, y2 = cy,
                width = 0.012f, jaggedness = 0.20f, detail = 8, branches = 5, branchLength = 0.35f,
                color = "#FFF9C4", coreColor = "#FFF9C4", glow = 1.2f, glowColor = "#FFFDE7",
                strikeMs = 10f, decayMs = 480f, gapMs = 10_000f, loop = false, seed = seed + 3,
            )
        }
        val rainParticles = remember {
            OGParticles.RAIN.copy(maxParticles = 250, emissionRate = 180f, speed = 1.4f)
        }

        LaunchedEffect(seed, playing) {
            if (!playing) return@LaunchedEffect
            striking = true
            delay(980L)
            striking = false
            burning = true
            delay(200L)
            afterBoltPlaying = true
            delay((durationMs - 1180L).coerceAtLeast(0L))
            burning = false
            afterBoltPlaying = false
            onDone?.invoke()
        }
        LaunchedEffect(seed, playing) {
            if (!playing) return@LaunchedEffect
            progress.animateTo(1f, tween(durationMs.toInt(), easing = LinearEasing))
        }

        val p = progress.value

        // Seismic camera trauma at strike impact (quadratic punch)
        val impactP = nfSmooth(tImpact, tImpact + 0.16f, p)
        val impactShake = ((1f - impactP) * (if (p >= tImpact && p < tImpact + 0.20f) 1f else 0f)).coerceIn(0f, 1f)
        val shakeTrauma = impactShake * impactShake
        val shakeJoltX = sin(p * 320f) * 28f * shakeTrauma
        val shakeJoltY = cos(p * 260f) * 18f * shakeTrauma
        val shakeTilt = sin(p * 190f) * 3.5f * shakeTrauma

        Box(
            modifier.fillMaxSize().graphicsLayer {
                alpha = 1f - nfSmooth(0.92f, 1f, p)
                translationX = shakeJoltX
                translationY = shakeJoltY
                rotationZ = shakeTilt
            }.clipToBounds(),
        ) {
            // Torrential background rain
            OGParticleView(rainParticles, Modifier.fillMaxSize(), playing = playing)

            // PRE-IMPACT: sky floods golden
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = nfSmooth(0f, tImpact * 0.55f, p) *
                        (1f - nfSmooth(tImpact - 0.008f, tImpact + 0.002f, p)) * 0.12f
                }.background(Color(0xFFFFD600))
            )

            // IMPACT: ONE-FRAME pure-white freeze-frame flash
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = (nfSmooth(tImpact - 0.004f, tImpact + 0.002f, p) *
                        (1f - nfSmooth(tImpact + 0.008f, tImpact + 0.060f, p))).coerceIn(0f, 1f)
                }.background(Color(0xFFFFFFFF))
            )

            // BURN: faint warm orange wash as the tile blazes
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = nfSmooth(tBurn, tBurn + 0.10f, p) * 0.06f
                }.background(Color(0xFFFF8C00))
            )

            // Upward static pre-charge sparks
            OGParticleView(preChargeSparks, Modifier.fillMaxSize(), playing = p < tImpact + 0.04f)

            // IMPACT: lightning bolt + sparks + sparkles (pure fractal bolt, no thick worm ribbon)
            OGLightningView(mainBolt, Modifier.fillMaxSize(), playing = striking)
            OGParticleView(impactSparks, Modifier.fillMaxSize(), playing = striking)
            OGParticleView(impactSparkles, Modifier.fillMaxSize(), playing = striking)

            // Overexposure flash bloom
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = (nfSmooth(tImpact, tImpact + 50f / durationMs, p) *
                        (1f - nfSmooth(tImpact + 50f / durationMs, tImpact + 150f / durationMs, p)))
                        .coerceIn(0f, 1f)
                }.background(Color.White)
            )

            // Shockwave rings & radial letter bursts
            Canvas(Modifier.fillMaxSize()) {
                val ring1 = nfSmooth(tImpact, tImpact + 380f / durationMs, p)
                val ring2 = nfSmooth(tImpact, tImpact + 700f / durationMs, p)
                drawOGShockwaveFrame(OGShockwaveSpec(color = "#FFF59D", maxRadius = 0.22f, ringWidth = 0.018f, alpha = 0.95f), cx, cy, ring1)
                drawOGShockwaveFrame(OGShockwaveSpec(color = "#FFE082", maxRadius = 0.44f, ringWidth = 0.026f, alpha = 0.72f), cx, cy, ring2)

                // ── Struck Tile Electrocution & Scorching ─────────────────────────
                val strikeOccurred = p >= tImpact
                if (strikeOccurred) {
                    val tW = tileWidth * size.width
                    val tH = tileHeight * size.height
                    val tLeft = (cx - tileWidth / 2f) * size.width
                    val tTop = (cy - tileHeight / 2f) * size.height

                    // 1. Dielectric Ground Streamers: hairline fractal discharge arcs (no worms)
                    val groundArcP = nfSmooth(tImpact, tImpact + 0.18f, p)
                    if (groundArcP in 0.01f..0.98f) {
                        val arcFade = (1f - groundArcP) * (1f - groundArcP)
                        val arcRng = Random(seed + (p * 80).toInt())
                        val groundArms = 12
                        for (a in 0 until groundArms) {
                            val baseAngle = a * (2f * PI.toFloat() / groundArms) + (arcRng.nextFloat() - 0.5f) * 0.35f
                            val arcLen = size.minDimension * (0.12f + arcRng.nextFloat() * 0.16f) * groundArcP
                            val arcPath = Path().apply {
                                moveTo(cx * size.width, cy * size.height)
                                var currX = cx * size.width
                                var currY = cy * size.height
                                val segs = 6
                                for (s in 1..segs) {
                                    val frac = s.toFloat() / segs
                                    val angDev = (arcRng.nextFloat() - 0.5f) * 0.9f
                                    val r = arcLen * frac
                                    currX = cx * size.width + cos(baseAngle + angDev) * r
                                    currY = cy * size.height + sin(baseAngle + angDev) * r
                                    lineTo(currX, currY)
                                }
                            }
                            // Cyan bloom halo + razor white-hot needle core
                            drawPath(
                                arcPath,
                                color = Color(0xFF00E5FF).copy(alpha = arcFade * 0.85f),
                                style = Stroke(width = 2.8f, cap = StrokeCap.Round),
                                blendMode = BlendMode.Plus,
                            )
                            drawPath(
                                arcPath,
                                color = Color.White.copy(alpha = arcFade * 0.98f),
                                style = Stroke(width = 1.0f, cap = StrokeCap.Round),
                                blendMode = BlendMode.Plus,
                            )
                        }
                    }

                    // 2. Tile Body: White-hot impact -> Charred charcoal body -> Scorched ember glow
                    val scorchAlpha = (1f - nfSmooth(0.85f, 1f, p)).coerceIn(0f, 1f)
                    val tileJitterX = if (p < tImpact + 0.12f) (Random(seed + (p * 200).toInt()).nextFloat() - 0.5f) * 8f else 0f
                    val tileJitterY = if (p < tImpact + 0.12f) (Random(seed + 1 + (p * 200).toInt()).nextFloat() - 0.5f) * 8f else 0f

                    withTransform({
                        translate(tileJitterX, tileJitterY)
                    }) {
                        // Cast shadow beneath charred tile
                        drawRoundRect(
                            color = Color(0x99000000),
                            topLeft = Offset(tLeft + 4f, tTop + 6f),
                            size = Size(tW, tH),
                            cornerRadius = CornerRadius(8f),
                        )
                        // Charred black tile body
                        drawRoundRect(
                            color = Color(0xFF141414).copy(alpha = scorchAlpha),
                            topLeft = Offset(tLeft, tTop),
                            size = Size(tW, tH),
                            cornerRadius = CornerRadius(8f),
                        )
                        // Scorched ember underglow rim
                        drawRoundRect(
                            color = Color(0xFFFF6D00).copy(alpha = scorchAlpha * 0.75f),
                            topLeft = Offset(tLeft, tTop),
                            size = Size(tW, tH),
                            cornerRadius = CornerRadius(8f),
                            style = Stroke(width = 3.5f),
                        )


                        // Blinding white-hot flash over the tile immediately upon strike
                        val whiteFlashAlpha = (1f - nfSmooth(tImpact, tImpact + 0.08f, p)).coerceIn(0f, 1f)
                        if (whiteFlashAlpha > 0.01f) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = whiteFlashAlpha),
                                topLeft = Offset(tLeft, tTop),
                                size = Size(tW, tH),
                                cornerRadius = CornerRadius(8f),
                            )
                            // Radial plasma flare star
                            val flareR = tW * 1.8f * whiteFlashAlpha
                            drawCircle(Color(0xFF00E5FF).copy(alpha = whiteFlashAlpha * 0.8f), radius = flareR * 1.4f, center = Offset(cx * size.width, cy * size.height), blendMode = BlendMode.Plus)
                            drawCircle(Color.White.copy(alpha = whiteFlashAlpha), radius = flareR * 0.6f, center = Offset(cx * size.width, cy * size.height), blendMode = BlendMode.Plus)
                            // Diamond spikes
                            for (sp in 0..3) {
                                val ang = sp * (PI.toFloat() / 2f)
                                val sx = cos(ang) * flareR * 2.5f
                                val sy = sin(ang) * flareR * 2.5f
                                drawLine(Color.White.copy(alpha = whiteFlashAlpha), Offset(cx * size.width, cy * size.height), Offset(cx * size.width + sx, cy * size.height + sy), strokeWidth = 2f, blendMode = BlendMode.Plus)
                            }
                        }
                    }
                }

                val letterP = nfSmooth(tImpact, tImpact + 280f / durationMs, p)
                if (letterP > 0f) {
                    val letterAlpha = (1f - nfSmooth(tImpact + 70f / durationMs, tImpact + 280f / durationMs, p)).coerceIn(0f, 1f)
                    impactLetters.forEach { (letter, angle, jitter) ->
                        val dist = size.minDimension * (0.11f + jitter * 0.10f) * letterP
                        val lx = cx * size.width + cos(angle) * dist
                        val ly = cy * size.height + sin(angle) * dist
                        if (letterAlpha > 0.01f) {
                            val measured = letterMeasurer.measure(
                                text = letter,
                                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFF9C4)),
                            )
                            withTransform({
                                translate(lx, ly)
                                rotate(degrees = jitter * 220f * letterP, pivot = Offset.Zero)
                            }) {
                                drawText(
                                    textLayoutResult = measured,
                                    topLeft = Offset(-measured.size.width / 2f, -measured.size.height / 2f),
                                    alpha = letterAlpha,
                                )
                            }
                        }
                    }
                }
            }

            // BURN: afterimage ghost bolt
            OGLightningView(afterBolt, Modifier.fillMaxSize(), playing = afterBoltPlaying)

            // BURN: single flame tongue at the struck tile
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val tW = maxWidth * tileWidth
                val tH = maxHeight * tileHeight
                val flameW = tW * 1.5f
                val flameH = tH * 2.2f
                Box(
                    Modifier
                        .offset(x = maxWidth * cx - flameW / 2f, y = maxHeight * cy + tH * 0.45f - flameH)
                        .width(flameW).height(flameH)
                        .graphicsLayer { alpha = nfSmooth(tBurn, tBurn + 0.08f, p) * (1f - nfSmooth(0.80f, 0.95f, p)) },
                ) {
                    OGFlameView(burnFlame, Modifier.fillMaxSize(), playing = burning)
                }
            }
        }
    }

    /**
     * Drop-in anime-grade **Earthquake Event** view.
     * Features dual-phase seismic camera trauma (P/S-wave), sharp geological fractures
     * converging on [targetX, targetY], ground-roll oval shockwave, stone spires & debris particles,
     * a 3D isometric abyss pit opening directly under the tile, spring-driven tile swallow into the
     * molten core, and snap-shut scar with closing shudder.
     */
    @Composable
    fun Earthquake(
        modifier: Modifier = Modifier,
        targetX: Float = 0.5f,
        targetY: Float = 0.5f,
        tileWidth: Float = 0.125f,
        tileHeight: Float = 0.125f,
        playing: Boolean = true,
        durationMs: Long = 3500L,
        seed: Int = 100,
        onDone: (() -> Unit)? = null,
    ) {
        val progress = remember(seed) { Animatable(0f) }
        var rupturing by remember(seed) { mutableStateOf(false) }

        val cx = targetX
        val cy = targetY

        // Tectonic Brittle Faults (Deterministic shear fractures along geological stress axes)
        val faultRifts = remember(seed, cx, cy) {
            val rng = Random(seed)
            val segments = mutableListOf<TectonicFaultSeg>()
            // 4 primary fault corridors radiating outward from the chasm rim
            val corridorAngles = listOf(34f, 126f, 216f, 306f)

            corridorAngles.forEachIndexed { corridorIdx, baseAngle ->
                val angle = baseAngle + rng.nextFloat() * 16f - 8f
                val rad = angle * PI.toFloat() / 180f
                // Start from the outer rim of the chasm
                var curX = cx + cos(rad) * 0.075f
                var curY = cy + sin(rad) * 0.055f

                val segCount = 4 + (corridorIdx % 2)
                var wFrac = 0.015f

                for (s in 0 until segCount) {
                    val segLen = 0.065f + rng.nextFloat() * 0.035f
                    // Brittle shear deviation (±18° max, maintaining forward momentum)
                    val dev = rng.nextFloat() * 36f - 18f
                    val sRad = (angle + dev) * PI.toFloat() / 180f
                    val nextX = curX + cos(sRad) * segLen
                    val nextY = curY + sin(sRad) * segLen

                    val order = (s.toFloat() / segCount).coerceIn(0f, 1f)
                    segments += TectonicFaultSeg(Offset(curX, curY), Offset(nextX, nextY), wFrac, order)

                    // Sharp acute branch fork at step 1 or 2
                    if ((s == 1 || s == 2) && rng.nextFloat() < 0.75f) {
                        val branchSide = if (rng.nextBoolean()) 36f else -36f
                        val bRad = (angle + branchSide) * PI.toFloat() / 180f
                        val bLen = segLen * 0.70f
                        val bx2 = curX + cos(bRad) * bLen
                        val by2 = curY + sin(bRad) * bLen
                        segments += TectonicFaultSeg(Offset(curX, curY), Offset(bx2, by2), wFrac * 0.52f, order + 0.12f)
                    }

                    curX = nextX
                    curY = nextY
                    wFrac *= 0.65f // Sharp root-to-tip taper
                }
            }
            segments.toList()
        }

        val debris = remember(seed, cx, cy) {
            OGParticles.EMBERS.copy(
                burst = 80, emissionRate = 30f, maxParticles = 120,
                x = cx, y = cy, spawnRadius = 0.22f,
                angleDeg = 270f, spreadDeg = 360f, speed = 0.50f, speedJitter = 0.90f,
                lifetimeMs = 1500f, gravityY = 0.30f,
                startColor = "#8D6E63", endColor = "#1A0800", endAlpha = 0f, seed = seed,
            )
        }
        val stoneSpires = remember(seed, cx, cy) {
            OGParticles.EMBERS.copy(
                burst = 45, emissionRate = 18f, maxParticles = 80,
                x = cx, y = cy, spawnRadius = 0.14f,
                angleDeg = 270f, spreadDeg = 55f, speed = 0.82f, speedJitter = 0.60f,
                lifetimeMs = 700f, gravityY = 0.55f,
                startColor = "#9E9E9E", endColor = "#424242", endAlpha = 0f, seed = seed + 3,
            )
        }
        val dropSpring = remember { OGSpringSpec(stiffness = 320f, dampingRatio = 0.92f) }

        LaunchedEffect(seed, playing) {
            if (!playing) return@LaunchedEffect
            delay(400L)
            rupturing = true
        }
        LaunchedEffect(seed, playing) {
            if (!playing) return@LaunchedEffect
            progress.animateTo(1f, tween(durationMs.toInt(), easing = LinearEasing))
            rupturing = false
            onDone?.invoke()
        }

        Box(
            modifier.fillMaxSize().graphicsLayer {
                val p = progress.value
                alpha = nfSmooth(0f, 0.05f, p) * (1f - nfSmooth(0.86f, 1f, p))
                // Translational shake — reads as seismic jolt, not screen flicker
                val j1 = nfSmooth(0.005f, 0.012f, p) * (1f - nfSmooth(0.012f, 0.025f, p))
                val j2 = nfSmooth(0.050f, 0.057f, p) * (1f - nfSmooth(0.057f, 0.070f, p))
                val j3 = nfSmooth(0.092f, 0.099f, p) * (1f - nfSmooth(0.099f, 0.112f, p))
                val j4 = nfSmooth(0.870f, 0.875f, p) * (1f - nfSmooth(0.875f, 0.886f, p)) // closing snap shudder
                val jolt = (j1 + j2 + j3 + j4).coerceIn(0f, 1f)
                translationX = jolt * 18f * if ((p * 1000).toInt() % 2 == 0) 1f else -1f
                translationY = jolt * 9f
            }.clipToBounds(),
        ) {
            val p = progress.value

            Canvas(Modifier.fillMaxSize()) {
                val originX = cx * size.width
                val originY = cy * size.height
                val tW = tileWidth * size.width
                val tH = tileHeight * size.height
                val minSide = min(size.width, size.height)

                // 1. Tectonic Fault Cracks (Brittle fractures, tapered, no worms)
                val crackP = nfSmooth(0.10f, 0.42f, p)
                if (crackP > 0f) {
                    val shadowOff = Offset(2.5f, 3.5f)

                    for (seg in faultRifts) {
                        if (crackP < seg.revealOrder * 0.65f) continue
                        val localP = ((crackP - seg.revealOrder * 0.65f) / 0.32f).coerceIn(0f, 1f)
                        if (localP <= 0f) continue

                        val p1 = Offset(seg.start.x * size.width, seg.start.y * size.height)
                        val p2Full = Offset(seg.end.x * size.width, seg.end.y * size.height)
                        val p2 = Offset(
                            OGFxMath.lerp(p1.x, p2Full.x, localP),
                            OGFxMath.lerp(p1.y, p2Full.y, localP),
                        )
                        val stroke = (seg.widthFrac * minSide).coerceAtLeast(1.2f)

                        // A. Cast-shadow crevice underlay (3D relief)
                        drawLine(
                            color = Color(0xFF080301).copy(alpha = 0.95f),
                            start = p1 + shadowOff,
                            end = p2 + shadowOff,
                            strokeWidth = stroke * 1.8f,
                            cap = StrokeCap.Round,
                        )
                        // B. Dark earth crevice core
                        drawLine(
                            color = Color(0xFF1C0A04),
                            start = p1,
                            end = p2,
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
                        // C. Bright fractured stone lip along illuminated side
                        drawLine(
                            color = Color(0xFFEFEBE9).copy(alpha = 0.75f),
                            start = p1,
                            end = p2,
                            strokeWidth = (stroke * 0.35f).coerceAtLeast(1f),
                            cap = StrokeCap.Round,
                        )

                        // D. Active leading fracture tip spark while spreading
                        if (localP in 0.05f..0.98f && crackP < 0.95f) {
                            val sparkR = stroke * 1.5f
                            drawCircle(Color(0xFFFFD54F).copy(alpha = 0.6f), radius = sparkR * 1.8f, center = p2)
                            drawCircle(Color.White, radius = sparkR * 0.7f, center = p2)
                        }
                    }
                }

                // Ground-roll shockwave
                val ovalP = nfSmooth(0.11f, 0.36f, p)
                drawOGShockwaveFrame(OGShockwave.GROUND.copy(color = "#7A7A7A", maxRadius = 0.58f, squash = 0.50f, alpha = 0.80f), cx, cy, ovalP)

                // 2. 3D Tectonic Chasm (Deep 3D Abyss Perspective + Sheer Cliff Walls + Magma Core)
                val abyssOpen = nfSmooth(0.18f, 0.48f, p)
                val abyssClose = nfSmooth(0.88f, 0.96f, p)
                val abyssP = abyssOpen * (1f - abyssClose)
                if (abyssP > 0.01f) {
                    val abyssW = tW * 3.8f * abyssP
                    val abyssH = tH * 2.6f * abyssP
                    val vertexCount = 20

                    // 1. Surface Rim Vertices (The crater opening on the ground plane)
                    val rimPoints = List(vertexCount) { i ->
                        val theta = i * (2f * PI.toFloat() / vertexCount)
                        val jitter = 0.88f + 0.24f * OGFxMath.hash01(i, seed + 11)
                        Offset(
                            originX + cos(theta) * (abyssW * 0.5f) * jitter,
                            originY + sin(theta) * (abyssH * 0.5f) * jitter,
                        )
                    }

                    // 2. Deep Sunken Abyss Floor (Offset DOWN in +Y by perspective!)
                    val depthY = abyssH * 0.70f
                    val floorCenter = Offset(originX, originY + depthY)
                    val floorScale = 0.48f
                    val floorPoints = List(vertexCount) { i ->
                        val theta = i * (2f * PI.toFloat() / vertexCount)
                        val jitter = 0.85f + 0.28f * OGFxMath.hash01(i, seed + 29)
                        Offset(
                            floorCenter.x + cos(theta) * (abyssW * 0.5f * floorScale) * jitter,
                            floorCenter.y + sin(theta) * (abyssH * 0.5f * floorScale) * jitter,
                        )
                    }

                    // A. Sheer Vertical North Cliff Face (Connecting northern surface rim to sunken floor)
                    val cliffPath = Path().apply {
                        val northRim = rimPoints.subList(vertexCount / 2, vertexCount)
                        val northFloor = floorPoints.subList(vertexCount / 2, vertexCount)
                        moveTo(northRim.first().x, northRim.first().y)
                        for (pt in northRim) lineTo(pt.x, pt.y)
                        for (pt in northFloor.reversed()) lineTo(pt.x, pt.y)
                        close()
                    }
                    val cliffBrush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFF2E2E2E),
                            0.4f to Color(0xFF1A1A1A),
                            1f to Color(0xFF080808),
                        ),
                        startY = originY - abyssH * 0.5f,
                        endY = floorCenter.y,
                    )
                    drawPath(cliffPath, brush = cliffBrush)

                    // Vertical cliff rock striation drop-lines (scores the rock wall vertically)
                    for (i in vertexCount / 2 until vertexCount step 2) {
                        val rPt = rimPoints[i]
                        val fPt = floorPoints[i]
                        drawLine(
                            color = Color(0xFF000000).copy(alpha = 0.90f),
                            start = rPt,
                            end = fPt,
                            strokeWidth = 2.0f,
                            cap = StrokeCap.Round,
                        )
                    }

                    // B. Descending Strata Shelves inside the pit (stepped geological depth)
                    for (shelf in 1..4) {
                        val shelfFrac = shelf.toFloat() / 4f
                        val shelfY = originY + depthY * shelfFrac
                        val sScale = OGFxMath.lerp(1f, floorScale, shelfFrac)
                        val sPath = Path().apply {
                            for (i in 0 until vertexCount) {
                                val theta = i * (2f * PI.toFloat() / vertexCount)
                                val jitter = 0.86f + 0.25f * OGFxMath.hash01(i, seed + 10 * shelf)
                                val sx = originX + cos(theta) * (abyssW * 0.5f * sScale) * jitter
                                val sy = shelfY + sin(theta) * (abyssH * 0.5f * sScale) * jitter
                                if (i == 0) moveTo(sx, sy) else lineTo(sx, sy)
                            }
                            close()
                        }
                        val shelfColor = when (shelf) {
                            1 -> Color(0xFF3A3A3A) // dark slate
                            2 -> Color(0xFF262626) // charcoal
                            3 -> Color(0xFF161616) // near-black rock
                            else -> Color(0xFF050505) // void floor
                        }
                        drawPath(sPath, color = shelfColor)
                    }

                    // C. Cold void floor — a barely-visible cool-gray shimmer to suggest wet rock depth
                    val floorGlimmer = floorScale * abyssP * 0.55f
                    val voidW = abyssW * floorScale * 0.80f
                    val voidH = abyssH * floorScale * 0.60f
                    drawOval(
                        color = Color(0xFF1E2230).copy(alpha = floorGlimmer),
                        topLeft = Offset(floorCenter.x - voidW * 0.5f, floorCenter.y - voidH * 0.5f),
                        size = Size(voidW, voidH),
                    )
                    drawOval(
                        color = Color(0xFF000000).copy(alpha = abyssP * 0.98f),
                        topLeft = Offset(floorCenter.x - voidW * 0.38f, floorCenter.y - voidH * 0.38f),
                        size = Size(voidW * 0.76f, voidH * 0.76f),
                    )

                    // D. Overhanging South Rim Shadow & Lip Highlight
                    val southShadowPath = Path().apply {
                        val southRim = rimPoints.subList(0, vertexCount / 2 + 1)
                        moveTo(southRim.first().x, southRim.first().y)
                        for (pt in southRim) lineTo(pt.x, pt.y)
                        for (pt in southRim.reversed()) lineTo(pt.x, pt.y - abyssH * 0.22f)
                        close()
                    }
                    drawPath(southShadowPath, color = Color(0xD9020100))

                    // Illuminated South Rim Lip (Beveled rock edge facing the viewer)
                    val southRimPath = Path().apply {
                        val southRim = rimPoints.subList(0, vertexCount / 2 + 1)
                        moveTo(southRim.first().x, southRim.first().y)
                        for (pt in southRim) lineTo(pt.x, pt.y)
                    }
                    // Cool rock bevel edge (fractured stone rim catching ambient light)
                    drawPath(southRimPath, color = Color(0xFFB0B0B0).copy(alpha = abyssP * 0.88f),
                        style = Stroke(width = 4.5f, cap = StrokeCap.Round))
                    // Crisp white specular crack-glint
                    drawPath(southRimPath, color = Color(0xFFFFFFFF).copy(alpha = abyssP * 0.65f),
                        style = Stroke(width = 1.8f, cap = StrokeCap.Round))

                    // E. Shattered stone boulders / rubble slabs along the edge
                    for (i in 0 until 8) {
                        val ang = i * (PI.toFloat() / 4f) + 0.15f
                        val rx = originX + cos(ang) * (abyssW * 0.52f)
                        val ry = originY + sin(ang) * (abyssH * 0.52f)
                        val slabW = 12f + 6f * OGFxMath.hash01(i, seed + 3)
                        val slabH = 9f + 5f * OGFxMath.hash01(i, seed + 5)
                        drawRoundRect(
                            color = Color(0xFF1E1E1E),
                            topLeft = Offset(rx - slabW / 2f, ry - slabH / 2f),
                            size = Size(slabW, slabH),
                            cornerRadius = CornerRadius(2f),
                        )
                        drawRoundRect(
                            color = Color(0xFF404040),
                            topLeft = Offset(rx - slabW / 2f + 1f, ry - slabH / 2f + 1f),
                            size = Size(slabW - 2f, slabH - 2f),
                            cornerRadius = CornerRadius(1f),
                        )
                    }
                }

                // 3. Tile Swallowed into 3D Abyss (Fractures in two jagged pieces and plunges into magma)
                val dropStartP = 0.42f; val dropEndP = 0.82f
                val dropP = nfSmooth(dropStartP, dropEndP, p)
                if (dropP > 0f) {
                    val dropSec = dropP * (dropEndP - dropStartP) * durationMs / 1000f
                    val springDrop = OGSpring.valueAt(dropSpring, dropSec, 0f, 1f)
                    val scaleF = (1f - springDrop * 0.88f).coerceAtLeast(0f)
                    val drawnW = tW * scaleF
                    val drawnH = tH * scaleF
                    val tileAlpha = (1f - nfSmooth(0.70f, 1f, springDrop)).coerceIn(0f, 1f)
                    val descentY = springDrop * (tH * 1.8f)

                    if (drawnW > 1f) {
                        // Fragment 1 (Left jagged half tilts and tumbles down into the abyss)
                        withTransform({
                            translate(-14f * springDrop, descentY)
                            rotate(degrees = -32f * springDrop, pivot = Offset(originX - drawnW * 0.25f, originY + descentY))
                        }) {
                            drawRoundRect(
                                color = Color(0xFF140804).copy(alpha = tileAlpha * 0.5f),
                                topLeft = Offset(originX - drawnW / 2f + 4f, originY - drawnH / 2f + 6f),
                                size = Size(drawnW * 0.52f, drawnH),
                                cornerRadius = CornerRadius(6f * scaleF),
                            )
                            drawRoundRect(
                                color = Color(0xFFFAF7F0).copy(alpha = tileAlpha),
                                topLeft = Offset(originX - drawnW / 2f, originY - drawnH / 2f),
                                size = Size(drawnW * 0.52f, drawnH),
                                cornerRadius = CornerRadius(6f * scaleF),
                            )
                            drawRoundRect(
                                color = Color(0xFFFF5722).copy(alpha = tileAlpha * springDrop * 0.90f),
                                topLeft = Offset(originX - drawnW / 2f, originY - drawnH / 2f),
                                size = Size(drawnW * 0.52f, drawnH),
                                cornerRadius = CornerRadius(6f * scaleF),
                            )
                        }
                        // Fragment 2 (Right jagged half tilts opposite and drops deeper into magma)
                        withTransform({
                            translate(16f * springDrop, descentY + 12f * springDrop)
                            rotate(degrees = 42f * springDrop, pivot = Offset(originX + drawnW * 0.25f, originY + descentY))
                        }) {
                            drawRoundRect(
                                color = Color(0xFF140804).copy(alpha = tileAlpha * 0.5f),
                                topLeft = Offset(originX + drawnW * 0.05f + 4f, originY - drawnH / 2f + 6f),
                                size = Size(drawnW * 0.46f, drawnH),
                                cornerRadius = CornerRadius(6f * scaleF),
                            )
                            drawRoundRect(
                                color = Color(0xFFFAF7F0).copy(alpha = tileAlpha),
                                topLeft = Offset(originX + drawnW * 0.05f, originY - drawnH / 2f),
                                size = Size(drawnW * 0.46f, drawnH),
                                cornerRadius = CornerRadius(6f * scaleF),
                            )
                            drawRoundRect(
                                color = Color(0xFFFF5722).copy(alpha = tileAlpha * springDrop * 0.95f),
                                topLeft = Offset(originX + drawnW * 0.05f, originY - drawnH / 2f),
                                size = Size(drawnW * 0.46f, drawnH),
                                cornerRadius = CornerRadius(6f * scaleF),
                            )
                        }
                    }
                }
            }

            OGParticleView(debris, Modifier.fillMaxSize(), playing = playing)
            OGParticleView(stoneSpires, Modifier.fillMaxSize(), playing = rupturing)
        }
    }

    /**
     * Drop-in anime-grade **Flood Event** view.
     * Features a sweeping flood torrent from one side across to the other,
     * with an advancing inundation water sheet, surging Gerstner wave crest,
     * churning frothing breaker lip, wind-whipped spray motes,
     * Worthington capillary ripples, and tile washout into the depths.
     */
    @Composable
    fun Flood(
        modifier: Modifier = Modifier,
        targetX: Float = 0.5f,
        targetY: Float = 0.5f,
        tileWidth: Float = 0.125f,
        tileHeight: Float = 0.125f,
        playing: Boolean = true,
        durationMs: Long = 4200L,
        seed: Int = 42,
        onDone: (() -> Unit)? = null,
    ) {
        val progress = remember(seed) { Animatable(0f) }
        var sprayPlaying by remember(seed) { mutableStateOf(false) }

        val cx = targetX
        val cy = targetY

        val tileHitSweep = ((cx - tileWidth * 0.5f + 0.15f) / 1.50f).coerceIn(0.05f, 0.85f)
        val pHit = 0.08f + tileHitSweep * (0.82f - 0.08f)

        val waterMist = remember(seed) {
            OGFog.MIST.copy(
                color = "#29B6F6", density = 8, opacity = 0.07f, scale = 0.60f,
                driftX = 0.010f, driftY = 0.07f, turbulence = 0.10f, wanderSpeed = 0.25f, seed = seed,
            )
        }
        val rainParticles = remember {
            OGParticles.RAIN.copy(maxParticles = 250, emissionRate = 180f, speed = 1.3f)
        }
        val droplets = remember(seed) {
            OGParticles.DROPLETS.copy(
                burst = 30, emissionRate = 42f, maxParticles = 95,
                x = 0.5f, y = 0.0f, spawnRadius = 0.50f,
                angleDeg = 90f, spreadDeg = 140f, speed = 0.62f, speedJitter = 0.48f,
                lifetimeMs = 1200f, gravityY = 0.28f,
                startColor = "#4FC3F7", endColor = "#0277BD", endAlpha = 0f, seed = seed,
            )
        }
        val underwaterAurora = remember(seed) {
            OGAurora.DREAMY.copy(
                bottomColor = "#B3E5FC", midColor = "#4FC3F7", topColor = "#E1F5FE",
                opacity = 0.12f, speed = 0.85f, amplitude = 0.04f, curtainHeight = 1.0f,
                curtainY = 0.0f, columns = 11, seed = seed,
            )
        }
        // Rising water spray from below — surging upward from wave impact
        val floodSpray = remember(seed, cx) {
            OGParticles.DROPLETS.copy(
                burst = 0, emissionRate = 24f, maxParticles = 60,
                x = cx, y = 1.05f, spawnRadius = 0.48f,
                angleDeg = 270f, spreadDeg = 55f,
                speed = 0.22f, speedJitter = 0.70f, gravityY = -0.04f,
                lifetimeMs = 2200f, drag = 0.08f,
                startSize = 0.010f, endSize = 0.003f, sizeJitter = 0.6f,
                startColor = "#B3E5FC", endColor = "#4FC3F7", startAlpha = 0.65f, endAlpha = 0f,
                seed = seed + 31,
            )
        }

        // Ripples: capillary rings spawn as flood torrent sweeps through
        val rippleState = remember(seed) { OGRippleState() }
        val rippleSpec = remember(seed) {
            OGRipple.POND.copy(
                propagationSpeed = 0.30f, decayLambda = 2.4f, maxRadius = 0.58f,
                secondaryRings = 5, ringSpacing = 0.062f,
                color = "#81D4FA", crestColor = "#E1F5FE", lineWidth = 0.006f,
                squash = 0.68f, crownDroplets = false, seed = seed,
            )
        }

        val sweepSpring = remember { OGSpringSpec(stiffness = 160f, dampingRatio = 0.98f) }

        // Flood sequence: spray begins, wave sweeps across
        LaunchedEffect(seed, playing) {
            if (!playing) return@LaunchedEffect
            sprayPlaying = true
        }
        LaunchedEffect(seed, playing) {
            if (!playing) return@LaunchedEffect
            val hitMs = (pHit * durationMs).toLong().coerceAtLeast(200L)
            delay(hitMs)
            var nowMs = 0L; withFrameMillis { nowMs = it }
            rippleState.spawn(cx, cy, nowMs)
            rippleState.spawn(cx - 0.12f, cy + 0.08f, nowMs)
            val rng = Random(seed + 99)
            repeat(6) {
                delay(280L); withFrameMillis { nowMs = it }
                rippleState.spawn(rng.nextFloat() * 0.7f + 0.15f, rng.nextFloat() * 0.7f + 0.15f, nowMs)
            }
        }
        LaunchedEffect(seed, playing) {
            if (!playing) return@LaunchedEffect
            progress.animateTo(1f, tween(durationMs.toInt(), easing = LinearEasing))
            onDone?.invoke()
        }

        val p = progress.value

        // Tidal surge heave (camera undulates gently with the rising flood mass)
        val heaveY = sin(p * 5f * PI.toFloat()) * 9f * nfSmooth(0.08f, 0.40f, p) * (1f - nfSmooth(0.82f, 1f, p))

        Box(
            modifier.fillMaxSize().graphicsLayer {
                alpha = nfSmooth(0f, 0.05f, p) * (1f - nfSmooth(0.84f, 1f, p))
                translationY = heaveY
            }.clipToBounds(),
        ) {
            // Underwater aurora shimmer
            OGAuroraView(
                underwaterAurora,
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = nfSmooth(0.12f, 0.25f, p) * (1f - nfSmooth(0.72f, 0.88f, p))
                },
            )

            // Rising water spray from below
            OGParticleView(floodSpray, Modifier.fillMaxSize(), playing = sprayPlaying)

            // Canvas: Inundation water sheet, surging wave breaker, and the washed tile
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height

                // Wave progress: sweeps across from left to right (from -0.15 to 1.35)
                val floodSweep = nfSmooth(0.08f, 0.82f, p)
                val frontX = (-0.15f + floodSweep * 1.50f) * w

                if (frontX > -50f && frontX < w + 200f) {
                    val segments = 32

                    // Profile of the surging wave crest across the screen height
                    fun waveProfile(y: Float): Float {
                        val normY = y / h
                        return frontX +
                            sin(normY * 4f * PI.toFloat() + p * 12f) * (w * 0.035f) +
                            cos(normY * 7f * PI.toFloat() - p * 8f) * (w * 0.020f)
                    }

                    // A. Submerged Inundation Water Sheet (Covers the board tiles behind the wave)
                    val waterSheetPath = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(waveProfile(0f), 0f)
                        for (i in 1..segments) {
                            val y = (i.toFloat() / segments) * h
                            lineTo(waveProfile(y), y)
                        }
                        lineTo(0f, h)
                        close()
                    }
                    // Depth volumetric water gradient: dark navy trench -> deep oceanic azure -> tropical turquoise -> translucent shelf
                    val waterDepthBrush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xE60D233A),
                            0.65f to Color(0xCC01579B),
                            0.92f to Color(0xA600B0FF),
                            1f to Color(0x66E0F7FA),
                        ),
                        startX = 0f,
                        endX = frontX,
                    )
                    drawPath(waterSheetPath, brush = waterDepthBrush)

                    // B. Rising Cavitation Bubbles churning in the flooded volume
                    if (frontX > 10f) {
                        val bubbleRng = Random(seed + 19)
                        val bubbleCount = 18
                        for (b in 0 until bubbleCount) {
                            val bxFrac = bubbleRng.nextFloat()
                            val bx = bxFrac * frontX
                            val by = ((bubbleRng.nextFloat() + p * 0.65f + b * 0.11f) % 1f) * h
                            val br = 3.5f + bubbleRng.nextFloat() * 8.5f
                            val bAlpha = (nfSmooth(0.05f, 0.25f, bx / frontX) * (1f - nfSmooth(0.90f, 1f, bx / frontX))).coerceIn(0f, 1f)
                            if (bx > 5f && bAlpha > 0.05f) {
                                drawOGBubbleFrame(bx, by, br, tint = Color(0xFF81D4FA), alpha = bAlpha * 0.80f)
                            }
                        }
                    }

                    // C. Shimmering Caustic Light Ribbons on the flooded surface
                    val causticsCount = 6
                    for (c in 0 until causticsCount) {
                        val caustY = (c.toFloat() / causticsCount) * h + sin(p * 8f + c) * 15f
                        val caustPath = Path()
                        val caustEnd = (waveProfile(caustY) - 20f).coerceAtLeast(0f)
                        if (caustEnd > 10f) {
                            caustPath.moveTo(0f, caustY)
                            val cSteps = 12
                            for (ci in 1..cSteps) {
                                val cxVal = (ci.toFloat() / cSteps) * caustEnd
                                val cyVal = caustY + sin(cxVal * 0.04f + p * 10f + c) * 8f
                                caustPath.lineTo(cxVal, cyVal)
                            }
                            drawPath(
                                caustPath,
                                color = Color(0x28B3E5FC),
                                style = Stroke(width = 4f, cap = StrokeCap.Round),
                            )
                        }
                    }

                    // C. Frothing Wave Breaker Lip (Smooth rolling ocean wave crest with aerated foam)
                    val lipPath = Path().apply {
                        moveTo(waveProfile(0f), 0f)
                        for (i in 1..segments) {
                            val y = (i.toFloat() / segments) * h
                            lineTo(waveProfile(y), y)
                        }
                    }
                    drawPath(lipPath, color = Color(0x9980D8FF), style = Stroke(width = 24f, cap = StrokeCap.Round))
                    drawPath(lipPath, color = Color.White.copy(alpha = 0.90f), style = Stroke(width = 8f, cap = StrokeCap.Round))

                    // Organic Whitewater Foam Discs along the advancing wave lip
                    val foamRng = Random(seed + 41)
                    val crestFoamCount = 22
                    for (f in 0 until crestFoamCount) {
                        val fy = (f.toFloat() / crestFoamCount) * h + (foamRng.nextFloat() - 0.5f) * 20f
                        val fx = waveProfile(fy) + (foamRng.nextFloat() - 0.5f) * 18f
                        val fr = 6f + foamRng.nextFloat() * 12f
                        drawCircle(Color.White.copy(alpha = 0.80f), radius = fr, center = Offset(fx, fy))
                    }

                    // D. Wind-Whipped Spray Motes flying off the wave lip
                    val rng = Random(seed + (p * 80).toInt())
                    for (k in 0 until 18) {
                        val py = rng.nextFloat() * h
                        val px = waveProfile(py) + rng.nextFloat() * 40f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.80f),
                            radius = 2f + rng.nextFloat() * 2.5f,
                            center = Offset(px, py),
                        )
                    }
                }

                // Expanding water splash shockwave ring on surge impact
                val surgeP = nfSmooth(pHit, pHit + 0.28f, p)
                if (p >= pHit && surgeP < 0.98f) {
                    drawOGShockwaveFrame(
                        OGShockwave.SPLASH.copy(color = "#4FC3F7", maxRadius = 0.65f, ringWidth = 0.024f, squash = 0.58f, alpha = 0.80f),
                        cx, cy, surgeP,
                    )
                }
            }

            // Ripple rings from impact and droplet splashes
            OGRippleView(rippleState, rippleSpec, Modifier.fillMaxSize(), playing = playing)

            // Mist and driving torrential rain
            OGFogView(waterMist, Modifier.fillMaxSize())
            OGParticleView(droplets, Modifier.fillMaxSize(), playing = playing)
            OGParticleView(rainParticles, Modifier.fillMaxSize(), playing = playing)
        }
    }
}

// ── Shared Animation Math Helpers ─────────────────────────────────────────────────────────────

private data class TectonicFaultSeg(
    val start: Offset,
    val end: Offset,
    val widthFrac: Float,
    val revealOrder: Float,
)

private fun nfSmooth(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
    val u = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}
