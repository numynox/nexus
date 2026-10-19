import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** A shared module under `core/`: `:core:ui` becomes `io.github.numynox.nexus.core.ui`. */
class NexusLibraryPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            namespace = "io.github.numynox.nexus." + path.removePrefix(":").replace(':', '.')
            compileSdk = NexusSdk.COMPILE

            defaultConfig {
                minSdk = NexusSdk.MIN
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }

        optInToExpressive()
    }
}
