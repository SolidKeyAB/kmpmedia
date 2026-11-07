# KMPMedia vs. the Field — Competitive Comparison (2025/2026)

> Research snapshot, Oct 2026. KMPMedia = `se.solidkey:kmpmedia-lib`: a zero-extra-dependency,
> pure-Compose (no custom shaders, no Skia-direct), data-defined, LLM-authorable KMP graphics/animation
> toolkit (Android + iOS via Compose Multiplatform). Below are the competitors a developer would
> realistically evaluate instead, with current versions / licenses / platform facts.

## 1. Capability matrix

Legend: ✅ first-class · ⚠️ partial / possible-with-work / caveat · ❌ not offered

| Capability | KMPMedia | Rive (+Rive-CMP) | Compottie | Lottie (Airbnb) | Skiko / Skia-direct | Haze | Konfetti / ConfettiKit | Spine | DragonBones | Compose built-in | Korge |
|---|---|---|---|---|---|---|---|---|---|---|---|
| KMP-native (iOS+Android, pure Kotlin) | ✅ | ⚠️ community wrapper | ✅ pure-Kotlin | ❌ Android-only | ✅ (is the engine) | ✅ | ⚠️ ConfettiKit ✅ / Konfetti Android | ⚠️ no CMP | ⚠️ no CMP | ⚠️ Android; CMP partial | ✅ engine |
| Skeletal / cut-out puppet rig | ✅ raster puppet | ✅ bones+meshes | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ best-in-class | ✅ | ❌ | ⚠️ plugins |
| Vector keyframe anim (AE/JSON) | ⚠️ stage timeline | ✅ own format | ✅ Lottie | ✅ Lottie | ⚠️ DIY | ❌ | ❌ | ⚠️ own | ⚠️ own | ⚠️ DIY | ⚠️ |
| Interactive state machines | ⚠️ tap triggers | ✅ signature | ❌ | ❌ | ❌ | ❌ | ❌ | ⚠️ events | ❌ | ⚠️ manual | ✅ |
| Particles | ✅ data-defined | ⚠️ in-editor | ❌ | ❌ | ⚠️ DIY | ❌ | ✅ confetti only | ❌ | ❌ | ⚠️ DIY | ✅ |
| Blur / glassmorphism | ❌ no true blur | ❌ | ❌ | ❌ | ✅ shader | ✅ specialty | ❌ | ❌ | ❌ | ⚠️ Android | ⚠️ |
| Custom shaders (SkSL/AGSL) | ❌ by design | ⚠️ own renderer | ❌ | ❌ | ✅ full | ✅ | ❌ | ❌ | ❌ | ⚠️ GL | ⚠️ |
| Action/anime FX (slash/lightning/speed-lines) | ✅ unique | ⚠️ hand-animate | ⚠️ | ⚠️ | ⚠️ DIY | ❌ | ❌ | ⚠️ | ⚠️ | ❌ | ⚠️ |
| Color grade / blend modes | ✅ ColorMatrix | ⚠️ in-editor | ⚠️ | ⚠️ | ✅ | ⚠️ tint | ❌ | ❌ | ❌ | ⚠️ | ⚠️ |
| Audio-reactive (FFT→visuals) | ✅ unique | ❌ | ❌ | ❌ | ⚠️ DIY | ❌ | ❌ | ❌ | ❌ | ⚠️ DIY | ⚠️ |
| Gesture interactivity (grab/pinch/twist + hit-test) | ✅ | ✅ pointer→SM | ⚠️ | ⚠️ | ⚠️ DIY | ❌ | ❌ | ⚠️ | ⚠️ | ⚠️ manual | ✅ |
| GIF/MP4 off-screen export | ✅ built-in | ⚠️ editor | ❌ | ❌ | ⚠️ DIY | ❌ | ❌ | ⚠️ editor | ⚠️ editor | ❌ | ⚠️ |
| LLM / data-authorable (JSON) | ✅ core thesis | ⚠️ .riv binary | ✅ consumes JSON | ✅ consumes JSON | ❌ | ❌ | ⚠️ config | ❌ binary | ❌ binary | ❌ | ❌ |
| Needs external design tool | ✅ none (Layer Cutter optional) | ❌ Editor | ❌ After Effects | ❌ After Effects | ✅ none | ✅ none | ✅ none | ❌ Spine | ❌ tool | ✅ none | ⚠️ plugin |
| License | **MIT** | MIT runtime (Editor closed) | MIT | Apache-2.0 | Apache-2.0 | Apache-2.0 | Apache-2.0 / MIT | commercial ($69+) | MIT (tool abandoned) | Apache-2.0 | Apache-2.0 |

