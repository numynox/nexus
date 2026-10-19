@file:UseSerializers(InstantSerializer::class, LocalDateSerializer::class)

package io.github.numynox.nexus.vibilia.data

import io.github.numynox.nexus.core.data.InstantSerializer
import io.github.numynox.nexus.core.data.LocalDateSerializer
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Row shapes, hand-written like the web app's lib/data.ts
// (decisions/2026-09-04-permissive-supabase-client-generics.md). Column names
// are the database's; property names are Kotlin's.

/** Glossary: `car` in SQL, "Vehicle" in the UI. */
@Serializable
data class Car(
    val id: String,
    val name: String,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("tank_capacity") val tankCapacity: Double? = null,
    val vin: String? = null,
    val plate: String? = null,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
) {
    val description: String
        get() = listOfNotNull(year?.toString(), make, model).joinToString(" ")
}

@Serializable
data class CarDraft(
    val name: String,
    @SerialName("tank_capacity") val tankCapacity: Double,
    val vin: String? = null,
    val plate: String? = null,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
)

@Serializable
data class CarMember(
    @SerialName("user_id") val userId: String,
    val role: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
) {
    val isOwner: Boolean get() = role == "owner"
    val displayName: String get() = fullName?.takeIf { it.isNotBlank() } ?: email?.takeIf { it.isNotBlank() } ?: userId
}

@Serializable
data class StationSummary(
    val id: String,
    val brand: String? = null,
    val place: String? = null,
    val street: String? = null,
    @SerialName("house_number") val houseNumber: String? = null,
) {
    /** "Aral Erlangen (Hauptstraße 3)", as the web log prints it. */
    val label: String
        get() = "${brand.orEmpty()} ${place.orEmpty()} (${street.orEmpty()} ${houseNumber.orEmpty()})"
            .replace(Regex("\\s+"), " ").replace("( ", "(").replace(" )", ")").replace("()", "").trim()
}

@Serializable
data class RefuelEvent(
    val id: Long,
    @SerialName("car_id") val carId: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("fueled_at") val fueledAt: Instant,
    val mileage: Int,
    val liters: Double,
    @SerialName("total_price") val totalPrice: Double,
    @SerialName("price_per_liter_calculated") val pricePerLiterCalculated: Double? = null,
    @SerialName("fuel_level_after") val fuelLevelAfter: Double? = null,
    @SerialName("is_full_refuel") val isFullRefuel: Boolean = true,
    @SerialName("missed_previous_refuel") val missedPreviousRefuel: Boolean = false,
    @SerialName("fuel_station_id") val fuelStationId: String? = null,
    @SerialName("fuel_station") val fuelStation: StationSummary? = null,
) {
    val pricePerLiter: Double
        get() = pricePerLiterCalculated ?: if (liters > 0) totalPrice / liters else 0.0
}

/** The editable part of a refuel; car and user are set on insert only. */
@Serializable
data class RefuelDraft(
    val mileage: Int,
    val liters: Double,
    @SerialName("total_price") val totalPrice: Double,
    @SerialName("fuel_level_after") val fuelLevelAfter: Double,
    @SerialName("is_full_refuel") val isFullRefuel: Boolean,
    @SerialName("missed_previous_refuel") val missedPreviousRefuel: Boolean,
    @SerialName("price_per_liter_calculated") val pricePerLiterCalculated: Double,
    @SerialName("fueled_at") val fueledAt: Instant,
    @SerialName("fuel_station_id") val fuelStationId: String?,
)

@Serializable
data class CarExpense(
    val id: Long,
    @SerialName("car_id") val carId: String,
    @SerialName("expensed_at") val expensedAt: Instant,
    val title: String,
    val amount: Double,
    val mileage: Int? = null,
    val category: String = "Other",
    val notes: String? = null,
)

@Serializable
data class ExpenseDraft(
    @SerialName("expensed_at") val expensedAt: Instant,
    val title: String,
    val amount: Double,
    val mileage: Int?,
    val category: String,
    val notes: String?,
)

