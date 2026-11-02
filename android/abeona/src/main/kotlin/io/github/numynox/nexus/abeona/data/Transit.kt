package io.github.numynox.nexus.abeona.data

import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The abeona-transit Edge Function's JSON, by hand. This mirrors
// supabase/functions/abeona-transit/contract.ts and the web's
// app/abeona/src/lib/transit.ts: change all three together. Times are
// ISO-8601 instants as strings, parsed where they are used.

@Serializable
data class Station(
    val id: String,
    val name: String,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val country: String? = null,
)

/** A stop as seen from one leg: where it starts, ends, or passes. */
@Serializable
data class StopTime(
    val id: String? = null,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    /** Scheduled time here (departure at a leg's origin, arrival otherwise). */
    val planned: String? = null,
    /** Current estimate; null when the provider has no realtime data for it. */
    val current: String? = null,
    @SerialName("planned_platform") val plannedPlatform: String? = null,
    val platform: String? = null,
    val cancelled: Boolean = false,
) {
    val plannedAt: Instant? get() = planned.toInstantOrNull()
    val currentAt: Instant? get() = current.toInstantOrNull()

    /** What the passenger should expect: the estimate when there is one. */
    val expectedAt: Instant? get() = currentAt ?: plannedAt

    /** The platform to go to: the current one, else the planned one. */
    val shownPlatform: String? get() = platform ?: plannedPlatform
}

/** An intermediate stop of a leg. */
@Serializable
data class Stopover(
    val id: String? = null,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("planned_arrival") val plannedArrival: String? = null,
    val arrival: String? = null,
    @SerialName("planned_departure") val plannedDeparture: String? = null,
    val departure: String? = null,
    val cancelled: Boolean = false,
)

/** What a leg is: a train, a bus, the walk between two of them, or something else. */
@Serializable
enum class LegMode {
    @SerialName("train") Train,
    @SerialName("bus") Bus,
    @SerialName("walk") Walk,
    @SerialName("other") Other,
}

@Serializable
data class Leg(
    val mode: LegMode = LegMode.Train,
    /** ICE, IC, EC, RJX, RE, RB, S, … — null for walks or when unknown. */
    val product: String? = null,
    /** What the board shows, e.g. "ICE 932" or "RE 4". */
    @SerialName("line_name") val lineName: String? = null,
    /** The provider's trip id, enough to refresh this leg on its own. */
    @SerialName("trip_ref") val tripRef: String? = null,
    val headsign: String? = null,
    val operator: String? = null,
    val from: StopTime,
    val to: StopTime,
    val stopovers: List<Stopover> = emptyList(),
    /** [[lon, lat], …], simplified to a few hundred points at most. */
    val polyline: List<List<Double>>? = null,
    val cancelled: Boolean = false,
    val realtime: Boolean = false,
) {
    val isWalk: Boolean get() = mode == LegMode.Walk

    /** "ICE 597", else the product, else the mode. */
    val label: String
        get() = lineName?.takeIf { it.isNotBlank() } ?: product ?: when (mode) {
            LegMode.Bus -> "Bus"
            LegMode.Walk -> "Walk"
            else -> "Train"
        }
}

@Serializable
data class Journey(
    val provider: String = "transitous",
    /** Hands the whole journey back to the provider for a refresh. */
    @SerialName("refresh_token") val refreshToken: String? = null,
    val legs: List<Leg> = emptyList(),
)

@Serializable
data class StationsResponse(val stations: List<Station> = emptyList())

@Serializable
data class JourneysResponse(
    val journeys: List<Journey> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("previous_cursor") val previousCursor: String? = null,
)

/** Saved leg references for a refresh, used when the token fails. */
@Serializable
data class RefreshLegRef(
    @SerialName("trip_ref") val tripRef: String?,
    @SerialName("from_id") val fromId: String?,
    @SerialName("to_id") val toId: String?,
    @SerialName("from_name") val fromName: String,
    @SerialName("to_name") val toName: String,
)

/** [legs] is index-aligned with the saved legs; null means no update for that one. */
@Serializable
data class RefreshResponse(
    val journey: Journey? = null,
    val legs: List<Leg?> = emptyList(),
)

/** "all" trains, or "regional" (no IC/ICE/EC). */
enum class ProductFilter(val wire: String) { All("all"), Regional("regional") }

internal fun String?.toInstantOrNull(): Instant? = this?.let {
    runCatching { OffsetDateTime.parse(it).toInstant() }.getOrElse { _ -> runCatching { Instant.parse(it) }.getOrNull() }
}
