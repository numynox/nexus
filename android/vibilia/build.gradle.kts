plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// ── Build-time configuration ─────────────────────────────────────────────────
// The same sources the websites read, so nothing is configured twice:
//   * PUBLIC_SUPABASE_URL / PUBLIC_SUPABASE_ANON_KEY / PUBLIC_CARTO_API_KEY from
//     the environment (CI repository variables) or the repository's root .env
//   * Vibilia's price bucket and nearby radius from config.yaml

val repositoryRoot: File = rootDir.parentFile

val dotEnv: Map<String, String> = repositoryRoot.resolve(".env").takeIf { it.isFile }
    ?.readLines()
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
    ?.associate { line -> line.substringBefore("=").trim() to line.substringAfter("=").trim().trim('"') }
    .orEmpty()

fun nexusSetting(key: String): String =
    providers.environmentVariable(key).orNull?.takeIf { it.isNotBlank() } ?: dotEnv[key].orEmpty()

val configYaml: String = repositoryRoot.resolve("config.yaml").takeIf { it.isFile }?.readText().orEmpty()

fun vibiliaYamlNumber(key: String, default: String): String =
    Regex("""^\s+$key:\s*([0-9.]+)""", RegexOption.MULTILINE).find(configYaml)?.groupValues?.get(1) ?: default

// CI passes the run number; local builds are version code 1.
val nexusVersionCode: Int = providers.environmentVariable("NEXUS_VERSION_CODE").orNull?.toIntOrNull() ?: 1

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// ── Signing ──────────────────────────────────────────────────────────────────
// Release builds are signed with the key CI decodes from repository secrets
// (DEPLOYMENT.md). Without it a release build falls back to the debug key, which
// is fine for trying a release build locally but cannot update an installed app.
val releaseKeystore: File? = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull?.let(::File)?.takeIf { it.isFile }

android {
    namespace = "io.github.numynox.nexus.vibilia"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.numynox.nexus.vibilia"
        minSdk = 29
        targetSdk = 36
        versionCode = nexusVersionCode
        versionName = "1.0.$nexusVersionCode"

        buildConfigField("String", "SUPABASE_URL", nexusSetting("PUBLIC_SUPABASE_URL").asBuildConfigString())
        buildConfigField("String", "SUPABASE_ANON_KEY", nexusSetting("PUBLIC_SUPABASE_ANON_KEY").asBuildConfigString())
        buildConfigField("String", "CARTO_API_KEY", nexusSetting("PUBLIC_CARTO_API_KEY").asBuildConfigString())
        buildConfigField("String", "RELEASE_REPOSITORY", "\"numynox/nexus\"")
        buildConfigField("int", "PRICE_BUCKET_MINUTES", vibiliaYamlNumber("price_bucket_minutes", "10"))
        buildConfigField("double", "SEARCH_RADIUS_KM", vibiliaYamlNumber("search_radius", "3").let { if ("." in it) it else "$it.0" })

        // The map's native library is large, so release builds carry only
        // arm64, which every current phone is. Debug adds the emulators' x86_64
        // (build-type filters are merged with these, never narrowed).
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            ndk {
                abiFilters += "x86_64"
            }
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

dependencies {
    implementation(project(":core:ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.maplibre.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
