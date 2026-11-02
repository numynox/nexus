package io.github.numynox.nexus.abeona.domain

import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.Stopover
import io.github.numynox.nexus.abeona.data.Trip
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One leg's live values, as abeona_update_trip_live takes them. */
@Serializable
data class LiveUpdate(
    val idx: Int,
    val departure: String?,
    val arrival: String?,
    @SerialName("departure_platform") val departurePlatform: String?,
    @SerialName("arrival_platform") val arrivalPlatform: String?,
    val cancelled: Boolean,
    val realtime: Boolean,
    val stopovers: List<Stopover>,
)

/**
 * What to write for an Edge Function refresh: [refreshed] is index-aligned
 * with the trip's legs, null where the provider had nothing for a leg.
 */
fun liveUpdates(refreshed: List<Leg?>): List<LiveUpdate> =
    refreshed.mapIndexedNotNull { idx, leg ->
        leg?.let {
            LiveUpdate(
                idx = idx,
                departure = it.from.current,
                arrival = it.to.current,
                departurePlatform = it.from.platform,
                arrivalPlatform = it.to.platform,
                cancelled = it.cancelled,
                realtime = it.realtime,
                stopovers = it.stopovers,
            )
        }
    }

/**
 * The trip as the database holds it after [liveUpdates] were written: current
 * times replaced, platforms kept when the refresh has none (the RPC's
 * coalesce), planned values and the route untouched.
 */
fun applyRefresh(trip: Trip, refreshed: List<Leg?>, refreshedAt: String): Trip =
    trip.copy(
        refreshedAt = refreshedAt,
        legs = trip.legs.mapIndexed { idx, saved ->
            val fresh = refreshed.getOrNull(idx) ?: return@mapIndexed saved
            saved.copy(
                from = saved.from.copy(current = fresh.from.current, platform = fresh.from.platform ?: saved.from.platform),
                to = saved.to.copy(current = fresh.to.current, platform = fresh.to.platform ?: saved.to.platform),
                cancelled = fresh.cancelled,
                realtime = fresh.realtime,
                stopovers = fresh.stopovers,
            )
        },
    )
