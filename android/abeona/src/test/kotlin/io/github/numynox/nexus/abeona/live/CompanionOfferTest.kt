package io.github.numynox.nexus.abeona.live

import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Trip
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** When the trip screen offers "Start live companion", says it starts by itself, or shows nothing. */
class CompanionOfferTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun trip(departs: String, arrives: String) = Trip(
        id = "t", householdId = "h", originName = "Hamburg Hbf", destinationName = "Hamburg-Altona",
        plannedDeparture = at(departs).toString(), plannedArrival = at(arrives).toString(),
        legs = listOf(
            Leg(lineName = "ICE 600", from = StopTime("a", "Hamburg Hbf", planned = at(departs).toString()), to = StopTime("b", "Hamburg-Altona", planned = at(arrives).toString())),
        ),
    )

    private val morning = trip("2026-11-08T10:24", "2026-11-08T10:40")
    private val night = trip("2026-11-09T02:10", "2026-11-09T04:00")

    private fun offer(trip: Trip, now: String, auto: Boolean = false, running: Boolean = false, stopped: Boolean = false) =
        companionOffer(trip, at(now), auto, 15, running, stopped, zone)

    @Test
    fun startingByItselfReplacesTheButton() {
        assertEquals(CompanionOffer.Automatic(at("2026-11-08T09:54")), offer(morning, "2026-11-01T12:00", auto = true))
    }

    @Test
    fun runningOffersStop() {
        assertEquals(CompanionOffer.Running, offer(morning, "2026-11-08T10:00", auto = true, running = true))
    }

    @Test
    fun byHandOnlyOnTheTravelDay() {
        assertEquals(CompanionOffer.Hidden, offer(morning, "2026-11-07T20:00"))
        assertEquals(CompanionOffer.Manual, offer(morning, "2026-11-08T06:00"))
    }

    @Test
    fun aDepartureBeforeThreeCanBeFollowedFromTheEveningBefore() {
        assertEquals(CompanionOffer.Manual, offer(night, "2026-11-08T22:00"))
        assertEquals(CompanionOffer.Hidden, offer(night, "2026-11-07T22:00"))
    }

    @Test
    fun stoppedByHandItCanBeStartedAgain() {
        assertEquals(CompanionOffer.Manual, offer(morning, "2026-11-08T10:10", auto = true, stopped = true))
    }

    @Test
    fun nothingOnceTheTripIsOver() {
        assertEquals(CompanionOffer.Hidden, offer(morning, "2026-11-08T11:30", auto = true))
    }
}
