package io.github.numynox.nexus.abeona.domain

import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.toInstantOrNull
import java.time.Duration
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

// The travel day, mirrored from the web's app/abeona/src/lib/travelday.ts:
// where someone is on their trip right now, from the clock and the live
// values, corrected by this phone's signals (on-board Wi-Fi, position).
// Change both together; the cases both test suites run are in
// app/abeona/src/lib/travelday.fixtures.json (TravelDayFixturesTest).

/** The five states of a travel day, each with one hero card (glossary: Travel day). */
enum class TravelDayKind { BeforeLeaving, AtStation, OnBoard, Changing, Arrived }

/** What decided the state: the clock and live values, the phone's position, or the train's Wi-Fi. */
enum class TravelDaySource { Time, Location, Onboard }

/** A position fix during a trip. */
data class Position(val lat: Double, val lon: Double, val speedKmh: Double?)

/** The phone's optional signals; the web has none of them. */
data class TravelSignals(
    /** The ride the on-board Wi-Fi (ICE Portal, ÖBB Railnet) says this phone is on. */
    val onboardLegIndex: Int? = null,
    val position: Position? = null,
)

data class TravelDay(
    val kind: TravelDayKind,
    /** The ride the state is about: the one to catch, or the one on board. Null once arrived. */
    val legIndex: Int? = null,
    /** Changing: the ride just left. */
    val fromLegIndex: Int? = null,
    /** Before leaving and at the station: the first departure minus the time to the station. */
    val leaveAt: Instant? = null,
    /** Before leaving: whole minutes until [leaveAt], rounded up. */
    val minutesToLeave: Int? = null,
    /** Until the departure of the ride to catch, rounded up; negative once it has left. */
    val minutesToDeparture: Int? = null,
    /** On board: until the ride's arrival, rounded up. */
    val minutesToArrival: Int? = null,
    val cancelled: Boolean = false,
    /** A signal says the traveller is not on the ride the clock says they should be. */
    val missedChange: Boolean = false,
    val source: TravelDaySource = TravelDaySource.Time,
)

object TravelDayRules {
    const val NEAR_STOP_METRES = 300.0
    const val ON_LINE_METRES = 1000.0
    const val MOVING_KMH = 30.0
    const val MISSED_AFTER_MINUTES = 2L
    const val DEFAULT_STATION_MINUTES = 15
}

/** Whole minutes from [now] to [then], rounded up (like JavaScript's Math.ceil). */
private fun minutesUntil(now: Instant, then: Instant): Int =
    Math.ceil(Duration.between(now, then).toMillis() / 60_000.0).toInt()

/** Great-circle distance in metres. */
fun distanceMetres(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLon = Math.toRadians(bLon - aLon)
    val h = sin(dLat / 2).let { it * it } + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).let { it * it }
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

/** Metres from a point to a line of [lon, lat] points, on a flat projection around the point. */
fun distanceToLineMetres(lat: Double, lon: Double, line: List<List<Double>>): Double? {
    if (line.isEmpty()) return null
    val kx = 6_371_000.0 * Math.PI / 180 * cos(Math.toRadians(lat))
    val ky = 6_371_000.0 * Math.PI / 180
    val xy = line.map { (it[0] - lon) * kx to (it[1] - lat) * ky }
    if (xy.size == 1) return hypot(xy[0].first, xy[0].second)
    var best = Double.MAX_VALUE
    for (i in 0 until xy.size - 1) {
        val (ax, ay) = xy[i]
        val (bx, by) = xy[i + 1]
        val dx = bx - ax
        val dy = by - ay
        val len = dx * dx + dy * dy
        val t = if (len == 0.0) 0.0 else (-(ax * dx + ay * dy) / len).coerceIn(0.0, 1.0)
        best = minOf(best, hypot(ax + t * dx, ay + t * dy))
    }
    return best
}

private fun rideIndices(legs: List<Leg>) = legs.indices.filter { !legs[it].isWalk }

