package com.solidkey.painpoints.stage

import com.solidkey.painpoints.ai.OGSceneIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OGRigRecipeTest {

    // ── Codec (same forward-compatible contract as the other specs) ──────────────────────────────

    @Test
    fun recipeRoundTrips() {
        for (r in OGRigRecipes.builtIns) {
            val back = OGRigRecipes.decode(OGRigRecipes.encode(r))
            assertEquals(r, back, "recipe '${r.kind}' should survive encode → decode unchanged")
        }
    }

    @Test
    fun decodeToleratesFencesAndRejectsGarbage() {
        val encoded = OGRigRecipes.encode(OGRigRecipes.builtIns.first())
        val fenced = "Sure, here is your recipe:\n```json\n$encoded\n```"
        assertNotNull(OGRigRecipes.decodeOrNull(fenced), "should lift the JSON out of prose + fences")
        assertNull(OGRigRecipes.decodeOrNull("not json at all {{{"), "garbage decodes to null")
    }

    // ── Selection by description ─────────────────────────────────────────────────────────────────

    @Test
    fun selectMatchesSubjectWordsByKind() {
        assertEquals("quadruped", OGRigRecipes.select("a smiling cat, front facing")?.kind)
        assertEquals("humanoid", OGRigRecipes.select("a person waving hello")?.kind)
        assertEquals("bird", OGRigRecipes.select("a little owl on a branch")?.kind)
        assertEquals("flower", OGRigRecipes.select("a pink sunflower in a pot")?.kind)
        assertEquals("game-piece", OGRigRecipes.select("a chess pawn")?.kind)
    }

    @Test
    fun selectMatchesTheNewerFamilies() {
        assertEquals("fish", OGRigRecipes.select("a little goldfish")?.kind)
        assertEquals("fish", OGRigRecipes.select("a blue pet fish")?.kind, "a narrower fish wins over the broad 'pet'")
        assertEquals("butterfly", OGRigRecipes.select("a pink butterfly")?.kind)
        assertEquals("butterfly", OGRigRecipes.select("a little moth")?.kind)
        assertEquals("quadruped", OGRigRecipes.select("a sleepy leopard")?.kind)
        // The offline floor stays HONEST: a subject it has no faithful schematic for (a sprawling
        // reptile, a generic "bug") returns null, so the director falls back to mood rather than a
        // wrong figure — the LLM authoring path still draws these via the anatomy rules in stagePrompt.
        assertNull(OGRigRecipes.select("a green crocodile"))
        assertNull(OGRigRecipes.select("a tiny bug"))
    }

    @Test
    fun fishWagPartsHingeRatherThanFloatOnTheBody() {
        val fish = OGRigRecipes.builtIns.first { it.kind == "fish" }
        val animated = OGSceneIdle.animate(fish.assemble())
        // Every sway part must find a real contact joint and hinge there; demoting to a vertical
        // "float" (a non-null translate) is the dishonest motion this whole system exists to avoid —
        // the dorsal fin in particular only touches the body along an EDGE, not at a vertex.
        for (label in listOf("tail", "dorsal", "belly", "fin_side")) {
            val m = animated.shapes.first { it.label == label }.motion
            assertNotNull(m, "fish part '$label' should carry a sway motion")
            assertNull(m.translate, "fish part '$label' should hinge at its root, not demote to a float")
        }
    }

    @Test
    fun selectPicksTheEarliestMentionedSubject() {
        // Salience, not catalog order: the subject named FIRST wins, whichever kind it is.
        assertEquals("quadruped", OGRigRecipes.select("a cat chasing a butterfly")?.kind)
        assertEquals("butterfly", OGRigRecipes.select("a butterfly landing on a cat")?.kind)
        assertEquals("quadruped", OGRigRecipes.select("a bear catching a salmon")?.kind)
        assertEquals("fish", OGRigRecipes.select("a salmon leaping past a bear")?.kind)
    }

    @Test
    fun specificSubjectBeatsAGenericCatchAll() {
        // A broad catch-all ("pet"/"animal"/"creature") is demoted, so a species named ANYWHERE wins…
        assertEquals("fish", OGRigRecipes.select("a blue pet fish")?.kind)
        assertEquals("bird", OGRigRecipes.select("a pet parrot")?.kind)
        // …but a bare catch-all with no species still resolves to the closest figure, not null.
        assertEquals("quadruped", OGRigRecipes.select("a cute pet")?.kind)
        assertEquals("quadruped", OGRigRecipes.select("a strange creature")?.kind)
    }

    @Test
    fun selectReturnsNullForPureAtmosphere() {
        assertNull(OGRigRecipes.select("a stormy sea at night"))
        assertNull(OGRigRecipes.select("a calm neon city dawn"))
    }

    // ── assemble: a living, labelled scene ───────────────────────────────────────────────────────

    @Test
    fun assembleBuildsOneRegionPerPartWithMotionVerbs() {
        val cat = OGRigRecipes.builtIns.first { it.kind == "quadruped" }
        val scene = cat.assemble()
        assertEquals(cat.parts.size, scene.shapes.size, "one region per part")
        assertNotNull(scene.shapes.firstOrNull { it.label == "body" }?.motion, "body breathes")
        assertNotNull(scene.shapes.firstOrNull { it.label == "tail" }?.motion, "tail sways")
        assertNotNull(scene.shapes.firstOrNull { it.label == "eye_l" }?.motion, "eyes blink")
        assertNull(scene.background, "a figure is transparent so a backdrop shows through")
    }

    @Test
    fun assembleKeepsEveryVertexInsideTheFrame() {
        for (r in OGRigRecipes.builtIns) {
            r.assemble(aspect = 16f / 9f).shapes.flatMap { it.points }.forEach { p ->
                assertTrue(p.x in -0.02f..1.02f && p.y in -0.02f..1.02f,
                    "recipe '${r.kind}' vertex ($p) should stay within the normalized frame")
            }
        }
    }

    @Test
    fun colorOverridesWinOverDefaults() {
        val cat = OGRigRecipes.builtIns.first { it.kind == "quadruped" }
        val recoloured = cat.assemble(colorOverrides = mapOf("body" to "#123456"))
        assertEquals("#123456", recoloured.shapes.first { it.label == "body" }.fill)
        // The default palette is untouched when no override is given.
        assertTrue(cat.assemble().shapes.first { it.label == "body" }.fill != "#123456")
    }

    // ── Extendability: a brand-new kind as data, with no code change ─────────────────────────────

    @Test
    fun aCustomRecipeCanBeSelectedAndAssembled() {
        val blob = OGRigRecipe(
            kind = "blob",
            aliases = listOf("blob", "slime"),
            colors = mapOf("body" to "#44cc88", "eye" to "#222222"),
            parts = listOf(
                OGRigPart(id = "body", role = "body", cx = 0.5f, cy = 0.6f, w = 0.6f, h = 0.5f, motion = "breathe"),
                OGRigPart(id = "eye_l", role = "eye", cx = 0.4f, cy = 0.5f, w = 0.08f, h = 0.1f, z = 1, motion = "blink"),
                OGRigPart(id = "eye_r", role = "eye", cx = 0.6f, cy = 0.5f, w = 0.08f, h = 0.1f, z = 1, motion = "blink"),
            ),
        )
        val catalog = OGRigRecipes.builtIns + blob
        assertEquals("blob", OGRigRecipes.select("a happy green blob", catalog)?.kind)
        // Survives the wire and still assembles.
        val back = OGRigRecipes.decode(OGRigRecipes.encode(blob))
        assertEquals(blob, back)
        assertEquals(3, back.assemble().shapes.size)
    }

    @Test
    fun customCatalogCanBeInjectedIntoTheOfflineAuthor() {
        val blob = OGRigRecipe(
            kind = "blob",
            aliases = listOf("blob"),
            colors = mapOf("body" to "#44cc88"),
            parts = listOf(OGRigPart(id = "body", role = "body", w = 0.6f, h = 0.6f, motion = "breathe")),
        )
        val author = OGOfflineStageAuthor(recipes = OGRigRecipes.builtIns + blob)
        assertTrue(author.direct("a happy blob").layers.any { it.id == "subject" })
    }

    // ── Integration: the offline floor now DRAWS the subject ─────────────────────────────────────

    @Test
    fun offlineAuthorAddsASubjectLayerForARecognisedSubject() {
        val cat = OGOfflineStageAuthor().direct("a smiling cat")
        assertTrue(cat.layers.any { it.id == "subject" }, "a cat should get a drawn figure, not just a title")
    }

    @Test
    fun offlineAuthorStaysMoodOnlyWhenNoSubjectMatches() {
        val sea = OGOfflineStageAuthor().direct("a stormy sea")
        assertTrue(sea.layers.none { it.id == "subject" }, "pure atmosphere gets no figure")
        assertTrue(sea.layers.isNotEmpty(), "but it is still a real movie")
    }
}