## 2. Per-competitor profiles

**Rive (+ Rive-CMP).** Interactive state-machine 2D animation with its own vector editor (bones, meshes, IK, pointer-driven state machines). Runtimes/renderer/format MIT; the **Editor is closed-source** (free tier generous; OpenRive is an MIT community editor). Official runtimes: android/ios/web/C++ — **no official Compose Multiplatform runtime**; `dev.muazkadan:rive-cmp` (community, ~v0.2.1 Dec 2025) bridges the native runtimes. Beats us on the **visual state-machine editor + designer-driven interactivity**. <https://github.com/rive-app/rive-runtime> · <https://github.com/muazkadan/Rive-CMP>

**Lottie (Airbnb) / lottie-compose.** After Effects → Bodymovin JSON vector playback. Apache-2.0, ~6.7.1 (Oct 2025), **Android-only — Airbnb do not support KMP/CMP**. Massive designer ecosystem (LottieFiles). <https://central.sonatype.com/artifact/com.airbnb.android/lottie-compose>

**Compottie.** `io.github.alexzhirkevich:compottie`, **MIT**, ~2.3.3 (2026). A **pure-Kotlin Lottie renderer** (no native bridge) for CMP — Android/iOS/JVM/macOS/Web. Strongest KMP-native vector-animation competitor, but confined to the Lottie format + needs AE to author. <https://github.com/alexzhirkevich/compottie>

**Skiko / Skia-direct.** The engine Compose MP renders on (Metal on iOS). Gives raw **RuntimeShader/SkSL** (AGSL on Android), path ops, true blur/bloom — everything we deliberately forgo. Apache-2.0. The floor you drop to for real shaders. <https://en.wikipedia.org/wiki/Skia_Graphics_Engine>

**Haze (Chris Banes).** Specialty **background blur / glassmorphism** for CMP (Android/iOS/macOS/Desktop/Web). Apache-2.0, **2.0 (Sep 2026)** — split `hazeBlur`/`hazeGlass`, hardware-accelerated. Fills our missing blur/glass. <https://chrisbanes.me/posts/haze-2.0.0/>

**Konfetti / ConfettiKit.** Konfetti (`nl.dionsegijn`, Apache-2.0, ~2.0.5) = classic **Android** confetti. **ConfettiKit** (`io.github.vinceglb`, ~0.8.0 Feb 2026) = KMP/CMP port. Confetti only — a narrow subset of our particle pack. <https://github.com/vinceglb/ConfettiKit>

**Spine (Esoteric Software).** Industry-standard **2D skeletal animation** (bones, meshes, IK) + commercial editor (Essential $69 / Pro $299); runtime license requires users to own Spine to redistribute (updated Apr 2025). Broad engine runtimes, **no CMP runtime**. Gold standard for rigging — far deeper than our raster puppet, but paid + tool-bound. <https://en.esotericsoftware.com/spine-runtimes-license>

**DragonBones.** Free, **MIT** 2D skeletal tool; desktop editor effectively **abandoned**, runtimes live on GitHub, no CMP runtime. Free puppet alternative to Spine but stagnant. <https://github.com/openfl/dragonbones>

**Telegram rlottie.** Standalone **C++** Lottie renderer (Samsung/Telegram). Mixed licensing (LGPL-2.1 / MIT-with-bundled). Fast, but C++/native, no Kotlin/CMP API. <https://github.com/Samsung/rlottie>

**Jetpack Compose built-in animation.** `animate*AsState`, `Animatable`, `Transition`, `AnimatedVisibility` — Apache-2.0, the baseline. Great for property/UI tweening; **no particles, FX, puppets, color-grade, export, or authoring format**. <https://developer.android.com/develop/ui/compose/animation/value-based>

