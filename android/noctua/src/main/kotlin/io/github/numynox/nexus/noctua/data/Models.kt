@file:UseSerializers(InstantSerializer::class)

package io.github.numynox.nexus.noctua.data

import io.github.numynox.nexus.core.data.InstantSerializer
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// Row shapes, hand-written like the web app's lib/types.ts and lib/data.ts
// (decisions/2026-09-04-permissive-supabase-client-generics.md). Column names
// are the database's; property names are Kotlin's.

/** A feed from the shared catalogue. `icon` is a favicon URL, or null. */
@Serializable
data class Feed(
    val id: Long,
    val name: String,
    val url: String = "",
    val icon: String? = null,
    val enabled: Boolean = true,
)

/**
 * A user's named, ordered grouping of feeds. [icon] is a key from
 * `SectionIcons` (the web's SECTION_ICONS), never an emoji; sections have no
 * colour (decisions/2026-09-06-sections-have-no-colour.md).
 */
data class Section(
    val id: Long,
    val name: String,
    val icon: String,
    val sortOrder: Int,
    val feeds: List<Feed>,
)

@Serializable
internal data class SectionRow(
    val id: Long,
    val title: String,
    val icon: String? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("sections_feeds") val sectionFeeds: List<SectionFeedRow> = emptyList(),
) {
    fun toSection() = Section(
        id = id,
        name = title,
        icon = icon?.takeIf { it.isNotBlank() } ?: DEFAULT_SECTION_ICON,
        sortOrder = sortOrder ?: 0,
        feeds = sectionFeeds.mapNotNull { join -> join.feed?.takeIf { it.enabled } },
    )
}

@Serializable
internal data class SectionFeedRow(
    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("feed_id") val feedId: Long,
    @SerialName("feeds") val feed: Feed? = null,
)

/** One entry from a feed. [summary] is the feed's own description — never generated. */
data class Article(
    val id: Long,
    val feedId: Long,
    val feedName: String,
    val feedIcon: String?,
    val title: String,
    val url: String,
    val published: Instant?,
    val updated: Instant?,
    val author: String?,
    val summary: String?,
    val imageUrl: String?,
    val tags: List<String>,
) {
    /** What the list sorts and groups by: published, else updated. */
    val time: Instant? get() = published ?: updated
}

@Serializable
internal data class ArticleRow(
    val id: Long,
    @SerialName("feed_id") val feedId: Long,
    val title: String? = null,
    val url: String = "",
    @SerialName("published_at") val publishedAt: Instant? = null,
    @SerialName("updated_at") val updatedAt: Instant? = null,
    val author: String? = null,
    val summary: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val tags: List<String>? = null,
    /** Embedded through `feeds(…)` where the query asks for it. */
    val feeds: Feed? = null,
) {
    fun toArticle(feed: Feed?) = Article(
        id = id,
        feedId = feedId,
        feedName = feed?.name ?: feeds?.name ?: "Unknown feed",
        feedIcon = feed?.icon ?: feeds?.icon,
        title = title?.takeIf { it.isNotBlank() } ?: "Untitled",
        url = url,
        published = publishedAt,
        updated = updatedAt,
        author = author?.takeIf { it.isNotBlank() },
        summary = summary?.let(::plainText)?.takeIf { it.isNotBlank() },
        imageUrl = imageUrl?.takeIf { it.startsWith("http") },
        tags = tags.orEmpty(),
    )
}

/** An article with when it was read or starred, for the library lists. */
data class DatedArticle(val article: Article, val at: Instant)

@Serializable
internal data class ReadRow(
    @SerialName("article_id") val articleId: Long,
    @SerialName("read_at") val readAt: Instant,
    val articles: ArticleRow? = null,
)

@Serializable
internal data class StarRow(
    @SerialName("article_id") val articleId: Long,
    @SerialName("starred_at") val starredAt: Instant? = null,
    val articles: ArticleRow? = null,
)

@Serializable
internal data class GroupRow(
    @SerialName("article_id") val articleId: Long,
    @SerialName("group_key") val groupKey: Long,
)

/** One read, with its feed — what the statistics are computed from. */
data class ReadRecord(val articleId: Long, val readAt: Instant, val feedName: String)

@Serializable
internal data class ReadStatisticsRow(
    @SerialName("article_id") val articleId: Long,
    @SerialName("read_at") val readAt: Instant,
    val articles: ArticleFeedRow? = null,
)

@Serializable
internal data class ArticleFeedRow(
    @SerialName("feed_id") val feedId: Long? = null,
    @SerialName("published_at") val publishedAt: Instant? = null,
    val feeds: FeedName? = null,
)

@Serializable
internal data class FeedName(val name: String? = null)

/** Feed descriptions often carry markup; the list shows text. */
internal fun plainText(html: String): String =
    html.replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("&#(\\d+);")) { match -> match.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
        .replace(Regex("\\s+"), " ")
        .trim()
