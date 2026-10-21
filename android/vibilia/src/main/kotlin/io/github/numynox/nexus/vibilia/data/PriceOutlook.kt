package io.github.numynox.nexus.vibilia.data

import java.time.LocalDate

/**
 * How fuel prices move over a day, learned from the last days' history: for
 * each hour, how far the lowest price of our stations usually sits above that
 * day's low. German prices follow the same rhythm day after day — a jump at
 * noon, a slide through the afternoon, the low from the evening to the next
 * late morning — so the shape predicts when the next low comes, and the
 * current price says at what level.
 *
 * [premiums] maps hour of day (0–23) to the typical premium over the day's
 * low, in €/L: the median across the days seen.
 */
class PriceOutlook(val premiums: Map<Int, Double>) {

    /** The typical premium at [hour], or null when the history never covered it. */
    fun premiumAt(hour: Int): Double? = premiums[Math.floorMod(hour, 24)]

    /** True when [hour] usually sits at the day's low, within [tolerance]. */
    fun isLow(hour: Int, tolerance: Double = Tolerance): Boolean = premiumAt(hour)?.let { it <= tolerance + 1e-9 } == true

    /**
     * The first hour after [hour], up to [within] hours ahead, that usually
     * sits at the low — or null if none does.
     */
    fun nextLow(hour: Int, within: Int = 3, tolerance: Double = Tolerance): Int? =
        (1..within).map { hour + it }.firstOrNull { isLow(it, tolerance) }?.let { Math.floorMod(it, 24) }

    /** What a price of [current] at [hour] usually becomes at [target]. */
    fun expected(current: Double, hour: Int, target: Int): Double? {
        val now = premiumAt(hour) ?: return null
        val then = premiumAt(target) ?: return null
        return current - now + then
    }

    companion object {
        /** A cent: within it, a price counts as at the low. */
        const val Tolerance = 0.01

        /** Days that must be complete before the shape is trusted. */
        private const val MinDays = 3

        /**
         * Learns the shape from hourly [buckets] (`get_fuel_price_plot_history`
         * with 60-minute buckets). Today is left out: half a day has no low yet.
         * Null with fewer than three days covering most of the day.
         */
        fun from(buckets: List<PriceBucket>, today: LocalDate): PriceOutlook? {
            val days = buckets.filter { it.day.isBefore(today) }
                .groupBy { it.day }
                .mapValues { (_, day) -> day.associate { it.bucketMinute / 60 to it.minPrice } }
                .filterValues { it.size >= 20 }
            if (days.size < MinDays) return null
            val premiums = (0..23).mapNotNull { hour ->
                val values = days.values.mapNotNull { day -> day[hour]?.let { it - day.values.min() } }.sorted()
                if (values.isEmpty()) null else hour to values[values.size / 2]
            }.toMap()
            return PriceOutlook(premiums)
        }
    }
}
