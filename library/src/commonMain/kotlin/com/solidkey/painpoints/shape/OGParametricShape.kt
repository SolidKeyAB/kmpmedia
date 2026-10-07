package com.solidkey.painpoints.shape

import com.solidkey.painpoints.ai.OGAiVector
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * **Parametric shapes** — pure, deterministic generators that turn a handful of numbers into a
 * closed outline (a `List<OGPoint>` in normalized `0..1` space, centred in the box with radius up
 * to `0.5`). They are the generative sibling of [OGPolygonShape]'s hand-/AI-drawn lasso: instead of
 * supplying vertices, you describe a *family* — a 7-point star, a 12-tooth gear, a 6-petal flower, a
 * squircle, an organic blob — and get the vertices back. Wrap the result in an [OGPolygonShape]
 * (optionally with `smoothing`) for any `clipShape`, feed it to a style pipeline, or use it as a
 * particle shape.
 *
 * Everything here is pure maths (no RNG at call time — the blob's lumpiness is a deterministic hash
 * of its `seed`), so a given spec yields the **same** outline on Android, iOS, preview and export,
 * and every generator is directly unit-testable. Generation happens once (when you build the shape),
 * never per frame, so it is free on the 60fps clip path — the same perf gate the rest of the library
 * clears.
 */
object OGParametric {

    private const val DEG = (PI / 180.0).toFloat()
    private const val TWO_PI = (2.0 * PI).toFloat()

    private fun polar(r: Float, angleDeg: Float): OGPoint =
        OGPoint(0.5f + r * cos(angleDeg * DEG), 0.5f + r * sin(angleDeg * DEG))

    /**
     * A regular n-gon with [sides] (≥3) vertices, pointing up by default (first vertex at the top);
     * [rotationDeg] spins it. `sides = 3` is a triangle, `6` a hexagon, and so on.
     */
    fun regularPolygon(sides: Int, rotationDeg: Float = 0f): List<OGPoint> {
        val n = sides.coerceAtLeast(3)
        val base = -90f + rotationDeg
        return List(n) { i -> polar(0.5f, base + 360f * i / n) }
    }

    /**
     * A [points]-pointed star (≥2). [innerRatio] (`0..1`) is the valley radius as a fraction of the
     * tip radius — small = spiky, near `1` = almost a polygon. Points up by default.
     */
    fun star(points: Int, innerRatio: Float = 0.5f, rotationDeg: Float = 0f): List<OGPoint> {
        val p = points.coerceAtLeast(2)
        val inner = innerRatio.coerceIn(0.01f, 1f) * 0.5f
        val base = -90f + rotationDeg
        return List(2 * p) { i ->
            val r = if (i % 2 == 0) 0.5f else inner
            polar(r, base + 180f * i / p)
        }
    }

    /**
     * A cog/gear with [teeth] (≥3) square teeth. [depth] (`0..0.9`) is how far each valley cuts in
     * as a fraction of the radius. Four vertices per tooth give the blocky, mechanical profile.
     */
    fun gear(teeth: Int, depth: Float = 0.3f, rotationDeg: Float = 0f): List<OGPoint> {
        val t = teeth.coerceAtLeast(3)
        val outer = 0.5f
        val inner = outer * (1f - depth.coerceIn(0f, 0.9f))
        val base = -90f + rotationDeg
        val step = 360f / t
        val out = ArrayList<OGPoint>(t * 4)
        for (i in 0 until t) {
            val a = base + step * i
            out.add(polar(outer, a))                 // tooth tip, leading edge
            out.add(polar(outer, a + step * 0.5f))   // tooth tip, trailing edge
            out.add(polar(inner, a + step * 0.5f))   // drop into the valley
            out.add(polar(inner, a + step))          // valley floor up to the next tooth
        }
        return out
    }

    /**
     * A [petals]-petal flower (rose): the radius swells to the tip at each petal and dips to
     * `1 - depth` in the valleys, sampled smoothly with [samples] points around. [depth] `0..1`.
     */
    fun flower(petals: Int, depth: Float = 0.4f, samples: Int = 64, rotationDeg: Float = 0f): List<OGPoint> {
        val k = petals.coerceAtLeast(2)
        val d = depth.coerceIn(0f, 1f)
        val s = samples.coerceAtLeast(k * 4)
        return List(s) { i ->
            val th = 360f * i / s
            val c = cos(k * th * DEG)
            val r = 0.5f * (1f - d * 0.5f * (1f - c))
            polar(r, th + rotationDeg)
        }
    }

