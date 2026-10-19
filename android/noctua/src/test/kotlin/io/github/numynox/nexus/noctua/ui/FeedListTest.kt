package io.github.numynox.nexus.noctua.ui

import io.github.numynox.nexus.noctua.data.Article
import io.github.numynox.nexus.noctua.data.SortOrder
import io.github.numynox.nexus.noctua.data.plainText
import io.github.numynox.nexus.noctua.ui.feed.buildFeed
import io.github.numynox.nexus.noctua.ui.feed.dayLabel
import io.github.numynox.nexus.noctua.ui.feed.relativeAge
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedListTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 19)
    private fun at(day: Int, hour: Int) = LocalDate.of(2026, 10, day).atTime(hour, 0).toInstant(zone)

    private fun article(id: Long, time: Instant?, feed: Long = 1, title: String = "Story $id") = Article(
        id = id, feedId = feed, feedName = "Feed $feed", feedIcon = null, title = title, url = "https://example.com/$id",
        published = time, updated = null, author = null, summary = null, imageUrl = null, tags = emptyList(),
    )

    private fun build(
        articles: List<Article>,
        hidden: Set<Long> = emptySet(),
        query: String = "",
        feed: Long? = null,
        order: SortOrder = SortOrder.Newest,
        groups: Map<Long, Long> = emptyMap(),
    ) = buildFeed(articles, hidden, query, feed, order, groups, today, zone, Locale.ENGLISH)

    @Test
    fun groupsByDayNewestFirst() {
        val days = build(listOf(article(1, at(17, 9)), article(2, at(19, 8)), article(3, at(18, 20)), article(4, at(19, 12))))
        assertEquals(listOf("Today", "Yesterday", "Saturday"), days.map { it.label })
        assertEquals(listOf(4L, 2L), days[0].items.map { it.article.id })
    }

    @Test
    fun oldestFirstReversesTheDays() {
        val days = build(listOf(article(1, at(17, 9)), article(2, at(19, 8))), order = SortOrder.Oldest)
        assertEquals(listOf("Saturday", "Today"), days.map { it.label })
    }

    @Test
    fun sameStoryCollapsesUnderTheFirstInSortOrder() {
        val articles = listOf(article(1, at(19, 8)), article(2, at(19, 9)), article(3, at(19, 10)))
        val groups = mapOf(1L to 1L, 3L to 1L)
        val newest = build(articles, groups = groups).single().items
        assertEquals(listOf(3L, 2L), newest.map { it.article.id })
        assertEquals(listOf(1L), newest[0].similar.map { it.id })
        val oldest = build(articles, groups = groups, order = SortOrder.Oldest).single().items
        assertEquals(1L, oldest[0].article.id)
        assertEquals(listOf(1L, 3L), oldest[0].members.map { it.id })
    }

    @Test
    fun hiddenSearchAndFeedFilterApplyBeforeGrouping() {
        val articles = listOf(article(1, at(19, 8), feed = 1, title = "Rust in the kernel"), article(2, at(19, 9), feed = 2, title = "Rust belt"), article(3, at(19, 10), feed = 1))
        assertEquals(listOf(3L, 1L), build(articles, hidden = setOf(2)).single().items.map { it.article.id })
        assertEquals(listOf(2L, 1L), build(articles, query = "rust").single().items.map { it.article.id })
        assertEquals(listOf(3L, 1L), build(articles, feed = 1).single().items.map { it.article.id })
    }

    @Test
    fun labelsAndAges() {
        assertEquals("Undated", dayLabel(null, today, zone, Locale.ENGLISH))
        assertEquals("2 October", dayLabel(at(2, 12), today, zone, Locale.ENGLISH))
        assertEquals("31 December 2025", dayLabel(LocalDate.of(2025, 12, 31).atStartOfDay(zone).toInstant(), today, zone, Locale.ENGLISH))
        val now = at(19, 12)
        assertEquals("now", relativeAge(now, now))
        assertEquals("45m", relativeAge(now.minusSeconds(45 * 60), now))
        assertEquals("3h", relativeAge(now.minusSeconds(3 * 3600), now))
        assertEquals("2d", relativeAge(now.minusSeconds(50 * 3600), now))
    }

    @Test
    fun summariesLoseTheirMarkup() {
        assertEquals("Fish & chips — \"tonight\"", plainText("<p>Fish &amp; chips &#8212; &quot;tonight&quot;</p>"))
    }
}
