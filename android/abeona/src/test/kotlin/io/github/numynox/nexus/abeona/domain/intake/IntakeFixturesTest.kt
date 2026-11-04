package io.github.numynox.nexus.abeona.domain.intake

import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Journey
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.Station
import io.github.numynox.nexus.abeona.domain.Travellers
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
    )

    @Serializable
    private data class MatchCase(
        val name: String,
        val share: String,
        val stations: Map<String, String>,
        val journeys: Map<String, List<Journey>>,
        val expect: MatchExpect,
    )

    @Serializable
    private data class Fixtures(
        val share: List<ShareCase>,
        val ticket: List<TicketCase>,
        val pdf: List<PdfCase>,
        val fares: List<FareCase>,
        val platforms: List<PlatformCase>,
        val lines: List<LineCase>,
        val zones: List<ZoneCase>,
        val matching: List<MatchCase>,
        val discounts: List<DiscountCase>,
    )

    @Serializable
    private data class DiscountCase(
        val name: String,
        val ticket: List<Int?>,
        val given: List<Int?>,
        val held: List<Int>,
        val expect: Int?,
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
        assertEquals(c.name, c.expect, parseDbTicket(text))
        // What PdfBox gives: one space where pdftotext keeps columns apart.
        assertEquals("${c.name}, spaces collapsed", c.expect, parseDbTicket(text.replace(Regex("[ \\t]+"), " ")))
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
    fun discounts() = fixtures.discounts.forEach { c ->
        assertEquals(c.name, c.expect, Travellers.nextDiscount(c.ticket, c.given, c.held))
    }

    /** The key the fixtures use: the web's `toISOString()`, with milliseconds. */
    private fun Instant.webIso(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC).format(this)

    @Test
    fun matching() = fixtures.matching.forEach { c ->
        val parsed = checkNotNull(parseShareText(resourceText(c.share)))
        val port = object : TransitPort {
            override suspend fun stations(query: String) = c.stations[query]?.let { listOf(Station(it, query)) } ?: emptyList()
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
    }

    @Test
    fun priceOfAReturnBookingIsOnlyOffered() {
        val ticket = fixtures.ticket.first().expect
        val prefill = prefillOf(ticket)
        assertEquals(PriceScope.Booking, prefill.priceScope)
        assertEquals(ticket.priceCents, prefill.priceCents)
        assertEquals(5870, halfOf(11740))
    }
}
