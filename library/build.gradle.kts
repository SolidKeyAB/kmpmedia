import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import java.util.Properties

// Flexible version logic: Use -Pversion=... if passed; otherwise fallback
// e.g. ./gradlew publish -Pversion=1.2.3
// Every Gradle project always has a `version` property (defaults to the literal
// "unspecified"), so we must treat that default as "not set" — otherwise -Pversion
// is never needed and LIBRARY_VERSION_FALLBACK would be dead. CI passes -Pversion=<tag>.
version = when {
    project.hasProperty("version") && project.property("version") != "unspecified" ->
        project.property("version") as String
    project.hasProperty("LIBRARY_VERSION_FALLBACK") -> project.property("LIBRARY_VERSION_FALLBACK") as String
    else -> "1.0.0-SNAPSHOT"
}
group = project.findProperty("GROUP") as String

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)  // Serialization must come before Compose (if needed)
    alias(libs.plugins.compose.multiplatform)// Compose must be applied before compose.compiler
    alias(libs.plugins.compose.compiler)     // Compiler plugin (should be after compose.multiplatform)
    id("maven-publish")
    id("com.vanniktech.maven.publish") version "0.29.0"
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvmToolchain(17)

    val xcf = XCFramework()

    // iOS is a first-class target and is always configured. Building/publishing the
    // iOS artifacts requires a macOS runner with Xcode.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "library"   // ✅ Ensures consistent framework name
            isStatic = false      // ✅ Optional: Ensure dynamic framework if needed
            xcf.add(this)         // ✅ Add each framework to the XCFramework
        }
    }

    sourceSets {

        val commonMain by getting {
            dependencies {
                // ✅ Expose Kotlin Standard Library
//                api(kotlin("stdlib"))
//                api(kotlin("stdlib-common"))

                // ✅ Expose Coroutines (sample app relies on it)
                api(libs.kotlinx.coroutines.core)

                // ✅ Expose Serialization (sample app needs it)
                api(libs.kotlinx.serialization.core)
                api(libs.kotlinx.serialization.json) // JSON codec for the AI-interop layer (OGAiVector)

                // ✅ Expose Ktor (sample app likely does networking)
                api(libs.ktor.client.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)

                // ✅ Expose JetBrains Compose dependencies
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material3)

                // ✅ Expose Animations (if sample app uses them)
                api(libs.animation)

                // ✅ Internal dependencies (only used inside the library)
                implementation(compose.ui)
                implementation(libs.ui)

                implementation(libs.foundation)
                implementation(libs.material3)
                implementation(libs.runtime)
                implementation(libs.kermit)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(compose.ui)
                implementation(compose.material3)
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.media3.exoplayer) // Latest as of Feb 2025
                implementation(libs.androidx.media3.ui)
                implementation(libs.compose.runtime.runtime)

                implementation(libs.androidx.activity.compose) // ✅ Required for `setContent` in Android
                implementation(libs.androidx.runtime.android) // ✅ Android-specific Compose runtime
                implementation(libs.androidx.ui.android) // ✅ Android UI utilities
                implementation(libs.androidx.ui.unit.android) // ✅ Android UI unit utilities
                implementation(libs.compose.ui.tooling.preview) // ✅ Android Studio preview
            }
        }

        val iosMain by creating {
            dependencies {
                implementation(libs.runtime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(compose.ui)
                api(libs.ktor.client.darwin) // Darwin engine for iOS
                implementation(libs.kermit) //Add latest version
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit) // JUnit support
                implementation(libs.kotlinx.coroutines.test) // Coroutine testing
            }
        }

    }
}

