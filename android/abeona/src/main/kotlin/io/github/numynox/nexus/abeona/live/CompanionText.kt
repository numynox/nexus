package io.github.numynox.nexus.abeona.live

import io.github.numynox.nexus.abeona.data.AlertSettings
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.Confidence
import io.github.numynox.nexus.abeona.domain.TravelDayKind
import io.github.numynox.nexus.abeona.domain.TravelDayRules
import io.github.numynox.nexus.abeona.domain.RideProgress
import io.github.numynox.nexus.abeona.domain.TravelSignals
import io.github.numynox.nexus.abeona.domain.arrivalDelay
import io.github.numynox.nexus.abeona.domain.departureDelay
import io.github.numynox.nexus.abeona.domain.finalArrivalDelay
import io.github.numynox.nexus.abeona.domain.hasImpossibleChange
import io.github.numynox.nexus.abeona.domain.normalisePlatform
import io.github.numynox.nexus.abeona.domain.rideProgress
import io.github.numynox.nexus.abeona.domain.trainBindingLifted
import io.github.numynox.nexus.abeona.domain.transfers
import io.github.numynox.nexus.abeona.domain.travelDay
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import java.time.Instant
import kotlin.math.roundToInt

/**
 * What the ongoing notification says. [title] is the one thing to know now
 * ("ICE 728 arrives in 1 h 25 min · Pl. 7 · 10:19"), to the minute; [text] the line under
 * it, which is all a collapsed notification shows, so it carries the time and
 * the delay of that next thing; [lines] everything, for the expanded one,
 * most urgent first. [chip] is the few characters
 * Android 16+ shows in the status bar for a Live Update ("12 min", "Pl. 7");
 * null when there is nothing to count down. [progress] is how far along the
 * ride on board is, for the notification's bar.
 */
data class CompanionStatus(
    val title: String,
    val text: String,
    val finished: Boolean,
    val chip: String? = null,
    val lines: List<String> = listOf(text),
    val progress: RideProgress? = null,
)

/** "4 min", "1 h 05": short enough for the status bar chip. */
internal fun chipMinutes(minutes: Int?): String? {
    val m = minutes?.coerceAtLeast(0) ?: return null
    return if (m < 60) "$m min" else "${m / 60} h ${"%02d".format(m % 60)}"
}

/** Something worth a heads-up: the platform moved, a train was cancelled, a change is at risk. */
data class CompanionAlert(val key: String, val title: String, val text: String)

private fun withDelay(text: String, delay: Int) = if (delay > 0) "$text (+$delay)" else text

/**
 * "Next: Aschaffenburg Hbf 10:35", "Now at Aschaffenburg Hbf · then Würzburg
 * Hbf", "Next stop: Würzburg Hbf": the ride's next stop, from the timetable
 * and the live times (domain/RideProgress.kt). How many are left is
 * [stopsLeft], on the notification's last line.
 */
fun nextStopLine(progress: RideProgress): String? {
    val last = progress.stops.lastIndex
    val current = progress.current
    val next = progress.next ?: return null
    return when {
        current != null && current in 1 until last ->
            "Now at ${progress.stops[current].name}" + if (next == last) " · then ${progress.stops[next].name}" else ""
        next == last -> "Next stop: ${progress.stops[next].name}"
        else -> "Next: ${progress.stops[next].name} ${TripFormat.time(progress.stops[next].arrival)}"
    }
}

/** "3 stops left", "last stop". */
fun stopsLeft(progress: RideProgress): String = if (progress.stopsLeft == 1) "last stop" else "${progress.stopsLeft} stops left"

/**
 * The notification for the travel day (domain/TravelDay.kt).
 * Before leaving: when to leave, then the first ride. At the station or
 * changing: the platform and the train, with the change's margin. On board:
 * the title is the train, when it arrives where the next thing happens, the
 * platform there and the time ("ICE 728 arrives in 1 h 25 min · Pl. 7 ·
 * 10:19"); then the next train (platform, time, delay, minutes to change,
 * and how the change looks unless it is safe), the next stop, and last how
 * many stops are left and the train's speed when its Wi-Fi says. A missed
 * change leads with "Looks like".
 * Finished once the last ride has arrived.
 */
