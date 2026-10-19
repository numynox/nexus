import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/** SDK levels for every module (decisions/2026-10-19-native-android-apps.md). */
internal object NexusSdk {
    const val COMPILE = 37
    // One behind compile until the app has run on a device with the newer
    // platform's behaviour changes; nothing forces it, the apps are sideloaded.
    const val TARGET = 36
    const val MIN = 29
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/** Material 3 Expressive is still experimental API; every module uses it. */
internal fun Project.optInToExpressive() {
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions.optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}
