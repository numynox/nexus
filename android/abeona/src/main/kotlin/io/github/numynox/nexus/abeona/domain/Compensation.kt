package io.github.numynox.nexus.abeona.domain

import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode

/**
 * The words around a compensation verdict, and the manual replacement ride.
 * The verdict, its amount, reasons and the German form text come from
 * abeona_compensation() in Postgres, so both clients say the same; only the
 * short labels live here, mirrored from app/abeona/src/lib/compensation.ts.
 * Change both together.
 */
object CompensationText {
    /** The card's headline: what the delay is worth, or what is missing. */
    fun headline(verdict: String, amount: String?): String = when (verdict) {
        "eligible" -> amount?.let { "$it to claim" } ?: "Compensation due"
        "below_minimum" -> amount?.let { "$it, under DB's €4 minimum" } ?: "Under DB's €4 minimum"
        "unknown_price" -> "Compensation due: add the fare"
        "unknown_arrival" -> "When did you arrive?"
        else -> "No compensation"
    }

    val STATUSES = listOf("open", "claimed", "paid", "rejected")

    fun status(status: String): String = when (status) {
        "claimed" -> "Claimed"
        "paid" -> "Paid"
        "rejected" -> "Rejected"
        else -> "Not claimed yet"
    }

    /** "Claim by 14 Jan 2027 (in 72 days)", for the deadline in days from today. */
    fun deadline(dayLabel: String, daysLeft: Int): String = when {
        daysLeft < 0 -> "The claim deadline was $dayLabel"
        daysLeft == 0 -> "Claim by today, $dayLabel"
        daysLeft == 1 -> "Claim by tomorrow, $dayLabel"
        else -> "Claim by $dayLabel (in $daysLeft days)"
    }

    /** Where the delay came from, in a few words. */
    fun source(source: String): String = when (source) {
        "final" -> "as recorded after arrival"
        "replacement" -> "with the trains you took instead"
        "live" -> "from the last live update"
        "planned" -> "no live data, so the timetable"
        else -> ""
    }

    /** Whether the verdict leaves something to claim with DB's form. */
    fun claimable(verdict: String): Boolean = verdict in setOf("eligible", "below_minimum", "unknown_price")
}

/**
 * A ride entered by hand: from the stop of the missed ride to the trip's
 * destination, arriving at [arrival] (ISO). Only what is known is filled in.
 */
fun manualReplacement(missed: Leg, destination: Leg, line: String, arrival: String): Leg {
    val name = line.trim()
    val product = Regex("^([A-Za-zÄÖÜäöü]{1,5})(?=\\s|\\d|$)").find(name)?.groupValues?.get(1)?.uppercase()
    return Leg(
        mode = LegMode.Train,
        product = product,
        lineName = name.ifEmpty { null },
        from = missed.from.copy(current = null, cancelled = false),
        to = destination.to.copy(planned = arrival, current = arrival, cancelled = false),
        realtime = false,
    )
}
