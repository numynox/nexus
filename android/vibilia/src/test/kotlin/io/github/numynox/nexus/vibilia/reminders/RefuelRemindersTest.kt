package io.github.numynox.nexus.vibilia.reminders

import io.github.numynox.nexus.vibilia.data.Car
import io.github.numynox.nexus.vibilia.data.FuelLevel
import io.github.numynox.nexus.vibilia.data.PriceBucket
import io.github.numynox.nexus.vibilia.data.PriceOutlook
import io.github.numynox.nexus.vibilia.data.StationPrice
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefuelRemindersTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 21)
    private val refuelled = today.minusDays(10).atTime(8, 0).toInstant(zone)
    private fun at(hour: Int): Instant = today.atTime(hour, 10).toInstant(zone)

    private fun level(remaining: Double) = FuelLevel(
        method = FuelLevel.Method.Drive, lastRefuelAt = refuelled, lastMileage = 10_000, tankCapacity = 50.0,
        startingLiters = 50.0, remainingLiters = remaining, drivingHours = 5.0, estimatedKm = 500.0,
        kmPerHour = 100.0, litersPerHour = 8.0, litersPerDay = null,
    )

    private val golf = Car(id = "g", name = "Golf", tankCapacity = 50.0)
    private val polo = Car(id = "p", name = "Polo", tankCapacity = 50.0)
    private fun jet(price: Double) = StationPrice(id = "s", brand = "JET", place = "TENNENLOHE", street = "", houseNumber = "", price = price, discount = 0.0)

    /**
     * Last week's shape, as the database showed it: the low from the evening
     * to late morning, 1.969; up 17 ct at noon, sliding back by 19:00.
     */
    private val outlook = PriceOutlook.from(
        (1L..5L).flatMap { back ->
            val day = today.minusDays(back)
            (0..23).map { hour ->
                val price = when (hour) {
                    in 12..13 -> 2.139
                    14 -> 2.089
                    15 -> 2.069
                    16 -> 2.039
                    17 -> 2.009
                    18 -> 1.989
                    else -> 1.969
                }
                PriceBucket(day, hour * 60, price)
            }
        } + PriceBucket(today, 0, 1.5), // today's half day is left out
        today,
    )!!

    @Test
    fun theOutlookLearnsTheDailyShape() {
        assertEquals(0.0, outlook.premiumAt(9)!!, 1e-9)
        assertEquals(0.17, outlook.premiumAt(12)!!, 1e-9)
        assertTrue(outlook.isLow(19))
        assertFalse(outlook.isLow(17))
        assertEquals(19, outlook.nextLow(16))
        assertNull(outlook.nextLow(12))
        assertEquals(1.919, outlook.expected(1.959, 17, 19)!!, 1e-9)
        assertNull(PriceOutlook.from(emptyList(), today))
    }

    @Test
    fun atTheLowItSaysRefuelNowWhereAndForHowMuch() {
        val (title, lines) = RefuelReminders.message(listOf(golf to level(8.0), polo to level(40.0)), 20, jet(1.939), outlook, at(9), zone)!!
        assertEquals("Refuel the Golf now", title)
        assertEquals("Fill up for 1.939 €/L at JET Tennenlohe — today's low", lines[0])
        assertEquals("8 L left · 100 km range", lines[1])
    }

    @Test
    fun aLowWithinThreeHoursIsWorthWaitingFor() {
        val (title, lines) = RefuelReminders.message(listOf(golf to level(8.0)), 20, jet(2.039), outlook, at(16), zone)!!
        assertEquals("Refuel the Golf from 19:00", title)
        assertEquals("Fill up for 1.969 €/L at JET Tennenlohe — expected, now 2.039 €/L", lines[0])
    }

    @Test
    fun atNoonWithTheLowFarOffItWaits() {
        assertNull(RefuelReminders.message(listOf(golf to level(8.0)), 20, jet(2.139), outlook, at(12), zone))
    }

    @Test
    fun anAlmostEmptyTankDoesNotWaitForAPrice() {
        // 49 L used in 10 days: the last litre lasts hours.
        val (title, lines) = RefuelReminders.message(listOf(golf to level(1.0)), 20, jet(2.139), outlook, at(12), zone)!!
        assertEquals("Refuel the Golf today", title)
        assertEquals("Fill up for 2.139 €/L at JET Tennenlohe — no lower price due soon", lines[0])
    }

    @Test
    fun withoutHistoryAnyHourWillDo() {
        assertEquals("Refuel the Golf now", RefuelReminders.message(listOf(golf to level(8.0)), 20, jet(2.0), null, at(12), zone)!!.first)
    }

    @Test
    fun severalCarsAreNamedLowestFirst() {
        val (title, lines) = RefuelReminders.message(listOf(golf to level(8.0), polo to level(4.0)), 20, jet(1.939), outlook, at(9), zone)!!
        assertEquals("Refuel 2 cars now", title)
        assertEquals("Polo: 4 L left · 50 km range", lines[1])
    }

    @Test
    fun nothingLowSaysNothing() {
        assertNull(RefuelReminders.message(listOf(golf to level(40.0)), 20, jet(1.939), outlook, at(9), zone))
    }

    @Test
    fun theCheckRunsJustAfterTheHoursPriceRefresh() {
        assertEquals(Duration.ofMinutes(5), RefuelReminders.delayUntilNextCheck(LocalDateTime.of(2026, 10, 21, 6, 5)))
        assertEquals(Duration.ofMinutes(50), RefuelReminders.delayUntilNextCheck(LocalDateTime.of(2026, 10, 21, 6, 20)))
    }
}
