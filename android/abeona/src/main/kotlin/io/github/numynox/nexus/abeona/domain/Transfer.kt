package io.github.numynox.nexus.abeona.domain

import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Leg
import java.time.Duration
import java.time.Instant

// The rules both clients share. They must give the same answers as the web's
// copy (app/abeona/src/lib/transfer.ts) and, for the binding rule, the SQL
// function abeona_train_binding_lifted: change them together
// (decisions/2026-11-02-abeona-train-companion.md).

/** How likely a change is to work out (glossary: Transfer confidence). */
enum class Confidence(val label: String) { SAFE("Safe"), TIGHT("Tight"), AT_RISK("At risk") }

/** The change between arriving leg [arriving] and the next ride [departing], indices into the trip's legs. */
data class Transfer(
    val arriving: Int,
    val departing: Int,
    /** Minutes between the expected arrival and the expected departure. */
    val availableMinutes: Int,
    /** Minutes the change needs: walking plus a margin, or a platform change. */
    val requiredMinutes: Int,
    /** Planned minutes of walk legs between the two rides; 0 when none. */
    val walkMinutes: Int,
    /** True when the change involves a walk leg or another stop. */
    val changesStation: Boolean,
    val confidence: Confidence,
) {
    val marginMinutes: Int get() = availableMinutes - requiredMinutes
}

/** Whole minutes from [planned] to [current], rounded; 0 when either is unknown. */
fun delayMinutes(planned: Instant?, current: Instant?): Int {
    if (planned == null || current == null) return 0
    return minutesBetween(planned, current)
}

/** Rounded whole minutes from [from] to [to], half a minute rounding up (like JavaScript's Math.round). */
fun minutesBetween(from: Instant, to: Instant): Int =
    Math.round(Duration.between(from, to).toMillis() / 60_000.0).toInt()

/** "Gleis 7 ", "platform 7" and "7" are the same platform. */
fun normalisePlatform(platform: String?): String? {
    var value = platform?.trim()?.uppercase() ?: return null
    for (prefix in listOf("GLEIS ", "PLATFORM ")) {
        if (value.startsWith(prefix)) value = value.removePrefix(prefix).trim()
    }
    return value.ifEmpty { null }
}

/** Every change of a journey: each ride and the next ride after it, with the walks between counted. */
fun transfers(legs: List<Leg>): List<Transfer> {
    val rides = legs.indices.filter { !legs[it].isWalk }
    return rides.zipWithNext().mapNotNull { (a, b) -> transferBetween(legs, a, b) }
}

private fun transferBetween(legs: List<Leg>, a: Int, b: Int): Transfer? {
    val arriving = legs[a]
    val departing = legs[b]
    val arrival = arriving.to.expectedAt ?: return null
    val departure = departing.from.expectedAt ?: return null
    val walks = legs.subList(a + 1, b).filter { it.isWalk }
    val walkMinutes = walks.sumOf { walk ->
        val start = walk.from.plannedAt
        val end = walk.to.plannedAt
        if (start != null && end != null) minutesBetween(start, end) else 0
    }
    val available = minutesBetween(arrival, departure)
    val changesStation = walks.isNotEmpty() || arriving.to.id != departing.from.id
    val samePlatform = normalisePlatform(arriving.to.shownPlatform)
        ?.let { it == normalisePlatform(departing.from.shownPlatform) } == true
    val required = when {
        changesStation -> walkMinutes + 3
        samePlatform -> 2
        else -> 5
    }
    val margin = available - required
    val confidence = when {
        arriving.cancelled || departing.cancelled -> Confidence.AT_RISK
        margin < 0 -> Confidence.AT_RISK
        margin < 3 -> Confidence.TIGHT
        else -> Confidence.SAFE
    }
    return Transfer(a, b, available, required, walkMinutes, changesStation, confidence)
}

/** The worst of a journey's changes, or null when it has none. */
fun worstConfidence(legs: List<Leg>): Confidence? = transfers(legs).maxOfOrNull { it.confidence }

/**
 * DB lifts the train binding of a bound long-distance ticket once the expected
 * delay at the destination reaches 20 minutes. Regional tickets, the
 * Deutschlandticket and the BahnCard 100 have no binding to lift.
 */
fun trainBindingLifted(fareType: FareType, trainBinding: Boolean, arrivalDelayMinutes: Int): Boolean =
    trainBinding &&
        fareType !in setOf(FareType.Regional, FareType.Deutschlandticket, FareType.BahnCard100) &&
        arrivalDelayMinutes >= 20

/** The delay of the last ride's arrival: what the destination will see. */
fun finalArrivalDelay(legs: List<Leg>): Int =
    legs.lastOrNull { !it.isWalk }?.let { delayMinutes(it.to.plannedAt, it.to.currentAt) } ?: 0

/** The departure delay of a leg's first stop. */
fun departureDelay(leg: Leg): Int = delayMinutes(leg.from.plannedAt, leg.from.currentAt)

/** The arrival delay of a leg's last stop. */
fun arrivalDelay(leg: Leg): Int = delayMinutes(leg.to.plannedAt, leg.to.currentAt)

/** True when the current platform is known and differs from the planned one. */
fun platformChanged(planned: String?, current: String?): Boolean {
    val p = normalisePlatform(planned) ?: return false
    val c = normalisePlatform(current) ?: return false
    return p != c
}

/** Rides only, in order: the products a journey uses ("ICE", "RE"). */
fun products(legs: List<Leg>): List<String> =
    legs.filter { !it.isWalk }.map { it.product ?: it.label }.distinct()

/** Number of changes: rides minus one. */
fun changes(legs: List<Leg>): Int = (legs.count { !it.isWalk } - 1).coerceAtLeast(0)
