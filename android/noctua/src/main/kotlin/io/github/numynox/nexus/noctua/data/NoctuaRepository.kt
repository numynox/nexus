package io.github.numynox.nexus.noctua.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Everything a feed screen needs from one load. */
data class FeedContent(
    val sections: List<Section>,
    val articles: List<Article>,
    /** Article id → when it was read; durable, per user (glossary: "read"). */
    val reads: Map<Long, Instant>,
    val starred: Set<Long>,
)

/**
 * The Android counterpart of app/noctua/src/lib/data.ts: every query Noctua
 * makes, and the only code that talks to Supabase. RLS scopes everything to
 * the signed-in user — sections and reads by `user_id`, articles by whether
 * one of the user's sections holds their feed — so the filters on `user_id`
 * here are for the query planner and for clarity, not for security.
 */
class NoctuaRepository(private val supabase: SupabaseClient) {

    private val userId: String
        get() = supabase.auth.currentUserOrNull()?.id ?: error("Not signed in.")

    // ── Sections and feeds ──────────────────────────────────────────────────

    suspend fun sections(): List<Section> =
        supabase.from("sections")
            .select(Columns.raw("id,title,icon,sort_order,sections_feeds(sort_order,feed_id,feeds(id,name,url,icon,enabled))")) {
                filter { eq("user_id", userId) }
                order("sort_order", Order.ASCENDING)
                order("id", Order.ASCENDING)
                order("sort_order", Order.ASCENDING, referencedTable = "sections_feeds")
                order("feed_id", Order.ASCENDING, referencedTable = "sections_feeds")
            }
            .decodeList<SectionRow>()
            .map { it.toSection() }

    /** The shared catalogue: every enabled feed, readable by every user. */
    suspend fun catalogue(): List<Feed> =
        supabase.from("feeds")
            .select(Columns.list("id", "name", "url", "icon", "enabled")) {
                filter { eq("enabled", true) }
                order("name", Order.ASCENDING)
            }
            .decodeList()

    suspend fun createSection(title: String, icon: String) {
        val next = supabase.from("sections")
            .select(Columns.list("id", "sort_order")) {
                filter { eq("user_id", userId) }
                order("sort_order", Order.DESCENDING)
                limit(1)
            }
            .decodeList<SortRow>()
            .firstOrNull()?.sortOrder?.plus(1) ?: 0
        supabase.from("sections").insert(
            buildJsonObject {
                put("user_id", userId)
                put("title", title.trim())
                put("icon", icon.ifBlank { DEFAULT_SECTION_ICON })
                put("sort_order", next)
            },
        )
    }

    suspend fun updateSection(sectionId: Long, title: String, icon: String) {
        supabase.from("sections").update(
            buildJsonObject {
                put("title", title.trim())
                put("icon", icon.ifBlank { DEFAULT_SECTION_ICON })
            },
        ) { filter { eq("id", sectionId); eq("user_id", userId) } }
    }

    suspend fun deleteSection(sectionId: Long) {
        supabase.from("sections").delete { filter { eq("id", sectionId); eq("user_id", userId) } }
    }

    /** Writes the given order of sections; only rows whose position changed are updated. */
    suspend fun reorderSections(ordered: List<Section>) {
        ordered.forEachIndexed { index, section ->
            if (section.sortOrder != index) {
                supabase.from("sections").update(buildJsonObject { put("sort_order", index) }) {
                    filter { eq("id", section.id); eq("user_id", userId) }
                }
            }
        }
    }

    suspend fun addFeed(sectionId: Long, feedId: Long, position: Int) {
        supabase.from("sections_feeds").upsert(
            buildJsonObject {
                put("section_id", sectionId)
                put("feed_id", feedId)
                put("sort_order", position)
            },
        ) {
            onConflict = "section_id,feed_id"
            ignoreDuplicates = true
        }
    }

    suspend fun removeFeed(sectionId: Long, feedId: Long) {
        supabase.from("sections_feeds").delete { filter { eq("section_id", sectionId); eq("feed_id", feedId) } }
    }

    /** Writes [feedIds] as the section's order, top to bottom. */
    suspend fun reorderFeeds(sectionId: Long, feedIds: List<Long>) {
        feedIds.forEachIndexed { index, feedId ->
            supabase.from("sections_feeds").update(buildJsonObject { put("sort_order", index) }) {
                filter { eq("section_id", sectionId); eq("feed_id", feedId) }
            }
        }
    }

    // ── Articles ────────────────────────────────────────────────────────────

