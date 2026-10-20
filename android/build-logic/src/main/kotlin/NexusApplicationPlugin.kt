import com.android.build.api.dsl.ApplicationExtension
import java.io.File
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Everything a Nexus app module has in common. The module is named after the
 * app (`:vibilia`, `:noctua`, `:annona`), and that name gives the package,
 * the application id and the release tag; its build script adds only what is
 * its own — dependencies and app-specific BuildConfig fields.
 */
class NexusApplicationPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        pluginManager.apply("io.github.takahirom.roborazzi")

        val settings = extensions.create("nexus", NexusBuildSettings::class.java, this)
        val app = name

        // ── Version ──────────────────────────────────────────────────────────
        // The one place the version is set, for all three apps. CI passes its
        // run number, which becomes the version code and the last part of the
        // name; local builds are 1. CI reads the name back with
        // `printVersionName` for the APK file and the release tag, and the
        // in-app update check compares only the run number, so the prefix can
        // change freely.
        val versionCode = providers.environmentVariable("NEXUS_VERSION_CODE").orNull?.toIntOrNull() ?: 1
        val versionName = "$VERSION_PREFIX.$versionCode"
        tasks.register("printVersionName") {
            doLast { println(versionName) }
        }

        // ── Signing ──────────────────────────────────────────────────────────
        // Release builds are signed with the key CI decodes from repository
        // secrets (DEPLOYMENT.md), one key for every app. Without it a release
        // build falls back to the debug key, which is fine for trying a release
        // build locally but cannot update an installed app.
        val releaseKeystore: File? = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
            ?.let(::File)?.takeIf { it.isFile }

        extensions.configure<ApplicationExtension> {
            namespace = "io.github.numynox.nexus.$app"
            compileSdk = NexusSdk.COMPILE

            defaultConfig {
                applicationId = "io.github.numynox.nexus.$app"
                minSdk = NexusSdk.MIN
                targetSdk = NexusSdk.TARGET
                this.versionCode = versionCode
                this.versionName = versionName

                buildConfigField("String", "SUPABASE_URL", settings.setting("PUBLIC_SUPABASE_URL").asBuildConfigString())
                buildConfigField("String", "SUPABASE_ANON_KEY", settings.setting("PUBLIC_SUPABASE_ANON_KEY").asBuildConfigString())
                buildConfigField("String", "RELEASE_REPOSITORY", RELEASE_REPOSITORY.asBuildConfigString())
                buildConfigField("String", "RELEASE_TAG_PREFIX", "$app-android-v".asBuildConfigString())

                // Release builds carry only arm64, which every current phone
                // is; native libraries (the map, the barcode reader) are large.
                // Debug adds the emulators' x86_64 (build-type filters are
                // merged with these, never narrowed).
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
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
                }
                getByName("debug") {
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

        optInToExpressive()

        dependencies {
            add("implementation", project(":core:ui"))
            add("testImplementation", project(":core:testing"))
            add("debugImplementation", libs.lib("compose-ui-test-manifest"))
        }
    }

    private companion object {
        const val VERSION_PREFIX = "0.0"
        const val RELEASE_REPOSITORY = "numynox/nexus"
    }
}
