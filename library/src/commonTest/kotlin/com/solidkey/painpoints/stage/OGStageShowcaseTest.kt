package com.solidkey.painpoints.stage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **Describe → playable movie, proven against real model output.** Each JSON below was authored by a
 * language model from a single one-line description, handed only the [OGStages.stagePrompt] contract
 * (plus the per-pack field lists) — no tools, no repair. These tests pin that the UNMODIFIED
 * [OGStages.decode] turns that raw model reply into a live [OGStage] the player can run: right layer
 * mix, live timeline, and every instant resolves without throwing. They also guard the model-friendly
 * decode tolerances the contract leans on (a bare-number transform track; the styled [OGStageContent.Text]
 * title card every movie ends on).
 *
 * If a future codec change breaks real model output, one of these fails — that is the point.
 */
class OGStageShowcaseTest {

    /** "A stormy night over the sea …" → scene (gradient sky + sea + moving wave crests) + a forked
     *  bolt + a screen flash + teardrop rain + a STORM title. Note the rain layer's `"opacity": 0.8`
     *  — a bare number, the common model shorthand for a constant track. */
    private val stormJson = """
        {"width":1600,"height":900,"durationMs":6000,"fps":30,"background":"#05070f","layers":[{"id":"sky","content":{"type":"scene","scene":{"version":4,"background":"#060a16","motion":{"duration":6,"mode":"reverse","easing":"easeInOut","background":{"from":"#060a16","to":"#0c1430"}},"shapes":[{"points":[{"x":0,"y":0},{"x":1,"y":0},{"x":1,"y":0.62},{"x":0,"y":0.62}],"label":"sky","gradient":{"type":"linear","from":{"x":0.5,"y":0},"to":{"x":0.5,"y":0.62},"stops":[{"at":0,"color":"#02040c"},{"at":0.6,"color":"#0a1430"},{"at":1,"color":"#16284f"}]}},{"points":[{"x":0,"y":0.6},{"x":1,"y":0.6},{"x":1,"y":1},{"x":0,"y":1}],"label":"sea","gradient":{"type":"linear","from":{"x":0.5,"y":0.6},"to":{"x":0.5,"y":1},"stops":[{"at":0,"color":"#0b1b34"},{"at":1,"color":"#030710"}]}},{"points":[{"x":0,"y":0.58},{"x":0.14,"y":0.62},{"x":0.3,"y":0.59},{"x":0.46,"y":0.63},{"x":0.62,"y":0.59},{"x":0.78,"y":0.63},{"x":0.92,"y":0.6},{"x":1,"y":0.62},{"x":1,"y":0.72},{"x":0,"y":0.72}],"label":"wavecrest1","fill":"#1a3357","opacity":0.75,"smoothing":0.9,"motion":{"duration":3.2,"mode":"reverse","easing":"easeInOut","translate":{"from":{"x":-0.03,"y":0},"to":{"x":0.03,"y":0.01}}}},{"points":[{"x":0,"y":0.74},{"x":0.18,"y":0.78},{"x":0.36,"y":0.75},{"x":0.54,"y":0.79},{"x":0.72,"y":0.75},{"x":0.88,"y":0.79},{"x":1,"y":0.76},{"x":1,"y":0.9},{"x":0,"y":0.9}],"label":"wavecrest2","fill":"#122641","opacity":0.85,"smoothing":0.9,"motion":{"duration":2.6,"mode":"reverse","easing":"easeInOut","translate":{"from":{"x":0.04,"y":0},"to":{"x":-0.04,"y":0.012}}}}]}}},{"id":"bolt","content":{"type":"lightning","lightning":{"x1":0.68,"y1":0.02,"x2":0.58,"y2":0.6,"width":0.013,"jaggedness":0.2,"detail":7,"branches":6,"branchLength":0.42,"color":"#8fb8ff","coreColor":"#eef4ff","glow":0.95,"strikeMs":120,"decayMs":520,"gapMs":1900,"loop":true,"seed":77}},"blend":"plus","opacity":{"default":0,"keyframes":[{"timeMs":0,"value":0,"easing":"LINEAR"},{"timeMs":900,"value":0,"easing":"EASE_IN"},{"timeMs":1100,"value":1,"easing":"EASE_OUT"},{"timeMs":6000,"value":1,"easing":"LINEAR"}]}},{"id":"flash","content":{"type":"solid","color":"#3a5a9a"},"blend":"screen","opacity":{"default":0,"keyframes":[{"timeMs":0,"value":0,"easing":"LINEAR"},{"timeMs":1080,"value":0,"easing":"EASE_IN"},{"timeMs":1120,"value":0.5,"easing":"EASE_OUT"},{"timeMs":1400,"value":0,"easing":"EASE_OUT"},{"timeMs":3700,"value":0,"easing":"EASE_IN"},{"timeMs":3760,"value":0.35,"easing":"EASE_OUT"},{"timeMs":4000,"value":0,"easing":"EASE_OUT"}]}},{"id":"rain","content":{"type":"particles","particles":{"maxParticles":900,"emissionRate":500,"burst":0,"lifetimeMs":1400,"x":0.5,"y":-0.05,"spawnRadius":0.6,"angleDeg":100,"spreadDeg":6,"speed":1.8,"speedJitter":0.3,"gravityX":0,"gravityY":1.2,"drag":0,"startSize":0.004,"endSize":0.004,"startColor":"#9fc4ff","endColor":"#4a6fae","startAlpha":0.55,"endAlpha":0.2,"spinDeg":0,"shape":"teardrop","seed":41}},"blend":"screen","opacity":0.8},{"id":"title","content":{"type":"text","text":"STORM","size":0.26,"color":"#dce8ff","bold":true,"family":null,"style":{"ops":[{"op":"roughen","amplitude":0.02,"detail":4}]}},"scale":{"default":1,"keyframes":[{"timeMs":1100,"value":2.4,"easing":"EASE_OUT"},{"timeMs":1600,"value":1,"easing":"EASE_OUT"}]},"opacity":{"default":0,"keyframes":[{"timeMs":1000,"value":0,"easing":"LINEAR"},{"timeMs":1200,"value":1,"easing":"EASE_OUT"},{"timeMs":5600,"value":1,"easing":"LINEAR"},{"timeMs":6000,"value":0,"easing":"EASE_IN"}]}}]}
    """.trimIndent()

