package io.github.numynox.nexus.abeona.data

import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The ticket, as far as the app knows it (glossary: Fare type). */
@Serializable
enum class FareType(val label: String) {
    @SerialName("flex") Flex("Flex"),
    @SerialName("saver") Saver("Saver"),
    @SerialName("regional") Regional("Regional"),
    @SerialName("deutschlandticket") Deutschlandticket("Deutschlandticket"),
    @SerialName("bahncard100") BahnCard100("BahnCard 100"),
    @SerialName("other") Other("Other");

    val wire: String get() = when (this) {
        Flex -> "flex"
        Saver -> "saver"
        Regional -> "regional"
        Deutschlandticket -> "deutschlandticket"
        BahnCard100 -> "bahncard100"
        Other -> "other"
    }
}

/**
 * A saved trip with its legs in order — what every screen, the widget, the
 * live companion and the offline pack work with. Legs use the contract's
 * [Leg] shape, so search results and saved trips go through the same rules.
 */
@Serializable
data class Trip(
    val id: String,
    val householdId: String,
    val title: String? = null,
    val status: String = "planned",
    val originName: String,
    val destinationName: String,
    val plannedDeparture: String,
    val plannedArrival: String,
    val fareType: FareType = FareType.Other,
    val trainBinding: Boolean = false,
    val travelClass: Int = 2,
    val priceCents: Int? = null,
    val ticketPath: String? = null,
    val refreshToken: String? = null,
    val refreshedAt: String? = null,
    val legs: List<Leg> = emptyList(),
    /** The BahnCard discount the fare was bought with (25, 50, 100); a BahnCard 100 is also a [FareType]. */
    val bahncardDiscount: Int? = null,
    /** Set once the snapshot job froze every ride's actual values. */
    val finalizedAt: String? = null,
    /** The ride (index into [legs]) that was missed or cancelled under the traveller. */
    val missedLegIdx: Int? = null,
    /** The rides actually taken instead, in the contract's shape. */
    val replacementLegs: List<Leg>? = null,
    /** The delay was caused by extreme weather or a person on the track. */
    val forceMajeure: Boolean = false,
) {
    val departureAt: Instant get() = plannedDeparture.toInstantOrNull() ?: Instant.EPOCH
    val arrivalAt: Instant get() = plannedArrival.toInstantOrNull() ?: Instant.EPOCH

    /** The first ride's expected departure, falling back to the plan. */
    val expectedDeparture: Instant
        get() = legs.firstOrNull { !it.isWalk }?.from?.expectedAt ?: departureAt

    /** The last ride's expected arrival, falling back to the plan. */
    val expectedArrival: Instant
        get() = legs.lastOrNull { !it.isWalk }?.to?.expectedAt ?: arrivalAt

    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: "$originName → $destinationName"
    val ticketFileName: String? get() = ticketPath?.substringAfterLast('/')
}

/** A row of abeona_trips with its legs embedded, as PostgREST returns it. */
@Serializable
internal data class TripRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    val title: String? = null,
    val status: String = "planned",
    @SerialName("origin_name") val originName: String,
    @SerialName("destination_name") val destinationName: String,
    @SerialName("planned_departure") val plannedDeparture: String,
    @SerialName("planned_arrival") val plannedArrival: String,
    @SerialName("fare_type") val fareType: FareType = FareType.Other,
    @SerialName("train_binding") val trainBinding: Boolean = false,
    @SerialName("travel_class") val travelClass: Int = 2,
    @SerialName("price_cents") val priceCents: Int? = null,
    @SerialName("ticket_path") val ticketPath: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("refreshed_at") val refreshedAt: String? = null,
    @SerialName("bahncard_discount") val bahncardDiscount: Int? = null,
    @SerialName("finalized_at") val finalizedAt: String? = null,
    @SerialName("missed_leg_idx") val missedLegIdx: Int? = null,
    @SerialName("replacement_legs") val replacementLegs: List<Leg>? = null,
    @SerialName("force_majeure") val forceMajeure: Boolean = false,
    @SerialName("abeona_legs") val legs: List<LegRow> = emptyList(),
) {
    fun toTrip() = Trip(
        id = id,
        householdId = householdId,
        title = title,
        status = status,
        originName = originName,
        destinationName = destinationName,
        plannedDeparture = plannedDeparture,
        plannedArrival = plannedArrival,
        fareType = fareType,
        trainBinding = trainBinding,
        travelClass = travelClass,
        priceCents = priceCents,
        ticketPath = ticketPath,
        refreshToken = refreshToken,
        refreshedAt = refreshedAt,
        legs = legs.sortedBy { it.idx }.map { it.toLeg() },
        bahncardDiscount = bahncardDiscount,
        finalizedAt = finalizedAt,
        missedLegIdx = missedLegIdx,
        replacementLegs = replacementLegs,
        forceMajeure = forceMajeure,
    )
}

