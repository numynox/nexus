package io.github.numynox.nexus.vibilia.commute

import io.github.numynox.nexus.vibilia.data.Commute
import io.github.numynox.nexus.vibilia.data.CommuteExpectation
import io.github.numynox.nexus.vibilia.location.LatLng
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommutePlanTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private val commute = Commute(
        carId = "car",
        weekdays = listOf(1, 2, 3, 4, 5),
        toWorkFrom = "06:30:00",
        toWorkUntil = "09:30:00",
        toHomeFrom = "16:00",
        toHomeUntil = "19:00",
        homeLat = 49.5897,
        homeLng = 11.0040,
        homeRadiusM = 300,
        workLat = 49.5730,
        workLng = 11.0270,
        workRadiusM = 400,
    )

    // 2 Nov 2026 is a Monday; Berlin is UTC+1 in November.
    private fun berlin(local: String): Instant = java.time.LocalDateTime.parse(local).atZone(berlin).toInstant()

    @Test
    fun `a connection inside a window on a commute day picks its direction`() {
        assertEquals(CommuteDirection.ToWork, CommutePlan.directionAt(commute, berlin("2026-11-02T06:30")))
        assertEquals(CommuteDirection.ToWork, CommutePlan.directionAt(commute, berlin("2026-11-02T09:30")))
        assertEquals(CommuteDirection.ToHome, CommutePlan.directionAt(commute, berlin("2026-11-06T17:15")))
    }

    @Test
    fun `outside the windows or on a free day there is nothing to announce`() {
        assertNull(CommutePlan.directionAt(commute, berlin("2026-11-02T06:29")))
        assertNull(CommutePlan.directionAt(commute, berlin("2026-11-02T12:00")))
        assertNull(CommutePlan.directionAt(commute, berlin("2026-11-07T07:00")))
    }

    @Test
    fun `the trip has to start in the geofence it starts from`() {
        val nearHome = LatLng(49.5910, 11.0050) // ~160 m from home
        val atWork = LatLng(49.5731, 11.0271)
        val elsewhere = LatLng(49.6200, 11.1000)
        assertTrue(CommutePlan.startsAtOrigin(commute, CommuteDirection.ToWork, listOf(elsewhere, nearHome)))
        assertFalse(CommutePlan.startsAtOrigin(commute, CommuteDirection.ToWork, listOf(elsewhere, atWork)))
        assertTrue(CommutePlan.startsAtOrigin(commute, CommuteDirection.ToHome, listOf(atWork)))
        assertFalse(CommutePlan.startsAtOrigin(commute, CommuteDirection.ToHome, emptyList()))
    }

    @Test
    fun `the sentence names the place and the arrival time`() {
        val connected = berlin("2026-11-02T07:13")
        val expectations = listOf(CommuteExpectation("to_work", 32 * 60, 8), CommuteExpectation("to_home", null, 0))
        val seconds = CommutePlan.expectedSeconds(expectations, CommuteDirection.ToWork)!!
        assertEquals(
            "Die Ankunftszeit auf der Arbeit ist 7:45 Uhr. Gute Fahrt.",
            CommutePlan.announcement(CommuteDirection.ToWork, connected.plusSeconds(seconds.toLong()), berlin),
        )
        assertEquals(
            "Die Ankunftszeit daheim ist 17:05 Uhr. Gute Fahrt.",
            CommutePlan.announcement(CommuteDirection.ToHome, berlin("2026-11-02T17:05"), berlin),
        )
        assertNull(CommutePlan.expectedSeconds(expectations, CommuteDirection.ToHome))
    }

    @Test
    fun `one announcement per car, direction and day`() {
        val morning = CommutePlan.announcedKey("car", CommuteDirection.ToWork, berlin("2026-11-02T07:00"))
        assertEquals(morning, CommutePlan.announcedKey("car", CommuteDirection.ToWork, berlin("2026-11-02T08:30")))
        assertTrue(morning != CommutePlan.announcedKey("car", CommuteDirection.ToWork, berlin("2026-11-03T07:00")))
        assertTrue(morning != CommutePlan.announcedKey("car", CommuteDirection.ToHome, berlin("2026-11-02T17:00")))
    }
}
