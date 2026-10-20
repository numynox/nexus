package io.github.numynox.nexus.vibilia.screenshots

import io.github.numynox.nexus.core.testing.FakeSupabase
import io.github.numynox.nexus.vibilia.data.BluetoothLink
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.sin
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A Supabase that answers every query Vibilia makes with plausible data, so
 * the real app can run in a JVM test and be photographed. Dates are relative
 * to now, so the screenshots always look current.
 */
object FakeBackend {
    const val USER_ID = FakeSupabase.USER_ID
    const val GOLF = "c1111111-1111-4111-8111-111111111111"
    const val POLO = "c2222222-2222-4222-8222-222222222222"
    const val GOLF_RADIO = "F4:5E:AB:12:34:56"

    val links = listOf(BluetoothLink(GOLF, GOLF_RADIO, "VW Radio"))

    val config = FakeSupabase.config("Vibilia")

    private val zone = ZoneId.systemDefault()
    private val now = Instant.now().truncatedTo(ChronoUnit.MINUTES)
    private fun daysAgo(days: Long, hour: Int = 17) =
        LocalDate.now(zone).minusDays(days).atTime(hour, 12).atZone(zone).toInstant().toString()

    fun engine() = FakeSupabase.engine { request, path ->
        val params = request.url.parameters
        when {
            path.endsWith("/functions/v1/fetch-nearby-fuel-prices") -> nearby().toString()
            path.endsWith("/rpc/get_fuel_stations_current_prices") -> currentPrices().toString()
            path.endsWith("/rpc/get_fuel_price_plot_history") -> priceHistory().toString()
            path.endsWith("/rpc/get_fuel_price_weekly_minima") -> weeklyMinima().toString()
            path.endsWith("/rpc/get_car_fuel_level_estimate") -> fuelLevel().toString()
            path.endsWith("/rpc/get_car_refuel_statistics") -> statistics().toString()
            path.endsWith("/rpc/get_car_refuel_year_bounds") -> """[{"min_year": ${LocalDate.now().year - 2}, "max_year": ${LocalDate.now().year}}]"""
            path.endsWith("/rpc/get_car_drive_model") -> driveModel().toString()
            path.endsWith("/rpc/get_car_daily_distance") -> dailyDistance().toString()
            path.endsWith("/rpc/get_car_drive_periods") -> drivePeriods().toString()
            path.endsWith("/rpc/get_car_refuel_plot_events") -> plotEvents().toString()
            path.endsWith("/rpc/get_car_distance_per_month") -> months().toString()
            path.endsWith("/rpc/list_car_members") -> members().toString()
            path.endsWith("/rest/v1/cars") -> cars().toString()
            path.endsWith("/rest/v1/refuel_events") && params["limit"] == "1" -> JsonArray(refuels().take(1)).toString()
            path.endsWith("/rest/v1/refuel_events") -> JsonArray(refuels()).toString()
            path.endsWith("/rest/v1/car_expenses") -> expenses().toString()
            path.endsWith("/rest/v1/fuel_stations") -> stations().toString()
            path.endsWith("/rest/v1/vibilia_car_bluetooth_devices") ->
                buildJsonArray { links.forEach { addJsonObject { put("car_id", it.carId); put("address", it.address); put("name", it.name) } } }.toString()
            else -> null
        }
    }

    private fun cars() = buildJsonArray {
        addJsonObject {
            put("id", GOLF); put("name", "Golf"); put("owner_id", USER_ID); put("tank_capacity", 50)
            put("plate", "ER-VW 2019"); put("make", "Volkswagen"); put("model", "Golf VIII"); put("year", 2019)
        }
        addJsonObject {
            put("id", POLO); put("name", "Polo"); put("owner_id", USER_ID); put("tank_capacity", 40)
            put("plate", "ER-PO 88"); put("make", "Volkswagen"); put("model", "Polo"); put("year", 2015)
        }
    }

    private data class Station(val id: String, val brand: String, val place: String, val street: String, val number: String, val lat: Double, val lng: Double)

    private val ours = listOf(
        Station("s-aral", "ARAL", "ERLANGEN", "Äußere Brucker Str.", "51", 49.5950, 10.9890),
        Station("s-shell", "Shell", "ERLANGEN", "Nürnberger Str.", "45", 49.5880, 11.0080),
        Station("s-jet", "JET", "TENNENLOHE", "Am Wetterkreuz", "1", 49.5490, 11.0230),
        Station("s-esso", "Esso", "BUBENREUTH", "Bayreuther Str.", "8", 49.6260, 11.0190),
    )

    private fun stations() = buildJsonArray {
        ours.forEach { s ->
            addJsonObject {
                put("id", s.id); put("brand", s.brand); put("place", s.place); put("street", s.street)
                put("house_number", s.number); put("lat", s.lat); put("lng", s.lng)
            }
        }
    }

