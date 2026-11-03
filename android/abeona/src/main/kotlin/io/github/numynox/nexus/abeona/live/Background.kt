package io.github.numynox.nexus.abeona.live

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.numynox.nexus.abeona.AbeonaApplication
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.reminders.LeaveReminder
import io.github.numynox.nexus.abeona.widget.NextTripWidget
import io.github.numynox.nexus.core.data.isNetworkFailure
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What runs without the app open: a half-hourly job that keeps the next trip's
 * live values and the widget current, and — when "Live companion" is on in
 * Settings — a job half an hour before the next departure that starts the
 * companion. The time-to-leave reminder is set from here too.
 */
object Background {
    private const val PERIODIC = "abeona-next-trip"
    private const val AUTOSTART = "abeona-companion-autostart"
    internal const val KEY_TRIP = "trip_id"

    /** How long before a departure the companion starts by itself. */
    val LEAD: Duration = Duration.ofMinutes(30)

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NextTripWorker>(Duration.ofMinutes(30))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Sets the companion's start for the next trip, or cancels it when the
     * setting is off; and the time-to-leave reminder, likewise.
     */
    suspend fun scheduleAutostart(context: Context, trips: List<Trip>, now: Instant = Instant.now()) {
        val container = (context.applicationContext as AbeonaApplication).container
        val settings = container.preferences.current()
        LeaveReminder.schedule(context, trips, settings.leaveReminder, settings.stationMinutes, now)
        val work = WorkManager.getInstance(context)
        val next = trips.firstOrNull { it.expectedDeparture.isAfter(now) }
        if (!settings.liveCompanion || next == null || next.departureAt.isAfter(now.plus(Duration.ofDays(7)))) {
            work.cancelUniqueWork(AUTOSTART)
            return
        }
        val delay = Duration.between(now, next.departureAt.minus(LEAD)).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<CompanionStartWorker>()
            .setInitialDelay(delay)
            .setInputData(workDataOf(KEY_TRIP to next.id))
            .build()
        work.enqueueUniqueWork(AUTOSTART, ExistingWorkPolicy.REPLACE, request)
    }

    /** Starts the companion; Android 12+ may refuse from the background, and then a tap on a reminder does it. */
    internal fun startOrRemind(context: Context, trip: Trip) {
        try {
            LiveCompanion.start(context, trip.id)
        } catch (refused: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException is an IllegalStateException.
            LiveCompanion.remind(context, trip)
        }
    }

    internal fun startsSoon(trip: Trip, now: Instant): Boolean =
        !trip.expectedDeparture.isAfter(now.plus(LEAD).plusSeconds(300)) &&
            trip.expectedArrival.isAfter(now)
}

/** Half-hourly: the next trip's live values (on its day), the widget, and the companion's start if it is due. */
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
                if (container.preferences.current().liveCompanion && Background.startsSoon(next, now) && LiveCompanion.running.value == null) {
                    Background.startOrRemind(applicationContext, next)
                }
            }
            Background.scheduleAutostart(applicationContext, trips, now)
            NextTripWidget.refresh(applicationContext)
            Result.success()
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) Result.retry() else Result.failure()
        }
    }
}

/** Half an hour before a departure: start following it. */
class CompanionStartWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AbeonaApplication).container
        if (!container.preferences.current().liveCompanion) return Result.success()
        container.core.auth.awaitUserId() ?: return Result.success()
        val id = inputData.getString(Background.KEY_TRIP) ?: return Result.success()
        val trip = runCatching { container.trips.trip(id).value }.getOrNull() ?: return Result.success()
        if (Background.startsSoon(trip, Instant.now()) && LiveCompanion.running.value == null) {
            Background.startOrRemind(applicationContext, trip)
        }
        return Result.success()
    }
}
