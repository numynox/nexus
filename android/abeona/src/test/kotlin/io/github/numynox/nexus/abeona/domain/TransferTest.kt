package io.github.numynox.nexus.abeona.domain

import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.StopTime
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared rules, case by case — the web's transfer.ts must give the same answers. */
class TransferTest {
    private val t0 = Instant.parse("2026-11-03T08:00:00Z")
    private fun at(minutes: Int) = t0.plusSeconds(minutes * 60L).toString()

    private fun stop(name: String, planned: Int, current: Int? = null, platform: String? = null, id: String? = name) =
        StopTime(id = id, name = name, planned = at(planned), current = current?.let(::at), plannedPlatform = platform)

    private fun ride(from: StopTime, to: StopTime, cancelled: Boolean = false) =
        Leg(mode = LegMode.Train, product = "RE", lineName = "RE 4", from = from, to = to, cancelled = cancelled)

    private fun walk(from: String, to: String, start: Int, end: Int) =
        Leg(mode = LegMode.Walk, from = stop(from, start), to = stop(to, end))

    @Test
    fun delayRoundsToWholeMinutes() {
        assertEquals(0, delayMinutes(t0, null))
        assertEquals(0, delayMinutes(null, t0))
        assertEquals(7, delayMinutes(t0, t0.plusSeconds(7 * 60)))
        assertEquals(1, delayMinutes(t0, t0.plusSeconds(30)))
        assertEquals(0, delayMinutes(t0, t0.plusSeconds(29)))
        assertEquals(-2, delayMinutes(t0, t0.minusSeconds(120)))
    }

    @Test
    fun platformsAreNormalised() {
        assertEquals("7", normalisePlatform(" Gleis 7 "))
        assertEquals("7A", normalisePlatform("platform 7a"))
        assertNull(normalisePlatform("  "))
        assertTrue(platformChanged("Gleis 7", "8"))
        assertFalse(platformChanged("Gleis 7", "7"))
        assertFalse(platformChanged("7", null))
    }

    @Test
    fun samePlatformNeedsTwoMinutes() {
        val legs = listOf(
            ride(stop("A", 0), stop("B", 60, platform = "Gleis 4")),
            ride(stop("B", 64, platform = "4"), stop("C", 120)),
        )
        val change = transfers(legs).single()
        assertEquals(4, change.availableMinutes)
        assertEquals(2, change.requiredMinutes)
        assertEquals(Confidence.TIGHT, change.confidence)
    }

    @Test
    fun otherPlatformNeedsFiveMinutes() {
        val legs = listOf(
            ride(stop("A", 0), stop("B", 60, platform = "4")),
            ride(stop("B", 70, platform = "9"), stop("C", 120)),
        )
        val change = transfers(legs).single()
        assertEquals(5, change.requiredMinutes)
        assertEquals(Confidence.SAFE, change.confidence)
    }

    @Test
    fun delayEatsTheMargin() {
        val legs = listOf(
            ride(stop("A", 0), stop("B", 60, current = 67, platform = "4")),
            ride(stop("B", 70, platform = "9"), stop("C", 120)),
        )
        val change = transfers(legs).single()
        assertEquals(3, change.availableMinutes)
        assertEquals(-2, change.marginMinutes)
        assertEquals(Confidence.AT_RISK, change.confidence)
    }

    @Test
    fun walkBetweenStationsCountsItsPlannedMinutes() {
        val legs = listOf(
            ride(stop("A", 0), stop("B", 60)),
            walk("B", "B Bus", 61, 69),
            ride(stop("B Bus", 75), stop("C", 120)),
        )
        val change = transfers(legs).single()
        assertEquals(0, change.arriving)
        assertEquals(2, change.departing)
        assertEquals(8, change.walkMinutes)
        assertEquals(11, change.requiredMinutes)
        assertEquals(15, change.availableMinutes)
        assertEquals(Confidence.SAFE, change.confidence)
    }

    @Test
    fun differentStopWithoutWalkNeedsThreeMinutes() {
        val legs = listOf(ride(stop("A", 0), stop("B", 60)), ride(stop("B2", 64), stop("C", 120)))
        val change = transfers(legs).single()
        assertEquals(3, change.requiredMinutes)
        assertEquals(Confidence.TIGHT, change.confidence)
    }

    @Test
    fun cancelledLegPutsTheChangeAtRisk() {
        val legs = listOf(ride(stop("A", 0), stop("B", 60)), ride(stop("B", 90), stop("C", 120), cancelled = true))
        assertEquals(Confidence.AT_RISK, transfers(legs).single().confidence)
    }

    @Test
    fun bindingLiftsAtTwentyMinutesForBoundLongDistanceFares() {
        assertTrue(trainBindingLifted(FareType.Saver, true, 20))
        assertFalse(trainBindingLifted(FareType.Saver, true, 19))
        assertFalse(trainBindingLifted(FareType.Saver, false, 45))
        assertFalse(trainBindingLifted(FareType.Regional, true, 45))
        assertFalse(trainBindingLifted(FareType.Deutschlandticket, true, 45))
        assertFalse(trainBindingLifted(FareType.BahnCard100, true, 45))
        assertTrue(trainBindingLifted(FareType.Flex, true, 30))
    }

    @Test
    fun finalDelayIsTheLastRidesArrival() {
        val legs = listOf(
            ride(stop("A", 0, current = 5), stop("B", 60, current = 62)),
            ride(stop("B", 70), stop("C", 120, current = 143)),
            walk("C", "Home", 121, 130),
        )
        assertEquals(23, finalArrivalDelay(legs))
        assertEquals(0, finalArrivalDelay(emptyList()))
    }
}
