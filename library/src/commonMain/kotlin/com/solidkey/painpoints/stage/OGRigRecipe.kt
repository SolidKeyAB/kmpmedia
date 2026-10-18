package com.solidkey.painpoints.stage

import com.solidkey.painpoints.ai.OGAiVector
import com.solidkey.painpoints.ai.OGPlacedShapeSpec
import com.solidkey.painpoints.ai.OGPointSpec
import com.solidkey.painpoints.ai.OGSceneIdle
import com.solidkey.painpoints.ai.OGSceneSpec
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

/**
 * An **extendable articulation recipe**: the "anatomy & mechanics" of ONE kind of subject, expressed
 * as plain DATA. A recipe lists the subject's labelled [parts] in a unit square (where each part sits,
 * how big, which way it is turned, what colour role it fills, and which idle VERB it moves with), so
 * [assemble] can build a living [OGSceneSpec] from it deterministically — no model, no key, no network.
 *
 * It is the data-defined grounding behind KMPMedia's describe→movie loop. The library already owns the
 * runtime MECHANICS (a part's idle motion + its honest hinge come from [OGSceneIdle], groups + shared
 * pivots from the SVG front-end, deform from `OGRig`); a recipe only supplies the KNOWLEDGE of what a
 * subject is MADE OF so the offline director can draw it instead of falling back to mood + a title.
 *
 * **Extendable by design, three ways:**
 *  1. As data — decode a new recipe from JSON ([OGRigRecipes.decode]); the codec tolerates fences and
 *     unknown fields, so a model or a config file can add a kind without a code change.
 *  2. By injection — hand your own catalog to the author (`OGOfflineStageAuthor(recipes = …)` /
 *     [OGRigRecipes.select]); no global mutable state to fork.
 *  3. By customization — `recipe.copy(...)` or [assemble]'s `colorOverrides` / `fit` recolour and
 *     resize a built-in without redrawing it.
 *
 * Every field is a plain value or a STRING (not an enum) so an unknown shape / role / motion degrades
 * to a sensible default rather than failing to decode — the usual KMPMedia "forward-compatible wire"
 * rule. Parts are authored in a 0..1 unit square (y down); [assemble] centres and aspect-corrects that
 * square into the frame so circles stay round whatever the movie's dimensions.
 */
