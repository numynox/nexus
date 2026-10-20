package io.github.numynox.nexus.vibilia.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelLevelTest {
    private val car = Car(id = "c", name = "Golf", tankCapacity = 50.0)

    private fun refuel(id: Long, at: String, mileage: Int, liters: Double, level: Double, missed: Boolean = false) =
        RefuelEvent(id, "c", null, Instant.parse(at), mileage, liters, liters * 1.5, 1.5, level, true, missed)

    @Test
    fun `the calendar fallback matches the web's arithmetic`() {
        // 30 L used over 10 days → 3 L/day; 5 days later 15 L are gone.
        val level = FuelLevel.fromRefuels(
            car,
            listOf(
                refuel(1, "2026-01-01T10:00:00Z", 1000, 40.0, 50.0),
                refuel(2, "2026-01-11T10:00:00Z", 1500, 30.0, 50.0),
            ),
            now = Instant.parse("2026-01-16T10:00:00Z"),
        )!!
        assertEquals(FuelLevel.Method.Time, level.method)
        assertEquals(35.0, level.remainingLiters, 1e-9)
        assertEquals(0.7, level.fillRatio, 1e-9)
    }

    @Test
    fun `no estimate without an interval`() {
        assertNull(FuelLevel.fromRefuels(car, listOf(refuel(1, "2026-01-01T10:00:00Z", 1000, 40.0, 50.0))))
    }

    @Test
    fun `live driving lowers a drive estimate and leaves a calendar one alone`() {
        val drive = FuelLevel(
            method = FuelLevel.Method.Drive, lastRefuelAt = Instant.EPOCH, lastMileage = 10_600, tankCapacity = 50.0,
            startingLiters = 50.0, remainingLiters = 38.0, drivingHours = 1.5, estimatedKm = 150.0,
            kmPerHour = 100.0, litersPerHour = 8.0, litersPerDay = null,
        )
        val later = drive.plusLocalDriving(0.5)
        assertEquals(34.0, later.remainingLiters, 1e-9)
        assertEquals(10_800.0, later.estimatedMileage!!, 1e-9)
        assertEquals(425.0, later.rangeKm!!, 1e-9)

        val calendar = drive.copy(method = FuelLevel.Method.Time)
        assertEquals(calendar, calendar.plusLocalDriving(2.0))
    }

    @Test
    fun `a drive estimate without a tracked tank says its speed is assumed`() {
        val row = FuelLevelRow(
            method = "drive", lastRefuelAt = Instant.EPOCH, tankCapacity = 50.0, startingLiters = 50.0,
            remainingLiters = 35.0, fillRatio = 0.7, drivingHours = 2.0, kmPerHour = 50.0, litersPerHour = 3.0,
            calendarLiters = 9.0, modelSampleCount = 0,
        )
        val level = FuelLevel.from(row)
        assertTrue(level.speedAssumed)
        assertEquals(9.0, level.calendarLiters, 1e-9)
        assertFalse(FuelLevel.from(row.copy(modelSampleCount = 3)).speedAssumed)
        assertFalse("a database before the prior sends no count", FuelLevel.from(row.copy(modelSampleCount = null)).speedAssumed)
    }
}
