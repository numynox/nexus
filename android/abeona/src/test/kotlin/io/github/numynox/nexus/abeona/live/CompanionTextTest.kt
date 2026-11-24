package io.github.numynox.nexus.abeona.live

import io.github.numynox.nexus.abeona.data.AlertSettings
import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Stopover
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
        val status = companionStatus(trip, t0.minusSeconds(25 * 60))
        assertEquals("Leave in 10 min", status.title)
        assertEquals(listOf("RE 4 · Pl. 3 · 08:00 from Mainz Hbf", "15 min to the station"), status.lines)
        assertEquals("10 min", status.chip)
    }

    @Test
    fun atTheStationShowsPlatformAndCountdown() {
        val status = companionStatus(trip, t0.minusSeconds(10 * 60))
        assertEquals("Pl. 3 · RE 4 in 10 min", status.title)
        assertEquals("to Frankfurt Hbf · departs 08:00", status.text)
        assertEquals("Pl. 3", status.chip)
    }

    @Test
    fun changingShowsTheNextTrainAndTheMargin() {
        val status = companionStatus(trip, t0.plusSeconds(44 * 60))
        assertEquals("Pl. 7 · ICE 25 in 4 min", status.title)
        assertEquals(listOf("to Würzburg Hbf · departs 08:48", "8 min to change · Safe · from Pl. 11"), status.lines)
    }

    @Test
    fun theTrainsWifiAddsTheSpeed() {
        val onboard = OnboardStatus("ICE Portal", "ICE", "25", 247.4, null, null, "Würzburg Hbf", null, "5", t0)
        val signals = TravelSignals(onboardLegIndex = 1)
        val status = companionStatus(trip, t0.plusSeconds(60 * 60), signals = signals, onboard = onboard)
        assertEquals("ICE 25 arrives in 1 h · Pl. 5 · 10:00", status.title)
        assertEquals(listOf("Würzburg Hbf", "last stop · 247 km/h"), status.lines)
    }

    @Test
    fun standingAtTheChangeAfterTheTrainLeftLooksMissed() {
        val signals = TravelSignals(position = Position(50.1071, 8.6636, 0.0, 10.0))
        val placed = trip.copy(legs = trip.legs.map { it.copy(from = it.from.copy(lat = 50.1071, lon = 8.6636)) })
        val status = companionStatus(placed, t0.plusSeconds(52 * 60), signals = signals)
        assertEquals("Looks like you missed the ICE 25", status.title)
        assertEquals("It left Frankfurt Hbf at 08:48 · see alternatives in Abeona", status.text)
        assertEquals("Missed", status.chip)
        assertEquals("missed-1", missedAlert(placed, t0.plusSeconds(52 * 60), 15, signals, AlertSettings())?.key)
        assertEquals(null, missedAlert(placed, t0.plusSeconds(52 * 60), 15, signals, AlertSettings(connectionAtRisk = false)))
    }

    @Test
    fun onBoardShowsTheChangeAhead() {
        val status = companionStatus(trip, t0.plusSeconds(28 * 60))
        assertEquals("RE 4 arrives in 12 min · Pl. 11 · 08:40", status.title)
        // A safe change goes without saying.
        assertEquals(listOf("Change at Frankfurt Hbf", "Then ICE 25 · Pl. 7 · 08:48 · 8 min change", "last stop"), status.lines)
        assertEquals(0.7, status.progress!!.fraction, 1e-9)
    }

    @Test
    fun aTightChangeSaysSo() {
        val tight = trip.copy(legs = listOf(trip.legs[0], trip.legs[1].copy(from = trip.legs[1].from.copy(planned = at(46)))))
        assertEquals("Then ICE 25 · Pl. 7 · 08:46 · 6 min change (Tight)", companionStatus(tight, t0.plusSeconds(28 * 60)).lines[1])
    }

    @Test
    fun delaysShowRightUnderTheTitle() {
        val late = trip.copy(
            legs = listOf(
                trip.legs[0].copy(to = trip.legs[0].to.copy(current = at(46))),
                trip.legs[1].copy(from = trip.legs[1].from.copy(current = at(55))),
            ),
        )
        val status = companionStatus(late, t0.plusSeconds(28 * 60))
        assertEquals("RE 4 arrives in 18 min · Pl. 11 · 08:46 (+6)", status.title)
        assertEquals("Change at Frankfurt Hbf", status.text)
        assertTrue(status.lines[1], status.lines[1].startsWith("Then ICE 25 · Pl. 7 · 08:55 (+7) · 9 min change"))
    }

    @Test
    fun onBoardCountsTheStopsLeft() {
        val withStop = trip.copy(
            legs = listOf(
                trip.legs[0],
                trip.legs[1].copy(stopovers = listOf(Stopover("a", "Aschaffenburg Hbf", plannedArrival = at(75), plannedDeparture = at(77)))),
            ),
        )
        assertEquals(
            listOf("Würzburg Hbf", "Next: Aschaffenburg Hbf 09:15", "2 stops left"),
            companionStatus(withStop, t0.plusSeconds(60 * 60)).lines,
        )
        assertEquals("Now at Aschaffenburg Hbf · then Würzburg Hbf", companionStatus(withStop, t0.plusSeconds(76 * 60)).lines[1])
        // Only the last stop left: no "Next stop" line for the place the lines already name.
        assertEquals(listOf("Würzburg Hbf", "last stop"), companionStatus(withStop, t0.plusSeconds(90 * 60)).lines)
    }

    @Test
    fun lastRideShowsArrival() {
        val status = companionStatus(trip, t0.plusSeconds(60 * 60))
        assertEquals("ICE 25 arrives in 1 h · Pl. 5 · 10:00", status.title)
        assertEquals("Würzburg Hbf", status.text)
        assertTrue(companionStatus(trip, t0.plusSeconds(121 * 60)).finished)
        assertEquals("Arrived at Würzburg Hbf", companionStatus(trip, t0.plusSeconds(121 * 60)).title)
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
        val alerts = companionAlerts(trip, late, t0)
        assertEquals(listOf("platform-1-9", "risk-1", "binding", "delay-0"), alerts.map { it.key })
        // A newer alert about the same thing replaces the older one: one notification per subject.
        assertEquals(listOf("ride-1-platform", "change-1", "binding", "ride-0-delay"), alerts.map { it.subject })
        // What to act on comes with a heads-up; a delay is an update.
        assertEquals(listOf(AlertChannel.Act, AlertChannel.Act, AlertChannel.Act, AlertChannel.Updates), alerts.map { it.channel })
        assertEquals(emptyList<String>(), companionAlerts(late, late, t0).map { it.key })
    }

    @Test
    fun theBindingAlertSaysWhatTheBannerSays() {
        val late = applyRefresh(trip, listOf(null, trip.legs[1].copy(to = trip.legs[1].to.copy(current = at(142)))), at(1))
        val binding = companionAlerts(trip, late, t0).single { it.key == "binding" }
        assertEquals("You may take another train", binding.title)
        assertEquals("Expected +22 min at Würzburg Hbf: train binding lifted.", binding.text)
        val impossible = applyRefresh(trip, listOf(trip.legs[0].copy(to = trip.legs[0].to.copy(current = at(50))), null), at(1))
        assertEquals("A change cannot be made: train binding lifted.", companionAlerts(trip, impossible, t0).single { it.key == "binding" }.text)
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

    private fun lateBy(m: Int) = applyRefresh(trip, listOf(trip.legs[0].copy(from = trip.legs[0].from.copy(current = at(m))), null), at(1))

    @Test
    fun aDelayAlertsOnceAtTheThreshold() {
        val before = t0.minusSeconds(30 * 60)
        assertEquals(emptyList<String>(), companionAlerts(trip, lateBy(4), before).map { it.key })
        assertEquals(listOf("delay-0"), companionAlerts(lateBy(4), lateBy(6), before).map { it.key })
        // Not again for each further step: what matters next is the destination.
        assertEquals(emptyList<String>(), companionAlerts(lateBy(6), lateBy(9), before, alerted = setOf("delay-0")).map { it.key })
        assertEquals(emptyList<String>(), companionAlerts(lateBy(9), lateBy(16), before, alerted = setOf("delay-0")).map { it.key })
    }

    @Test
    fun aDelayedTrainOnTimeAgainReplacesTheAlertQuietly() {
        val before = t0.minusSeconds(30 * 60)
        val back = companionAlerts(lateBy(6), lateBy(1), before, alerted = setOf("delay-0")).single()
        assertEquals("ontime-0", back.key)
        assertEquals("ride-0-delay", back.subject)
        assertEquals("RE 4 is on time again", back.title)
        assertEquals("Leaves Mainz Hbf at 08:00", back.text)
        assertEquals(AlertChannel.Updates, back.channel)
        assertTrue(back.silent)
        // Only after an alert that it was late.
        assertEquals(emptyList<String>(), companionAlerts(lateBy(6), lateBy(1), before).map { it.key })
    }

    @Test
    fun theDestinationDelayAlertsAtTheCompensationThresholds() {
        fun arriving(m: Int) = applyRefresh(trip, listOf(null, trip.legs[1].copy(to = trip.legs[1].to.copy(current = at(120 + m)))), at(1))
        fun destination(a: Trip, b: Trip) = companionAlerts(a, b, t0.plusSeconds(60 * 60)).filter { it.key.startsWith("destination") }
        val sixty = destination(arriving(50), arriving(62)).single()
        assertEquals("destination-60", sixty.key)
        assertEquals("Expected +62 min at Würzburg Hbf", sixty.title)
        assertEquals("Compensation: 25 % of the fare", sixty.text)
        assertEquals("destination-delay", sixty.subject)
        assertEquals(AlertChannel.Updates, sixty.channel)
        assertEquals(emptyList<CompanionAlert>(), destination(arriving(62), arriving(80)))
        // Past both at once: the higher one only.
        assertEquals(listOf("Compensation: 50 % of the fare"), destination(arriving(50), arriving(125)).map { it.text })
        // Delays off: none of it.
        assertEquals(emptyList<String>(), companionAlerts(arriving(50), arriving(62), t0, AlertSettings(delayMinutes = 0)).map { it.key }.filter { it.startsWith("destination") })
    }

    @Test
    fun aChangeBackOnTrackReplacesTheWarning() {
        fun arrivingAt(m: Int) = applyRefresh(trip, listOf(trip.legs[0].copy(to = trip.legs[0].to.copy(current = at(m))), null), at(1))
        val risky = arrivingAt(47)
        assertEquals("risk-1", companionAlerts(trip, risky, t0).first { it.subject == "change-1" }.key)
        val works = companionAlerts(risky, arrivingAt(41), t0, alerted = setOf("risk-1")).single { it.subject == "change-1" }
        assertEquals("works-1", works.key)
        assertEquals("Change at Frankfurt Hbf works again", works.title)
        assertEquals("7 min · ICE 25 from Pl. 7", works.text)
        assertTrue(works.silent)
        assertEquals(AlertChannel.Updates, works.channel)
        // Nothing was said about it: nothing to take back.
        assertEquals(emptyList<CompanionAlert>(), companionAlerts(risky, arrivingAt(41), t0).filter { it.subject == "change-1" })
    }

    @Test
    fun getOffFiveMinutesBefore() {
        val settings = AlertSettings()
        // On the RE 4, ten minutes out: not yet.
        assertEquals(null, getOffAlert(trip, t0.plusSeconds(30 * 60), 15, TravelSignals(), settings))
        val alert = getOffAlert(trip, t0.plusSeconds(35 * 60), 15, TravelSignals(), settings)!!
        assertEquals("getoff-0", alert.key)
        assertEquals("Get off at Frankfurt Hbf in 5 min", alert.title)
        assertEquals("Pl. 11 · then ICE 25 from Pl. 7", alert.text)
        assertEquals(AlertChannel.Act, alert.channel)
        // The same news on the next tick: the companion posts a key once.
        assertEquals("getoff-0", getOffAlert(trip, t0.plusSeconds(37 * 60), 15, TravelSignals(), settings)?.key)
        // At the destination, with the side the doors open on from the train's Wi-Fi.
        val onboard = OnboardStatus("ÖBB Railnet", "ICE", "25", 120.0, null, null, "Würzburg Hbf", null, null, t0, exitSides = mapOf("Würzburg Hbf" to ExitSide.Right))
        val last = getOffAlert(trip, t0.plusSeconds(116 * 60), 15, TravelSignals(onboardLegIndex = 1), settings, onboard)!!
        assertEquals("Get off at Würzburg Hbf in 4 min", last.title)
        assertEquals("Pl. 5 · exit right", last.text)
        assertEquals(null, getOffAlert(trip, t0.plusSeconds(116 * 60), 15, TravelSignals(), settings.copy(getOffReminder = false)))
        // An unknown platform is left out.
        val noPlatform = trip.copy(legs = listOf(trip.legs[0], trip.legs[1].copy(to = trip.legs[1].to.copy(plannedPlatform = null))))
        assertEquals("", getOffAlert(noPlatform, t0.plusSeconds(116 * 60), 15, TravelSignals(), settings)?.text)
    }

    @Test
    fun theLiveUpdateSaysLess() {
        // Before leaving: the train to catch.
        assertEquals(listOf("RE 4 · Pl. 3 · 08:00 from Mainz Hbf"), companionStatus(trip, t0.minusSeconds(25 * 60)).info)
        // At the station: where to sit, before boarding.
        val seated = trip.copy(legs = listOf(trip.legs[0].copy(coach = "24", seats = "66"), trip.legs[1]))
        val atStation = companionStatus(seated, t0.minusSeconds(10 * 60))
        assertEquals(listOf("to Frankfurt Hbf · departs 08:00", "Coach 24 · Seat 66"), atStation.info)
        // Two lines under the title: the header is not shown on a promoted Live Update.
        assertEquals(CompactText("to Frankfurt Hbf · departs 08:00\nCoach 24 · Seat 66", null), atStation.compact(null))
        // On board with a change ahead: the change in one line; no stops left (the bar has them).
        val onBoard = companionStatus(trip, t0.plusSeconds(28 * 60))
        assertEquals(listOf("Frankfurt Hbf → ICE 25 · Pl. 7 · 8 min"), onBoard.info)
        // The header says only how fresh, and only when it is not live.
        assertEquals(CompactText("Frankfurt Hbf → ICE 25 · Pl. 7 · 8 min", "Offline 08:20"), onBoard.compact("Offline 08:20"))
        val tight = trip.copy(legs = listOf(trip.legs[0], trip.legs[1].copy(from = trip.legs[1].from.copy(planned = at(46)))))
        assertEquals("Frankfurt Hbf → ICE 25 · Pl. 7 · 6 min · tight", companionStatus(tight, t0.plusSeconds(28 * 60)).info[0])
        // On the last ride, from its Wi-Fi: the destination, the speed.
        val portal = OnboardStatus("ICE Portal", "ICE", "25", 247.4, null, null, "Würzburg Hbf", null, "5", t0)
        val last = companionStatus(trip, t0.plusSeconds(60 * 60), signals = TravelSignals(onboardLegIndex = 1), onboard = portal)
        assertEquals(listOf("Würzburg Hbf", "247 km/h"), last.info)
        assertEquals(CompactText("Würzburg Hbf\n247 km/h", null), last.compact(null))
        // With a stop on the way and the train's Wi-Fi: the change, then the next stop and the speed on one line.
        val stopping = trip.copy(legs = listOf(trip.legs[0].copy(stopovers = listOf(Stopover("r", "Rüsselsheim", plannedArrival = at(15), plannedDeparture = at(16)))), trip.legs[1]))
        val re = OnboardStatus("Railnet", "RE", "4", 160.0, null, null, "Rüsselsheim", null, null, t0)
        assertEquals(
            CompactText("Frankfurt Hbf → ICE 25 · Pl. 7 · 8 min\nNext: Rüsselsheim 08:15 · 160 km/h", null),
            companionStatus(stopping, t0.plusSeconds(10 * 60), signals = TravelSignals(onboardLegIndex = 0), onboard = re).compact(null),
        )
        // Changing: the margin, the seat.
        val changing = companionStatus(seated.copy(legs = listOf(trip.legs[0], trip.legs[1].copy(coach = "7", seats = "45-48"))), t0.plusSeconds(44 * 60))
        assertEquals(listOf("to Würzburg Hbf · departs 08:48", "8 min to change · from Pl. 11", "Coach 7 · Seats 45–48"), changing.info)
        // Missed: what happened, and the way to the alternatives.
        val signals = TravelSignals(position = Position(50.1071, 8.6636, 0.0, 10.0))
        val placed = trip.copy(legs = trip.legs.map { it.copy(from = it.from.copy(lat = 50.1071, lon = 8.6636)) })
        val missed = companionStatus(placed, t0.plusSeconds(52 * 60), signals = signals)
        assertEquals(listOf("It left Frankfurt Hbf at 08:48 · see alternatives in Abeona"), missed.info)
        assertTrue(missed.broken)
        assertEquals(false, onBoard.broken)
    }

    @Test
    fun theBarIsTheWholeJourney() {
        // A ride is a segment of 100, however long: 40 minutes on the RE 4, 72 on the ICE 25.
        val before = journeyBar(trip.legs, t0.minusSeconds(25 * 60))!!
        assertEquals(listOf(BarSegment(100, BarTone.Ride), BarSegment(100, BarTone.Ride)), before.segments)
        assertEquals(200, before.max)
        assertEquals(0, before.progress)
        // The change on the boundary, in its confidence's colour.
        assertEquals(listOf(BarPoint(100, BarTone.Ok)), before.points)
        // 28 of the RE's 40 minutes.
        assertEquals(70, journeyBar(trip.legs, t0.plusSeconds(28 * 60))!!.progress)
        // Changing: on the boundary.
        assertEquals(100, journeyBar(trip.legs, t0.plusSeconds(44 * 60))!!.progress)
        // 12 of the ICE's 72 minutes; the change passed stays as a point.
        val last = journeyBar(trip.legs, t0.plusSeconds(60 * 60))!!
        assertEquals(116, last.progress)
        assertEquals(listOf(BarPoint(100, BarTone.Ok)), last.points)
        // Arrived: the end.
        assertEquals(200, journeyBar(trip.legs, t0.plusSeconds(130 * 60))!!.progress)
        // A change at risk is a red point; the rides stay the brand.
        val risky = trip.copy(legs = listOf(trip.legs[0].copy(to = trip.legs[0].to.copy(current = at(47))), trip.legs[1]))
        assertEquals(List(2) { BarSegment(100, BarTone.Ride) }, journeyBar(risky.legs, t0)!!.segments)
        assertEquals(listOf(BarPoint(100, BarTone.Bad)), journeyBar(risky.legs, t0)!!.points)
        // One ride: one segment, nothing to mark.
        val alone = journeyBar(listOf(trip.legs[0]), t0.plusSeconds(10 * 60))!!
        assertEquals(listOf(BarSegment(100, BarTone.Ride)), alone.segments)
        assertEquals(emptyList<BarPoint>(), alone.points)
        assertEquals(25, alone.progress)
        // The companion's bar, by the travel day: before leaving the traveller is on foot.
        val leaving = companionStatus(trip, t0.minusSeconds(25 * 60)).bar!!
        assertEquals(before.copy(walking = true), leaving)
        // Changing, by the clock or the day: a walker on the change's point; on board the train.
        assertTrue(journeyBar(trip.legs, t0.plusSeconds(44 * 60))!!.walking)
        assertTrue(companionStatus(trip, t0.plusSeconds(44 * 60)).bar!!.walking)
        assertEquals(false, companionStatus(trip, t0.plusSeconds(28 * 60)).bar!!.walking)
        // A train missed: the travel day keeps the traveller at the change, where the clock alone would put them on the ICE.
        val signals = TravelSignals(position = Position(50.1071, 8.6636, 0.0, 10.0))
        val placed = trip.copy(legs = trip.legs.map { it.copy(from = it.from.copy(lat = 50.1071, lon = 8.6636)) })
        assertEquals(105, journeyBar(placed.legs, t0.plusSeconds(52 * 60))!!.progress)
        assertEquals(100, companionStatus(placed, t0.plusSeconds(52 * 60), signals = signals).bar!!.progress)
    }

    @Test
    fun aLongJourneyMarksTheChangesAhead() {
        // Six rides of 20 minutes with 10-minute changes: five changes, four points.
        val legs = (0 until 6).map { n ->
            Leg(
                lineName = "RB $n", product = "RB",
                from = StopTime("s$n", "Stop $n", planned = at(n * 30), plannedPlatform = "1"),
                to = StopTime("s${n + 1}", "Stop ${n + 1}", planned = at(n * 30 + 20), plannedPlatform = "2"),
            )
        }
        assertEquals(List(6) { BarSegment(100, BarTone.Ride) }, journeyBar(legs, t0)!!.segments)
        // On the first ride: the next four changes.
        val early = journeyBar(legs, t0.plusSeconds(5 * 60))!!
        assertEquals(25, early.progress)
        assertEquals(listOf(100, 200, 300, 400), early.points.map { it.position })
        // On the fifth: the one change ahead, then the nearest ones passed.
        val late = journeyBar(legs, t0.plusSeconds(125 * 60))!!
        assertEquals(425, late.progress)
        assertEquals(listOf(200, 300, 400, 500), late.points.map { it.position })
        // 55 minutes in, between the second and third rides: on their boundary.
        assertEquals(200, journeyBar(legs, t0.plusSeconds(55 * 60))!!.progress)
        // More rides than Android draws segments: one, the changes still marked.
        val one = journeyBar(legs, t0, maxSegments = 5)!!
        assertEquals(listOf(BarSegment(600, BarTone.Ride)), one.segments)
        assertEquals(listOf(100, 200, 300, 400), one.points.map { it.position })
    }

    @Test
    fun theTopSpeedIsWrittenWhenItIsNews() {
        val speeds = TopSpeedRecorder()
        assertEquals(247, speeds.observe(0, 247.4, null, t0))
        // Not 3 km/h more: not worth a write.
        assertEquals(null, speeds.observe(0, 249.0, null, t0.plusSeconds(90)))
        // More, but within the minute: later.
        val again = speeds.observe(0, 251.0, null, t0.plusSeconds(30))
        assertEquals(null, again)
        assertEquals(251, speeds.observe(0, 250.0, null, t0.plusSeconds(61)))
        // Glitches, and what the leg already holds.
        assertEquals(null, speeds.observe(1, 460.0, null, t0))
        assertEquals(null, speeds.observe(1, 0.4, null, t0))
        assertEquals(null, speeds.observe(2, 280.0, 300, t0))
        assertEquals(303, speeds.observe(2, 303.0, 300, t0))
    }

    @Test
    fun oldLiveValuesSaySo() {
        val updated = t0
        assertEquals("Live 08:00", LiveCompanion.freshness(updated, t0.plusSeconds(170)))
        assertEquals("Not updated since 08:00", LiveCompanion.freshness(updated, t0.plusSeconds(24 * 60)))
        // At the end of the last line, after the speed: no line of its own.
        assertEquals(
            "Arrives 19:47 (+6) · Pl. 6\nICE 528 · 127 km/h · Live 08:00",
            LiveCompanion.expandedText(listOf("Arrives 19:47 (+6) · Pl. 6", "ICE 528 · 127 km/h"), "Live 08:00"),
        )
        assertEquals("Arrives 19:47", LiveCompanion.expandedText(listOf("Arrives 19:47"), null))
        // The Live Update says it only when it is not live.
        assertEquals(null, LiveCompanion.compactFreshness(updated, t0.plusSeconds(170), timetableOnly = false))
        assertEquals("Timetable 08:00", LiveCompanion.compactFreshness(updated, t0.plusSeconds(170), timetableOnly = true))
        assertEquals("Offline 08:00", LiveCompanion.compactFreshness(updated, t0.plusSeconds(24 * 60), timetableOnly = false))
    }
}
