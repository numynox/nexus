package io.github.numynox.nexus.noctua.ui.statistics

import io.github.numynox.nexus.noctua.data.ReadingStatisticsRow
import java.time.LocalDate

data class FeedReads(
    val feed: String,
    val read: Int,
    val published: Int,
    /** Reads of articles that were also published in the window. */
    val readPublished: Int = read,
) {
    /** Share of the feed's articles in the window that were read, 0–1: the same articles on both sides. */
    val share: Double get() = if (published > 0) readPublished.toDouble() / published else 0.0
}

data class BusiestHour(val hour: Int, val count: Int)

/**
 * The web statistics dashboard's numbers (StatisticsDashboard.svelte), from
 * the same counts: `noctua_reading_statistics` does the counting, both clients
 * only shape the result. Weeks start on Monday, the window is the configured
 * number of weeks or fewer if reading started more recently, days are calendar
 * days in the backend's reporting time zone.
 */
data class ReadingStats(
    val weeks: Int,
    val totalRead: Int,
    val totalArticles: Long,
    val thisWeekRead: Int,
    val thisWeekPublished: Int,
    val averageReadPerWeek: Double,
    val averagePublishedPerWeek: Double,
    val currentStreak: Int,
    val longestStreak: Int,
    val activeDays: Int,
    /** Days of the window so far, today included — not the rest of this week. */
    val windowDays: Int,
    val busiestHour: BusiestHour?,
    val feeds: List<FeedReads>,
    /** Reads per weekday, Monday first, averaged over the window's weeks. */
    val weekdayAverages: List<Double>,
    /** [weekday][hour] → reads in the window, Monday first. */
    val readHeatmap: List<List<Int>>,
    /** [weekday][hour] → articles published in the window, Monday first. */
    val publishedHeatmap: List<List<Int>>,
) {
    val mostReadFeed: FeedReads? get() = feeds.firstOrNull()

    companion object {
        fun from(row: ReadingStatisticsRow): ReadingStats {
            val weeks = row.weeks.coerceAtLeast(1)
            val today = LocalDate.parse(row.today)
            val windowStart = LocalDate.parse(row.windowStart)
            val readDays = row.readDays.mapNotNullTo(HashSet()) { runCatching { LocalDate.parse(it) }.getOrNull() }

            var current = 0
            var cursor = today
            // The streak survives the morning before the day's first article.
            if (cursor !in readDays) cursor = cursor.minusDays(1)
            while (cursor in readDays) {
                current++
                cursor = cursor.minusDays(1)
            }
            var longest = 0
            var run = 0
            var previous: LocalDate? = null
            readDays.sorted().forEach { day ->
                run = if (previous?.plusDays(1) == day) run + 1 else 1
                longest = maxOf(longest, run)
                previous = day
            }

            val readHeatmap = heatmap(row.readHours)
            val hours = IntArray(24) { hour -> readHeatmap.sumOf { it[hour] } }
            val busiest = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }?.let { BusiestHour(it, hours[it]) }

            return ReadingStats(
                weeks = weeks,
                totalRead = row.totalRead,
                totalArticles = row.totalArticles,
                thisWeekRead = row.thisWeekRead,
                thisWeekPublished = row.thisWeekPublished,
                averageReadPerWeek = row.windowRead.toDouble() / weeks,
                averagePublishedPerWeek = row.windowPublished.toDouble() / weeks,
                currentStreak = current,
                longestStreak = longest,
                activeDays = readDays.count { !it.isBefore(windowStart) },
                windowDays = row.windowDays,
                busiestHour = busiest,
                feeds = row.feeds.map { FeedReads(it.feed ?: "Unknown feed", it.read, it.published, it.readPublished) },
                weekdayAverages = readHeatmap.map { day -> day.sum().toDouble() / weeks },
                readHeatmap = readHeatmap,
                publishedHeatmap = heatmap(row.publishedHours),
            )
        }

        /** Seven rows of 24 hours from the non-empty [weekday, hour, count] cells. */
        private fun heatmap(cells: List<List<Int>>): List<List<Int>> {
            val grid = List(7) { IntArray(24) }
            cells.forEach { cell ->
                val (day, hour, count) = cell.takeIf { it.size == 3 } ?: return@forEach
                if (day in 0..6 && hour in 0..23) grid[day][hour] = count
            }
            return grid.map { it.toList() }
        }
    }
}