fun companionStatus(
    trip: Trip,
    now: Instant,
    stationMinutes: Int = TravelDayRules.DEFAULT_STATION_MINUTES,
    signals: TravelSignals = TravelSignals(),
    onboard: OnboardStatus? = null,
): CompanionStatus {
    val day = travelDay(trip.legs, now, stationMinutes, signals)
    val index = day.legIndex
    if (day.kind == TravelDayKind.Arrived || index == null) {
        val last = trip.legs.lastOrNull { !it.isWalk }
        val text = withDelay("${trip.displayTitle} · ${TripFormat.time(trip.expectedArrival)}", last?.let(::arrivalDelay) ?: 0)
        return CompanionStatus("Arrived at ${trip.destinationName}", text, finished = true)
    }
    val leg = trip.legs[index]
    val platform = TripFormat.platform(leg.from.shownPlatform)
    if (day.missedChange && day.kind != TravelDayKind.OnBoard) {
        return CompanionStatus(
            "Looks like you missed the ${leg.label}",
            "It left ${leg.from.name} at ${TripFormat.time(leg.from.expectedAt)} · see alternatives in Abeona",
            finished = false,
            chip = "Missed",
        )
    }
    if (leg.cancelled) {
        return CompanionStatus(
            "${leg.label} is cancelled",
            "It was to leave ${leg.from.name} at ${TripFormat.time(leg.from.plannedAt)} · see alternatives in Abeona",
            finished = false,
            chip = "Cancelled",
        )
    }
    val toward = leg.headsign ?: leg.to.name
    return when (day.kind) {
        TravelDayKind.BeforeLeaving -> {
            val ride = withDelay(
                listOfNotNull(leg.label, platform, TripFormat.time(leg.from.expectedAt)).joinToString(" · "),
                departureDelay(leg),
            ) + " from ${leg.from.name}"
            CompanionStatus(
                "Leave ${TripFormat.relative((day.minutesToLeave ?: 0).toLong())}",
                ride,
                finished = false,
                chip = chipMinutes(day.minutesToLeave),
                lines = listOf(ride, "$stationMinutes min to the station"),
            )
        }
        TravelDayKind.AtStation, TravelDayKind.Changing -> {
            val title = listOfNotNull(platform, "${leg.label} ${TripFormat.relative((day.minutesToDeparture ?: 0).toLong())}").joinToString(" · ")
            val text = withDelay("to $toward · departs ${TripFormat.time(leg.from.expectedAt)}", departureDelay(leg))
            val lines = mutableListOf(text)
            if (day.kind == TravelDayKind.Changing) {
                transfers(trip.legs).firstOrNull { it.departing == index }?.let { change ->
                    val from = trip.legs[change.arriving].to.shownPlatform
                    lines += listOfNotNull(
                        "${change.availableMinutes} min to change · ${change.confidence.label}",
                        if (change.walkMinutes > 0) "walk ${change.walkMinutes} min" else from?.let { "from Pl. $it" },
                    ).joinToString(" · ")
                }
            }
            CompanionStatus(
                title,
                text,
                finished = false,
                chip = platform ?: chipMinutes(day.minutesToDeparture),
                lines = lines,
            )
        }
        else -> {
            val change = transfers(trip.legs).firstOrNull { it.arriving == index }
            val progress = rideProgress(leg, now)
            // The train, when it arrives where the next thing happens, the platform there and the time.
            val title = if (day.missedChange) {
                "Looks like you missed your change"
            } else {
                listOfNotNull(
                    "${leg.label} arrives ${TripFormat.relative((day.minutesToArrival ?: 0).toLong())}",
                    TripFormat.platform(leg.to.shownPlatform),
                    withDelay(TripFormat.time(leg.to.expectedAt), arrivalDelay(leg)),
                ).joinToString(" · ")
            }
            val lines = mutableListOf<String>()
            // The last ride: the title names no place, so the destination leads the lines.
            if (change == null && !day.missedChange) lines += leg.to.name
            if (change != null) {
                val next = trip.legs[change.departing]
                lines += listOfNotNull(
                    "Then ${next.label}",
                    TripFormat.platform(next.from.shownPlatform),
                    withDelay(TripFormat.time(next.from.expectedAt), departureDelay(next)),
                    // Safe goes without saying; tight, at risk or impossible does not.
                    "${change.availableMinutes} min change" + if (change.confidence == Confidence.SAFE) "" else " (${change.confidence.label})",
                ).joinToString(" · ")
            }
            // The stop coming up; not "Next stop: <where the title arrives>".
            val upcoming = progress?.takeIf { it.next != it.stops.lastIndex || (it.current ?: -1) in 1 until it.stops.lastIndex }
            upcoming?.let(::nextStopLine)?.let { lines += it }
            // Last, small: how many stops are left and the train's speed (the freshness follows on this line).
            listOfNotNull(
                progress?.let(::stopsLeft),
                onboard?.speedKmh?.takeIf { it >= 1 }?.let { "${it.roundToInt()} km/h" },
            ).takeIf { it.isNotEmpty() }?.let { lines += it.joinToString(" · ") }
            CompanionStatus(
                title,
                lines.firstOrNull() ?: leg.to.name,
                finished = false,
                chip = if (day.missedChange) "Missed" else chipMinutes(day.minutesToArrival),
                lines = lines.ifEmpty { listOf(leg.to.name) },
                progress = progress,
            )
        }
    }
}