**Honorable mentions.** Orbital (skydoves, shared-element transitions, CMP), compose-shimmer (`com.valentinilk.shimmer`, ~1.4.0) — narrow single-effect libs, little overlap. **Korge** (~6.0.0, May 2025, Apache-2.0) — full KMP **game engine**; an alternative only if you're building a game, not UI animation.

## 3. KMPMedia's genuine differentiators

1. **Data / LLM-authorable by design** — every pack is a JSON spec a model can emit (describe→movie, stage docs). No competitor's *authoring* story is LLM-first; Compottie only *consumes* model-friendly JSON, can't generate its own feature set from prose.
2. **Breadth in one pure-Kotlin KMP artifact** — the field is fragmented (Haze=blur, ConfettiKit=confetti, Compottie=vector, Rive/Spine=rigs, Skiko=shaders). We bundle styles, particles, FX, looks, motion, dynamics, audio-reactive, gesture, camera, stage+export, puppet, fog, light/shadow, skin behind one dependency.
3. **No external tool, no native/shader dependency** — zero extra deps, no Editor subscription (Rive/Spine), no NDK/C++ bridge (rlottie, Rive-CMP), no AE license. Layer Cutter is a free web tool, not a paywall.
4. **Cross-platform pixel-identical + 60fps fixed-capacity sims** — staying in pure Compose avoids the platform-shader divergence Rive-CMP (per-platform native runtimes) and Haze (platform render effects) inherently carry.
5. **Audio-reactive vectors** — essentially unique among Compose/KMP UI libraries.

## 4. Honest gaps

- **No visual editor / designer ecosystem** — Rive's state-machine editor, Spine's rigging tool, LottieFiles' marketplace are each a moat we don't match. Decisive for designer-driven teams.
- **No true blur/bloom/custom shaders** — the no-shader rule means Haze (glass/blur) and Skiko (SkSL) beat us for glassmorphism / gaussian bloom / arbitrary pixel FX.
- **Puppet is raster cut-out, not true skeletal** — Spine/DragonBones/Rive offer bones + mesh deform + IK; ours is jointed PNG movers (lighter, less expressive).
- **Not the AE/Lottie standard** — for importing existing motion-design assets, Compottie/Lottie win outright (huge existing content library).
- **Interactivity is tap-triggers, not a full state machine** — Rive wins on branching interactive logic.

## 5. When to reach for which

- Designer owns the animation + branching interactivity → **Rive**.
- Importing After Effects motion design into KMP → **Compottie** (Lottie if Android-only).
- Just background blur / frosted glass → **Haze**.
- Just confetti → **ConfettiKit** (KMP) / **Konfetti** (Android).
- Deep 2D character rigging → **Spine** (paid, best) / **DragonBones** (free, abandoned).
- Custom shaders / true bloom / bespoke pixel FX → **Skiko/SkSL** (+ Haze for blur).
- Shipping a game, not UI → **Korge**.
- Simple property/UI transitions → **Compose built-in**.
- One KMP dependency doing FX + particles + motion + puppet + stage/export, authored from JSON / by an LLM, pixel-identical on iOS+Android, no editor or shaders → **KMPMedia** — the only combination the rest of the field can't assemble from a single library.

## Note / to verify
- **KMPMedia's own license** — **MIT** (© 2025 Özge Güner / SolidKey AB), per the repo `LICENSE`. So KMPMedia is the *only* all-in-one here that's both MIT-licensed AND KMP-native with no editor/shaders — Spine is paid, Rive's editor is closed, and the rest are single-purpose.
- **rlottie licensing** is genuinely mixed across forks (LGPL-2.1 vs MIT-with-bundled) — verify per fork.
- **Rive CMP** — only the community Rive-CMP wrapper exists as of these sources; Rive has signaled interest in a first-party CMP runtime — recheck.
- Context: Compose Multiplatform for iOS went **Stable in 1.8.0 (May 2025)**, underpinning every "KMP-native on iOS" claim.
