package io.github.numynox.nexus.vibilia.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.numynox.nexus.core.data.isNetworkFailure
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.vibilia.MainActivity
import io.github.numynox.nexus.vibilia.R
import io.github.numynox.nexus.vibilia.VibiliaApplication
import io.github.numynox.nexus.vibilia.data.Car
import io.github.numynox.nexus.vibilia.data.FuelLevel
import io.github.numynox.nexus.vibilia.data.PriceOutlook
import io.github.numynox.nexus.vibilia.data.StationPrice
import io.github.numynox.nexus.vibilia.data.VibiliaSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * "Fill up for 1.889 €/L at JET" — told when it pays off. While a tank is low,
 * an hourly check compares the hour with the day's usual price shape
 * ([PriceOutlook]) and speaks up once a day: when prices sit at their daily
 * low, when the low is due within three hours, or — whatever the prices —
 * when the tank runs dry within half a day. Device-local and off by default,
 * like Annona's expiry reminder: a car is shared, nagging is personal.
 */
object RefuelReminders {
    private const val WORK = "vibilia-refuel-reminder"
    private const val CHANNEL = "refuel"
    private const val NOTIFICATION = 2

    /** Nobody wants a fuel tip at night. */
    val Awake = 7..21

    /** A low due this far ahead is worth waiting for. */
    const val LookAheadHours = 3

    /** A car low on fuel: under the threshold, or dry within a day. */
    private val EmptySoon: Duration = Duration.ofDays(1)

    /** Dry this soon, and the reminder does not wait for a good price. */
    private val Urgent: Duration = Duration.ofHours(12)

    fun apply(context: Context, settings: VibiliaSettings, now: LocalDateTime = LocalDateTime.now()) {
        val work = WorkManager.getInstance(context)
        if (!settings.refuelRemindersOn) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<RefuelReminderWorker>(Duration.ofHours(1))
            .setInitialDelay(delayUntilNextCheck(now))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Until ten past the next hour: just after the price refresh at :07. */
    fun delayUntilNextCheck(now: LocalDateTime): Duration {
        var next = now.withMinute(10).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusHours(1)
        return Duration.between(now, next)
    }

    /** Low: under [percent] full, or dry within a day. */
    fun isLow(level: FuelLevel, percent: Int, now: Instant = Instant.now()): Boolean =
        level.fillRatio * 100 < percent || level.emptyAt(now)?.isBefore(now.plus(EmptySoon)) == true

    /** When to fuel, and at what price per litre at [Advice.station]. */
    sealed interface Advice {
        val station: StationPrice
        val price: Double

        /** Prices are at their daily low now. */
        data class Now(override val station: StationPrice, override val price: Double) : Advice

        /** The low is due at [hour] o'clock, at about [price]. */
        data class At(val hour: Int, override val station: StationPrice, override val price: Double) : Advice

        /** No good price in sight, but the tank will not wait. */
        data class Today(override val station: StationPrice, override val price: Double) : Advice
    }

    /**
     * When to fuel, given the cheapest station now and the day's usual
     * shape. Without a shape — too little history — any hour is as good.
     * Null when it pays to wait longer than [LookAheadHours] and the tank
     * allows it.
     */
    fun advice(cheapest: StationPrice, outlook: PriceOutlook?, hour: Int, urgent: Boolean): Advice? {
        val price = cheapest.effectivePrice ?: return null
        if (outlook == null || outlook.isLow(hour)) return Advice.Now(cheapest, price)
        outlook.nextLow(hour, LookAheadHours)?.let { target ->
            val expected = outlook.expected(price, hour, target) ?: price
            if (expected < price - PriceOutlook.Tolerance) return Advice.At(target, cheapest, expected)
            return Advice.Now(cheapest, price)
        }
        return if (urgent) Advice.Today(cheapest, price) else null
    }

    /** The notification's title and lines, or null when no car is low or it is not the time. */
    fun message(
        levels: List<Pair<Car, FuelLevel>>,
        percent: Int,
        cheapest: StationPrice?,
        outlook: PriceOutlook?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<String, List<String>>? {
        val low = levels.filter { (_, level) -> isLow(level, percent, now) }.sortedBy { (_, level) -> level.fillRatio }
        if (low.isEmpty() || cheapest == null) return null
        val urgent = low.any { (_, level) -> level.emptyAt(now)?.isBefore(now.plus(Urgent)) == true }
        val advice = advice(cheapest, outlook, now.atZone(zone).hour, urgent) ?: return null
        val cars = if (low.size == 1) "the ${low.single().first.name}" else "${low.size} cars"
        val title = when (advice) {
            is Advice.Now -> "Refuel $cars now"
            is Advice.At -> "Refuel $cars from %02d:00".format(advice.hour)
            is Advice.Today -> "Refuel $cars today"
        }
        val where = "${advice.station.displayName} ${advice.station.displayPlace}".trim()
        val price = "${NexusFormat.number(advice.price, 3)} €/L"
        val first = "Fill up for $price at $where" + when (advice) {
            is Advice.Now -> " — today's low"
            is Advice.At -> " — expected, now ${NexusFormat.number(cheapest.effectivePrice!!, 3)} €/L"
            is Advice.Today -> " — no lower price due soon"
        }
        return title to listOf(first) + low.map { (car, level) -> line(car, level, named = low.size > 1) }
    }

    private fun line(car: Car, level: FuelLevel, named: Boolean): String = listOfNotNull(
        "${NexusFormat.liters(level.remainingLiters, 0)} left".let { if (named) "${car.name}: $it" else it },
        level.rangeKm?.let { "${NexusFormat.km(it)} range" },
    ).joinToString(" · ")

    internal fun notify(context: Context, title: String, lines: List<String>) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle().also { inbox -> lines.take(6).forEach(inbox::addLine) }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.vibilia_red))
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION, notification)
    }
}

class RefuelReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as VibiliaApplication).container
        val settings = container.preferences.current()
        if (!settings.refuelRemindersOn) return Result.success()
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        if (now.hour !in RefuelReminders.Awake || container.preferences.refuelReminderSentOn() == now.toLocalDate()) return Result.success()
        container.auth.awaitUserId() ?: return Result.success()
        return try {
            val levels = container.cars.cars().filter { (it.tankCapacity ?: 0.0) > 0 }.mapNotNull { car ->
                container.statistics.fuelLevel(car.id, car) { container.logbook.refuels(car.id) }?.let { car to it }
            }
            if (levels.none { (_, level) -> RefuelReminders.isLow(level, settings.refuelReminderPercent) }) return Result.success()
            val cheapest = container.prices.currentPrices(settings.fuelType)
                .filter { it.effectivePrice != null }.minByOrNull { it.effectivePrice!! }
            // The shape is a nicety: without it the reminder just says where it is cheapest now.
            val outlook = runCatching {
                val since = now.toLocalDate().minusDays(7).atStartOfDay(zone).toInstant()
                PriceOutlook.from(container.prices.priceHistory(settings.fuelType, since, 60, zone.id), now.toLocalDate())
            }.getOrNull()
            RefuelReminders.message(levels, settings.refuelReminderPercent, cheapest, outlook, zone = zone)?.let { (title, lines) ->
                RefuelReminders.notify(applicationContext, title, lines)
                container.preferences.setRefuelReminderSentOn(now.toLocalDate())
            }
            Result.success()
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) Result.retry() else Result.failure()
        }
    }
}
