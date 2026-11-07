package io.github.numynox.nexus.abeona.domain

import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Stopover
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private val FixtureJson = Json { ignoreUnknownKeys = true }

/**
 * Ride progress against app/abeona/src/lib/progress.fixtures.json — the same
 * file the web's progress.test.ts runs, copied into the test resources by
 * Gradle. Add cases there, not here.
 */
class RideProgressFixturesTest {
    @Serializable
    private data class Stop(
        val id: String? = null,
        val name: String,
        val lat: Double? = null,
        val lon: Double? = null,
        val planned: String? = null,
        val current: String? = null,
    ) {
        fun toStopTime() = StopTime(id, name, lat, lon, planned, current)
    }

    @Serializable
    private data class FixtureStopover(
        val id: String? = null,
        val name: String,
        val lat: Double? = null,
        val lon: Double? = null,
        @SerialName("planned_arrival") val plannedArrival: String? = null,
        val arrival: String? = null,
        @SerialName("planned_departure") val plannedDeparture: String? = null,
        val departure: String? = null,
        val cancelled: Boolean = false,
    )

    @Serializable
    private data class FixtureLeg(val from: Stop, val to: Stop, val stopovers: List<FixtureStopover>, val polyline: List<List<Double>>) {
        fun toLeg() = Leg(
            from = from.toStopTime(), to = to.toStopTime(), polyline = polyline,
            stopovers = stopovers.map { Stopover(it.id, it.name, it.lat, it.lon, it.plannedArrival, it.arrival, it.plannedDeparture, it.departure, it.cancelled) },
        )
    }

    @Serializable
    private data class Point(val lat: Double, val lon: Double)

    @Serializable
    private data class Expected(
        val stops: List<String>,
        val current: Int?,
        val next: Int?,
        val stopsLeft: Int,
        val minutesToNext: Int?,
        val fraction: Double,
        val estimate: Point?,
        val passed: List<Boolean>,
    )

    @Serializable
    private data class Case(val name: String, val now: String, val expect: Expected)

    @Serializable
    private data class Fixtures(val leg: FixtureLeg, val cases: List<Case>)

    private val fixtures: Fixtures = FixtureJson.decodeFromString(
        checkNotNull(javaClass.getResource("/progress.fixtures.json")) { "progress.fixtures.json is not on the test classpath" }.readText(),
    )

    @Test
    fun rideProgress() = fixtures.cases.forEach { c ->
        val p = rideProgress(fixtures.leg.toLeg(), Instant.parse(c.now))
        assertNotNull(c.name, p)
        p!!
        assertEquals(c.name, c.expect.stops, p.stops.map { it.id })
        assertEquals(c.name, c.expect.passed, p.stops.map { it.passed })
        assertEquals(c.name, c.expect.current, p.current)
        assertEquals(c.name, c.expect.next, p.next)
        assertEquals(c.name, c.expect.stopsLeft, p.stopsLeft)
        assertEquals(c.name, c.expect.minutesToNext, p.minutesToNext)
        assertEquals(c.name, c.expect.fraction, p.fraction, 1e-4)
        val e = c.expect.estimate
        if (e == null) {
            assertNull(c.name, p.estimate)
        } else {
            assertNotNull(c.name, p.estimate)
            assertEquals(c.name, e.lat, p.estimate!!.lat, 1e-4)
            assertEquals(c.name, e.lon, p.estimate!!.lon, 1e-4)
        }
    }

    @Test
    fun noTimesNoProgress() {
        val leg = fixtures.leg.toLeg()
        assertNull(rideProgress(leg.copy(from = leg.from.copy(planned = null, current = null)), Instant.now()))
    }
}
