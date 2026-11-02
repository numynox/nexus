package io.github.numynox.nexus.abeona.data

import java.time.Instant
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
)
