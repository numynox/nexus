package io.github.numynox.nexus.vibilia.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.numynox.nexus.core.data.NexusJson
import io.ktor.client.statement.bodyAsText
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

// The Android counterpart of app/vibilia/src/lib/data.ts: every query and RPC
// call Vibilia makes, grouped by what it is about. Screens never talk to
// Supabase directly.

private fun JsonObject.with(vararg extra: Pair<String, String>): JsonObject = buildJsonObject {
    this@with.forEach { (key, value) -> put(key, value) }
    extra.forEach { (key, value) -> put(key, value) }
}

private inline fun <reified T> T.toJsonObject(): JsonObject = NexusJson.encodeToJsonElement(this).jsonObject

class FuelPriceRepository(private val supabase: SupabaseClient) {

    suspend fun currentPrices(fuelType: String): List<StationPrice> =
        supabase.postgrest.rpc(
            "get_fuel_stations_current_prices",
            buildJsonObject { put("p_fuel_type", fuelType) },
        ).decodeList<CurrentPriceRow>().map { it.toStationPrice() }

    /** Minimum price per time-of-day bucket and day, as the history chart plots it. */
    suspend fun priceHistory(fuelType: String, since: Instant, bucketMinutes: Int, timeZone: String): List<PriceBucket> =
        supabase.postgrest.rpc(
            "get_fuel_price_plot_history",
            buildJsonObject {
                put("p_fuel_type", fuelType)
                put("p_since", since.toString())
                put("p_bucket_minutes", bucketMinutes)
                put("p_time_zone", timeZone)
            },
        ).decodeList()

    suspend fun weeklyMinima(fuelType: String, since: Instant, until: Instant): List<WeeklyMinimum> =
        supabase.postgrest.rpc(
            "get_fuel_price_weekly_minima",
            buildJsonObject {
                put("p_fuel_type", fuelType)
                put("p_since", since.toString())
                put("p_until", until.toString())
                put("p_time_zone", "Europe/Berlin")
            },
        ).decodeList()

