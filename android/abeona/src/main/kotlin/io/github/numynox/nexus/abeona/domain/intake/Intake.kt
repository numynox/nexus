package io.github.numynox.nexus.abeona.domain.intake

import io.github.numynox.nexus.abeona.data.FareType

// Intake end to end, without the network; app/abeona/src/lib/intake/intake.ts.

/** What the save sheet starts with; every field stays editable. */
data class Prefill(
    val fareType: FareType? = null,
    val trainBinding: Boolean? = null,
    val travelClass: Int? = null,
    /** One discount per person the ticket prints ([ParsedTicket.discounts]); empty for a share. */
    val discounts: List<Int?> = emptyList(),
    /** How many people the ticket is for: a hint next to "Who travels". */
    val travellers: Int? = null,
    val priceCents: Int? = null,
    /** Booking: the printed price is the whole booking; the sheet asks before saving it. */
    val priceScope: PriceScope = PriceScope.Trip,
)

enum class IntakeSource(val wire: String) { Share("share"), Pdf("pdf") }

data class IntakeDraft(
    val source: IntakeSource,
    val parsed: Parsed,
    val match: MatchResult,
    val prefill: Prefill,
    /** The ticket PDF that was read, attached to the trip once it is saved. */
    val ticket: TicketFile? = null,
)

/** A ticket's bytes as shared or picked, kept until the trip exists. */
class TicketFile(val name: String, val mimeType: String, val bytes: ByteArray)

/** A pasted or shared text: a DB Navigator share, or a ticket's text. Null when it is neither. */
fun parseText(text: String): Parsed? = when {
    isShareText(text) && !isDbTicket(text) -> parseShareText(text)
    isDbTicket(text) -> parseDbTicket(text)
    else -> parseShareText(text)
}

fun prefillOf(parsed: Parsed): Prefill = when (parsed) {
    is ParsedShare -> Prefill()
    is ParsedTicket -> Prefill(
        fareType = parsed.fareType,
        trainBinding = parsed.trainBinding,
        travelClass = parsed.travelClass,
        discounts = parsed.discounts,
        travellers = parsed.travellers,
        priceCents = parsed.priceCents,
        priceScope = parsed.priceScope,
    )
}

/** Half a booking's price, to the cent: offered by the sheet, never applied by itself. */
fun halfOf(cents: Int): Int = Math.round(cents / 2.0).toInt()

suspend fun intake(parsed: Parsed, source: IntakeSource, port: TransitPort, ticket: TicketFile? = null): IntakeDraft {
    require(parsed.legs.isNotEmpty()) { "No rides were found in it." }
    return IntakeDraft(source, parsed, matchConnection(parsed.legs, port), prefillOf(parsed), ticket)
}

/** One English sentence on how well the timetable knows the rides; the web says the same. */
fun matchSummary(match: MatchResult): String = when (match.status) {
    MatchStatus.Full -> "Found in the timetable: Abeona follows its delays and platforms."
    MatchStatus.Partial -> "${match.matched} of ${match.total} rides found in the timetable. The others are saved as printed, without live updates."
    MatchStatus.None -> "Not found in the timetable — it may be too far ahead, in the past, or abroad. Saved as printed, without live updates."
}
