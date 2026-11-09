package io.github.numynox.nexus.abeona.domain

import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Stopover
import io.github.numynox.nexus.abeona.data.toInstantOrNull
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val FixtureJson = Json { ignoreUnknownKeys = true }

/**
 * The travel day against app/abeona/src/lib/travelday.fixtures.json — the
 * same file the web's travelday.test.ts runs, copied into the test resources
 * by Gradle with the transfer fixtures. Add cases there, not here.
 */
class TravelDayFixturesTest {
    @Serializable
    private data class Stop(
        val id: String? = null,
        val name: String? = null,
        val lat: Double? = null,
        val lon: Double? = null,
        val planned: String? = null,
        val current: String? = null,
        @SerialName("planned_platform") val plannedPlatform: String? = null,
        val platform: String? = null,
    ) {
        fun toStopTime() = StopTime(id, name ?: id ?: "?", lat, lon, planned, current, plannedPlatform, platform)
    }

    @Serializable
    private data class FixtureStopover(
        val id: String? = null,
        val name: String,
        val lat: Double? = null,
        val lon: Double? = null,
        @SerialName("planned_arrival") val plannedArrival: String? = null,
        val arrival: String? = null,
    )

    @Serializable
    private data class FixtureLeg(
        val mode: LegMode = LegMode.Train,
        val cancelled: Boolean = false,
        val from: Stop,
        val to: Stop,
        val stopovers: List<FixtureStopover> = emptyList(),
        val polyline: List<List<Double>>? = null,
    ) {
        fun toLeg() = Leg(
            mode = mode, cancelled = cancelled, from = from.toStopTime(), to = to.toStopTime(), polyline = polyline,
            stopovers = stopovers.map { Stopover(it.id, it.name, it.lat, it.lon, plannedArrival = it.plannedArrival, arrival = it.arrival) },
        )
    }

    @Serializable
    private data class FixturePosition(val lat: Double, val lon: Double, val speedKmh: Double? = null)

    @Serializable
    private data class Signals(val onboardLegIndex: Int? = null, val position: FixturePosition? = null) {
        fun toSignals() = TravelSignals(onboardLegIndex, position?.let { Position(it.lat, it.lon, it.speedKmh) })
    }

    @Serializable
    private data class Expected(
        val kind: String,
        val legIndex: Int? = null,
        val fromLegIndex: Int? = null,
        val leaveAt: String? = null,
        val minutesToLeave: Int? = null,
        val minutesToDeparture: Int? = null,
        val minutesToArrival: Int? = null,
        val cancelled: Boolean = false,
        val missedChange: Boolean = false,
        val source: String = "time",
    )

    @Serializable
    private data class DayCase(
        val name: String,
        val now: String,
        val stationMinutes: Int,
        val signals: Signals? = null,
        val legs: List<FixtureLeg>,
        val expect: Expected,
    )

    @Serializable
    private data class ExpectedOrigin(val id: String?, val name: String, val time: String)

    @Serializable
    private data class OriginCase(
        val name: String,
        val now: String,
        val stationMinutes: Int,
        val signals: Signals? = null,
        val legs: List<FixtureLeg>,
        val expect: ExpectedOrigin?,
    )

    @Serializable
    private data class ReasonCase(
        val name: String,
        val now: String,
        val stationMinutes: Int,
        val signals: Signals? = null,
        val legs: List<FixtureLeg>,
        val expect: String?,
    )

    @Serializable
    private data class ShortCase(
        val name: String,
        val now: String,
        val stationMinutes: Int,
        val signals: Signals? = null,
        val legs: List<FixtureLeg>,
        val expect: Boolean,
    )

    @Serializable
    private data class Fixtures(
        val travelDay: List<DayCase>,
        val alternativesOrigin: List<OriginCase>,
        val alternativesReason: List<ReasonCase>,
        val missedShortConnection: List<ShortCase>,
    )

    private val fixtures: Fixtures = FixtureJson.decodeFromString(
        checkNotNull(javaClass.getResource("/travelday.fixtures.json")) { "travelday.fixtures.json is not on the test classpath" }.readText(),
    )

    /** The web's wire names. */
    private val TravelDayKind.wire: String
        get() = when (this) {
            TravelDayKind.BeforeLeaving -> "before_leaving"
            TravelDayKind.AtStation -> "at_station"
            TravelDayKind.OnBoard -> "on_board"
            TravelDayKind.Changing -> "changing"
            TravelDayKind.Arrived -> "arrived"
        }

    private val AlternativesReason.wire: String
        get() = when (this) {
            AlternativesReason.Missed -> "missed"
            AlternativesReason.Cancelled -> "cancelled"
            AlternativesReason.Impossible -> "impossible"
            AlternativesReason.AtRisk -> "at_risk"
        }

    private fun day(now: String, stationMinutes: Int, signals: Signals?, legs: List<Leg>) =
        travelDay(legs, Instant.parse(now), stationMinutes, signals?.toSignals() ?: TravelSignals())

    @Test
    fun travelDay() = fixtures.travelDay.forEach { c ->
        val d = day(c.now, c.stationMinutes, c.signals, c.legs.map { it.toLeg() })
        val actual = Expected(
            kind = d.kind.wire,
            legIndex = d.legIndex,
            fromLegIndex = d.fromLegIndex,
            leaveAt = d.leaveAt?.toString(),
            minutesToLeave = d.minutesToLeave,
            minutesToDeparture = d.minutesToDeparture,
            minutesToArrival = d.minutesToArrival,
            cancelled = d.cancelled,
            missedChange = d.missedChange,
            source = d.source.name.lowercase(),
        )
        // Instants compare as instants.
        assertEquals(c.name, c.expect.copy(leaveAt = null), actual.copy(leaveAt = null))
        assertEquals(c.name, c.expect.leaveAt.toInstantOrNull(), d.leaveAt)
    }

    @Test
    fun alternativesOrigin() = fixtures.alternativesOrigin.forEach { c ->
        val legs = c.legs.map { it.toLeg() }
        val now = Instant.parse(c.now)
        val origin = alternativesOrigin(legs, day(c.now, c.stationMinutes, c.signals, legs), now)
        if (c.expect == null) {
            assertNull(c.name, origin)
        } else {
            assertEquals(c.name, c.expect.id, origin?.id)
            assertEquals(c.name, c.expect.name, origin?.name)
            assertEquals(c.name, c.expect.time.toInstantOrNull(), origin?.time)
        }
    }

    @Test
    fun alternativesReason() = fixtures.alternativesReason.forEach { c ->
        val legs = c.legs.map { it.toLeg() }
        assertEquals(c.name, c.expect, alternativesReason(legs, day(c.now, c.stationMinutes, c.signals, legs))?.wire)
    }

    @Test
    fun missedShortConnection() = fixtures.missedShortConnection.forEach { c ->
        val legs = c.legs.map { it.toLeg() }
        assertEquals(c.name, c.expect, missedShortConnection(legs, day(c.now, c.stationMinutes, c.signals, legs)))
    }
}