/** A row of abeona_legs: planned and current values in separate columns. */
@Serializable
internal data class LegRow(
    val idx: Int = 0,
    val mode: LegMode = LegMode.Train,
    val product: String? = null,
    @SerialName("line_name") val lineName: String? = null,
    @SerialName("trip_ref") val tripRef: String? = null,
    val headsign: String? = null,
    val operator: String? = null,
    @SerialName("from_id") val fromId: String? = null,
    @SerialName("from_name") val fromName: String,
    @SerialName("from_lat") val fromLat: Double? = null,
    @SerialName("from_lon") val fromLon: Double? = null,
    @SerialName("to_id") val toId: String? = null,
    @SerialName("to_name") val toName: String,
    @SerialName("to_lat") val toLat: Double? = null,
    @SerialName("to_lon") val toLon: Double? = null,
    @SerialName("planned_departure") val plannedDeparture: String,
    val departure: String? = null,
    @SerialName("planned_arrival") val plannedArrival: String,
    val arrival: String? = null,
    @SerialName("planned_departure_platform") val plannedDeparturePlatform: String? = null,
    @SerialName("departure_platform") val departurePlatform: String? = null,
    @SerialName("planned_arrival_platform") val plannedArrivalPlatform: String? = null,
    @SerialName("arrival_platform") val arrivalPlatform: String? = null,
    val cancelled: Boolean = false,
    val realtime: Boolean = false,
    val stopovers: List<Stopover> = emptyList(),
    val polyline: List<List<Double>>? = null,
) {
    fun toLeg() = Leg(
        mode = mode,
        product = product,
        lineName = lineName,
        tripRef = tripRef,
        headsign = headsign,
        operator = operator,
        from = StopTime(fromId, fromName, fromLat, fromLon, plannedDeparture, departure, plannedDeparturePlatform, departurePlatform),
        to = StopTime(toId, toName, toLat, toLon, plannedArrival, arrival, plannedArrivalPlatform, arrivalPlatform),
        stopovers = stopovers,
        polyline = polyline,
        cancelled = cancelled,
        realtime = realtime,
    )
}

/** Someone who rides: a member, or a child or partner without an account (glossary: Traveller). */
@Serializable
data class Traveller(
    val id: String,
    @SerialName("household_id") val householdId: String,
    val name: String,
    @SerialName("user_id") val userId: String? = null,
)

/** What the save sheet knows about a new trip's ticket and who travels on it. */
data class SaveTripInput(
    val title: String?,
    val fareType: FareType,
    val trainBinding: Boolean,
    val travelClass: Int,
    val priceCents: Int?,
    /** 25, 50 or null; a BahnCard 100 is a [FareType]. */
    val bahncardDiscount: Int? = null,
    /** Who travels; null leaves it to the database, which puts the caller's own traveller on. */
    val travellerIds: List<String>? = null,
    /**
     * A journey that already arrived: saved as source "import", final, never
     * refreshed; [arrivalDelayMinutes] is the delay typed by hand.
     */
    val past: Boolean = false,
    val arrivalDelayMinutes: Int? = null,
    /** "share" or "pdf" when it came through intake; the search otherwise. */
    val source: String? = null,
)

/**
 * A BahnCard (glossary: BahnCard). It belongs to a traveller, and through
 * them to a household; a merge on invite carries it along. Dates are the
 * first and last day it is valid.
 */
@Serializable
data class BahnCard(
    val id: String,
    @SerialName("traveller_id") val travellerId: String,
    val kind: Int,
    @SerialName("travel_class") val travelClass: Int = 2,
    @SerialName("valid_from") val validFrom: String,
    @SerialName("valid_to") val validTo: String,
    @SerialName("card_number") val cardNumber: String? = null,
    /** Renews unless cancelled [noticeWeeks] weeks before [validTo]. */
    val subscription: Boolean = true,
    @SerialName("notice_weeks") val noticeWeeks: Int = 4,
    @SerialName("file_path") val filePath: String? = null,
) {
    val from: LocalDate get() = LocalDate.parse(validFrom)
    val to: LocalDate get() = LocalDate.parse(validTo)
    /** The last day to cancel a subscription. */
    val deadline: LocalDate get() = to.minusWeeks(noticeWeeks.toLong())
    val label: String get() = "BahnCard $kind · ${if (travelClass == 1) "1st" else "2nd"} class"
    val fileName: String? get() = filePath?.substringAfterLast('/')
}