    /**
     * The newest [limit] articles of the given feeds — one feed screen's
     * worth. The user's read marks and stars for them are separate calls, so
     * the feed can ask for them side by side.
     */
    suspend fun articles(feeds: List<Feed>, limit: Int): List<Article> {
        val byId = feeds.associateBy { it.id }
        if (byId.isEmpty()) return emptyList()
        return supabase.from("articles")
            .select(Columns.list("id", "feed_id", "title", "url", "published_at", "updated_at", "author", "summary", "image_url", "tags")) {
                filter { isIn("feed_id", byId.keys.toList()) }
                order("published_at", Order.DESCENDING, nullsFirst = false)
                // A stable tiebreak, so two articles published in the same
                // second do not trade places between loads.
                order("id", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<ArticleRow>()
            .map { it.toArticle(byId[it.feedId]) }
    }

    /** When each of these articles was read, in chunks short enough for a URL, fetched together. */
    suspend fun reads(articleIds: List<Long>): Map<Long, Instant> {
        if (articleIds.isEmpty()) return emptyMap()
        return coroutineScope {
            articleIds.chunked(200).map { chunk ->
                async {
                    supabase.from("article_reads")
                        .select(Columns.list("article_id", "read_at")) {
                            filter { eq("user_id", userId); isIn("article_id", chunk) }
                        }
                        .decodeList<ReadRow>()
                }
            }.awaitAll().flatten()
        }.associate { it.articleId to it.readAt }
    }

    suspend fun starredIds(): Set<Long> =
        supabase.from("article_stars")
            .select(Columns.list("article_id")) { filter { eq("user_id", userId) } }
            .decodeList<StarRow>()
            .map { it.articleId }
            .toSet()

    /**
     * Which of these articles are the same story — article id → group key,
     * the lowest id of its cluster (decisions/2026-09-05-duplicate-story-collapsing.md).
     */
    suspend fun similarGroups(articleIds: List<Long>): Map<Long, Long> {
        if (articleIds.isEmpty()) return emptyMap()
        return supabase.postgrest.rpc(
            "get_similar_article_groups",
            buildJsonObject { putJsonArray("p_article_ids") { articleIds.forEach { add(it) } } },
        ).decodeList<GroupRow>().associate { it.articleId to it.groupKey }
    }

    /** Marks articles read in one round trip; also what "mark all read" uses. */
    suspend fun markRead(articleIds: Collection<Long>) {
        if (articleIds.isEmpty()) return
        val at = Instant.now().toString()
        supabase.from("article_reads").upsert(
            JsonArray(
                articleIds.map { id ->
                    buildJsonObject {
                        put("user_id", userId)
                        put("article_id", id)
                        put("read_at", at)
                    }
                },
            ),
        ) {
            onConflict = "user_id,article_id"
        }
    }

    /** Removes read marks for exactly these articles — the undo of [markRead]. */
    suspend fun markUnread(articleIds: Collection<Long>) {
        if (articleIds.isEmpty()) return
        supabase.from("article_reads").delete {
            filter { eq("user_id", userId); isIn("article_id", articleIds.toList()) }
        }
    }

    suspend fun star(articleId: Long) {
        supabase.from("article_stars").upsert(
            buildJsonObject {
                put("user_id", userId)
                put("article_id", articleId)
                put("starred_at", Instant.now().toString())
            },
        ) {
            onConflict = "user_id,article_id"
            ignoreDuplicates = true
        }
    }

    suspend fun unstar(articleId: Long) {
        supabase.from("article_stars").delete { filter { eq("user_id", userId); eq("article_id", articleId) } }
    }

    /** Starred articles, newest star first — straight from the stars, not only the loaded feed. */
    suspend fun starredArticles(): List<DatedArticle> =
        supabase.from("article_stars")
            .select(Columns.raw("article_id,starred_at,articles(${ArticleColumns},feeds(id,name,icon))")) {
                filter { eq("user_id", userId) }
                order("starred_at", Order.DESCENDING)
            }
            .decodeList<StarRow>()
            .mapNotNull { row -> row.articles?.let { DatedArticle(it.toArticle(null), row.starredAt ?: Instant.EPOCH) } }

    /** What was read, most recent first. */
    suspend fun readArticles(limit: Int): List<DatedArticle> =
        supabase.from("article_reads")
            .select(Columns.raw("article_id,read_at,articles(${ArticleColumns},feeds(id,name,icon))")) {
                filter { eq("user_id", userId) }
                order("read_at", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<ReadRow>()
            .mapNotNull { row -> row.articles?.let { DatedArticle(it.toArticle(null), row.readAt) } }

    /** What was read since [since], oldest first: the stars over the end of the feed. */
    suspend fun readSince(since: Instant): List<DatedArticle> =
        supabase.from("article_reads")
            .select(Columns.raw("article_id,read_at,articles(${ArticleColumns},feeds(id,name,icon))")) {
                filter { eq("user_id", userId); gte("read_at", since.toString()) }
                order("read_at", Order.ASCENDING)
                limit(TodayLimit.toLong())
            }
            .decodeList<ReadRow>()
            .filter { !it.readAt.isBefore(since) }
            .sortedBy { it.readAt }
            .mapNotNull { row -> row.articles?.let { DatedArticle(it.toArticle(null), row.readAt) } }

    /** Wipes the durable read history; statistics start over. */
    suspend fun clearReadHistory() {
        supabase.from("article_reads").delete { filter { eq("user_id", userId) } }
    }

    // ── Statistics ──────────────────────────────────────────────────────────

    /** The statistics over the last [weeks] weeks, counted by the database. */
    suspend fun readingStatistics(weeks: Int): ReadingStatisticsRow =
        supabase.postgrest.rpc("noctua_reading_statistics", buildJsonObject { put("p_weeks", weeks) })
            .decodeAs<ReadingStatisticsRow>()

    private companion object {
        /** More stars than any day's reading, and than the sky has room for. */
        const val TodayLimit = 150
        const val ArticleColumns = "id,feed_id,title,url,published_at,updated_at,author,summary,image_url,tags"
    }
}

@kotlinx.serialization.Serializable
internal data class SortRow(
    val id: Long,
    @kotlinx.serialization.SerialName("sort_order") val sortOrder: Int? = null,
)
