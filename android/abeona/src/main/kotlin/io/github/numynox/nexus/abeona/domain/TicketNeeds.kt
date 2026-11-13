package io.github.numynox.nexus.abeona.domain

import java.time.LocalDate

/**
 * What a trip's ticket needs (issue #62): how many travellers, and which
 * BahnCards it was booked with — not who. A ticket prints persons, not names
 * ("3 Personen … mit 1 BC25, 1 BC50"). app/abeona/src/lib/ticketNeeds.ts is
 * the same; both run the "ticket_summary" and "ticket_cards" cases in
 * intake.fixtures.json.
 */
object TicketNeeds {
    /** People on the ticket (null when unknown) and one discount per BahnCard, ascending. */
    data class Summary(val travellerCount: Int?, val bahncardDiscounts: List<Int>)

    /**
     * From what the ticket printed: the persons ([travellers], else one per
     * discount printed) and their BahnCards ([discounts], one per person,
     * null for a person without one).
     */
    fun summary(travellers: Int?, discounts: List<Int?>): Summary =
        Summary(travellers ?: discounts.size.takeIf { it > 0 }, discounts.filterNotNull().sorted())

    /** The few fields of a household BahnCard the ticket screen chooses by. */
    data class CardOnFile(val id: String, val kind: Int, val validFrom: LocalDate, val validTo: LocalDate, val hasFile: Boolean)

    /**
     * The household's BahnCards to show with a trip's ticket: one per
     * BahnCard the ticket was booked with (a BahnCard 100 fare: one per
     * traveller), each with a file and valid on [day]. The same kind first,
     * else the smallest that covers it; the largest discount first. A card
     * is used once. Returns the cards' ids.
     */
    fun cardsForTrip(cards: List<CardOnFile>, discounts: List<Int>, travellerCount: Int?, bahncard100Fare: Boolean, day: LocalDate): List<String> {
        val need = if (bahncard100Fare) List(maxOf(1, travellerCount ?: 1)) { 100 } else discounts.sortedDescending()
        val left = cards.filter { it.hasFile && !it.validFrom.isAfter(day) && !it.validTo.isBefore(day) }.toMutableList()
        val shown = mutableListOf<String>()
        for (d in need) {
            val cover = left.firstOrNull { it.kind == d } ?: left.filter { it.kind > d }.minByOrNull { it.kind } ?: continue
            shown += cover.id
            left.remove(cover)
        }
        return shown
    }
}
