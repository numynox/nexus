package io.github.numynox.nexus.abeona.domain.intake

import io.github.numynox.nexus.abeona.data.Journey
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.Station
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.domain.Confidence
import io.github.numynox.nexus.abeona.domain.worstConfidence
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

/** A real position; the lookups' (0, 0) means none. */
private fun located(lat: Double?, lon: Double?) = lat != null && lon != null && !(lat == 0.0 && lon == 0.0)

/**
 * A stop of a ride nothing matched, placed: where the neighbouring ride
 * found in the timetable is at the same change (its printed name differs:
 * "Frankfurt(Main)Hbf", "Frankfurt (Main) Hauptbahnhof"), else the station
 * looked up for its printed name. The printed name and times stay. As in
 * the web's match.ts.
 */
private fun place(stop: StopTime, neighbour: StopTime?, station: Station?): StopTime = when {
    neighbour != null && located(neighbour.lat, neighbour.lon) -> stop.copy(id = neighbour.id, lat = neighbour.lat, lon = neighbour.lon)
    station != null && located(station.lat, station.lon) -> stop.copy(id = station.id, lat = station.lat, lon = station.lon)
    else -> stop
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
    val stations: Map<String, Station?> = names.map { name ->
        async {
            name to runCatching { port.stations(name).firstOrNull() }.getOrElse { problem = problem ?: it.text(); null }
        }
    }.awaitAll().toMap()
    val ids: Map<String, String?> = stations.mapValues { it.value?.id }

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
        journey = Journey(
            legs = legs.mapIndexed { i, parsed ->
                found[i]?.let { withPrinted(it, parsed) } ?: parsedToLeg(parsed).let { leg ->
                    leg.copy(
                        from = place(leg.from, found.getOrNull(i - 1)?.to, stations[parsed.from]),
                        to = place(leg.to, found.getOrNull(i + 1)?.from, stations[parsed.to]),
                    )
                }
            },
        ),
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

/** A saved ride the timetable did not have when it was saved: a ride, with no trip ref. */
fun isUnfound(leg: Leg): Boolean = leg.mode != LegMode.Walk && leg.tripRef.isNullOrEmpty() && lineKey(leg.lineName).isNotEmpty()

/** A ride the timetable can be asked about: a train or bus with a line name. */
fun isCheckable(leg: Leg): Boolean = leg.mode != LegMode.Walk && lineKey(leg.lineName).isNotEmpty()

/** "ICE 725" → "ICE": the kind of train. */
private fun lineFamily(line: String?): String = lineKey(line).takeWhile { it in 'A'..'Z' }

/** How far after the ride's departure another train of its kind still proves the timetable covers its day. */
private val CoveredWithin: Duration = Duration.ofHours(36)

/** The same train number on these later days proves it too: the next day, or the same weekday a week on. */
private val LaterDays = listOf(1L, 7L)

/** What checking a saved ride against the timetable found; the web's SavedLegCheck. */
data class SavedLegCheck(
    /** The timetable's ride at the same stops and minutes; its trip ref may differ from the saved one. */
    val found: Leg?,
    /** Not found, although the timetable covers its day: it no longer runs as booked. */
    val dropped: Boolean,
)

/**
 * Whether a saved ride still runs as booked (match.ts checkSavedLeg): its
 * line at the same minutes between its stops. Not found is dropped only when
 * the timetable covers its day: later trains of its kind within 36 hours,
 * else the same train number the next day or a week on.
 */
suspend fun checkSavedLeg(leg: Leg, port: TransitPort): SavedLegCheck {
    val none = SavedLegCheck(null, false)
    if (!isCheckable(leg)) return none
    val from = leg.from.id ?: return none
    val to = leg.to.id ?: return none
    val departure = leg.from.plannedAt ?: return none
    val arrival = leg.to.plannedAt ?: return none
    val key = lineKey(leg.lineName)
    val family = lineFamily(leg.lineName)
    var later = false
    for (journey in port.journeys(from, to, departure)) {
        for (ride in journey.rides()) {
            if (!ride.tripRef.isNullOrEmpty() && lineKey(ride.lineName) == key &&
                sameMinute(ride.from.planned, departure) && sameMinute(ride.to.planned, arrival)
            ) {
                return SavedLegCheck(ride, false)
            }
            val at = ride.from.plannedAt ?: continue
            if (family.isNotEmpty() && lineFamily(ride.lineName) == family && at.isAfter(departure) &&
                !at.isAfter(departure.plus(CoveredWithin))
            ) {
                later = true
            }
        }
    }
    if (later) return SavedLegCheck(null, true)
    for (days in LaterDays) {
        val other = port.journeys(from, to, departure.plus(Duration.ofDays(days)))
        if (other.any { journey -> journey.rides().any { lineKey(it.lineName) == key } }) return SavedLegCheck(null, true)
    }
    return none
}

/** The rides' trains, in order. */
private fun trainsOf(legs: List<Leg>): List<String> = legs.filter { it.mode != LegMode.Walk }.map { lineKey(it.lineName) }

/**
 * When a ride was dropped, the connection that is clearly the same trip
 * (match.ts clearReplacement): the same trains in the same order from the
 * ride before the dropped one to where the dropped one ends, then the booked
 * rides after it as they are; every change safe or tight. Null when the
 * traveller has to choose.
 */
suspend fun clearReplacement(legs: List<Leg>, droppedIdx: Int, port: TransitPort): Pair<Int, List<Leg>>? {
    val rideIdx = legs.indices.filter { legs[it].mode != LegMode.Walk }
    val pos = rideIdx.indexOf(droppedIdx)
    if (pos < 0) return null
    val startIdx = rideIdx[maxOf(0, pos - 1)]
    val start = legs[startIdx]
    val dropped = legs[droppedIdx]
    val from = start.from.id ?: return null
    val to = dropped.to.id ?: return null
    val time = start.from.plannedAt ?: return null
    val wanted = trainsOf(legs.subList(startIdx, droppedIdx + 1))
    val after = legs.drop(droppedIdx + 1)
    // A later ride that is not in the timetable either cannot be relied on.
    if (after.any { it.mode != LegMode.Walk && (it.cancelled || it.tripRef.isNullOrEmpty()) }) return null
    for (journey in port.journeys(from, to, time)) {
        if (trainsOf(journey.legs) != wanted) continue
        if (journey.legs.any { it.mode != LegMode.Walk && (it.cancelled || it.tripRef.isNullOrEmpty()) }) continue
        val replacement = journey.legs + after
        val worst = worstConfidence(replacement)
        if (worst == Confidence.IMPOSSIBLE || worst == Confidence.AT_RISK) continue
        return startIdx to replacement
    }
    return null
}