    private fun currentPrices() = buildJsonArray {
        val prices = listOf(1.789, 1.819, 1.749, 1.799)
        ours.forEachIndexed { i, s ->
            addJsonObject {
                put("id", s.id); put("name", s.brand); put("brand", s.brand); put("street", s.street)
                put("house_number", s.number); put("place", s.place); put("whole_day", i == 0)
                put("discount", if (i == 1) 0.02 else 0.0); put("current_price", prices[i]); put("checked_at", now.toString())
                putJsonArray("opening_times") {
                    addJsonObject { put("text", "Mo-Fr"); put("start", "06:00:00"); put("end", "22:00:00") }
                    addJsonObject { put("text", "Sa, So"); put("start", "07:00:00"); put("end", "21:00:00") }
                }
            }
        }
    }

    private fun priceHistory() = buildJsonArray {
        val today = LocalDate.now(zone)
        val nowMinute = java.time.LocalTime.now(zone).let { it.hour * 60 + it.minute }
        for (offset in 0..3) {
            for (slot in 0 until 144) {
                val minute = slot * 10
                if (offset == 0 && minute > nowMinute) continue
                // Prices peak in the morning and drop in the evening, as they do.
                val price = 1.74 + 0.012 * offset + 0.06 * (1 + sin((minute - 300) / 1440.0 * 2 * Math.PI)) / 2 +
                    (if (minute in 1080..1290) -0.035 else 0.0)
                addJsonObject {
                    put("day_key", today.minusDays(offset.toLong()).toString()); put("bucket_minute", minute)
                    put("min_price", Math.round(price * 1000) / 1000.0)
                }
            }
        }
    }

    private fun weeklyMinima() = buildJsonArray {
        for (week in 52 downTo 0) {
            addJsonObject {
                put("week_start", LocalDate.now(zone).minusWeeks(week.toLong()).toString())
                put("min_price", Math.round((1.68 + 0.06 * sin(week / 6.0)) * 1000) / 1000.0)
            }
        }
    }

    private fun refuels(): List<JsonObject> {
        // Newest first, every ~3 weeks, as the logbook query orders them.
        return (0 until 16).map { i ->
            val mileage = 45_230 - i * 620 - (i % 3) * 35
            val liters = 41.2 + (i % 4) * 1.3
            val price = 1.749 + 0.02 * sin(i.toDouble())
            buildJsonObject {
                put("id", 100 - i); put("car_id", GOLF); put("user_id", USER_ID)
                put("fueled_at", daysAgo(3L + i * 21L)); put("created_at", daysAgo(3L + i * 21L))
                put("mileage", mileage); put("liters", liters); put("total_price", Math.round(liters * price * 100) / 100.0)
                put("price_per_liter_calculated", Math.round(price * 1000) / 1000.0)
                put("fuel_level_after", if (i == 4) 38 else 50); put("is_full_refuel", i != 4); put("missed_previous_refuel", false)
                val s = ours[i % ours.size]
                put("fuel_station_id", s.id)
                putJsonObject("fuel_station") { put("id", s.id); put("brand", s.brand); put("place", s.place.lowercase().replaceFirstChar { it.uppercase() }); put("street", s.street); put("house_number", s.number) }
            }
        }
    }

    private fun expenses() = buildJsonArray {
        addJsonObject { put("id", 7); put("car_id", GOLF); put("expensed_at", daysAgo(12)); put("title", "Annual inspection"); put("amount", 189.90); put("mileage", 44_900); put("category", "Service"); put("notes", "Brakes checked, wipers replaced") }
        addJsonObject { put("id", 6); put("car_id", GOLF); put("expensed_at", daysAgo(40)); put("title", "Car wash"); put("amount", 14.0); put("category", "Vehicle Wash") }
        addJsonObject { put("id", 5); put("car_id", GOLF); put("expensed_at", daysAgo(95)); put("title", "Insurance"); put("amount", 412.0); put("category", "Insurance") }
    }

    private fun fuelLevel() = buildJsonArray {
        addJsonObject {
            put("method", "drive"); put("last_refuel_at", daysAgo(3)); put("last_mileage", 45_230); put("tank_capacity", 50)
            put("starting_liters", 50); put("consumed_liters", 14.2); put("remaining_liters", 35.8); put("fill_ratio", 0.716)
            put("driving_hours", 3.2); put("estimated_km", 218); put("estimated_mileage", 45_448)
            put("km_per_hour", 68.2); put("liters_per_hour", 4.4); put("liters_per_day", 1.4)
        }
    }

