package io.github.numynox.nexus.abeona.live

import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.StopTime
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The on-board portals' answers as their public descriptions show them. They
 * are not public APIs: what matters is that the known shape reads, and that
 * anything else reads as "not on board", never as a crash.
 */
class OnboardTest {
    private val at = Instant.parse("2026-11-03T10:40:00Z")

    private val iceStatus = """
        {"connection":true,"serviceLevel":"AVAILABLE_SERVICE","gpsStatus":"VALID","internet":"HIGH",
         "latitude":49.98,"longitude":9.14,"tileY":-102,"tileX":134,"series":"411","serverTime":1793702400000,
         "speed":247.0,"trainType":"ICE","tzn":"ICE1166","wagonClass":"SECOND","connectivity":{"currentState":"HIGH"}}
    """.trimIndent()

    private val iceTrip = """
        {"trip":{"tripDate":"2026-11-03","trainType":"ICE","vzn":"25","actualPosition":40000,"distanceFromLastStop":1000,
         "stopInfo":{"scheduledNext":"8000260_00","actualNext":"8000260_00","actualLast":"8000010_00","finalStationName":"Wien Hbf"},
         "stops":[
          {"station":{"evaNr":"8000105_00","name":"Frankfurt(Main)Hbf"},"timetable":{"scheduledDepartureTime":1793700000000},"track":{"scheduled":"8","actual":"8"},"info":{"passed":true}},
          {"station":{"evaNr":"8000260_00","name":"Würzburg Hbf"},"timetable":{"scheduledArrivalTime":1793704080000,"actualArrivalTime":1793704500000,"arrivalDelay":"+7"},"track":{"scheduled":"4","actual":"5"},"info":{"passed":false}}
         ]}}
    """.trimIndent()

    @Test
    fun readsTheIcePortal() {
        val status = parseIcePortal(iceStatus, iceTrip, at)!!
        assertEquals("ICE 25", status.trainLabel)
        assertEquals(247.0, status.speedKmh!!, 0.01)
        assertEquals("Würzburg Hbf", status.nextStop)
        assertEquals(Instant.ofEpochMilli(1793704500000), status.nextStopAt)
        assertEquals("5", status.nextTrack)
    }

    @Test
    fun theStatusAloneIsEnoughToBeOnBoard() {
        val status = parseIcePortal(iceStatus, null, at)!!
        assertEquals("ICE", status.trainType)
        assertNull(status.trainNumber)
        assertNull(status.nextStop)
    }

    @Test
    fun aCaptivePortalOrAnythingElseIsNotOnBoard() {
        assertNull(parseIcePortal("<html>Login</html>", null, at))
        assertNull(parseIcePortal("{\"error\":\"nope\"}", null, at))
        assertNull(parseRailnet("[]", null, at))
    }

    @Test
    fun readsRailnet() {
        val combined = """
            {"trainType":"RJX","tripNumber":"0860","destination":{"de":"Zürich HB","en":"Zurich HB"},
             "nextStation":{"name":{"de":"Linz Hbf","en":"Linz Hbf"},"arrival":{"scheduled":"12:34","forecast":"12:36"},"track":{"de":"3"}}}
        """.trimIndent()
        val status = parseRailnet(combined, "187.5", at)!!
        assertEquals("RJX 860", status.trainLabel)
        assertEquals(187.5, status.speedKmh!!, 0.01)
        assertEquals("Linz Hbf", status.nextStop)
        assertEquals("3", status.nextTrack)
        assertEquals(Instant.parse("2026-11-03T11:36:00Z"), status.nextStopAt)
    }

    @Test
    fun matchesTheRideByNumberAndType() {
        fun ride(name: String, product: String) = Leg(mode = LegMode.Train, lineName = name, product = product, from = StopTime(name = "a"), to = StopTime(name = "b"))
        val legs = listOf(ride("RE 4", "RE"), Leg(mode = LegMode.Walk, from = StopTime(name = "b"), to = StopTime(name = "b")), ride("ICE 25", "ICE"))
        val status = parseIcePortal(iceStatus, iceTrip, at)
        assertEquals(2, matchOnboardLeg(legs, status))
        assertNull(matchOnboardLeg(legs, status?.copy(trainNumber = "26")))
        assertNull(matchOnboardLeg(legs, status?.copy(trainType = "IC", trainNumber = "4")))
        assertNull(matchOnboardLeg(legs, null))
    }
}
