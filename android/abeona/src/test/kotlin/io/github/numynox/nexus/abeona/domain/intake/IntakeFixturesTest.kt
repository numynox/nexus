package io.github.numynox.nexus.abeona.domain.intake

import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Journey
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.Station
import io.github.numynox.nexus.abeona.domain.TicketNeeds
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The table has a `$comment` the tests do not read. */
internal val IntakeJson = Json { ignoreUnknownKeys = true }

internal fun resourceText(name: String): String =
    checkNotNull(IntakeFixturesTest::class.java.getResource("/$name")) { "$name is not on the test classpath" }.readText()

/**
 * Intake against app/abeona/src/lib/intake/intake.fixtures.json and the
 * samples beside it — the same files the web's intake.test.ts runs. Gradle
 * copies them into the test resources (build.gradle.kts). Add cases there.
 */
class IntakeFixturesTest {
    @Serializable private data class ShareCase(val name: String, val file: String, val expect: ParsedShare)
    @Serializable private data class TicketCase(val name: String, val file: String, val expect: ParsedTicket)
    @Serializable private data class TripsCase(val name: String, val file: String, val expect: List<ParsedTicket>)
    @Serializable private data class PdfCase(val name: String, val file: String, @SerialName("same_as") val sameAs: String)
    @Serializable private data class FareCase(val name: String?, @SerialName("fare_type") val fareType: FareType, val binding: Boolean)
    @Serializable private data class PlatformCase(val value: String?, val expect: String?)
    @Serializable private data class LineCase(val a: String, val b: String, val same: Boolean)
    @Serializable private data class ZoneCase(val date: String, val time: String, val expect: String)

    @Serializable
    private data class MatchExpect(
        val status: String,
        val matched: Int,
        @SerialName("refresh_token") val refreshToken: String?,
        @SerialName("trip_refs") val tripRefs: List<String?>,
        @SerialName("from_names") val fromNames: List<String>,
        @SerialName("from_platforms") val fromPlatforms: List<String?>,
        @SerialName("first_departure") val firstDeparture: String,
        @SerialName("last_arrival") val lastArrival: String,
        val legs: Int? = null,
        /** Per ride: whether its start and its end have a position the map can draw. */
        val located: List<List<Boolean>>? = null,
    )

    @Serializable
    private data class MatchCase(
        val name: String,
        val share: String,
        val stations: Map<String, String>,
        @SerialName("station_coords") val stationCoords: Map<String, List<Double>> = emptyMap(),
        val journeys: Map<String, List<Journey>>,
        val expect: MatchExpect,
    )

    /** A saved ride the timetable did not have, looked up again; the trip ref found, or null. */
    @Serializable
    private data class RefindCase(val name: String, val leg: Leg, val journeys: Map<String, List<Journey>>, val expect: String?)

    @Serializable
    private data class Fixtures(
        val share: List<ShareCase>,
        val ticket: List<TicketCase>,
        val trenitalia: List<TicketCase>,
        val trips: List<TripsCase>,
        val pdf: List<PdfCase>,
        val fares: List<FareCase>,
        val platforms: List<PlatformCase>,
        val lines: List<LineCase>,
        val zones: List<ZoneCase>,
        val matching: List<MatchCase>,
        @SerialName("ticket_summary") val ticketSummary: List<SummaryCase>,
        @SerialName("ticket_cards") val ticketCards: List<CardsCase>,
        val refinding: List<RefindCase>,
    )

    @Serializable
    private data class SummaryExpect(
        @SerialName("traveller_count") val travellerCount: Int?,
        @SerialName("bahncard_discounts") val bahncardDiscounts: List<Int>,
    )

    @Serializable
    private data class SummaryCase(val name: String, val travellers: Int?, val discounts: List<Int?>, val expect: SummaryExpect)

    @Serializable
    private data class FixtureCard(
        val id: String,
        val kind: Int,
        @SerialName("valid_from") val validFrom: String,
        @SerialName("valid_to") val validTo: String,
        val file: Boolean,
    )

    @Serializable
    private data class CardsCase(
        val name: String,
        val cards: List<FixtureCard>,
        val discounts: List<Int>,
        @SerialName("traveller_count") val travellerCount: Int?,
        @SerialName("fare_type") val fareType: String,
        val day: String,
        val expect: List<String>,
    )

    private val fixtures: Fixtures = IntakeJson.decodeFromString(resourceText("intake.fixtures.json"))

    @Test
    fun shareTexts() = fixtures.share.forEach { c ->
        val text = resourceText(c.file)
        assertTrue(c.name, isShareText(text))
        assertFalse(c.name, isDbTicket(text))
        assertEquals(c.name, c.expect, parseShareText(text))
    }

    @Test
    fun tickets() = fixtures.ticket.forEach { c ->
        val text = resourceText(c.file)
        assertTrue(c.name, isDbTicket(text))
        assertFalse(c.name, isShareText(text))
        assertFalse(c.name, isTrenitaliaTicket(text))
        assertEquals(c.name, c.expect, parseDbTicket(text))
        assertEquals(c.name, listOf(c.expect), parseTicketTrips(text))
        // What PdfBox gives: one space where pdftotext keeps columns apart.
        assertEquals("${c.name}, spaces collapsed", c.expect, parseDbTicket(text.replace(Regex("[ \\t]+"), " ")))
    }