    /** "Cherry blossoms drifting over pink hills at sunset …" → a day→dusk scene motion (sky + grade),
     *  a setting sun with its own region motion, parallax hills, two petal emitters, a hand-drawn
     *  HANAMI title. */
    private val hanamiJson = """
        {"width":1600,"height":900,"durationMs":6500,"fps":30,"background":"#f6c9bf","layers":[{"id":"sky","content":{"type":"scene","scene":{"version":4,"background":"#f7c8b4","motion":{"duration":6.5,"mode":"once","easing":"easeInOut","background":{"from":"#f7c8b4","to":"#3a2a5c"},"look":{"from":{"brightness":0.08,"saturation":1.1,"temperature":0.2},"to":{"brightness":-0.22,"saturation":0.85,"temperature":-0.25,"hue":-14}}},"shapes":[{"points":[{"x":0,"y":0},{"x":1,"y":0},{"x":1,"y":0.62},{"x":0,"y":0.62}],"label":"sky","gradient":{"type":"linear","from":{"x":0.5,"y":0},"to":{"x":0.5,"y":0.62},"stops":[{"at":0,"color":"#f9b7c6"},{"at":0.55,"color":"#f6c9bf"},{"at":1,"color":"#efd9c6"}]}},{"points":[{"x":0.72,"y":0.2},{"x":0.82,"y":0.16},{"x":0.9,"y":0.24},{"x":0.86,"y":0.34},{"x":0.74,"y":0.32}],"label":"sun","fill":"#ffe3cf","opacity":0.9,"smoothing":1,"motion":{"duration":6.5,"mode":"once","easing":"easeIn","translate":{"from":{"x":0,"y":0},"to":{"x":-0.04,"y":0.26}},"opacity":{"from":0.95,"to":0.25}}}]}}},{"id":"hills_back","content":{"type":"scene","scene":{"version":4,"shapes":[{"points":[{"x":0,"y":0.66},{"x":0.22,"y":0.56},{"x":0.46,"y":0.62},{"x":0.7,"y":0.54},{"x":1,"y":0.6},{"x":1,"y":1},{"x":0,"y":1}],"label":"hill_far","fill":"#e8a7b8","opacity":0.85,"smoothing":0.8}]}}},{"id":"hills_mid","content":{"type":"scene","scene":{"version":4,"shapes":[{"points":[{"x":0,"y":0.78},{"x":0.3,"y":0.68},{"x":0.58,"y":0.74},{"x":0.84,"y":0.66},{"x":1,"y":0.72},{"x":1,"y":1},{"x":0,"y":1}],"label":"hill_mid","fill":"#dd93ab","smoothing":0.85}]}}},{"id":"hills_front","content":{"type":"scene","scene":{"version":4,"shapes":[{"points":[{"x":0,"y":0.9},{"x":0.26,"y":0.82},{"x":0.52,"y":0.88},{"x":0.78,"y":0.8},{"x":1,"y":0.86},{"x":1,"y":1},{"x":0,"y":1}],"label":"hill_near","fill":"#c97b97","smoothing":0.9}]}}},{"id":"petals_far","content":{"type":"particles","particles":{"maxParticles":60,"emissionRate":10,"lifetimeMs":6000,"x":0.5,"y":-0.02,"spawnRadius":0.6,"angleDeg":110,"spreadDeg":40,"speed":0.05,"speedJitter":0.5,"gravityX":-0.01,"gravityY":0.02,"drag":0.1,"startSize":0.012,"endSize":0.01,"startColor":"#ffd7e6","endColor":"#f6b9cf","startAlpha":0.7,"endAlpha":0.5,"spinDeg":120,"shape":"petal","seed":11}},"opacity":{"default":0.8,"keyframes":[{"timeMs":0,"value":0.8,"easing":"LINEAR"},{"timeMs":6500,"value":0.55,"easing":"EASE_OUT"}]}},{"id":"petals_near","content":{"type":"particles","particles":{"maxParticles":90,"emissionRate":16,"lifetimeMs":5500,"x":0.5,"y":-0.02,"spawnRadius":0.6,"angleDeg":105,"spreadDeg":50,"speed":0.09,"speedJitter":0.6,"gravityX":-0.02,"gravityY":0.04,"drag":0.08,"startSize":0.03,"endSize":0.022,"startColor":"#ffe0ec","endColor":"#f3a9c6","startAlpha":0.95,"endAlpha":0.7,"spinDeg":200,"shape":"petal","seed":27}}},{"id":"title","content":{"type":"text","text":"HANAMI","size":0.2,"color":"#fff1f4","bold":true,"family":"serif","style":{"name":"handdrawn","ops":[{"op":"roughen","amplitude":0.03,"detail":4}]}},"y":{"default":-0.05},"opacity":{"default":0,"keyframes":[{"timeMs":0,"value":0,"easing":"EASE_OUT"},{"timeMs":900,"value":1,"easing":"EASE_OUT"},{"timeMs":5200,"value":1,"easing":"LINEAR"},{"timeMs":6400,"value":0.85,"easing":"EASE_IN"}]},"scale":{"default":1,"keyframes":[{"timeMs":0,"value":1.08,"easing":"EASE_OUT"},{"timeMs":1200,"value":1,"easing":"EASE_OUT"}]}}]}
    """.trimIndent()