/** The fixed list the web app offers, in its order. */
val ExpenseCategories = listOf(
    "Service", "Maintenance", "Insurance", "Tax", "Parking",
    "Toll", "Vehicle Wash", "Repairs", "Accessories", "Other",
)

/** E5 / E10 / Diesel, stored capitalised (glossary.md). */
val FuelTypes = listOf("E5", "E10", "Diesel")

@Serializable
data class FuelStation(
    val id: String,
    val brand: String? = null,
    val place: String? = null,
    val street: String? = null,
    @SerialName("house_number") val houseNumber: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
) {
    val summary: StationSummary get() = StationSummary(id, brand, place, street, houseNumber)
}

/** Tankerkoenig's opening hours, stored raw as JSONB. */
data class OpeningTime(val label: String, val start: String?, val end: String?) {
    val range: String
        get() = listOfNotNull(start?.take(5), end?.take(5)).joinToString(" – ")
}

fun parseOpeningTimes(json: JsonElement?): List<OpeningTime> =
    (json as? JsonArray).orEmpty().mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        fun field(name: String) = (obj[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        OpeningTime(field("text") ?: "Opening hours", field("start"), field("end"))
    }

/** A station with its latest price — either one of ours or one found nearby. */
data class StationPrice(
    val id: String,
    val brand: String,
    val place: String,
    val street: String,
    val houseNumber: String,
    val price: Double?,
    /** Our own negotiated discount per litre, e.g. a fuel card. */
    val discount: Double = 0.0,
    val wholeDay: Boolean = false,
    val openingTimes: List<OpeningTime> = emptyList(),
    val checkedAt: Instant? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val distanceKm: Double? = null,
    val isOpen: Boolean? = null,
) {
    val effectivePrice: Double? get() = price?.let { (it - discount).coerceAtLeast(0.0) }
    val displayName: String get() = brand.ifBlank { "Station" }
    val displayPlace: String get() = place.trim().lowercase().replaceFirstChar { it.titlecase() }
    val address: String get() = "$street $houseNumber".trim()
}

@Serializable
data class CurrentPriceRow(
    val id: String,
    val name: String? = null,
    val brand: String? = null,
    val street: String? = null,
    @SerialName("house_number") val houseNumber: String? = null,
    val place: String? = null,
    @SerialName("opening_times") val openingTimes: JsonElement? = null,
    @SerialName("whole_day") val wholeDay: Boolean? = null,
    val discount: Double? = null,
    @SerialName("current_price") val currentPrice: Double? = null,
    @SerialName("checked_at") val checkedAt: Instant? = null,
) {
    fun toStationPrice() = StationPrice(
        id = id,
        brand = brand ?: name.orEmpty(),
        place = place.orEmpty(),
        street = street.orEmpty(),
        houseNumber = houseNumber.orEmpty(),
        price = currentPrice,
        discount = discount ?: 0.0,
        wholeDay = wholeDay ?: false,
        openingTimes = parseOpeningTimes(openingTimes),
        checkedAt = checkedAt,
    )
}

@Serializable
data class NearbyResponse(val stations: List<NearbyStationRow> = emptyList())

@Serializable
data class NearbyStationRow(
    val id: String,
    val name: String? = null,
    val brand: String? = null,
    val street: String? = null,
    @SerialName("house_number") val houseNumber: String? = null,
    val place: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val currentPrice: Double? = null,
    val distanceKm: Double? = null,
    val isOpen: Boolean? = null,
) {
    fun toStationPrice() = StationPrice(
        id = id,
        brand = brand?.takeIf { it.isNotBlank() } ?: name.orEmpty(),
        place = place.orEmpty(),
        street = street.orEmpty(),
        houseNumber = houseNumber.orEmpty(),
        price = currentPrice,
        lat = lat,
        lng = lng,
        distanceKm = distanceKm,
        isOpen = isOpen,
    )
}

@Serializable
data class PriceBucket(
    @SerialName("day_key") val day: LocalDate,
    @SerialName("bucket_minute") val bucketMinute: Int,
    @SerialName("min_price") val minPrice: Double,
)

@Serializable
data class WeeklyMinimum(
    @SerialName("week_start") val weekStart: LocalDate,
    @SerialName("min_price") val minPrice: Double,
)

/** The most recent refuel across every accessible car — the dot on the price chart. */
data class LastRefuelPrice(val fueledAt: Instant, val pricePerLiter: Double)

@Serializable
data class LastRefuelRow(
    @SerialName("fueled_at") val fueledAt: Instant,
    @SerialName("price_per_liter_calculated") val pricePerLiterCalculated: Double? = null,
    val liters: Double? = null,
    @SerialName("total_price") val totalPrice: Double? = null,
)

@Serializable
data class CarStatistics(
    @SerialName("latest_mileage") val latestMileage: Int = 0,
    @SerialName("driven_total_km") val drivenTotalKm: Double = 0.0,
    @SerialName("driven_this_year_km") val drivenThisYearKm: Double = 0.0,
    @SerialName("driven_last_year_km") val drivenLastYearKm: Double = 0.0,
    @SerialName("driven_avg_month_km") val drivenAvgMonthKm: Double = 0.0,
    @SerialName("driven_avg_day_km") val drivenAvgDayKm: Double = 0.0,
    @SerialName("fuel_used_total_l") val fuelUsedTotalL: Double = 0.0,
    @SerialName("fuel_used_this_year_l") val fuelUsedThisYearL: Double = 0.0,
    @SerialName("fuel_used_last_year_l") val fuelUsedLastYearL: Double = 0.0,
    @SerialName("fuel_used_avg_month_l") val fuelUsedAvgMonthL: Double = 0.0,
    @SerialName("fuel_used_avg_day_l") val fuelUsedAvgDayL: Double = 0.0,
    @SerialName("fuel_cost_total_eur") val fuelCostTotalEur: Double = 0.0,
    @SerialName("fuel_cost_this_year_eur") val fuelCostThisYearEur: Double = 0.0,
    @SerialName("fuel_cost_last_year_eur") val fuelCostLastYearEur: Double = 0.0,
    @SerialName("fuel_cost_avg_month_eur") val fuelCostAvgMonthEur: Double = 0.0,
    @SerialName("fuel_cost_avg_day_eur") val fuelCostAvgDayEur: Double = 0.0,
    @SerialName("avg_consumption_l_per_100km") val avgConsumption: Double = 0.0,
    @SerialName("min_consumption_l_per_100km") val minConsumption: Double = 0.0,
    @SerialName("max_consumption_l_per_100km") val maxConsumption: Double = 0.0,
    @SerialName("avg_cost_per_km_eur") val avgCostPerKm: Double = 0.0,
    @SerialName("min_cost_per_km_eur") val minCostPerKm: Double = 0.0,
    @SerialName("max_cost_per_km_eur") val maxCostPerKm: Double = 0.0,
    @SerialName("expense_total_eur") val expenseTotalEur: Double = 0.0,
    @SerialName("expense_this_year_eur") val expenseThisYearEur: Double = 0.0,
    @SerialName("expense_last_year_eur") val expenseLastYearEur: Double = 0.0,
    @SerialName("expense_avg_year_eur") val expenseAvgYearEur: Double = 0.0,
)

@Serializable
data class YearBounds(
    @SerialName("min_year") val minYear: Int? = null,
    @SerialName("max_year") val maxYear: Int? = null,
)

@Serializable
data class PlotEvent(
    @SerialName("fueled_at") val fueledAt: Instant,
    val mileage: Int? = null,
    @SerialName("price_per_liter") val pricePerLiter: Double? = null,
    @SerialName("consumption_l_per_100km") val consumption: Double? = null,
)

@Serializable
data class DistanceMonth(
    @SerialName("month_key") val month: LocalDate,
    @SerialName("km_driven") val kmDriven: Double = 0.0,
    @SerialName("liters_used") val litersUsed: Double = 0.0,
    @SerialName("estimated_km") val estimatedKm: Double = 0.0,
    @SerialName("estimated_liters") val estimatedLiters: Double = 0.0,
    @SerialName("driving_hours") val drivingHours: Double = 0.0,
)

@Serializable
data class DailyDistance(
    val day: LocalDate,
    val km: Double = 0.0,
    val liters: Double = 0.0,
    @SerialName("estimated_km") val estimatedKm: Double = 0.0,
    @SerialName("estimated_liters") val estimatedLiters: Double = 0.0,
    @SerialName("driving_seconds") val drivingSeconds: Double = 0.0,
)

/** What a car does per hour of driving, learned from Bluetooth drives. */
@Serializable
data class DriveModel(
    @SerialName("tracking_since") val trackingSince: Instant? = null,
    @SerialName("last_drive_at") val lastDriveAt: Instant? = null,
    @SerialName("sample_count") val sampleCount: Int = 0,
    @SerialName("sample_hours") val sampleHours: Double = 0.0,
    @SerialName("sample_km") val sampleKm: Double = 0.0,
    @SerialName("km_per_hour") val kmPerHour: Double? = null,
    @SerialName("liters_per_hour") val litersPerHour: Double? = null,
    @SerialName("liters_per_100km") val litersPer100Km: Double? = null,
)

@Serializable
data class DrivePeriod(
    @SerialName("started_at") val startedAt: Instant,
    @SerialName("ended_at") val endedAt: Instant,
    @SerialName("reporter_count") val reporterCount: Int = 1,
) {
    val hours: Double get() = (endedAt.toEpochMilli() - startedAt.toEpochMilli()) / 3_600_000.0
}

/** A car's most recent drive, seen by any phone, with its distance estimated from the drive model. */
data class LastDrive(val startedAt: Instant, val endedAt: Instant, val estimatedKm: Double?) {
    val hours: Double get() = (endedAt.toEpochMilli() - startedAt.toEpochMilli()) / 3_600_000.0

    companion object {
        /**
         * The later of the server's last merged period and this phone's own
         * drive that is not uploaded yet, so a drive shows as soon as it ends.
         */
        fun of(server: DrivePeriod?, local: Pair<Instant, Instant>?, kmPerHour: Double?): LastDrive? {
            val (start, end) = listOfNotNull(server?.let { it.startedAt to it.endedAt }, local).maxByOrNull { it.second } ?: return null
            val hours = (end.toEpochMilli() - start.toEpochMilli()) / 3_600_000.0
            return LastDrive(start, end, kmPerHour?.let { it * hours })
        }
    }
}

@Serializable
data class FuelLevelRow(
    val method: String,
    @SerialName("last_refuel_at") val lastRefuelAt: Instant,
    @SerialName("last_mileage") val lastMileage: Int? = null,
    @SerialName("tank_capacity") val tankCapacity: Double,
    @SerialName("starting_liters") val startingLiters: Double,
    @SerialName("consumed_liters") val consumedLiters: Double = 0.0,
    @SerialName("remaining_liters") val remainingLiters: Double,
    @SerialName("fill_ratio") val fillRatio: Double,
    @SerialName("driving_hours") val drivingHours: Double? = null,
    @SerialName("estimated_km") val estimatedKm: Double? = null,
    @SerialName("estimated_mileage") val estimatedMileage: Double? = null,
    @SerialName("km_per_hour") val kmPerHour: Double? = null,
    @SerialName("liters_per_hour") val litersPerHour: Double? = null,
    @SerialName("liters_per_day") val litersPerDay: Double? = null,
)

@Serializable
data class BluetoothLink(
    @SerialName("car_id") val carId: String,
    val address: String,
    val name: String? = null,
)

/** One connection, as a phone reports it. The id is generated here, so retries are no-ops. */
@Serializable
data class DriveIntervalUpload(
    val id: String,
    @SerialName("car_id") val carId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("started_at") val startedAt: Instant,
    @SerialName("ended_at") val endedAt: Instant,
)