    /**
     * A superellipse (Lamé curve) with exponent [exponent] (`> 0`): `2` is a circle, large values
     * approach a rounded square (a "squircle"), and values below `1` pinch into a 4-point star.
     * Sampled with [samples] points.
     */
    fun superellipse(exponent: Float = 4f, samples: Int = 64, rotationDeg: Float = 0f): List<OGPoint> {
        val n = exponent.coerceAtLeast(0.1f).toDouble()
        val s = samples.coerceAtLeast(8)
        val rot = rotationDeg * DEG
        return List(s) { i ->
            val th = TWO_PI * i / s
            val ct = cos(th.toDouble())
            val st = sin(th.toDouble())
            // |x|^n + |y|^n = 1, parametrized so the curve is smooth and closed.
            val ex = 0.5 * sign(ct) * abs(ct).pow(2.0 / n)
            val ey = 0.5 * sign(st) * abs(st).pow(2.0 / n)
            // Rotate in the box and recentre.
            val rx = ex * cos(rot.toDouble()) - ey * sin(rot.toDouble())
            val ry = ex * sin(rot.toDouble()) + ey * cos(rot.toDouble())
            OGPoint((0.5 + rx).toFloat(), (0.5 + ry).toFloat())
        }
    }

    /**
     * An organic **blob**: [lobes] (≥3) control radii placed evenly around the circle, each pulled
     * inward by up to [irregularity] (`0..1`) by a deterministic hash of [seed], then interpolated
     * smoothly (cosine) with [samples] points for a lumpy closed curve. A different [seed] is a
     * different blob; the same seed is identical everywhere.
     */
    fun blob(lobes: Int, irregularity: Float = 0.4f, seed: Int = 0, samples: Int = 64): List<OGPoint> {
        val L = lobes.coerceAtLeast(3)
        val irr = irregularity.coerceIn(0f, 1f)
        val s = samples.coerceAtLeast(L * 4)
        // Control radius at each lobe: 0.5 pulled in by up to irr*0.5 via a stable hash in [0,1].
        val ctrl = FloatArray(L) { j -> 0.5f * (1f - irr * 0.5f * hashUnit(j, seed)) }
        return List(s) { i ->
            val f = i.toFloat() / s * L            // which lobe segment we're in
            val j = f.toInt() % L
            val t = f - f.toInt()
            // Cosine interpolation between control radii for a smooth, seamless loop.
            val smoothT = (1f - cos(t * PI.toFloat())) * 0.5f
            val r = ctrl[j] + (ctrl[(j + 1) % L] - ctrl[j]) * smoothT
            polar(r, 360f * i / s)
        }
    }

    /** Deterministic pseudo-random value in `[0,1]` from an integer hash (matches OGStyleOps' noise). */
    private fun hashUnit(i: Int, seed: Int): Float {
        var h = i * 374761393 + seed * 1274126177 + 0x9E3779B1.toInt()
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535f
    }

    private fun sign(v: Double): Double = if (v >= 0.0) 1.0 else -1.0
}

/**
 * A **data-defined parametric shape**: name a [kind] and its parameters, call [toPoints] for the
 * outline or [toShape] for a ready `clipShape`. Serializable (all params flat + optional), so a
 * designer or a language model can author and share a shape as plain JSON — the generative twin of
 * [OGPolygonShape]'s point list and [com.solidkey.painpoints.style.OGStyleSpec]'s style pack. Decode
 * / encode via [OGParametrics].
 *
 * [count] is the primary integer for the family (polygon sides, star points, gear teeth, flower
 * petals, blob lobes); the other fields apply to the kinds that read them (see each generator).
 */
