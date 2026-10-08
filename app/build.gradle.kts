import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.HostTest
import com.android.build.api.variant.UnitTest
import dev.rikka.tools.refine.RefineFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.serialization)
    // `dev.rikka.tools.refine` is deliberately NOT applied here: see the `androidComponents` block.
}

/**
 * The git tag this build is published under, e.g. `v1.2.3`. CI sets `RELEASE_TAG`; a local build can
 * pass `-PreleaseTag=v1.2.3` to pose as a release. Empty for an ordinary local build.
 *
 * The tag is also the GitHub release's name, which is the version a GitHub app's row stores — so it
 * is exactly what the app writes into its own row to know which release it is (`SelfAppSeed`,
 * `ReconcileSelfUpdateUseCase`).
 */
val releaseTag: String = providers.environmentVariable("RELEASE_TAG")
    .orElse(providers.gradleProperty("releaseTag"))
    .getOrElse("")
    .trim()

val releaseVersion: ReleaseVersion? = releaseTag.takeIf { it.isNotEmpty() }?.let(::parseReleaseTag)

android {
    namespace = "dev.re7gog.b_sideloader"
    compileSdk {
        version = release(37) {
            //minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.re7gog.b_sideloader"
        minSdk = 26
        targetSdk = 37
        // A release build takes both from its tag, so the version code can never be forgotten:
        // self-update counts an install as landed only when the version code went up. The literals
        // are for untagged local builds.
        versionCode = releaseVersion?.code ?: 2
        versionName = releaseVersion?.name ?: "1.0.1"
        // parseReleaseTag only lets [0-9A-Za-z.-] through, so the tag needs no escaping.
        buildConfigField("String", "RELEASE_TAG", "\"$releaseTag\"")
        // Whether the app seeds a row that tracks its own GitHub releases (`SelfAppSeed`). Only a
        // build that shares the published package can be updated by those releases.
        buildConfigField("boolean", "TRACKS_ITSELF", "true")

        testInstrumentationRunner = "dev.re7gog.b_sideloader.HiltTestRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = System.getenv("ANDROID_KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // R8 keep rules live in `src/<sourceSet>/keepRules/**/*.keep` (AGP 9 source-set
            // convention) instead of `proguardFiles`. The AOSP default optimize rules are
            // pulled in automatically because `keepRules.includeDefault` defaults to true.
            //signingConfig = signingConfigs.getByName("debug")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
        /*
         * A release candidate for the developer's own phone: `dev.re7gog.b_sideloader.dev`, so it
         * installs next to the published app instead of over it, with separate data (database,
         * TDLib session, API keys, settings).
         *
         * Everything else is the release build — R8, resource shrinking, no LeakCanary, no HTTP
         * logging, `BuildConfig.DEBUG` false — because the point is to try what is about to ship,
         * keep rules included. It is signed with the local debug key, so `assembleDev` works
         * without the release keystore; the different package means the signatures never meet.
         *
         * The package suffix is safe: the Shizuku provider authority is `${applicationId}.shizuku`,
         * and the install/uninstall result PendingIntents name their receiver explicitly.
         *
         * It does not track itself: B-SideLoader's releases are published under the release
         * package, so "updating" a dev build to one would install the release app beside it and
         * then offer the same update forever. It also never claims a release tag, even when CI
         * sets one.
         */
        create("dev") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("debug")
            // :tdlib only has debug and release.
            matchingFallbacks += listOf("release")
            buildConfigField("String", "RELEASE_TAG", "\"\"")
            buildConfigField("boolean", "TRACKS_ITSELF", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Every lint check on, every warning fatal: `./gradlew lint` fails on any finding, and CI runs
    // it before a release is built. Project-wide exceptions, each with its reason, are in the root
    // `lint.xml`; single ones go next to the code (`@Suppress` / `tools:ignore`). No baseline:
    // a finding is fixed or explicitly suppressed, never parked.
    lint {
        lintConfig = rootProject.file("lint.xml")
        checkAllWarnings = true
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        // Tests are code too; a leaky test or a wrong API level in one is still a bug.
        checkTestSources = true
        // TDLib wrapper is linted by its own module, against the same rules.
        checkDependencies = false
        // Hilt/Room/KSP output: a finding there cannot be fixed where it is reported.
        checkGeneratedSources = false
        explainIssues = true
        textReport = true
        sarifReport = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    // Room's exported schemas double as the input for the migration tests, which read the old
    // schema from the test classpath (`AppsDatabaseMigrationTest`).
    sourceSets.getByName("test") {
        resources.directories.add(layout.projectDirectory.dir("schemas").asFile.path)
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources/manifest to inflate Compose content.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                // Robolectric's android-all image is large; the default 512 MB test heap is tight
                // once Compose and Room tests share one JVM.
                it.maxHeapSize = "2g"
                // Robolectric's FileDescriptor interceptor, reached while the API 37 image starts,
                // calls `jdk.internal.access.SharedSecrets`, which JDK 17+ does not export.
                it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }
}

/*
 * What the `dev.rikka.tools.refine` plugin would register, minus the part that breaks Robolectric.
 *
 * Refine rewrites references to `@RefineAs` hidden-API stubs (`PackageInstallerHidden` ->
 * `PackageInstaller`). The plugin registers its ASM visitor with `InstrumentationScope.ALL` on
 * *every* component, and its factory claims every class is instrumentable — so on the unit-test
 * classpath AGP re-serialises every class of every dependency jar. Class bytes change, the signed
 * BouncyCastle and Conscrypt jars Robolectric loads fail their signature check, and every
 * Robolectric test dies in class loading with "SHA-256 digest error".
 *
 * So the app and instrumented tests keep exactly what the plugin did, and host (unit) tests only
 * remap this project's own classes, leaving dependency jars untouched. The app's only refined
 * references are in `PrivilegedApkInstaller`, which no unit test runs.
 */
@Suppress("UnstableApiUsage")
androidComponents {
    onVariants { variant ->
        variant.components.forEach { component ->
            val isHostTest = component is HostTest || component is UnitTest
            component.instrumentation.transformClassesWith(
                RefineFactory::class.java,
                if (isHostTest) InstrumentationScope.PROJECT else InstrumentationScope.ALL,
            ) {}
        }
    }
}

ksp {
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
    arg("room.generateKotlin", "true")
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.material3)
    // AppCompat provides the per-app language (locale) backport for API < 33
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.android)
    // Immutable collections are Compose-stable, so UI models can hold lists without
    // silently defeating recomposition skipping.
    implementation(libs.kotlinx.collections.immutable)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Installs itself through a ContentProvider in the debug manifest; nothing reaches release.
    debugImplementation(libs.leakcanary.android)

    // Room
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    // Hilt
    implementation(libs.dagger.hilt)
    ksp(libs.dagger.hilt.compiler)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // Navigation 3
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.serialization)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    // OkHttp
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging)

    // Work
    implementation(libs.work.runtime)

    // Shizuku and Dhizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.dhizuku.api)
    implementation(libs.hidden.api.bypass)

    ksp(libs.refine.annotation.processor)
    compileOnly(libs.refine.annotation)
    implementation(libs.refine.runtime)
    compileOnly(libs.hidden.stub)

    // Telegram
    implementation(project(":tdlib"))

    // Settings
    implementation(libs.datastore.preferences)

    // Notifications
    implementation(libs.accompanist.permissions)

    // On-device AI (Gemini Nano)
    implementation(libs.mlkit.genai.prompt)

    // Compose-specific lint rules (modifier order, state hoisting, naming, ...), run by `lint`.
    lintChecks(libs.compose.lint.checks)

    // ---- Local (JVM) tests ----
    //
    // Pure logic (selection, mappers, use cases, ViewModels, navigation) runs as plain JUnit.
    // Framework-bound code (Room, DataStore, WorkManager, PackageManager, notifications, receivers,
    // Compose screens) runs on Robolectric in the same source set — see `docs/testing.md`.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // ui-test-junit4 drags in espresso-core 3.5.0, whose idling path reflects on
    // InputManager.getInstance() — gone from API 34+, so every screen test on the API 37 image dies
    // in Espresso.onIdle. 3.6.0+ resolves it lazily; declaring it pins the catalog version.
    testImplementation(libs.androidx.espresso.core)
    // Only for the `@AndroidEntryPoint` receivers, which cannot run outside a Hilt application.
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.dagger.hilt.compiler)

    // ---- Instrumented tests ----
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.dagger.hilt.compiler)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

