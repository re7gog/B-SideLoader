import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.drinkless.tdlib"
    compileSdk {
        version = release(37) {
            //minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                val maskSecret = getSecret("MASK_SECRET", "0")
                val idSecret = getSecret("ID_SECRET", "0")
                val hashSecret = getSecret("HASH_SECRET", "INSERT_YOURSELF!")

                cppFlags("-DMASK_SECRET=$maskSecret", "-DID_SECRET=$idSecret", "-DHASH_SECRET=\"\\\"$hashSecret\\\"\"")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories.add("src/main/libs")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.androidx.annotation)
}

fun getSecret(name: String, defaultValue: String): String {
    // GitHub Actions
    val envValue = System.getenv(name)
    if (!envValue.isNullOrEmpty()) return envValue

    // Local build
    val localProps = File(project.rootDir, "local.properties")
    if (localProps.exists()) {
        val properties = Properties().apply {
            localProps.inputStream().use { load(it) }
        }
        val propValue = properties.getProperty(name)
        if (!propValue.isNullOrEmpty()) return propValue
    }
    return defaultValue
}