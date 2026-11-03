package io.github.numynox.nexus.abeona.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.github.numynox.nexus.abeona.domain.applyRefresh
import io.github.numynox.nexus.abeona.domain.liveUpdates
import io.github.numynox.nexus.core.data.NexusJson
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * The Android counterpart of app/abeona/src/lib/data.ts: every query,
 * RPC, Edge Function call and Storage request Abeona makes, and the only code
 * that talks to Supabase. Everything is scoped to the caller's household
 * (decisions/2026-11-02-abeona-train-companion.md); RLS enforces it.
 */
class AbeonaRepository(private val supabase: SupabaseClient) {
    // ── Household ───────────────────────────────────────────────────────────

    private val householdLock = Mutex()
    private var household: Pair<String, String>? = null

    /**
     * The caller's household, created on first use. Asked once per signed-in
     * user and remembered; every household-scoped query goes through here.
     */
    suspend fun householdId(): String = householdLock.withLock {
        supabase.auth.awaitInitialization()
        val user = supabase.auth.currentUserOrNull()?.id ?: error("Not signed in.")
        household?.takeIf { it.first == user }?.second
            ?: NexusJson.decodeFromString<String>(supabase.postgrest.rpc("abeona_ensure_household").data)
                .also { household = user to it }
    }

    suspend fun members(): List<HouseholdMember> {
        householdId()
        return supabase.postgrest.rpc("abeona_list_household_members").decodeList()
    }

    /** Adds a Nexus user by email; their own trips come along if they had a household alone. */
    suspend fun invite(email: String) {
        householdId()
        supabase.postgrest.rpc("abeona_invite_to_household", buildJsonObject { put("p_email", email.trim()) })
    }

    /** Leaves the shared household; the trips stay with it, and a fresh one starts. */
    suspend fun leaveHousehold() {
        val fresh = NexusJson.decodeFromString<String>(supabase.postgrest.rpc("abeona_leave_household").data)
        householdLock.withLock {
            household = supabase.auth.currentUserOrNull()?.id?.let { it to fresh }
        }
    }

    // ── Travellers ──────────────────────────────────────────────────────────

    suspend fun travellers(): List<Traveller> {
        val household = householdId()
        return supabase.from("abeona_travellers").select {
            filter { eq("household_id", household) }
            order("created_at", Order.ASCENDING)
        }.decodeList()
    }

    suspend fun addTraveller(name: String) {
        val household = householdId()
        supabase.from("abeona_travellers").insert(buildJsonObject { put("household_id", household); put("name", name.trim()) })
    }

    suspend fun renameTraveller(id: String, name: String) {
        supabase.from("abeona_travellers").update(buildJsonObject { put("name", name.trim()) }) { filter { eq("id", id) } }
    }

    suspend fun deleteTraveller(id: String) {
        supabase.from("abeona_travellers").delete { filter { eq("id", id) } }
    }

    // ── Trips ───────────────────────────────────────────────────────────────

    private val tripColumns = Columns.raw("*,abeona_legs(*)")

    /** Trips not yet over (arrived less than two hours ago), soonest first. */
    suspend fun upcomingTrips(now: Instant = Instant.now()): List<Trip> {
        val household = householdId()
        return supabase.from("abeona_trips").select(tripColumns) {
            filter {
                eq("household_id", household)
                eq("status", "planned")
                gte("planned_arrival", now.minus(2, ChronoUnit.HOURS).toString())
            }
            order("planned_departure", Order.ASCENDING)
        }.decodeList<TripRow>().map { it.toTrip() }
    }

    suspend fun trip(id: String): Trip? {
        householdId()
        return supabase.from("abeona_trips").select(tripColumns) { filter { eq("id", id) } }
            .decodeList<TripRow>().firstOrNull()?.toTrip()
    }

    /** Saves a searched journey as a trip; the legs go in verbatim, as the contract gave them. */
    suspend fun saveTrip(
        journey: Journey,
        title: String?,
        fareType: FareType,
        trainBinding: Boolean,
        travelClass: Int,
        priceCents: Int?,
    ): String {
        householdId()
        val trip = buildJsonObject {
            put("title", title?.trim()?.takeIf { it.isNotEmpty() })
            put("source", "search")
            put("provider", journey.provider)
            put("refresh_token", journey.refreshToken)
            put("fare_type", fareType.wire)
            put("train_binding", trainBinding)
            put("travel_class", travelClass)
            put("price_cents", priceCents)
        }
        val params = buildJsonObject {
            put("p_trip", trip)
            put("p_legs", NexusJson.encodeToJsonElement(journey.legs))
        }
        return NexusJson.decodeFromString<String>(supabase.postgrest.rpc("abeona_save_trip", params).data)
    }

