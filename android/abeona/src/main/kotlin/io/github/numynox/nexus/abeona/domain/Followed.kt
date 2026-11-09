package io.github.numynox.nexus.abeona.domain

import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Trip

// The journey as it is actually travelled once a connection from the
// alternatives was taken, mirrored from the web's
// app/abeona/src/lib/followed.ts. Change both together; the cases both test
// suites run are in app/abeona/src/lib/followed.fixtures.json
// (FollowedFixturesTest).

private fun sameStop(aId: String?, aName: String, bId: String?, bName: String): Boolean =
    if (aId != null && bId != null) aId == bId else aName.trim().equals(bName.trim(), ignoreCase = true)

/** [leg] ending at its stopover [k], with the line cut at the point nearest to it. */
private fun cutAt(leg: Leg, k: Int): Leg {
    val stop = leg.stopovers[k]
    val to = StopTime(
        id = stop.id, name = stop.name, lat = stop.lat, lon = stop.lon,
        planned = stop.plannedArrival, current = stop.arrival, cancelled = stop.cancelled,
    )
    var polyline = leg.polyline
    val lat = stop.lat
    val lon = stop.lon
    if (polyline != null && polyline.size > 1 && lat != null && lon != null) {
        val best = polyline.indices.minByOrNull { i ->
            val p = polyline!![i]
            if (p.size < 2) Double.MAX_VALUE else (p[1] - lat) * (p[1] - lat) + (p[0] - lon) * (p[0] - lon)
        } ?: 0
        polyline = polyline.subList(0, maxOf(best, 1) + 1)
    }
    return leg.copy(to = to, stopovers = leg.stopovers.subList(0, k), polyline = polyline)
}

/** The stopover of [leg] where [start] is, or -1. */
private fun stopoverAt(leg: Leg?, start: StopTime): Int {
    if (leg == null || leg.mode == LegMode.Walk) return -1
    return leg.stopovers.indexOfFirst { sameStop(it.id, it.name, start.id, start.name) }
}

/**
 * The legs as travelled: [legs] unchanged without a replacement, else the
 * rides before [missedIdx] and the [replacement], with the ride the
 * replacement leaves from cut at that stop.
 */
fun followedLegs(legs: List<Leg>, missedIdx: Int?, replacement: List<Leg>?): List<Leg> {
    if (missedIdx == null || replacement.isNullOrEmpty() || missedIdx < 0 || missedIdx > legs.size) return legs
    val start = replacement.first().from
    val prefix = legs.subList(0, missedIdx)
    // On the replaced ride itself (on board when the alternative was taken).
    val replaced = legs.getOrNull(missedIdx)
    val onReplaced = stopoverAt(replaced, start)
    if (replaced != null && onReplaced >= 0) return prefix + cutAt(replaced, onReplaced) + replacement
    // On the ride before it (getting off before the change).
    val last = prefix.lastOrNull()
    val onLast = stopoverAt(last, start)
    if (last != null && onLast >= 0) return prefix.dropLast(1) + cutAt(last, onLast) + replacement
    return prefix + replacement
}

/**
 * The ride a connection taken now replaces: on board, the next ride (the
 * change that will not work), else the current one if there is none; before
 * a ride, the one to catch. Null when nothing is left.
 */
fun replacedRide(legs: List<Leg>, day: TravelDay): Int? {
    val index = day.legIndex ?: return null
    if (day.kind == TravelDayKind.Arrived) return null
    if (day.kind != TravelDayKind.OnBoard) return index
    return legs.indices.firstOrNull { it > index && !legs[it].isWalk } ?: index
}

/** What to store when a connection is taken: the ride it replaces (missed_leg_idx) and the legs from there (replacement_legs). */
data class Taken(val missedIdx: Int, val replacement: List<Leg>)

/**
 * Taking [chosen] now, with [day] worked out on the legs as travelled. With
 * nothing taken before, it replaces the ride [replacedRide] names, and
 * [followedLegs] cuts the ride left early. With a connection already taken,
 * the new one goes into that replacement at the same place, so the plan's
 * own rides stay as they were. Null when nothing is left to replace.
 */
fun takeConnection(legs: List<Leg>, missedIdx: Int?, replacement: List<Leg>?, day: TravelDay, chosen: List<Leg>): Taken? {
    if (missedIdx == null || replacement.isNullOrEmpty()) return replacedRide(legs, day)?.let { Taken(it, chosen) }
    val followed = followedLegs(legs, missedIdx, replacement)
    val k = replacedRide(followed, day) ?: return null
    if (k < missedIdx) return Taken(k, chosen)
    return Taken(missedIdx, followedLegs(followed.drop(missedIdx), k - missedIdx, chosen))
}

/** The trip as travelled: its legs replaced from the missed ride on by the connection taken instead. */
val Trip.followed: Trip
    get() {
        val legs = followedLegs(legs, missedLegIdx, replacementLegs)
        return if (legs === this.legs) this else copy(legs = legs)
    }

/** A connection from the alternatives was taken: the trip follows it. */
val Trip.followsReplacement: Boolean get() = missedLegIdx != null && !replacementLegs.isNullOrEmpty()
