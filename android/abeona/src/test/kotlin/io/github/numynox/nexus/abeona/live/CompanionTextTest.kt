package io.github.numynox.nexus.abeona.live

import io.github.numynox.nexus.abeona.data.AlertSettings
import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.Position
import io.github.numynox.nexus.abeona.domain.TravelSignals
import io.github.numynox.nexus.abeona.domain.applyRefresh
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CompanionTextTest {
    private val t0 = Instant.parse("2026-11-03T08:00:00Z")
    private fun at(m: Int) = t0.plusSeconds(m * 60L).toString()

    @Before
    fun utc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    private val trip = Trip(
        id = "t", householdId = "h", originName = "Mainz Hbf", destinationName = "Würzburg Hbf",
        plannedDeparture = at(0), plannedArrival = at(120), fareType = FareType.Saver, trainBinding = true,
        legs = listOf(
            Leg(lineName = "RE 4", product = "RE", from = StopTime("m", "Mainz Hbf", planned = at(0), plannedPlatform = "3"), to = StopTime("f", "Frankfurt Hbf", planned = at(40), plannedPlatform = "11")),
            Leg(lineName = "ICE 25", product = "ICE", from = StopTime("f", "Frankfurt Hbf", planned = at(48), plannedPlatform = "7"), to = StopTime("w", "Würzburg Hbf", planned = at(120), plannedPlatform = "5")),
        ),
    )

    @Test
    fun beforeLeavingSaysWhenToLeave() {
        assertEquals("Leave in 10 min · RE 4 → Frankfurt Hbf · Pl. 3 · departs in 25 min", companionStatus(trip, t0.minusSeconds(25 * 60)).text)
    }

    @Test
    fun atTheStationShowsPlatformAndCountdown() {
        assertEquals("RE 4 → Frankfurt Hbf · Pl. 3 · departs in 10 min", companionStatus(trip, t0.minusSeconds(10 * 60)).text)
    }

    @Test
    fun changingShowsTheNextTrain() {
        assertEquals("ICE 25 → Würzburg Hbf · Pl. 7 · departs in 4 min", companionStatus(trip, t0.plusSeconds(44 * 60)).text)
    }

    @Test
    fun theTrainsWifiAddsTheSpeed() {
        val onboard = OnboardStatus("ICE Portal", "ICE", "25", 247.4, null, null, "Würzburg Hbf", null, "5", t0)
        val signals = TravelSignals(onboardLegIndex = 1)
        assertEquals("ICE 25 → Würzburg Hbf · Pl. 5 · arrives 10:00 · 247 km/h", companionStatus(trip, t0.plusSeconds(60 * 60), signals = signals, onboard = onboard).text)
    }

    @Test
    fun standingAtTheChangeAfterTheTrainLeftLooksMissed() {
        val signals = TravelSignals(position = Position(50.1071, 8.6636, 0.0))
        val placed = trip.copy(legs = trip.legs.map { it.copy(from = it.from.copy(lat = 50.1071, lon = 8.6636)) })
        assertEquals(
            "Looks like you missed the ICE 25 · see alternatives in Abeona",
            companionStatus(placed, t0.plusSeconds(52 * 60), signals = signals).text,
        )
        assertEquals("missed-1", missedAlert(placed, t0.plusSeconds(52 * 60), 15, signals, AlertSettings())?.key)
        assertEquals(null, missedAlert(placed, t0.plusSeconds(52 * 60), 15, signals, AlertSettings(connectionAtRisk = false)))
    }

    @Test
    fun onBoardShowsTheChange() {
        assertEquals("Safe · change in 12 min · RE 4 → Frankfurt Hbf · Pl. 7", companionStatus(trip, t0.plusSeconds(28 * 60)).text)
    }

    @Test
    fun aTightChangeLeadsTheLine() {
        val tight = trip.copy(legs = listOf(trip.legs[0], trip.legs[1].copy(from = trip.legs[1].from.copy(planned = at(46)))))
        assertEquals("Tight · change in 12 min · RE 4 → Frankfurt Hbf · Pl. 7", companionStatus(tight, t0.plusSeconds(28 * 60)).text)
    }

    @Test
    fun lastRideShowsArrival() {
        assertEquals("ICE 25 → Würzburg Hbf · Pl. 5 · arrives 10:00", companionStatus(trip, t0.plusSeconds(60 * 60)).text)
        assertTrue(companionStatus(trip, t0.plusSeconds(121 * 60)).finished)
    }

    @Test
    fun alertsOnlyForNews() {
        val late = applyRefresh(
            trip,
            listOf(
                trip.legs[0].copy(to = trip.legs[0].to.copy(current = at(47))),
                trip.legs[1].copy(from = trip.legs[1].from.copy(current = at(48), platform = "9"), to = trip.legs[1].to.copy(current = at(145))),
            ),
            at(1),
        )
        val keys = companionAlerts(trip, late, t0).map { it.key }
        assertEquals(listOf("platform-1-9", "risk-1", "binding", "delay-0-1"), keys)
        assertEquals(emptyList<String>(), companionAlerts(late, late, t0).map { it.key })
    }

    @Test
    fun alertsFollowTheSettings() {
        val late = applyRefresh(
            trip,
            listOf(
                trip.legs[0].copy(to = trip.legs[0].to.copy(current = at(47))),
                trip.legs[1].copy(from = trip.legs[1].from.copy(current = at(48), platform = "9"), cancelled = true),
            ),
            at(1),
        )
        val quiet = AlertSettings(delayMinutes = 0, connectionAtRisk = false, platformChange = false, cancellation = false)
        // The binding is DB's rule, not an alert to switch off.
        assertEquals(emptyList<String>(), companionAlerts(trip, late, t0, quiet).map { it.key })
        assertEquals(listOf("cancel-1", "platform-1-9", "risk-1"), companionAlerts(trip, late, t0, AlertSettings(delayMinutes = 10)).map { it.key })
    }

    @Test
    fun aDelayAlertsAtTheThresholdAndAgainAsItGrows() {
        fun lateBy(m: Int) = applyRefresh(trip, listOf(trip.legs[0].copy(from = trip.legs[0].from.copy(current = at(m))), null), at(1))
        val before = t0.minusSeconds(30 * 60)
        assertEquals(emptyList<String>(), companionAlerts(trip, lateBy(4), before).map { it.key })
        assertEquals(listOf("delay-0-1"), companionAlerts(lateBy(4), lateBy(6), before).map { it.key })
        assertEquals(emptyList<String>(), companionAlerts(lateBy(6), lateBy(9), before).map { it.key })
        assertEquals(listOf("delay-0-2"), companionAlerts(lateBy(9), lateBy(11), before).map { it.key })
    }
}
