package io.github.numynox.nexus.abeona.domain.intake

import io.github.numynox.nexus.abeona.data.Journey
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.Station
import io.github.numynox.nexus.abeona.data.StopTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// Parsed rides → the connection in the timetable, through abeona-transit's
// `locations` and `journeys`. The Kotlin twin of
// app/abeona/src/lib/intake/match.ts, where the steps are explained; both run
// the "matching" cases of intake.fixtures.json.

/** DB prints local times; read in Europe/Berlin (right for the neighbours, not for the UK or Portugal). */
val IntakeZone: ZoneId = ZoneId.of("Europe/Berlin")

/** What matching needs from the timetable; the repository and the tests each provide one. */
interface TransitPort {
    suspend fun stations(query: String): List<Station>

    /** Journeys from [from] to [to] (stop ids) departing at or after [time]. */
    suspend fun journeys(from: String, to: String, time: Instant): List<Journey>
}

enum class MatchStatus { Full, Partial, None }

data class MatchResult(
    /** The journey to save: the timetable's where found, the parsed rides elsewhere. */
    val journey: Journey,
    val status: MatchStatus,
    val matched: Int,
    val total: Int,
    /** Why nothing matched, when a lookup failed. */
    val problem: String?,
)

fun localToInstant(date: String, time: String, zone: ZoneId = IntakeZone): Instant =
    LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

private fun sameMinute(iso: String?, expected: Instant): Boolean {
    val at = iso?.let { runCatching { Instant.parse(it) }.getOrNull() ?: runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        ?: return false
    return Duration.between(at, expected).abs() < Duration.ofMinutes(1)
}

/** "RE19" = "RE 19", or the bracketed number: "RE19 (4901)" = "RE 4901". */
fun sameLine(parsed: ParsedLeg, lineName: String?): Boolean {
    val key = lineKey(lineName)
    if (key.isEmpty()) return false
    if (key == lineKey(parsed.line)) return true
    return parsed.number != null && parsed.product != null && key == lineKey("${parsed.product}${parsed.number}")
}

/** The timetable's ride is the parsed one: same line, departing and arriving the same minute. */
fun legMatches(parsed: ParsedLeg, leg: Leg): Boolean =
    leg.mode != LegMode.Walk &&
        sameLine(parsed, leg.lineName) &&
        sameMinute(leg.from.planned, localToInstant(parsed.date, parsed.departure)) &&
        sameMinute(leg.to.planned, localToInstant(parsed.arrivalDate, parsed.arrival))

/** A parsed ride as a leg of the contract: no route line, no trip id, nothing live. */
fun parsedToLeg(parsed: ParsedLeg): Leg {
    val bus = parsed.product == "BUS"
    fun stop(name: String, planned: Instant, platform: String?) =
        StopTime(name = name, planned = planned.toString(), plannedPlatform = platform, platform = platform)
    return Leg(
        mode = if (bus) LegMode.Bus else LegMode.Train,
        product = if (bus) "Bus" else parsed.product,
        lineName = parsed.line.ifEmpty { null },
        headsign = parsed.headsign,
        from = stop(parsed.from, localToInstant(parsed.date, parsed.departure), parsed.fromPlatform),
        to = stop(parsed.to, localToInstant(parsed.arrivalDate, parsed.arrival), parsed.toPlatform),
    )
}

/** The timetable's leg, with the printed platforms where it has none. */
private fun withPrinted(leg: Leg, parsed: ParsedLeg): Leg {
    fun fill(s: StopTime, platform: String?) =
        if (s.plannedPlatform != null || platform == null) s else s.copy(plannedPlatform = platform, platform = s.platform ?: platform)
    return leg.copy(from = fill(leg.from, parsed.fromPlatform), to = fill(leg.to, parsed.toPlatform))
}

/** The parsed rides alone, for saving when nothing could be looked up. */
fun parsedJourney(legs: List<ParsedLeg>): Journey = Journey(legs = legs.map(::parsedToLeg))

private fun Journey.rides() = legs.filter { it.mode != LegMode.Walk }

private fun Throwable.text() = message ?: toString()

suspend fun matchConnection(legs: List<ParsedLeg>, port: TransitPort): MatchResult = coroutineScope {
    require(legs.isNotEmpty()) { "Nothing to match: no rides were found." }
    val total = legs.size
    var problem: String? = null

    // 1. Stations, each name once.
    val names = legs.flatMap { listOf(it.from, it.to) }.distinct()
    val ids: Map<String, String?> = names.map { name ->
        async {
            name to runCatching { port.stations(name).firstOrNull()?.id }.getOrElse { problem = problem ?: it.text(); null }
        }
    }.awaitAll().toMap()

    // 2. The whole journey.
    val first = legs.first()
    val last = legs.last()
    val originId = ids[first.from]
    val destinationId = ids[last.to]
    val candidates = if (originId != null && destinationId != null) {
        runCatching { port.journeys(originId, destinationId, localToInstant(first.date, first.departure)) }
            .getOrElse { problem = problem ?: it.text(); emptyList() }
    } else {
        emptyList()
    }
    for (journey in candidates) {
        val rides = journey.rides()
        if (rides.size == total && rides.withIndex().all { (i, leg) -> legMatches(legs[i], leg) }) {
            var k = 0
            val filled = journey.legs.map { leg -> if (leg.mode == LegMode.Walk) leg else withPrinted(leg, legs[k++]) }
            return@coroutineScope MatchResult(journey.copy(legs = filled), MatchStatus.Full, total, total, null)
        }
    }

    // 3. Ride by ride.
    val pool = candidates.flatMap { it.rides() }
    val found: List<Leg?> = legs.map { parsed ->
        async {
            pool.firstOrNull { legMatches(parsed, it) } ?: run {
                val from = ids[parsed.from]
                val to = ids[parsed.to]
                if (from == null || to == null) return@run null
                runCatching { port.journeys(from, to, localToInstant(parsed.date, parsed.departure)) }
                    .getOrElse { problem = problem ?: it.text(); emptyList() }
                    .firstNotNullOfOrNull { journey -> journey.rides().firstOrNull { legMatches(parsed, it) } }
            }
        }
    }.awaitAll()

    val matched = found.count { it != null }
    MatchResult(
        journey = Journey(legs = legs.mapIndexed { i, parsed -> found[i]?.let { withPrinted(it, parsed) } ?: parsedToLeg(parsed) }),
        status = when (matched) {
            total -> MatchStatus.Full
            0 -> MatchStatus.None
            else -> MatchStatus.Partial
        },
        matched = matched,
        total = total,
        problem = if (matched == total) null else problem,
    )
}
