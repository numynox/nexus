package io.github.numynox.nexus.abeona.domain.intake

// The coach and seats of a ride's reservation, as the ticket prints them, and
// how both clients write them. The Kotlin twin of
// app/abeona/src/lib/intake/seat.ts; both run the "seats" cases of
// intake.fixtures.json.

/** A ride's reserved place: the coach and the seats in it, each as printed; either may be unknown. */
data class Seat(val coach: String? = null, val seats: String? = null)

/** One seat number as printed: "66", "10C". */
private const val ONE = """\d{1,3}[A-Z]?"""

/** DB: "Wg. 24, Pl. 66", "Wg. 23, Pl. 71 73", "Wg. 7, Pl. 45 - 48"; the seats only after their coach. */
private val DbSeat = Regex("""\bWg\.\s*([0-9A-Za-z]{1,8})(?:\s*,\s*Pl\.\s*($ONE(?:\s*-\s*$ONE|\s+$ONE)*))?""")

/** Trenitalia, as its parser writes it: "Coach 7, seats 10C, 10D", "Coach 3, seat 5A", "Coach 3". */
private val TrenitaliaCoach = Regex("""\bCoach\s+([0-9A-Za-z]{1,8})\b""")
private val TrenitaliaSeats = Regex("""\bseats?\s+($ONE(?:\s*,\s*$ONE)*)""", RegexOption.IGNORE_CASE)

/**
 * The coach and seats of [reservation], a ride's reservation note: DB's
 * "Wg. 24, Pl. 66" or Trenitalia's "Coach 7, seats 10C, 10D". Seats keep the
 * ticket's own numbers, a space between several and a dash for a range
 * ("45 - 48" → "45-48"). Never the "Res.Nr." digits. Nothing found: both null.
 */
fun seatOf(reservation: String?): Seat {
    val text = reservation?.takeIf { it.isNotBlank() } ?: return Seat()
    DbSeat.find(text)?.let { m ->
        val seats = m.groupValues[2].takeIf { it.isNotBlank() }
            ?.replace(Regex("""\s*-\s*"""), "-")
            ?.replace(Regex("""\s+"""), " ")
        return Seat(m.groupValues[1], seats)
    }
    TrenitaliaCoach.find(text)?.let { coach ->
        val seats = TrenitaliaSeats.find(text, coach.range.last + 1)?.groupValues?.get(1)
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(" ")
        return Seat(coach.groupValues[1], seats?.ifEmpty { null })
    }
    return Seat()
}

/**
 * How a ride's place reads, the same on both clients: "Coach 24 · Seat 66",
 * "Coach 23 · Seats 71, 73", "Coach 7 · Seats 45–48" (an en dash for a
 * range), "Coach 24" or "Seat 66" alone; null when neither is known.
 */
fun seatText(coach: String?, seats: String?): String? {
    val c = coach?.trim()?.takeIf { it.isNotEmpty() }
    val s = seats?.trim()?.takeIf { it.isNotEmpty() }
    val place = s?.let {
        val several = it.contains(' ') || it.contains('-')
        val shown = it.split(Regex("""\s+""")).joinToString(", ") { part -> part.replace('-', '–') }
        "${if (several) "Seats" else "Seat"} $shown"
    }
    return listOfNotNull(c?.let { "Coach $it" }, place).joinToString(" · ").ifEmpty { null }
}