    private fun statistics() = buildJsonArray {
        addJsonObject {
            put("latest_mileage", 45_230); put("driven_total_km", 18_430); put("driven_this_year_km", 6_210); put("driven_last_year_km", 7_480)
            put("driven_avg_month_km", 615); put("driven_avg_day_km", 20.2); put("fuel_used_total_l", 1_190); put("fuel_used_this_year_l", 398)
            put("fuel_used_last_year_l", 470); put("fuel_used_avg_month_l", 39.6); put("fuel_used_avg_day_l", 1.3); put("fuel_cost_total_eur", 2_105)
            put("fuel_cost_this_year_eur", 702); put("fuel_cost_last_year_eur", 842); put("fuel_cost_avg_month_eur", 70.1); put("fuel_cost_avg_day_eur", 2.31)
            put("avg_consumption_l_per_100km", 6.46); put("min_consumption_l_per_100km", 5.61); put("max_consumption_l_per_100km", 7.92)
            put("avg_cost_per_km_eur", 0.114); put("min_cost_per_km_eur", 0.096); put("max_cost_per_km_eur", 0.141)
            put("expense_total_eur", 1_420); put("expense_this_year_eur", 615.9); put("expense_last_year_eur", 560); put("expense_avg_year_eur", 473)
        }
    }

    private fun driveModel() = buildJsonArray {
        addJsonObject {
            put("tracking_since", daysAgo(80)); put("last_drive_at", now.minusSeconds(3_600).toString()); put("sample_count", 4)
            put("sample_hours", 41.5); put("sample_km", 2_830); put("km_per_hour", 68.2); put("liters_per_hour", 4.4); put("liters_per_100km", 6.45); put("speed_factor", 1.08)
        }
    }

    private fun dailyDistance() = buildJsonArray {
        for (i in 29 downTo 0) {
            addJsonObject {
                put("day", LocalDate.now(zone).minusDays(i.toLong()).toString())
                val weekend = LocalDate.now(zone).minusDays(i.toLong()).dayOfWeek.value >= 6
                val km = if (i < 3) 0.0 else if (weekend) (if (i % 2 == 0) 85.0 else 0.0) else 34.0 + 9 * sin(i.toDouble())
                put("km", km); put("liters", km * 0.0645)
                put("estimated_km", if (i < 3 && i != 1) 72.0 else 0.0); put("estimated_liters", if (i < 3 && i != 1) 4.6 else 0.0)
                put("driving_seconds", (km.coerceAtLeast(if (i < 3 && i != 1) 72.0 else 0.0) / 68.2 * 3600).toInt())
            }
        }
    }

    private fun drivePeriods() = buildJsonArray {
        listOf(0 to 2, 0 to 9, 2 to 1, 2 to 9, 4 to 3).forEach { (day, hourAgo) ->
            val start = now.minus(day.toLong(), ChronoUnit.DAYS).minus(hourAgo.toLong(), ChronoUnit.HOURS).minusSeconds(2_400)
            addJsonObject {
                put("started_at", start.toString()); put("ended_at", start.plusSeconds(1_500L + hourAgo * 120L).toString())
                put("reporter_count", if (day == 2 && hourAgo == 9) 2 else 1)
            }
        }
    }

    private fun plotEvents() = buildJsonArray {
        refuels().reversed().forEachIndexed { i, r ->
            addJsonObject {
                put("fueled_at", r["fueled_at"]!!); put("mileage", r["mileage"]!!); put("price_per_liter", r["price_per_liter_calculated"]!!)
                if (i > 0) put("consumption_l_per_100km", 6.4 + 0.7 * sin(i * 1.3))
            }
        }
    }

    private fun months() = buildJsonArray {
        val first = LocalDate.now(zone).withDayOfMonth(1)
        for (i in 11 downTo 0) {
            addJsonObject {
                put("month_key", first.minusMonths(i.toLong()).toString())
                put("km_driven", if (i == 0) 410.0 else 560.0 + 160 * sin(i * 0.9)); put("liters_used", 38.0)
                put("estimated_km", if (i == 0) 218.0 else 0.0); put("estimated_liters", if (i == 0) 14.2 else 0.0); put("driving_hours", 9.0)
            }
        }
    }

    private fun members() = buildJsonArray {
        addJsonObject { put("user_id", USER_ID); put("role", "owner"); put("full_name", "Jannis"); put("email", "jannis@example.com") }
        addJsonObject { put("user_id", "22222222-2222-4222-8222-222222222222"); put("role", "shared"); put("full_name", "Alex"); put("email", "alex@example.com") }
    }

    private fun nearby() = buildJsonObject {
        putJsonArray("stations") {
            val list = listOf(
                Triple("TotalEnergies", 1.739, 0.6), Triple("ARAL", 1.789, 1.1), Triple("JET", 1.749, 1.9),
                Triple("Shell", 1.819, 2.2), Triple("Agip", 1.769, 2.6), Triple("bft", 1.759, 2.9),
            )
            list.forEachIndexed { i, (brand, price, dist) ->
                addJsonObject {
                    put("id", "n$i"); put("name", brand); put("brand", brand); put("street", "Musterstraße"); put("house_number", "${i + 1}")
                    put("place", "Erlangen"); put("lat", 49.59 + 0.012 * sin(i * 1.7)); put("lng", 11.0 + 0.016 * kotlin.math.cos(i * 1.3))
                    put("currentPrice", price); put("distanceKm", dist); put("isOpen", i != 3)
                }
            }
        }
    }

    @Suppress("unused")
    private fun ids(vararg values: String) = buildJsonArray { values.forEach { add(it) } }
}