@Serializable
public data class OGRigRecipe(
    /** The subject kind, free text: `"humanoid"`, `"quadruped"`, `"bird"`, `"flower"`, `"game-piece"`. */
    public val kind: String,
    /** Words in a description that select this recipe (`"cat"`, `"dog"`, `"fox"` → a quadruped). Matched
     *  as whole words, case-insensitively, by [OGRigRecipes.select]. */
    public val aliases: List<String> = emptyList(),
    /** The parts, each becoming one labelled scene region; drawn back-to-front by [OGRigPart.z]. */
    public val parts: List<OGRigPart> = emptyList(),
    /** Default role→colour map, e.g. `"body"` to `"#f0a152"`. Override per call via [assemble]'s
     *  `colorOverrides` (so one recipe paints a ginger cat or a grey one). */
    public val colors: Map<String, String> = emptyMap(),
    /** How tall the figure stands in its frame, as a fraction of height (0..1). */
    public val fit: Float = 0.82f,
    /** Advisory version; decoding never branches on it. */
    public val version: Int = 1,
    /** A free human note (also usable as a prompt hint). */
    public val note: String = "",
) {
    /**
     * Build a living, transparent-background [OGSceneSpec] from this recipe. Pure data assembly done
     * ONCE (never per frame): each part becomes a labelled [OGPlacedShapeSpec] whose polygon is the
     * part's outline, centred + aspect-corrected into the frame, filled from the (overridable) palette,
     * and tagged with its idle motion. Run the result through [OGSceneIdle.animate] (the offline author
     * does) to let the tuned idle engine re-root sways onto real joints and collapse eyes onto one line.
     *
     * @param aspect the target frame's width / height (so the unit square is not stretched).
     * @param colorOverrides role→colour entries that WIN over [colors] (recolour without redrawing).
     * @param fit figure height as a fraction of frame height (defaults to the recipe's own [fit]).
     */
    public fun assemble(
        aspect: Float = 16f / 9f,
        colorOverrides: Map<String, String> = emptyMap(),
        fit: Float = this.fit,
    ): OGSceneSpec {
        val palette = colors + colorOverrides
        val band = fit.coerceIn(0.05f, 1f)
        val top = (1f - band) / 2f
        val widthFrac = (band / aspect).coerceIn(0.01f, 1f)
        val left = 0.5f - widthFrac / 2f
        val shapes = parts.sortedBy { it.z }.map { part ->
            val outline = outlineOf(part).map { p ->
                OGPointSpec(left + p.x * widthFrac, top + p.y * band)
            }
            OGPlacedShapeSpec(
                points = outline,
                label = part.id,
                fill = palette[part.role] ?: palette["body"] ?: FALLBACK_FILL,
                opacity = part.opacity,
                smoothing = part.smoothing ?: defaultSmoothing(part.shape),
                motion = OGSceneIdle.motionNamed(part.motion),
            )
        }
        return OGSceneSpec(shapes = shapes, version = 4, background = null)
    }

    private fun outlineOf(part: OGRigPart): List<OGPointSpec> {
        val raw = when (part.shape.trim().lowercase()) {
            "triangle" -> triangle(part.w, part.h)
            "leaf" -> leaf(part.w, part.h)
            "trapezoid" -> trapezoid(part.w, part.h)
            else -> ellipse(part.w, part.h) // "ellipse" + any unknown shape
        }
        if (part.rotationDeg == 0f) return raw.map { OGPointSpec(part.cx + it.x, part.cy + it.y) }
        val a = part.rotationDeg * PI_F / 180f
        val c = cos(a); val s = sin(a)
        return raw.map { OGPointSpec(part.cx + it.x * c - it.y * s, part.cy + it.x * s + it.y * c) }
    }

    public companion object {
        private const val PI_F = 3.1415927f
        private const val FALLBACK_FILL = "#cccccc"

        /** Round shapes smooth, pointed/flat ones barely. */
        private fun defaultSmoothing(shape: String): Float = when (shape.trim().lowercase()) {
            "triangle", "trapezoid" -> 0.15f
            "leaf" -> 0.7f
            else -> 1f
        }

        /** An origin-centred ellipse with half-extents (w/2, h/2). */
        private fun ellipse(w: Float, h: Float, n: Int = 20): List<OGPointSpec> {
            val rx = w / 2f; val ry = h / 2f
            return (0 until n).map { i ->
                val t = i / n.toFloat() * 2f * PI_F
                OGPointSpec(rx * cos(t), ry * sin(t))
            }
        }

        /** An origin-centred triangle, apex UP (toward -y). */
        private fun triangle(w: Float, h: Float): List<OGPointSpec> =
            listOf(OGPointSpec(0f, -h / 2f), OGPointSpec(w / 2f, h / 2f), OGPointSpec(-w / 2f, h / 2f))

        /** An origin-centred pointed oval (a leaf / petal / wing / tail), long axis vertical. */
        private fun leaf(w: Float, h: Float): List<OGPointSpec> = listOf(
            OGPointSpec(0f, -h / 2f),
            OGPointSpec(w * 0.45f, -h * 0.15f),
            OGPointSpec(w / 2f, h * 0.15f),
            OGPointSpec(0f, h / 2f),
            OGPointSpec(-w / 2f, h * 0.15f),
            OGPointSpec(-w * 0.45f, -h * 0.15f),
        )

        /** An origin-centred trapezoid, WIDER at the base (+y), for a pot / pedestal / pawn base. */
        private fun trapezoid(w: Float, h: Float): List<OGPointSpec> = listOf(
            OGPointSpec(-w * 0.6f, -h / 2f),
            OGPointSpec(w * 0.6f, -h / 2f),
            OGPointSpec(w / 2f, h / 2f),
            OGPointSpec(-w / 2f, h / 2f),
        )
    }
}

/**
 * One labelled part of an [OGRigRecipe]. Authored in the recipe's 0..1 unit square (y down). [id] becomes
 * the region's label (so [OGSceneIdle] recognises an `"eye"`/`"tail"`/`"body"` by name), [role] picks a
 * colour from the recipe palette, [shape] chooses the outline, and [motion] names an idle verb.
 */
