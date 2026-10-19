package io.github.numynox.nexus.vibilia.data

import java.time.Duration
import java.time.Instant

/**
 * How much fuel is in the tank. Computed by get_car_fuel_level_estimate; see
 * decisions/2026-10-19-bluetooth-drive-tracking.md for the two methods.
 */
data class FuelLevel(
    val method: Method,
    val lastRefuelAt: Instant,
    val lastMileage: Int?,
    val tankCapacity: Double,
    val startingLiters: Double,
    val remainingLiters: Double,
    val drivingHours: Double?,
    val estimatedKm: Double?,
    val kmPerHour: Double?,
    val litersPerHour: Double?,
    val litersPerDay: Double?,
) {
    enum class Method { Drive, Time }

    val fillRatio: Double get() = (remainingLiters / tankCapacity).coerceIn(0.0, 1.0)
    val estimatedMileage: Double? get() = lastMileage?.let { mileage -> estimatedKm?.let { mileage + it } }

    /** How far the remaining fuel goes at the learned consumption. */
    val rangeKm: Double?
        get() = if (kmPerHour != null && litersPerHour != null && litersPerHour > 0) {
            remainingLiters / litersPerHour * kmPerHour
        } else {
            null
        }

    /**
     * Adds driving this phone has seen but not uploaded yet — above all the
     * drive in progress — so the gauge moves while you drive.
     */
    fun plusLocalDriving(hours: Double): FuelLevel {
        if (method != Method.Drive || hours <= 0 || litersPerHour == null) return this
        return copy(
            remainingLiters = (remainingLiters - hours * litersPerHour).coerceAtLeast(0.0),
            drivingHours = (drivingHours ?: 0.0) + hours,
            estimatedKm = kmPerHour?.let { (estimatedKm ?: 0.0) + hours * it } ?: estimatedKm,
        )
    }

    companion object {
        fun from(row: FuelLevelRow) = FuelLevel(
            method = if (row.method == "drive") Method.Drive else Method.Time,
            lastRefuelAt = row.lastRefuelAt,
            lastMileage = row.lastMileage,
            tankCapacity = row.tankCapacity,
            startingLiters = row.startingLiters,
            remainingLiters = row.remainingLiters,
            drivingHours = row.drivingHours,
            estimatedKm = row.estimatedKm,
            kmPerHour = row.kmPerHour,
            litersPerHour = row.litersPerHour,
            litersPerDay = row.litersPerDay,
        )

        /**
         * The calendar method, computed on the phone — only for a database
         * without get_car_fuel_level_estimate. Same rules as the web app's old
         * fetchFuelLevelEstimateForCar: mean litres per day over the last year's
         * refuel intervals.
         */
        fun fromRefuels(car: Car?, refuels: List<RefuelEvent>, now: Instant = Instant.now()): FuelLevel? {
            val tank = car?.tankCapacity?.takeIf { it > 0 } ?: return null
            val ordered = refuels.sortedByDescending { it.fueledAt }
            val latest = ordered.firstOrNull() ?: return null
            val starting = latest.fuelLevelAfter?.takeIf { it >= 0 } ?: return null
            val yearAgo = now.minus(Duration.ofDays(365))
            val samples = ordered.zipWithNext().mapNotNull { (newer, older) ->
                if (newer.missedPreviousRefuel || newer.fueledAt.isBefore(yearAgo)) return@mapNotNull null
                val days = Duration.between(older.fueledAt, newer.fueledAt).toMillis() / 86_400_000.0
                val olderLevel = older.fuelLevelAfter ?: return@mapNotNull null
                val newerLevel = newer.fuelLevelAfter ?: return@mapNotNull null
                val consumed = olderLevel + newer.liters - newerLevel
                if (days <= 0 || consumed < 0) null else consumed / days
            }
            if (samples.isEmpty()) return null
            val perDay = samples.average()
            val days = Duration.between(latest.fueledAt, now).toMillis().coerceAtLeast(0) / 86_400_000.0
            return FuelLevel(
                method = Method.Time,
                lastRefuelAt = latest.fueledAt,
                lastMileage = latest.mileage,
                tankCapacity = tank,
                startingLiters = minOf(tank, starting),
                remainingLiters = (starting - days * perDay).coerceAtLeast(0.0),
                drivingHours = null,
                estimatedKm = null,
                kmPerHour = null,
                litersPerHour = null,
                litersPerDay = perDay,
            )
        }
    }
}
