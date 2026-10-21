package io.github.numynox.nexus.noctua.ui

import io.github.numynox.nexus.noctua.data.FeedStatisticsRow
import io.github.numynox.nexus.noctua.data.ReadingStatisticsRow
import io.github.numynox.nexus.noctua.ui.statistics.ReadingStats
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The counting is the database's (supabase/tests/noctua_reading_statistics.test.sql); this pins the shaping. */
class ReadingStatsTest {
    // A Wednesday, so "this week" began on Monday the 19th.
    private val today = LocalDate.of(2026, 10, 21)

    private fun row(
        readDays: List<LocalDate> = emptyList(),
        weeks: Int = 2,
        readHours: List<List<Int>> = emptyList(),
        feeds: List<FeedStatisticsRow> = emptyList(),
    ) = ReadingStatisticsRow(
        weeks = weeks,
        today = today.toString(),
        windowStart = today.minusDays(2).minusWeeks((weeks - 1).toLong()).toString(),
        windowDays = (weeks - 1) * 7 + 3,
        totalRead = 1_797,
        totalArticles = 14_235,
        thisWeekRead = 24,
        thisWeekPublished = 471,
        windowRead = 40,
        windowPublished = 1_000,
        readDays = readDays.map { it.toString() },
        feeds = feeds,
        readHours = readHours,
    )

    @Test
    fun streaksCountCalendarDays() {
        val days = listOf(today.minusDays(1), today.minusDays(2), today.minusDays(3), today.minusDays(10), today.minusDays(11))
        val stats = ReadingStats.from(row(days))
        // Nothing read yet today: the streak still stands from yesterday.
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
        assertEquals(0, ReadingStats.from(row(listOf(today.minusDays(2)))).currentStreak)
        // Only days in the window are active ones.
        assertEquals(3, stats.activeDays)
        assertEquals(10, stats.windowDays)
    }

    @Test
    fun totalsComeStraightFromTheDatabase() {
        val stats = ReadingStats.from(row())
        // More than one PostgREST page: these used to stop at 1,000.
        assertEquals(1_797, stats.totalRead)
        assertEquals(20.0, stats.averageReadPerWeek, 1e-9)
        assertEquals(500.0, stats.averagePublishedPerWeek, 1e-9)
        assertEquals(471, stats.thisWeekPublished)
    }

    @Test
    fun hoursMakeTheHeatmapWeekdaysAndBusiestHour() {
        val stats = ReadingStats.from(row(readHours = listOf(listOf(2, 8, 6), listOf(0, 8, 2), listOf(4, 21, 5))))
        assertEquals(6, stats.readHeatmap[2][8])
        assertEquals(8, stats.busiestHour?.hour)
        assertEquals(8, stats.busiestHour?.count)
        assertEquals(3.0, stats.weekdayAverages[2], 1e-9)
        assertEquals(2.5, stats.weekdayAverages[4], 1e-9)
    }

    @Test
    fun feedShareComparesTheSameArticles() {
        val stats = ReadingStats.from(row(feeds = listOf(FeedStatisticsRow("Heise", read = 30, readPublished = 20, published = 40), FeedStatisticsRow(null, 3, 0, 0))))
        assertEquals(0.5, stats.feeds[0].share, 1e-9)
        assertEquals("Unknown feed", stats.feeds[1].feed)
        assertEquals("Heise", stats.mostReadFeed?.feed)
    }

    @Test
    fun nothingReadHasNoBusiestHour() {
        assertNull(ReadingStats.from(row()).busiestHour)
    }
}