@Serializable
data class OGParametricSpec(
    /** `polygon` · `star` · `gear` · `flower` · `superellipse` · `blob`. Case-insensitive. */
    val kind: String,
    /** polygon sides / star points / gear teeth / flower petals / blob lobes (≥ its family's floor). */
    val count: Int = 5,
    /** `star`: valley radius as a fraction of the tip radius (`0..1`). */
    val innerRatio: Float = 0.5f,
    /** `gear`/`flower`: how deep the valleys cut in (`0..1`). */
    val depth: Float = 0.3f,
    /** `superellipse`: the Lamé exponent (`2` = circle, big = squircle, `<1` = 4-point star). */
    val exponent: Float = 4f,
    /** `blob`: how far the lobes pull inward (`0..1`). */
    val irregularity: Float = 0.4f,
    /** spin the shape, in degrees. */
    val rotationDeg: Float = 0f,
    /** `blob`: vary for a different random blob from the same inputs. */
    val seed: Int = 0,
    /** sample count for the continuous kinds (`flower`/`superellipse`/`blob`). */
    val samples: Int = 64,
) {
    /** The generated outline in normalized `0..1` space (empty for an unknown [kind]). */
    fun toPoints(): List<OGPoint> = when (kind.lowercase()) {
        "polygon", "ngon" -> OGParametric.regularPolygon(count, rotationDeg)
        "star" -> OGParametric.star(count, innerRatio, rotationDeg)
        "gear", "cog" -> OGParametric.gear(count, depth, rotationDeg)
        "flower", "rose" -> OGParametric.flower(count, depth, samples, rotationDeg)
        "superellipse", "squircle" -> OGParametric.superellipse(exponent, samples, rotationDeg)
        "blob" -> OGParametric.blob(count, irregularity, seed, samples)
        else -> emptyList()
    }

    /** Build an [OGPolygonShape] (optionally rounded by [smoothing]) for any `clipShape`. */
    fun toShape(smoothing: Float = 0f): OGPolygonShape = OGPolygonShape(toPoints(), smoothing)
}

/**
 * The **codec** for [OGParametricSpec] — decode a JSON shape (designer- or model-authored) into a
 * live outline / [OGPolygonShape], and encode one back. Mirrors
 * [com.solidkey.painpoints.style.OGStyles] / [OGAiVector]: reuses the same lenient JSON config and
 * tolerant extraction, so a reply wrapped in code fences or prose still parses. No network, no AI
 * SDK — just the contract and the parse/serialize.
 */
object OGParametrics {
    /** The shared lenient JSON config (reused from [OGAiVector] so every data layer agrees). */
    val json get() = OGAiVector.json

    /** Decode a JSON parametric shape into its [OGParametricSpec]. Throws on malformed input. */
    fun decodeSpec(text: String): OGParametricSpec =
        json.decodeFromString(OGParametricSpec.serializer(), OGAiVector.extractJson(text))

    /** Decode straight into an [OGPolygonShape]. Throws on malformed input. */
    fun decode(text: String, smoothing: Float = 0f): OGPolygonShape = decodeSpec(text).toShape(smoothing)

    /** [decode] but returns `null` instead of throwing on malformed input. */
    fun decodeOrNull(text: String, smoothing: Float = 0f): OGPolygonShape? =
        runCatching { decode(text, smoothing) }.getOrNull()

    /** Serialize a parametric shape to JSON (compact; defaults omitted). */
    fun encode(spec: OGParametricSpec): String = json.encodeToString(OGParametricSpec.serializer(), spec)

    /**
     * Build a ready-to-send instruction constraining a model to output exactly the parametric-shape
     * JSON [decode] expects. Wire the returned string into your model; pass the reply to [decode].
     */
    fun shapePrompt(instruction: String): String = """
        You output ONLY a JSON object describing a parametric shape for the KMPMedia library.

        Fields (use the ones your kind needs; omit the rest):
        - "kind": one of "polygon" | "star" | "gear" | "flower" | "superellipse" | "blob"
        - "count": sides (polygon) / points (star) / teeth (gear) / petals (flower) / lobes (blob)
        - "innerRatio": star valley/tip ratio, 0..1
        - "depth": gear or flower valley depth, 0..1
        - "exponent": superellipse Lamé exponent (2 = circle, 8 = squircle, 0.6 = 4-point star)
        - "irregularity": blob lumpiness, 0..1    - "seed": integer, varies a blob
        - "rotationDeg": spin in degrees

        Schema: {"kind":"...", ...}
        Examples:
        {"kind":"star","count":7,"innerRatio":0.45}
        {"kind":"gear","count":12,"depth":0.3}
        {"kind":"blob","count":6,"irregularity":0.5,"seed":3}

        Output the JSON only, with no prose and no markdown fences.

        Task: $instruction
    """.trimIndent()
}
