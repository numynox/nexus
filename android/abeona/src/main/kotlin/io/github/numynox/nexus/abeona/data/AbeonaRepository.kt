package io.github.numynox.nexus.abeona.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.github.numynox.nexus.abeona.domain.AlternativesOrigin
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    suspend fun saveTrip(journey: Journey, input: SaveTripInput): String {
        householdId()
        val trip = buildJsonObject {
            put("title", input.title?.trim()?.takeIf { it.isNotEmpty() })
            put("source", if (input.past) "import" else "search")
            if (input.past) input.arrivalDelayMinutes?.let { put("arrival_delay_minutes", it) }
            put("provider", journey.provider)
            put("refresh_token", journey.refreshToken)
            put("fare_type", input.fareType.wire)
            put("train_binding", input.trainBinding)
            put("travel_class", input.travelClass)
            put("price_cents", input.priceCents)
            put("bahncard_discount", input.bahncardDiscount)
            input.travellerIds?.let { ids -> put("traveller_ids", JsonArray(ids.map(::JsonPrimitive))) }
        }
        val params = buildJsonObject {
            put("p_trip", trip)
            put("p_legs", NexusJson.encodeToJsonElement(journey.legs))
        }
        return NexusJson.decodeFromString<String>(supabase.postgrest.rpc("abeona_save_trip", params).data)
    }

    /** The BahnCard discount of the trip's fare; null for none. */
    suspend fun setBahncardDiscount(tripId: String, discount: Int?) {
        supabase.from("abeona_trips").update(buildJsonObject { put("bahncard_discount", discount) }) { filter { eq("id", tripId) } }
    }

    /** The ids of the travellers on a trip. */
    suspend fun tripTravellerIds(tripId: String): List<String> =
        supabase.from("abeona_trip_travellers").select(Columns.list("traveller_id")) { filter { eq("trip_id", tripId) } }
            .decodeList<TripTravellerRow>().map { it.travellerId }

    suspend fun setTripTravellers(tripId: String, travellerIds: List<String>) {
        supabase.postgrest.rpc(
            "abeona_set_trip_travellers",
            buildJsonObject {
                put("p_trip_id", tripId)
                put("p_traveller_ids", JsonArray(travellerIds.map(::JsonPrimitive)))
            },
        )
    }

    /** What is wrong with the trip's BahnCards: the same rows, and words, as on the web. */
    suspend fun tripWarnings(tripId: String): List<TripWarning> =
        supabase.postgrest.rpc("abeona_trip_warnings", buildJsonObject { put("p_trip_id", tripId) }).decodeList()

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

    // ── After the ride ──────────────────────────────────────────────────────

    /** What the trip's delay is worth, by the rules in abeona_compensation_rules; null for a trip not visible. */
    suspend fun compensation(tripId: String): Compensation? {
        val data = supabase.postgrest.rpc("abeona_compensation", buildJsonObject { put("p_trip_id", tripId) }).data
        return if (data.isBlank() || data == "null") null else NexusJson.decodeFromString(data)
    }

    /** The trip's claim, if one was started. */
    suspend fun claim(tripId: String): Claim? =
        supabase.from("abeona_claims").select { filter { eq("trip_id", tripId) } }.decodeList<Claim>().firstOrNull()

    /**
     * Moves the trip's claim to [status], creating it on first use. Claimed and
     * paid stamp their date; going back to open clears both. The web does the same.
     */
    suspend fun setClaimStatus(tripId: String, status: String, amountCents: Int?, existing: Claim?, now: Instant = Instant.now()): Claim {
        val row = buildJsonObject {
            put("trip_id", tripId)
            put("status", status)
            put("amount_cents", amountCents)
            put("claimed_at", if (status == "open") null else existing?.claimedAt ?: now.toString())
            put("paid_at", if (status == "paid") existing?.paidAt ?: now.toString() else null)
            put("updated_at", now.toString())
        }
        return supabase.from("abeona_claims").upsert(row) {
            onConflict = "trip_id"
            select()
        }.decodeSingle()
    }

    suspend fun setTripPrice(tripId: String, priceCents: Int?) {
        supabase.from("abeona_trips").update(buildJsonObject { put("price_cents", priceCents) }) { filter { eq("id", tripId) } }
    }

    suspend fun setForceMajeure(tripId: String, value: Boolean) {
        supabase.from("abeona_trips").update(buildJsonObject { put("force_majeure", value) }) { filter { eq("id", tripId) } }
    }

    /** Records the ride that was missed and what was taken instead; nulls clear it. */
    suspend fun recordDisruption(tripId: String, missedLegIdx: Int?, replacement: List<Leg>?) {
        supabase.from("abeona_trips").update(
            buildJsonObject {
                put("missed_leg_idx", missedLegIdx)
                put("replacement_legs", replacement?.takeIf { it.isNotEmpty() }?.let { NexusJson.encodeToJsonElement(it) } ?: kotlinx.serialization.json.JsonNull)
            },
        ) { filter { eq("id", tripId) } }
    }

    /** Claims to send, claims waiting for an answer, and what was earned back in [year]. */
    suspend fun claimsOverview(year: Int): ClaimsOverview {
        householdId()
        return NexusJson.decodeFromString(
            supabase.postgrest.rpc("abeona_claims_overview", buildJsonObject { put("p_year", year) }).data,
        )
    }

    /**
     * Connections from the stop of a missed ride at its planned time, to pick
     * the one actually taken: the alternatives search, after the fact.
     */
    suspend fun connectionsAfter(trip: Trip, missed: Leg): AlternativesResponse = alternatives(
        trip,
        AlternativesOrigin(missed.from.id, missed.from.name, missed.from.lat, missed.from.lon, missed.from.plannedAt ?: Instant.now()),
    )

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

    /** The household's rides that arrived in [year], one thinned line per pair of stations. */
    suspend fun travelMap(year: Int, zone: ZoneId = ZoneId.systemDefault()): List<TravelMapLine> {
        householdId()
        val from = java.time.LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant()
        val to = java.time.LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant()
        return supabase.postgrest.rpc(
            "abeona_travel_map",
            buildJsonObject { put("p_from", from.toString()); put("p_to", to.toString()) },
        ).decodeList()
    }

    // ── Share links (abeona_share_links, opened by abeona-public) ───────────

    /** The trip's live share links, one per kind at most. */
    suspend fun shareLinks(tripId: String, now: Instant = Instant.now()): List<ShareLink> =
        supabase.from("abeona_share_links").select {
            filter {
                eq("trip_id", tripId)
                gt("expires_at", now.toString())
            }
        }.decodeList()

    /** Makes a link of [kind] ("ics" or "status"); the database chooses the token and the expiry. */
    suspend fun createShareLink(tripId: String, kind: String, now: Instant = Instant.now()): ShareLink {
        // An expired link of the same kind still holds the (trip, kind) slot.
        supabase.from("abeona_share_links").delete {
            filter {
                eq("trip_id", tripId)
                eq("kind", kind)
                lte("expires_at", now.toString())
            }
        }
        return supabase.from("abeona_share_links").insert(buildJsonObject { put("trip_id", tripId); put("kind", kind) }) { select() }
            .decodeSingle()
    }

    /** Revoking deletes the row: the link opens nothing from then on. */
    suspend fun revokeShareLink(token: String) {
        supabase.from("abeona_share_links").delete { filter { eq("token", token) } }
    }

    // ── Imports (the abeona-import Edge Function) ───────────────────────────

    /**
     * Reads the Träwelling check-ins between [from] and [to] as trips. The
     * token goes to the Edge Function in this one request and is kept nowhere.
     */
    suspend fun readTraewelling(token: String, from: java.time.LocalDate, to: java.time.LocalDate): ImportResult =
        NexusJson.decodeFromString(
            supabase.functions.invoke(
                function = "abeona-import",
                body = buildJsonObject {
                    put("action", "import_traewelling")
                    put("token", token.trim())
                    put("from", from.toString())
                    put("to", to.toString())
                },
            ).bodyAsText(),
        )

    /** Saves an imported trip, final from the start; its legs go in as the function gave them. */
    suspend fun saveImportedTrip(trip: ImportedTrip): String {
        householdId()
        val params = buildJsonObject {
            put(
                "p_trip",
                buildJsonObject {
                    put("source", "import")
                    put("provider", "traewelling")
                    put("fare_type", "other")
                    trip.arrivalDelayMinutes?.let { put("arrival_delay_minutes", it) }
                },
            )
            put("p_legs", JsonArray(trip.legs))
        }
        return NexusJson.decodeFromString<String>(supabase.postgrest.rpc("abeona_save_trip", params).data)
    }

    // ── BahnCards ──────────────────────────────────────────────────────────

    /** Every card of the household's travellers, the one running longest first. */
    suspend fun bahnCards(): List<BahnCard> {
        val household = householdId()
        return supabase.from("abeona_bahncards").select(Columns.raw("*,abeona_travellers!inner(household_id)")) {
            filter { eq("abeona_travellers.household_id", household) }
            order("valid_to", Order.DESCENDING)
        }.decodeList()
    }

    /** Inserts a card, or updates it when [id] is given; returns it as stored. */
    suspend fun saveBahnCard(input: BahnCardInput, id: String? = null): BahnCard {
        val row = buildJsonObject {
            put("traveller_id", input.travellerId)
            put("kind", input.kind)
            put("travel_class", input.travelClass)
            put("valid_from", input.validFrom.toString())
            put("valid_to", input.validTo.toString())
            put("card_number", input.cardNumber?.trim()?.takeIf { it.isNotEmpty() })
            put("subscription", input.subscription)
            put("notice_weeks", input.noticeWeeks.coerceIn(0, 26))
        }
        val table = supabase.from("abeona_bahncards")
        return if (id == null) {
            table.insert(row) { select() }.decodeSingle()
        } else {
            table.update(row) { select(); filter { eq("id", id) } }.decodeSingle()
        }
    }

    suspend fun deleteBahnCard(card: BahnCard) {
        supabase.from("abeona_bahncards").delete { filter { eq("id", card.id) } }
        card.filePath?.let { path -> runCatching { tickets.delete(path) } }
    }

    /**
     * Uploads the card's PDF or image to `<household_id>/bahncards/<card_id>/<file>`
     * and records it on the card. Access follows the card, not the prefix
     * (decisions/2026-11-03-abeona-bahncard-files-follow-the-card.md).
     */
    suspend fun uploadBahnCardFile(card: BahnCard, fileName: String, mimeType: String, bytes: ByteArray): String {
        val path = "${householdId()}/bahncards/${card.id}/${safeFileName(fileName)}"
        tickets.upload(path, bytes) {
            upsert = true
            contentType = ContentType.parse(mimeType)
        }
        supabase.from("abeona_bahncards").update(buildJsonObject { put("file_path", path) }) { filter { eq("id", card.id) } }
        card.filePath?.takeIf { it != path }?.let { old -> runCatching { tickets.delete(old) } }
        return path
    }

    suspend fun removeBahnCardFile(card: BahnCard) {
        supabase.from("abeona_bahncards").update(buildJsonObject { put("file_path", null as String?) }) { filter { eq("id", card.id) } }
        card.filePath?.let { path -> runCatching { tickets.delete(path) } }
    }

    /** Subscriptions whose cancellation deadline is at most [withinDays] away, or passed while the card runs. */
    suspend fun bahnCardDeadlines(withinDays: Int = DEADLINE_DAYS): List<BahnCardDeadline> {
        householdId()
        return supabase.postgrest.rpc("abeona_bahncard_deadlines", buildJsonObject { put("p_within_days", withinDays) }).decodeList()
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

    /**
     * Connections from [from] (a stop, by id else by position) to the trip's
     * destination, marked by the Edge Function against what the ticket allows
     * now. One request per refresh; save the live values first, since the
     * allowance reads them.
     */
    suspend fun alternatives(trip: Trip, from: AlternativesOrigin): AlternativesResponse {
        fun place(id: String?, lat: Double?, lon: Double?) = id ?: if (lat != null && lon != null) "$lat,$lon" else null
        val origin = place(from.id, from.lat, from.lon)
        val destination = trip.legs.lastOrNull()?.to?.let { place(it.id, it.lat, it.lon) }
        if (origin == null || destination == null) error("This trip has no stop to search from.")
        return NexusJson.decodeFromString(
            transit(
                buildJsonObject {
                    put("action", "alternatives")
                    put("trip_id", trip.id)
                    put("from", origin)
                    put("to", destination)
                    put("time", from.time.toString())
                },
            ),
        )
    }

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

    /** A ticket or BahnCard file, with the session, never a signed URL (decisions/2026-11-02-abeona-ticket-storage.md). */
    suspend fun downloadTicket(path: String): ByteArray = tickets.downloadAuthenticated(path)

    companion object {
        /** How far ahead the reminder and the Trips banner look for a cancellation deadline; the web uses the same. */
        const val DEADLINE_DAYS = 14

        /** Where the web's status page lives (GitHub Pages, DEPLOYMENT.md). */
        const val STATUS_PAGE = "https://numynox.github.io/nexus/abeona/status"

        /**
         * Where a share link points: the calendar file straight from the
         * abeona-public Edge Function, the status page on the website.
         */
        fun shareUrl(link: ShareLink, supabaseUrl: String): String =
            if (link.kind == "ics") "${supabaseUrl.trimEnd('/')}/functions/v1/abeona-public?t=${link.token}"
            else "$STATUS_PAGE?t=${link.token}"

        /** Letters, digits, dot, dash and underscore; Storage keys are picky, and so are people's file names. */
        fun safeFileName(name: String): String =
            name.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').take(80).ifEmpty { "ticket.pdf" }
    }
}
