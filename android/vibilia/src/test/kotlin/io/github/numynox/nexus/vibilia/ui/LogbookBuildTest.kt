package io.github.numynox.nexus.vibilia.ui

import io.github.numynox.nexus.vibilia.data.CarExpense
import io.github.numynox.nexus.vibilia.data.RefuelEvent
import io.github.numynox.nexus.vibilia.ui.logbook.LogEntry
import io.github.numynox.nexus.vibilia.ui.logbook.LogbookViewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The same fixture as supabase/tests/vehicle_statistics.test.sql, so both clients agree. */
class LogbookBuildTest {
    private fun refuel(id: Long, at: String, mileage: Int, liters: Double, missed: Boolean = false) =
        RefuelEvent(id, "c", null, Instant.parse(at), mileage, liters, liters * 1.5, 1.5, 50.0, true, missed)

    @Test
    fun `consumption follows the statistics rules`() {
        val logbook = LogbookViewModel.build(
            listOf(
                refuel(1, "2026-01-01T10:00:00Z", 10_000, 40.0),
                refuel(2, "2026-01-15T10:00:00Z", 10_500, 40.0),
                refuel(3, "2026-02-01T10:00:00Z", 11_000, 25.0, missed = true),
                refuel(4, "2026-02-15T10:00:00Z", 11_250, 25.0),
            ),
            emptyList(),
            zone = ZoneOffset.UTC,
        )
        val byId = logbook.months.flatMap { it.entries }.filterIsInstance<LogEntry.Refuel>().associateBy { it.event.id }
        assertNull("first refuel has no interval", byId.getValue(1).consumption)
        assertEquals(8.0, byId.getValue(2).consumption!!, 1e-9)
        assertNull("missed refuel has distance but no consumption", byId.getValue(3).consumption)
        assertEquals(500, byId.getValue(3).kmSincePrevious)
        assertEquals(10.0, byId.getValue(4).consumption!!, 1e-9)
        assertEquals(9.0, logbook.averageConsumption!!, 1e-9)
        assertEquals(listOf("2026-02", "2026-01"), logbook.months.map { it.month.toString() })
    }

    @Test
    fun `each month says how its spend compares with the month before`() {
        val logbook = LogbookViewModel.build(
            listOf(
                refuel(1, "2026-01-10T10:00:00Z", 10_000, 40.0), // 60 €
                refuel(2, "2026-02-10T10:00:00Z", 10_500, 32.0), // 48 €
                refuel(3, "2026-04-10T10:00:00Z", 11_000, 40.0), // 60 €, after a March without entries
            ),
            listOf(CarExpense(id = 1, carId = "c", expensedAt = Instant.parse("2026-02-20T10:00:00Z"), title = "Wash", amount = 20.0)),
            zone = ZoneOffset.UTC,
        )
        val byMonth = logbook.months.associateBy { it.month.toString() }
        assertNull("nothing before the first month to compare with", byMonth.getValue("2026-01").comparison)
        assertEquals("8 € more than January", byMonth.getValue("2026-02").comparison)
        assertEquals(48.0, byMonth.getValue("2026-02").fuel, 1e-9)
        assertEquals(20.0, byMonth.getValue("2026-02").other, 1e-9)
        assertEquals("60 € more than March", byMonth.getValue("2026-04").comparison)
    }

    @Test
    fun `the comparison reads naturally`() {
        val september = YearMonth.of(2026, 9)
        assertEquals("12 € less than September", LogbookViewModel.spendComparison(80.0, 92.0, september, locale = Locale.ENGLISH))
        assertEquals("Same as September", LogbookViewModel.spendComparison(80.2, 80.0, september, locale = Locale.ENGLISH))
        assertEquals("5 € more than September so far", LogbookViewModel.spendComparison(85.0, 80.0, september, running = true, locale = Locale.ENGLISH))
    }
}