    suspend fun lastRefuelPrice(): LastRefuelPrice? =
        supabase.from("refuel_events")
            .select(Columns.list("fueled_at", "price_per_liter_calculated", "liters", "total_price", "created_at")) {
                order("fueled_at", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<LastRefuelRow>()
            .firstOrNull()
            ?.let { row ->
                val price = row.pricePerLiterCalculated
                    ?: row.totalPrice?.let { total -> row.liters?.takeIf { it > 0 }?.let { total / it } }
                price?.let { LastRefuelPrice(row.fueledAt, it) }
            }

    /** Stations we track, for the refuel form's picker. */
    suspend fun stations(): List<FuelStation> =
        supabase.from("fuel_stations")
            .select(Columns.list("id", "brand", "place", "street", "house_number", "lat", "lng")) {
                order("brand", Order.ASCENDING)
                order("place", Order.ASCENDING)
                order("street", Order.ASCENDING)
                order("house_number", Order.ASCENDING)
            }
            .decodeList()

    private data class NearbyCache(val at: Instant, val lat: Double, val lng: Double, val key: String, val stations: List<StationPrice>)

    @Volatile
    private var nearbyCache: NearbyCache? = null

    /**
     * Any station within the radius, live from Tankerkoenig through the
     * fetch-nearby-fuel-prices Edge Function. Cached for 15 minutes while the
     * phone stays put, like the web app's cache, to spare the API.
     */
    suspend fun nearby(lat: Double, lng: Double, fuelType: String, radiusKm: Double, force: Boolean = false): List<StationPrice> {
        val key = "$fuelType/$radiusKm"
        val now = Instant.now()
        nearbyCache?.takeIf {
            !force && it.key == key && it.at.plus(15, ChronoUnit.MINUTES).isAfter(now) &&
                abs(it.lat - lat) < 0.003 && abs(it.lng - lng) < 0.003
        }?.let { return it.stations }

        val response = supabase.functions.invoke(
            function = "fetch-nearby-fuel-prices",
            body = buildJsonObject {
                put("lat", lat)
                put("lng", lng)
                put("fuelType", fuelType)
                put("radiusKm", radiusKm)
            },
        )
        val stations = NexusJson.decodeFromString<NearbyResponse>(response.bodyAsText()).stations.map { it.toStationPrice() }
        nearbyCache = NearbyCache(now, lat, lng, key, stations)
        return stations
    }
}

class CarRepository(private val supabase: SupabaseClient) {

    /** RLS returns exactly the cars the user owns or has been given. */
    suspend fun cars(): List<Car> =
        supabase.from("cars").select { order("created_at", Order.DESCENDING) }.decodeList()

    suspend fun create(ownerId: String, draft: CarDraft): Car =
        supabase.from("cars").insert(draft.toJsonObject().with("owner_id" to ownerId)) { select() }.decodeSingle()

    suspend fun update(carId: String, draft: CarDraft): Car =
        supabase.from("cars").update(draft.toJsonObject()) {
            filter { eq("id", carId) }
            select()
        }.decodeSingle()

    suspend fun delete(carId: String) {
        supabase.from("cars").delete { filter { eq("id", carId) } }
    }

    suspend fun members(carId: String): List<CarMember> =
        supabase.postgrest.rpc("list_car_members", buildJsonObject { put("p_car_id", carId) }).decodeList()

    suspend fun share(carId: String, email: String) {
        supabase.postgrest.rpc(
            "share_car_with_email",
            buildJsonObject {
                put("p_car_id", carId)
                put("p_email", email.trim())
            },
        )
    }

    suspend fun bluetoothLinks(): List<BluetoothLink> =
        supabase.from("vibilia_car_bluetooth_devices")
            .select(Columns.list("car_id", "address", "name")) { order("created_at", Order.ASCENDING) }
            .decodeList()

    suspend fun link(carId: String, address: String, name: String?) {
        supabase.from("vibilia_car_bluetooth_devices").upsert(
            buildJsonObject {
                put("car_id", carId)
                put("address", address.uppercase())
                put("name", name)
            },
        ) { onConflict = "car_id,address" }
    }

    suspend fun unlink(carId: String, address: String) {
        supabase.from("vibilia_car_bluetooth_devices").delete {
            filter {
                eq("car_id", carId)
                eq("address", address)
            }
        }
    }
}

class LogbookRepository(private val supabase: SupabaseClient) {

    suspend fun refuels(carId: String): List<RefuelEvent> =
        supabase.from("refuel_events")
            .select(Columns.raw("*, fuel_station:fuel_stations(id, brand, place, street, house_number)")) {
                filter { eq("car_id", carId) }
                order("fueled_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun expenses(carId: String): List<CarExpense> =
        supabase.from("car_expenses").select {
            filter { eq("car_id", carId) }
            order("expensed_at", Order.DESCENDING)
        }.decodeList()

    suspend fun createRefuel(carId: String, userId: String, draft: RefuelDraft) {
        supabase.from("refuel_events").insert(draft.toJsonObject().with("car_id" to carId, "user_id" to userId))
    }

    suspend fun updateRefuel(id: Long, draft: RefuelDraft) {
        supabase.from("refuel_events").update(draft.toJsonObject()) { filter { eq("id", id) } }
    }

    suspend fun createExpense(carId: String, userId: String, draft: ExpenseDraft) {
        supabase.from("car_expenses").insert(draft.toJsonObject().with("car_id" to carId, "user_id" to userId))
    }

    suspend fun updateExpense(id: Long, draft: ExpenseDraft) {
        supabase.from("car_expenses").update(draft.toJsonObject()) { filter { eq("id", id) } }
    }
}

/**
 * Statistics, the drive model and the fuel estimate. The drive-tracking RPCs
 * (20261019120000_vibilia_drive_tracking.sql) fall back to the older ones or to
 * nothing, so this app also works against a database that predates them.
 */
class StatisticsRepository(private val supabase: SupabaseClient) {

    private fun carParams(carId: String, since: Instant? = null, until: Instant? = null) = buildJsonObject {
        put("p_car_id", carId)
        since?.let { put("p_since", it.toString()) }
        until?.let { put("p_until", it.toString()) }
    }

    suspend fun statistics(carId: String): CarStatistics? =
        supabase.postgrest.rpc("get_car_refuel_statistics", carParams(carId)).decodeList<CarStatistics>().firstOrNull()

    suspend fun yearBounds(carId: String): YearBounds =
        supabase.postgrest.rpc("get_car_refuel_year_bounds", carParams(carId)).decodeList<YearBounds>().firstOrNull()
            ?: YearBounds()

    suspend fun plotEvents(carId: String, since: Instant, until: Instant): List<PlotEvent> =
        supabase.postgrest.rpc("get_car_refuel_plot_events", carParams(carId, since, until)).decodeList()

    suspend fun distancePerMonth(carId: String, since: Instant, until: Instant): List<DistanceMonth> =
        runCatching {
            supabase.postgrest.rpc("get_car_distance_per_month", carParams(carId, since, until)).decodeList<DistanceMonth>()
        }.getOrElse {
            supabase.postgrest.rpc("get_car_km_per_month", carParams(carId, since, until)).decodeList<DistanceMonth>()
        }

    suspend fun dailyDistance(carId: String, since: Instant, until: Instant): List<DailyDistance> =
        runCatching {
            supabase.postgrest.rpc("get_car_daily_distance", carParams(carId, since, until)).decodeList<DailyDistance>()
        }.getOrDefault(emptyList())

    suspend fun driveModel(carId: String): DriveModel? =
        runCatching {
            supabase.postgrest.rpc("get_car_drive_model", carParams(carId)).decodeList<DriveModel>().firstOrNull()
        }.getOrNull()?.takeIf { it.trackingSince != null }

    suspend fun drivePeriods(carId: String, since: Instant, until: Instant): List<DrivePeriod> =
        runCatching {
            supabase.postgrest.rpc("get_car_drive_periods", carParams(carId, since, until)).decodeList<DrivePeriod>()
        }.getOrDefault(emptyList())

    /** Null when there is not enough history to estimate anything. */
    suspend fun fuelLevel(carId: String, car: Car?, refuels: suspend () -> List<RefuelEvent>): FuelLevel? =
        runCatching {
            supabase.postgrest.rpc("get_car_fuel_level_estimate", carParams(carId)).decodeList<FuelLevelRow>()
        }.map { rows -> rows.firstOrNull()?.let(FuelLevel::from) }
            .getOrElse { FuelLevel.fromRefuels(car, refuels()) }
}

class DriveRepository(private val supabase: SupabaseClient) {
    /** Idempotent: an interval already uploaded (same id) is skipped by the database. */
    suspend fun upload(intervals: List<DriveIntervalUpload>) {
        if (intervals.isEmpty()) return
        supabase.from("vibilia_drive_intervals").upsert(intervals) {
            onConflict = "id"
            ignoreDuplicates = true
        }
    }
}