private fun state(
    kind: TravelDayKind,
    legs: List<Leg>,
    now: Instant,
    legIndex: Int? = null,
    fromLegIndex: Int? = null,
    leaveAt: Instant? = null,
    minutesToLeave: Int? = null,
    missedChange: Boolean = false,
    source: TravelDaySource = TravelDaySource.Time,
): TravelDay {
    val leg = legIndex?.let { legs[it] }
    val departure = leg?.from?.expectedAt
    val arrival = leg?.to?.expectedAt
    return TravelDay(
        kind = kind,
        legIndex = legIndex,
        fromLegIndex = fromLegIndex,
        leaveAt = leaveAt,
        minutesToLeave = minutesToLeave,
        minutesToDeparture = if (kind != TravelDayKind.OnBoard && kind != TravelDayKind.Arrived && departure != null) minutesUntil(now, departure) else null,
        minutesToArrival = if (kind == TravelDayKind.OnBoard && arrival != null) minutesUntil(now, arrival) else null,
        cancelled = leg?.cancelled ?: false,
        missedChange = missedChange,
        source = source,
    )
}

private fun byTime(legs: List<Leg>, now: Instant, stationMinutes: Int): TravelDay {
    val rides = rideIndices(legs)
    if (rides.isEmpty()) return state(TravelDayKind.Arrived, legs, now)
    val last = legs[rides.last()].to.expectedAt
    if (last != null && !now.isBefore(last)) return state(TravelDayKind.Arrived, legs, now)

    val first = rides.first()
    val departure = legs[first].from.expectedAt ?: return state(TravelDayKind.BeforeLeaving, legs, now, legIndex = first)
    val leaveAt = departure.minus(Duration.ofMinutes(stationMinutes.coerceAtLeast(0).toLong()))
    if (now.isBefore(leaveAt)) {
        return state(TravelDayKind.BeforeLeaving, legs, now, legIndex = first, leaveAt = leaveAt, minutesToLeave = minutesUntil(now, leaveAt))
    }
    if (now.isBefore(departure)) return state(TravelDayKind.AtStation, legs, now, legIndex = first, leaveAt = leaveAt)

    rides.forEachIndexed { k, i ->
        val arrival = legs[i].to.expectedAt
        if (arrival == null || now.isBefore(arrival)) return state(TravelDayKind.OnBoard, legs, now, legIndex = i)
        val j = rides.getOrNull(k + 1) ?: return state(TravelDayKind.Arrived, legs, now)
        val next = legs[j].from.expectedAt
        if (next == null || now.isBefore(next)) return state(TravelDayKind.Changing, legs, now, legIndex = j, fromLegIndex = i)
    }
    return state(TravelDayKind.Arrived, legs, now)
}

private fun near(position: Position, stop: StopTime): Boolean {
    val lat = stop.lat ?: return false
    val lon = stop.lon ?: return false
    return distanceMetres(position.lat, position.lon, lat, lon) <= TravelDayRules.NEAR_STOP_METRES
}

private fun moving(position: Position) = (position.speedKmh ?: 0.0) > TravelDayRules.MOVING_KMH

private fun onLine(position: Position, leg: Leg): Boolean {
    if (!moving(position)) return false
    val line = leg.polyline?.takeIf { it.isNotEmpty() }
        ?: listOfNotNull(
            leg.from.lon?.let { lon -> leg.from.lat?.let { listOf(lon, it) } },
            leg.to.lon?.let { lon -> leg.to.lat?.let { listOf(lon, it) } },
        ).takeIf { it.size == 2 }
        ?: return false
    val d = distanceToLineMetres(position.lat, position.lon, line) ?: return false
    return d <= TravelDayRules.ON_LINE_METRES
}

/**
 * Where the traveller is on their trip at [now]: by the clock first, then
 * corrected by the signals, strongest first. The rules are spelled out on the
 * web's travelDay() in travelday.ts.
 */
