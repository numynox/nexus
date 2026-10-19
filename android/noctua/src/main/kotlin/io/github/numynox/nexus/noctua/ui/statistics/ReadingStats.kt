package io.github.numynox.nexus.noctua.ui.statistics

import io.github.numynox.nexus.noctua.data.PublishedArticle
import io.github.numynox.nexus.noctua.data.ReadRecord
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class FeedReads(val feed: String, val read: Int, val published: Int) {
    /** Share of the feed's articles in the window that were read, 0–1. */
    val share: Double get() = if (published > 0) read.toDouble() / published else 0.0
}

data class BusiestHour(val hour: Int, val count: Int)

/**
 * The web statistics dashboard's numbers (StatisticsDashboard.svelte), worked
 * out the same way: weeks start on Monday, the window is the configured number
 * of weeks or fewer if reading started more recently, days are local
 * calendar days.
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
        fun startOfWeek(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /** How many weeks the window spans, given the oldest read. */
        fun windowWeeks(oldestRead: Instant?, configuredWeeks: Int, today: LocalDate, zone: ZoneId): Int {
            if (oldestRead == null) return configuredWeeks.coerceAtLeast(1)
            val weeks = ChronoUnit.WEEKS.between(startOfWeek(oldestRead.atZone(zone).toLocalDate()), startOfWeek(today)).toInt() + 1
            return weeks.coerceAtMost(configuredWeeks).coerceAtLeast(1)
        }

        fun windowStart(weeks: Int, today: LocalDate): LocalDate = startOfWeek(today).minusWeeks((weeks - 1).toLong())

        fun compute(
            reads: List<ReadRecord>,
            published: List<PublishedArticle>,
            totalArticles: Long,
            weeks: Int,
            today: LocalDate,
            zone: ZoneId,
        ): ReadingStats {
            val windowStart = windowStart(weeks, today)
            val weekStart = startOfWeek(today)
            fun Instant.date() = atZone(zone).toLocalDate()
            fun Instant.hour() = atZone(zone).hour
            fun Instant.weekday() = date().dayOfWeek.value - 1

            val inWindow = reads.filter { !it.readAt.date().isBefore(windowStart) }
            val publishedInWindow = published.filter { !it.publishedAt.date().isBefore(windowStart) }
            val readDays = reads.mapTo(HashSet()) { it.readAt.date() }

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

            val hours = IntArray(24).also { counts -> inWindow.forEach { counts[it.readAt.hour()]++ } }
            val busiest = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }?.let { BusiestHour(it, hours[it]) }

            val publishedByFeed = publishedInWindow.groupingBy { it.feedName }.eachCount()
            val feeds = inWindow.groupingBy { it.feedName }.eachCount()
                .map { (feed, count) -> FeedReads(feed, count, publishedByFeed[feed] ?: 0) }
                .sortedWith(compareByDescending<FeedReads> { it.read }.thenBy { it.feed })

            fun heatmap(times: List<Instant>) = List(7) { day -> List(24) { hour -> times.count { it.weekday() == day && it.hour() == hour } } }
            val weekdays = IntArray(7).also { counts -> inWindow.forEach { counts[it.readAt.weekday()]++ } }

            return ReadingStats(
                weeks = weeks,
                totalRead = reads.size,
                totalArticles = totalArticles,
                thisWeekRead = reads.count { !it.readAt.date().isBefore(weekStart) },
                thisWeekPublished = publishedInWindow.count { !it.publishedAt.date().isBefore(weekStart) },
                averageReadPerWeek = inWindow.size.toDouble() / weeks,
                averagePublishedPerWeek = publishedInWindow.size.toDouble() / weeks,
                currentStreak = current,
                longestStreak = longest,
                activeDays = inWindow.mapTo(HashSet()) { it.readAt.date() }.size,
                windowDays = weeks * 7,
                busiestHour = busiest,
                feeds = feeds,
                weekdayAverages = weekdays.map { it.toDouble() / weeks },
                readHeatmap = heatmap(inWindow.map { it.readAt }),
                publishedHeatmap = heatmap(publishedInWindow.map { it.publishedAt }),
            )
        }
    }
}
