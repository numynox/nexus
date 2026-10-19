import java.io.File
import org.gradle.api.Project

/**
 * Build-time configuration from the sources the websites read, so nothing is
 * configured twice:
 *   * `PUBLIC_*` values from the environment (CI repository variables) or the
 *     repository's root `.env`
 *   * each app's block in `config.yaml`
 *
 * Available in every app's build script as `nexus`.
 */
open class NexusBuildSettings(project: Project) {
    private val repositoryRoot: File = project.rootDir.parentFile
    private val environment = project.providers

    private val dotEnv: Map<String, String> by lazy {
        repositoryRoot.resolve(".env").takeIf { it.isFile }
            ?.readLines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
            ?.associate { line -> line.substringBefore("=").trim() to line.substringAfter("=").trim().trim('"') }
            .orEmpty()
    }

    private val configYaml: List<String> by lazy {
        repositoryRoot.resolve("config.yaml").takeIf { it.isFile }?.readLines().orEmpty()
    }

    /** An environment variable, else the root `.env`'s value, else empty. */
    fun setting(key: String): String =
        environment.environmentVariable(key).orNull?.takeIf { it.isNotBlank() } ?: dotEnv[key].orEmpty()

    /**
     * A scalar from `settings.<app>` in `config.yaml`. Read with a line scan,
     * not a YAML parser: the file is flat, and `packages/config` stays the one
     * real reader of it.
     */
    fun configValue(app: String, key: String, default: String): String {
        val start = configYaml.indexOfFirst { it.trimEnd() == "  $app:" }
        if (start < 0) return default
        return configYaml.drop(start + 1)
            .takeWhile { it.isBlank() || it.startsWith("    ") }
            .firstNotNullOfOrNull { line ->
                Regex("""^\s{4}$key:\s*"?([^"#]*?)"?\s*(#.*)?$""").find(line)?.groupValues?.get(1)
            }
            ?.takeIf { it.isNotBlank() }
            ?: default
    }

    /** As [configValue], formatted as a Kotlin int literal for a BuildConfig field. */
    fun configInt(app: String, key: String, default: Int): String =
        (configValue(app, key, "$default").toDoubleOrNull()?.toInt() ?: default).toString()

    /** As [configValue], formatted as a Kotlin double literal for a BuildConfig field. */
    fun configDouble(app: String, key: String, default: Double): String =
        (configValue(app, key, "$default").toDoubleOrNull() ?: default).toString()
}

/** A string as a Java literal, for `buildConfigField("String", …)`. */
fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
