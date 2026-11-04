package io.github.numynox.nexus.abeona.domain.intake

import io.github.numynox.nexus.abeona.data.FareType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// What intake reads out of a DB Navigator share text or a DB ticket's text
// layer, before anything is looked up. Parsing happens on the phone only,
// never in an Edge Function (decisions/2026-11-04-abeona-intake.md).
//
// This mirrors app/abeona/src/lib/intake/parsed.ts; both run the cases in
// intake.fixtures.json, so the field names are the JSON's. Times are local
// wall-clock times as printed ("06:29"), dates ISO ("2025-11-14").

/** One ride as printed: a share text's block, or two rows of a ticket's stop table. */
@Serializable
data class ParsedLeg(
    /** As printed, without the bracketed number: "RE19", "ICE 728". */
    val line: String,
    /** The letters of the line: "RE", "ICE"; null when it has none. */
    val product: String? = null,
    /** The number DB prints in brackets after a regional line: "RE19 (4901)" → "4901". */
    val number: String? = null,
    /** "Nach …" in a share text. */
    val headsign: String? = null,
    val from: String,
    val to: String,
    /** Local date of the departure. */
    val date: String,
    val departure: String,
    /** The next day when the ride runs past midnight. */
    @SerialName("arrival_date") val arrivalDate: String,
    val arrival: String,
    /** "Gleis 4 C-F" → "4". */
    @SerialName("from_platform") val fromPlatform: String? = null,
    @SerialName("to_platform") val toPlatform: String? = null,
    val reservation: String? = null,
)

sealed interface Parsed {
    val legs: List<ParsedLeg>
}

/** A DB Navigator "Teilen" text. */
@Serializable
data class ParsedShare(
    val kind: String = "share",
    val origin: String,
    val destination: String,
    val date: String,
    override val legs: List<ParsedLeg>,
    val link: String? = null,
    /** Kept as a reference only: resolving it needs bahn.de, which refuses our servers. */
    val vbid: String? = null,
) : Parsed

/** Whose price a ticket prints: "booking" is one direction of a return booking, the user confirms. */
@Serializable
enum class PriceScope {
    @SerialName("trip") Trip,
    @SerialName("booking") Booking,
}

/** A DB Online-Ticket (the PDF's text layer). */
@Serializable
data class ParsedTicket(
    val kind: String = "ticket",
    val operator: String = "db",
    @SerialName("fare_name") val fareName: String? = null,
    @SerialName("fare_type") val fareType: FareType,
    /** "outward" or "return" for a half of a return booking; null for a single. */
    val direction: String? = null,
    @SerialName("travel_class") val travelClass: Int? = null,
    val travellers: Int? = null,
    /**
     * One BahnCard discount per person the ticket prints, null for one
     * without: "3 Personen … mit 1 BC25, 1 BC50" → [25, 50, null]. Which
     * traveller holds which the save sheet works out (domain/Travellers.kt).
     */
    val discounts: List<Int?> = emptyList(),
    @SerialName("train_binding") val trainBinding: Boolean,
    @SerialName("booked_lines") val bookedLines: List<String>,
    val origin: String? = null,
    val destination: String? = null,
    val date: String? = null,
    override val legs: List<ParsedLeg>,
    @SerialName("price_cents") val priceCents: Int? = null,
    @SerialName("price_scope") val priceScope: PriceScope,
    val reservation: String? = null,
) : Parsed