    /** "A neon-city speed rush …" → two converging radial speed-line fields, two electric 'bolt'-edge
     *  slashes, a star-spark burst, and a boiling RUSH title that pops + rotates in. */
    private val rushJson = """
        {"width":1600,"height":900,"durationMs":5000,"fps":30,"background":"#05060a","layers":[{"id":"bg","content":{"type":"solid","color":"#070513"},"opacity":{"default":1,"keyframes":[{"timeMs":0,"value":0,"easing":"EASE_OUT"},{"timeMs":400,"value":1,"easing":"LINEAR"}]}},{"id":"lines","content":{"type":"speedlines","speedLines":{"mode":"radial","cx":0.5,"cy":0.5,"count":56,"color":"#19f6ff","innerRadius":0.28,"outerRadius":1.08,"thickness":0.011,"lengthJitter":0.55,"speed":1.6,"pulse":0.3,"alpha":0.85,"seed":7}},"blend":"plus","opacity":{"default":0.9,"keyframes":[{"timeMs":0,"value":0,"easing":"EASE_OUT"},{"timeMs":500,"value":0.95,"easing":"LINEAR"},{"timeMs":4600,"value":0.95,"easing":"LINEAR"},{"timeMs":5000,"value":0.4,"easing":"EASE_IN"}]},"scale":{"default":1,"keyframes":[{"timeMs":0,"value":1.25,"easing":"EASE_OUT"},{"timeMs":900,"value":1,"easing":"EASE_OUT"}]}},{"id":"lines2","content":{"type":"speedlines","speedLines":{"mode":"radial","cx":0.5,"cy":0.5,"count":40,"color":"#ff2bd6","innerRadius":0.34,"outerRadius":1.05,"thickness":0.008,"lengthJitter":0.7,"speed":2.1,"pulse":0.35,"alpha":0.7,"seed":23}},"blend":"plus","opacity":{"default":0.7,"keyframes":[{"timeMs":0,"value":0,"easing":"EASE_OUT"},{"timeMs":650,"value":0.75,"easing":"LINEAR"}]}},{"id":"slash","content":{"type":"slash","slash":{"path":[{"x":0.06,"y":0.82},{"x":0.42,"y":0.52},{"x":0.72,"y":0.34},{"x":0.96,"y":0.12}],"width":0.11,"taper":0.9,"peak":0.4,"smoothing":0.6,"samples":96,"colorStart":"#19f6ff","colorEnd":"#ff2bd6","glow":0.9,"glowColor":"#8affff","edge":"bolt","breatheAmp":0.1,"revealMs":420,"holdMs":2600,"fadeMs":700,"loop":true,"seed":11}},"blend":"plus","startMs":600,"opacity":{"default":1,"keyframes":[{"timeMs":600,"value":0,"easing":"EASE_OUT"},{"timeMs":780,"value":1,"easing":"LINEAR"}]}},{"id":"slash2","content":{"type":"slash","slash":{"path":[{"x":0.1,"y":0.2},{"x":0.4,"y":0.44},{"x":0.68,"y":0.64},{"x":0.94,"y":0.86}],"width":0.07,"taper":0.88,"peak":0.4,"smoothing":0.5,"samples":80,"colorStart":"#ff2bd6","colorEnd":"#19f6ff","glow":0.8,"glowColor":"#ff9bf0","edge":"bolt","revealMs":360,"holdMs":2200,"fadeMs":600,"loop":true,"seed":31}},"blend":"plus","startMs":1100,"opacity":{"default":0.85,"keyframes":[{"timeMs":1100,"value":0,"easing":"EASE_OUT"},{"timeMs":1260,"value":0.85,"easing":"LINEAR"}]}},{"id":"sparks","content":{"type":"particles","particles":{"maxParticles":120,"emissionRate":60,"burst":40,"lifetimeMs":900,"x":0.5,"y":0.5,"spawnRadius":0.05,"angleDeg":270,"spreadDeg":360,"speed":0.45,"gravityY":0.1,"startSize":0.012,"endSize":0,"startColor":"#baffff","endColor":"#ff2bd6","startAlpha":1,"endAlpha":0,"shape":"star","seed":5}},"blend":"plus","startMs":700},{"id":"title","content":{"type":"text","text":"RUSH","size":0.34,"color":"#eafcff","bold":true,"italic":true,"family":null,"style":{"name":"vibe","ops":[{"op":"boil","amplitude":0.02,"boilFps":9}]}},"opacity":{"default":1,"keyframes":[{"timeMs":0,"value":0,"easing":"EASE_OUT"},{"timeMs":850,"value":0,"easing":"LINEAR"},{"timeMs":1050,"value":1,"easing":"EASE_OUT"}]},"scale":{"default":1,"keyframes":[{"timeMs":850,"value":2.6,"easing":"EASE_OUT"},{"timeMs":1150,"value":0.92,"easing":"EASE_OUT"},{"timeMs":1320,"value":1,"easing":"EASE_IN_OUT"}]},"rotationDeg":{"default":0,"keyframes":[{"timeMs":850,"value":-6,"easing":"EASE_OUT"},{"timeMs":1300,"value":0,"easing":"EASE_OUT"}]}}]}
    """.trimIndent()

