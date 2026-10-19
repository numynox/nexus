package io.github.numynox.nexus.core.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class AvailableUpdate(
    val versionName: String,
    /** The release page, where the APK can be downloaded and installed. */
    val pageUrl: String,
    val apkUrl: String?,
)

/**
 * Apps are sideloaded from GitHub Releases, so nothing tells an installed app
 * that a newer one exists. This asks GitHub for the latest release carrying
 * this app's tag prefix and compares version codes, which CI writes into the
 * tag as `<prefix><major>.<minor>.<versionCode>`.
 */
class UpdateChecker(private val config: NexusConfig) {

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
    )

    /** Null when up to date, offline, or the repository is not reachable. */
    suspend fun check(): AvailableUpdate? = runCatching {
        HttpClient(OkHttp).use { client ->
            val body = client.get("https://api.github.com/repos/${config.releaseRepository}/releases?per_page=30") {
                header("Accept", "application/vnd.github+json")
            }.bodyAsText()
            NexusJson.decodeFromString<List<Release>>(body)
                .filter { !it.draft && !it.prerelease && it.tagName.startsWith(config.releaseTagPrefix) }
                .mapNotNull { release -> versionCodeOf(release.tagName)?.let { it to release } }
                .maxByOrNull { it.first }
                ?.takeIf { (code, _) -> code > config.versionCode }
                ?.let { (_, release) ->
                    AvailableUpdate(
                        versionName = release.tagName.removePrefix(config.releaseTagPrefix),
                        pageUrl = release.htmlUrl,
                        apkUrl = release.assets.firstOrNull { it.name.endsWith(".apk") }?.downloadUrl,
                    )
                }
        }
    }.getOrNull()

    private fun versionCodeOf(tag: String): Int? =
        tag.removePrefix(config.releaseTagPrefix).substringAfterLast('.').toIntOrNull()
}