@Serializable
public data class OGRigPart(
    /** The region label and animatable name: `"head"`, `"torso"`, `"eye_l"`, `"tail"`, `"petal_3"`. */
    public val id: String,
    /** Which palette colour to fill with: `"body"`, `"skin"`, `"accent"`, `"dark"`, `"eye"`, … */
    public val role: String = "body",
    /** Outline kind: `"ellipse"` | `"triangle"` | `"leaf"` | `"trapezoid"` (unknown → ellipse). */
    public val shape: String = "ellipse",
    /** Part centre in the unit square. */
    public val cx: Float = 0.5f,
    public val cy: Float = 0.5f,
    /** Part size as a fraction of the unit square. */
    public val w: Float = 0.2f,
    public val h: Float = 0.2f,
    /** Static orientation in degrees (clockwise), about the part centre. */
    public val rotationDeg: Float = 0f,
    /** Paint order; low draws BEHIND (root a limb behind the body with a low z). */
    public val z: Int = 0,
    /** Idle verb — `""` | `"breathe"` | `"sway"` | `"blink"` | `"wave"` | `"bob"` | `"nod"` | `"pulse"`.
     *  Resolved via [OGSceneIdle.motionNamed]; `""` leaves it to the label-based auto-idle. */
    public val motion: String = "",
    /** Outline roundness 0..1; null → the shape's own default. */
    public val smoothing: Float? = null,
    /** Whole-part alpha 0..1. */
    public val opacity: Float? = null,
)

/**
 * The built-in catalog of [OGRigRecipe]s plus the selection + codec glue. Deliberately has NO mutable
 * global registry: extend by decoding recipes from JSON and/or passing your own catalog to [select]
 * (and to `OGOfflineStageAuthor(recipes = …)`), so there is nothing to fork and nothing to race.
 */
public object OGRigRecipes {

    /** The shipped recipes, ordered so a more specific kind wins a tie in [select]. A narrower kind
     *  (fish, butterfly) is listed BEFORE the broad quadruped so "a pet fish" draws a fish, not an
     *  animal that happened to match the word "pet". */
    public val builtIns: List<OGRigRecipe> = listOf(
        gamePiece(), bird(), butterfly(), fish(), quadruped(), flower(), humanoid(),
    )

    /**
     * The recipe in [catalog] whose subject word appears EARLIEST in [text] (whole word, case-insensitive;
     * a multi-word alias matches as a substring), or null if none match. Earliest-mention wins — so
     * "a cat chasing a butterfly" is a cat, not a butterfly — and a broad catch-all alias (`"animal"`,
     * `"pet"`, `"creature"`, `"character"`, `"figure"`) is demoted so a specific species named anywhere
     * outranks it ("a pet fish" is a fish). Ties break by [catalog] order. Pass your own [catalog] (e.g.
     * `OGRigRecipes.builtIns + myRecipe`) to extend the set.
     */
    public fun select(text: String, catalog: List<OGRigRecipe> = builtIns): OGRigRecipe? {
        val lower = text.lowercase()
        val tokens = tokenize(lower)
        var best: OGRigRecipe? = null
        var bestAt = Int.MAX_VALUE
        for (recipe in catalog) {
            var at = Int.MAX_VALUE
            for (a in recipe.aliases) {
                val i = matchIndex(a.lowercase(), tokens, lower)
                if (i < at) at = i
            }
            if (at < bestAt) { bestAt = at; best = recipe } // strict < ⇒ catalog order breaks a tie
        }
        return best
    }

    /** Catch-all category aliases that read like adjectives: they only win when no specific subject word
     *  matched, so a species named ANYWHERE in the text outranks them (keeps "a pet fish" a fish). */
    private val GENERIC_ALIASES = hashSetOf("animal", "creature", "pet", "character", "figure")

    /** The delimiters that separate words in a description (the set the selector has always split on). */
    private const val WORD_DELIMS = " \n\t,.;:!?\"'()-/_"

    /** The rank a matched GENERIC alias sorts at: after every real text position, before "no match". */
    private const val GENERIC_RANK = Int.MAX_VALUE - 1

