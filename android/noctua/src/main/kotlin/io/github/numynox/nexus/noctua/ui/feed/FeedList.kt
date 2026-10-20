package io.github.numynox.nexus.noctua.ui.feed

import io.github.numynox.nexus.noctua.data.Article
import io.github.numynox.nexus.noctua.data.SortOrder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One row of the feed: a story, and the same story from other feeds behind it. */
data class FeedItem(val article: Article, val similar: List<Article>) {
    /** Every article of the cluster — seen and read act on the whole of it. */
    val members: List<Article> get() = listOf(article) + similar
}

data class DayGroup(val label: String, val items: List<FeedItem>)

/**
 * The feed as the list draws it — the web's `groupedArticles`. Filters by the
 * seen snapshot taken at load (so articles seen now stay put until the next
 * refresh) and the search, sorts, collapses same-story clusters under the
 * first of them in sort order, and groups by day.
 */
fun buildFeed(
    articles: List<Article>,
    hiddenAtLoad: Set<Long>,
    query: String,
    feedId: Long?,
    sortOrder: SortOrder,
    groups: Map<Long, Long>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): List<DayGroup> {
    val needle = query.trim().lowercase()
    val visible = articles.asSequence()
        .filter { it.id !in hiddenAtLoad }
        .filter { feedId == null || it.feedId == feedId }
        .filter {
            needle.isEmpty() || it.title.lowercase().contains(needle) ||
                it.summary?.lowercase()?.contains(needle) == true || it.feedName.lowercase().contains(needle)
        }
        .sortedWith(
            if (sortOrder == SortOrder.Oldest) compareBy<Article> { it.time ?: Instant.EPOCH }.thenBy { it.id }
            else compareByDescending<Article> { it.time ?: Instant.EPOCH }.thenByDescending { it.id },
        )
        .toList()

    val leaders = LinkedHashMap<Long, Pair<Article, MutableList<Article>>>()
    visible.forEach { article ->
        val key = groups[article.id] ?: article.id
        leaders.getOrPut(key) { article to mutableListOf() }.let { (leader, similar) -> if (leader !== article) similar += article }
    }

    val result = mutableListOf<DayGroup>()
    leaders.values.forEach { (leader, similar) ->
        val label = dayLabel(leader.time, today, zone, locale)
        val item = FeedItem(leader, similar)
        if (result.lastOrNull()?.label == label) {
            result[result.lastIndex] = result.last().copy(items = result.last().items + item)
        } else {
            result += DayGroup(label, listOf(item))
        }
    }
    return result
}

/** "Today" / "Yesterday" / a weekday / a date — whatever orients fastest. */
fun dayLabel(time: Instant?, today: LocalDate, zone: ZoneId, locale: Locale): String {
    if (time == null) return "Undated"
    val date = time.atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0 -> "Today"
        days == 1L -> "Yesterday"
        days < 6 -> date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        date.year == today.year -> DateTimeFormatter.ofPattern("d MMMM", locale).format(date)
        else -> DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(date)
    }
}

/** "now", "12m", "3h", "2d" — the age of an article at a glance. */
fun relativeAge(time: Instant?, now: Instant = Instant.now()): String {
    if (time == null) return ""
    val minutes = ChronoUnit.MINUTES.between(time, now)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${(minutes + 30) / 60}h"
        else -> "${(minutes + 12 * 60) / (60 * 24)}d"
    }
}

/**
 * Articles shown and not seen yet — the web's `displayedUnreadAndUnseenCount`.
 * Every member of a cluster counts, as on the web, though only its leader is
 * drawn: a cluster is seen as a whole, so the count drops by all of them at once.
 */
fun unseenCount(days: List<DayGroup>, seen: Set<Long>): Int =
    days.sumOf { day -> day.items.sumOf { item -> item.members.count { it.id !in seen } } }

/**
 * How far through the feed the reader is, 0–1 — the web's progress bar
 * (ArticleFeedView.svelte): of the [baseline] unseen when the list loaded,
 * the share seen since. A list that loaded with nothing unseen is complete.
 */
fun seenProgress(baseline: Int, unseen: Int): Float =
    if (baseline == 0) {
        if (unseen == 0) 1f else 0f
    } else {
        ((baseline - unseen).toFloat() / baseline).coerceIn(0f, 1f)
    }