/** What a release tag turns into: `versionName` and `versionCode`. */
data class ReleaseVersion(val name: String, val code: Int)

/**
 * `v1.2.3` -> name `1.2.3`, code `1_02_03_99`; `v1.2.3-rc2` -> name `1.2.3-rc2`, code `1_02_03_02`.
 *
 * The last two digits order a tag's pre-releases below the release itself, by the number that ends
 * the suffix (`-test` counts as 0), so every tag installs over the ones before it. Two pre-releases
 * of the same version need rising numbers (`-rc1`, `-rc2`) to update into each other.
 *
 * A tag that does not fit fails the build: a guessed version code is worse than no release, because
 * a code that does not go up makes the installed app keep offering the same update.
 */
fun parseReleaseTag(tag: String): ReleaseVersion {
    val match = Regex("""v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?""").matchEntire(tag)
        ?: error("Release tag '$tag' is not vMAJOR.MINOR.PATCH[-suffix]")
    val (major, minor, patch) = (1..3).map { match.groupValues[it].toInt() }
    require(major <= 2_099 && minor <= 99 && patch <= 99) {
        "Release tag '$tag' does not fit the version code scheme (major <= 2099, minor/patch <= 99)"
    }
    val suffix = match.groupValues[4]
    val stage = if (suffix.isEmpty()) {
        99
    } else {
        (Regex("""\d+$""").find(suffix)?.value?.toIntOrNull() ?: 0).coerceAtMost(98)
    }
    return ReleaseVersion(
        name = tag.removePrefix("v"),
        code = major * 1_000_000 + minor * 10_000 + patch * 100 + stage,
    )
}
