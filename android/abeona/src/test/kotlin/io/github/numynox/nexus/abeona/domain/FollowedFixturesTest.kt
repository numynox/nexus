package io.github.numynox.nexus.abeona.domain

import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Stopover
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

private val FixtureJson = Json { ignoreUnknownKeys = true }

/**
 * Followed legs against app/abeona/src/lib/followed.fixtures.json — the same
 * file the web's followed.test.ts runs. Add cases there, not here.
 */
class FollowedFixturesTest {
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
    )

    @Serializable
    private data class FixtureLeg(
        val mode: LegMode = LegMode.Train,
        @SerialName("line_name") val lineName: String? = null,
        val from: Stop,
        val to: Stop,
        val stopovers: List<FixtureStopover> = emptyList(),
        val polyline: List<List<Double>>? = null,
    ) {
        fun toLeg() = Leg(
            mode = mode, lineName = lineName, from = from.toStopTime(), to = to.toStopTime(), polyline = polyline,
            stopovers = stopovers.map { Stopover(it.id, it.name, it.lat, it.lon, it.plannedArrival, it.arrival) },
        )
    }

    @Serializable
    private data class Expected(
        val leg: String,
        val stopovers: Int,
        val polylineEnd: List<Double>? = null,
        val toPlanned: String? = null,
        val toCurrent: String? = null,
    )

    @Serializable
    private data class FollowedCase(val name: String, val missedIdx: Int?, val replacement: List<FixtureLeg>?, val expect: List<Expected>)

    @Serializable
    private data class ReplacedCase(val name: String, val kind: String, val legIndex: Int?, val expect: Int?)

    @Serializable
    private data class Day(val kind: String, val legIndex: Int?)

    @Serializable
    private data class ExpectedTaken(val missedIdx: Int, val replacement: List<String>, val followed: List<String>)

    @Serializable
    private data class TakeCase(
        val name: String,
        val missedIdx: Int?,
        val replacement: List<FixtureLeg>?,
        val day: Day,
        val chosen: List<FixtureLeg>,
        val expect: ExpectedTaken?,
    )

    @Serializable
    private data class Fixtures(
        val legs: List<FixtureLeg>,
        val followedLegs: List<FollowedCase>,
        val replacedRide: List<ReplacedCase>,
        val takeConnection: List<TakeCase>,
    )

    private fun kindOf(wire: String) = when (wire) {
        "on_board" -> TravelDayKind.OnBoard
        "changing" -> TravelDayKind.Changing
        "at_station" -> TravelDayKind.AtStation
        "before_leaving" -> TravelDayKind.BeforeLeaving
        else -> TravelDayKind.Arrived
    }

    private val Leg.id get() = "${from.id ?: "?"}>${to.id ?: "?"}"

    private val fixtures: Fixtures = FixtureJson.decodeFromString(
        checkNotNull(javaClass.getResource("/followed.fixtures.json")) { "followed.fixtures.json is not on the test classpath" }.readText(),
    )
    private val legs = fixtures.legs.map { it.toLeg() }

    @Test
    fun followedLegs() = fixtures.followedLegs.forEach { c ->
        val result = followedLegs(legs, c.missedIdx, c.replacement?.map { it.toLeg() })
        assertEquals(c.name, c.expect.map { it.leg to it.stopovers }, result.map { "${it.from.id ?: "?"}>${it.to.id ?: "?"}" to it.stopovers.size })
        c.expect.forEachIndexed { i, e ->
            e.polylineEnd?.let { assertEquals(c.name, it, result[i].polyline?.last()) }
            e.toPlanned?.let { assertEquals(c.name, it, result[i].to.planned) }
            e.toCurrent?.let { assertEquals(c.name, it, result[i].to.current) }
        }
    }

    @Test
    fun replacedRide() = fixtures.replacedRide.forEach { c ->
        val kind = when (c.kind) {
            "on_board" -> TravelDayKind.OnBoard
            "changing" -> TravelDayKind.Changing
            "at_station" -> TravelDayKind.AtStation
            "before_leaving" -> TravelDayKind.BeforeLeaving
            else -> TravelDayKind.Arrived
        }
        assertEquals(c.name, c.expect, replacedRide(legs, TravelDay(kind, legIndex = c.legIndex)))
    }

    @Test
    fun takeConnection() = fixtures.takeConnection.forEach { c ->
        val taken = takeConnection(
            legs, c.missedIdx, c.replacement?.map { it.toLeg() },
            TravelDay(kindOf(c.day.kind), legIndex = c.day.legIndex), c.chosen.map { it.toLeg() },
        )
        val e = c.expect
        if (e == null) {
            assertEquals(c.name, null, taken)
        } else {
            assertEquals(c.name, e.missedIdx, taken?.missedIdx)
            assertEquals(c.name, e.replacement, taken?.replacement?.map { it.id })
            assertEquals(c.name, e.followed, followedLegs(legs, taken!!.missedIdx, taken.replacement).map { it.id })
        }
    }
}
