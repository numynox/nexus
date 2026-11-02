package io.github.numynox.nexus.abeona.live

import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.Confidence
import io.github.numynox.nexus.abeona.domain.arrivalDelay
import io.github.numynox.nexus.abeona.domain.departureDelay
import io.github.numynox.nexus.abeona.domain.finalArrivalDelay
import io.github.numynox.nexus.abeona.domain.normalisePlatform
import io.github.numynox.nexus.abeona.domain.trainBindingLifted
import io.github.numynox.nexus.abeona.domain.transfers
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import java.time.Duration
import java.time.Instant

/** What the ongoing notification says: one line, the next thing to do. */
data class CompanionStatus(val title: String, val text: String, val finished: Boolean)

/** Something worth a heads-up: the platform moved, a train was cancelled, a change is at risk. */
data class CompanionAlert(val key: String, val title: String, val text: String)

private fun minutesUntil(now: Instant, then: Instant): Long =
    Math.ceil(Duration.between(now, then).toMillis() / 60_000.0).toLong().coerceAtLeast(0)

/**
 * The line for the ongoing notification, e.g.
 * "RE 4 → Frankfurt Hbf · Pl. 7 · change in 12 min · tight".
 * Before a ride: its platform and when it leaves. On board: where to get off,
 * the next train's platform, and how the change looks. Finished once the last
 * ride has arrived.
 */
fun companionStatus(trip: Trip, now: Instant): CompanionStatus {
    val title = trip.displayTitle
    val rides = trip.legs.withIndex().filter { !it.value.isWalk }
    val current = rides.firstOrNull { (_, leg) -> leg.to.expectedAt?.isAfter(now) ?: false }
        ?: return CompanionStatus(title, "Arrived at ${trip.destinationName}", finished = true)
    val (index, leg) = current
    val parts = mutableListOf("${leg.label} → ${leg.to.name}")
    if (leg.cancelled) {
        parts += "cancelled"
        return CompanionStatus(title, parts.joinToString(" · "), finished = false)
    }
    val departure = leg.from.expectedAt
    if (departure != null && departure.isAfter(now)) {
        TripFormat.platform(leg.from.shownPlatform)?.let { parts += it }
        parts += "departs ${TripFormat.relative(minutesUntil(now, departure))}"
        departureDelay(leg).takeIf { it > 0 }?.let { parts += "+$it" }
    } else {
        val change = transfers(trip.legs).firstOrNull { it.arriving == index }
        if (change != null) {
            TripFormat.platform(trip.legs[change.departing].from.shownPlatform)?.let { parts += it }
            leg.to.expectedAt?.let { parts += "change ${TripFormat.relative(minutesUntil(now, it))}" }
            parts += change.confidence.label.lowercase()
        } else {
            TripFormat.platform(leg.to.shownPlatform)?.let { parts += it }
            parts += "arrives ${TripFormat.time(leg.to.expectedAt)}"
            arrivalDelay(leg).takeIf { it > 0 }?.let { parts += "+$it" }
        }
    }
    return CompanionStatus(title, parts.joinToString(" · "), finished = false)
}

/**
 * What changed between two refreshes that someone on the platform needs to
 * know now. Only news: a platform that was already changed is not repeated.
 * Each alert has a [CompanionAlert.key], so the same news is posted once.
 */
fun companionAlerts(before: Trip, after: Trip, now: Instant): List<CompanionAlert> {
    val alerts = mutableListOf<CompanionAlert>()
    after.legs.forEachIndexed { index, leg ->
        val old = before.legs.getOrNull(index) ?: return@forEachIndexed
        if (leg.isWalk) return@forEachIndexed
        if (leg.cancelled && !old.cancelled) {
            alerts += CompanionAlert("cancel-$index", "${leg.label} is cancelled", "It was to leave ${leg.from.name} at ${TripFormat.time(leg.from.plannedAt)}. Check DB Navigator for alternatives.")
        }
        val departed = leg.from.expectedAt?.isBefore(now) ?: false
        val oldPlatform = normalisePlatform(old.from.shownPlatform)
        val newPlatform = normalisePlatform(leg.from.shownPlatform)
        if (!departed && newPlatform != null && oldPlatform != null && newPlatform != oldPlatform) {
            alerts += CompanionAlert(
                "platform-$index-$newPlatform",
                "Platform change: ${leg.label} now from Pl. ${leg.from.shownPlatform}",
                "At ${leg.from.name}, ${TripFormat.time(leg.from.expectedAt)} (was Pl. ${old.from.shownPlatform}).",
            )
        }
    }
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
    return alerts
}
