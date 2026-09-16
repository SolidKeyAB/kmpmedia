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

