package io.github.numynox.nexus.abeona.live

import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.data.toInstantOrNull
import io.github.numynox.nexus.abeona.domain.TravelDay
import io.github.numynox.nexus.abeona.domain.TravelDayKind
import io.github.numynox.nexus.abeona.domain.TravelDayRules
import io.github.numynox.nexus.abeona.domain.TravelDaySource
import io.github.numynox.nexus.abeona.domain.TravelSignals
import io.github.numynox.nexus.abeona.domain.distanceMetres
import io.github.numynox.nexus.abeona.domain.distanceToLineMetres
import io.github.numynox.nexus.abeona.domain.travelDay
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** The three ways Abeona places the traveller, strongest first (glossary: signals). */
enum class EvidenceKind { Wifi, Location, Timetable }

/** What a signal does for the travel day right now. */
enum class EvidenceState {
    /** It decided where the traveller is. */
    Decides,
    /** It has something to say, and it agrees. */
    Agrees,
    /** It has nothing to say: not on a train's Wi-Fi, no fix yet. */
    Silent,
    /** It is turned off or not allowed. */
    Off,
}

/** One line of "How Abeona knows": a signal, what it says, and whether it decided. */
data class Evidence(val kind: EvidenceKind, val state: EvidenceState, val title: String, val detail: String)

/** "Train Wi-Fi", "Location", "Timetable": what placed the traveller, for the notification's small print. */
fun sourceLabel(day: TravelDay): String = when (day.source) {
    TravelDaySource.Onboard -> "Train Wi-Fi"
    TravelDaySource.Location -> "Location"
    TravelDaySource.Time -> "Timetable"
}

fun sourceLabel(trip: Trip, now: Instant, stationMinutes: Int, signals: TravelSignals): String =
    sourceLabel(travelDay(trip.legs, now, stationMinutes, signals))

/**
 * Each signal and what it says about [trip] at [now], for the dashboard's
 * "How Abeona knows". [onboard] and [fix] are the latest, fresh or not:
 * a stale one reads as silent. [locationOn] is the setting with the
 * permission; [companionRunning] whether the position is being followed.
 */
fun evidence(
    trip: Trip,
    day: TravelDay,
    now: Instant,
    onboard: OnboardStatus?,
    fix: Fix?,
    locationOn: Boolean,
    companionRunning: Boolean,
): List<Evidence> {
    val legs = trip.legs
    val freshOnboard = onboard?.takeIf { Duration.between(it.at, now) <= LiveSignals.FRESH }
    val wifi = when {
        freshOnboard == null -> Evidence(EvidenceKind.Wifi, EvidenceState.Silent, "Train Wi-Fi", "Not connected to a train's Wi-Fi")
        else -> {
            val k = matchOnboardLeg(legs, freshOnboard)
            val train = freshOnboard.trainLabel ?: "a train"
            when {
                k == null -> Evidence(EvidenceKind.Wifi, EvidenceState.Silent, "Train Wi-Fi", "${freshOnboard.portal} names $train, not one of your rides")
                day.source == TravelDaySource.Onboard -> Evidence(EvidenceKind.Wifi, EvidenceState.Decides, "Train Wi-Fi", "${freshOnboard.portal} names the ${legs[k].label}: you are on it")
                else -> Evidence(EvidenceKind.Wifi, EvidenceState.Agrees, "Train Wi-Fi", "${freshOnboard.portal} names the ${legs[k].label}")
            }
        }
    }

    val position = fix?.takeIf { Duration.between(it.at, now) <= LiveSignals.FRESH }?.position
    val location = when {
        !locationOn -> Evidence(EvidenceKind.Location, EvidenceState.Off, "Location", "Off · use it to see where you are on the map")
        !companionRunning -> Evidence(EvidenceKind.Location, EvidenceState.Off, "Location", "Followed while the live companion runs: start it to use it")
        position == null -> Evidence(EvidenceKind.Location, EvidenceState.Silent, "Location", "Waiting for a position fix")
        else -> {
            val state = if (day.source == TravelDaySource.Location) EvidenceState.Decides else EvidenceState.Agrees
            val stops = legs.filter { !it.isWalk }.flatMap { leg ->
                listOf(leg.from.name to (leg.from.lat to leg.from.lon), leg.to.name to (leg.to.lat to leg.to.lon)) +
                    leg.stopovers.map { it.name to (it.lat to it.lon) }
            }
            val near = stops.mapNotNull { (name, p) ->
                val lat = p.first ?: return@mapNotNull null
                val lon = p.second ?: return@mapNotNull null
                name to distanceMetres(position.lat, position.lon, lat, lon)
            }.minByOrNull { it.second }
            val line = legs.filter { !it.isWalk }.mapNotNull { leg ->
                leg.polyline?.takeIf { it.isNotEmpty() }?.let { distanceToLineMetres(position.lat, position.lon, it) }
            }.minOrNull()
            val speed = position.speedKmh?.takeIf { it >= 1 }?.let { " · ${it.roundToInt()} km/h" }.orEmpty()
            val detail = when {
                near != null && near.second <= TravelDayRules.NEAR_STOP_METRES -> "At ${near.first}$speed"
                line != null && line <= TravelDayRules.ON_LINE_METRES -> "On your route$speed"
                line != null -> "${formatKm(line)} from your route$speed"
                near != null -> "${formatKm(near.second)} from ${near.first}$speed"
                else -> "Position known$speed"
            }
            Evidence(EvidenceKind.Location, state, "Location", detail)
        }
    }

    val realtime = legs.any { it.realtime }
    val refreshed = trip.refreshedAt.toInstantOrNull()
    val timetable = Evidence(
        EvidenceKind.Timetable,
        if (day.source == TravelDaySource.Time) EvidenceState.Decides else EvidenceState.Agrees,
        "Timetable",
        when {
            day.kind == TravelDayKind.Arrived -> "The last ride has arrived"
            realtime && refreshed != null -> "Live times, updated ${TripFormat.time(refreshed)}"
            realtime -> "Live times"
            else -> "Planned times: no live data for these trains"
        },
    )
    return listOf(wifi, location, timetable)
}

private fun formatKm(metres: Double): String =
    if (metres < 1000) "${(metres / 10).roundToInt() * 10} m" else "%.1f km".format(java.util.Locale.ENGLISH, metres / 1000)
