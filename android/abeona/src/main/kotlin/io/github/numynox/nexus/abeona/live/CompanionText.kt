package io.github.numynox.nexus.abeona.live

import io.github.numynox.nexus.abeona.data.AlertSettings
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.Confidence
import io.github.numynox.nexus.abeona.domain.TravelDayKind
import io.github.numynox.nexus.abeona.domain.TravelDayRules
import io.github.numynox.nexus.abeona.domain.TravelSignals
import io.github.numynox.nexus.abeona.domain.arrivalDelay
import io.github.numynox.nexus.abeona.domain.departureDelay
import io.github.numynox.nexus.abeona.domain.finalArrivalDelay
import io.github.numynox.nexus.abeona.domain.normalisePlatform
import io.github.numynox.nexus.abeona.domain.trainBindingLifted
import io.github.numynox.nexus.abeona.domain.transfers
import io.github.numynox.nexus.abeona.domain.travelDay
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import java.time.Instant
import kotlin.math.roundToInt

/** What the ongoing notification says: one line, the next thing to do. */
data class CompanionStatus(val title: String, val text: String, val finished: Boolean)

/** Something worth a heads-up: the platform moved, a train was cancelled, a change is at risk. */
data class CompanionAlert(val key: String, val title: String, val text: String)

/**
 * The line for the ongoing notification, from the travel day (domain/TravelDay.kt), e.g.
 * "Tight · change in 12 min · RE 4 → Frankfurt Hbf · Pl. 7".
 * Before leaving: when to leave, then the first ride's platform and
 * departure. At the station or changing: the ride to catch, its platform and
 * when it leaves. On board before a change: how the change looks and when it
 * comes first, since a narrow notification cuts the tail, then where to get
 * off and the next train's platform. On the last ride: where and when it
 * arrives, and the train's speed when its Wi-Fi says. A missed change leads
 * with "Looks like". Finished once the last ride has arrived.
 */
fun companionStatus(
    trip: Trip,
    now: Instant,
    stationMinutes: Int = TravelDayRules.DEFAULT_STATION_MINUTES,
    signals: TravelSignals = TravelSignals(),
    onboard: OnboardStatus? = null,
): CompanionStatus {
    val title = trip.displayTitle
    val day = travelDay(trip.legs, now, stationMinutes, signals)
    val index = day.legIndex
    if (day.kind == TravelDayKind.Arrived || index == null) {
        return CompanionStatus(title, "Arrived at ${trip.destinationName}", finished = true)
    }
    val leg = trip.legs[index]
    val parts = mutableListOf("${leg.label} → ${leg.to.name}")
    if (day.missedChange && day.kind != TravelDayKind.OnBoard) {
        return CompanionStatus(title, "Looks like you missed the ${leg.label} · see alternatives in Abeona", finished = false)
    }
    if (leg.cancelled) {
        parts += "cancelled"
        return CompanionStatus(title, parts.joinToString(" · "), finished = false)
    }
    when (day.kind) {
        TravelDayKind.BeforeLeaving, TravelDayKind.AtStation, TravelDayKind.Changing -> {
            TripFormat.platform(leg.from.shownPlatform)?.let { parts += it }
            parts += "departs ${TripFormat.relative((day.minutesToDeparture ?: 0).toLong())}"
            departureDelay(leg).takeIf { it > 0 }?.let { parts += "+$it" }
            if (day.kind == TravelDayKind.BeforeLeaving) parts.add(0, "Leave ${TripFormat.relative((day.minutesToLeave ?: 0).toLong())}")
        }
        else -> {
            val change = transfers(trip.legs).firstOrNull { it.arriving == index }
            if (change != null) {
                // The confidence first: a narrow notification cuts the tail.
                parts.addAll(0, listOf(change.confidence.label, "change ${TripFormat.relative((day.minutesToArrival ?: 0).toLong())}"))
                TripFormat.platform(trip.legs[change.departing].from.shownPlatform)?.let { parts += it }
            } else {
                TripFormat.platform(leg.to.shownPlatform)?.let { parts += it }
                parts += "arrives ${TripFormat.time(leg.to.expectedAt)}"
                arrivalDelay(leg).takeIf { it > 0 }?.let { parts += "+$it" }
            }
            onboard?.speedKmh?.takeIf { it >= 1 }?.let { parts += "${it.roundToInt()} km/h" }
            if (day.missedChange) parts.add(0, "Looks like you missed your change")
        }
    }
    return CompanionStatus(title, parts.joinToString(" · "), finished = false)
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
            if (change.confidence == Confidence.AT_RISK && was != Confidence.AT_RISK) {
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
    if (trainBindingLifted(after.fareType, after.trainBinding, delay) &&
        !trainBindingLifted(before.fareType, before.trainBinding, finalArrivalDelay(before.legs))
    ) {
        alerts += CompanionAlert(
            "binding",
            "Train binding lifted",
            "Expected +$delay min at ${after.destinationName}: your ticket is valid on other trains for this route.",
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