fun travelDay(
    legs: List<Leg>,
    now: Instant,
    stationMinutes: Int = TravelDayRules.DEFAULT_STATION_MINUTES,
    signals: TravelSignals = TravelSignals(),
): TravelDay {
    val day = byTime(legs, now, stationMinutes)
    val rides = rideIndices(legs)

    val k = signals.onboardLegIndex
    if (k != null && k in rides) {
        val leftAt = rides.firstOrNull { it > k }?.let { legs[it].from.expectedAt }
        return state(
            TravelDayKind.OnBoard, legs, now, legIndex = k,
            missedChange = leftAt != null && now.isAfter(leftAt),
            source = TravelDaySource.Onboard,
        )
    }

    val position = signals.position ?: return day
    val index = day.legIndex ?: return day
    if (day.kind == TravelDayKind.Arrived) return day
    val leg = legs[index]
    return when (day.kind) {
        TravelDayKind.BeforeLeaving, TravelDayKind.AtStation ->
            if (near(position, leg.from)) {
                state(TravelDayKind.AtStation, legs, now, legIndex = index, leaveAt = day.leaveAt, source = TravelDaySource.Location)
            } else {
                day
            }
        TravelDayKind.OnBoard -> {
            val departure = leg.from.expectedAt
            when {
                onLine(position, leg) -> day.copy(source = TravelDaySource.Location)
                !moving(position) && near(position, leg.from) && departure != null &&
                    !now.isBefore(departure.plus(Duration.ofMinutes(TravelDayRules.MISSED_AFTER_MINUTES))) -> {
                    val previous = rides.lastOrNull { it < index }
                    if (previous == null) {
                        state(TravelDayKind.AtStation, legs, now, legIndex = index, missedChange = true, source = TravelDaySource.Location)
                    } else {
                        state(TravelDayKind.Changing, legs, now, legIndex = index, fromLegIndex = previous, missedChange = true, source = TravelDaySource.Location)
                    }
                }
                else -> day
            }
        }
        TravelDayKind.Changing -> {
            val from = day.fromLegIndex
            when {
                from != null && onLine(position, legs[from]) ->
                    state(TravelDayKind.OnBoard, legs, now, legIndex = from, source = TravelDaySource.Location)
                near(position, leg.from) -> day.copy(source = TravelDaySource.Location)
                else -> day
            }
        }
        TravelDayKind.Arrived -> day
    }
}

/** Where an alternatives search starts, and when the traveller can be there. */
data class AlternativesOrigin(val id: String?, val name: String, val lat: Double?, val lon: Double?, val time: Instant)

/**
 * Before leaving, at the station or changing: the stop of the ride to catch,
 * from now. On board: the train's next stop (the first stopover still ahead,
 * else where the ride ends), from its arrival there; none when that is the
 * destination. Arrived: none.
 */
fun alternativesOrigin(legs: List<Leg>, day: TravelDay, now: Instant): AlternativesOrigin? {
    val index = day.legIndex ?: return null
    if (day.kind == TravelDayKind.Arrived) return null
    val leg = legs[index]
    if (day.kind != TravelDayKind.OnBoard) return AlternativesOrigin(leg.from.id, leg.from.name, leg.from.lat, leg.from.lon, now)
    leg.stopovers.forEach { stop ->
        val at = (stop.arrival ?: stop.plannedArrival).toInstantOrNull()
        if (at != null && at.isAfter(now)) return AlternativesOrigin(stop.id, stop.name, stop.lat, stop.lon, at)
    }
    // Where the ride ends. When that is the destination there is nothing left
    // to find another way to.
    val destination = legs.last().to
    val same = if (leg.to.id != null && destination.id != null) leg.to.id == destination.id else leg.to.name == destination.name
    if (same) return null
    val end = leg.to.expectedAt
    return AlternativesOrigin(leg.to.id, leg.to.name, leg.to.lat, leg.to.lon, if (end != null && end.isAfter(now)) end else now)
}

/** Why the trip should offer alternatives by itself (glossary: Alternatives). */
enum class AlternativesReason { Missed, Cancelled, AtRisk }

/** A missed change first, then a cancelled ride still ahead, then a change ahead at risk. Never once arrived. */
fun alternativesReason(legs: List<Leg>, day: TravelDay): AlternativesReason? {
    val from = day.legIndex ?: return null
    if (day.kind == TravelDayKind.Arrived) return null
    if (day.missedChange) return AlternativesReason.Missed
    if (legs.withIndex().any { (i, leg) -> i >= from && !leg.isWalk && leg.cancelled }) return AlternativesReason.Cancelled
    val ahead = transfers(legs).filter { if (day.kind == TravelDayKind.OnBoard) it.arriving >= from else it.departing >= from }
    return if (ahead.any { it.confidence == Confidence.AT_RISK }) AlternativesReason.AtRisk else null
}
