package io.github.numynox.nexus.core.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class AvailableUpdate(
    val versionName: String,
    /** The run number CI wrote into the tag; what Android compares. */
    val versionCode: Long,
    /** The release page, for builds that cannot install the APK themselves. */
    val pageUrl: String,
    val apkUrl: String?,
)

/**
 * Apps are sideloaded from GitHub Releases, so nothing tells an installed app
 * that a newer one exists. This asks GitHub for the releases carrying this
 * app's tag prefix and picks the highest version code, which CI writes into
 * the tag as `<prefix><major>.<minor>.<versionCode>`. Unauthenticated: the
 * repository is public, and 60 requests an hour is plenty for a daily check.
 */
class UpdateChecker(
    private val repository: String,
    private val tagPrefix: String,
    private val engine: HttpClientEngine? = null,
) {
    constructor(config: NexusConfig, engine: HttpClientEngine? = null) :
        this(config.releaseRepository, config.releaseTagPrefix, engine)

    @Serializable
    internal data class Release(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    internal data class Asset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
    )

    /** The newest release, or null when offline or the repository is not reachable. */
    suspend fun latest(): AvailableUpdate? = runCatching {
        (engine?.let { HttpClient(it) } ?: HttpClient(OkHttp)).use { client ->
            // Every app releases into the same repository, so one page of 100
            // keeps this app's newest in view however busy the others are.
            val body = client.get("https://api.github.com/repos/$repository/releases?per_page=100") {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "Nexus-Android")
            }.bodyAsText()
            newest(body, tagPrefix)
        }
    }.getOrNull()

    /** The newest release if it is newer than [installedVersionCode], else null. */
    suspend fun check(installedVersionCode: Long): AvailableUpdate? =
        latest()?.takeIf { it.versionCode > installedVersionCode }

    companion object {
        /** The highest version code among [tagPrefix]'s published releases in GitHub's [json]. */
        fun newest(json: String, tagPrefix: String): AvailableUpdate? =
            NexusJson.decodeFromString<List<Release>>(json)
                .filter { !it.draft && !it.prerelease && it.tagName.startsWith(tagPrefix) }
                .mapNotNull { release -> versionCodeOf(release.tagName, tagPrefix)?.let { it to release } }
                .maxByOrNull { it.first }
                ?.let { (code, release) ->
                    AvailableUpdate(
                        versionName = release.tagName.removePrefix(tagPrefix),
                        versionCode = code,
                        pageUrl = release.htmlUrl,
                        apkUrl = release.assets.firstOrNull { it.name.endsWith(".apk") }?.downloadUrl,
                    )
                }

        private fun versionCodeOf(tag: String, prefix: String): Long? =
            tag.removePrefix(prefix).substringAfterLast('.').toLongOrNull()
    }
}
