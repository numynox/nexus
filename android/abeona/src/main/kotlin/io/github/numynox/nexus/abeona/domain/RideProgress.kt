package io.github.numynox.nexus.abeona.domain

import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.toInstantOrNull
import java.time.Duration
import java.time.Instant

// Where a ride is right now, stop by stop, mirrored from the web's
// app/abeona/src/lib/progress.ts. Change both together; the cases both test
// suites run are in app/abeona/src/lib/progress.fixtures.json
// (RideProgressFixturesTest).

/** A stop of a ride, with the times the train is there. */
data class ProgressStop(
    val id: String?,
    val name: String,
    val lat: Double?,
    val lon: Double?,
    /** Expected arrival; the ride's first stop has its departure here. */
    val arrival: Instant,
    /** Expected departure; the ride's last stop has its arrival here. */
    val departure: Instant,
    /** 0 at the first stop, 1 at the last, by time. */
    val fraction: Double,
    /** The train has left it (or, for the last stop, arrived there). */
    val passed: Boolean,
)

/** A point on the map. */
data class LatLon(val lat: Double, val lon: Double)

data class RideProgress(
    /** Where the ride stops: its first stop, the stopovers it still serves, its last stop. */
    val stops: List<ProgressStop>,
    /** The stop the train stands at now (before it leaves, at a stopover, or once arrived); null while it is moving. */
    val current: Int?,
    /** The next stop the train arrives at; null once it has arrived at the last one. */
    val next: Int?,
    /** Stops still ahead, the last one included: 0 once arrived. */
    val stopsLeft: Int,
    /** Whole minutes until the train is at [next], rounded up; null once arrived. */
    val minutesToNext: Int?,
    /** 0 before it leaves, 1 once it has arrived: by time. */
    val fraction: Double,
    /** Where the timetable puts the train now, on its line; null without coordinates. */
    val estimate: LatLon?,
)

private fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)

private fun millis(a: Instant, b: Instant) = Duration.between(a, b).toMillis().toDouble()

private fun ProgressStop.point(): LatLon? = lat?.let { la -> lon?.let { LatLon(la, it) } }

/** The index of the line's point nearest to [p], at or after [from]. */
private fun nearestIndex(line: List<List<Double>>, p: LatLon, from: Int): Int {
    var best = from
    var bestDistance = Double.MAX_VALUE
    for (i in from until line.size) {
        val d = distanceMetres(p.lat, p.lon, line[i][1], line[i][0])
        if (d < bestDistance) {
            bestDistance = d
            best = i
        }
    }
    return best
}

/** The point [f] of the way from [a] to [b]: along [line] between the points nearest to each, else straight. */
private fun along(a: LatLon, b: LatLon, f: Double, line: List<List<Double>>?): LatLon {
    val points = line?.filter { it.size >= 2 }
    if (points != null && points.size >= 2) {
        val i = nearestIndex(points, a, 0)
        val j = nearestIndex(points, b, i)
        if (j > i) {
            val part = points.subList(i, j + 1)
            val lengths = (1 until part.size).map { k -> distanceMetres(part[k - 1][1], part[k - 1][0], part[k][1], part[k][0]) }
            val total = lengths.sum()
            if (total > 0) {
                var left = f * total
                for (k in lengths.indices) {
                    if (left <= lengths[k] || k == lengths.size - 1) {
                        val g = if (lengths[k] == 0.0) 0.0 else clamp01(left / lengths[k])
                        return LatLon(
                            part[k][1] + (part[k + 1][1] - part[k][1]) * g,
                            part[k][0] + (part[k + 1][0] - part[k][0]) * g,
                        )
                    }
                    left -= lengths[k]
                }
            }
        }
    }
    return LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
}

/**
 * The progress of [leg] at [now]. Cancelled stopovers and stopovers without
 * any time are left out: the train does not stop there, or nobody knows when.
 * Null when the ride's own ends have no time.
 */
fun rideProgress(leg: Leg, now: Instant): RideProgress? {
    val start = leg.from.expectedAt ?: return null
    val end = leg.to.expectedAt ?: return null
    val span = millis(start, end).coerceAtLeast(1.0)

    data class Raw(val id: String?, val name: String, val lat: Double?, val lon: Double?, val arrival: Instant, val departure: Instant)
    val raw = mutableListOf(Raw(leg.from.id, leg.from.name, leg.from.lat, leg.from.lon, start, start))
    leg.stopovers.forEach { s ->
        if (s.cancelled) return@forEach
        val arrival = (s.arrival ?: s.plannedArrival).toInstantOrNull()
        val departure = (s.departure ?: s.plannedDeparture).toInstantOrNull()
        val a = arrival ?: departure ?: return@forEach
        val d = departure?.takeIf { !it.isBefore(a) } ?: a
        raw += Raw(s.id, s.name, s.lat, s.lon, a, d)
    }
    raw += Raw(leg.to.id, leg.to.name, leg.to.lat, leg.to.lon, end, end)

    val last = raw.size - 1
    val stops = raw.mapIndexed { i, s ->
        ProgressStop(
            s.id, s.name, s.lat, s.lon, s.arrival, s.departure,
            fraction = clamp01(millis(start, s.arrival) / span),
            passed = !now.isBefore(if (i == last) s.arrival else s.departure),
        )
    }

    // The first stop not yet left is where the train is, or what it heads for.
    var current: Int? = null
    var next: Int? = null
    when {
        now.isBefore(start) -> {
            current = 0
            next = if (last > 0) 1 else null
        }
        !now.isBefore(end) -> current = last
        else -> {
            val i = (1..last).firstOrNull { now.isBefore(if (it == last) stops[it].arrival else stops[it].departure) } ?: last
            if (i < last && !now.isBefore(stops[i].arrival)) {
                current = i
                next = i + 1
            } else {
                next = i
            }
        }
    }

    val estimate = when {
        current != null -> stops[current].point()
        next != null -> {
            val a = stops[next - 1]
            val b = stops[next]
            val pa = a.point()
            val pb = b.point()
            if (pa != null && pb != null) {
                val f = clamp01(millis(a.departure, now) / millis(a.departure, b.arrival).coerceAtLeast(1.0))
                along(pa, pb, f, leg.polyline)
            } else {
                null
            }
        }
        else -> null
    }

    return RideProgress(
        stops = stops,
        current = current,
        next = next,
        stopsLeft = next?.let { stops.size - it } ?: 0,
        minutesToNext = next?.let { Math.ceil(millis(now, stops[it].arrival) / 60_000.0).toInt().coerceAtLeast(0) },
        fraction = clamp01(millis(start, now) / span),
        estimate = estimate,
    )
}