    suspend fun deleteTrip(trip: Trip) {
        supabase.from("abeona_trips").delete { filter { eq("id", trip.id) } }
        trip.ticketPath?.let { path -> runCatching { tickets.delete(path) } }
    }

    /**
     * Asks the provider for the trip's live values, writes them through
     * abeona_update_trip_live, and returns the trip as it now stands.
     */
    suspend fun refreshTrip(trip: Trip, now: Instant = Instant.now()): Trip {
        val body = buildJsonObject {
            put("action", "refresh")
            put("refresh_token", trip.refreshToken)
            put(
                "legs",
                NexusJson.encodeToJsonElement(
                    trip.legs.map { RefreshLegRef(it.tripRef, it.from.id, it.to.id, it.from.name, it.to.name) },
                ),
            )
        }
        val response = NexusJson.decodeFromString<RefreshResponse>(transit(body))
        val refreshed = trip.legs.indices.map { response.legs.getOrNull(it) }
        val updates = liveUpdates(refreshed)
        if (updates.isNotEmpty()) {
            supabase.postgrest.rpc(
                "abeona_update_trip_live",
                buildJsonObject {
                    put("p_trip_id", trip.id)
                    put("p_legs", NexusJson.encodeToJsonElement(updates))
                },
            )
        }
        return applyRefresh(trip, refreshed, now.toString())
    }

    /** From the start of [year] to the start of the next, in the phone's zone. */
    suspend fun statistics(year: Int, zone: ZoneId = ZoneId.systemDefault()): Statistics {
        householdId()
        val from = java.time.LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant()
        val to = java.time.LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant()
        return NexusJson.decodeFromString(
            supabase.postgrest.rpc(
                "abeona_statistics",
                buildJsonObject { put("p_from", from.toString()); put("p_to", to.toString()) },
            ).data,
        )
    }

    // ── Timetable (the abeona-transit Edge Function) ────────────────────────

    private suspend fun transit(body: JsonObject): String =
        supabase.functions.invoke(function = "abeona-transit", body = body).bodyAsText()

    suspend fun stations(query: String): List<Station> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return NexusJson.decodeFromString<StationsResponse>(
            transit(buildJsonObject { put("action", "locations"); put("query", q.take(100)) }),
        ).stations
    }

    suspend fun journeys(
        from: Station,
        to: Station,
        time: Instant?,
        arriveBy: Boolean,
        products: ProductFilter,
        cursor: String? = null,
    ): JourneysResponse = NexusJson.decodeFromString(
        transit(
            buildJsonObject {
                put("action", "journeys")
                put("from", from.id)
                put("to", to.id)
                time?.let { put("time", it.toString()) }
                put("arrive_by", arriveBy)
                put("products", products.wire)
                cursor?.let { put("cursor", it) }
            },
        ),
    )

    // ── Tickets (Storage bucket abeona-tickets) ──────────────────────────────

    private val tickets get() = supabase.storage.from("abeona-tickets")

    /**
     * Uploads a ticket to `<household_id>/<trip_id>/<file>` (the path rule
     * of decisions/2026-11-02-abeona-ticket-storage.md) and records it on
     * the trip. Returns the object path.
     */
    suspend fun uploadTicket(trip: Trip, fileName: String, mimeType: String, bytes: ByteArray): String {
        val path = "${trip.householdId}/${trip.id}/${safeFileName(fileName)}"
        tickets.upload(path, bytes) {
            upsert = true
            contentType = ContentType.parse(mimeType)
        }
        supabase.from("abeona_trips").update(buildJsonObject { put("ticket_path", path) }) { filter { eq("id", trip.id) } }
        trip.ticketPath?.takeIf { it != path }?.let { old -> runCatching { tickets.delete(old) } }
        return path
    }

    /** With the session, never a signed URL (decisions/2026-11-02-abeona-ticket-storage.md). */
    suspend fun downloadTicket(path: String): ByteArray = tickets.downloadAuthenticated(path)

    companion object {
        /** Letters, digits, dot, dash and underscore; Storage keys are picky, and so are people's file names. */
        fun safeFileName(name: String): String =
            name.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').take(80).ifEmpty { "ticket.pdf" }
    }
}