    /** Split [lower] into (word, startIndex) pairs on [WORD_DELIMS], so a match is ranked by WHERE it
     *  occurs, not merely whether it occurs. */
    private fun tokenize(lower: String): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        var i = 0
        val n = lower.length
        while (i < n) {
            if (lower[i] in WORD_DELIMS) { i++; continue }
            val start = i
            while (i < n && lower[i] !in WORD_DELIMS) i++
            out.add(lower.substring(start, i) to start)
        }
        return out
    }

    /** The earliest character index at which [alias] matches (a whole-word token, or a substring for a
     *  multi-word alias), [GENERIC_RANK] if it matches only as a catch-all, else [Int.MAX_VALUE]. */
    private fun matchIndex(alias: String, tokens: List<Pair<String, Int>>, lower: String): Int {
        if (alias.isEmpty()) return Int.MAX_VALUE
        val generic = alias in GENERIC_ALIASES
        if (' ' in alias) {
            val i = lower.indexOf(alias)
            return if (i < 0) Int.MAX_VALUE else if (generic) GENERIC_RANK else i
        }
        var best = Int.MAX_VALUE
        for ((w, at) in tokens) if (w == alias && at < best) best = at
        return if (best == Int.MAX_VALUE) Int.MAX_VALUE else if (generic) GENERIC_RANK else best
    }

    /** Serialize a recipe to JSON (to persist or seed a prompt with a custom kind). */
    public fun encode(recipe: OGRigRecipe): String =
        OGAiVector.json.encodeToString(OGRigRecipe.serializer(), recipe)

    /** Parse a recipe from JSON; tolerates surrounding prose / fences. Throws on garbage. */
    public fun decode(text: String): OGRigRecipe =
        OGAiVector.json.decodeFromString(OGRigRecipe.serializer(), OGAiVector.extractJson(text))

    /** [decode] but returns null instead of throwing. */
    public fun decodeOrNull(text: String): OGRigRecipe? = runCatching { decode(text) }.getOrNull()

    // ── Built-in recipes — schematic, proportion-based figures in the 0..1 unit square ───────────────

    /** A front-facing person: head + torso, rooted arms + legs, hair, two blinking eyes. */
    private fun humanoid(): OGRigRecipe = OGRigRecipe(
        kind = "humanoid",
        aliases = listOf(
            "person", "human", "man", "woman", "girl", "boy", "child", "kid", "lady", "guy",
            "robot", "hero", "knight", "wizard", "character", "figure", "astronaut", "ninja",
            "princess", "prince", "king", "queen", "soldier", "warrior", "pirate", "mage", "witch",
            "angel", "fairy", "elf", "dwarf", "giant", "gnome", "zombie", "mascot", "superhero",
            "cowboy", "farmer", "doctor", "chef", "dancer", "baby", "teacher", "samurai", "viking",
        ),
        colors = mapOf(
            "skin" to "#f1c9a5", "body" to "#4f8dd6", "accent" to "#6a4b3a",
            "eye" to "#2a2a2a", "dark" to "#c25a3a",
        ),
        parts = listOf(
            OGRigPart("leg_l", role = "skin", shape = "ellipse", cx = 0.44f, cy = 0.84f, w = 0.12f, h = 0.30f, z = 0),
            OGRigPart("leg_r", role = "skin", shape = "ellipse", cx = 0.56f, cy = 0.84f, w = 0.12f, h = 0.30f, z = 0),
            OGRigPart("arm_l", role = "skin", shape = "ellipse", cx = 0.30f, cy = 0.54f, w = 0.10f, h = 0.34f, rotationDeg = 14f, z = 0),
            OGRigPart("arm_r", role = "skin", shape = "ellipse", cx = 0.70f, cy = 0.54f, w = 0.10f, h = 0.34f, rotationDeg = -14f, z = 0),
            OGRigPart("torso", role = "body", shape = "ellipse", cx = 0.5f, cy = 0.55f, w = 0.30f, h = 0.44f, z = 1, motion = "breathe"),
            OGRigPart("hair", role = "accent", shape = "ellipse", cx = 0.5f, cy = 0.17f, w = 0.30f, h = 0.26f, z = 1),
            OGRigPart("head", role = "skin", shape = "ellipse", cx = 0.5f, cy = 0.21f, w = 0.26f, h = 0.27f, z = 2),
            OGRigPart("eye_l", role = "eye", shape = "ellipse", cx = 0.44f, cy = 0.21f, w = 0.045f, h = 0.06f, z = 3, motion = "blink"),
            OGRigPart("eye_r", role = "eye", shape = "ellipse", cx = 0.56f, cy = 0.21f, w = 0.045f, h = 0.06f, z = 3, motion = "blink"),
            OGRigPart("mouth", role = "dark", shape = "leaf", cx = 0.5f, cy = 0.28f, w = 0.08f, h = 0.03f, z = 3),
        ),
        note = "front-facing person, rooted limbs, eyes blink, chest breathes",
    )

    /** A front-facing, seated creature (cat / dog / fox / bear …): body, head, ears, eyes, muzzle, tail. */
    private fun quadruped(): OGRigRecipe = OGRigRecipe(
        kind = "quadruped",
        aliases = listOf(
            "cat", "kitten", "dog", "puppy", "fox", "wolf", "bear", "lion", "tiger", "panda",
            "rabbit", "bunny", "mouse", "deer", "pig", "cow", "sheep", "goat", "horse", "pony",
            "koala", "raccoon", "squirrel", "hamster", "animal", "creature", "pet", "cub",
            // Only animals a front-facing SEATED cat/dog schematic can honestly stand in for. Left OUT
            // on purpose: trunk/neck/horn silhouettes (elephant, giraffe, rhino, hippo, camel) and
            // sprawling reptiles (lizard, crocodile, turtle) — those contradict this figure and the
            // sprawling-reptile rule in stagePrompt, so the LLM path draws them and the offline floor
            // stays mood-only rather than faking a wrong body.
            "leopard", "cheetah", "jaguar", "panther", "lynx", "hyena", "zebra", "llama", "alpaca",
            "donkey", "mule", "buffalo", "bison", "moose", "elk", "antelope", "gazelle", "monkey",
            "ape", "gorilla", "chimp", "sloth", "otter", "ferret", "chipmunk", "hedgehog", "badger",
            "meerkat", "calf", "lamb", "foal",
        ),
        colors = mapOf(
            "body" to "#f0a152", "light" to "#ffd0a8", "eye" to "#2a2a2a", "dark" to "#c25a3a",
        ),
        parts = listOf(
            OGRigPart("tail", role = "body", shape = "leaf", cx = 0.26f, cy = 0.72f, w = 0.14f, h = 0.34f, rotationDeg = -32f, z = 0, motion = "sway"),
            OGRigPart("body", role = "body", shape = "ellipse", cx = 0.5f, cy = 0.70f, w = 0.44f, h = 0.42f, z = 1, motion = "breathe"),
            OGRigPart("paw_l", role = "body", shape = "ellipse", cx = 0.40f, cy = 0.90f, w = 0.13f, h = 0.12f, z = 2),
            OGRigPart("paw_r", role = "body", shape = "ellipse", cx = 0.60f, cy = 0.90f, w = 0.13f, h = 0.12f, z = 2),
            OGRigPart("ear_l", role = "body", shape = "triangle", cx = 0.37f, cy = 0.17f, w = 0.15f, h = 0.20f, z = 2),
            OGRigPart("ear_r", role = "body", shape = "triangle", cx = 0.63f, cy = 0.17f, w = 0.15f, h = 0.20f, z = 2),
            OGRigPart("head", role = "body", shape = "ellipse", cx = 0.5f, cy = 0.31f, w = 0.42f, h = 0.40f, z = 3),
            OGRigPart("muzzle", role = "light", shape = "ellipse", cx = 0.5f, cy = 0.40f, w = 0.24f, h = 0.15f, z = 4),
            OGRigPart("eye_l", role = "eye", shape = "ellipse", cx = 0.41f, cy = 0.29f, w = 0.08f, h = 0.10f, z = 5, motion = "blink"),
            OGRigPart("eye_r", role = "eye", shape = "ellipse", cx = 0.59f, cy = 0.29f, w = 0.08f, h = 0.10f, z = 5, motion = "blink"),
            OGRigPart("nose", role = "dark", shape = "triangle", cx = 0.5f, cy = 0.39f, w = 0.07f, h = 0.06f, rotationDeg = 180f, z = 6),
        ),
        note = "front-facing seated animal; tail sways, body breathes, eyes blink",
    )

    /** A plump little bird: body, head, wing, tail, beak, eye, feet. */
    private fun bird(): OGRigRecipe = OGRigRecipe(
        kind = "bird",
        aliases = listOf(
            "bird", "robin", "sparrow", "owl", "duck", "chick", "chicken", "parrot", "penguin",
            "dove", "finch", "canary", "hen", "crow", "pigeon", "budgie",
            "eagle", "hawk", "falcon", "swan", "goose", "flamingo", "peacock", "seagull", "gull",
            "magpie", "woodpecker", "hummingbird", "toucan", "cardinal", "jay", "heron", "stork",
            "ostrich", "turkey", "rooster", "cockatoo", "macaw", "swallow", "wren", "raven",
        ),
        colors = mapOf(
            "body" to "#7ec8e3", "accent" to "#4f9fc0", "beak" to "#f5a623", "eye" to "#2a2a2a",
        ),
        parts = listOf(
            OGRigPart("tail", role = "body", shape = "leaf", cx = 0.28f, cy = 0.64f, w = 0.18f, h = 0.26f, rotationDeg = -28f, z = 0, motion = "sway"),
            OGRigPart("foot_l", role = "beak", shape = "ellipse", cx = 0.46f, cy = 0.92f, w = 0.05f, h = 0.08f, z = 0),
            OGRigPart("foot_r", role = "beak", shape = "ellipse", cx = 0.58f, cy = 0.92f, w = 0.05f, h = 0.08f, z = 0),
            OGRigPart("body", role = "body", shape = "ellipse", cx = 0.52f, cy = 0.62f, w = 0.36f, h = 0.46f, z = 1, motion = "breathe"),
            OGRigPart("wing", role = "accent", shape = "leaf", cx = 0.60f, cy = 0.58f, w = 0.20f, h = 0.30f, rotationDeg = 24f, z = 2, motion = "sway"),
            OGRigPart("head", role = "body", shape = "ellipse", cx = 0.5f, cy = 0.27f, w = 0.28f, h = 0.28f, z = 3),
            OGRigPart("beak", role = "beak", shape = "triangle", cx = 0.5f, cy = 0.34f, w = 0.11f, h = 0.10f, rotationDeg = 180f, z = 4),
            OGRigPart("eye_l", role = "eye", shape = "ellipse", cx = 0.44f, cy = 0.24f, w = 0.055f, h = 0.065f, z = 4, motion = "blink"),
            OGRigPart("eye_r", role = "eye", shape = "ellipse", cx = 0.56f, cy = 0.24f, w = 0.055f, h = 0.065f, z = 4, motion = "blink"),
        ),
        note = "plump bird; tail + wing sway, body breathes, eyes blink",
    )

    /** A front-on butterfly: a slim body, two pairs of fluttering wings, head + antennae + eyes. */
    private fun butterfly(): OGRigRecipe = OGRigRecipe(
        kind = "butterfly",
        aliases = listOf(
            // Only winged lepidoptera read right from this legless wings-and-body schematic; a generic
            // "bug" / "insect" / "dragonfly" (six visible legs, long thin body) is left to the LLM path.
            "butterfly", "moth", "mariposa",
        ),
        colors = mapOf(
            "wing" to "#ff8fb1", "accent" to "#ffd34e", "body" to "#5a3b2a", "eye" to "#2a2a2a",
        ),
        parts = listOf(
            OGRigPart("wing_ul", role = "wing", shape = "leaf", cx = 0.33f, cy = 0.38f, w = 0.30f, h = 0.32f, rotationDeg = -28f, z = 0, motion = "sway"),
            OGRigPart("wing_ur", role = "wing", shape = "leaf", cx = 0.67f, cy = 0.38f, w = 0.30f, h = 0.32f, rotationDeg = 28f, z = 0, motion = "sway"),
            OGRigPart("wing_ll", role = "accent", shape = "leaf", cx = 0.37f, cy = 0.63f, w = 0.24f, h = 0.26f, rotationDeg = -150f, z = 0, motion = "sway"),
            OGRigPart("wing_lr", role = "accent", shape = "leaf", cx = 0.63f, cy = 0.63f, w = 0.24f, h = 0.26f, rotationDeg = 150f, z = 0, motion = "sway"),
            OGRigPart("antenna_l", role = "body", shape = "leaf", cx = 0.45f, cy = 0.16f, w = 0.03f, h = 0.14f, rotationDeg = -22f, z = 1, motion = "sway"),
            OGRigPart("antenna_r", role = "body", shape = "leaf", cx = 0.55f, cy = 0.16f, w = 0.03f, h = 0.14f, rotationDeg = 22f, z = 1, motion = "sway"),
            OGRigPart("body", role = "body", shape = "ellipse", cx = 0.5f, cy = 0.5f, w = 0.08f, h = 0.44f, z = 2, motion = "breathe"),
            OGRigPart("head", role = "body", shape = "ellipse", cx = 0.5f, cy = 0.26f, w = 0.11f, h = 0.11f, z = 3),
            OGRigPart("eye_l", role = "eye", shape = "ellipse", cx = 0.47f, cy = 0.25f, w = 0.03f, h = 0.035f, z = 4, motion = "blink"),
            OGRigPart("eye_r", role = "eye", shape = "ellipse", cx = 0.53f, cy = 0.25f, w = 0.03f, h = 0.035f, z = 4, motion = "blink"),
        ),
        note = "front-on butterfly; wings flutter (sway from the body), antennae sway, body breathes",
    )

    /** A side-on fish: a long body, a sweeping tail fin, dorsal + belly fins, one eye and a mouth. */
    private fun fish(): OGRigRecipe = OGRigRecipe(
        kind = "fish",
        aliases = listOf(
            "fish", "goldfish", "koi", "carp", "salmon", "trout", "tuna", "guppy", "clownfish",
            "shark", "dolphin", "whale", "minnow", "betta", "angelfish", "swordfish", "piranha",
        ),
        colors = mapOf(
            "body" to "#5bbcd6", "fin" to "#3f9cbc", "light" to "#bfe8f2", "eye" to "#222222", "dark" to "#214a5a",
        ),
        parts = listOf(
            OGRigPart("tail", role = "fin", shape = "leaf", cx = 0.17f, cy = 0.5f, w = 0.22f, h = 0.36f, z = 0, motion = "sway"),
            OGRigPart("dorsal", role = "fin", shape = "leaf", cx = 0.5f, cy = 0.36f, w = 0.22f, h = 0.20f, z = 0, motion = "sway"),
            OGRigPart("belly", role = "fin", shape = "leaf", cx = 0.48f, cy = 0.66f, w = 0.14f, h = 0.12f, rotationDeg = 150f, z = 0, motion = "sway"),
            OGRigPart("body", role = "body", shape = "ellipse", cx = 0.54f, cy = 0.5f, w = 0.62f, h = 0.36f, z = 1, motion = "breathe"),
            OGRigPart("belly_light", role = "light", shape = "ellipse", cx = 0.56f, cy = 0.56f, w = 0.44f, h = 0.16f, z = 2),
            OGRigPart("fin_side", role = "fin", shape = "leaf", cx = 0.60f, cy = 0.56f, w = 0.12f, h = 0.16f, rotationDeg = 160f, z = 3, motion = "sway"),
            OGRigPart("eye_l", role = "eye", shape = "ellipse", cx = 0.74f, cy = 0.45f, w = 0.06f, h = 0.07f, z = 4, motion = "blink"),
            OGRigPart("mouth", role = "dark", shape = "leaf", cx = 0.84f, cy = 0.54f, w = 0.06f, h = 0.03f, z = 4),
        ),
        fit = 0.72f,
        note = "side-on fish; tail + fins sway, body breathes, one eye blinks (profile view has one eye)",
    )

    /** A potted flower: pot, stem, two sway leaves, a ring of petals around a pulsing centre. */
    private fun flower(): OGRigRecipe = OGRigRecipe(
        kind = "flower",
        aliases = listOf(
            "flower", "rose", "tulip", "daisy", "sunflower", "bloom", "blossom", "plant",
            "lotus", "poppy", "lily", "dandelion", "potplant",
            "daffodil", "orchid", "marigold", "hyacinth", "peony", "petunia", "violet", "carnation",
            "buttercup", "bluebell", "sakura", "chrysanthemum", "hibiscus", "magnolia", "pansy",
            "snowdrop", "crocus", "camellia",
        ),
        colors = mapOf(
            "pot" to "#c97b4a", "stem" to "#4e9a4e", "petal" to "#ff8fb1", "accent" to "#ffd34e",
        ),
        parts = listOf(
            OGRigPart("stem", role = "stem", shape = "ellipse", cx = 0.5f, cy = 0.62f, w = 0.05f, h = 0.40f, z = 0),
            OGRigPart("leaf_l", role = "stem", shape = "leaf", cx = 0.40f, cy = 0.66f, w = 0.20f, h = 0.12f, rotationDeg = -55f, z = 0, motion = "sway"),
            OGRigPart("leaf_r", role = "stem", shape = "leaf", cx = 0.60f, cy = 0.60f, w = 0.20f, h = 0.12f, rotationDeg = 55f, z = 0, motion = "sway"),
            OGRigPart("pot", role = "pot", shape = "trapezoid", cx = 0.5f, cy = 0.87f, w = 0.34f, h = 0.20f, z = 1),
            OGRigPart("petal_0", role = "petal", shape = "leaf", cx = 0.5f, cy = 0.22f, w = 0.12f, h = 0.20f, rotationDeg = 0f, z = 2, motion = "pulse"),
            OGRigPart("petal_1", role = "petal", shape = "leaf", cx = 0.5f, cy = 0.22f, w = 0.12f, h = 0.20f, rotationDeg = 60f, z = 2, motion = "pulse"),
            OGRigPart("petal_2", role = "petal", shape = "leaf", cx = 0.5f, cy = 0.22f, w = 0.12f, h = 0.20f, rotationDeg = 120f, z = 2, motion = "pulse"),
            OGRigPart("petal_3", role = "petal", shape = "leaf", cx = 0.5f, cy = 0.22f, w = 0.12f, h = 0.20f, rotationDeg = 180f, z = 2, motion = "pulse"),
            OGRigPart("petal_4", role = "petal", shape = "leaf", cx = 0.5f, cy = 0.22f, w = 0.12f, h = 0.20f, rotationDeg = 240f, z = 2, motion = "pulse"),
            OGRigPart("petal_5", role = "petal", shape = "leaf", cx = 0.5f, cy = 0.22f, w = 0.12f, h = 0.20f, rotationDeg = 300f, z = 2, motion = "pulse"),
            OGRigPart("bloom", role = "accent", shape = "ellipse", cx = 0.5f, cy = 0.22f, w = 0.12f, h = 0.12f, z = 3, motion = "pulse"),
        ),
        fit = 0.9f,
        note = "potted flower; leaves sway, the bloom pulses",
    )

    /** A game piece / token (a chess pawn): one rigid silhouette that bobs as a whole unit. */
    private fun gamePiece(): OGRigRecipe = OGRigRecipe(
        kind = "game-piece",
        aliases = listOf(
            // Kept deliberately specific: generic words like "piece" / "marble" mis-routed common
            // requests ("a piece of cheese", "a lion made of marble") to a pawn, so they are out.
            "pawn", "chess", "chesspiece", "checker", "meeple", "dice",
        ),
        colors = mapOf("piece" to "#8a6d53"),
        parts = listOf(
            OGRigPart("base", role = "piece", shape = "trapezoid", cx = 0.5f, cy = 0.82f, w = 0.36f, h = 0.12f, z = 0, motion = "bob"),
            OGRigPart("stem", role = "piece", shape = "trapezoid", cx = 0.5f, cy = 0.60f, w = 0.20f, h = 0.32f, z = 1, motion = "bob"),
            OGRigPart("collar", role = "piece", shape = "ellipse", cx = 0.5f, cy = 0.44f, w = 0.28f, h = 0.08f, z = 2, motion = "bob"),
            OGRigPart("body", role = "piece", shape = "ellipse", cx = 0.5f, cy = 0.38f, w = 0.22f, h = 0.22f, z = 2, motion = "bob"),
            OGRigPart("head", role = "piece", shape = "ellipse", cx = 0.5f, cy = 0.22f, w = 0.19f, h = 0.19f, z = 3, motion = "bob"),
        ),
        note = "a rigid pawn-shaped token that bobs as one piece (no internal joints)",
    )
}
