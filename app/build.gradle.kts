plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.refine)
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
            // Keeps the two builds distinguishable in logs/crash reports without a suffix,
            // which would break the Shizuku provider authority and the install receivers.
            isMinifyEnabled = false
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

    // Room's exported schemas double as the input for automated migration tests.
    sourceSets.getByName("androidTest") {
        assets.directories.add(layout.projectDirectory.dir("schemas").asFile.path)
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources/manifest to inflate Compose content.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
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

    // ---- Local (JVM) tests ----
    //
    // Everything that does not need the Android framework runs here: domain selection logic,
    // mappers, use cases, ViewModels and the navigation state machine. Robolectric is deliberately
    // absent — see `docs/testing.md` for why, and for how to re-enable it.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.arch.core.testing)

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
