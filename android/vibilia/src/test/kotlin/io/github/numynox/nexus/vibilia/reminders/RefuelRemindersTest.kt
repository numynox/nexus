package io.github.numynox.nexus.vibilia.reminders

import io.github.numynox.nexus.vibilia.data.Car
import io.github.numynox.nexus.vibilia.data.FuelLevel
import io.github.numynox.nexus.vibilia.data.StationPrice
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefuelRemindersTest {
    private val refuelled = Instant.parse("2026-10-01T08:00:00Z")
    private val now = refuelled.plus(Duration.ofDays(10))

    private fun level(remaining: Double) = FuelLevel(
        method = FuelLevel.Method.Drive, lastRefuelAt = refuelled, lastMileage = 10_000, tankCapacity = 50.0,
        startingLiters = 50.0, remainingLiters = remaining, drivingHours = 5.0, estimatedKm = 500.0,
        kmPerHour = 100.0, litersPerHour = 8.0, litersPerDay = null,
    )

    private val golf = Car(id = "g", name = "Golf", tankCapacity = 50.0)
    private val polo = Car(id = "p", name = "Polo", tankCapacity = 50.0)
    private val jet = StationPrice(id = "s", brand = "JET", place = "", street = "", houseNumber = "", price = 1.80, discount = 0.05)

    @Test
    fun lowIsUnderTheThresholdOrDryWithinADay() {
        assertTrue(RefuelReminders.isLow(level(8.0), 20, now))
        assertFalse(RefuelReminders.isLow(level(30.0), 20, now))
        // 14 L used in 10 days → 1.4 L a day: 1 L left lasts less than a day, whatever the threshold.
        val tank = level(36.0).copy(remainingLiters = 1.0, startingLiters = 15.0, tankCapacity = 5.0)
        assertTrue(RefuelReminders.isLow(tank, 10, now))
    }

    @Test
    fun oneCarSaysWhatIsLeftHowFarAndWhatFillingUpCosts() {
        val (title, lines) = RefuelReminders.message(listOf(golf to level(8.0), polo to level(40.0)), 20, jet, now)!!
        assertEquals("Time to refuel the Golf", title)
        // 42 L at 1.75 €.
        assertEquals(listOf("8 L left · 100 km range · fill up 73.50 € at JET"), lines)
    }

    @Test
    fun severalCarsAreNamedLowestFirst() {
        val (title, lines) = RefuelReminders.message(listOf(golf to level(8.0), polo to level(4.0)), 20, null, now)!!
        assertEquals("2 cars need fuel", title)
        assertEquals(listOf("Polo: 4 L left · 50 km range", "Golf: 8 L left · 100 km range"), lines)
    }

    @Test
    fun nothingLowSaysNothing() {
        assertNull(RefuelReminders.message(listOf(golf to level(40.0)), 20, jet, now))
    }

    @Test
    fun theCheckRunsAtTheNextChosenHour() {
        val morning = LocalDateTime.of(2026, 10, 21, 6, 30)
        assertEquals(Duration.ofMinutes(30), RefuelReminders.delayUntil(7, morning))
        assertEquals(Duration.ofHours(23).plusMinutes(30), RefuelReminders.delayUntil(6, morning))
    }
}
