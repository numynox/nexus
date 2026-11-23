package io.github.numynox.nexus.abeona.ui.live

import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Stopover
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where the train is among the stops of the ride on board, for the band's line and the unfolded stops' rail. */
class RideTrackTest {
    private val t0 = Instant.parse("2026-11-23T20:00:00Z")
    private fun at(m: Double): Instant = t0.plusMillis((m * 60_000).toLong())
    private fun iso(m: Int) = at(m.toDouble()).toString()

    private val leg = Leg(
        lineName = "ICE 25", product = "ICE",
        from = StopTime("ffm", "Frankfurt(Main)Hbf", planned = iso(0)),
        to = StopTime("wue", "Würzburg Hbf", planned = iso(68)),
        stopovers = listOf(
            Stopover("han", "Hanau Hbf", plannedArrival = iso(17), arrival = iso(24), plannedDeparture = iso(19), departure = iso(26)),
            Stopover("off", "Offenbach", plannedArrival = iso(28), plannedDeparture = iso(29), cancelled = true),
            Stopover("asch", "Aschaffenburg Hbf", plannedArrival = iso(30), arrival = iso(37), plannedDeparture = iso(32), departure = iso(39)),
        ),
    )

    @Test
    fun theStopAheadIsTheOneTheTrainStandsAtOrHeadsFor() {
        assertEquals(0, stopAhead(leg, at(10.0))!!.index)
        assertEquals(false, stopAhead(leg, at(10.0))!!.standing)
        // Standing at Hanau, by the live times.
        assertEquals(true, stopAhead(leg, at(25.0))!!.standing)
        // A stop the train does not make is skipped.
        assertEquals(2, stopAhead(leg, at(27.0))!!.index)
        // After the last stop between the ends: where to get off, which the step says.
        assertNull(stopAhead(leg, at(45.0)))
    }

    @Test
    fun theTrainMovesSmoothlyBetweenStopsAndWaitsAtThem() {
        // Boarded at 0, Hanau 24–26, Aschaffenburg 37–39, Würzburg 68.
        val times = listOf(at(0.0) to at(0.0), at(24.0) to at(26.0), at(37.0) to at(39.0), at(68.0) to at(68.0))
        assertEquals(0.0, trackPosition(times, at(-2.0)), 1e-9)
        assertEquals(0.5, trackPosition(times, at(12.0)), 1e-9)
        // Within a minute it moves on: no jump from minute to minute.
        assertEquals(0.5 + 0.5 / 24, trackPosition(times, at(12.5)), 1e-9)
        assertEquals(1.0, trackPosition(times, at(25.0)), 1e-9)
        assertEquals(1.0 + 4.0 / 11, trackPosition(times, at(30.0)), 1e-9)
        assertEquals(3.0, trackPosition(times, at(90.0)), 1e-9)
    }
}
