package com.solidkey.painpoints.ai

import java.io.File
import java.util.Random
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Scaling benchmark for the AI scene codec at a BIG triangle count — the "image made of a large number
 * of triangles" case. Pure CPU (JVM), so the numbers are machine-representative of the library's own
 * decode/sanitize/expand cost (GPU draw/fps is a separate device question). Writes a readable report to
 * /tmp/kmpmedia_bench.txt and asserts the whole thing completes in a sane budget so it can't silently
 * regress. Run: ./gradlew :library:testDebugUnitTest --tests '*OGSceneScaleBench*'
 */
class OGSceneScaleBench {

    private fun ms(nanos: Long) = "%.1f".format(nanos / 1_000_000.0)

    /** A scene of [n] random triangles, each with a flat fill (the dense-mesh "realism" path). */
    private fun randomMesh(n: Int, rng: Random): OGSceneSpec {
        val shapes = ArrayList<OGPlacedShapeSpec>(n)
        repeat(n) {
            val cx = rng.nextFloat(); val cy = rng.nextFloat()
            val pts = (0 until 3).map { OGPointSpec((cx + rng.nextFloat() * 0.02f).coerceIn(0f, 1f), (cy + rng.nextFloat() * 0.02f).coerceIn(0f, 1f)) }
            val col = "#%06X".format(rng.nextInt(0xFFFFFF))
            shapes.add(OGPlacedShapeSpec(points = pts, fill = col))
        }
        return OGSceneSpec(shapes = shapes, version = 3)
    }

    @Test
    fun benchmarkSceneCodecAtScale() {
        val sb = StringBuilder()
        sb.appendLine("KMPMedia AI scene codec — scaling benchmark (JVM, CPU-only)")
        sb.appendLine("=".repeat(64))

        var worstTotalMs = 0.0
        for (n in intArrayOf(5_000, 20_000, 50_000, 100_000)) {
            val rng = Random(42)
            val scene = randomMesh(n, rng)

            var json = ""
            val encodeNs = measureNanoTime { json = OGAiVector.encodeScene(scene) }
            var decoded: OGSceneSpec = OGSceneSpec()
            val decodeNs = measureNanoTime { decoded = OGAiVector.decodeScene(json) }
            var clean: OGSceneSpec = OGSceneSpec()
            val sanitizeNs = measureNanoTime { clean = decoded.sanitized() }
            val toShapesNs = measureNanoTime { clean.toShapes() }

            val totalMs = (encodeNs + decodeNs + sanitizeNs + toShapesNs) / 1_000_000.0
            worstTotalMs = maxOf(worstTotalMs, totalMs)
            sb.appendLine()
            sb.appendLine("${"%,d".format(n)} triangles  (${"%,d".format(json.length)} chars JSON, ${"%.1f".format(json.length / 1_048_576.0)} MB)")
            sb.appendLine("  encode   ${ms(encodeNs)} ms")
            sb.appendLine("  decode   ${ms(decodeNs)} ms   <- the hot path a device runs once")
            sb.appendLine("  sanitize ${ms(sanitizeNs)} ms")
            sb.appendLine("  toShapes ${ms(toShapesNs)} ms  (${clean.shapes.size} live OGPolygonShape)")
            sb.appendLine("  decode+sanitize+toShapes = ${"%.1f".format((decodeNs + sanitizeNs + toShapesNs) / 1_000_000.0)} ms")
        }

        // --- 1.39 fields at scale ---
        sb.appendLine()
        sb.appendLine("-".repeat(64))
        sb.appendLine("1.39 richer-stills fields at their caps:")

        // Scatter: one template stamped to the 400 cap, round-tripped through JSON.
        val scatterScene = OGSceneSpec(
            shapes = listOf(
                OGPlacedShapeSpec(
                    points = listOf(OGPointSpec(0.5f, 0.4f), OGPointSpec(0.55f, 0.6f), OGPointSpec(0.45f, 0.6f)),
                    fill = "#1E5A2A",
                    scatter = OGScatterSpec(count = 400, seed = 3),
                ),
            ),
            version = 3,
        )
        val sJson = OGAiVector.encodeScene(scatterScene)
        var expanded: OGSceneSpec = OGSceneSpec()
        val expandNs = measureNanoTime { expanded = OGAiVector.decodeScene(sJson).sanitized().expanded() }
        sb.appendLine("  scatter count=400: decode+expand = ${ms(expandNs)} ms -> ${expanded.shapes.size} shapes")

        // Texture: the densest grid (120x120 = 14,400 strokes), the per-region mark field.
        val tex = OGTextureSpec(density = 120, seed = 7).sanitized()
        var marks = 0
        val marksNs = measureNanoTime { marks = tex.marks().size }
        sb.appendLine("  texture density=120: marks() = ${ms(marksNs)} ms -> $marks strokes")

        // Glow: the pass plan is trivial maths.
        val glowNs = measureNanoTime { repeat(1000) { OGGlowSpec(passes = 8).glowPasses() } }
        sb.appendLine("  glow glowPasses() x1000 = ${ms(glowNs)} ms")

        sb.appendLine()
        sb.appendLine("Worst single-scene codec total (100k tris): ${"%.1f".format(worstTotalMs)} ms")

        val report = sb.toString()
        println(report)
        runCatching { File("/tmp/kmpmedia_bench.txt").writeText(report) }

        // Sanity floor: even 100k triangles must round-trip well under 10s on CI-class CPU.
        assertTrue(worstTotalMs < 10_000, "codec at 100k tris too slow: ${worstTotalMs}ms")
    }
}