    /** All three decode, are live, end on a styled text title, and every frame resolves cleanly. */
    private fun assertPlayableMovie(json: String, expectedLayers: Int, title: String) {
        val stage = OGStages.decodeOrNull(json)
        assertNotNull(stage, "model JSON for $title should decode into a stage")
        assertEquals(expectedLayers, stage.layers.size, "$title layer count")
        assertTrue(stage.isLive, "$title should be a live movie")
        val text = stage.layers.mapNotNull { it.content as? OGStageContent.Text }.firstOrNull()
        assertNotNull(text, "$title should carry a styled text title card")
        assertEquals(title, text.text)
        // Every sampled instant across the timeline flattens without throwing.
        for (i in 0..8) {
            val t = stage.durationMs * i / 8
            stage.resolveAt(t)
        }
    }

    @Test
    fun stormMovieDecodesAndPlays() = assertPlayableMovie(stormJson, expectedLayers = 5, title = "STORM")

    @Test
    fun hanamiMovieDecodesAndPlays() = assertPlayableMovie(hanamiJson, expectedLayers = 7, title = "HANAMI")

    @Test
    fun rushMovieDecodesAndPlays() = assertPlayableMovie(rushJson, expectedLayers = 7, title = "RUSH")

    @Test
    fun bareNumberTransformTrackDecodesAsConstant() {
        // STORM's rain layer wrote `"opacity": 0.8` — a bare number, not a track object. The lenient
        // OGKeyframedFloat decoder must read it as a constant 0.8 track (the model-authorability win).
        val stage = OGStages.decode(stormJson)
        val rain = stage.layers.first { it.id == "rain" }
        assertEquals(0.8f, rain.opacity.valueAt(1234L), 1e-4f)
    }
}
