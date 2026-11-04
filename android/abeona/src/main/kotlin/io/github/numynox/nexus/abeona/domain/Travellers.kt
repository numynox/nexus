package io.github.numynox.nexus.abeona.domain

/**
 * Who on a ticket holds which BahnCard. A ticket prints persons, not names
 * ("3 Personen … mit 1 BC25, 1 BC50"), so the save sheet hands its discounts
 * to the travellers the user picks, one at a time, and the user can change
 * each. app/abeona/src/lib/travellers.ts is the same; both run the
 * "discounts" cases in intake.fixtures.json.
 */
object Travellers {
    /**
     * The discount for the next traveller on the trip: one of [ticket]'s not
     * yet [given] to someone. [held] are the kinds of the BahnCards that
     * traveller holds on the day. In order: the one their own card matches;
     * none, if they hold no card and the ticket has a person without one; the
     * first left, as printed; none, once everybody is given away.
     */
    fun nextDiscount(ticket: List<Int?>, given: List<Int?>, held: List<Int>): Int? {
        val left = ticket.toMutableList()
        given.forEach { left.remove(it) }
        left.firstOrNull { it != null && it in held }?.let { return it }
        if (held.isEmpty() && null in left) return null
        return left.firstOrNull { it != null }
    }

    /** From a search: the BahnCard the traveller holds, since that is usually what they bought with. */
    fun fromCards(held: List<Int>): Int? = when {
        50 in held -> 50
        25 in held -> 25
        else -> null
    }
}