/** What the BahnCard sheet edits: a card without its id and file. */
data class BahnCardInput(
    val travellerId: String,
    val kind: Int,
    val travelClass: Int,
    val validFrom: LocalDate,
    val validTo: LocalDate,
    val cardNumber: String?,
    val subscription: Boolean,
    val noticeWeeks: Int,
)

/** A row of abeona_bahncard_deadlines(): a subscription to cancel soon, or whose deadline has passed. */
@Serializable
data class BahnCardDeadline(
    @SerialName("card_id") val cardId: String,
    @SerialName("traveller_id") val travellerId: String,
    @SerialName("traveller_name") val travellerName: String,
    val kind: Int,
    @SerialName("travel_class") val travelClass: Int = 2,
    @SerialName("valid_to") val validTo: String,
    @SerialName("notice_weeks") val noticeWeeks: Int = 4,
    val deadline: String,
    /** Negative once the deadline has passed. */
    @SerialName("days_left") val daysLeft: Int,
)

/** A row of abeona_trip_warnings(); [message] is shown as is, the same on the web. */
@Serializable
data class TripWarning(
    val kind: String,
    @SerialName("traveller_id") val travellerId: String? = null,
    @SerialName("traveller_name") val travellerName: String? = null,
    @SerialName("card_id") val cardId: String? = null,
    val message: String,
)

@Serializable
internal data class TripTravellerRow(@SerialName("traveller_id") val travellerId: String)

@Serializable
data class HouseholdMember(
    @SerialName("user_id") val userId: String,
    val email: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val role: String = "member",
) {
    val displayName: String get() = fullName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@') ?: "Member"
}

@Serializable
data class RouteCount(val from: String, val to: String, val count: Int)

@Serializable
data class StationCount(val name: String, val count: Int)

@Serializable
data class MonthStat(val month: String, val trips: Int = 0, val km: Double = 0.0)

/** What abeona_statistics(p_from, p_to) returns: trips that arrived in the window. */
@Serializable
data class Statistics(
    val trips: Int = 0,
    val km: Double = 0.0,
    @SerialName("minutes_on_train") val minutesOnTrain: Double = 0.0,
    val rides: Int = 0,
    @SerialName("delay_minutes") val delayMinutes: Double = 0.0,
    @SerialName("punctual_share") val punctualShare: Double? = null,
    @SerialName("longest_km") val longestKm: Double = 0.0,
    @SerialName("max_delay_minutes") val maxDelayMinutes: Double = 0.0,
    val stations: Int = 0,
    @SerialName("top_route") val topRoute: RouteCount? = null,
    @SerialName("top_station") val topStation: StationCount? = null,
    val products: Map<String, Int> = emptyMap(),
    @SerialName("by_month") val byMonth: List<MonthStat> = emptyList(),
    /** CO₂ the rides saved against the car, one traveller, kg; approximate (abeona_co2_factors). */
    @SerialName("co2_saved_kg") val co2SavedKg: Double = 0.0,
    @SerialName("co2_train_kg") val co2TrainKg: Double = 0.0,
    @SerialName("co2_car_kg") val co2CarKg: Double = 0.0,
    @SerialName("co2_factors") val co2Factors: Co2Factors? = null,
    /** ISO codes of the countries ridden from or to, most often first. */
    val countries: List<String> = emptyList(),
    @SerialName("longest_trip") val longestTrip: LongestTrip? = null,
)

/** Where the CO₂ figures come from (abeona_co2_factors), g CO₂e per passenger-km. */
@Serializable
data class Co2Factors(val car: Double = 0.0, val source: String? = null)

@Serializable
data class LongestTrip(
    @SerialName("trip_id") val tripId: String,
    val from: String,
    val to: String,
    val km: Double = 0.0,
    /** yyyy-mm-dd */
    val date: String? = null,
)

/** A station on the travel map. */
@Serializable
data class MapStop(val name: String, val lat: Double, val lon: Double)

