package io.github.numynox.nexus.noctua.ui

import io.github.numynox.nexus.noctua.data.PublishedArticle
import io.github.numynox.nexus.noctua.data.ReadRecord
import io.github.numynox.nexus.noctua.ui.statistics.ReadingStats
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingStatsTest {
    private val zone = ZoneOffset.UTC
    // A Wednesday, so "this week" began on Monday the 19th.
    private val today = LocalDate.of(2026, 10, 21)
    private fun read(day: LocalDate, hour: Int, feed: String = "Heise") = ReadRecord(0, day.atTime(hour, 30).toInstant(zone), feed)

    @Test
    fun windowIsShorterWhileReadingIsNew() {
        assertEquals(8, ReadingStats.windowWeeks(null, 8, today, zone))
        assertEquals(1, ReadingStats.windowWeeks(today.atStartOfDay(zone).toInstant(), 8, today, zone))
        // Read first on the Sunday before: that week and this one.
        assertEquals(2, ReadingStats.windowWeeks(LocalDate.of(2026, 10, 18).atStartOfDay(zone).toInstant(), 8, today, zone))
        assertEquals(8, ReadingStats.windowWeeks(LocalDate.of(2025, 1, 1).atStartOfDay(zone).toInstant(), 8, today, zone))
    }

    @Test
    fun streaksCountCalendarDays() {
        val reads = listOf(
            read(today.minusDays(1), 8), read(today.minusDays(2), 22), read(today.minusDays(3), 7),
            read(today.minusDays(10), 9), read(today.minusDays(11), 9),
        )
        val stats = ReadingStats.compute(reads, emptyList(), 10, 2, today, zone)
        // Nothing read yet today: the streak still stands from yesterday.
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
        assertEquals(0, ReadingStats.compute(listOf(read(today.minusDays(2), 8)), emptyList(), 1, 1, today, zone).currentStreak)
    }

    @Test
    fun weeklyFiguresAndFeeds() {
        val reads = listOf(
            read(today, 8, "Heise"), read(today, 8, "Heise"), read(today.minusDays(1), 20, "Ars"),
            read(today.minusDays(8), 20, "Heise"),
            // Before the two-week window: counts in total only.
            read(today.minusDays(30), 20, "Ars"),
        )
        val published = listOf(
            PublishedArticle(today.atTime(6, 0).toInstant(zone), "Heise"),
            PublishedArticle(today.atTime(7, 0).toInstant(zone), "Heise"),
            PublishedArticle(today.minusDays(9).atTime(7, 0).toInstant(zone), "Heise"),
            PublishedArticle(today.minusDays(9).atTime(7, 0).toInstant(zone), "Ars"),
        )
        val stats = ReadingStats.compute(reads, published, 99, 2, today, zone)
        assertEquals(5, stats.totalRead)
        assertEquals(3, stats.thisWeekRead)
        assertEquals(2, stats.thisWeekPublished)
        assertEquals(2.0, stats.averageReadPerWeek, 1e-9)
        assertEquals(2.0, stats.averagePublishedPerWeek, 1e-9)
        assertEquals(listOf("Heise", "Ars"), stats.feeds.map { it.feed })
        assertEquals(1.0, stats.feeds[0].share, 1e-9)
        assertEquals(8, stats.busiestHour?.hour)
        assertEquals(2, stats.readHeatmap[2][8])
        assertEquals(1.0, stats.weekdayAverages[2], 1e-9)
        assertEquals(3, stats.activeDays)
        assertEquals(14, stats.windowDays)
    }

    @Test
    fun nothingReadHasNoBusiestHour() {
        assertNull(ReadingStats.compute(emptyList(), emptyList(), 0, 8, today, zone).busiestHour)
    }
}
