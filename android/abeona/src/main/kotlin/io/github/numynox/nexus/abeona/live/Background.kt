package io.github.numynox.nexus.abeona.live

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.numynox.nexus.abeona.AbeonaApplication
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.reminders.LeaveReminder
import io.github.numynox.nexus.abeona.widget.NextTripWidget
import io.github.numynox.nexus.core.data.isNetworkFailure
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What runs without the app open: a half-hourly job that keeps the next trip's
 * live values and the widget current, and — when "Live companion" is on in
 * Settings — an exact alarm before the next departure that starts the
 * companion. The time-to-leave reminder is set from here too.
 *
 * Why an alarm and not a WorkManager job: Android 12+ refuses to start a
 * foreground service from a background job, so the job could only ever post
 * a "tap to follow" reminder. An exact alarm (USE_EXACT_ALARM; the app is
 * sideloaded, so Play's policy on it does not apply) is one of the exemptions,
 * and it also fires on time in Doze, where a delayed job may run late.
 * Without the permission (Android 12 with it revoked) it falls back to an
 * inexact alarm and the reminder.
 */
object Background {
    private const val PERIODIC = "abeona-next-trip"
    /** The WorkManager job older versions used for the start; cancelled so it does not fire twice. */
    private const val OLD_AUTOSTART = "abeona-companion-autostart"
    internal const val ACTION_AUTOSTART = "io.github.numynox.nexus.abeona.live.AUTOSTART"
    internal const val KEY_TRIP = "trip_id"

    /** The least time before a departure the companion starts by itself. */
    val MIN_LEAD: Duration = Duration.ofMinutes(30)

    /**
     * How long before the first departure the companion starts: the time to
     * the station plus a quarter of an hour, so "Time to leave" is on the
     * lock screen before it is time to go; never less than [MIN_LEAD].
     */
    fun lead(stationMinutes: Int): Duration =
        maxOf(MIN_LEAD, Duration.ofMinutes(stationMinutes.coerceAtLeast(0) + 15L))

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NextTripWorker>(Duration.ofMinutes(30))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** When the companion should start for [trip]. */
    fun startAt(trip: Trip, stationMinutes: Int): Instant = trip.expectedDeparture.minus(lead(stationMinutes))

    /** From the start until the trip has arrived: the companion should be running. */
    fun isDue(trip: Trip, stationMinutes: Int, now: Instant): Boolean =
        !now.isBefore(startAt(trip, stationMinutes).minusSeconds(60)) && trip.expectedArrival.isAfter(now)

    /**
     * Sets the companion's start for the next trip, or cancels it when the
     * setting is off; and the time-to-leave reminder, likewise. With the app
     * in front ([foreground]), a trip that is already due starts at once:
     * opening the app during a ride is enough to have it followed.
     */
    suspend fun scheduleAutostart(context: Context, trips: List<Trip>, now: Instant = Instant.now(), foreground: Boolean = false) {
        val container = (context.applicationContext as AbeonaApplication).container
        val settings = container.preferences.current()
        LeaveReminder.schedule(context, trips, settings.leaveReminder, settings.stationMinutes, now)
        WorkManager.getInstance(context).cancelUniqueWork(OLD_AUTOSTART)
        val next = trips.firstOrNull { it.expectedArrival.isAfter(now) && it.legs.any { leg -> !leg.isWalk } }
        if (!settings.liveCompanion || next == null || next.departureAt.isAfter(now.plus(Duration.ofDays(7)))) {
            CompanionAlarm.cancel(context)
            return
        }
        if (isDue(next, settings.stationMinutes, now)) {
            CompanionAlarm.cancel(context)
            if (LiveCompanion.running.value == null && LiveCompanion.stoppedByHand.value != next.id) {
                // In front Android allows the start; should it not (the app went
                // away meanwhile), the alarm may.
                val started = foreground && runCatching { LiveCompanion.start(context, next.id) }.isSuccess
                if (!started) CompanionAlarm.set(context, next.id, now)
            }
            return
        }
        CompanionAlarm.set(context, next.id, startAt(next, settings.stationMinutes))
    }
}

/** What the trip screen offers for the live companion (live/Background.kt: companionOffer). */
sealed interface CompanionOffer {
    /** It follows this trip: "Stop". */
    data object Running : CompanionOffer

    /** It will start by itself at [at] (or now, when that has passed): small print, no button. */
    data class Automatic(val at: Instant) : CompanionOffer

    /** It does not start by itself (the setting is off, or it was stopped by hand): "Start". */
    data object Manual : CompanionOffer

    /** Not now: too early (another day) or the trip is over. */
    data object Hidden : CompanionOffer
}

/** Trips leaving before this local time can be followed from the evening before. */
val EARLY_DEPARTURE: LocalTime = LocalTime.of(3, 0)

/**
 * The companion's offer for [trip]: running, starting by itself, startable
 * by hand, or nothing. By hand only on the travel day, from the evening
 * before for a departure before 03:00, and while the trip is under way; never
 * later than [LiveCompanion.LINGER] after the arrival.
 */
fun companionOffer(
    trip: Trip,
    now: Instant,
    autoStart: Boolean,
    stationMinutes: Int,
    running: Boolean,
    stoppedByHand: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): CompanionOffer {
    if (running) return CompanionOffer.Running
    if (!LiveCompanion.canFollow(trip, now) || trip.legs.none { !it.isWalk }) return CompanionOffer.Hidden
    // After the arrival nothing starts it by itself any more: by hand, until it is too late.
    if (autoStart && !stoppedByHand && trip.expectedArrival.isAfter(now)) return CompanionOffer.Automatic(Background.startAt(trip, stationMinutes))
    val departure = trip.expectedDeparture.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val day = departure.toLocalDate()
    val onTheDay = today == day ||
        (today == day.minusDays(1) && departure.toLocalTime().isBefore(EARLY_DEPARTURE)) ||
        !now.isBefore(trip.expectedDeparture)
    return if (onTheDay) CompanionOffer.Manual else CompanionOffer.Hidden
}

/** The one alarm that starts the companion. */
internal object CompanionAlarm {
    private fun pending(context: Context, tripId: String?, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, CompanionAlarmReceiver::class.java)
                .setAction(Background.ACTION_AUTOSTART)
                .apply { tripId?.let { putExtra(Background.KEY_TRIP, it) } },
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    fun canBeExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun set(context: Context, tripId: String, at: Instant) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pending(context, tripId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val millis = maxOf(at.toEpochMilli(), System.currentTimeMillis() + 1_000)
        if (canBeExact(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    fun cancel(context: Context) {
        val intent = pending(context, null, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(intent)
        intent.cancel()
    }
}

/**
 * The alarm went off: start following the trip, if the setting is still on
 * and nothing follows a trip already. No network first: Android allows the
 * start only for a few seconds after the alarm, and the service loads the
 * trip itself (and stops if it is gone). The alarm is set again whenever
 * the trips load, so a train that moved later moves the alarm with it.
 * Should Android refuse anyway, a reminder with a "Follow live" button does it.
 */
class CompanionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Background.ACTION_AUTOSTART) return
        val id = intent.getStringExtra(Background.KEY_TRIP) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val container = (app as AbeonaApplication).container
                if (!container.preferences.current().liveCompanion || LiveCompanion.running.value != null) return@launch
                try {
                    LiveCompanion.start(app, id)
                } catch (refused: IllegalStateException) {
                    // ForegroundServiceStartNotAllowedException is an IllegalStateException.
                    runCatching { container.trips.trip(id).value }.getOrNull()?.let { LiveCompanion.remind(app, it) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Half-hourly: the next trip's live values (on its day), the widget, and the companion's alarm. */
class NextTripWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AbeonaApplication).container
        container.core.auth.awaitUserId() ?: return Result.success()
        return try {
            val trips = container.trips.upcoming().value
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            trips.firstOrNull { it.expectedArrival.isAfter(now) }?.let { next ->
                if (next.departureAt.atZone(zone).toLocalDate() <= LocalDate.now(zone)) runCatching { container.trips.refresh(next) }
            }
            // A trip already due gets an alarm for now: the alarm, unlike this
            // job, may start the companion.
            Background.scheduleAutostart(applicationContext, trips, now)
            NextTripWidget.refresh(applicationContext)
            Result.success()
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) Result.retry() else Result.failure()
        }
    }
}