/** One line of the map of everything travelled (abeona_travel_map): a pair of stations, either way. */
@Serializable
data class TravelMapLine(
    val from: MapStop,
    val to: MapStop,
    val product: String? = null,
    val count: Int = 1,
    /** [[lon, lat], …], thinned. */
    val polyline: List<List<Double>> = emptyList(),
) {
    /** As a leg, so the trip map draws it. */
    fun toLeg(): Leg = Leg(
        mode = LegMode.Train,
        product = product,
        from = StopTime(name = from.name, lat = from.lat, lon = from.lon),
        to = StopTime(name = to.name, lat = to.lat, lon = to.lon),
        polyline = polyline,
    )
}

/** abeona_share_links: the token is the whole credential; revoking deletes the row. */
@Serializable
data class ShareLink(
    val token: String,
    @SerialName("trip_id") val tripId: String,
    /** "ics" or "status". */
    val kind: String,
    @SerialName("expires_at") val expiresAt: String,
)

/** A trip abeona-import read from Träwelling, not saved yet. */
@Serializable
data class ImportedTrip(
    @SerialName("external_id") val externalId: String,
    val origin: String,
    val destination: String,
    @SerialName("planned_departure") val plannedDeparture: String,
    @SerialName("planned_arrival") val plannedArrival: String,
    @SerialName("arrival_delay_minutes") val arrivalDelayMinutes: Int? = null,
    val legs: List<kotlinx.serialization.json.JsonObject> = emptyList(),
    /** A trip with the same origin and departure is saved already. */
    val duplicate: Boolean = false,
) {
    /** "ICE 25, RE 10" */
    val lines: String get() = legs.joinToString(", ") {
        (it["line_name"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNullSafe()
            ?: (it["product"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNullSafe() ?: "?"
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

@Serializable
data class ImportResult(
    val trips: List<ImportedTrip> = emptyList(),
    val statuses: Int = 0,
    val skipped: Int = 0,
    val truncated: Boolean = false,
)

/** abeona_compensation(trip_id); [reasons] and [hints] are English sentences, shown as is, the same on the web. */
@Serializable
data class Compensation(
    @SerialName("trip_id") val tripId: String,
    /** eligible, not_eligible, below_minimum, unknown_price, unknown_arrival. */
    val verdict: String,
    @SerialName("amount_cents") val amountCents: Int? = null,
    val percent: Double? = null,
    @SerialName("delay_minutes") val delayMinutes: Int? = null,
    @SerialName("delay_source") val delaySource: String = "planned",
    @SerialName("planned_arrival") val plannedArrival: String? = null,
    @SerialName("actual_arrival") val actualArrival: String? = null,
    @SerialName("threshold_minutes") val thresholdMinutes: Int? = null,
    val reasons: List<String> = emptyList(),
    val hints: List<String> = emptyList(),
    /** yyyy-mm-dd, the last day to claim. */
    val deadline: String,
    @SerialName("deadline_passed") val deadlinePassed: Boolean = false,
    val poolable: Boolean = false,
    /** German text for DB's passenger-rights form; null when there is nothing to claim. */
    @SerialName("summary_de") val summaryDe: String? = null,
    @SerialName("claim_url") val claimUrl: String = "https://www.bahn.de/service/informationen-buchung/fahrgastrechte",
)

/** abeona_claims: one per trip, access follows the trip. Status is open, claimed, paid or rejected. */
@Serializable
data class Claim(
    val id: String,
    @SerialName("trip_id") val tripId: String,
    val status: String = "open",
    @SerialName("amount_cents") val amountCents: Int? = null,
    @SerialName("claimed_at") val claimedAt: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    val note: String? = null,
)

/** A row of the claims overview. */
@Serializable
data class ClaimEntry(
    @SerialName("trip_id") val tripId: String,
    val title: String? = null,
    @SerialName("origin_name") val originName: String,
    @SerialName("destination_name") val destinationName: String,
    @SerialName("planned_departure") val plannedDeparture: String,
    @SerialName("delay_minutes") val delayMinutes: Int? = null,
    val verdict: String,
    @SerialName("amount_cents") val amountCents: Int? = null,
    val deadline: String,
    @SerialName("days_left") val daysLeft: Int,
    val status: String = "open",
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: "$originName → $destinationName"
}

/** abeona_claims_overview(year). */
@Serializable
data class ClaimsOverview(
    @SerialName("to_claim") val toClaim: List<ClaimEntry> = emptyList(),
    val waiting: List<ClaimEntry> = emptyList(),
    @SerialName("earned_cents") val earnedCents: Int = 0,
    val paid: Int = 0,
)
