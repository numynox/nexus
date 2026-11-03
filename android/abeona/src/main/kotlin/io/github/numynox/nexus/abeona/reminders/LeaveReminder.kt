package io.github.numynox.nexus.abeona.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.numynox.nexus.abeona.AbeonaApplication
import io.github.numynox.nexus.abeona.MainActivity
import io.github.numynox.nexus.abeona.R
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.departureDelay
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import java.time.Duration
import java.time.Instant

/**
 * "Time to leave": one notification when the next trip's first train leaves
 * in the phone's time to the station. Device-local and off by default, like
 * the BahnCard reminder. A one-off job is set for the moment to leave; when
 * it runs it refreshes the trip, and a train that now leaves later moves the
 * job instead of ringing early.
 */
object LeaveReminder {
    private const val WORK = "abeona-leave-reminder"
    private const val CHANNEL = "leave"
    private const val NOTIFICATION = 4712
    internal const val KEY_TRIP = "trip_id"

    /** Never further ahead than this, so a trip next month does not hold a job. */
    private val HORIZON: Duration = Duration.ofDays(7)

    /** A train that moved later by more than this gets the reminder moved with it. */
    internal val SLACK: Duration = Duration.ofMinutes(3)

    /** When to leave for [trip]: its first ride's expected departure minus [stationMinutes]. */
    fun leaveAt(trip: Trip, stationMinutes: Int): Instant =
        trip.expectedDeparture.minus(Duration.ofMinutes(stationMinutes.coerceAtLeast(0).toLong()))

    /** Sets the reminder for the next trip still to leave for, or cancels it. */
    fun schedule(context: Context, trips: List<Trip>, on: Boolean, stationMinutes: Int, now: Instant = Instant.now()) {
        val work = WorkManager.getInstance(context)
        val next = trips.firstOrNull { leaveAt(it, stationMinutes).isAfter(now) }
        if (!on || next == null || leaveAt(next, stationMinutes).isAfter(now.plus(HORIZON))) {
            work.cancelUniqueWork(WORK)
            return
        }
        enqueue(context, next.id, Duration.between(now, leaveAt(next, stationMinutes)))
    }

    internal fun enqueue(context: Context, tripId: String, delay: Duration) {
        val request = OneTimeWorkRequestBuilder<LeaveReminderWorker>()
            .setInitialDelay(delay.coerceAtLeast(Duration.ZERO))
            .setInputData(workDataOf(KEY_TRIP to tripId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** "Time to leave for Frankfurt(Main)Hbf" and "ICE 25 leaves at 10:57 (+7) from Pl. 8. 15 min to the station." */
    fun message(trip: Trip, stationMinutes: Int): Pair<String, String> {
        val first = trip.legs.firstOrNull { !it.isWalk }
        val title = "Time to leave for ${first?.from?.name ?: trip.originName}"
        if (first == null) return title to "${trip.displayTitle}. $stationMinutes min to the station."
        val delay = departureDelay(first).takeIf { it > 0 }?.let { " (+$it)" } ?: ""
        val platform = first.from.shownPlatform?.let { " from Pl. $it" } ?: ""
        val text = if (first.cancelled) {
            "${first.label} is cancelled. Open the trip for alternatives."
        } else {
            "${first.label} leaves at ${TripFormat.time(first.from.expectedAt)}$delay$platform. $stationMinutes min to the station."
        }
        return title to text
    }

    /** The notification itself; built here so the screenshot test draws the real one. */
    fun build(context: Context, trip: Trip, title: String, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.abeona_green))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setShowWhen(false)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    NOTIFICATION,
                    MainActivity.intentFor(context, trip.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setAutoCancel(true)
            .build()

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.leave_channel), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    internal fun notify(context: Context, trip: Trip, stationMinutes: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val (title, text) = message(trip, stationMinutes)
        NotificationManagerCompat.from(context).notify(NOTIFICATION, build(context, trip, title, text))
    }
}

class LeaveReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AbeonaApplication).container
        val settings = container.preferences.current()
        if (!settings.leaveReminder) return Result.success()
        container.core.auth.awaitUserId() ?: return Result.success()
        val id = inputData.getString(LeaveReminder.KEY_TRIP) ?: return Result.success()
        val saved = runCatching { container.trips.trip(id).value }.getOrNull() ?: return Result.success()
        // The latest departure, if the network allows; the saved one otherwise.
        val trip = runCatching { container.trips.refresh(saved) }.getOrDefault(saved)
        val now = Instant.now()
        val leaveAt = LeaveReminder.leaveAt(trip, settings.stationMinutes)
        when {
            leaveAt.isAfter(now.plus(LeaveReminder.SLACK)) -> LeaveReminder.enqueue(applicationContext, trip.id, Duration.between(now, leaveAt))
            trip.expectedDeparture.isAfter(now) -> LeaveReminder.notify(applicationContext, trip, settings.stationMinutes)
        }
        return Result.success()
    }
}
