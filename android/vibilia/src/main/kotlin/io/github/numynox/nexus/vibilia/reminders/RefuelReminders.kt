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
import io.github.numynox.nexus.vibilia.data.StationPrice
import io.github.numynox.nexus.vibilia.data.VibiliaSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The daily "time to refuel" notification — Annona's expiry reminder, for the
 * tank. Device-local and off by default: a car is shared, but each phone
 * decides whether it nags. WorkManager keeps one periodic job; near the chosen
 * hour it asks Postgres for every car's fuel estimate and speaks up only for
 * the low ones.
 */
object RefuelReminders {
    private const val WORK = "vibilia-refuel-reminder"
    private const val CHANNEL = "refuel"
    private const val NOTIFICATION = 2

    /** A car runs dry within this, whatever its level: worth a word too. */
    private val EmptySoon: Duration = Duration.ofDays(1)

    fun apply(context: Context, settings: VibiliaSettings, now: LocalDateTime = LocalDateTime.now()) {
        val work = WorkManager.getInstance(context)
        if (!settings.refuelRemindersOn) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<RefuelReminderWorker>(Duration.ofDays(1))
            .setInitialDelay(delayUntil(settings.refuelReminderHour, now))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Until the next [hour] o'clock — today if it is still ahead, else tomorrow. */
    fun delayUntil(hour: Int, now: LocalDateTime): Duration {
        var next = now.toLocalDate().atTime(LocalTime.of(hour, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    /** Low: under [percent] full, or dry within a day. */
    fun isLow(level: FuelLevel, percent: Int, now: Instant = Instant.now()): Boolean =
        level.fillRatio * 100 < percent || level.emptyAt(now)?.isBefore(now.plus(EmptySoon)) == true

    /**
     * What a reminder says about [levels] — each car with its estimate — or
     * null when none is low. The fill-up cost is at [cheapest], our cheapest
     * station right now.
     */
    fun message(
        levels: List<Pair<Car, FuelLevel>>,
        percent: Int,
        cheapest: StationPrice?,
        now: Instant = Instant.now(),
    ): Pair<String, List<String>>? {
        val low = levels.filter { (_, level) -> isLow(level, percent, now) }.sortedBy { (_, level) -> level.fillRatio }
        if (low.isEmpty()) return null
        val title = if (low.size == 1) "Time to refuel the ${low.single().first.name}" else "${low.size} cars need fuel"
        return title to low.map { (car, level) -> line(car, level, cheapest, named = low.size > 1) }
    }

    private fun line(car: Car, level: FuelLevel, cheapest: StationPrice?, named: Boolean): String = listOfNotNull(
        "${NexusFormat.liters(level.remainingLiters, 0)} left".let { if (named) "${car.name}: $it" else it },
        level.rangeKm?.let { "${NexusFormat.km(it)} range" },
        cheapest?.effectivePrice?.let { "fill up ${NexusFormat.euro(level.litersToFull * it)} at ${cheapest.displayName}" },
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
            .setStyle(if (lines.size > 1) style else NotificationCompat.BigTextStyle().bigText(lines.first()))
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
        container.auth.awaitUserId() ?: return Result.success()
        return try {
            val levels = container.cars.cars().filter { (it.tankCapacity ?: 0.0) > 0 }.mapNotNull { car ->
                container.statistics.fuelLevel(car.id, car) { container.logbook.refuels(car.id) }?.let { car to it }
            }
            // The price is a nicety: without it the reminder still says what is left.
            val cheapest = runCatching { container.prices.currentPrices(settings.fuelType) }.getOrDefault(emptyList())
                .filter { it.effectivePrice != null }.minByOrNull { it.effectivePrice!! }
            RefuelReminders.message(levels, settings.refuelReminderPercent, cheapest)
                ?.let { (title, lines) -> RefuelReminders.notify(applicationContext, title, lines) }
            Result.success()
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) Result.retry() else Result.failure()
        }
    }
}