    @Test
    fun trenitaliaTickets() = fixtures.trenitalia.forEach { c ->
        val text = resourceText(c.file)
        assertTrue(c.name, isTrenitaliaTicket(text))
        assertFalse(c.name, isDbTicket(text))
        assertFalse(c.name, isShareText(text))
        assertEquals(c.name, c.expect, parseTrenitaliaTicket(text))
        assertEquals(c.name, listOf(c.expect), parseTicketTrips(text))
        assertEquals(c.name, c.expect, parseText(text))
        assertEquals("${c.name}, spaces collapsed", c.expect, parseTrenitaliaTicket(text.replace(Regex("[ \\t]+"), " ")))
    }

    @Test
    fun ticketsForBothDirections() = fixtures.trips.forEach { c ->
        val text = resourceText(c.file)
        assertTrue(c.name, isDbTicket(text))
        assertFalse(c.name, isTrenitaliaTicket(text))
        assertEquals(c.name, c.expect, parseDbTickets(text))
        assertEquals(c.name, c.expect, parseTicketTrips(text))
        assertEquals("${c.name}, spaces collapsed", c.expect, parseTicketTrips(text.replace(Regex("[ \\t]+"), " ")))
    }

    @Test
    fun fareNames() = fixtures.fares.forEach { c ->
        assertEquals("${c.name}", FareGuess(c.fareType, c.binding), fareOf(c.name))
    }

    @Test
    fun platforms() = fixtures.platforms.forEach { c -> assertEquals("${c.value}", c.expect, platformOf(c.value)) }

    @Test
    fun lines() = fixtures.lines.forEach { c -> assertEquals("${c.a} / ${c.b}", c.same, lineKey(c.a) == lineKey(c.b)) }

    @Test
    fun zones() = fixtures.zones.forEach { c ->
        assertEquals("${c.date} ${c.time}", Instant.parse(c.expect), localToInstant(c.date, c.time))
    }

    @Test
    fun ticketSummary() = fixtures.ticketSummary.forEach { c ->
        assertEquals(c.name, TicketNeeds.Summary(c.expect.travellerCount, c.expect.bahncardDiscounts), TicketNeeds.summary(c.travellers, c.discounts))
    }

    @Test
    fun ticketCards() = fixtures.ticketCards.forEach { c ->
        val cards = c.cards.map { TicketNeeds.CardOnFile(it.id, it.kind, LocalDate.parse(it.validFrom), LocalDate.parse(it.validTo), it.file) }
        assertEquals(c.name, c.expect, TicketNeeds.cardsForTrip(cards, c.discounts, c.travellerCount, c.fareType == "bahncard100", LocalDate.parse(c.day)))
    }

    /** The key the fixtures use: the web's `toISOString()`, with milliseconds. */
    private fun Instant.webIso(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC).format(this)

    @Test
    fun matching() = fixtures.matching.forEach { c ->
        val parsed = checkNotNull(parseShareText(resourceText(c.share)))
        val port = object : TransitPort {
            override suspend fun stations(query: String) = c.stations[query]?.let { id ->
                val at = c.stationCoords[query]
                listOf(Station(id, query, lat = at?.get(0) ?: 0.0, lon = at?.get(1) ?: 0.0))
            } ?: emptyList()
            override suspend fun journeys(from: String, to: String, time: Instant) = c.journeys["$from>$to@${time.webIso()}"] ?: emptyList()
        }
        val result = runBlocking { matchConnection(parsed.legs, port) }
        val rides = result.journey.legs.filter { it.mode != LegMode.Walk }
        assertEquals(c.name, c.expect.status, result.status.name.lowercase())
        assertEquals(c.name, c.expect.matched, result.matched)
        assertEquals(c.name, parsed.legs.size, result.total)
        assertEquals(c.name, c.expect.refreshToken, result.journey.refreshToken)
        assertEquals(c.name, c.expect.tripRefs, rides.map { it.tripRef })
        assertEquals(c.name, c.expect.fromNames, rides.map { it.from.name })
        assertEquals(c.name, c.expect.fromPlatforms, rides.map { it.from.plannedPlatform })
        assertEquals(c.name, Instant.parse(c.expect.firstDeparture), rides.first().from.plannedAt)
        assertEquals(c.name, Instant.parse(c.expect.lastArrival), rides.last().to.plannedAt)
        c.expect.legs?.let { assertEquals(c.name, it, result.journey.legs.size) }
        fun at(lat: Double?, lon: Double?) = lat != null && lon != null && !(lat == 0.0 && lon == 0.0)
        c.expect.located?.let { expected ->
            assertEquals("${c.name}: where the map can draw each ride", expected, rides.map { listOf(at(it.from.lat, it.from.lon), at(it.to.lat, it.to.lon)) })
        }
    }

    @Test
    fun refinding() = fixtures.refinding.forEach { c ->
        val port = object : TransitPort {
            override suspend fun stations(query: String) = emptyList<Station>()
            override suspend fun journeys(from: String, to: String, time: Instant) = c.journeys["$from>$to@${time.webIso()}"] ?: emptyList()
        }
        assertEquals(c.name, c.expect, runBlocking { findSavedLeg(c.leg, port) }?.tripRef)
    }
}