android {
    buildTypes {
        getByName("debug") {
            dependencies {
                implementation(compose.uiTooling)
            }
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildFeatures {
        viewBinding = false
        dataBinding = false
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.7.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    namespace = "com.solidkey.painpoints"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

publishing {
    val gprUser = (localProps.getProperty("gpr.user") ?: System.getenv("GPR_USER"))
    val gprKey = (localProps.getProperty("gpr.key") ?: System.getenv("GPR_TOKEN"))

    // ✅ Fail only if we’re actually publishing
    if (project.hasProperty("publishToGithub")) {
        checkNotNull(gprUser) { "Missing GitHub username in local.properties or environment!" }
        checkNotNull(gprKey) { "Missing GitHub token in local.properties or environment!" }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/SolidKeyAB/kmpmedia")
            credentials {
                username = gprUser
                password = gprKey
            }
        }
    }
}



// Maven Central publishing via the vanniktech plugin.
// Coordinates are read automatically from gradle.properties (GROUP,
// POM_ARTIFACT_ID) and the project version; the POM metadata (name, description,
// url, license, developer, scm) is read from the POM_* properties. Setting
// coordinates() again here would re-assign values the plugin has already
// finalized, so it is intentionally omitted.
mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    // Maven Central requires signed artifacts; GitHub Packages and local builds do not.
    // Only sign when a signing key is provided (via ORG_GRADLE_PROJECT_signingInMemoryKey
    // or -PsigningInMemoryKey) so the GitHub Packages release flow keeps working unsigned.
    if (project.hasProperty("signingInMemoryKey") ||
        System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null
    ) {
        signAllPublications()
    }
}

// ── OG Manifest generator ─────────────────────────────────────────────────────
// Produces docs/OG_MANIFEST.md — a compact, AI-friendly index of every public
// primitive in the library. Run manually or hook into your release workflow:
//
//   ./gradlew generateOgManifest
//
// The file is fully derived from source; never edit it by hand.
// ─────────────────────────────────────────────────────────────────────────────
tasks.register("generateOgManifest") {
    group = "documentation"
    description = "Generates docs/OG_MANIFEST.md — compact AI-friendly index of every library primitive."

    val sourceDir = file("src/commonMain/kotlin/com/solidkey/painpoints")
    val outputFile = rootProject.file("docs/OG_MANIFEST.md")

    inputs.dir(sourceDir)
    outputs.file(outputFile)

    doLast {
        // ── Helpers ───────────────────────────────────────────────────────────

        fun fieldsOf(src: String, className: String): List<String> {
            val start = src.indexOf("data class $className(").takeIf { it >= 0 } ?: return emptyList()
            var depth = 0; val buf = StringBuilder(); var i = src.indexOf('(', start)
            while (i < src.length) {
                val c = src[i]
                if (c == '(') depth++ else if (c == ')') { depth--; if (depth == 0) break }
                buf.append(c); i++
            }
            return Regex("val\\s+(\\w+)\\s*:").findAll(buf).map { it.groupValues[1] }
                .filter { it != "name" }.toList()
        }

        fun descOf(src: String): String {
            val kdoc = Regex("/\\*\\*(.*?)\\*/", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(src)?.groupValues?.get(1) ?: return ""
            val line = kdoc.lines().map { it.trim().trimStart('*').trim() }
                .firstOrNull { it.isNotBlank() && !it.startsWith("@") } ?: return ""
            val after = Regex("\\*\\*.*?\\*\\*\\s*[—–-]\\s*(.+)").find(line)?.groupValues?.get(1) ?: line
            return after.replace(Regex("\\[([^]]+)\\](?:\\([^)]*\\))?"), "$1")
                .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
                .substringBefore(". ").substringBefore(".").trim()
        }

        fun presetsOf(src: String, name: String): List<String> =
            Regex("val\\s+([A-Z][A-Za-z0-9_]+)\\s*=\\s*$name(?:Spec)?\\s*\\(")
                .findAll(src).map { it.groupValues[1] }.toList()

        fun viewOf(src: String, name: String): String? {
            val m = Regex("fun\\s+(${name}View)\\s*\\(([^)]+)\\)").find(src) ?: return null
            val params = m.groupValues[2].split(",").mapNotNull { p ->
                val n = p.trim().removePrefix("val ").removePrefix("var ")
                    .substringBefore(":").substringBefore("=").trim()
                n.takeIf { it.isNotBlank() && it != "modifier" }
            }
            return "${m.groupValues[1]}(${params.joinToString(", ")})"
        }

        fun dirSrc(dir: java.io.File): String =
            dir.listFiles()?.filter { it.extension == "kt" }?.joinToString("\n") { it.readText() } ?: ""

        fun renderBlock(primitive: String, desc: String, fields: List<String>, presets: List<String>, view: String?): String =
            buildString {
                appendLine("### $primitive")
                if (desc.isNotBlank()) appendLine("$desc.")
                if (fields.isNotEmpty()) appendLine("Fields: `${fields.joinToString("` `")}`")
                if (presets.isNotEmpty()) appendLine("Presets: `${presets.joinToString("` `") { it.lowercase() }}`")
                if (view != null) appendLine("View: `$view`")
            }

        // ── Scan ──────────────────────────────────────────────────────────────
        val seen = mutableSetOf<String>()
        val byPkg = linkedMapOf<String, MutableList<String>>()

        fun record(primitive: String, pkg: String, desc: String, fields: List<String>, presets: List<String>, view: String?) {
            if (!seen.add(primitive)) return
            byPkg.getOrPut(pkg) { mutableListOf() } += renderBlock(primitive, desc, fields, presets, view)
        }

        // Pass 1: OG*Spec.kt — authoritative
        sourceDir.walkTopDown()
            .filter { it.name.startsWith("OG") && it.name.endsWith("Spec.kt") }
            .sortedBy { it.name }
            .forEach specLoop@{ f ->
                val primitive = f.nameWithoutExtension.removeSuffix("Spec")
                val pkg = f.parentFile.relativeTo(sourceDir).path.replace("\\", "/")
                val specSrc = f.readText()
                val poolSrc = dirSrc(f.parentFile)
                val cls = if (specSrc.contains("data class ${primitive}Spec(")) "${primitive}Spec" else primitive
                record(primitive, pkg, descOf(specSrc), fieldsOf(specSrc, cls), presetsOf(poolSrc, primitive), viewOf(poolSrc, primitive))
            }

        // Pass 2: primitives with embedded data class + codec in same file
        sourceDir.walkTopDown()
            .filter { it.name.startsWith("OG") && it.name.endsWith(".kt") && !it.name.endsWith("Spec.kt") && !it.name.endsWith("View.kt") }
            .sortedBy { it.name }
            .forEach fileLoop@{ f ->
                val src = f.readText()
                val hasPresets = src.contains("val presets") || src.contains("fun decodeSpec") ||
                    Regex("object\\s+OG\\w+s\\s*\\{").containsMatchIn(src)
                if (!hasPresets) return@fileLoop
                val pkg = f.parentFile.relativeTo(sourceDir).path.replace("\\", "/")
                val poolSrc = dirSrc(f.parentFile)
                val filePrimitive = f.nameWithoutExtension
                Regex("data class (OG[A-Za-z0-9]+)\\s*\\(").findAll(src).forEach innerLoop@{ m ->
                    val cls = m.groupValues[1]
                    val primitive = cls.removeSuffix("Spec")
                    if (primitive != filePrimitive) return@innerLoop
                    record(primitive, pkg, descOf(src), fieldsOf(src, cls), presetsOf(poolSrc, primitive), viewOf(poolSrc, primitive))
                }
            }

        // ── Render ────────────────────────────────────────────────────────────
        val sorted = byPkg.entries.sortedBy { it.key }
        val total = seen.size

        val out = buildString {
            appendLine("# OGManifest — KMPMedia Library Primitives")
            appendLine()
            appendLine("> Auto-generated by `./gradlew generateOgManifest`. Do **not** edit by hand.")
            appendLine("> **$total primitives** across **${sorted.size} packages**.")
            appendLine()
            appendLine("## Quick usage")
            appendLine("```kotlin")
            appendLine("// Spec (serializable data, AI-authorable JSON) + drop-in View")
            appendLine("OGFlameView(OGFlame.CAMPFIRE, Modifier.fillMaxSize())")
            appendLine("OGDigitalRainView(OGDigitalRain.MATRIX, Modifier.fillMaxSize())")
            appendLine("// Timing primitive — no View")
            appendLine("val ramp = remember { OGTimeRampValue(OGTimeRamp.BULLET_TIME) }")
            appendLine("ramp.trigger() // on slow-mo moment")
            appendLine("val dt = rawDt * ramp.timeScale // in game loop")
            appendLine("```")
            appendLine()
            appendLine("---")
            appendLine()
            sorted.forEach { (pkg, blocks) ->
                appendLine("## `$pkg`")
                appendLine()
                blocks.forEach { appendLine(it) }
            }
        }

        outputFile.parentFile.mkdirs()
        outputFile.writeText(out)
        println("✅  OG_MANIFEST.md — $total primitives in ${sorted.size} packages → ${outputFile.path}")
    }
}

