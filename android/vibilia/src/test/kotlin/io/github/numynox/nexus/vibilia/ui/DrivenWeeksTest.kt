package io.github.numynox.nexus.vibilia.ui

import io.github.numynox.nexus.vibilia.data.DrivenDay
import io.github.numynox.nexus.vibilia.ui.logbook.DrivenWeeks
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivenWeeksTest {
    // A Wednesday.
    private val today = LocalDate.parse("2026-11-11")

    @Test
    fun `this week comes first, Monday on top, nothing after today`() {
        val weeks = DrivenWeeks.build(
            listOf(DrivenDay(LocalDate.parse("2026-11-09"), 35.0), DrivenDay(LocalDate.parse("2026-11-11"), 0.0)),
            today,
        )
        assertEquals(1, weeks.size)
        assertEquals(listOf(35.0, 0.0, 0.0, null, null, null, null), weeks[0])
    }

    @Test
    fun `weeks start at the week of the first day, before it no squares`() {
        // Thursday three weeks back.
        val weeks = DrivenWeeks.build(listOf(DrivenDay(LocalDate.parse("2026-10-22"), 60.0)), today)
        assertEquals(4, weeks.size)
        assertEquals(listOf(null, null, null, 60.0, 0.0, 0.0, 0.0), weeks[3])
    }

    @Test
    fun `at most fifty weeks`() {
        val weeks = DrivenWeeks.build(listOf(DrivenDay(LocalDate.parse("2024-01-01"), 10.0)), today)
        assertEquals(50, weeks.size)
        assertTrue(weeks.last().none { it == null })
    }

    @Test
    fun `no days, no graph`() {
        assertTrue(DrivenWeeks.build(emptyList(), today).isEmpty())
    }

    @Test
    fun `the request starts on the Monday of the oldest week`() {
        assertEquals(
            LocalDate.parse("2025-12-01").atStartOfDay(ZoneOffset.UTC).toInstant(),
            DrivenWeeks.since(today, ZoneOffset.UTC),
        )
        assertNull(DrivenWeeks.build(listOf(DrivenDay(today, 1.0)), today)[0][6])
    }

    @Test
    fun `shades follow the quartiles of the days driven, not the longest trip`() {
        val weeks = listOf(listOf(0.0, 10.0, 20.0, 30.0, 40.0, 500.0, null))
        assertEquals(listOf(20.0, 30.0, 40.0), DrivenWeeks.quartiles(weeks))
        assertTrue(DrivenWeeks.quartiles(listOf(listOf(0.0, null))).isEmpty())
    }
}