/** The delay that matters for a ride now: its departure until it has left, then its arrival. */
private fun liveDelay(leg: Leg, now: Instant): Int =
    if (leg.from.expectedAt?.isAfter(now) == true) departureDelay(leg) else arrivalDelay(leg)

/**
 * What changed between two refreshes that someone on the platform needs to
 * know now, as far as [settings] asks for it. Only news: a platform that was
 * already changed is not repeated. Each alert has a [CompanionAlert.key], so
 * the same news is posted once; a delay alerts when it reaches the threshold
 * and again each time it grows by as much.
 */
fun companionAlerts(before: Trip, after: Trip, now: Instant, settings: AlertSettings = AlertSettings()): List<CompanionAlert> {
    val alerts = mutableListOf<CompanionAlert>()
    after.legs.forEachIndexed { index, leg ->
        val old = before.legs.getOrNull(index) ?: return@forEachIndexed
        if (leg.isWalk) return@forEachIndexed
        if (settings.cancellation && leg.cancelled && !old.cancelled) {
            alerts += CompanionAlert("cancel-$index", "${leg.label} is cancelled", "It was to leave ${leg.from.name} at ${TripFormat.time(leg.from.plannedAt)}. Abeona shows alternatives on the trip.")
        }
        val departed = leg.from.expectedAt?.isBefore(now) ?: false
        val oldPlatform = normalisePlatform(old.from.shownPlatform)
        val newPlatform = normalisePlatform(leg.from.shownPlatform)
        if (settings.platformChange && !departed && newPlatform != null && oldPlatform != null && newPlatform != oldPlatform) {
            alerts += CompanionAlert(
                "platform-$index-$newPlatform",
                "Platform change: ${leg.label} now from Pl. ${leg.from.shownPlatform}",
                "At ${leg.from.name}, ${TripFormat.time(leg.from.expectedAt)} (was Pl. ${old.from.shownPlatform}).",
            )
        }
    }
    if (settings.connectionAtRisk) {
        val oldChanges = transfers(before.legs).associateBy { it.departing }
        transfers(after.legs).forEach { change ->
            val was = oldChanges[change.departing]?.confidence
            if (change.confidence == Confidence.IMPOSSIBLE && was != Confidence.IMPOSSIBLE) {
                val station = after.legs[change.departing].from.name
                val next = after.legs[change.departing]
                val arriving = after.legs[change.arriving]
                alerts += CompanionAlert(
                    "impossible-${change.departing}",
                    "Your change at $station is impossible",
                    "${next.label} leaves at ${TripFormat.time(next.from.expectedAt)}, before the ${arriving.label} is in (${TripFormat.time(arriving.to.expectedAt)}). " +
                        "Abeona shows other ways to ${after.destinationName}.",
                )
            } else if (change.confidence == Confidence.AT_RISK && was != Confidence.AT_RISK && was != Confidence.IMPOSSIBLE) {
                val station = after.legs[change.departing].from.name
                alerts += CompanionAlert(
                    "risk-${change.departing}",
                    "Your change at $station is at risk",
                    "${change.availableMinutes} min to change, about ${change.requiredMinutes} needed. ${after.legs[change.departing].label} leaves ${TripFormat.time(after.legs[change.departing].from.expectedAt)}.",
                )
            }
        }
    }
    val delay = finalArrivalDelay(after.legs)
    if (trainBindingLifted(after.fareType, after.trainBinding, delay, hasImpossibleChange(after.legs)) &&
        !trainBindingLifted(before.fareType, before.trainBinding, finalArrivalDelay(before.legs), hasImpossibleChange(before.legs))
    ) {
        alerts += CompanionAlert(
            "binding",
            "Train binding lifted",
            if (delay >= 20) {
                "Expected +$delay min at ${after.destinationName}: your ticket is valid on other trains for this route."
            } else {
                "A connection on your trip is impossible: your ticket is valid on other trains for this route."
            },
        )
    }
    val step = settings.delayMinutes
    if (step > 0) {
        // The ride that matters now: the first one that has not arrived.
        val index = after.legs.indices.firstOrNull { !after.legs[it].isWalk && after.legs[it].to.expectedAt?.isAfter(now) == true }
        val leg = index?.let { after.legs[it] }
        val old = index?.let { before.legs.getOrNull(it) }
        if (leg != null && old != null && !leg.cancelled) {
            val now1 = liveDelay(leg, now)
            val was = liveDelay(old, now).coerceAtLeast(0)
            if (now1 >= step && now1 / step > was / step) {
                val leaving = leg.from.expectedAt?.isAfter(now) == true
                alerts += CompanionAlert(
                    "delay-$index-${now1 / step}",
                    "${leg.label} is now +$now1",
                    if (leaving) {
                        "Leaves ${leg.from.name} at ${TripFormat.time(leg.from.expectedAt)} instead of ${TripFormat.time(leg.from.plannedAt)}."
                    } else {
                        "Arrives at ${leg.to.name} at ${TripFormat.time(leg.to.expectedAt)} instead of ${TripFormat.time(leg.to.plannedAt)}."
                    },
                )
            }
        }
    }
    return alerts
}

/** A heads-up when a signal says the traveller missed a change (or the first train): once per ride. */
fun missedAlert(trip: Trip, now: Instant, stationMinutes: Int, signals: TravelSignals, settings: AlertSettings): CompanionAlert? {
    if (!settings.connectionAtRisk) return null
    val day = travelDay(trip.legs, now, stationMinutes, signals)
    if (!day.missedChange) return null
    val missed = when (day.kind) {
        TravelDayKind.OnBoard -> trip.legs.indices.firstOrNull { it > (day.legIndex ?: return null) && !trip.legs[it].isWalk }
        else -> day.legIndex
    } ?: return null
    val leg = trip.legs[missed]
    return CompanionAlert(
        "missed-$missed",
        "Looks like you missed the ${leg.label}",
        "It left ${leg.from.name} at ${TripFormat.time(leg.from.expectedAt)}. Abeona shows alternatives your ticket covers.",
    )
}
